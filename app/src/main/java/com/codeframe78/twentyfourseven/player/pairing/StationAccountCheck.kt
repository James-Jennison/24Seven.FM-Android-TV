package com.codeframe78.twentyfourseven.player.pairing

import com.codeframe78.twentyfourseven.player.data.TrustedStationNavigation
import com.codeframe78.twentyfourseven.player.data.readBounded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

/** Where a handed-over session may be sent: the station's own origin from this table, never from the phone. */
internal object StationOrigins {
    private val ORIGINS = mapOf(
        "sst" to "https://streamingsoundtracks.com/",
        "1980s" to "https://1980s.fm/",
        "afm" to "https://adagio.fm/",
        "dfm" to "https://death.fm/",
        "efm" to "https://entranced.fm/",
    )
    fun of(station: String): String? = ORIGINS[station]
}

internal enum class SignInState { SignedIn, SignedOut, Unknown }

internal interface StationAccountCheck {
    suspend fun check(session: StationSession): SignInState
}

/**
 * Read-only: fetches the station's home page with the session and looks for the logout link. It never
 * requests `op=logout`, which would sign the owner out everywhere. A display-name match is not used
 * because the name also appears on the signed-out page.
 */
internal class HomePageAccountCheck(
    private val connect: (URI) -> HttpURLConnection = { it.toURL().openConnection() as HttpURLConnection },
) : StationAccountCheck {
    override suspend fun check(session: StationSession): SignInState = withContext(Dispatchers.IO) {
        val origin = StationOrigins.of(session.station) ?: return@withContext SignInState.Unknown
        try {
            val html = fetch(URI(origin), session)
            if (Jsoup.parse(html, origin).select("a[href]").any { LOGOUT.containsMatchIn(it.attr("href")) }) SignInState.SignedIn
            else SignInState.SignedOut
        } catch (_: IOException) {
            SignInState.Unknown
        }
    }

    private fun fetch(start: URI, session: StationSession): String {
        var uri = start
        repeat(MAX_REDIRECTS + 1) { n ->
            TrustedStationNavigation.requireSameHttpsOrigin(uri, start)
            val c = connect(uri)
            try {
                c.connectTimeout = TIMEOUT; c.readTimeout = TIMEOUT; c.instanceFollowRedirects = false
                c.setRequestProperty("Accept", "text/html")
                c.setRequestProperty("User-Agent", "24Seven.FM-TV/0.1 (Android)")
                c.setRequestProperty("Cookie", session.cookies.joinToString("; ") { "${it.name}=${it.value}" })
                val status = c.responseCode
                if (status in REDIRECTS) {
                    if (n == MAX_REDIRECTS) throw IOException("Too many redirects")
                    uri = TrustedStationNavigation.resolveRedirect(uri, c.getHeaderField("Location"), start)
                    return@repeat
                }
                if (status !in 200..299) throw IOException("HTTP $status")
                return c.inputStream.bufferedReader(StandardCharsets.ISO_8859_1).use { it.readBounded(MAX_CHARS) }
            } finally {
                c.disconnect()
            }
        }
        throw IOException("Did not complete")
    }

    private companion object {
        val LOGOUT = Regex("op=logout", RegexOption.IGNORE_CASE)
        const val TIMEOUT = 10_000
        const val MAX_CHARS = 512_000
        const val MAX_REDIRECTS = 5
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
    }
}
