package com.codeframe78.twentyfourseven.player.pairing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Fixtures are excerpts of the public StreamingSoundtracks request pages (no personal data). */
class RequestCatalogTest {
    private val parser = CatalogParser()
    private val origin = "https://streamingsoundtracks.com/"
    private fun fixture(name: String) = javaClass.getResource("/pairing/$name")!!.readText()

    @Test fun albumPageYieldsTracksWithTrackRefs() {
        val album = parser.parseAlbum(fixture("album.html"), origin, "B00000JC79")
        assertEquals("Peter Gunn - Henry Mancini", album.title)
        // The station's disabled button carries no request link, so that track is not offered at all.
        assertEquals(2, album.tracks.size)
        val first = album.tracks.first()
        assertEquals("Peter Gunn", first.title)
        assertEquals("Henry Mancini", first.artist)
        assertEquals("B00000JC79:224947", first.trackRef)
        assertTrue(first.eligible)
        assertTrue(album.tracks.all { it.songId.isNotBlank() })
    }

    @Test fun albumPageForAnotherAlbumIdYieldsNothing() {
        assertTrue(parser.parseAlbum(fixture("album.html"), origin, "OTHER").tracks.isEmpty())
    }

    @Test fun searchPageYieldsAlbumsAndArtists() {
        val albums = parser.parseSearch(fixture("search.html"), origin)
        assertEquals(2, albums.size)
        assertEquals(CatalogTarget.Album("B075Z6BJB9"), albums.first().target)
        assertEquals("Mr. Robot: Volume 4", albums.first().subtitle)
        val artists = parser.parseSearch(fixture("artists.html"), origin)
        assertTrue(artists.isNotEmpty())
        assertEquals(CatalogTarget.Artist("Henry Mancini"), artists.first().target)
    }

    @Test fun linksToAnotherHostAreIgnored() {
        val html = """<table><tr><td><a href="https://evil.example/modules.php?name=Album&asin=X1">Bad</a></td></tr></table>"""
        assertTrue(parser.parseSearch(html, origin).isEmpty())
    }

    // Browser state machine

    private class FakeCatalog : RequestCatalog {
        val gate = HashMap<String, CompletableDeferred<Unit>>()
        var fail = false
        override suspend fun search(station: String, query: String, field: CatalogField): List<CatalogEntry> {
            gate[query]?.await()
            if (fail) throw IOException("down")
            return if (query == "none") emptyList() else listOf(CatalogEntry(CatalogTarget.Album("A-$query"), "Album $query", null, null))
        }
        override suspend fun albumsBy(station: String, artist: String) = listOf(CatalogEntry(CatalogTarget.Album("A1"), "By $artist", null, null))
        override suspend fun album(station: String, albumId: String) =
            CatalogAlbum("T $albumId", listOf(CatalogTrack(albumId, "7", "Song", "Who", "T $albumId", "3:00", true)))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun TestScope.browser(c: RequestCatalog = FakeCatalog()) = CatalogBrowser(c, "sst", this)

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun searchThenAlbumThenBackWalksUp() = runTest(StandardTestDispatcher()) {
        val b = browser()
        b.search("silent"); advanceUntilIdle()
        assertTrue(b.ui.value is CatalogUi.Entries)
        b.open((b.ui.value as CatalogUi.Entries).items.first()); advanceUntilIdle()
        val tracks = b.ui.value as CatalogUi.Tracks
        assertEquals("A-silent:7", tracks.tracks.single().trackRef)
        assertTrue(b.back()); assertTrue(b.ui.value is CatalogUi.Entries)
        assertTrue(b.back()); assertTrue(b.ui.value is CatalogUi.Query)
        assertFalse(b.back())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun shortQueryAndEmptyAndFailureAreExplained() = runTest(StandardTestDispatcher()) {
        val c = FakeCatalog(); val b = browser(c)
        b.search("a"); assertTrue((b.ui.value as CatalogUi.Query).notice != null)
        b.search("none"); advanceUntilIdle(); assertTrue(b.ui.value is CatalogUi.Message)
        assertTrue(b.back()); c.fail = true
        b.search("oops"); advanceUntilIdle(); assertTrue(b.ui.value is CatalogUi.Message)
        assertTrue(b.back()); assertTrue(b.ui.value is CatalogUi.Query)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun slowEarlierLoadCannotReplaceALaterOne() = runTest(StandardTestDispatcher()) {
        val c = FakeCatalog(); c.gate["slow"] = CompletableDeferred()
        val b = browser(c)
        b.search("slow"); advanceUntilIdle()
        assertTrue(b.ui.value is CatalogUi.Loading)
        assertTrue(b.back().not())          // cancels the pending load, still at the query level
        b.search("fast"); advanceUntilIdle()
        c.gate["slow"]!!.complete(Unit); advanceUntilIdle()
        assertEquals("Album fast", (b.ui.value as CatalogUi.Entries).items.single().title)
        assertNull(null)
    }
}
