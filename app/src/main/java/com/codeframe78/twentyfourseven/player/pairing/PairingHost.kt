package com.codeframe78.twentyfourseven.player.pairing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.EOFException
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom

/** Everything the TV shows to let a phone find and trust this pairing. */
internal class PairingOffer(
    val host: String,
    val port: Int,
    val keyHash: ByteArray,
    val qrSecret: ByteArray,
    val typedCode: String,
    val expiresAtMillis: Long,
    private val expiresAtEpochSeconds: Long,
    private val tvName: String,
) {
    val fingerprint: String get() = pairingFingerprint(keyHash)

    /** The QR payload from the protocol document: `v`, `name`, `host`, `port`, `secret`, `exp`, `kh`. */
    fun qrPayload(): String = JSONObject()
        .put("v", PAIRING_PROTOCOL_VERSION)
        .put("name", tvName)
        .put("host", host)
        .put("port", port)
        .put("secret", qrSecret.toHex())
        .put("exp", expiresAtEpochSeconds)
        .put("kh", keyHash.toHex())
        .toString()
}

internal fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

/**
 * Owns one pairing: the listening socket, the connections and the [PairingSession]. All session
 * state is touched only by the single actor coroutine; sockets are read on IO threads that only
 * post events. The listener exists only between [start] and the end of the pairing, and nothing
 * outlives [close]. [openServer] supplies a TLS server socket in production and a plain one in
 * tests; the TLS key is pinned by the phone through the QR key hash or the confirmed fingerprint.
 */
