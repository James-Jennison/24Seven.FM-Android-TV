package com.codeframe78.twentyfourseven.player.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import com.codeframe78.twentyfourseven.player.domain.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NativeVideoBoundaryTest {
    @get:Rule val compose = createComposeRule()
    @Test fun nativeLanternRendersAndRestoresWithoutDecoderAndIonCannotExposeEmptyTexture() {
        lateinit var view: VideoVisualizerView
        compose.setContent { AndroidView(modifier = Modifier.fillMaxSize(),
            factory = { VideoVisualizerView(it).also { created -> view = created } }, onRelease = { it.release() }) }
        compose.waitForIdle()
        compose.runOnUiThread {
            view.select(IdleVisualizerStyle.LanternDrift)
            view.setForeground(true)
        }
        compose.waitForIdle()
        compose.runOnUiThread {
            val player = VideoVisualizerView::class.java.getDeclaredField("player").apply { isAccessible = true }
            val water = (0 until view.childCount).map { view.getChildAt(it) }.filterIsInstance<LanternWaterView>().single()
            val audio = AudioVisualizationState(energy = .8f, channelCount = 2, leftDbfs = -10f, rightDbfs = -10f,
                calibratedBandDbfs = listOf(-8f, -8f, -12f, -20f), transient = .6f)
            fun advance() = view.updateAudio(audio.copy(sampledAtElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime()), .05f, false)
            fun pixels(): IntArray {
                val image = Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888)
                val canvas = Canvas(image); canvas.scale(320f/water.width,180f/water.height);water.draw(canvas)
                val result = IntArray(320*180);image.getPixels(result,0,320,0,0,320,180);image.recycle();return result
            }
            repeat(20) { advance() }
            assertNull(player.get(view));assertEquals(View.GONE,view.getChildAt(0).visibility)
            assertEquals(View.VISIBLE,water.visibility);assertEquals(View.GONE,view.getChildAt(1).visibility)
            val before=pixels();repeat(30) { advance() };val after=pixels()
            assertTrue(after.any { it and 0x00ffffff != 0 });assertFalse(before.contentEquals(after))
            view.setForeground(false)
            assertEquals(View.VISIBLE,view.getChildAt(1).visibility);assertEquals(View.GONE,water.visibility)
            view.setForeground(true);advance()
            assertNull(player.get(view));assertEquals(View.VISIBLE,water.visibility)
            // Defensive entry into video wrapper: native Ion must leave only its poster, never an empty texture.
            view.select(IdleVisualizerStyle.IonTransit)
            assertNull(player.get(view));assertEquals(View.GONE,view.getChildAt(0).visibility)
            assertEquals(View.VISIBLE,view.getChildAt(1).visibility)
        }
    }
}
