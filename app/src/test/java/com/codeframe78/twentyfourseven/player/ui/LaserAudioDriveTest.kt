package com.codeframe78.twentyfourseven.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaserAudioDriveTest {
    @Test fun loudSustainedAudioRetainsAttackHeadroom() {
        assertEquals(.7f, laserAudioDrive(1f, 1f, 0f), .00001f)
        assertEquals(1f, laserAudioDrive(1f, 1f, 1f), .00001f)
    }

    @Test fun silenceAndFullScaleStayBounded() {
        assertEquals(0f, laserAudioDrive(0f, 0f, 0f), 0f)
        for (local in 0..10) for (bass in 0..10) for (attack in 0..10) {
            val drive = laserAudioDrive(local / 10f, bass / 10f, attack / 10f)
            assertTrue(drive in 0f..1f)
            if (attack < 10) assertTrue(
                laserAudioDrive(local / 10f, bass / 10f, (attack + 1) / 10f) > drive,
            )
        }
    }
}
