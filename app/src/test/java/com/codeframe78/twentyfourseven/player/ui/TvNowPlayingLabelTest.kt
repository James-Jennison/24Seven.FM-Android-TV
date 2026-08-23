package com.codeframe78.twentyfourseven.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TvNowPlayingLabelTest {
    @Test
    fun `reorders station artist track metadata as track artist`() {
        assertEquals(
            "On Golden Pond - Dave Grusin",
            tvNowPlayingTrackArtistLabel("Dave Grusin - On Golden Pond"),
        )
    }

    @Test
    fun `preserves a single field and leaves absent metadata blank`() {
        assertEquals("Live radio", tvNowPlayingTrackArtistLabel(" Live radio "))
        assertEquals(null, tvNowPlayingTrackArtistLabel(null))
    }
}
