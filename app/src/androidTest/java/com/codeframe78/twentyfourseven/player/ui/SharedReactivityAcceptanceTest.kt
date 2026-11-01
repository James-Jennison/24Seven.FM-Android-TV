package com.codeframe78.twentyfourseven.player.ui

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import com.codeframe78.twentyfourseven.player.MainActivity
import com.codeframe78.twentyfourseven.player.RadioApplication
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerStyle
import com.codeframe78.twentyfourseven.player.domain.PlaybackStatus
import org.junit.Assert.*
import org.junit.Test

/** Real service PCM and both real rendering paths. Timing here is freshness, not acoustic latency. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SharedReactivityAcceptanceTest {
    private lateinit var activity: MainActivity
    private fun onUi(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)

    @Test fun pcm16AndFloatBytesPassThroughUnchanged() {
        val repository = com.codeframe78.twentyfourseven.player.data.InMemoryAudioVisualizationRepository()
        val processor = com.codeframe78.twentyfourseven.player.playback.AudioVisualizationProcessor(repository)
        try {
            for (encoding in listOf(androidx.media3.common.C.ENCODING_PCM_16BIT, androidx.media3.common.C.ENCODING_PCM_FLOAT)) {
                val bytesPerSample = if (encoding == androidx.media3.common.C.ENCODING_PCM_FLOAT) 4 else 2
                processor.configure(androidx.media3.common.audio.AudioProcessor.AudioFormat(48000, 2, encoding))
                processor.flush()
                for (frames in listOf(1, 123, 1024, 2301)) {
                    val bytes = java.nio.ByteBuffer.allocateDirect(frames * 2 * bytesPerSample).order(java.nio.ByteOrder.nativeOrder())
                    repeat(frames * 2) { index ->
                        if (bytesPerSample == 4) bytes.putFloat(kotlin.math.sin(index.toDouble()).toFloat())
                        else bytes.putShort((index * 173 - 32000).toShort())
                    }
                    bytes.flip()
                    val expected = ByteArray(bytes.remaining()).also { bytes.duplicate().get(it) }
                    processor.queueInput(bytes)
                    assertFalse(bytes.hasRemaining())
                    val output = processor.output
                    val actual = ByteArray(output.remaining()).also { output.get(it) }
                    assertArrayEquals("PCM encoding=$encoding frames=$frames", expected, actual)
                }
            }
        } finally { processor.close() }
    }

    @Test fun closingProcessorClearsApplicationSnapshot() {
        val repository = com.codeframe78.twentyfourseven.player.data.InMemoryAudioVisualizationRepository()
        val processor = com.codeframe78.twentyfourseven.player.playback.AudioVisualizationProcessor(repository)
        repository.publish(com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState(energy = .8f))
        processor.close()
        assertEquals(com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState(), repository.state.value)
    }

    @Test fun liveSharedStateDrivesNeonAndEmeraldAndRecoversAfterPause() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.startActivity(android.content.Intent(context, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        val launchDeadline = SystemClock.elapsedRealtime() + 15000
        while (!::activity.isInitialized && SystemClock.elapsedRealtime() < launchDeadline) {
            onUi {
                androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .filterIsInstance<MainActivity>().firstOrNull()?.let { activity = it }
            }
            Thread.sleep(50)
        }
        check(::activity.isInitialized) { "MainActivity did not resume" }
        val app = activity.application as RadioApplication
        lateinit var controller: com.codeframe78.twentyfourseven.player.domain.PlaybackController
        lateinit var audio: kotlinx.coroutines.flow.StateFlow<com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState>
        onUi {
            controller = app.appContainer.playbackController
            audio = ViewModelProvider(activity)[TvRadioViewModel::class.java].visualization
        }
        val legacyComparison = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("legacyComparison") == "true"
        val compatibilityAudio = kotlinx.coroutines.flow.MutableStateFlow(audio.value.copy(music = null))
        val renderAudio = if (legacyComparison) compatibilityAudio else audio
        var wasPlaying = false
        onUi {
            wasPlaying = controller.state.value.status == PlaybackStatus.Playing
            controller.play()
        }
        fun waitForMusic() {
            val end = SystemClock.elapsedRealtime() + 20000
            while (audio.value.music?.spectralWindowEndSample == null && SystemClock.elapsedRealtime() < end) Thread.sleep(50)
            assertNotNull("Live service did not publish shared FFT state", audio.value.music?.spectralWindowEndSample)
        }
        lateinit var root: View
        try {
            waitForMusic()
            onUi { activity.setContent {
                root = LocalView.current
                TvIdleVisualizer(IdleVisualizerStyle.NeonKaleidoscope, renderAudio, modifier = Modifier.fillMaxSize())
            } }
            Thread.sleep(500)
            var lastSequence = -1L
            var advances = 0
            val ages = mutableListOf<Long>()
            repeat(300) {
                Thread.sleep(100)
                if (legacyComparison) compatibilityAudio.value = audio.value.copy(music = null)
                val music = audio.value.music
                if (music != null) {
                    if (music.sequence != lastSequence) advances++
                    lastSequence = music.sequence
                    ages.add(SystemClock.elapsedRealtime() - music.measuredAtElapsedRealtimeMs)
                    assertTrue(music.amplitude.isFinite() && music.amplitude in 0f..1f)
                }
            }
            assertTrue("Shared state did not keep advancing: $advances", advances > 240)
            val p95Age = ages.sorted()[(ages.size * .95).toInt().coerceAtMost(ages.lastIndex)]
            assertTrue("Decode freshness p95 too old: $p95Age ms", p95Age in 0..200)
            onUi {
                fun inspect(node: View) {
                    if (node is android.opengl.GLSurfaceView) {
                        println("PHASE1 surface=${node.width}x${node.height} shown=${node.isShown} attached=${node.isAttachedToWindow}")
                        node.queueEvent {
                            val renderer = node.javaClass.getDeclaredField("renderer").apply { isAccessible = true }.get(node)
                            for (name in listOf("frameNumber", "width", "height", "performanceDiagnosticsEnabled")) {
                                println("PHASE1 renderer $name=" + renderer.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(renderer))
                            }
                        }
                    }
                    if (node is ViewGroup) repeat(node.childCount) { inspect(node.getChildAt(it)) }
                }
                inspect(root.rootView)
            }
            Thread.sleep(250)
            val timing = VisualizerPerformanceDiagnostics.latest
            assertEquals("Neon Kaleidoscope", timing?.styleName)
            println("PHASE1 legacy=$legacyComparison decode-freshness-p95-ms=$p95Age GL=$timing")

            if (legacyComparison) return
            assertTrue("Neon GL p95 exceeds 50 ms: $timing", checkNotNull(timing).p95FrameMillis <= 50f)
            onUi { activity.setContent {
                root = LocalView.current
                TvIdleVisualizer(IdleVisualizerStyle.EmeraldResonance, audio, modifier = Modifier.fillMaxSize())
            } }
            fun findVideo(node: View): VideoVisualizerView? {
                if (node is VideoVisualizerView) return node
                if (node is ViewGroup) repeat(node.childCount) { findVideo(node.getChildAt(it))?.let { found -> return found } }
                return null
            }
            var lastFrames = -1
            repeat(12) { second ->
                repeat(30) { Thread.sleep(33) }
                if (second >= 3) onUi {
                    val view = checkNotNull(findVideo(root.rootView))
                    val field = VideoVisualizerView::class.java.getDeclaredField("player").apply { isAccessible = true }
                    val player = field.get(view) as androidx.media3.exoplayer.ExoPlayer
                    val frames = player.videoDecoderCounters!!.renderedOutputBufferCount
                    assertTrue("Emerald decoder stopped", player.isPlaying)
                    assertTrue("Emerald frames did not advance", frames > lastFrames)
                    lastFrames = frames
                }
            }
            val beforePause = checkNotNull(audio.value.music)
            onUi { controller.pause() }
            repeat(45) { Thread.sleep(33) }
            assertNotEquals(PlaybackStatus.Playing, controller.state.value.status)
            val resumeAt = SystemClock.elapsedRealtime()
            onUi {
                val view = checkNotNull(findVideo(root.rootView))
                val field = VideoVisualizerView::class.java.getDeclaredField("player").apply { isAccessible = true }
                assertFalse("Local clip should stop after station pauses", (field.get(view) as androidx.media3.exoplayer.ExoPlayer).playWhenReady)
                controller.play()
            }
            val oldStamp = audio.value.sampledAtElapsedRealtimeMs
            val deadline = SystemClock.elapsedRealtime() + 15000
            while (audio.value.sampledAtElapsedRealtimeMs <= oldStamp && SystemClock.elapsedRealtime() < deadline) Thread.sleep(50)
            assertTrue("Shared PCM failed to resume", audio.value.sampledAtElapsedRealtimeMs > oldStamp)
            fun freshSpectrumAfterResume(): Boolean {
                val current = audio.value.music ?: return false
                val end = current.spectralWindowEndSample ?: return false
                return current.measuredAtElapsedRealtimeMs >= resumeAt &&
                    (current.epoch != beforePause.epoch ||
                        (current.sequence > beforePause.sequence && end > checkNotNull(beforePause.spectralWindowEndSample)))
            }
            val spectrumDeadline = SystemClock.elapsedRealtime() + 15000
            while (!freshSpectrumAfterResume() && SystemClock.elapsedRealtime() < spectrumDeadline) Thread.sleep(50)
            assertTrue("Shared FFT did not advance after resume", freshSpectrumAfterResume())
        } finally {
            onUi {
                if (!wasPlaying) controller.pause()
                activity.finish()
            }
        }
    }
}
