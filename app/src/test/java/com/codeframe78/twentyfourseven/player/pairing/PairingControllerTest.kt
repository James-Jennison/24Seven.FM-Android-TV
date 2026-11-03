package com.codeframe78.twentyfourseven.player.pairing

import android.content.ContextWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

class PairingControllerTest {
    private val good = GateAttestation(adult = true, termsVersion = CURRENT_COMMUNITY_TERMS_VERSION, communityVisible = true)
    private val track = CatalogTrack("B00000JC79", "224947", "Peter Gunn", "Henry Mancini", "Peter Gunn", "2:00", true)

    private class MemoryStore(vararg initial: StationSession) : StationSessionStore {
        val sessions = initial.associateBy { it.station }.toMutableMap()
        override fun loadAll() = sessions.values.toList()
        override fun save(session: StationSession) { sessions[session.station] = session }
        override fun clear(station: String) { sessions.remove(station) }
        override fun clearAll() { sessions.clear() }
    }

    private fun session(station: String = "sst", at: Long = System.currentTimeMillis()) =
        StationSession(station, "dj_x", listOf(StoredCookie("PHPSESSID", "secret", null)), good, at)

    private val sent = AtomicInteger()

    private fun controller(
        store: StationSessionStore = MemoryStore(session()),
        html: String = "<p>Request Received. Your request was sent to the DJ application.</p>",
        check: SignInState = SignInState.SignedIn,
    ) = PairingController(
        context = ContextWrapper(null),
        scope = CoroutineScope(Dispatchers.Unconfined),
        enabled = true,
        store = store,
        accountCheck = object : StationAccountCheck { override suspend fun check(session: StationSession) = check },
        clockSource = { RequestClock(0, null) },
        submitter = RequestSubmitter(true, { _: URI, _: URI, _: StationSession -> sent.incrementAndGet(); html }, { _, _, _ -> false }),
    )

    private fun PairingController.awaitDone() = runBlocking { withTimeout(5_000) { request.first { it is TvRequestUi.Done } } as TvRequestUi.Done }

    @Test fun aRequestNeedsConfirmationAndIsSentOnce() {
        val c = controller()
        c.beginTvRequest(track, "sst")
        assertTrue(c.request.value is TvRequestUi.Confirming)
        assertEquals(0, sent.get())
        c.confirmTvRequest(); c.confirmTvRequest() // a second press must not send again
        val done = c.awaitDone()
        assertEquals(1, sent.get())
        assertTrue(done.result is TvRequestResult.Received)
        assertEquals(setOf("sst:224947"), c.requestedSongs.value)
    }

    @Test fun cancelBeforeConfirmSendsNothing() {
        val c = controller()
        c.beginTvRequest(track, "sst"); c.dismissTvRequest()
        assertNull(c.request.value)
        c.confirmTvRequest()
        assertNull(c.request.value)
        assertEquals(0, sent.get())
    }

    @Test fun aSecondPickWhileOneIsOpenIsIgnored() {
        val c = controller()
        c.beginTvRequest(track, "sst")
        c.beginTvRequest(track.copy(songId = "1"), "sst")
        assertEquals("224947", (c.request.value as TvRequestUi.Confirming).track.songId)
    }

    @Test fun aRefusedRequestIsNotMarkedRequested() {
        val c = controller(html = "<p>You must wait before another request.</p>")
        c.beginTvRequest(track, "sst"); c.confirmTvRequest()
        val done = c.awaitDone()
        assertFalse(done.result is TvRequestResult.Received || done.result is TvRequestResult.Queued)
        assertTrue(c.requestedSongs.value.isEmpty())
    }

    @Test fun noStoredSessionIsNotAllowedAndSendsNothing() {
        val c = controller(store = MemoryStore())
        c.beginTvRequest(track, "sst"); c.confirmTvRequest()
        assertTrue(c.awaitDone().result is TvRequestResult.NotAllowed)
        assertEquals(0, sent.get())
    }

    @Test fun dismissClearsAFinishedResult() {
        val c = controller()
        c.beginTvRequest(track, "sst"); c.confirmTvRequest(); c.awaitDone()
        c.dismissTvRequest()
        assertNull(c.request.value)
    }

