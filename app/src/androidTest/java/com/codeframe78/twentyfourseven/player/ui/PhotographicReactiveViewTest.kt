package com.codeframe78.twentyfourseven.player.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerStyle
import org.junit.Assert.*
import org.junit.Test

/** Native pixels only. Bundled decoder and live service acceptance use the video tests separately. */
class PhotographicReactiveViewTest {
    private fun pixels(style: IdleVisualizerStyle, frame: PhotographicFrame): IntArray {
        var result = IntArray(0)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = PhotographicReactiveView(ApplicationProvider.getApplicationContext())
            view.layout(0, 0, 1672, 941)
            view.present(style, frame, true)
            val bitmap = Bitmap.createBitmap(1672, 941, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            result = IntArray(1672 * 941)
            bitmap.getPixels(result, 0, 1672, 0, 0, 1672, 941)
            bitmap.recycle()
            view.release()
            assertEquals(android.view.View.GONE, view.visibility)
        }
        return result
    }

    private fun changed(a: IntArray, b: IntArray, left: Int, top: Int, right: Int, bottom: Int): Int {
        var count = 0
        for (y in top until bottom) for (x in left until right) if (a[y * 1672 + x] != b[y * 1672 + x]) count++
        return count
    }

    @Test fun boundedLaserSoftwareFallbackKeepsItsFullLightResolution() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = PhotographicReactiveView(ApplicationProvider.getApplicationContext())
            view.layout(0, 0, 480, 270)
            view.present(IdleVisualizerStyle.PulseQuilt,
                PhotographicFrame(light = List(4) { .8f }, phase = 1f), true)
            val output = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(output))
                val field = PhotographicReactiveView::class.java.getDeclaredField("lightBitmap").apply { isAccessible = true }
                val fallback = field.get(view) as Bitmap
                assertEquals(480, fallback.width)
                assertEquals(270, fallback.height)
                assertEquals(0, view.hardwareLaserFrames)
                assertTrue(Color.alpha(output.getPixel(98, 46)) > 0)
            } finally { output.recycle(); view.release() }
        }
    }

    @Test fun signalDeckHasIndependentMetersAndStereoInstrumentsWithoutEqBars() {
        val base = PhotographicFrame(channels = 2, leftDbfs = -40f, rightDbfs = -40f)
        val first = pixels(IdleVisualizerStyle.VinylPulse, base)
        val left = pixels(IdleVisualizerStyle.VinylPulse, base.copy(leftDbfs = -3f))
        assertTrue(changed(first, left, 214, 127, 778, 401) > 200)
        assertEquals(0, changed(first, left, 898, 127, 1457, 400))
        val right = pixels(IdleVisualizerStyle.VinylPulse, base.copy(rightDbfs = -3f))
        assertTrue(changed(first, right, 898, 127, 1457, 400) > 200)
        assertEquals(0, changed(first, right, 214, 127, 778, 401))
        val spectrum = pixels(IdleVisualizerStyle.VinylPulse, base.copy(bands = List(32) { if (it == 17) 1f else 0f }))
        assertArrayEquals("Signal Deck must not render spectrum EQ data", first, spectrum)
        val phase = pixels(IdleVisualizerStyle.VinylPulse, base.copy(correlation = -.9f))
        assertTrue("Phase gauge follows correlation", changed(first, phase, 217, 518, 510, 696) > 100)
        assertTrue("Balance carriage follows L/R levels", changed(first, right, 520, 550, 1160, 696) > 200)
        val attack = pixels(IdleVisualizerStyle.VinylPulse, base.copy(onset = 1f))
        assertTrue("Attack lamp follows measured transient", changed(first, attack, 1200, 650, 1245, 688) > 50)
        val clock = pixels(IdleVisualizerStyle.VinylPulse, base.copy(phase = 3f, cue = 99, laserTravel = 2f))
        assertArrayEquals("Console cannot animate from stage choreography or an ambient clock", first, clock)
        for (y in 0 until 941) for (x in 0 until 1672) {
            val inside = (x in 224..767 && y in 138..389) || (x in 908..1446 && y in 138..389) ||
                (x in 217..1449 && y in 518..695)
            if (!inside) assertEquals("instrument ink outside window at $x,$y", 0, Color.alpha(spectrum[y * 1672 + x]))
        }
    }

    @Test fun localizedLanternAndLensLightChangesAndReducedOutputIsDimmer() {
        val active = PhotographicFrame(light = listOf(.9f, .7f, .8f, .6f), phase = 1f, onset = .6f, lanternBass = .9f, lanternPulse = .9f, lanternWaterBass = .8f, laserBass = .9f, laserEnergy = .9f, accent = .8f)
        for (style in listOf(IdleVisualizerStyle.LanternDrift, IdleVisualizerStyle.PulseQuilt)) {
            val quiet = pixels(style, PhotographicFrame())
            val full = pixels(style, active)
            val reduced = pixels(style, active.copy(reduced = true))
            val moved = pixels(style, active.copy(phase = 1.5f, laserTravel = 1.5f))
            assertTrue(quiet.all { Color.alpha(it) == 0 })
            assertTrue(full.any { Color.alpha(it) > 0 })
            assertTrue(full.sumOf { Color.alpha(it).toLong() } > reduced.sumOf { Color.alpha(it).toLong() })
            assertTrue(changed(full, moved, 0, 0, 1672, 941) > 100)
            // Unpremultiplying near-transparent 8-bit edges may round RGB to white; they are not flashes.
            assertTrue("$style: no opaque or white flash; maxAlpha=${full.maxOf { Color.alpha(it) }}", full.all { Color.alpha(it) < 230 && (Color.alpha(it) < 16 || (it and 0x00FFFFFF) != 0x00FFFFFF) })
            if (style == IdleVisualizerStyle.LanternDrift) {
                assertTrue("sky and distant background untouched", full.take(1672 * 370).all { Color.alpha(it) == 0 })
                assertTrue(changed(quiet, full, 190, 390, 370, 530) > 100)
                assertTrue(changed(quiet, full, 190, 610, 370, 700) > 100)
            } else {
                for ((x, y) in PhotographicGeometry.laserFixtures) {
                    assertTrue("light must originate at measured lens $x,$y", Color.alpha(full[y.toInt() * 1672 + x.toInt()]) > 0)
                }
                assertTrue("Lasers reach the left screen edge", (0 until 941).any { y -> Color.alpha(full[y * 1672]) > 0 })
                assertTrue("Lasers reach the right screen edge", (0 until 941).any { y -> Color.alpha(full[y * 1672 + 1671]) > 0 })
                assertTrue("Lasers reach the top screen edge", (0 until 1672).any { x -> Color.alpha(full[x]) > 0 })
                assertTrue("Lasers reach the foreground screen edge", (0 until 1672).any { x -> Color.alpha(full[940 * 1672 + x]) > 0 })
            }
        }
    }

    @Test fun allFourPaperLanternsAndTheirReflectionsPulseWithBass() {
        val quiet = PhotographicFrame(light = List(4) { .3f }, phase = 1f)
        val low = pixels(IdleVisualizerStyle.LanternDrift, quiet)
        val bass = pixels(IdleVisualizerStyle.LanternDrift, quiet.copy(lanternBass = 1f, lanternPulse = 1f))
        assertTrue(bass.sumOf { Color.alpha(it).toLong() } > low.sumOf { Color.alpha(it).toLong() } * 4)
        for (p in PhotographicGeometry.lanterns) {
            assertTrue("Every paper shade responds to bass", changed(low, bass,
                (p[0] - p[2] / 2).toInt(), (p[1] - p[3] / 2).toInt(),
                (p[0] + p[2] / 2).toInt(), (p[1] + p[3] / 2).toInt()) > 100)
            assertTrue("Reflected bass illumination moves with the water", changed(low, bass,
                (p[0] - p[2] / 2).toInt(), p[4].toInt(),
                (p[0] + p[2] / 2).toInt(), minOf(941, (p[4] + p[3]).toInt())) > 100)
        }
    }

    @Test fun stalePcmSettlesToNoNativeLightAndReentryClearsOutput() {
        val drive = PhotographicAudioDrive()
        val audio = AudioVisualizationState(sampledAtElapsedRealtimeMs = 1000, energy = .8f, channelCount = 2,
            calibratedBandDbfs = List(4) { -6f })
        var frame = drive.update(audio, 1000, .1f, false)
        repeat(180) { frame = drive.update(audio, 2000, .1f, false) }
        for (style in listOf(IdleVisualizerStyle.LanternDrift, IdleVisualizerStyle.PulseQuilt)) {
            assertTrue(pixels(style, frame).all { Color.alpha(it) == 0 })
        }
        drive.reset()
        assertEquals(PhotographicFrame(), drive.update(AudioVisualizationState(), 2000, .1f, false))
    }
    @Test fun energeticLaserPatternsFillTheStageAndRemainDistinct() {
        val active = PhotographicFrame(light = listOf(.9f, .8f, .9f, .8f), phase = .8f,
            fan = List(4) { .7f }, accent = .8f)
        val fan = pixels(IdleVisualizerStyle.PulseQuilt, active)
        assertTrue("Bright saturated light must cover the stage", fan.count { Color.alpha(it) >= 16 } > 45000)
        assertTrue("Laser cores and lenses must have visible impact", fan.maxOf { Color.alpha(it) } > 140)
        for (cue in listOf(8, 16, 24)) {
            val pattern = pixels(IdleVisualizerStyle.PulseQuilt, active.copy(cue = cue))
            assertTrue("Fan, ribbon, tunnel and curtain must differ visibly", changed(fan, pattern, 80, 170, 1592, 800) > 15000)
            assertTrue("Every pattern stays translucent and avoids visible white flashes", pattern.all {
                Color.alpha(it) < 230 && (Color.alpha(it) < 16 || (it and 0x00FFFFFF) != 0x00FFFFFF)
            })
        }
    }

    @Test fun laserPixelsFollowMeasuredDriveInsteadOfTheAmbientClock() {
        val held = PhotographicFrame(light = List(4) { .4f }, laserTravel = .8f, laserBass = .1f)
        val first = pixels(IdleVisualizerStyle.PulseQuilt, held)
        assertArrayEquals("Ambient clock cannot run a laser routine", first,
            pixels(IdleVisualizerStyle.PulseQuilt, held.copy(phase = 4f)))
        val kick = pixels(IdleVisualizerStyle.PulseQuilt, held.copy(laserLight = List(4) { .9f },
            laserTravel = 1.8f, laserBass = .9f, laserEnergy = .8f, accent = 1f))
        assertTrue("Bass and attack must change beam geometry", changed(first, kick, 0, 0, 1672, 941) > 30000)
        assertTrue("Measured attack must visibly increase emitted light", kick.sumOf { Color.alpha(it).toLong() } >
            first.sumOf { Color.alpha(it).toLong() } * 3)
    }

    @Test fun tunnelRayAttenuatesWithDistanceEvenAcrossTheFanAxis() {
        val image = pixels(IdleVisualizerStyle.PulseQuilt, PhotographicFrame(
            light = listOf(1f, 0f, 0f, 0f), phase = .8f, laserTravel = 1.68f, cue = 16, accent = 1f))
        // The sixth tunnel ray from the upper-left lens travels almost horizontally.
        // It diverges strongly from the downward fan reference used by the old gradient.
        val angle = Math.PI + 1.68
        val dx = (836.0 + kotlin.math.cos(angle) * 530.0 - 340).toFloat()
        val dy = (440.0 + kotlin.math.sin(angle) * 230.0 - 160).toFloat()
        val exit = PhotographicGeometry.rayExitFactor(340f, 160f, dx, dy)
        val endX = 340.0 + dx * exit
        val endY = 160.0 + dy * exit
        fun alphaAt(fraction: Double): Int {
            val x = (340 + (endX - 340) * fraction).toInt()
            val y = (160 + (endY - 160) * fraction).toInt()
            var strongest = 0
            for (dy in -3..3) for (dx in -3..3) strongest = maxOf(strongest, Color.alpha(image[(y+dy)*1672+x+dx]))
            return strongest
        }
        assertTrue("Divergent tunnel ray must attenuate toward its endpoint", alphaAt(.3) > alphaAt(.85) + 10)
    }

}
