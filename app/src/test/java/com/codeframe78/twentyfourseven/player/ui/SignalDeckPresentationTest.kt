package com.codeframe78.twentyfourseven.player.ui

import org.junit.Assert.*
import org.junit.Test

class SignalDeckPresentationTest {
    @Test fun balanceUsesCalibratedChannelDifferenceAndCentersMonoOrMissingPcm() {
        val frame = PhotographicFrame(channels = 2, leftDbfs = -20f, rightDbfs = -8f)
        assertEquals(12f, SignalDeckPresentation.balanceDb(frame), 0f)
        assertEquals(-12f, SignalDeckPresentation.balanceDb(frame.copy(leftDbfs = -8f, rightDbfs = -20f)), 0f)
        assertEquals(24f, SignalDeckPresentation.balanceDb(frame.copy(leftDbfs = -60f, rightDbfs = 0f)), 0f)
        for (channels in listOf(0, 1)) assertEquals(0f, SignalDeckPresentation.balanceDb(frame.copy(channels = channels)), 0f)
    }

    @Test fun correlationMapsActualStereoPhaseAndMalformedInputsAreBounded() {
        assertEquals(180f, SignalDeckPresentation.correlationAngle(PhotographicFrame(correlation = -1f)), 0f)
        assertEquals(360f, SignalDeckPresentation.correlationAngle(PhotographicFrame(correlation = 1f)), 0f)
        assertEquals(270f, SignalDeckPresentation.correlationAngle(PhotographicFrame(correlation = Float.NaN)), 0f)
        assertEquals(0f, SignalDeckPresentation.balanceDb(PhotographicFrame(channels = 2, leftDbfs = Float.NaN, rightDbfs = Float.POSITIVE_INFINITY)), 0f)
        assertEquals(0f, SignalDeckPresentation.attack(PhotographicFrame(onset = 1f)), 0f)
        assertEquals(0f, SignalDeckPresentation.attack(PhotographicFrame(channels = 2, onset = Float.NaN)), 0f)
    }
}
