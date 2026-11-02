package com.codeframe78.twentyfourseven.player.pairing

import com.codeframe78.twentyfourseven.player.data.TrustedStationNavigation
import com.codeframe78.twentyfourseven.player.data.readBounded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Read-only view of a station's public request catalog. The TV only searches and reads album pages
 * (no sign-in, no cookies); it never submits a request. The identifiers it extracts are passed to the
 * phone as an opaque `trackRef`, and the phone re-resolves them itself before anything is sent.
 */
internal enum class CatalogField(val wire: String, val label: String) {
    Title("title", "Song"),
    Artist("artist", "Artist"),
    Album("album", "Album"),
    Genre("genre", "Genre"),
}

/** The station's own one-track suggestions. */
internal enum class SuggestionMode(val wire: String) {
    Random("random"),
    LeastPlayed("randomleast"),
}

/** Album artwork on the station's own site; the id is validated like any album id before it is used. */
internal fun coverUrl(station: String, albumId: String): String? {
    val origin = StationOrigins.of(station) ?: return null
    return if (albumId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) "${origin}images/cover/$albumId.jpg" else null
}

internal sealed interface CatalogTarget {
    data class Album(val albumId: String) : CatalogTarget
    data class Artist(val name: String) : CatalogTarget
}

internal data class CatalogEntry(val target: CatalogTarget, val title: String, val subtitle: String?, val year: String?)

internal data class CatalogTrack(
    val albumId: String,
    val songId: String,
    val title: String,
    val artist: String?,
    val albumTitle: String?,
    val duration: String?,
    /** Advisory only: the phone and the station decide whether a request is accepted. */
    val eligible: Boolean,
    /** The station's own reason when a track cannot be requested now, e.g. "Request cooldown ends: ...". */
    val note: String? = null,
) {
    /** `<albumId>:<songId>`, split at the last colon by the phone. */
    val trackRef: String get() = "$albumId:$songId"
}

internal data class CatalogAlbum(val title: String?, val tracks: List<CatalogTrack>)

internal interface RequestCatalog {
    suspend fun search(station: String, query: String, field: CatalogField): List<CatalogEntry>
    suspend fun albumsBy(station: String, artist: String): List<CatalogEntry>
    suspend fun album(station: String, albumId: String): CatalogAlbum
    suspend fun suggest(station: String, mode: SuggestionMode): CatalogAlbum
}

/** Pure HTML parsing, kept apart from the network so it is unit tested against fixtures. */
internal class CatalogParser {
    fun parseSearch(html: String, origin: String): List<CatalogEntry> {
        val host = URI(origin).host
        return Jsoup.parse(html, origin).select("tr").mapNotNull { row ->
            val albumLinks = row.select("a[href]").filter { link ->
                val uri = uriOrNull(link.absUrl("href")) ?: return@filter false
                uri.scheme == "https" && uri.host.equals(host, true) &&
                    query(uri, "name") == "Album" && !query(uri, "asin").isNullOrBlank()
            }
            val albumIds = albumLinks.mapNotNull { query(URI(it.absUrl("href")), "asin") }.distinct()
            if (albumIds.size == 1) {
                val title = albumLinks.first().text().clean()
                val subtitle = albumLinks.getOrNull(1)?.text()?.clean()
                if (title.isBlank() || albumLinks.size > 1 && subtitle.isNullOrBlank()) return@mapNotNull null
                val year = row.select("td").map { it.text().clean() }.lastOrNull { it.matches(YEAR) }
                return@mapNotNull CatalogEntry(CatalogTarget.Album(albumIds.single()), title, subtitle, year)
            }
            val artistLink = row.select("a[href]").firstOrNull { link ->
                val uri = uriOrNull(link.absUrl("href")) ?: return@firstOrNull false
                uri.scheme == "https" && uri.host.equals(host, true) && query(uri, "name") == "Requests" &&
                    query(uri, "postartistsearch").equals("true", true) && !query(uri, "artist").isNullOrBlank()
            } ?: return@mapNotNull null
            val name = query(URI(artistLink.absUrl("href")), "artist")?.clean()
                ?.takeIf { it.length <= MAX_ARTIST_CHARS } ?: return@mapNotNull null
            val display = artistLink.text().clean().ifBlank { return@mapNotNull null }
            val genre = row.select("td").map { it.text().clean() }.lastOrNull { it.isNotBlank() && it != display }
            CatalogEntry(CatalogTarget.Artist(name), display, genre, null)
        }.distinctBy { listOf(it.target, it.title, it.subtitle) }.take(MAX_ENTRIES)
    }