    private fun await(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!condition()) { check(System.currentTimeMillis() < end) { "timed out" }; Thread.sleep(5) }
    }

    @Test fun accountsShowStoredSessionsAndTheirSignInState() {
        val c = controller(store = MemoryStore(session("sst"), session("dfm")), check = SignInState.SignedOut)
        c.refreshAccounts()
        await { c.accounts.value.size == 2 && c.accounts.value.all { it.state == SignInState.SignedOut } }
        assertEquals(listOf("dfm", "sst"), c.accounts.value.map { it.station })
        assertTrue(c.accounts.value.all { it.state == SignInState.SignedOut })
    }

    @Test fun signOutRemovesOnlyThatStationOnThisTv() {
        val store = MemoryStore(session("sst"), session("dfm"))
        val c = controller(store = store)
        c.refreshAccounts()
        await { c.accounts.value.size == 2 }
        c.signOutOnThisTv("sst")
        assertEquals(listOf("dfm"), c.accounts.value.map { it.station })
        assertEquals(setOf("dfm"), store.sessions.keys)
        c.signOutAllOnThisTv()
        assertTrue(c.accounts.value.isEmpty()); assertTrue(store.sessions.isEmpty())
    }

    @Test fun theClockIsReadForAStoredSessionOnly() {
        val c = controller()
        c.refreshClock("dfm")
        Thread.sleep(200) // no stored session for dfm: nothing is ever read
        assertTrue(c.clocks.value.isEmpty())
        c.refreshClock("sst")
        await { c.clocks.value.containsKey("sst") }
        assertEquals(0, c.clocks.value.getValue("sst").clock?.secondsUntilNextRequest)
    }

    // The signed-in check only reads the home page and never logs out.

    private class FakeConnection(url: URL, private val status: Int, private val body: String, private val location: String? = null) : HttpURLConnection(url) {
        var cookie: String? = null
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getHeaderField(name: String?) = if (name == "Location") location else null
        override fun setRequestProperty(key: String, value: String) { if (key == "Cookie") cookie = value }
        override fun getInputStream() = body.byteInputStream(Charsets.ISO_8859_1)
    }

    private fun check(status: Int = 200, body: String, location: String? = null, station: String = "sst"): Pair<SignInState, List<URI>> {
        val seen = mutableListOf<URI>()
        val result = runBlocking {
            HomePageAccountCheck { uri -> seen += uri; FakeConnection(uri.toURL(), if (seen.size == 1) status else 200, body, location) }.check(session(station))
        }
        return result to seen
    }

    @Test fun aLogoutLinkMeansSignedIn() =
        assertEquals(SignInState.SignedIn, check(body = """<a href="modules.php?name=Your_Account&op=logout">Logout</a>""").first)

    @Test fun noLogoutLinkMeansSignedOut() =
        assertEquals(SignInState.SignedOut, check(body = """<a href="modules.php?name=Your_Account">Login</a>""").first)

    @Test fun aServerErrorIsUnknownNotSignedOut() =
        assertEquals(SignInState.Unknown, check(status = 500, body = "").first)

    @Test fun aRedirectToAnotherHostIsNotFollowedWithTheSession() {
        val (state, seen) = check(status = 302, body = "", location = "https://evil.example/")
        assertTrue(state != SignInState.SignedIn)
        assertEquals(1, seen.size)
    }

    @Test fun theCheckNeverRequestsLogout() {
        val (_, seen) = check(body = """<a href="/x?op=logout">Logout</a>""")
        assertTrue(seen.none { it.toString().contains("op=logout") })
    }

    @Test fun aHandedOverSessionKeepsItsCookiesAndCapsLifetime() {
        val frame = PhoneFrame.Session("sst", "dj_x", listOf(SessionCookie("a", "1", -1), SessionCookie("b", "2", 10L * 365 * 24 * 3600)), good)
        val s = stationSessionFrom(frame, 1_000L)
        assertEquals(null, s.cookies[0].expiresAtEpochMillis)
        assertEquals(1_000L + 365L * 24 * 3600 * 1000, s.cookies[1].expiresAtEpochMillis)
        assertEquals(1_000L, s.handedOverAtMillis)
        assertFalse(s.toString().contains("1"))
    }
}
