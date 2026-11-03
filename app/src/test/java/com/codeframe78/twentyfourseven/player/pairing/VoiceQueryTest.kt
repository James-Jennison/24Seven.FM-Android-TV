package com.codeframe78.twentyfourseven.player.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceQueryTest {
    @Test fun plainWordsSearchSongs() = assertEquals(VoiceQuery(CatalogField.Title, "peter gunn"), parseVoiceQuery("Peter Gunn".lowercase()))
    @Test fun byArtistRanksByArtist() = assertEquals(VoiceQuery(CatalogField.Title, "Peter Gunn", "Henry Mancini"), parseVoiceQuery("Peter Gunn by Henry Mancini"))
    @Test fun leadingVerbsAreIgnored() = assertEquals(VoiceQuery(CatalogField.Title, "Kung", "Bulletproof Monk"), parseVoiceQuery("request Kung by Bulletproof Monk"))
    @Test fun fieldWordsPickTheField() {
        assertEquals(VoiceQuery(CatalogField.Artist, "Hans Zimmer"), parseVoiceQuery("artist Hans Zimmer"))
        assertEquals(VoiceQuery(CatalogField.Album, "Godzilla"), parseVoiceQuery("search for album Godzilla"))
        assertEquals(VoiceQuery(CatalogField.Genre, "jazz"), parseVoiceQuery("genre jazz"))
    }
    @Test fun anArtistNamedByIsNotSplitWhenFieldIsArtist() = assertEquals(VoiceQuery(CatalogField.Artist, "Standby by Me"), parseVoiceQuery("artist Standby by Me"))
    @Test fun tooShortOrEmptyIsNothing() { assertNull(parseVoiceQuery("")); assertNull(parseVoiceQuery("a")) }

    @Test fun rankingPutsArtistMatchesFirstAndKeepsOrder() {
        val a = CatalogEntry(CatalogTarget.Album("1"), "Peter Gunn - Other", null, null)
        val b = CatalogEntry(CatalogTarget.Album("2"), "Peter Gunn", "Henry Mancini", null)
        val c = CatalogEntry(CatalogTarget.Album("3"), "Gunn", "Henry Mancini", null)
        assertEquals(listOf(b, c, a), rankByArtist(listOf(a, b, c), "henry mancini"))
        assertEquals(listOf(a, b, c), rankByArtist(listOf(a, b, c), null))
    }
}
