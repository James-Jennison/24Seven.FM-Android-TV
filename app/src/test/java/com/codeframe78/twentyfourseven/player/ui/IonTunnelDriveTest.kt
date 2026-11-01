package com.codeframe78.twentyfourseven.player.ui

import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState
import org.junit.Assert.*
import org.junit.Test

class IonTunnelDriveTest {
    private val audio = AudioVisualizationState(sampledAtElapsedRealtimeMs = 1000, channelCount = 2, energy = .7f,
        leftDbfs = -12f, rightDbfs = -18f, calibratedBandDbfs = listOf(-9f,-12f,-30f,-40f), transient = .6f)
    @Test fun realPcmDrivesTravelAndStaleOrFutureAudioFreezesIt() {
        val drive = IonTunnelDrive()
        val active = drive.update(audio,1000,.05f,false)
        assertTrue(active.travel > 0f); assertTrue(active.bass > 0f)
        val stale = drive.update(audio,2000,.05f,false)
        assertEquals(active.travel,stale.travel,0f); assertTrue(stale.bass < active.bass)
        assertEquals(active.travel,drive.update(audio,999,.05f,false).travel,0f)
    }
    @Test fun reducedFlightIsSlowerAndMalformedInputCannotPoisonUniforms() {
        val normal = IonTunnelDrive().update(audio,1000,.05f,false)
        val reduced = IonTunnelDrive().update(audio,1000,.05f,true)
        assertTrue(reduced.travel < normal.travel * .25f)
        val bad = IonTunnelDrive().update(audio.copy(leftDbfs=Float.NaN,rightDbfs=Float.POSITIVE_INFINITY,
            calibratedBandDbfs=listOf(Float.NaN),transient=Float.NaN),1000,Float.NaN,false)
        assertEquals(IonTunnelFrame(),bad)
    }
}
