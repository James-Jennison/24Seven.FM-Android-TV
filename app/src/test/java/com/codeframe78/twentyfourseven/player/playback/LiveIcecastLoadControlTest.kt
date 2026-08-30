package com.codeframe78.twentyfourseven.player.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveIcecastLoadControlTest {
    @Test
    fun `uses the approved continuous stream buffer profile`() {
        assertEquals(12_000, LiveIcecastLoadControl.profile.minBufferMillis)
        assertEquals(45_000, LiveIcecastLoadControl.profile.maxBufferMillis)
        assertEquals(1_500, LiveIcecastLoadControl.profile.startupBufferMillis)
        assertEquals(3_000, LiveIcecastLoadControl.profile.rebufferBufferMillis)
        LiveIcecastLoadControl.create()
    }
}
