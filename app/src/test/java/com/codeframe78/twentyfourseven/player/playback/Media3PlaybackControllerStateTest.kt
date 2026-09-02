package com.codeframe78.twentyfourseven.player.playback

import com.codeframe78.twentyfourseven.player.domain.PlaybackStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Media3PlaybackControllerStateTest {
    @Test
    fun `controller connection failure is error instead of silent connecting`() {
        assertEquals(
            PlaybackStatus.Error,
            disconnectedPlaybackStatus(
                playRequested = true,
                connectionError = "Unable to connect to playback service",
            ),
        )
    }

    @Test
    fun `pending controller connection remains connecting only without an error`() {
        assertEquals(
            PlaybackStatus.Connecting,
            disconnectedPlaybackStatus(playRequested = true, connectionError = null),
        )
    }

    @Test
    fun `missing station change command produces a visible error message`() {
        assertEquals(
            "Playback service cannot switch stations",
            stationChangeCommandError(canChangeMediaItems = false),
        )
        assertNull(stationChangeCommandError(canChangeMediaItems = true))
    }
}
