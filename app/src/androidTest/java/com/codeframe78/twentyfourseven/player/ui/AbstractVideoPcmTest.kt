package com.codeframe78.twentyfourseven.player.ui

import android.graphics.Matrix
import android.os.SystemClock
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.codeframe78.twentyfourseven.player.data.InMemoryAudioVisualizationRepository
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerStyle
import com.codeframe78.twentyfourseven.player.playback.AudioVisualizationProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic tones pass through the real Media3 PCM analyser and the actual video view. */
@androidx.annotation.OptIn(UnstableApi::class)
class AbstractVideoPcmTest {
    @get:Rule val compose = createComposeRule()

    @Test fun productionUpdateLoopKeepsFluidMovingAcrossSeveralLoops() {
        val audio = MutableStateFlow(com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState())
        lateinit var root: View
        compose.setContent {
            root = LocalView.current
            TvVideoIdleVisualizer(IdleVisualizerStyle.AuroraDrift, audio, false, Modifier.fillMaxSize())
        }
        fun findVideo(node: View): VideoVisualizerView? {
            if (node is VideoVisualizerView) return node
            if (node is ViewGroup) repeat(node.childCount) { findVideo(node.getChildAt(it))?.let { found -> return found } }
            return null
        }
        var previous: IntArray? = null
        var samples = 0
        var lastRendered = -1
        val started = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - started < 65_000) {
            val elapsed = SystemClock.elapsedRealtime() - started
            // Fresh modulated band measurements exercise changing decoder speed and the real Compose ticker.
            audio.value = com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState(
                sampledAtElapsedRealtimeMs = SystemClock.elapsedRealtime(), energy = .6f, channelCount = 2,
                calibratedBandDbfs = listOf(-18f + 8f * sin(elapsed / 500.0).toFloat(), -20f, -25f, -30f),
            )
            compose.mainClock.advanceTimeBy(33)
            if (elapsed / 1_000 > samples && elapsed > 2_000) {
                samples = (elapsed / 1_000).toInt()
                lateinit var pixels: IntArray
                var position = -1L
                compose.runOnUiThread {
                    val view = checkNotNull(findVideo(root.rootView))
                    assertEquals(View.GONE, view.getChildAt(1).visibility)
                    val field = VideoVisualizerView::class.java.getDeclaredField("player").apply { isAccessible = true }
                    val decoder = field.get(view) as androidx.media3.exoplayer.ExoPlayer
                    assertTrue("Decoder must keep playing at second $samples; state=${decoder.playbackState}; ready=${decoder.playWhenReady}; suppression=${decoder.playbackSuppressionReason}; age=${SystemClock.elapsedRealtime() - audio.value.sampledAtElapsedRealtimeMs}", decoder.isPlaying)
                    position = decoder.currentPosition
                    val rendered = decoder.videoDecoderCounters!!.renderedOutputBufferCount
                    assertTrue("Decoder output stuck at second $samples: $rendered rendered buffers; position=$position", rendered > lastRendered)
                    lastRendered = rendered
                    val bitmap = (view.getChildAt(0) as TextureView).getBitmap(64, 36)!!
                    pixels = IntArray(64 * 36)
                    bitmap.getPixels(pixels, 0, 64, 0, 0, 64, 36)
                    bitmap.recycle()
                }
                assertFalse("Fluid froze at second $samples, media position $position", previous?.contentEquals(pixels) == true)
                previous = pixels
            }
            Thread.sleep(33)
        }
        assertTrue(samples >= 60)
    }

    @Test fun decodedBassExpandsVideoAndStalledPcmFreezesIt() {
        val repository = InMemoryAudioVisualizationRepository()
        val processor = AudioVisualizationProcessor(repository)
        lateinit var view: VideoVisualizerView
        compose.setContent {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                VideoVisualizerView(context).also {
                    view = it
                    it.select(IdleVisualizerStyle.AuroraDrift)
                    it.setForeground(true)
                }
            }, onRelease = { it.release() })
        }
        fun feedTone(hz: Double) {
            processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
            processor.flush()
            repeat(30) { block ->
                val bytes = ByteBuffer.allocateDirect(2_048 * 4).order(ByteOrder.nativeOrder())
                repeat(2_048) { sample ->
                    val value = (sin(2 * PI * hz * (block * 2_048 + sample) / 48_000) * 11_000).toInt().toShort()
                    bytes.putShort(value).putShort(value)
                }
                bytes.flip()
                processor.queueInput(bytes)
                processor.output.let { it.position(it.limit()) }
                compose.runOnUiThread { view.updateAudio(repository.state.value, .035f, false) }
                Thread.sleep(35)
            }
        }
        fun scale(): Float {
            var result = 0f
            compose.runOnUiThread {
                val values = FloatArray(9)
                (view.getChildAt(0) as TextureView).getTransform(Matrix()).getValues(values)
                result = values[Matrix.MSCALE_X]
            }
            return result
        }
        fun pixels(): IntArray {
            var result = IntArray(0)
            compose.runOnUiThread {
                val bitmap = (view.getChildAt(0) as TextureView).getBitmap(64, 36)!!
                result = IntArray(64 * 36)
                bitmap.getPixels(result, 0, 64, 0, 0, 64, 36)
                bitmap.recycle()
            }
            return result
        }
        try {
            feedTone(60.0)
            assertEquals(2, repository.state.value.channelCount)
            assertTrue(repository.state.value.sampledAtElapsedRealtimeMs > 0)
            assertTrue(repository.state.value.calibratedBandDbfs[0] > -35f)
            val bassScale = scale()
            assertTrue(bassScale > 1.03f)
            compose.waitUntil(10_000) {
                var visible = false
                compose.runOnUiThread { visible = view.getChildAt(1).visibility == View.GONE }
                visible
            }
            feedTone(5_000.0)
            assertTrue(repository.state.value.calibratedBandDbfs[3] > -35f)
            assertTrue("Bass must expand the video more than a high tone", bassScale > scale() + .02f)
            Thread.sleep(600)
            assertTrue(SystemClock.elapsedRealtime() - repository.state.value.sampledAtElapsedRealtimeMs > 500)
            compose.runOnUiThread { view.updateAudio(repository.state.value, .033f, false) }
            Thread.sleep(350)
            val stopped = pixels()
            Thread.sleep(350)
            assertArrayEquals("Stale PCM must stop the real decoder", stopped, pixels())
        } finally {
            processor.reset()
            processor.close()
            compose.runOnUiThread { view.release() }
        }
    }
}
