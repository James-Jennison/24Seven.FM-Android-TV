package com.codeframe78.twentyfourseven.player.ui

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ConcertLightingTest {
    @Test fun audioChangesDirectionAndLaserStrengthAtIdenticalClockTime() {
        val quiet = concertAudioAim(1f, 0, 0f, 0f)
        assertEquals(quiet + .9f, concertAudioAim(1f, 0, 1f, 0f), .00001f)
        assertEquals(quiet - .45f, concertAudioAim(1f, 0, 0f, 1f), .00001f)
        assertEquals(0f, concertLaserStrength(0f, 0f, 0f), 0f)
        assertTrue(concertLaserStrength(.3f, 1f, 1f) > concertLaserStrength(.3f, 0f, 0f))
        assertTrue(concertLaserStrength(1f, 1f, 1f) <= 1f)
        assertTrue(concertLaserStrength(1f, 1f, 1f) > concertLaserStrength(1f, 0f, 1f))
        assertEquals(1f, concertLaserStrength(2f, 2f, 2f), .00001f)
        assertTrue(concertLaserStrength(.1f, 0f, 0f) > .2f)
    }
    @Test fun cuesRemainBoundedContinuousAndSelective() {
        val period = (Math.PI * 2).toFloat()
        var accentFrames = 0
        repeat(1000) { frame ->
            val time = period * frame / 1000f
            if (concertLaserAccent(time) > .01f) accentFrames++
            repeat(6) { fixture ->
                val activity = concertActivity(time, fixture)
                assertTrue(activity in 0f..1f)
                assertTrue(concertAim(time, fixture) in -4f..4f)
                assertEquals(activity, concertActivity(time + period, fixture), .00001f)
                assertTrue(kotlin.math.abs(concertAim(time + .001f, fixture) - concertAim(time, fixture)) < .02f)
            }
            assertTrue((0..5).any { concertActivity(time, it) < .1f })
            assertTrue((0..5).any { concertActivity(time, it) > .2f })
        }
        assertTrue(accentFrames in 1..250)
    }
}
