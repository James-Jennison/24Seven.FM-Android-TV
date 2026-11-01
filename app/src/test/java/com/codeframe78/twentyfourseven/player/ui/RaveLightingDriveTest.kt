package com.codeframe78.twentyfourseven.player.ui

import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState
import org.junit.Assert.*
import org.junit.Test

class RaveLightingDriveTest {
    private fun audio(t: Long, bass: Float, mids: Float = bass, highs: Float = bass, transient: Float = 0f) = AudioVisualizationState(
        sampledAtElapsedRealtimeMs = t, energy = maxOf(.01f, bass), channelCount = 2,
        calibratedBandDbfs = listOf(bass, bass, mids, highs).map { it * 54f - 60f },
        bands = List(32) { if (it >= 20) highs else mids }, transient = transient,
    )

    @Test fun regularMeasuredKicksEstablishTempoAndDuplicateMeasurementsDoNotAddBeats() {
        val drive = RaveLightingDrive()
        var frame = RaveLightingFrame()
        for (t in 20L..8020L step 20) {
            val hit = t % 500 < 40
            frame = drive.update(audio(t, if (hit) .95f else .22f, .4f, .3f, if (hit) .95f else 0f), t, .02f, false)
        }
        assertEquals(120f, frame.bpm, 3f)
        assertTrue(frame.confidence > .5f)
        val cue = frame.cue
        val last = audio(8020, .95f, transient = .95f)
        repeat(10) { frame = drive.update(last, 8020L + it * 20, .02f, false) }
        assertEquals(cue, frame.cue)
    }

    @Test fun quietPassageAndKickReleaseProduceDropButLoudEntryDoesNot() {
        val drive = RaveLightingDrive()
        val entry = drive.update(audio(1000, .95f, transient = 1f), 1000, .033f, false)
        assertEquals(0f, entry.drop, 0f)
        assertEquals(0f, entry.white, 0f)
        drive.reset()
        var calm = RaveLightingFrame()
        for (t in 1000L..5000L step 20) calm = drive.update(audio(t, .08f), t, .02f, false)
        val peak = drive.update(audio(5020, .95f, transient = 1f), 5020, .02f, false)
        assertEquals(RaveSection.QUIET, calm.section)
        assertEquals(RaveSection.DROP, peak.section)
        assertTrue(peak.drop > .9f && peak.white > .9f && peak.kick > .8f)
    }

    @Test fun risingEnergyArmsBuildAndFallsBackAfterSustainedQuiet() {
        val drive = RaveLightingDrive()
        var maximum = 0f
        for (t in 1000L..9000L step 20) {
            val level = .15f + ((t - 1000) / 8000f) * .7f
            val frame = drive.update(audio(t, level), t, .02f, false)
            maximum = maxOf(maximum, frame.build)
        }
        assertTrue(maximum > .28f)
        var quiet = RaveLightingFrame()
        for (t in 9020L..18000L step 20) quiet = drive.update(audio(t, .05f), t, .02f, false)
        assertEquals(RaveSection.QUIET, quiet.section)
        assertTrue(quiet.energy < .1f && quiet.build < .01f)
    }

    @Test fun spectrumSeparatesMovementFromFineAccents() {
        val low = RaveLightingDrive(); val upper = RaveLightingDrive()
        var a = RaveLightingFrame(); var b = RaveLightingFrame()
        for (t in 1000L..3000L step 20) {
            a = low.update(audio(t, .85f, .1f, .1f), t, .02f, false)
            b = upper.update(audio(t, .1f, .85f, .85f), t, .02f, false)
        }
        assertTrue(a.bass > b.bass)
        assertTrue(b.highs > a.highs && b.motion > a.motion)
    }

    @Test fun midBandChangesColourOnMeasuredMusicalBoundaries() {
        val lower = RaveLightingDrive(); val stronger = RaveLightingDrive()
        var a = RaveLightingFrame(); var b = RaveLightingFrame()
        for (t in 20L..8020L step 20) {
            val hit = t % 500 < 40
            val bass = if (hit) .95f else .22f
            a = lower.update(audio(t, bass, .1f, .2f, if (hit) 1f else 0f), t, .02f, false)
            b = stronger.update(audio(t, bass, .9f, .2f, if (hit) 1f else 0f), t, .02f, false)
        }
        assertNotEquals(a.palette, b.palette)
        assertEquals(a.bpm, b.bpm, 3f)
    }

    @Test fun staleFutureAndInvalidAudioResetTheShowWithoutInventingOnsets() {
        val drive = RaveLightingDrive()
        drive.update(audio(1000, .9f), 1000, .033f, false)
        assertEquals(0f, drive.update(audio(1000, .9f), 1501, .033f, false).energy, 0f)
        assertEquals(0f, drive.update(audio(2000, .9f), 1900, .033f, false).energy, 0f)
        val invalid = audio(2000, .9f).copy(energy = Float.NaN)
        assertEquals(0f, drive.update(invalid, 2000, Float.NaN, false).energy, 0f)
        val resumed = drive.update(audio(3000, .95f, transient = 1f), 3000, .033f, false)
        assertEquals(0f, resumed.kick, 0f)
        assertEquals(0f, resumed.bpm, 0f)
    }

    @Test fun reducedModeRemovesWhiteBurstsAndReducesLightAndMovement() {
        val full = RaveLightingDrive(); val reduced = RaveLightingDrive()
        for (t in 1000L..5000L step 20) {
            full.update(audio(t, .08f), t, .02f, false)
            reduced.update(audio(t, .08f), t, .02f, true)
        }
        val a = full.update(audio(5020, .95f, transient = 1f), 5020, .02f, false)
        val b = reduced.update(audio(5020, .95f, transient = 1f), 5020, .02f, true)
        assertTrue(a.white > 0f)
        assertEquals(0f, b.white, 0f)
        assertTrue(b.energy < a.energy && b.motion < a.motion && b.drop < a.drop)
    }
}
