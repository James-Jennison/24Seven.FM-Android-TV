package com.codeframe78.twentyfourseven.player.pairing

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** What the TV shows to the listener for the pairing flow. */
sealed interface PairingUiState {
    /** State 1: a QR (and typed code) is on screen until [expiresAtMillis]. */
    data class ShowingCode(val expiresAtMillis: Long) : PairingUiState
    /** State 2. */
    data class PairedIdle(val phone: String, val account: String, val station: String) : PairingUiState
    /** State 4: a request was proposed; the phone is confirming or submitting. */
    data class Waiting(val track: ProposedTrack, val phase: WaitingPhase) : PairingUiState
    /** State 5. */
    data class Finished(val track: ProposedTrack, val outcome: RequestOutcome) : PairingUiState
    /** State 6: the pairing is over; the TV starts again from [ShowingCode]. */
    data class Ended(val reason: EndReason) : PairingUiState
}

enum class WaitingPhase { Sent, PhoneConfirming, Submitting }

data class ProposedTrack(
    val station: String,
    val trackRef: String,
    val title: String,
    val artist: String,
    val album: String,
)

sealed interface RequestOutcome {
    data object Accepted : RequestOutcome
    data class Refused(val reason: String) : RequestOutcome
    /** The listener cancelled on the phone, or the phone's safety gate refused. Nothing was submitted. */
    data object Declined : RequestOutcome
    /** The phone never confirmed or the link dropped before it said it was submitting. Nothing was submitted. */
    data object PhoneUnreachableNothingSubmitted : RequestOutcome
    /** The phone said it was submitting but never reported the result. Check the Queue; never resend. */
    data object UnknownCheckQueue : RequestOutcome
    /** The phone reported an uncertain station result. Check the Queue; never resend. */
    data object UncertainCheckQueue : RequestOutcome
}

enum class EndReason {
    CodeExpired, TooManyBadProofs, RevokedByPhone, RevokedByTv, StateChanged, Disconnected, ProtocolError, UnsupportedVersion,
}

/** What the driver must do after feeding the session an event. */
internal data class Reaction(val frames: List<TvFrame> = emptyList(), val close: Boolean = false)

/**
 * Pure pairing state machine. No I/O, no clock of its own and no threads: the driver owns the
 * socket and calls every method from one coroutine. The TV only ever displays what the phone
 * reports; it never infers success. A session is bound to the one connection that pairs it: the
 * driver refuses every other connection itself and never feeds it to the session.
 */
