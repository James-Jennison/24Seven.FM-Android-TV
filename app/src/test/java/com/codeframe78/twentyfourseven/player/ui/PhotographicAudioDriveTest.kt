package com.codeframe78.twentyfourseven.player.ui

import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerStyle
import org.junit.Assert.*
import org.junit.Test

class PhotographicAudioDriveTest {
    private val audio = AudioVisualizationState(sampledAtElapsedRealtimeMs = 1000L,
        energy = .7f, channelCount = 2, leftDbfs = -12f, rightDbfs = -42f,
        stereoCorrelation = -.8f, bands = List(32) { if (it == 9) .75f else 0f },
        calibratedBandDbfs = listOf(-6f, -60f, -30f, -60f), transient = .8f)

    @Test fun calibratedChannelsAndAllBandsStayIndependent() {
        val drive = PhotographicAudioDrive()
        val f = drive.update(audio, 1000L, .1f, false)
        assertEquals(-12f, f.leftDbfs, 0f); assertEquals(-42f, f.rightDbfs, 0f)
        assertEquals(-.8f, f.correlation, 0f)
        assertEquals(.75f, f.bands[9], 0f)
        assertEquals(1, f.bands.count { it > 0f })
        assertTrue(f.light[0] > f.light[2]); assertEquals(0f, f.light[1], 0f)
        val swap = drive.update(audio.copy(leftDbfs = -50f, rightDbfs = -3f,
            bands = List(32) { if (it == 31) .9f else 0f }), 1000L, .1f, false)
        assertEquals(-50f, swap.leftDbfs, 0f); assertEquals(-3f, swap.rightDbfs, 0f)
        assertEquals(0f, swap.bands[9], 0f); assertEquals(.9f, swap.bands[31], 0f)
        assertTrue(swap.leftPeak > swap.leftDbfs)
        assertEquals(-3f, swap.rightPeak, 0f)
    }

    @Test fun staleFutureAndSilentPcmFreezePhaseAndExtinguishLight() {
        for (invalid in listOf(audio.copy(sampledAtElapsedRealtimeMs = 499),
            audio.copy(sampledAtElapsedRealtimeMs = 1001), audio.copy(energy = 0f, leftDbfs = -60f, rightDbfs = -60f))) {
            val drive = PhotographicAudioDrive()
            val active = drive.update(audio, 1000L, .1f, false)
            var quiet = drive.update(invalid, 1000L, .1f, false)
            assertEquals(active.phase, quiet.phase, 0f)
            assertEquals(if (invalid.sampledAtElapsedRealtimeMs == 1000L) 2 else 0, quiet.channels); assertEquals(-60f, quiet.leftDbfs, 0f)
            assertTrue(quiet.light[0] < active.light[0])
            repeat(180) { quiet = drive.update(invalid, 1000L, .1f, false) }
            assertTrue(quiet.light.all { it == 0f }); assertEquals(-60f, quiet.leftPeak, 0f)
            assertEquals(0f, quiet.onset, 0f)
        }
    }

    @Test fun malformedInputIsBoundedAndResetClearsHistory() {
        val drive = PhotographicAudioDrive()
        val f = drive.update(audio.copy(leftDbfs = Float.NaN, rightDbfs = 8f,
            stereoCorrelation = Float.POSITIVE_INFINITY, bands = listOf(Float.NaN, 9f)), 1000, Float.NaN, true)
        assertEquals(-60f, f.leftDbfs, 0f); assertEquals(0f, f.rightDbfs, 0f)
        assertEquals(0f, f.correlation, 0f); assertEquals(32, f.bands.size)
        assertEquals(0f, f.bands[0], 0f); assertEquals(1f, f.bands[1], 0f)
        assertEquals(0f, f.phase, 0f)
        drive.reset()
        assertEquals(PhotographicFrame(), drive.update(AudioVisualizationState(), 1000, .1f, false))
    }