    fun parseAlbum(html: String, origin: String, albumId: String): CatalogAlbum {
        val host = URI(origin).host
        val document = Jsoup.parse(html, origin)
        val albumTitle = (document.selectFirst("meta[property=\"og:title\"]")?.attr("content")
            ?: document.title().substringAfter(" - ", "")).substringBefore(" | ").clean().ifBlank { null }
        val tracks = document.select("tr").mapNotNull { row ->
            // An outer layout row can wrap the whole album: only a row with exactly one request button is a track.
            if (row.select("img[src*=requestbutton]").size != 1) return@mapNotNull null
            val image = row.selectFirst("img[src*=requestbutton]") ?: return@mapNotNull null
            val uri = image.closest("a[href]")?.absUrl("href")?.let(::uriOrNull)
            val songId = uri?.takeIf {
                it.scheme == "https" && it.host.equals(host, true) &&
                    query(it, "name") == "Req" && query(it, "asin") == albumId
            }?.let { query(it, "songID") }?.takeIf { it.matches(NUMERIC) }
            // A disabled button has no request link: keep the row so the listener sees why, never as requestable.
            val reason = image.attr("alt").ifBlank { image.attr("title") }.clean().take(MAX_NOTE_CHARS).ifBlank { null }
            val cells = row.select("td")
            val titleCell = cells.firstOrNull { it.select("a[href*=postartistsearch]").isNotEmpty() }
                ?: cells.firstOrNull {
                    val t = it.text().clean()
                    (it.ownText().clean().isNotBlank() || it.selectFirst("span[itemprop=name]") != null) &&
                        t.length > 2 && !t.matches(DURATION) && !t.matches(NUMERIC)
                } ?: return@mapNotNull null
            // The live pages put the title in a schema.org span; older markup used the cell's own text.
            val title = titleCell.select("span[itemprop=name]").firstOrNull { it.closest("a") == null }?.text()?.clean()
                ?.takeIf { it.isNotBlank() }
                ?: titleCell.ownText().clean().ifBlank { titleCell.textNodes().joinToString(" ") { it.text() }.clean() }
            if (title.isBlank()) return@mapNotNull null
            CatalogTrack(
                albumId = albumId,
                songId = songId.orEmpty(),
                title = title,
                artist = titleCell.select("a[href*=postartistsearch]").map { it.text().clean() }.distinct().joinToString(", ").ifBlank { null },
                albumTitle = albumTitle,
                duration = cells.map { it.text().clean() }.firstOrNull { it.matches(DURATION) },
                eligible = songId != null && image.attr("src").contains("requestbutton_request"),
                note = if (songId == null || !image.attr("src").contains("requestbutton_request")) reason ?: "Not available to request right now." else null,
            )
        }.distinctBy { it.songId.ifBlank { "${it.title}|${it.artist}" } }.take(MAX_TRACKS)
        return CatalogAlbum(albumTitle, tracks)
    }

    /** The suggestion pages show one request row: button link, album link, "Album - Artist" and "n. Title (m:ss)". */
    fun parseSuggestion(html: String, origin: String): CatalogAlbum {
        val host = URI(origin).host
        val document = Jsoup.parse(html, origin)
        document.select("tr").forEach { row ->
            val image = row.selectFirst("img[src*=requestbutton_request]") ?: return@forEach
            val req = image.closest("a[href]")?.absUrl("href")?.let(::uriOrNull)
                ?.takeIf { it.scheme == "https" && it.host.equals(host, true) && query(it, "name") == "Req" } ?: return@forEach
            val albumId = query(req, "asin")?.takeIf { it.isNotBlank() } ?: return@forEach
            val songId = query(req, "songID")?.takeIf { it.matches(NUMERIC) } ?: return@forEach
            val details = row.selectFirst("td:has(b)") ?: return@forEach
            val descriptor = details.selectFirst("b")?.text()?.clean().orEmpty()
            val text = details.ownText().clean()
            val title = text.replace(Regex("^\\d+\\.\\s*"), "").replace(Regex("\\s*\\(\\d{1,2}:\\d{2}\\)\\s*$"), "").clean()
            if (title.isBlank()) return@forEach
            val albumTitle = descriptor.substringBeforeLast(" - ", descriptor).clean().ifBlank { null }
            val artist = descriptor.substringAfterLast(" - ", "").clean().ifBlank { null }
            return CatalogAlbum(
                albumTitle,
                listOf(CatalogTrack(albumId, songId, title, artist, albumTitle, DURATION.find(text)?.value, eligible = true)),
            )
        }
        return CatalogAlbum(null, emptyList())
    }