internal class PairingSession(
    secrets: List<ByteArray>,
    private val keyHash: ByteArray,
    private val codeExpiresAtMillis: Long,
    private val supportedStations: Set<String>,
    private val newSessionId: () -> String,
    private val config: PairingTimeouts = PairingTimeouts(),
) {
    /** The QR secret and the typed-code secret; either proves the same pairing. Null once burned. */
    private var secrets: List<ByteArray>? = secrets.map { it.copyOf() }
    private var badProofs = 0
    private var currentOp: String? = null
    private var track: ProposedTrack? = null
    private var phase = WaitingPhase.Sent
    private var phaseSinceMillis = 0L
    private var phoneLabel = ""
    private var accountLabel = ""
    private var station = ""
    private var linkLost = false

    var state: PairingUiState = PairingUiState.ShowingCode(codeExpiresAtMillis)
        private set

    val isPaired: Boolean get() = secrets == null && state !is PairingUiState.Ended

    fun onFrame(frame: PhoneFrame, nowMillis: Long): Reaction {
        if (state is PairingUiState.Ended) return Reaction(close = true)
        return when (frame) {
            is PhoneFrame.Pair -> onPair(frame, nowMillis)
            PhoneFrame.Ping -> Reaction(listOf(TvFrame.Pong))
            PhoneFrame.Pong -> Reaction()
            else -> if (secrets != null) Reaction() else onPaired(frame, nowMillis)
        }
    }

    private fun onPair(frame: PhoneFrame.Pair, now: Long): Reaction {
        if (secrets == null) return refuse(PairRefusal.AlreadyPaired)
        if (now >= codeExpiresAtMillis) {
            end(EndReason.CodeExpired)
            return Reaction(listOf(TvFrame.PairRefused(PairRefusal.Expired)), close = true)
        }
        if (!proofMatches(frame.proof) || frame.station !in supportedStations) {
            badProofs++
            if (badProofs >= config.maxBadProofs) {
                wipeSecret()
                end(EndReason.TooManyBadProofs)
                return Reaction(listOf(TvFrame.PairRefused(PairRefusal.BadProof)), close = true)
            }
            return Reaction(listOf(TvFrame.PairRefused(PairRefusal.BadProof)), close = true)
        }
        wipeSecret()
        phoneLabel = frame.phone
        accountLabel = frame.account
        station = frame.station
        state = PairingUiState.PairedIdle(phoneLabel, accountLabel, station)
        return Reaction(listOf(TvFrame.PairOk(newSessionId())))
    }

    private fun onPaired(frame: PhoneFrame, now: Long): Reaction = when (frame) {
        is PhoneFrame.Awaiting -> ifCurrent(frame.op) {
            if (phase == WaitingPhase.Sent) move(WaitingPhase.PhoneConfirming, now)
        }
        is PhoneFrame.Submitting -> ifCurrent(frame.op) {
            if (phase != WaitingPhase.Submitting) move(WaitingPhase.Submitting, now)
        }
        is PhoneFrame.Declined -> ifCurrent(frame.op) {
            // Declined is only valid before submitting; after it the outcome is a result frame.
            if (phase != WaitingPhase.Submitting) finish(RequestOutcome.Declined)
        }
        is PhoneFrame.Result -> ifCurrent(frame.op) {
            // A result without a prior `submitting` is never believed, least of all "accepted".
            if (phase == WaitingPhase.Submitting) {
                finish(
                    when (frame.code) {
                        ResultCode.Accepted -> RequestOutcome.Accepted
                        ResultCode.Refused -> RequestOutcome.Refused(frame.reason ?: "")
                        ResultCode.Uncertain -> RequestOutcome.UncertainCheckQueue
                    },
                )
            }
        }
        is PhoneFrame.StateChanged -> {
            end(EndReason.StateChanged)
            Reaction(listOf(TvFrame.Revoke), close = true)
        }
        PhoneFrame.Revoke -> {
            end(EndReason.RevokedByPhone)
            Reaction(close = true)
        }
        is PhoneFrame.Pair, PhoneFrame.Ping, PhoneFrame.Pong -> Reaction()
    }

    /** The listener chose a track on the TV. Valid only when paired and idle (or after a result). */
    fun propose(op: String, chosen: ProposedTrack, nowMillis: Long): Reaction {
        val current = state
        if (current !is PairingUiState.PairedIdle && current !is PairingUiState.Finished) return Reaction()
        if (chosen.station != station) return Reaction()
        val bounded = chosen.copy(
            title = chosen.title.take(MAX_TEXT_CHARS),
            artist = chosen.artist.take(MAX_TEXT_CHARS),
            album = chosen.album.take(MAX_TEXT_CHARS),
        )
        if (bounded.trackRef.isEmpty() || bounded.trackRef.length > MAX_TRACK_REF_CHARS) return Reaction()
        currentOp = op
        track = bounded
        phase = WaitingPhase.Sent
        phaseSinceMillis = nowMillis
        state = PairingUiState.Waiting(bounded, WaitingPhase.Sent)
        return Reaction(listOf(TvFrame.Propose(op, bounded.station, bounded.trackRef, bounded.title, bounded.artist, bounded.album)))
    }

    /** Back to the idle paired screen after the listener has read a result. */
    fun acknowledgeResult() {
        if (state is PairingUiState.Finished) {
            currentOp = null
            track = null
            state = if (linkLost) PairingUiState.Ended(EndReason.Disconnected)
            else PairingUiState.PairedIdle(phoneLabel, accountLabel, station)
        }
    }

    /** The listener revoked on the TV. */
    fun revoke(): Reaction {
        if (state is PairingUiState.Ended) return Reaction(close = true)
        wipeSecret()
        end(EndReason.RevokedByTv)
        return Reaction(listOf(TvFrame.Revoke), close = true)
    }

    /** Driver reports the connection is gone or two keep-alives were missed. */
    fun onDisconnect(): Reaction {
        linkLost = true
        when (state) {
            // A finished result stays on screen; acknowledging it then ends the pairing because the link is gone.
            is PairingUiState.Ended, is PairingUiState.ShowingCode, is PairingUiState.Finished -> Unit
            is PairingUiState.Waiting -> finish(
                // `submitting` always precedes the station request, so anything earlier means nothing went out.
                if (phase == WaitingPhase.Submitting) RequestOutcome.UnknownCheckQueue
                else RequestOutcome.PhoneUnreachableNothingSubmitted,
            )
            else -> end(EndReason.Disconnected)
        }
        return Reaction(close = true)
    }

    /** Called periodically by the driver. */
    fun tick(nowMillis: Long): Reaction {
        val s = state
        when {
            s is PairingUiState.ShowingCode && secrets != null && nowMillis >= codeExpiresAtMillis -> {
                wipeSecret()
                end(EndReason.CodeExpired)
                return Reaction(close = true)
            }
            s is PairingUiState.Waiting -> {
                val waited = nowMillis - phaseSinceMillis
                val limit = when (phase) {
                    WaitingPhase.Sent -> config.confirmStartMillis
                    WaitingPhase.PhoneConfirming -> config.phoneConfirmMillis
                    WaitingPhase.Submitting -> config.submitResultMillis
                }
                if (waited >= limit) {
                    finish(
                        if (phase == WaitingPhase.Submitting) RequestOutcome.UnknownCheckQueue
                        else RequestOutcome.PhoneUnreachableNothingSubmitted,
                    )
                }
            }
        }
        return Reaction()
    }

    private fun move(next: WaitingPhase, now: Long) {
        phase = next
        phaseSinceMillis = now
        track?.let { state = PairingUiState.Waiting(it, next) }
    }

    private fun finish(outcome: RequestOutcome) {
        val t = track ?: return
        state = PairingUiState.Finished(t, outcome)
    }

    private fun end(reason: EndReason) {
        state = PairingUiState.Ended(reason)
        currentOp = null
        track = null
    }

    private fun ifCurrent(op: String, block: () -> Unit): Reaction {
        if (state is PairingUiState.Waiting && op == currentOp) block()
        return Reaction()
    }

    private fun refuse(reason: PairRefusal) = Reaction(listOf(TvFrame.PairRefused(reason)), close = true)

    private fun proofMatches(proofHex: String): Boolean {
        val keys = secrets ?: return false
        val actual = hexOrNull(proofHex) ?: return false
        // Check every secret (no early exit) so timing does not reveal which one matched.
        var match = false
        for (key in keys) {
            val expected = Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(key, "HmacSHA256"))
                doFinal(keyHash)
            }
            if (MessageDigest.isEqual(expected, actual)) match = true
        }
        return match
    }

    private fun wipeSecret() {
        secrets?.forEach { it.fill(0) }
        secrets = null
    }
}

internal data class PairingTimeouts(
    val maxBadProofs: Int = 3,
    /** The phone must answer `awaiting` within this long of a proposal. */
    val confirmStartMillis: Long = 15_000,
    /** The listener has this long to approve on the phone. */
    val phoneConfirmMillis: Long = 120_000,
    /** After `submitting`, a missing result becomes "unknown, check the Queue". */
    val submitResultMillis: Long = 60_000,
)

internal fun hexOrNull(hex: String): ByteArray? {
    if (hex.length % 2 != 0) return null
    val out = ByteArray(hex.length / 2)
    for (i in out.indices) {
        val hi = Character.digit(hex[2 * i], 16)
        val lo = Character.digit(hex[2 * i + 1], 16)
        if (hi < 0 || lo < 0) return null
        out[i] = ((hi shl 4) or lo).toByte()
    }
    return out
}
