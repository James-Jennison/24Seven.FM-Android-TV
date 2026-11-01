package com.codeframe78.twentyfourseven.player.ui

import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class LanternWaterMotionTest {
    private fun mesh(phase: Float, bass: Float = 1f, reduced: Boolean = false) =
        FloatArray((LanternWaterMotion.Columns + 1) * (LanternWaterMotion.Rows + 1) * 2).also {
            LanternWaterMotion.update(it, phase, bass, reduced)
        }

    @Test fun fixedShoreAndBordersRemainRegisteredAndWaveLoopIsContinuous() {
        val rest = mesh(0f)
        val moved = mesh(1.5f)
        val wrapped = mesh(6.2831855f)
        for (i in rest.indices step 2) {
            assertEquals(rest[i], wrapped[i], .003f)
            assertEquals(rest[i + 1], wrapped[i + 1], .003f)
            if (rest[i + 1] <= 360f || rest[i] == 0f || rest[i] == 1672f || rest[i + 1] == 941f) {
                assertEquals(rest[i], moved[i], 0f)
                assertEquals(rest[i + 1], moved[i + 1], 0f)
            }
        }
        assertTrue("Foreground waves must be visible", moved.indices.maxOf { abs(moved[it] - rest[it]) } > 5f)
    }

    @Test fun floatingLanternContactFollowsTheSameWaterField() {
        val cx = (170f + 405f) / 2f
        val waterline = 580f
        val x = LanternWaterMotion.lanternX(0, cx, waterline, 1f, .8f, false)
        val y = LanternWaterMotion.lanternY(0, cx, waterline, 1f, .8f, false)
        assertEquals(LanternWaterMotion.waterX(waterline, 1f, .8f, false), x - cx, .0001f)
        assertEquals(LanternWaterMotion.waterY(cx, waterline, 1f, .8f, false), y - waterline, .0001f)
        val topY = LanternWaterMotion.lanternY(0, cx, waterline - 100f, 1f, .8f, false)
        assertEquals("Paper and raft translate together", 100f, y - topY, .0001f)
    }

    @Test fun bassStrengthensWavesAndReducedIntensitySoftensThem() {
        val rest = mesh(0f)
        fun distance(values: FloatArray) = values.indices.sumOf { abs(values[it] - rest[it]).toDouble() }
        val quiet = distance(mesh(1f, 0f))
        val bass = distance(mesh(1f, 1f))
        val reduced = distance(mesh(1f, 1f, true))
        assertTrue(bass > quiet * 2)
        assertTrue(reduced < bass * .3)
    }

    @Test fun theWaterMeshNeverFoldsAcrossTheWaveCycle() {
        repeat(16) { step ->
            val v = mesh(step * 6.2831855f / 16)
            fun area(a: Int, b: Int, c: Int): Float =
                (v[b] - v[a]) * (v[c + 1] - v[a + 1]) - (v[b + 1] - v[a + 1]) * (v[c] - v[a])
            for (y in 0 until LanternWaterMotion.Rows) for (x in 0 until LanternWaterMotion.Columns) {
                val a = (y * (LanternWaterMotion.Columns + 1) + x) * 2
                val b = a + 2
                val c = a + (LanternWaterMotion.Columns + 1) * 2
                assertTrue("Upper triangle stays oriented", area(a, b, c) > 0f)
                assertTrue("Lower triangle stays oriented", area(b, c + 2, c) > 0f)
            }
        }
    }

    @Test fun lanternPulseComesFromBassRatherThanTrebleAndSettlesOnStaleAudio() {
        val drive = PhotographicAudioDrive()
        val treble = AudioVisualizationState(sampledAtElapsedRealtimeMs = 1000, channelCount = 2,
            energy = .7f, calibratedBandDbfs = listOf(-60f, -60f, -6f, -6f))
        assertEquals(0f, drive.update(treble, 1000, .04f, false).lanternBass, 0f)
        val bass = treble.copy(sampledAtElapsedRealtimeMs = 1100, calibratedBandDbfs = listOf(-6f, -12f, -60f, -60f))
        var frame = drive.update(bass, 1100, .04f, false)
        assertTrue(frame.lanternBass > .6f)
        val phase = frame.phase
        repeat(40) { frame = drive.update(AudioVisualizationState(), 2000, .1f, false) }
        assertEquals(0f, frame.lanternBass, 0f)
        assertEquals(phase, frame.phase, 0f)
    }
    @Test fun sustainedBassSettlesButTheNextBassHitRelightsThePaper() {
        val drive = PhotographicAudioDrive()
        var now = 1000L
        fun sample(db: Float): PhotographicFrame {
            now += 33
            return drive.update(AudioVisualizationState(sampledAtElapsedRealtimeMs = now, channelCount = 2,
                energy = .7f, calibratedBandDbfs = listOf(db, db, -60f, -60f)), now, .033f, false)
        }
        repeat(120) { sample(-12f) }
        assertTrue("Steady bass cannot become a constant bright pulse", sample(-12f).lanternPulse < .05f)
        repeat(12) { sample(-30f) }
        val before = sample(-30f)
        val hit = sample(-6f)
        assertTrue("Kick visibly changes shade brightness", hit.lanternPulse - before.lanternPulse > .7f)
        assertTrue("Kick cannot jerk the water amplitude", kotlin.math.abs(hit.lanternWaterBass - before.lanternWaterBass) < .03f)
    }

}
