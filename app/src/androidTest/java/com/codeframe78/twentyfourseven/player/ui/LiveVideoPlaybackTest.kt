package com.codeframe78.twentyfourseven.player.ui

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.codeframe78.twentyfourseven.player.MainActivity
import com.codeframe78.twentyfourseven.player.RadioApplication
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerStyle
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class LiveVideoPlaybackTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun livePcmKeepsActualVideoFramesAdvancing() {
        val audio = androidx.lifecycle.ViewModelProvider(compose.activity)[TvRadioViewModel::class.java].visualization
        val style = androidx.test.platform.app.InstrumentationRegistry.getArguments()
            .getString("visualizerStyle")?.let(IdleVisualizerStyle::valueOf) ?: IdleVisualizerStyle.AuroraDrift
        lateinit var root: View
        compose.runOnUiThread { compose.activity.setContent {
            root = LocalView.current
            TvVideoIdleVisualizer(style, audio, false, Modifier.fillMaxSize())
        } }
        fun findVideo(node: View): VideoVisualizerView? {
            if (node is VideoVisualizerView) return node
            if (node is ViewGroup) repeat(node.childCount) { findVideo(node.getChildAt(it))?.let { found -> return found } }
            return null
        }
        var previous = -1
        var previousLights = -1
        repeat(30) { second ->
            repeat(30) { compose.mainClock.advanceTimeBy(33); Thread.sleep(33) }
            if (second >= 5) compose.runOnUiThread {
                val view = checkNotNull(findVideo(root.rootView))
                val field = VideoVisualizerView::class.java.getDeclaredField("player").apply { isAccessible = true }
                val player = field.get(view) as androidx.media3.exoplayer.ExoPlayer
                val state = audio.value
                val frames = player.videoDecoderCounters!!.renderedOutputBufferCount
                val diagnostic = "second=$second frames=$frames previous=$previous ready=${player.playWhenReady} suppression=${player.playbackSuppressionReason} state=${player.playbackState} age=${SystemClock.elapsedRealtime()-state.sampledAtElapsedRealtimeMs} energy=${state.energy} channels=${state.channelCount}"
                assertTrue(diagnostic, player.isPlaying)
                assertTrue(diagnostic, frames > previous + 2)
                if (style == IdleVisualizerStyle.DjConsole) {
                    val lights = view.getChildAt(3) as RaveLightingView
                    assertTrue("live PCM lighting must be visible", lights.isShown)
                    assertTrue("live PCM must drive lighting energy", lights.frame.energy > 0f)
                    assertTrue("live light frames must keep advancing", lights.renderedFrames > previousLights + 2)
                    previousLights = lights.renderedFrames
                }
                if (style.isPhotographicNative()) {
                    val native = (0 until view.childCount).map { view.getChildAt(it) }
                        .filterIsInstance<PhotographicReactiveView>().single()
                    assertTrue("native photographic overlay must be visible", native.isShown)
                    assertTrue("actual PCM must reach native presentation", native.frame.channels > 0)
                    assertTrue("native presentation must keep rendering", native.renderedFrames > previousLights + 2)
                    previousLights = native.renderedFrames
                }
                previous = frames
            }
        }
    }
}
