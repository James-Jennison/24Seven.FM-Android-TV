package com.codeframe78.twentyfourseven.player.ui

import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerStyle
import org.junit.Assert.*
import org.junit.Test

class ProceduralAudioDriveTest {
    @Test fun exactlyTenAdditiveStylesAndNoProtectedOriginals() {
        assertEquals(10,IdleVisualizerStyle.entries.count { it.isReferenceInspired() })
        assertFalse(IdleVisualizerStyle.LavaLamp.isReferenceInspired())
        assertFalse(IdleVisualizerStyle.PulseQuilt.isReferenceInspired())
    }
    @Test fun silenceInvalidDataAndResetAreSafe() {
        val drive=ProceduralAudioDrive()
        drive.update(AudioVisualizationState(energy=Float.NaN,channelCount=2,transient=Float.NaN,calibratedBandDbfs=listOf(Float.POSITIVE_INFINITY)),.1f,false)
        assertEquals(0f,drive.low,0f); assertEquals(0f,drive.hit,0f)
        drive.reset(); assertEquals(0f,drive.high,0f)
    }
    @Test fun independentBandsAndReducedIntensityAndDecay() {
        val loud=AudioVisualizationState(energy=.8f,channelCount=2,transient=.7f,calibratedBandDbfs=listOf(-6f,-6f,-60f,-60f))
        val drive=ProceduralAudioDrive(); val reduced=ProceduralAudioDrive()
        repeat(20) { drive.update(loud,.02f,false); reduced.update(loud,.02f,true) }
        assertTrue(drive.low>.9f); assertEquals(0f,drive.mid,0f); assertEquals(0f,drive.high,0f)
        assertEquals(drive.low*.25f,reduced.low,.0001f)
        assertTrue(drive.hit>.6f)
        repeat(100) { drive.update(AudioVisualizationState(),.02f,false) }
        assertTrue(drive.low<.001f && drive.hit<.001f)
    }
}
