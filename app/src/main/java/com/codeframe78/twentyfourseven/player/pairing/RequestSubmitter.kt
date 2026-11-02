package com.codeframe78.twentyfourseven.player.pairing

import com.codeframe78.twentyfourseven.player.data.TrustedStationNavigation
import com.codeframe78.twentyfourseven.player.data.readBounded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal sealed interface TvRequestResult {
    /** The station acknowledged and the track is visible in Queue. */
    data object Queued : TvRequestResult
    /** The station acknowledged but Queue did not show it. Check the Queue; never resend. */
    data class CheckQueue(val why: String) : TvRequestResult
    data class Rejected(val message: String) : TvRequestResult
    /** The station wants a sign-in: pair with the phone again. Nothing was requested. */
    data object SignInExpired : TvRequestResult
    /** Refused before anything was sent. */
    data class NotAllowed(val why: String) : TvRequestResult
}

/** One page fetch with the session's cookies; injected so the decision logic is testable without a network. */
internal fun interface SessionPageFetch {
    fun get(uri: URI, referer: URI, session: StationSession): String
}

internal fun interface QueueCheck {
    /** True when the song is visible in the station's upcoming Queue. */
    suspend fun isQueued(station: String, albumId: String, songId: String): Boolean
}

/**
 * Sends one request, the way the Player does: a single GET of the station's Req link for a track picked
 * from its album page, same origin only. Never retries: once the request could have reached the station,
 * every unclear outcome is reported as "check the Queue". Off unless [enabled], and only with a session
 * whose phone-vouched gate still passes against this build's terms version.
 */
internal class RequestSubmitter(
    private val enabled: Boolean,
    private val fetch: SessionPageFetch = HttpSessionPageFetch(),
    private val queue: QueueCheck = QueueCheck { _, _, _ -> false },
    /** Receives the station's reply text (public page text, never cookies) so the exact answer can be reported. */
    private val onReplyText: (String) -> Unit = {},
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun submit(session: StationSession?, track: CatalogTrack, station: String): TvRequestResult {
        if (!enabled) return TvRequestResult.NotAllowed("Requests from the TV are not enabled in this build.")
        if (session == null) return TvRequestResult.NotAllowed("No phone session on this TV. Pair with your phone first.")
        if (session.station != station) return TvRequestResult.NotAllowed("The stored session is for another station.")
        // The phone's vouching expires: a consent revoked on the phone must not outlive the stored session for long.
        if (nowMillis() - session.handedOverAtMillis > MAX_GATE_AGE_MILLIS) {
            return TvRequestResult.NotAllowed("This TV's sign-in is old. Pair with your phone again to renew it.")
        }
        // Re-checked on every request: a stale terms version blocks writes until the phone hands over again.
        session.gate.refusalReason?.let {
            return TvRequestResult.NotAllowed(
                if (it == "terms_out_of_date") "The community terms changed. Pair with your phone again." else "This account is not cleared to make requests.",
            )
        }
        if (!track.eligible || !SAFE_ALBUM_ID.matches(track.albumId) || !NUMERIC_ID.matches(track.songId)) {
            return TvRequestResult.NotAllowed("That track cannot be requested right now.")
        }
        val origin = StationOrigins.of(station) ?: return TvRequestResult.NotAllowed("Unsupported station.")
        val reqUri = URI(origin).resolve("/modules.php?name=Req&asin=${enc(track.albumId)}&songID=${enc(track.songId)}")
        val referer = URI(origin).resolve("/modules.php?name=Album&asin=${enc(track.albumId)}")
        // From here the request may have reached the station: nothing below is ever retried.
        val html = try {
            withContext(Dispatchers.IO) { fetch.get(reqUri, referer, session) }
        } catch (e: Exception) {
            runCatching { onReplyText("no readable reply after sending: ${e.javaClass.simpleName}") }
            return TvRequestResult.CheckQueue("The station did not answer clearly.")
        }
        // Diagnostics carry the size only: a station page can hold account-identifying text and is never logged.
        runCatching { onReplyText("reply read: ${html.length} chars") }
        return when (val outcome = classifyRequestPage(html)) {
            RequestPageOutcome.SignInRequired -> TvRequestResult.SignInExpired
            is RequestPageOutcome.Rejected -> TvRequestResult.Rejected(outcome.message)
            RequestPageOutcome.Acknowledged ->
                if (runCatching { queue.isQueued(station, track.albumId, track.songId) }.getOrDefault(false)) TvRequestResult.Queued
                else TvRequestResult.CheckQueue("The station acknowledged the request, but it is not visible in Queue yet.")
            RequestPageOutcome.Unrecognized -> TvRequestResult.CheckQueue("The station's reply was not recognised.")
        }
    }

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")

    private companion object {
        const val MAX_GATE_AGE_MILLIS = 30L * 24 * 3600 * 1000
        val SAFE_ALBUM_ID = Regex("[A-Za-z0-9_-]{1,64}")
        val NUMERIC_ID = Regex("\\d{1,20}")
    }
}

internal sealed interface RequestPageOutcome {
    data object SignInRequired : RequestPageOutcome
    data class Rejected(val message: String) : RequestPageOutcome
    data object Acknowledged : RequestPageOutcome
    data object Unrecognized : RequestPageOutcome
}

