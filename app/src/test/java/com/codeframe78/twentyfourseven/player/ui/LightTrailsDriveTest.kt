package com.codeframe78.twentyfourseven.player.ui

import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState
import org.junit.Assert.*
import org.junit.Test

class LightTrailsDriveTest {
    private val audio = AudioVisualizationState(sampledAtElapsedRealtimeMs = 1000, channelCount = 2, energy = .7f,
        leftDbfs = -12f, rightDbfs = -18f, calibratedBandDbfs = listOf(-9f,-12f,-30f,-40f), transient = .6f)
    @Test fun realPcmDrivesTravelAndStaleOrFutureAudioFreezesIt() {
        val drive = LightTrailsDrive()
        val active = drive.update(audio,1000,.05f,false)
        assertTrue(active.travel > 0f); assertTrue(active.bass > 0f)
        val stale = drive.update(audio,2000,.05f,false)
        assertEquals(active.travel,stale.travel,0f); assertTrue(stale.bass < active.bass)
        assertEquals(active.travel,drive.update(audio,999,.05f,false).travel,0f)
    }
    @Test fun reducedFlightIsSlowerAndMalformedInputCannotPoisonUniforms() {
        val normal = LightTrailsDrive().update(audio,1000,.05f,false)
        val reduced = LightTrailsDrive().update(audio,1000,.05f,true)
        assertTrue(reduced.travel < normal.travel * .25f)
        val bad = LightTrailsDrive().update(audio.copy(leftDbfs=Float.NaN,rightDbfs=Float.POSITIVE_INFINITY,
            calibratedBandDbfs=listOf(Float.NaN),transient=Float.NaN),1000,Float.NaN,false)
        assertEquals(LightTrailsFrame(),bad)
    }
    @Test fun bassNotesHaveDistinctAttacksAndGapsOnAContinuousTrack() {
        val drive=LightTrailsDrive()
        var now=1000L
        fun step(db: Float, count: Int): LightTrailsFrame {
            repeat(count) {
                now+=20
                drive.update(audio.copy(sampledAtElapsedRealtimeMs=now,
                    calibratedBandDbfs=listOf(db,db,-18f,-22f),transient=0f),now,.02f,false)
            }
            return drive.frame
        }
        step(-36f,100)
        repeat(6) {
            val hit=step(-10f,4)
            val gap=step(-42f,14)
            assertTrue("Each bass note must punch through: $hit",hit.bass>.65f)
            assertTrue("Gaps must visibly release: $gap",gap.bass<hit.bass*.25f)
            assertTrue("Sky independently follows mid/high detail",gap.sky>.4f)
        }
        val held=drive.frame
        val stale=drive.update(audio,now+1000,.05f,false)
        assertEquals(held.travel,stale.travel,0f)
        assertEquals(held.sweep,stale.sweep,0f)
    }
    @Test fun entryDoesNotInventBassAttackAndRepeatedSampleDoesNotRetrigger() {
        val drive=LightTrailsDrive()
        val entry=drive.update(audio,1000,.02f,false)
        assertTrue("Entry should seed the baseline",entry.bass<.5f)
        drive.update(audio.copy(sampledAtElapsedRealtimeMs=1020,calibratedBandDbfs=listOf(-42f,-42f,-18f,-22f)),1020,.02f,false)
        val note=audio.copy(sampledAtElapsedRealtimeMs=1040,calibratedBandDbfs=listOf(-8f,-8f,-18f,-22f),transient=0f)
        drive.update(note,1040,.02f,false)
        repeat(4) { drive.update(note,1060+it*20L,.02f,false) }
        val attack=drive.frame.attack
        repeat(8) { drive.update(note,1140+it*20L,.02f,false) }
        assertTrue("Same PCM sample must decay instead of retrigger",drive.frame.attack<attack)
        drive.update(note,3000,.05f,false)
        val resumed=drive.update(audio.copy(sampledAtElapsedRealtimeMs=3020),3020,.02f,false)
        assertTrue("Resume must not invent a new full attack",resumed.attack<.5f)
    }
}
