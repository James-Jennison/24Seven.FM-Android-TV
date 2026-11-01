package com.codeframe78.twentyfourseven.player.playback

import android.content.Context
import com.codeframe78.twentyfourseven.player.analysis.MusicAnalysisWorker
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.AudioSink
import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationRepository
import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Observes decoded PCM on Media3's audio path and publishes a compact, ephemeral waveform
 * envelope for the TV idle screen. It never records, retains, or transmits audio samples.
 */
@UnstableApi
internal class AudioVisualizationProcessor(
    private val visualization: AudioVisualizationRepository,
) : BaseAudioProcessor() {
    private var lastPublishedAtMs = 0L
    private var calibratedMeter = CalibratedPcmMeter(DefaultSampleRateHz)
    private var channelCount = 0
    private val musicWorker = MusicAnalysisWorker()
    private var captureScratch = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val supported =
            inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
                inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        return if (supported) {
            inputAudioFormat
        }
        else AudioProcessor.AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        captureCalibratedPcm(inputBuffer)
        publishVisualization()
        val output = replaceOutputBuffer(inputBuffer.remaining())
        output.put(inputBuffer)
        output.flip()
    }

    // Media3 still invokes this lifecycle callback for AudioProcessor implementations.
    // Keep the reset local until its replacement contract is available upstream.
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onFlush() {
        resetVisualization()
        if (inputAudioFormat.bytesPerFrame > 0 && inputAudioFormat.channelCount in 1..32 &&
            inputAudioFormat.sampleRate in 100..384000) {
            channelCount = inputAudioFormat.channelCount
            calibratedMeter.configure(inputAudioFormat.sampleRate)
            captureScratch = FloatArray(1024 * channelCount)
            musicWorker.configure(inputAudioFormat.sampleRate, channelCount)
        }
    }

    override fun onReset() = resetVisualization()

    fun close() {
        musicWorker.close()
        visualization.clear()
    }

    private fun publishVisualization() {
        val nowMs = android.os.SystemClock.elapsedRealtime()
        if (nowMs - lastPublishedAtMs < PublishIntervalMillis) return
        lastPublishedAtMs = nowMs
        val calibratedReading = calibratedMeter.reading
        visualization.publish(
            AudioVisualizationState(
                music = musicWorker.latest(),
                energy = calibratedReading.energy,
                sampledAtElapsedRealtimeMs = nowMs,
                leftEnergy = calibratedReading.energy,
                rightEnergy = calibratedReading.energy,
                leftDbfs = calibratedReading.leftDbfs,
                rightDbfs = calibratedReading.rightDbfs,
                stereoCorrelation = calibratedReading.stereoCorrelation,
                calibratedBandDbfs = calibratedReading.bandDbfs.toList(),
                channelCount = channelCount,
                waveform = calibratedMeter.waveformSnapshot().toList(),
                bands = calibratedReading.spectrumLevels.toList(),
                transient = calibratedReading.transient,
            ),
        )
    }

    /** Feeds every decoded frame into the calibrated meter, independently of UI publish cadence. */
    private fun captureCalibratedPcm(inputBuffer: ByteBuffer) {
        val frames = inputBuffer.remaining() / inputAudioFormat.bytesPerFrame
        if (frames <= 0) return
        val duplicate = inputBuffer.duplicate().order(ByteOrder.nativeOrder())
        val channels = inputAudioFormat.channelCount
        var capturedSamples = 0
        repeat(frames) {
            var mono = 0f
            var left = 0f
            var right = 0f
            for (channel in 0 until channels) {
                val sample = if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
                    duplicate.getFloat()
                } else {
                    duplicate.getShort().toFloat() / Short.MAX_VALUE
                }
                if (captureScratch.isNotEmpty()) captureScratch[capturedSamples++] = sample
                mono += sample
                if (channel == 0) left = sample
                if (channel == 1) right = sample
            }
            calibratedMeter.accept(mono / channels, left, if (channels > 1) right else left)
            if (capturedSamples == captureScratch.size && capturedSamples > 0) {
                musicWorker.offer(captureScratch, capturedSamples, android.os.SystemClock.elapsedRealtime())
                capturedSamples = 0
            }
        }
        if (capturedSamples > 0) musicWorker.offer(captureScratch, capturedSamples, android.os.SystemClock.elapsedRealtime())
    }

    private fun resetVisualization() {
        lastPublishedAtMs = 0L
        calibratedMeter.reset()
        musicWorker.clear()
        captureScratch = FloatArray(0)
        channelCount = 0
        visualization.clear()
    }

    private companion object {
        // 60 Hz matches the display cadence. The analysis window is bounded and discarded
        // immediately after each feature update; PCM is never retained or transmitted.
        const val PublishIntervalMillis = 16L
        const val DefaultSampleRateHz = 48_000
    }
}

/** Adds the visualization observer to the same decoded-audio path Media3 sends to the speaker. */
@UnstableApi
internal class AudioVisualizationRenderersFactory(
    context: Context,
    private val visualizationProcessor: AudioVisualizationProcessor,
) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParameters: Boolean,
    ): AudioSink = DefaultAudioSink.Builder(context)
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParameters)
        .setAudioProcessors(arrayOf(visualizationProcessor))
        .build()
}
