package com.codeframe78.twentyfourseven.player.ui

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import com.codeframe78.twentyfourseven.player.MainActivity
import com.codeframe78.twentyfourseven.player.RadioApplication
import org.junit.Assert.*
import org.junit.Test

/** Diagnostic only: owner controls the remote; no UI clicks or preference writes. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerVideoObservationTest {
    @Test fun observeOwnerOpenedVideoInNormalPlayer() {
        org.junit.Assume.assumeTrue("Requires the owner-operated diagnostic argument",
            androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("observeOwner") == "true")
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        fun findVideo(node: View): VideoVisualizerView? {
            if (node is VideoVisualizerView) return node
            if (node is ViewGroup) repeat(node.childCount) { findVideo(node.getChildAt(it))?.let { found -> return found } }
            return null
        }
        var seen = 0
        var previous = -1
        var previousView: VideoVisualizerView? = null
        val started = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - started < 120_000 && seen < 15) {
            Thread.sleep(1000)
            scenario.onActivity { activity ->
                val view = findVideo(activity.window.decorView) ?: return@onActivity
                val field = VideoVisualizerView::class.java.getDeclaredField("player").apply { isAccessible = true }
                val player = field.get(view) as? androidx.media3.exoplayer.ExoPlayer ?: return@onActivity
                if (previousView !== view) { previous = -1; seen = 0; previousView = view }
                val audio = (activity.application as RadioApplication).appContainer.audioVisualizationRepository.state.value
                val frames = player.videoDecoderCounters?.renderedOutputBufferCount ?: 0
                val frameField = VideoVisualizerView::class.java.getDeclaredField("frame").apply { isAccessible = true }
                val frame = frameField.get(view) as VideoAudioFrame
                val detail = "sample=$seen frames=$frames previous=$previous playWhenReady=${player.playWhenReady} suppression=${player.playbackSuppressionReason} playbackState=${player.playbackState} moving=${frame.moving} audioAgeMs=${SystemClock.elapsedRealtime()-audio.sampledAtElapsedRealtimeMs} energy=${audio.energy} channels=${audio.channelCount}"
                android.util.Log.i("VideoObservation", detail)
                if (seen >= 2) assertTrue(detail, frames > previous + 2)
                previous = frames
                seen++
            }
        }
        assertTrue("Owner-opened video was not observed long enough", seen >= 15)
    }
}
