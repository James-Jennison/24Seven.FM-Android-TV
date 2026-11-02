package com.codeframe78.twentyfourseven.player.pairing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Real loopback sockets against the real host; only TLS is swapped for a plain socket. */
class PairingHostTest {
    private val keyHash = ByteArray(32) { 0x22 }
    private val stations = setOf("sst", "afm")
    private lateinit var scope: CoroutineScope
    private val sockets = mutableListOf<Socket>()

    @Before fun setUp() { scope = CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    @After fun tearDown() { sockets.forEach { runCatching { it.close() } }; scope.cancel() }

    private val handed = mutableListOf<PhoneFrame.Session>()

    private fun host(keepAlive: PairingHost.KeepAlive = PairingHost.KeepAlive(tickMillis = 20), refuse: String? = null): PairingHost =
        PairingHost(
            parentScope = scope,
            openServer = { ServerSocket(0, 8, InetAddress.getLoopbackAddress()) },
            keyHash = keyHash,
            hostAddress = "127.0.0.1",
            tvName = "Test TV",
            supportedStations = stations,
            monotonicMillis = System::nanoTime.let { { System.nanoTime() / 1_000_000 } },
            epochMillis = System::currentTimeMillis,
            random = SecureRandom(),
            keepAlive = keepAlive,
            acceptSession = { handed += it; refuse },
        ).also { it.start() }

    private fun connect(h: PairingHost) = Socket(InetAddress.getLoopbackAddress(), h.offer.port).also { sockets += it; it.soTimeout = 3000 }

    private fun send(s: Socket, json: String) = FrameCodec.write(s.getOutputStream(), json)
    private fun recv(s: Socket): JSONObject? = FrameCodec.read(s.getInputStream())?.let(::JSONObject)

    private fun proof(key: ByteArray) = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
        .doFinal(keyHash).toHex()

    private fun pairFrame(proof: String) =
        """{"v":1,"type":"pair","proof":"$proof","phone":"Pixel","account":"dj_x","station":"sst"}"""

    private val track = ProposedTrack("sst", "ALB:42", "Title", "Artist", "Album")

    private fun awaitState(h: PairingHost, what: String, ok: (PairingUiState) -> Boolean) = runBlocking {
        repeat(150) { if (ok(h.state.value)) return@runBlocking; delay(20) }
        throw AssertionError("timed out waiting for $what; state=${h.state.value}")
    }

    private fun pairedPhone(h: PairingHost): Socket {
        val s = connect(h)
        send(s, pairFrame(proof(h.offer.qrSecret)))
        assertEquals("pair_ok", recv(s)!!.getString("type"))
        awaitState(h, "paired") { it is PairingUiState.PairedIdle }
        return s
    }

    @Test fun offerHasTheProtocolQrPayload() {
        val h = host()
        val qr = JSONObject(h.offer.qrPayload())
        assertEquals(1, qr.getInt("v"))
        assertEquals("127.0.0.1", qr.getString("host"))
        assertEquals(h.offer.port, qr.getInt("port"))
        assertEquals(32, qr.getString("secret").length)
        assertEquals(64, qr.getString("kh").length)
        assertEquals(6, h.offer.typedCode.length)
        h.close()
    }

    @Test fun typedCodeAndQrSecretBothPair() {
        val a = host(); val s = connect(a)
        send(s, pairFrame(proof(a.offer.typedCode.toByteArray()))); assertEquals("pair_ok", recv(s)!!.getString("type")); a.close()
        val b = host(); val t = connect(b)
        send(t, pairFrame(proof(b.offer.qrSecret))); assertEquals("pair_ok", recv(t)!!.getString("type")); b.close()
    }

    @Test fun fullRequestFlowReachesAccepted() {
        val h = host(); val phone = pairedPhone(h)
        h.propose(track)
        val propose = recv(phone)!!
        assertEquals("propose", propose.getString("type"))
        assertEquals("ALB:42", propose.getString("trackRef"))
        val op = propose.getString("op")
        send(phone, """{"v":1,"type":"awaiting","op":"$op"}""")
        awaitState(h, "confirming") { (it as? PairingUiState.Waiting)?.phase == WaitingPhase.PhoneConfirming }
        send(phone, """{"v":1,"type":"submitting","op":"$op"}""")
        send(phone, """{"v":1,"type":"result","op":"$op","code":"accepted"}""")
        awaitState(h, "accepted") { (it as? PairingUiState.Finished)?.outcome == RequestOutcome.Accepted }
        h.acknowledgeResult()
        awaitState(h, "idle again") { it is PairingUiState.PairedIdle }
        h.close()
    }

    @Test fun threeBadProofsBurnTheCodeAndCloseTheListener() {
        val h = host()
        repeat(3) {
            val s = connect(h)
            send(s, pairFrame("0".repeat(64)))
            assertEquals("pair_refused", recv(s)!!.getString("type"))
        }
        awaitState(h, "burned") { it == PairingUiState.Ended(EndReason.TooManyBadProofs) }
        try { Socket(InetAddress.getLoopbackAddress(), h.offer.port).also { sockets += it }; assertFalse("listener still open", true) } catch (_: java.io.IOException) {}
        h.close()
    }

    @Test fun nonPairFrameBeforePairingClosesThatConnection() {
        val h = host(); val s = connect(h)
        send(s, """{"v":1,"type":"submitting","op":"${"a".repeat(32)}"}""")
        assertNull(recv(s))
        assertTrue(h.state.value is PairingUiState.ShowingCode)
        h.close()
    }

    @Test fun malformedFrameClosesAndWrongVersionIsAnswered() {
        val h = host()
        val a = connect(h); send(a, "not json"); assertNull(recv(a))
        val b = connect(h); send(b, """{"v":2,"type":"ping"}""")
        assertEquals("unsupported_version", recv(b)!!.getString("error"))
        assertTrue(h.state.value is PairingUiState.ShowingCode)
        h.close()
    }

    @Test fun secondConnectionAfterPairingIsRefusedAndDoesNotDisturbTheFirst() {
        val h = host(); val phone = pairedPhone(h)
        val other = connect(h)
        // The listener is closed once paired, so a late connection never gets in.
        val refused = try { send(other, pairFrame(proof(h.offer.qrSecret))); recv(other) } catch (_: java.io.IOException) { null }
        assertTrue(refused == null || refused.getString("type") == "pair_refused")
        assertTrue(h.state.value is PairingUiState.PairedIdle)
        send(phone, """{"v":1,"type":"ping"}""")
        assertEquals("pong", recv(phone)!!.getString("type"))
        h.close()
    }

    @Test fun dropAfterSubmittingIsUnknownAndBeforeIsNothingSubmitted() {
        val h1 = host(); val p1 = pairedPhone(h1)
        h1.propose(track); val op1 = recv(p1)!!.getString("op")
        send(p1, """{"v":1,"type":"submitting","op":"$op1"}""")
        awaitState(h1, "submitting") { (it as? PairingUiState.Waiting)?.phase == WaitingPhase.Submitting }
        p1.close()
        awaitState(h1, "unknown") { (it as? PairingUiState.Finished)?.outcome == RequestOutcome.UnknownCheckQueue }
        h1.close()

        val h2 = host(); val p2 = pairedPhone(h2)
        h2.propose(track); recv(p2)
        p2.close()
        awaitState(h2, "unreachable") { (it as? PairingUiState.Finished)?.outcome == RequestOutcome.PhoneUnreachableNothingSubmitted }
        h2.acknowledgeResult()
        awaitState(h2, "ended") { it == PairingUiState.Ended(EndReason.Disconnected) }
        h2.close()
    }

    @Test fun silentPhoneIsDisconnectedAfterTheKeepAliveWindow() {
        val h = host(PairingHost.KeepAlive(pingEveryMillis = 50, silenceMillis = 300, tickMillis = 20, boundReadMillis = 2000))
        val phone = pairedPhone(h)
        assertEquals("ping", recv(phone)!!.getString("type")) // the TV pings while the link is quiet
        awaitState(h, "ended after silence") { it is PairingUiState.Ended }
        h.close()
    }

    @Test fun tvRevokeNotifiesThePhoneAndStopsListening() {
        val h = host(); val phone = pairedPhone(h)
        h.revoke()
        assertEquals("revoke", recv(phone)!!.getString("type"))
        awaitState(h, "revoked") { it == PairingUiState.Ended(EndReason.RevokedByTv) }
        h.close()
    }

    private fun sessionFrame(station: String = "sst", cookie: String = "abc123") =
        """{"v":1,"type":"session","station":"$station","account":"dj_x","cookies":[{"name":"PHPSESSID","value":"$cookie","maxAge":-1}]}"""

    @Test fun sessionHandOverIsStoredAndAcknowledgedOnce() {
        val h = host(); val phone = pairedPhone(h)
        send(phone, sessionFrame())
        val ok = recv(phone)!!
        assertEquals("session_ok", ok.getString("type")); assertEquals("sst", ok.getString("station"))
        assertEquals(1, handed.size); assertEquals("PHPSESSID", handed.single().cookies.single().name)
        send(phone, sessionFrame())
        assertEquals("already_handed_over", recv(phone)!!.getString("reason"))
        assertEquals(1, handed.size)
        h.close()
    }

    @Test fun sessionForAnotherStationIsRefusedAndNeverStored() {
        val h = host(); val phone = pairedPhone(h)
        send(phone, sessionFrame(station = "afm"))
        assertEquals("wrong_station", recv(phone)!!.getString("reason"))
        assertTrue(handed.isEmpty())
        h.close()
    }

    @Test fun storageRefusalIsReportedToThePhone() {
        val h = host(refuse = "storage_failed"); val phone = pairedPhone(h)
        send(phone, sessionFrame())
        assertEquals("storage_failed", recv(phone)!!.getString("reason"))
        h.close()
    }

    @Test fun sessionBeforePairingClosesTheConnection() {
        val h = host(); val s = connect(h)
        send(s, sessionFrame())
        assertNull(runCatching { recv(s) }.getOrNull())
        assertTrue(handed.isEmpty())
        h.close()
    }
}
