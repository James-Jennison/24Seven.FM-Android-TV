package com.codeframe78.twentyfourseven.player.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class LanternWaterViewTest {
    @Test fun photographedWaterAndFloatingLanternsMoveWithoutMovingTheSkyOrOpeningGaps() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = LanternWaterView(ApplicationProvider.getApplicationContext())
            view.layout(0, 0, 836, 471)
            fun draw(phase: Float): IntArray {
                view.present(true, PhotographicFrame(phase = phase, lanternBass = 1f, lanternWaterBass = 1f))
                val bitmap = Bitmap.createBitmap(836, 471, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val pixels = IntArray(836 * 471)
                bitmap.getPixels(pixels, 0, 836, 0, 0, 836, 471)
                bitmap.recycle()
                return pixels
            }
            try {
                val rest = draw(0f)
                val moved = draw(1.5f)
                assertArrayEquals("Sky and shoreline remain fixed", rest.copyOfRange(0, 836 * 170), moved.copyOfRange(0, 836 * 170))
                assertTrue("Warp cannot open transparent holes", moved.all { Color.alpha(it) > 200 })
                fun changes(left: Int, top: Int, right: Int, bottom: Int): Int {
                    var count = 0
                    for (y in top until bottom) for (x in left until right) if (rest[y * 836 + x] != moved[y * 836 + x]) count++
                    return count
                }
                assertTrue("Open water texture moves", changes(20, 300, 80, 450) > 100)
                assertTrue("Near paper lantern and raft move", changes(465, 260, 660, 410) > 1000)
                assertTrue("Left floating lantern moves", changes(100, 200, 190, 275) > 500)
            } finally { view.release() }
            assertEquals(android.view.View.GONE, view.visibility)
        }
    }
    @Test fun bassPulseChangesTheActualPaperBrightness() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = LanternWaterView(ApplicationProvider.getApplicationContext())
            view.layout(0, 0, 836, 471)
            fun paper(pulse: Float): IntArray {
                assertTrue(view.present(true, PhotographicFrame(lanternPulse = pulse)))
                val bitmap = Bitmap.createBitmap(836, 471, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val pixels = IntArray(836 * 471)
                bitmap.getPixels(pixels, 0, 836, 0, 0, 836, 471)
                bitmap.recycle()
                return pixels
            }
            try {
                val low = paper(0f)
                val high = paper(1f)
                for (p in PhotographicGeometry.lanterns) {
                    var before = 0L
                    var after = 0L
                    for (y in ((p[1] - p[3] * .35f) / 2).toInt()..((p[1] + p[3] * .35f) / 2).toInt()) {
                        for (x in ((p[0] - p[2] * .35f) / 2).toInt()..((p[0] + p[2] * .35f) / 2).toInt()) {
                            before += Color.red(low[y * 836 + x])
                            after += Color.red(high[y * 836 + x])
                        }
                    }
                    assertTrue("Each photographed shade must brighten substantially", after > before * 1.5)
                }
            } finally { view.release() }
        }
    }

    @Test fun physicalGpuPaperColorsBrightenAllFourLanterns() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        context.startActivity(android.content.Intent(context, com.codeframe78.twentyfourseven.player.MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        var screen: com.codeframe78.twentyfourseven.player.MainActivity? = null
        val deadline = android.os.SystemClock.elapsedRealtime() + 15000
        while (screen == null && android.os.SystemClock.elapsedRealtime() < deadline) {
            instrument.runOnMainSync {
                screen = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .filterIsInstance<com.codeframe78.twentyfourseven.player.MainActivity>().firstOrNull()
            }
            Thread.sleep(50)
        }
        val activity = checkNotNull(screen)
        lateinit var view: LanternWaterView
        instrument.runOnMainSync { view = LanternWaterView(activity); activity.setContentView(view) }
        fun capture(pulse: Float): Bitmap {
            instrument.runOnMainSync { assertTrue(view.present(true, PhotographicFrame(lanternPulse = pulse))) }
            Thread.sleep(350)
            lateinit var bitmap: Bitmap
            val done = java.util.concurrent.CountDownLatch(1)
            var result = -1
            instrument.runOnMainSync {
                bitmap = Bitmap.createBitmap(activity.window.decorView.width, activity.window.decorView.height, Bitmap.Config.ARGB_8888)
                android.view.PixelCopy.request(activity.window, bitmap, { result = it; done.countDown() }, android.os.Handler(android.os.Looper.getMainLooper()))
            }
            assertTrue(done.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(android.view.PixelCopy.SUCCESS, result)
            return bitmap
        }
        try {
            val low = capture(0f)
            val high = capture(1f)
            val origin = IntArray(2)
            instrument.runOnMainSync { view.getLocationInWindow(origin); assertTrue(view.hardwareFrames >= 2) }
            for (p in PhotographicGeometry.lanterns) {
                val x = origin[0] + (p[0] * view.width / PhotographicGeometry.Width).toInt()
                val y = origin[1] + (p[1] * view.height / PhotographicGeometry.Height).toInt()
                var before = 0L
                var after = 0L
                for (dy in -5..5) for (dx in -5..5) {
                    before += Color.red(low.getPixel(x + dx, y + dy))
                    after += Color.red(high.getPixel(x + dx, y + dy))
                }
                println("LANTERN_GPU paperX=$x brightnessRatio=${after.toDouble() / before}")
                assertTrue("GPU must brighten every photographed shade", after > before * 1.5)
            }
            for ((name, bitmap) in listOf("low" to low, "high" to high)) {
                java.io.File(context.getExternalFilesDir(null), "lantern-gpu-$name.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
        } finally { instrument.runOnMainSync { view.release(); activity.finish() } }
    }

}
