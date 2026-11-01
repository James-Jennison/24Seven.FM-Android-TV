package com.codeframe78.twentyfourseven.player.ui

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.codeframe78.twentyfourseven.player.MainActivity
import com.codeframe78.twentyfourseven.player.RadioApplication
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerStyle
import com.codeframe78.twentyfourseven.player.domain.PlaybackStatus
import org.junit.Assert.*
import org.junit.Test

/** Physical real-clock acceptance. Screenshots contain only this app's selected scene; no audio capture. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PhotographicLiveAcceptanceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun ui(block: () -> Unit) = instrumentation.runOnMainSync(block)
    @Test fun realPcmNormalReducedPauseAndResume() {
        val context = instrumentation.targetContext
        context.startActivity(android.content.Intent(context, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        var activity: MainActivity? = null
        val deadline = SystemClock.elapsedRealtime() + 15000
        while (activity == null && SystemClock.elapsedRealtime() < deadline) {
            ui { activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull() }
            Thread.sleep(50)
        }
        val screen = checkNotNull(activity)
        lateinit var controller: com.codeframe78.twentyfourseven.player.domain.PlaybackController
        lateinit var audio: kotlinx.coroutines.flow.StateFlow<com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState>
        var wasPlaying = false
        ui {
            controller = (screen.application as RadioApplication).appContainer.playbackController
            audio = ViewModelProvider(screen)[TvRadioViewModel::class.java].visualization
            wasPlaying = controller.state.value.status == PlaybackStatus.Playing
            controller.play()
        }
        fun waitFresh(after: Long) {
            val end = SystemClock.elapsedRealtime() + 20000
            while ((audio.value.sampledAtElapsedRealtimeMs <= after || audio.value.channelCount == 0) && SystemClock.elapsedRealtime() < end) Thread.sleep(50)
            assertTrue("Fresh real station PCM required", audio.value.sampledAtElapsedRealtimeMs > after && audio.value.channelCount > 0)
        }
        lateinit var root: View
        fun find(node: View): VideoVisualizerView? {
            if (node is VideoVisualizerView) return node
            if (node is ViewGroup) repeat(node.childCount) { find(node.getChildAt(it))?.let { result -> return result } }
            return null
        }
        fun owner() = checkNotNull(find(root.rootView))
        fun layer() = owner().let { v -> (0 until v.childCount).map { v.getChildAt(it) }.filterIsInstance<PhotographicReactiveView>().single() }
        fun water() = owner().let { v -> (0 until v.childCount).map { v.getChildAt(it) }.filterIsInstance<LanternWaterView>().single() }
        fun decoder() = VideoVisualizerView::class.java.getDeclaredField("player").apply { isAccessible = true }
            .get(owner()) as? androidx.media3.exoplayer.ExoPlayer
        val requested = InstrumentationRegistry.getArguments().getString("visualizerStyle")
        val styles = listOf(IdleVisualizerStyle.LanternDrift, IdleVisualizerStyle.VinylPulse, IdleVisualizerStyle.PulseQuilt).filter { requested == null || it.name == requested }
        try {
            waitFresh(SystemClock.elapsedRealtime() - 500)
            for (style in styles) {
                for (reduced in listOf(false, true)) {
                    ui { screen.setContent { root = LocalView.current; TvVideoIdleVisualizer(style, audio, reduced, Modifier.fillMaxSize()) } }
                    Thread.sleep(3000)
                    val intervals = mutableListOf<Double>()
                    var last = 0L
                    var firstTravel = 0f
                    var minLanternPulse = 1f
                    var maxLanternPulse = 0f
                    var minLaserLevel = 1f
                    var maxLaserLevel = 0f
                    val listener = android.view.ViewTreeObserver.OnDrawListener {
                        val now = System.nanoTime()
                        if (last != 0L) intervals.add((now - last) / 1e6)
                        last = now
                        if (style == IdleVisualizerStyle.LanternDrift) {
                            minLanternPulse = minOf(minLanternPulse, layer().frame.lanternPulse)
                            maxLanternPulse = maxOf(maxLanternPulse, layer().frame.lanternPulse)
                        }
                        if (style == IdleVisualizerStyle.PulseQuilt) {
                            val f = layer().frame
                            val level = (f.laserLight.maxOrNull() ?: 0f) * .75f + f.laserEnergy * .25f
                            minLaserLevel = minOf(minLaserLevel, level)
                            maxLaserLevel = maxOf(maxLaserLevel, level)
                        }
                    }
                    var firstNative = 0
                    var firstVideo = 0
                    var firstHardware = 0
                    var firstWater = 0
                    var drops = 0
                    ui {
                        assertTrue(layer().isShown)
                        firstWater = water().hardwareFrames
                        firstTravel = layer().frame.laserTravel
                        firstNative = layer().renderedFrames
                        firstHardware = layer().hardwareLaserFrames
                        firstVideo = (decoder()?.videoDecoderCounters?.renderedOutputBufferCount ?: 0)
                        drops = (decoder()?.videoDecoderCounters?.droppedBufferCount ?: 0)
                        root.viewTreeObserver.addOnDrawListener(listener)
                    }
                    try { Thread.sleep(8000) } finally { ui { root.viewTreeObserver.removeOnDrawListener(listener) } }
                    ui {
                        val frame = layer().frame
                        val count = layer().renderedFrames - firstNative
                        val video = (decoder()?.videoDecoderCounters?.renderedOutputBufferCount ?: 0) - firstVideo
                        val dropped = (decoder()?.videoDecoderCounters?.droppedBufferCount ?: 0) - drops
                        val sorted = intervals.sorted()
                        assertTrue("Enough actual draw samples", sorted.size > 100)
                        val p95 = sorted[(sorted.size * .95).toInt()]
                        val p99 = sorted[(sorted.size * .99).toInt()]
                        println("PHOTO_LIVE ${style.name} reduced=$reduced native=$count video=$video dropped=$dropped drawP95Ms=$p95 drawP99Ms=$p99 ageMs=${SystemClock.elapsedRealtime()-audio.value.sampledAtElapsedRealtimeMs} left=${frame.leftDbfs} right=${frame.rightDbfs} cue=${frame.cue} hardwareFrames=${layer().hardwareLaserFrames-firstHardware} lightSurface=${layer().width}x${layer().height}")
                        assertTrue("Native frames advancing", count > 120)
                        if (style == IdleVisualizerStyle.LanternDrift) assertNull("Native lanterns must not allocate a video decoder", decoder())
                        else assertTrue("Video decoder advancing", video > 120 && decoder()?.isPlaying == true)
                        assertTrue("Bounded dropped decoder frames", dropped <= video / 10)
                        assertTrue("Draw interval p95 $p95", p95 <= 44.0)
                        if (style == IdleVisualizerStyle.LanternDrift) {
                            val refreshMs = 1000.0 / root.display.refreshRate
                            println("WATER_CADENCE refreshMs=$refreshMs p95Ratio=${p95 / refreshMs} p99Ratio=${p99 / refreshMs}")
                            assertTrue("Lantern p95 follows display cadence", p95 <= refreshMs * 1.5)
                            assertTrue("Lantern p99 bounded to two refresh intervals", p99 <= refreshMs * 2.0)
                        }
                        if (style == IdleVisualizerStyle.PulseQuilt) {
                            println("PHOTO_MUSIC reduced=$reduced travelStart=$firstTravel travelEnd=${frame.laserTravel} lightMin=$minLaserLevel lightMax=$maxLaserLevel")
                            assertNotEquals("Actual measured changes must move lasers", firstTravel, frame.laserTravel)
                            assertTrue("Live musical dynamics must reach laser light", maxLaserLevel - minLaserLevel > .02f)
                            assertTrue("Real hardware laser path must advance", layer().hardwareLaserFrames - firstHardware > 120)
                            assertTrue("Laser GPU layer remains bounded", layer().width <= 480)
                        } else assertEquals("Meters and lantern composition keep full view geometry", owner().width, layer().width)
                        assertTrue("Fresh real PCM reaches layer", frame.channels > 0 && SystemClock.elapsedRealtime()-audio.value.sampledAtElapsedRealtimeMs in 0..500)
                        if (style == IdleVisualizerStyle.LanternDrift) {
                            println("LANTERN_PULSE min=$minLanternPulse max=$maxLanternPulse")
                            assertTrue("Actual bass dynamics visibly modulate paper", maxLanternPulse - minLanternPulse > .2f)
                            println("WATER_LIVE reduced=$reduced hardwareFrames=${water().hardwareFrames-firstWater} phase=${frame.phase} bass=${frame.lanternBass}")
                            assertTrue("Actual water mesh must render", water().isShown && water().hardwareFrames-firstWater > 120)
                        } else assertFalse("Water must stay hidden for other scenes", water().isShown)
                        assertEquals(reduced, frame.reduced)
                        assertEquals(1f, owner().getChildAt(0).scaleX, 0f)
                        assertEquals(1f, owner().getChildAt(0).scaleY, 0f)

                    }
                    // Copy the actual application window so hardware-rendered laser pixels are included.
                    // The test content is the fullscreen scene, without another app or system UI.
                    lateinit var bitmap: android.graphics.Bitmap
                    val copied = java.util.concurrent.CountDownLatch(1)
                    var result = -1
                    ui {
                        bitmap = android.graphics.Bitmap.createBitmap(root.rootView.width, root.rootView.height, android.graphics.Bitmap.Config.ARGB_8888)
                        android.view.PixelCopy.request(screen.window, bitmap, { code -> result = code; copied.countDown() }, android.os.Handler(android.os.Looper.getMainLooper()))
                    }
                    assertTrue("App window copy timed out", copied.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    assertEquals(android.view.PixelCopy.SUCCESS, result)
                    val file = java.io.File(context.getExternalFilesDir(null), "photo-${style.name}-${if (reduced) "reduced" else "normal"}.png")
                    file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()

                }
                val pauseAt = SystemClock.elapsedRealtime()
                ui { controller.pause() }
                // Media3 may finish queued PCM after pause; require bounded visual settling,
                // rather than assuming the last decoded buffer coincides with the button action.
                var settled = false
                while (!settled && SystemClock.elapsedRealtime() - pauseAt < 6000) {
                    Thread.sleep(100)
                    ui { settled = decoder()?.playWhenReady != true && layer().frame.light.all { it <= .001f } }
                }
                println("PHOTO_PAUSE ${style.name} settled=$settled elapsedMs=${SystemClock.elapsedRealtime()-pauseAt}")
                var phase = 0f
                ui {
                    assertFalse("Decoder stops when station pauses", decoder()?.playWhenReady == true)
                    assertTrue("Lights extinguish on stale PCM: light=${layer().frame.light} age=${SystemClock.elapsedRealtime()-audio.value.sampledAtElapsedRealtimeMs} status=${controller.state.value.status}", layer().frame.light.all { it <= .001f })
                    phase = layer().frame.phase
                }
                Thread.sleep(350)
                ui { assertEquals("Phase freezes on silence", phase, layer().frame.phase, 0f) }
                val resumedAt = SystemClock.elapsedRealtime()
                ui { controller.play() }
                waitFresh(resumedAt)
                Thread.sleep(1000)
                ui { assertTrue("Scene resumes", if (style == IdleVisualizerStyle.LanternDrift) water().isShown else decoder()?.isPlaying == true); assertTrue(layer().frame.channels > 0) }
            }
        } finally { ui { if (!wasPlaying) controller.pause(); screen.finish() } }
    }
}
