package com.codeframe78.twentyfourseven.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerQueueResponseParserTest {
    private val parser = PlayerQueueResponseParser()

    @Test
    fun `parses the public player queue and history rows`() {
        val result = parser.parse(
            """
            {
              "queue_html": "<tr><td>1</td><td><img src='/images/cover/040/ABCDEFGHIJ.jpg'></td><td><strong>Artist</strong><span>Track</span><a href='?songID=song-1&amp;asin=ABCDEFGHIJ'></a></td></tr>",
              "played_html": "<tr><td>1</td><td><img src='/images/cover/040/KLMNOPQRST.jpg'></td><td><strong>Composer</strong><span>Played Track</span><a href='?songID=song-2&amp;asin=KLMNOPQRST'></a></td></tr>"
            }
            """.trimIndent(),
            "https://streamingsoundtracks.com/",
        )

        val queued = result.upcoming.single()
        assertEquals(1, queued.position)
        assertEquals("Track", queued.displayTitle)
        assertEquals("Artist", queued.artistName)
        assertEquals("song-1", queued.songId)
        assertEquals("ABCDEFGHIJ", queued.albumId)
        assertEquals("https://streamingsoundtracks.com/images/cover/500/ABCDEFGHIJ.jpg", queued.artworkUrl)

        val played = result.recentlyPlayed.single()
        assertEquals("Played Track", played.displayTitle)
        assertEquals("Composer", played.artistName)
        assertEquals("song-2", played.songId)
        assertEquals("KLMNOPQRST", played.albumId)
    }

    @Test
    fun `parses bounded extended queue rows without member attribution`() {
        val result = parser.parseExtended(
            """
            <table>
              <tr><th>Queue</th></tr>
              <tr><td><b>1</b> 3:27</td><td><img src='/images/cover/040/ABCDEFGHIJ.jpg'></td><td><a href='?asin=ABCDEFGHIJ'><b>The Album</b></a> - <i>The Artist</i><br><span>The Track</span><span class='req-text'>Request By: Listener - <i>Private note</i></span></td></tr>
            </table>
            <table>
              <tr><th>Played</th></tr>
              <tr><td><b>1</b> 4:11</td><td><img src='/images/cover/040/KLMNOPQRST.jpg'></td><td><a href='?asin=KLMNOPQRST'><b>Album</b></a> - <i>Composer</i><br><span>Played</span></td></tr>
            </table>
            """.trimIndent(),
            "https://streamingsoundtracks.com/",
            maxTracks = 5,
        )

        assertEquals("The Track", result.upcoming.single().displayTitle)
        assertEquals("The Artist", result.upcoming.single().artistName)
        assertEquals("The Album", result.upcoming.single().albumTitle)
        assertEquals("3:27", result.upcoming.single().durationLabel)
        assertEquals("Played", result.recentlyPlayed.single().displayTitle)
    }

    @Test
    fun `rejects malformed public rows`() {
        val result = parser.parse("{\"queue_html\": \"<tr><td>1</td></tr>\", \"played_html\": \"\"}", "https://1980s.fm/")

        assertEquals(emptyList<Any>(), result.upcoming)
        assertEquals(emptyList<Any>(), result.recentlyPlayed)
        assertNull(result.upcoming.firstOrNull())
    }
}
