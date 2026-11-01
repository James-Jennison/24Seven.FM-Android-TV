package com.codeframe78.twentyfourseven.player.ui

import android.view.TextureView
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerStyle
import com.codeframe78.twentyfourseven.player.domain.LavaLampPalette
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real bundled-asset decode and lifecycle check; the drive is an explicit PCM-state fixture. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class VideoVisualizerViewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun everyClipDecodesMovesStopsAndRecreatesItsSurface() {
        lateinit var view: VideoVisualizerView
        compose.setContent {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { VideoVisualizerView(it).also { created -> view = created } },
                onRelease = { it.release() },
            )
        }
        val audio = AudioVisualizationState(energy = .8f, channelCount = 2,
            calibratedBandDbfs = listOf(-12f, -12f, -18f, -24f), transient = .6f,
            leftDbfs = -12f, rightDbfs = -24f, bands = List(32) { .6f })
        fun waitForFrame() {
            compose.waitUntil(10_000) {
                var ready = false
                compose.runOnUiThread {
                    ready = view.getChildAt(0).visibility == View.VISIBLE && view.getChildAt(1).visibility == View.GONE
                }
                ready
            }
        }
        fun renderedFrames(): Int {
            var count = 0
            compose.runOnUiThread {
                val field = VideoVisualizerView::class.java.getDeclaredField("player").apply { isAccessible = true }
                val decoder = field.get(view) as androidx.media3.exoplayer.ExoPlayer
                count = decoder.videoDecoderCounters!!.renderedOutputBufferCount
            }
            return count
        }
        var includePhotographic = false
        var includeLiveDeck = false
        fun sample(): IntArray {
            var pixels = IntArray(0)
            compose.runOnUiThread {
                val bitmap = (view.getChildAt(0) as TextureView).getBitmap(64, 36)
                assertNotNull(bitmap)
                bitmap!!.let {
                    if (includeLiveDeck) {
                        // DJ is now a photographic video plate plus native PCM details.
                        // Exercise the rendered deck, not a changing compressed background.
                        val canvas = android.graphics.Canvas(it)
                        canvas.scale(64f / view.width, 36f / view.height)
                        (view.getChildAt(2) as RaveEquipmentView).draw(canvas)
                        canvas.save()
                        canvas.scale(view.getChildAt(3).scaleX, view.getChildAt(3).scaleY)
                        (view.getChildAt(3) as RaveLightingView).draw(canvas)
                        canvas.restore()
                    }
                    if (includePhotographic) {
                        val canvas = android.graphics.Canvas(it)
                        canvas.scale(64f / view.width, 36f / view.height)
                        val children = (0 until view.childCount).map { index -> view.getChildAt(index) }
                        children.filterIsInstance<LanternWaterView>().singleOrNull()?.let { water ->
                            if (water.visibility == View.VISIBLE) water.draw(canvas)
                        }
                        val layer = children.filterIsInstance<PhotographicReactiveView>().single()
                        canvas.scale(layer.scaleX, layer.scaleY)
                        layer.draw(canvas)
                    }
                    pixels = IntArray(64 * 36); it.getPixels(pixels, 0, 64, 0, 0, 64, 36); it.recycle() }
            }
            // For DJ, require changes in the foreground deck itself, not ceiling beams.
            return if (includeLiveDeck) pixels.copyOfRange(64 * 24, pixels.size) else pixels
        }
        try {
            val requestedStyle = androidx.test.platform.app.InstrumentationRegistry.getArguments()
                .getString("visualizerStyle")?.let(IdleVisualizerStyle::valueOf)
            val cases = IdleVisualizerStyle.entries.filter { it.videoAsset()?.usesBundledVideo == true && (requestedStyle == null || it == requestedStyle) }.flatMap { style ->
                (if (style == IdleVisualizerStyle.LavaLamp) LavaLampPalette.entries else listOf(LavaLampPalette.Neon)).map { style to it }
            }
            assertTrue("Requested encoded video cases must exist", cases.isNotEmpty())
            for ((style, palette) in cases) {
                compose.runOnUiThread {
                    view.select(style, palette)
                    view.setForeground(true)
                    view.updateAudio(audio.copy(sampledAtElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime()), .1f, false)
                    assertFalse(view.isFocusable)
                }
                waitForFrame()
                includePhotographic = style.isPhotographicNative()
                includeLiveDeck = style == IdleVisualizerStyle.DjConsole
                val first = sample()
                val firstRendered = renderedFrames()
                Thread.sleep(800)
                assertTrue("${style.name} must render multiple decoded buffers", renderedFrames() >= firstRendered + 3)
                if (includeLiveDeck || includePhotographic) compose.runOnUiThread {
                    view.updateAudio(audio.copy(leftDbfs = -36f, rightDbfs = -6f, bands = List(32) { .2f },
                        sampledAtElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime()), .1f, false)
                }
                assertFalse("${style.name} must show changing rendered details", first.contentEquals(sample()))
                if (style == IdleVisualizerStyle.LavaLamp || style == IdleVisualizerStyle.DjConsole || includePhotographic) {
                    // Exercise every palette across its encoded loop on the physical decoder.
                    // This fixture fixes speed at 1x; LiveVideoPlaybackTest covers actual PCM speed.
                    lateinit var decoder: androidx.media3.exoplayer.ExoPlayer
                    var initialDropped = 0
                    compose.runOnUiThread {
                        val field = VideoVisualizerView::class.java.getDeclaredField("player").apply { isAccessible = true }
                        decoder = field.get(view) as androidx.media3.exoplayer.ExoPlayer
                        decoder.setPlaybackSpeed(1f)
                        decoder.seekTo(0)
                        initialDropped = decoder.videoDecoderCounters!!.droppedBufferCount
                    }
                    var previousPosition = -1L
                    var previousFrames = renderedFrames()
                    var wrapped = false
                    repeat(23) {
                        Thread.sleep(1000)
                        compose.runOnUiThread {
                            val frames = decoder.videoDecoderCounters!!.renderedOutputBufferCount
                            assertTrue("${palette.name}: frames must advance every second", frames > previousFrames + 3)
                            previousFrames = frames
                            val position = decoder.currentPosition
                            if (previousPosition >= 0 && position < previousPosition) wrapped = true
                            previousPosition = position
                            assertTrue("${palette.name}: decoder remains playing", decoder.isPlaying)
                        }
                    }
                    compose.runOnUiThread {
                        val counters = decoder.videoDecoderCounters!!
                        val rendered = counters.renderedOutputBufferCount - firstRendered
                        val dropped = counters.droppedBufferCount - initialDropped
                        assertTrue("${palette.name}: must cross the 20-second loop", wrapped)
                        assertTrue("${palette.name}: excessive dropped frames $dropped/$rendered", dropped <= rendered / 10)
                        println("VIDEO_LOOP_ACCEPTANCE ${style.name} ${palette.name}: rendered=$rendered dropped=$dropped wrap=$wrapped")
                    }
                }
                compose.runOnUiThread { view.updateAudio(AudioVisualizationState(), .1f, false) }
                Thread.sleep(350)
                val quiet = sample()
                Thread.sleep(350)
                assertArrayEquals("${style.name} pauses on silence", quiet, sample())
                compose.runOnUiThread {
                    view.setForeground(false)
                    assertEquals(View.VISIBLE, view.getChildAt(1).visibility)
                    view.setForeground(true)
                    view.updateAudio(audio.copy(sampledAtElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime()), .1f, true)
                }
                waitForFrame()
                assertTrue("${style.name} has a visible decoded image", sample().any { it and 0x00FFFFFF != 0 })
                println("VIDEO_ACCEPTANCE ${style.name} ${palette.name}: decoded movement, silence and foreground recreation PASS")
            }
        } finally {
            compose.runOnUiThread { view.release(); view.release() }
        }
    }
}
