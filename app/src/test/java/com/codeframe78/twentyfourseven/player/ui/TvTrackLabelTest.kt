package com.codeframe78.twentyfourseven.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TvTrackLabelTest {
    @Test
    fun `renders track and artist when both are supplied`() {
        assertEquals(
            "Main Theme / Jane Composer",
            tvTrackLabel("Main Theme", "Jane Composer"),
        )
    }

    @Test
    fun `keeps the title alone when artist metadata is absent or duplicated`() {
        assertEquals("Main Theme", tvTrackLabel("Main Theme", null))
        assertEquals("Main Theme", tvTrackLabel("Main Theme", " main theme "))
    }
}
