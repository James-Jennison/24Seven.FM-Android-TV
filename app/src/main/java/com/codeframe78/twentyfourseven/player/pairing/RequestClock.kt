package com.codeframe78.twentyfourseven.player.pairing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URI

/**
 * The stations print the listener's request clock on their "Your Requests" page: seconds until the
 * next request is allowed, and seconds until the listener's earliest queued request starts.
 * The station stays the authority; the TV only shows what it reads and never decides eligibility.
 */
internal data class RequestClock(
    /** Seconds until the next request is allowed; 0 when the station says Ready; null when unknown. */
    val secondsUntilNextRequest: Int?,
    val secondsUntilQueuedRequestPlays: Int?,
)

internal object RequestClockParser {
    private const val MAX_SECONDS = 7 * 24 * 60 * 60
    private val WAIT_MINUTES = Regex("Request\\s+Wait:\\s*([0-9]{1,4})\\s+Minutes?", RegexOption.IGNORE_CASE)
    private val READY = Regex("Your\\s+Request:\\s*Ready", RegexOption.IGNORE_CASE)

    /** Null when the page carries no recognisable clock (including a sign-in page). */
    fun parse(html: String): RequestClock? {
        val document = Jsoup.parse(html)
        if (document.selectFirst("input[name=user_password]") != null) return null
        val clock = document.selectFirst("div[class*=request-clock][data-seconds]")
        if (clock != null) {
            val seconds = clock.attr("data-seconds").trim().toIntOrNull()?.takeIf { it in 0..MAX_SECONDS }
            val word = clock.selectFirst("[class*=request-value]")?.text()?.trim().orEmpty()
            val next = when {
                seconds == null -> null
                seconds > 0 -> seconds
                word.equals("Ready", ignoreCase = true) -> 0
                else -> null
            }
            val queue = clock.attr("data-queue-seconds").trim().toIntOrNull()?.takeIf { it in 0..MAX_SECONDS }
            return RequestClock(next, queue)
        }
        // Older page layout: the same facts as text.
        val text = document.text().replace(Regex("\\s+"), " ")
        val minutes = WAIT_MINUTES.find(text)?.groupValues?.get(1)?.toIntOrNull()
        return when {
            minutes != null && minutes > 0 -> RequestClock(minutes * 60, null)
            minutes == 0 || READY.containsMatchIn(text) -> RequestClock(0, null)
            else -> null
        }
    }
}

internal fun interface RequestClockSource {
    suspend fun read(session: StationSession): RequestClock?
}

/** One read-only GET of the station's Your Requests page with the stored session; no polling. */
internal class YourRequestsClockSource(
    private val fetch: SessionPageFetch = HttpSessionPageFetch(diag = { android.util.Log.i("PairingHost", "request clock: $it") }),
) : RequestClockSource {
    override suspend fun read(session: StationSession): RequestClock? = withContext(Dispatchers.IO) {
        val origin = StationOrigins.of(session.station) ?: return@withContext null
        try {
            val base = URI(origin)
            RequestClockParser.parse(fetch.get(base.resolve("/modules.php?name=Your_Requests"), base, session))
        } catch (_: IOException) {
            null
        }
    }
}