    @Test fun normalAndReducedKeepFixedCameraAndRawCalibration() {
        for (style in listOf(IdleVisualizerStyle.LanternDrift, IdleVisualizerStyle.VinylPulse, IdleVisualizerStyle.PulseQuilt)) {
            for (reduced in listOf(false, true)) {
                val video = VideoAudioDrive().update(style, audio, .1f, reduced, 1000L)
                assertEquals(1f, video.scaleX, 0f); assertEquals(1f, video.scaleY, 0f)
                assertEquals(0f, video.shear, 0f); assertEquals(1f, video.alpha, 0f)
                assertTrue(video.moving)
                assertFalse(VideoAudioDrive().update(style, audio, .1f, reduced, 1501L).moving)
                assertEquals(-12f, PhotographicAudioDrive().update(audio, 1000, .1f, reduced).leftDbfs, 0f)
            }
        }
        assertEquals(210f, PhotographicGeometry.meterAngle(-60f), 0f)
        assertEquals(270f, PhotographicGeometry.meterAngle(-30f), 0f)
        assertEquals(330f, PhotographicGeometry.meterAngle(0f), 0f)
    }
    @Test fun laserPatternsAdvanceOnlyFromDistinctMeasuredAttacks() {
        val drive = PhotographicAudioDrive()
        var frame = PhotographicFrame()
        repeat(121) { index ->
            val stamp = 1000L + index * 100L
            frame = drive.update(audio.copy(sampledAtElapsedRealtimeMs = stamp,
                transient = if (index % 4 == 0) .4f else .002f), stamp, .1f, false)
        }
        assertTrue("Measured attacks must change choreography", frame.cue >= 16)
        val hits = frame.cue
        repeat(30) { frame = drive.update(audio.copy(sampledAtElapsedRealtimeMs = 13000L,
            transient = .9f), 13000L, .033f, false) }
        assertEquals("Duplicate PCM snapshots cannot invent cue events", hits, frame.cue)
        val phase = frame.phase
        val aim = frame.aim
        frame = drive.update(audio.copy(sampledAtElapsedRealtimeMs = 13000L), 14000L, .1f, false)
        assertEquals(hits, frame.cue)
        assertEquals(phase, frame.phase, 0f)
        assertEquals(aim, frame.aim)
        repeat(60) { frame = drive.update(AudioVisualizationState(), 14000L, .1f, false) }
        val resumed = drive.update(audio.copy(sampledAtElapsedRealtimeMs = 20000L), 20000L, .033f, false)
        assertEquals("Reentry cannot invent a cue", hits, resumed.cue)
        assertTrue("Reentry cannot revive an old attack pulse", resumed.accent < .001f)
        drive.reset()
        assertEquals(PhotographicFrame(), drive.update(AudioVisualizationState(), 14000L, .1f, false))
    }

    @Test fun steadyMusicDoesNotRunAnAutonomousLaserPattern() {
        val drive = PhotographicAudioDrive()
        var f = PhotographicFrame()
        repeat(100) { i ->
            val stamp = 1000L + i * 33
            f = drive.update(audio.copy(sampledAtElapsedRealtimeMs = stamp, transient = .002f), stamp, .033f, false)
        }
        assertEquals(0f, f.laserTravel, 0f)
        assertEquals(0, f.cue)
        assertTrue("Lantern ambient phase remains separate", f.phase > 0f)
        val held = f.laserTravel
        repeat(10) { f = drive.update(audio.copy(sampledAtElapsedRealtimeMs = 4267L), 4267L, .033f, false) }
        assertEquals("Repeated capture cannot drive motion", held, f.laserTravel, 0f)
    }

    @Test fun measuredBassAttacksDriveFastLightAndTravelWithQuietContrast() {
        val drive = PhotographicAudioDrive()
        val quiet = audio.copy(leftDbfs = -45f, rightDbfs = -45f,
            calibratedBandDbfs = List(4) { -48f }, transient = .002f)
        drive.update(quiet, 1000, .033f, false)
        val loud = quiet.copy(sampledAtElapsedRealtimeMs = 1100, leftDbfs = -9f, rightDbfs = -15f,
            calibratedBandDbfs = listOf(-6f, -12f, -36f, -42f), transient = .8f)
        val kick = drive.update(loud, 1100, .033f, false)
        assertTrue(kick.laserTravel > 1f)
        assertTrue(kick.accent > .5f)
        assertTrue("Fast attack remains clearly visible", kick.laserLight[0] > .7f)
        assertTrue(kick.laserLight[0] > kick.laserLight[3] * 10)
        var settled = kick
        repeat(20) { i ->
            val stamp = 1200L + i * 33
            settled = drive.update(quiet.copy(sampledAtElapsedRealtimeMs = stamp), stamp, .033f, false)
        }
        assertTrue(settled.laserLight[0] < kick.laserLight[0] * .01f)
        assertEquals(kick.laserTravel, settled.laserTravel, 0f)
        val resumed = drive.update(loud.copy(sampledAtElapsedRealtimeMs = 4000L), 4000L, .033f, false)
        assertEquals("A gap is not an invented movement impulse", settled.laserTravel, resumed.laserTravel, 0f)
    }

    @Test fun projectedLasersContinueToAllScreenEdges() {
        for ((dx, dy) in listOf(1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f, 1f to 1f, -1f to -1f)) {
            val t = PhotographicGeometry.rayExitFactor(836f, 470f, dx, dy)
            val x = 836f + dx * t; val y = 470f + dy * t
            assertTrue(t.isFinite() && t > 0f)
            assertTrue("Ray ends beyond a full-screen boundary", x <= -8f || x >= 1680f || y <= -8f || y >= 949f)
        }
        assertEquals(0f, PhotographicGeometry.rayExitFactor(836f, 470f, 0f, 0f), 0f)
    }

}