    private fun uriOrNull(value: String) = runCatching { URI(value) }.getOrNull()

    private fun query(uri: URI, name: String): String? = uri.rawQuery.orEmpty().split('&')
        .mapNotNull { pair -> pair.split('=', limit = 2).takeIf { it.size == 2 } }
        .firstOrNull { it[0].equals(name, true) }
        ?.get(1)
        ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }

    private fun String.clean() = replace(Regex("\\s+"), " ").trim()

    private companion object {
        val YEAR = Regex("(?:19|20)\\d{2}")
        val DURATION = Regex("\\d{1,2}:\\d{2}")
        val NUMERIC = Regex("\\d+")
        const val MAX_ENTRIES = 100
        const val MAX_TRACKS = 250
        const val MAX_ARTIST_CHARS = 200
        const val MAX_NOTE_CHARS = 120
    }
}

/** Anonymous, same-origin HTTPS GETs of the public request pages; bounded in size, time and redirects. */
internal class StationRequestCatalog(
    private val parser: CatalogParser = CatalogParser(),
    private val connect: (URI) -> HttpURLConnection = { it.toURL().openConnection() as HttpURLConnection },
) : RequestCatalog {
    override suspend fun search(station: String, query: String, field: CatalogField): List<CatalogEntry> = withContext(Dispatchers.IO) {
        val origin = origin(station)
        val html = get(origin, "modules.php?name=Requests&searchfor=1&searchpgstart=1&searchby=${field.wire}&searchtext=${enc(query)}&search=Search")
        parser.parseSearch(html, origin)
    }

    override suspend fun albumsBy(station: String, artist: String): List<CatalogEntry> = withContext(Dispatchers.IO) {
        val origin = origin(station)
        parser.parseSearch(get(origin, "modules.php?name=Requests&postartistsearch=true&artist=${enc(artist)}"), origin)
    }

    override suspend fun album(station: String, albumId: String): CatalogAlbum = withContext(Dispatchers.IO) {
        val origin = origin(station)
        parser.parseAlbum(get(origin, "modules.php?name=Album&asin=${enc(albumId)}"), origin, albumId)
    }

    override suspend fun suggest(station: String, mode: SuggestionMode): CatalogAlbum = withContext(Dispatchers.IO) {
        val origin = origin(station)
        parser.parseSuggestion(get(origin, "modules.php?name=Requests&${mode.wire}=1&searchpgstart=1"), origin)
    }

    private fun origin(station: String) = StationOrigins.of(station) ?: throw IOException("Unsupported station")

    private fun get(origin: String, path: String): String {
        val expected = URI(origin)
        var uri = expected.resolve(path)
        repeat(MAX_REDIRECTS + 1) { n ->
            TrustedStationNavigation.requireSameHttpsOrigin(uri, expected)
            val connection = connect(uri)
            try {
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "text/html")
                connection.setRequestProperty("User-Agent", USER_AGENT)
                val status = connection.responseCode
                if (status in REDIRECTS) {
                    if (n == MAX_REDIRECTS) throw IOException("Too many redirects")
                    uri = TrustedStationNavigation.resolveRedirect(uri, connection.getHeaderField("Location"), expected)
                    return@repeat
                }
                if (status !in 200..299) throw IOException("Station returned HTTP $status")
                val charset = connection.contentType?.substringAfter("charset=", "")?.substringBefore(';')?.trim()?.trim('"')
                    ?.takeIf(String::isNotEmpty)?.let { runCatching { Charset.forName(it) }.getOrNull() }
                    ?: StandardCharsets.ISO_8859_1
                return connection.inputStream.bufferedReader(charset).use { it.readBounded(MAX_CHARS) }
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Request did not complete")
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val USER_AGENT = "24Seven.FM-TV/0.1 (Android; anonymous listener)"
        const val TIMEOUT_MILLIS = 10_000
        const val MAX_CHARS = 512_000
        const val MAX_REDIRECTS = 5
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
    }
}
