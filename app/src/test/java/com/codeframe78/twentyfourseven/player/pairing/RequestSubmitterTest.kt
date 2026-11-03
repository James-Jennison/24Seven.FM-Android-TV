package com.codeframe78.twentyfourseven.player.pairing

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RequestSubmitterTest {
    private val good = GateAttestation(adult = true, termsVersion = CURRENT_COMMUNITY_TERMS_VERSION, communityVisible = true)
    private fun session(gate: GateAttestation = good, station: String = "sst") =
        StationSession(station, "dj_x", listOf(StoredCookie("PHPSESSID", "secret-value", null)), gate, handedOverAtMillis = NOW)
    private val track = CatalogTrack("B00000JC79", "224947", "Peter Gunn", "Henry Mancini", "Peter Gunn", "2:00", true)
    private companion object { const val NOW = 1_800_000_000_000L }
    private var calls = 0
    private var requested: String? = null
    private fun submitter(enabled: Boolean = true, html: String? = "<p>Request Successful</p>", queued: Boolean = true, fail: Boolean = false) =
        RequestSubmitter(enabled, { uri, _, _ -> calls++; requested = uri.toString(); if (fail) throw IOException("x"); html!! }, { _, _, _ -> queued }, nowMillis = { NOW + 1000 })

    private fun run(s: RequestSubmitter, session: StationSession? = session(), t: CatalogTrack = track) =
        runBlocking { s.submit(session, t, "sst") }

    @Test fun disabledBuildSendsNothing() {
        assertTrue(run(submitter(enabled = false)) is TvRequestResult.NotAllowed); assertEquals(0, calls)
    }

    @Test fun staleTermsOrFailedGateSendNothing() {
        assertTrue(run(submitter(), session(good.copy(termsVersion = "2025-01-01"))) is TvRequestResult.NotAllowed)
        assertTrue(run(submitter(), session(good.copy(adult = false))) is TvRequestResult.NotAllowed)
        assertTrue(run(submitter(), session(GateAttestation())) is TvRequestResult.NotAllowed)
        assertTrue(run(submitter(), null) is TvRequestResult.NotAllowed)
        assertEquals(0, calls)
    }

    @Test fun ineligibleOrMalformedTrackSendsNothing() {
        assertTrue(run(submitter(), t = track.copy(eligible = false)) is TvRequestResult.NotAllowed)
        assertTrue(run(submitter(), t = track.copy(songId = "1;2")) is TvRequestResult.NotAllowed)
        assertTrue(run(submitter(), t = track.copy(albumId = "../x")) is TvRequestResult.NotAllowed)
        assertEquals(0, calls)
    }

    @Test fun acknowledgedAndQueuedIsConfirmedWithExactlyOneFetch() {
        assertEquals(TvRequestResult.Queued, run(submitter()))
        assertEquals(1, calls)
        assertEquals("https://streamingsoundtracks.com/modules.php?name=Req&asin=B00000JC79&songID=224947", requested)
    }

    @Test fun acknowledgedButNotVisibleMeansCheckQueue() {
        assertTrue(run(submitter(queued = false)) is TvRequestResult.CheckQueue)
        assertEquals(1, calls)
    }

    @Test fun networkFailureAfterSendingIsCheckQueueNeverRetried() {
        assertTrue(run(submitter(fail = true)) is TvRequestResult.CheckQueue)
        assertEquals(1, calls)
    }

    @Test fun repliesAreClassified() {
        assertEquals(RequestPageOutcome.SignInRequired, classifyRequestPage("<p>Please log in to request</p>"))
        assertTrue(classifyRequestPage("<p>This track was played recently</p>") is RequestPageOutcome.Rejected)
        assertEquals(RequestPageOutcome.Unrecognized, classifyRequestPage("<p>Hello</p>"))
        assertTrue(run(submitter(html = "<p>please log in</p>")) == TvRequestResult.SignInExpired)
        assertTrue(run(submitter(html = "<p>Hello</p>")) is TvRequestResult.CheckQueue)
    }

    @Test fun anOldHandOverSendsNothing() {
        val old = session().let { StationSession(it.station, it.account, it.cookies, it.gate, handedOverAtMillis = NOW - 31L * 24 * 3600 * 1000) }
        assertTrue(run(submitter(), old) is TvRequestResult.NotAllowed)
        assertEquals(0, calls)
    }

    @Test fun aClearStationVerdictBeatsTheWordLogin() {
        val page = "<a href=\"/login\">Login</a><p>Request Successful</p>"
        assertEquals(RequestPageOutcome.Acknowledged, classifyRequestPage(page))
        assertTrue(classifyRequestPage("<a>Login</a><p>This track was played recently</p>") is RequestPageOutcome.Rejected)
        assertEquals(RequestPageOutcome.Unrecognized, classifyRequestPage("<a href=\"/login\">Login</a><p>Welcome back</p>"))
        assertEquals(RequestPageOutcome.SignInRequired, classifyRequestPage("<p>Please log in to request songs</p>"))
    }

    @Test fun diagnosticsNeverCarryThePageText() {
        val seen = mutableListOf<String>()
        val s = RequestSubmitter(true, { _, _, _ -> "<p>Request Successful for secret-account-name</p>" }, { _, _, _ -> true }, onReplyText = { seen += it }, nowMillis = { NOW + 1 })
        runBlocking { s.submit(session(), track, "sst") }
        assertTrue(seen.isNotEmpty())
        assertTrue(seen.none { it.contains("secret-account-name") })
    }

    @Test fun aSessionIsNeverSentToAnotherStationsOrigin() {
        val fetch = HttpSessionPageFetch(connect = { throw AssertionError("must not connect") })
        val session = session(station = "sst")
        val failure = runCatching { fetch.get(java.net.URI("https://evil.example/"), java.net.URI("https://streamingsoundtracks.com/"), session) }.exceptionOrNull()
        assertTrue(failure is java.io.IOException)
        val other = runCatching { fetch.get(java.net.URI("https://1980s.fm/"), java.net.URI("https://1980s.fm/"), session) }.exceptionOrNull()
        assertTrue(other is java.io.IOException)
    }

    @Test fun aSessionDatedInTheFutureIsRefused() {
        val future = session().let { StationSession(it.station, it.account, it.cookies, it.gate, handedOverAtMillis = NOW + 10L * 24 * 3600 * 1000) }
        assertTrue(run(submitter(), future) is TvRequestResult.NotAllowed)
        assertEquals(0, calls)
    }

    @Test fun aSessionIsOnlySentOverHttpsToItsOwnPort() {
        val fetch = HttpSessionPageFetch(connect = { throw AssertionError("must not connect") })
        val s = session(station = "sst")
        assertTrue(runCatching { fetch.get(java.net.URI("http://streamingsoundtracks.com/"), java.net.URI("https://streamingsoundtracks.com/"), s) }.exceptionOrNull() != null)
        assertTrue(runCatching { fetch.get(java.net.URI("https://streamingsoundtracks.com:8443/"), java.net.URI("https://streamingsoundtracks.com/"), s) }.exceptionOrNull() != null)
    }

    private class FakeConn(url: java.net.URL, val status: Int, val location: String?, val body: String, val seen: MutableList<String>) : java.net.HttpURLConnection(url) {
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun connect() {}
        override fun getResponseCode(): Int { seen += url.toString(); return status }
        override fun getHeaderField(name: String?): String? = if (name == "Location") location else null
        override fun getContentType(): String = "text/html"
        override fun getInputStream(): java.io.InputStream = body.byteInputStream(Charsets.ISO_8859_1)
    }

    private fun redirecting(location: String, seen: MutableList<String>): HttpSessionPageFetch {
        var hops = 0
        return HttpSessionPageFetch(connect = {
            if (hops++ == 0) FakeConn(it.toURL(), 302, location, "", seen) else FakeConn(it.toURL(), 200, null, "<html>ok</html>", seen)
        })
    }

    @Test fun aPlainHttpRedirectOnTheSameHostIsUpgradedToHttpsAndFollowedOnce() {
        val seen = mutableListOf<String>()
        val body = redirecting("http://streamingsoundtracks.com/modules.php?name=Queue", seen)
            .get(java.net.URI("https://streamingsoundtracks.com/modules.php?name=Req&asin=1&songID=2"), java.net.URI("https://streamingsoundtracks.com/"), session(station = "sst"))
        assertEquals("<html>ok</html>", body)
        assertEquals(2, seen.size)
        assertTrue(seen.all { it.startsWith("https://streamingsoundtracks.com/") })
    }

    @Test fun aRedirectBackIntoTheRequestEndpointIsNeverFollowed() {
        val seen = mutableListOf<String>()
        val failure = runCatching {
            redirecting("http://streamingsoundtracks.com/modules.php?name=Req&asin=1&songID=2", seen)
                .get(java.net.URI("https://streamingsoundtracks.com/modules.php?name=Req&asin=1&songID=2"), java.net.URI("https://streamingsoundtracks.com/"), session(station = "sst"))
        }.exceptionOrNull()
        assertTrue(failure is java.io.IOException)
        assertEquals(1, seen.size)
    }

    @Test fun aRedirectToAnotherHostIsRefused() {
        val seen = mutableListOf<String>()
        val failure = runCatching {
            redirecting("https://evil.example/x", seen)
                .get(java.net.URI("https://streamingsoundtracks.com/modules.php?name=Req&asin=1&songID=2"), java.net.URI("https://streamingsoundtracks.com/"), session(station = "sst"))
        }.exceptionOrNull()
        assertTrue(failure is java.io.IOException)
        assertEquals(1, seen.size)
    }
}
