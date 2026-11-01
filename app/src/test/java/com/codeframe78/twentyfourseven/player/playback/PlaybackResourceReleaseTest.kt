package com.codeframe78.twentyfourseven.player.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackResourceReleaseTest {
    @Test fun analysisIsClosedWhenEitherEarlierReleaseThrows() {
        for (failure in listOf("session", "player")) {
            val calls = mutableListOf<String>()
            fun release(name: String) { calls.add(name); if (name == failure) error("synthetic release failure") }
            try {
                releasePlaybackResources({ release("session") }, { release("player") }, { release("analysis") })
            } catch (_: IllegalStateException) { }
            assertEquals(listOf("session", "player", "analysis"), calls)
        }
    }
}
