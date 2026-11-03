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
        // A disabled button has no request link: the track is listed, not requestable, with the station's reason.
        assertEquals(3, album.tracks.size)
        val blocked = album.tracks.last()
        assertFalse(blocked.eligible)
        assertEquals("", blocked.songId)
        assertTrue(blocked.note!!.startsWith("Request cooldown ends"))
        val first = album.tracks.first()
        assertEquals("Peter Gunn", first.title)
        assertEquals("Henry Mancini", first.artist)
        assertEquals("B00000JC79:224947", first.trackRef)
        assertTrue(first.eligible)
        assertTrue(album.tracks.filter { it.eligible }.all { it.songId.isNotBlank() })
    }

    @Test fun albumPageForAnotherAlbumIdYieldsNothing() {
        assertTrue(parser.parseAlbum(fixture("album.html"), origin, "OTHER").tracks.none { it.eligible })
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

    @Test fun anOuterRowWrappingTheWholeAlbumIsNotATrack() {
        val inner = fixture("album.html").substringAfter("<table>").substringBefore("</table>")
        val html = "<table><tr><td>$inner</td></tr></table>".let { "<html><head><meta property=\"og:title\" content=\"A - B | X\"></head><body><table><tr><td><table>$inner</table></td></tr></table></body></html>" }
        val tracks = parser.parseAlbum(html, origin, "B00000JC79").tracks
        assertEquals(3, tracks.size)
        assertEquals("Henry Mancini", tracks.first().artist)
    }

    @Test fun suggestionPageYieldsOneTrack() {
        val html = """<table><tr><td><a href="/modules.php?name=Req&amp;asin=B0015RB3S0&amp;songID=249430"><img src="/modules/SAM/images/requestbutton_request.png" alt="Request this track."></a></td>
            <td><a href="modules.php?name=Album&amp;asin=B0015RB3S0"><img src="../../images/cover/040/B0015RB3S0.jpg"></a></td>
            <td><b>&nbsp;Sudden Impact - Lalo Schifrin</b><br>&nbsp;4. Frisco Night (2:50)</td></tr></table>"""
        val track = parser.parseSuggestion(html, origin).tracks.single()
        assertEquals("B0015RB3S0:249430", track.trackRef)
        assertEquals("Frisco Night", track.title)
        assertEquals("Lalo Schifrin", track.artist)
        assertEquals("Sudden Impact", track.albumTitle)
        assertEquals("2:50", track.duration)
    }

    @Test fun coverUrlIsBuiltOnlyForKnownStationsAndSafeIds() {
        assertEquals("https://streamingsoundtracks.com/images/cover/B00000JC79.jpg", coverUrl("sst", "B00000JC79"))
        assertNull(coverUrl("zzz", "B00000JC79"))
        assertNull(coverUrl("sst", "../x"))
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
        override suspend fun suggest(station: String, mode: SuggestionMode) =
            CatalogAlbum("Alb", listOf(CatalogTrack("A9", "9", "Pick", "Who", "Alb", "1:00", true)))
        override suspend fun browse(station: String, kind: BrowseKind, key: String) =
            listOf(CatalogEntry(CatalogTarget.Album("B-$key"), "${kind.wire} $key", null, null))
        override suspend fun page(station: String, source: PageSource, start: Int) =
            listOf(CatalogEntry(CatalogTarget.Album("P$start"), "Page $start", null, null))
        override suspend fun waits(station: String) = listOf(WaitCard("Member Wait", "20 minutes"))
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
        assertTrue(b.back())                // cancels the pending load and stays on the query screen
        assertTrue(b.ui.value is CatalogUi.Query)
        assertFalse(b.back())               // a second Back is at the root
        b.search("fast"); advanceUntilIdle()
        c.gate["slow"]!!.complete(Unit); advanceUntilIdle()
        assertEquals("Album fast", (b.ui.value as CatalogUi.Entries).items.single().title)
        assertNull(null)
    }

    @Test fun theWaitCardsAreReadFromTheRequestPage() {
        val waits = CatalogParser().parseWaits(fixture("requests-home.html"))
        assertEquals(listOf(WaitCard("Member Wait", "20 minutes"), WaitCard("VIP Wait", "1 minute")), waits)
    }

    @Test fun browsingArtistsByLetterListsArtists() {
        val entries = CatalogParser().parseSearch(fixture("browse-artist.html"), "https://streamingsoundtracks.com/")
        assertTrue(entries.size > 10)
        assertTrue(entries.any { it.target is CatalogTarget.Artist })
        // The A-Z navigation bar must never become an artist's subtitle, nor an artist appear twice.
        assertTrue(entries.none { it.subtitle?.contains("Album: #") == true })
        assertEquals(entries.size, entries.map { it.target }.distinct().size)
    }

    @Test fun browsingByYearListsAlbums() {
        val entries = CatalogParser().parseSearch(fixture("browse-year.html"), "https://streamingsoundtracks.com/")
        assertTrue(entries.isNotEmpty())
    }

    @Test fun browseKeysAreLettersOrYears() {
        assertEquals(27, browseKeys(BrowseKind.Artist).size)
        assertEquals("numbers", browseKeys(BrowseKind.Album).first().second)
        assertEquals("20262026", browseKeys(BrowseKind.Year, 2026).first().second)
        assertEquals("1990", browseKeys(BrowseKind.Year, 2026).last().first)
    }

    @Test fun theNextPageIsFoundFromThePageLinks() {
        assertEquals(97, CatalogParser().nextStart(fixture("browse-artist.html"), 1))
        assertEquals(193, CatalogParser().nextStart(fixture("browse-artist.html"), 97))
        assertEquals(null, CatalogParser().nextStart(fixture("browse-artist.html"), 193))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun resetReturnsToTheSearchPage() = runTest {
        val b = browser()
        b.setField(CatalogField.Artist)
        b.search("abc")
        advanceUntilIdle()
        assertTrue(b.ui.value is CatalogUi.Entries)
        b.reset()
        assertTrue(b.ui.value is CatalogUi.Query)
        assertEquals(false, b.back())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun backDuringALoadKeepsTheLevelItStartedFrom() = runTest(StandardTestDispatcher()) {
        val c = FakeCatalog(); c.gate["A-first"] = CompletableDeferred()
        val b = browser(c)
        b.search("first"); advanceUntilIdle()
        val list = b.ui.value as CatalogUi.Entries
        b.open(CatalogEntry(CatalogTarget.Artist("Who"), "Who", null, null))
        assertTrue(b.ui.value is CatalogUi.Loading)
        assertTrue(b.back())
        assertEquals(list, b.ui.value)
        assertTrue(b.back()); assertTrue(b.ui.value is CatalogUi.Query)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun repeatedMessagesAndShowMoreAddNoExtraLevels() = runTest(StandardTestDispatcher()) {
        val c = FakeCatalog(); val b = browser(c)
        b.search("none"); advanceUntilIdle()
        assertTrue(b.back()); b.search("none"); advanceUntilIdle()
        b.back(); b.search("abc"); advanceUntilIdle()
        val more = CatalogEntry(CatalogTarget.More(PageSource.Search(CatalogField.Title, "abc"), 97), "Show more", null, null)
        val base = b.ui.value as CatalogUi.Entries
        b.open(more); advanceUntilIdle()
        val extended = b.ui.value as CatalogUi.Entries
        assertEquals(base.items.size + 1, extended.items.size)
        assertTrue(b.back()); assertTrue(b.ui.value is CatalogUi.Query) // one Back leaves the continued list
        assertFalse(b.back())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun showMoreDoesNotRepeatAnEntryFromAnEarlierPage() = runTest(StandardTestDispatcher()) {
        val c = object : RequestCatalog by FakeCatalog() {
            override suspend fun page(station: String, source: PageSource, start: Int) =
                listOf(CatalogEntry(CatalogTarget.Album("A-abc"), "Album abc", null, null), CatalogEntry(CatalogTarget.Album("P$start"), "Page $start", null, null))
        }
        val b = browser(c)
        b.search("abc"); advanceUntilIdle()
        b.open(CatalogEntry(CatalogTarget.More(PageSource.Search(CatalogField.Title, "abc"), 97), "Show more", null, null)); advanceUntilIdle()
        val ids = (b.ui.value as CatalogUi.Entries).items.map { it.target }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test fun waitFormatIsLocaleIndependent() {
        val saved = java.util.Locale.getDefault()
        try { java.util.Locale.setDefault(java.util.Locale("ar", "EG")); assertEquals("1:05:09", formatWait(3909)); assertEquals("2:03", formatWait(123)) }
        finally { java.util.Locale.setDefault(saved) }
    }
}