/** The Player's classification of the station's reply, kept in step with its patterns. */
internal fun classifyRequestPage(html: String): RequestPageOutcome {
    val text = Jsoup.parse(html).text().replace(Regex("\\s+"), " ").trim()
    // A clear station verdict wins over the word "login", which most station pages carry in a header or footer link.
    if (ACCEPTANCE.any { it.containsMatchIn(text) }) return RequestPageOutcome.Acknowledged
    REJECTION.firstOrNull { it.first.containsMatchIn(text) }?.let { return RequestPageOutcome.Rejected(it.second) }
    if (SIGN_IN_REQUIRED.any { it.containsMatchIn(text) }) return RequestPageOutcome.SignInRequired
    return RequestPageOutcome.Unrecognized
}

private val SIGN_IN_REQUIRED = listOf(
    Regex("(?:please )?log in to (?:request|continue)", RegexOption.IGNORE_CASE),
    Regex("you (?:must|need to) (?:be )?(?:log(?:ged)? ?in|sign(?:ed)? ?in)", RegexOption.IGNORE_CASE),
    Regex("(?:login|log in) required", RegexOption.IGNORE_CASE),
    Regex("please log in", RegexOption.IGNORE_CASE),
)

private val ACCEPTANCE = listOf(
    Regex("request successful", RegexOption.IGNORE_CASE),
    Regex("request has successfully been delivered", RegexOption.IGNORE_CASE),
    Regex("request was successfully delivered", RegexOption.IGNORE_CASE),
)

private val REJECTION = listOf(
    Regex("played recently", RegexOption.IGNORE_CASE) to "The station reports this track was played recently.",
    Regex("already (?:in|on) the queue", RegexOption.IGNORE_CASE) to "The station reports this track is already in the queue.",
    Regex("(?:request\\s+wait|cooldown|too soon to request|request limit)", RegexOption.IGNORE_CASE) to "The station reports that this account must wait before requesting again.",
    Regex("request failed", RegexOption.IGNORE_CASE) to "The station rejected this request. Check Queue before trying again.",
    Regex("cannot request", RegexOption.IGNORE_CASE) to "The station rejected this request. Check Queue before trying again.",
    Regex("can only request", RegexOption.IGNORE_CASE) to "The station rejected this request. Check Queue before trying again.",
    Regex("(?:request|track) (?:is )?not available", RegexOption.IGNORE_CASE) to "The station rejected this request. Check Queue before trying again.",
    Regex("not eligible", RegexOption.IGNORE_CASE) to "The station rejected this request. Check Queue before trying again.",
)

/** Same-origin HTTPS GET with the session's Cookie header; bounded; redirects stay on the station. */
internal class HttpSessionPageFetch(
    private val connect: (URI) -> HttpURLConnection = { it.toURL().openConnection() as HttpURLConnection },
    /** Diagnostics only: status, redirects, elapsed time and failure class. Never URLs with cookies, never cookie values. */
    private val diag: (String) -> Unit = {},
    private val clock: () -> Long = System::nanoTime,
) : SessionPageFetch {
    override fun get(uri: URI, referer: URI, session: StationSession): String {
        // The session may only ever be sent to its own station's origin, whatever the caller passed in.
        val expected = StationOrigins.of(session.station)?.let(::URI)
        require(expected != null && uri.host.equals(expected.host, ignoreCase = true)) { "session does not belong to this origin" }
        val began = clock()
        fun ms() = (clock() - began) / 1_000_000
        try {
            return fetchOnce(uri, referer, session) { diag("hop ${it}") }.also { diag("reply read: ${it.length} chars in ${ms()} ms") }
        } catch (e: Exception) {
            diag("fetch failed after ${ms()} ms: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }

    private fun fetchOnce(uri: URI, referer: URI, session: StationSession, hop: (String) -> Unit): String {
        val start = uri
        var current = uri
        repeat(MAX_REDIRECTS + 1) { n ->
            TrustedStationNavigation.requireSameHttpsOrigin(current, start)
            val c = connect(current)
            try {
                c.connectTimeout = 15_000; c.readTimeout = 60_000; c.instanceFollowRedirects = false
                c.setRequestProperty("Accept", "text/html")
                c.setRequestProperty("User-Agent", "24Seven.FM-TV/0.1 (Android)")
                c.setRequestProperty("Referer", referer.toString())
                c.setRequestProperty("Cookie", session.cookies.joinToString("; ") { "${it.name}=${it.value}" })
                val status = c.responseCode
                hop("status=$status path=${current.path} redirect=${status in setOf(301, 302, 303, 307, 308)} contentType=${c.contentType}")
                if (status in setOf(301, 302, 303, 307, 308)) {
                    if (n == MAX_REDIRECTS) throw IOException("Too many redirects")
                    current = TrustedStationNavigation.resolveRedirect(current, c.getHeaderField("Location"), start)
                    return@repeat
                }
                if (status !in 200..299) throw IOException("HTTP $status")
                return c.inputStream.bufferedReader(StandardCharsets.ISO_8859_1).use { it.readBounded(1_000_000) }
            } finally {
                c.disconnect()
            }
        }
        throw IOException("Did not complete")
    }

    private companion object { const val MAX_REDIRECTS = 5 }
}
