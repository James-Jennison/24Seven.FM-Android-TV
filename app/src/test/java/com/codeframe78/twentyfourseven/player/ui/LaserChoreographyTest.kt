package com.codeframe78.twentyfourseven.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaserChoreographyTest {
    @Test fun hitsAreSeparatedAndDecayDuringSustainedAudio() {
        val motion = LaserChoreography()
        motion.update(.02f, 0f, 0f, .5f)
        motion.update(.02f, 1f, 0f, .5f)
        assertTrue(motion.bassHit > .9f)
        assertEquals(0f, motion.snareHit, 0f)
        repeat(60) { motion.update(.02f, 1f, 0f, .5f) }
        assertTrue(motion.bassHit < .01f)
        motion.update(.02f, 1f, 1f, .5f)
        assertTrue(motion.snareHit > .9f)
        repeat(30) { motion.update(.02f, 1f, 1f, .5f) }
        assertTrue(motion.snareHit < .01f)
    }

    @Test fun entryResetAndInvalidInputDoNotCreateHits() {
        val motion = LaserChoreography()
        motion.update(.02f, 1f, 1f, 1f)
        assertEquals(0f, motion.bassHit, 0f)
        assertEquals(0f, motion.snareHit, 0f)
        motion.reset()
        motion.update(Float.NaN, 1f, 1f, 1f)
        motion.update(.02f, Float.NaN, Float.POSITIVE_INFINITY, Float.NaN)
        assertEquals(0f, motion.clock, 0f)
    }

    @Test fun banksRemainCoordinatedAndLoopWithoutJump() {
        for (step in 0..100) {
            val t = step * .13f
            assertEquals(.16f, laserGroupCentre(t, 1) - laserGroupCentre(t, 0), .00001f)
            assertEquals(.16f, laserGroupCentre(t, 5) - laserGroupCentre(t, 4), .00001f)
            for (fixture in 0..7) {
                assertTrue(laserGroupCentre(t, fixture) in -3f..3f)
                assertEquals(laserGroupCentre(t, fixture),
                    laserGroupCentre(t + (Math.PI * 2).toFloat(), fixture), .0001f)
            }
        }
    }
}