internal class PairingHost(
    parentScope: CoroutineScope,
    private val openServer: () -> ServerSocket,
    keyHash: ByteArray,
    private val hostAddress: String,
    private val tvName: String,
    private val supportedStations: Set<String>,
    private val monotonicMillis: () -> Long,
    private val epochMillis: () -> Long,
    private val random: SecureRandom = SecureRandom(),
    private val codeLifetimeMillis: Long = 120_000L,
    private val timeouts: PairingTimeouts = PairingTimeouts(),
    private val keepAlive: KeepAlive = KeepAlive(),
    /** Diagnostics only: connection and frame-type events, never codes, proofs, secrets or key material. */
    private val log: (String) -> Unit = {},
    /** Stores a handed-over session; returns null when accepted or a short refusal reason. Must not log values. */
    private val acceptSession: (PhoneFrame.Session) -> String? = { "unsupported" },
) {
    internal data class KeepAlive(
        val pingEveryMillis: Long = 15_000,
        /** Two missed keep-alives: nothing received for this long means the phone is gone. */
        val silenceMillis: Long = 35_000,
        /**
         * An unpaired connection may sit idle while the listener compares the fingerprint on the phone
         * before the phone sends `pair`, so this must outlast that (the code itself lives 120 s).
         */
        val firstFrameMillis: Long = 125_000,
        /** A paired link is read with a timeout past [silenceMillis], so the tick, not the socket, decides. */
        val boundReadMillis: Long = 40_000,
        val tickMillis: Long = 500,
        val maxUnbound: Int = 4,
    )

    private class Conn(val socket: Socket) {
        @Volatile var lastRx = 0L
        private val lock = Any()
        fun send(frame: TvFrame): Boolean = try {
            synchronized(lock) { FrameCodec.write(socket.getOutputStream(), PairingJson.encode(frame)) }
            true
        } catch (_: Exception) {
            close()
            false
        }
        fun close() = runCatching { socket.close() }
    }

    private sealed interface Event {
        data class Received(val conn: Conn, val frame: PhoneFrame) : Event
        data class Closed(val conn: Conn) : Event
        data object Tick : Event
        data class Propose(val op: String, val track: ProposedTrack) : Event
        data object Acknowledge : Event
        data object Revoke : Event
    }

    private val job = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + job)
    private val events = Channel<Event>(capacity = 64)
    private val serial = Dispatchers.IO.limitedParallelism(1)
    private val server = openServer()
    private val qrSecret = ByteArray(16).also(random::nextBytes)
    private val typedCode = "%06d".format(random.nextInt(1_000_000))
    private val startedAt = monotonicMillis()
    private val session = PairingSession(
        secrets = listOf(qrSecret.copyOf(), typedCode.toByteArray(Charsets.US_ASCII)),
        keyHash = keyHash,
        codeExpiresAtMillis = startedAt + codeLifetimeMillis,
        supportedStations = supportedStations,
        newSessionId = { ByteArray(16).also(random::nextBytes).toHex() },
        config = timeouts,
    )
    private val conns = mutableSetOf<Conn>()
    private var bound: Conn? = null
    private var lastPingAt = startedAt
    private var finished = false

    val offer = PairingOffer(
        host = hostAddress,
        port = server.localPort,
        keyHash = keyHash,
        qrSecret = qrSecret,
        typedCode = typedCode,
        expiresAtMillis = startedAt + codeLifetimeMillis,
        expiresAtEpochSeconds = (epochMillis() + codeLifetimeMillis) / 1000,
        tvName = tvName,
    )

    private val _state = MutableStateFlow<PairingUiState>(session.state)
    val state: StateFlow<PairingUiState> = _state

    fun start() {
        scope.launch(Dispatchers.IO) { acceptLoop() }
        scope.launch { ticker() }
        scope.launch { actor() }
    }

    fun propose(track: ProposedTrack) {
        val op = ByteArray(16).also(random::nextBytes).toHex()
        events.trySend(Event.Propose(op, track))
    }

    fun acknowledgeResult() { events.trySend(Event.Acknowledge) }
    fun revoke() { events.trySend(Event.Revoke) }

    /** Stops listening and drops every connection. Safe to call repeatedly. */
    fun close() {
        shutdown()
        job.cancel()
    }

    private suspend fun acceptLoop() {
        while (scope.isActive && !server.isClosed) {
            val socket = try { server.accept() } catch (_: Exception) { return }
            val conn = Conn(socket)
            log("accepted connection from ${socket.inetAddress?.hostAddress}")
            val admitted = synchronized(conns) {
                (conns.size < keepAlive.maxUnbound + 1).also { if (it) conns.add(conn) }
            }
            if (!admitted) { log("refused connection: too many unbound"); conn.close(); continue }
            scope.launch(Dispatchers.IO) { readLoop(conn) }
        }
    }

    private suspend fun readLoop(conn: Conn) {
        try {
            conn.socket.soTimeout = keepAlive.firstFrameMillis.toInt()
            while (true) {
                val text = FrameCodec.read(conn.socket.getInputStream()) ?: break
                conn.lastRx = monotonicMillis()
                val parsed = PairingJson.parsePhoneFrame(text)
                log("frame received: ${frameName(parsed)}")
                when (parsed) {
                    is ParsedFrame.Frame -> events.send(Event.Received(conn, parsed.frame))
                    // Answered here, before the socket closes, so the error actually reaches the phone.
                    ParsedFrame.UnsupportedVersion -> { conn.send(TvFrame.Error("unsupported_version")); break }
                    ParsedFrame.UnknownType -> Unit
                    is ParsedFrame.Malformed -> break
                }
            }
        } catch (_: EOFException) { log("connection closed by peer")
        } catch (e: FrameException) { log("connection closed: bad frame (${e.message})")
        } catch (e: java.io.IOException) { log("connection closed: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            conn.close()
            events.send(Event.Closed(conn))
        }
    }

    private fun frameName(parsed: ParsedFrame) = when (parsed) {
        is ParsedFrame.Frame -> parsed.frame::class.simpleName
        ParsedFrame.UnsupportedVersion -> "unsupported_version"
        ParsedFrame.UnknownType -> "unknown_type"
        is ParsedFrame.Malformed -> "malformed(${parsed.reason})"
    }

    private suspend fun ticker() {
        while (scope.isActive) {
            delay(keepAlive.tickMillis)
            events.send(Event.Tick)
        }
    }

    private suspend fun actor() {
        for (event in events) {
            withContext(serial) { handle(event) }
            _state.value = session.state
            if (session.state is PairingUiState.Ended) shutdown()
        }
    }

    private fun handle(event: Event) {
        val now = monotonicMillis()
        when (event) {
            is Event.Received -> onReceived(event.conn, event.frame, now)
            is Event.Closed -> onClosed(event.conn)
            Event.Tick -> onTick(now)
            is Event.Propose -> bound?.let { c -> send(c, session.propose(event.op, event.track, now)) }
            Event.Acknowledge -> session.acknowledgeResult()
            Event.Revoke -> {
                val r = session.revoke()
                bound?.let { send(it, r) }
                shutdown()
            }
        }
    }

    private fun onReceived(conn: Conn, frame: PhoneFrame, now: Long) {
        val isBound = conn === bound
        // Until a connection has paired, the only frame it may send is `pair`.
        if (!isBound && frame !is PhoneFrame.Pair) { conn.close(); return }
        // Once paired, a second `pair` on the bound connection is answered by the session as already_paired.
        if (isBound && frame is PhoneFrame.Session) { onSessionFrame(conn, frame); return }
        val before = session.isPaired
        val reaction = session.onFrame(frame, now)
        if (frame is PhoneFrame.Pair) log("pair result: ${reaction.frames.firstOrNull()?.let { it::class.simpleName } ?: "none"}")
        if (!isBound && !before && session.isPaired) {
            bound = conn
            conn.socket.soTimeout = keepAlive.boundReadMillis.toInt()
            // Everyone else is shut out the moment the pairing succeeds.
            synchronized(conns) { conns.filter { it !== conn }.forEach { it.close() } }
        }
        send(conn, reaction)
    }

    /** One hand-over per station per pairing; a phone signed in to several stations sends one frame for each. */
    private val handedOver = mutableSetOf<String>()

    private fun onSessionFrame(conn: Conn, frame: PhoneFrame.Session) {
        val refusal = when {
            frame.station in handedOver -> "already_handed_over"
            frame.station !in supportedStations -> "unsupported_station"
            else -> acceptSession(frame)
        }
        if (refusal == null) handedOver += frame.station
        log("session hand-over: station=${frame.station} cookies=${frame.cookies.size} names=${frame.cookies.map { it.name }} result=${refusal ?: "stored"}")
        conn.send(if (refusal == null) TvFrame.SessionOk(frame.station) else TvFrame.SessionRefused(frame.station, refusal))
    }

    private fun send(conn: Conn, reaction: Reaction) {
        reaction.frames.forEach { conn.send(it) }
        if (reaction.close) conn.close()
    }

    private fun onClosed(conn: Conn) {
        synchronized(conns) { conns.remove(conn) }
        if (conn === bound) {
            log("bound connection gone (state=${session.state::class.simpleName})")
            bound = null
            session.onDisconnect()
            // No further connection may ever bind: the pairing is over once its link is gone.
            closeServer()
        }
    }

    private fun onTick(now: Long) {
        session.tick(now)
        val c = bound ?: return
        if (now - c.lastRx >= keepAlive.silenceMillis) {
            log("closing: silence for ${now - c.lastRx} ms")
            c.close() // the reader posts Closed, which reports the disconnect once
            return
        }
        if (now - lastPingAt >= keepAlive.pingEveryMillis) {
            lastPingAt = now
            c.send(TvFrame.Ping)
        }
    }

    private fun closeServer() { runCatching { server.close() } }

    private fun shutdown() {
        if (finished) return
        finished = true
        log("shutdown: listener and connections closed (state=${session.state::class.simpleName})")
        closeServer()
        synchronized(conns) { conns.toList() }.forEach { it.close() }
    }
}
