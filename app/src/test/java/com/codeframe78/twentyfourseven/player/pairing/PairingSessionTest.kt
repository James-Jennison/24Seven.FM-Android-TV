package com.codeframe78.twentyfourseven.player.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class PairingSessionTest {
    private val secret = ByteArray(16) { (it + 1).toByte() }
    private val keyHash = ByteArray(32) { (it * 3).toByte() }
    private val stations = setOf("sst", "1980s", "afm", "dfm", "efm")
    private val op = "0123456789abcdef0123456789abcdef"
    private val track = ProposedTrack("sst", "ALB:42", "Title", "Artist", "Album")
    private val timeouts = PairingTimeouts()

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun proof(s: ByteArray = secret) =
        hex(Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(s, "HmacSHA256")) }.doFinal(keyHash))

    private fun session(expires: Long = 1_000_000) =
        PairingSession(secret, keyHash, expires, stations, { "session-1" }, timeouts)

    private fun pair(station: String = "sst", p: String = proof()) = PhoneFrame.Pair(p, "Pixel", "dj_x", station)

    private fun paired(): PairingSession = session().also { it.onFrame(pair(), 0) }

    private fun waiting(phase: WaitingPhase? = null, now: Long = 0): PairingSession = paired().also {
        it.propose(op, track, now)
        when (phase) {
            WaitingPhase.PhoneConfirming -> it.onFrame(PhoneFrame.Awaiting(op), now)
            WaitingPhase.Submitting -> { it.onFrame(PhoneFrame.Awaiting(op), now); it.onFrame(PhoneFrame.Submitting(op), now) }
            else -> Unit
        }
    }

    private fun outcome(s: PairingSession) = (s.state as PairingUiState.Finished).outcome

    @Test fun validProofPairsAndBurnsTheSecret() {
        val s = session()
        val r = s.onFrame(pair(), 10)
        assertEquals(listOf<TvFrame>(TvFrame.PairOk("session-1")), r.frames)
        assertEquals(PairingUiState.PairedIdle("Pixel", "dj_x", "sst"), s.state)
        // The same proof again, even on a fresh connection, is refused.
        val again = s.onFrame(pair(), 11)
        assertEquals(listOf<TvFrame>(TvFrame.PairRefused(PairRefusal.AlreadyPaired)), again.frames)
        assertTrue(again.close)
    }

    @Test fun badProofIsRefusedAndThirdAttemptBurnsTheCode() {
        val s = session()
        val bad = pair(p = proof(ByteArray(16)))
        repeat(2) {
            val r = s.onFrame(bad, 1)
            assertEquals(listOf<TvFrame>(TvFrame.PairRefused(PairRefusal.BadProof)), r.frames)
            assertTrue(s.state is PairingUiState.ShowingCode)
        }
        s.onFrame(bad, 1)
        assertEquals(PairingUiState.Ended(EndReason.TooManyBadProofs), s.state)
        // Even the right proof no longer works.
        assertTrue(s.onFrame(pair(), 2).close)
        assertTrue(s.state is PairingUiState.Ended)
    }

    @Test fun malformedHexAndUnknownStationDoNotPair() {
        val s = session()
        s.onFrame(pair(p = "z".repeat(64)), 1)
        s.onFrame(pair(station = "nope"), 1)
        assertTrue(s.state is PairingUiState.ShowingCode)
    }

    @Test fun expiredCodeIsRefusedAtTheBoundary() {
        val s = session(expires = 1000)
        val r = s.onFrame(pair(), 1000)
        assertEquals(listOf<TvFrame>(TvFrame.PairRefused(PairRefusal.Expired)), r.frames)
        assertEquals(PairingUiState.Ended(EndReason.CodeExpired), s.state)
        val ok = session(expires = 1000)
        ok.onFrame(pair(), 999)
        assertTrue(ok.state is PairingUiState.PairedIdle)
    }

    @Test fun tickExpiresAnUnusedCodeButNotAPairedOne() {
        val s = session(expires = 1000)
        s.tick(999)
        assertTrue(s.state is PairingUiState.ShowingCode)
        s.tick(1000)
        assertEquals(PairingUiState.Ended(EndReason.CodeExpired), s.state)
        val p = session(expires = 1000).also { it.onFrame(pair(), 0) }
        p.tick(5000)
        assertTrue(p.state is PairingUiState.PairedIdle)
    }

    @Test fun framesBeforePairingAreIgnored() {
        val s = session()
        assertTrue(s.onFrame(PhoneFrame.Submitting(op), 0).frames.isEmpty())
        assertTrue(s.state is PairingUiState.ShowingCode)
    }

    @Test fun happyPathAccepted() {
        val s = waiting()
        assertEquals(PairingUiState.Waiting(track, WaitingPhase.Sent), s.state)
        s.onFrame(PhoneFrame.Awaiting(op), 1)
        assertEquals(PairingUiState.Waiting(track, WaitingPhase.PhoneConfirming), s.state)
        s.onFrame(PhoneFrame.Submitting(op), 2)
        assertEquals(PairingUiState.Waiting(track, WaitingPhase.Submitting), s.state)
        s.onFrame(PhoneFrame.Result(op, ResultCode.Accepted, null), 3)
        assertEquals(RequestOutcome.Accepted, outcome(s))
        s.acknowledgeResult()
        assertEquals(PairingUiState.PairedIdle("Pixel", "dj_x", "sst"), s.state)
    }

    @Test fun proposeSendsExactlyOneBoundedFrame() {
        val s = paired()
        val long = track.copy(title = "t".repeat(1000))
        val r = s.propose(op, long, 0)
        assertEquals(1, r.frames.size)
        assertEquals(MAX_TEXT_CHARS, (r.frames[0] as TvFrame.Propose).title.length)
        // A second propose while waiting is ignored.
        assertTrue(s.propose("f".repeat(32), track, 0).frames.isEmpty())
    }

    @Test fun proposeForAnotherStationOrBadRefIsIgnored() {
        val s = paired()
        assertTrue(s.propose(op, track.copy(station = "afm"), 0).frames.isEmpty())
        assertTrue(s.propose(op, track.copy(trackRef = ""), 0).frames.isEmpty())
        assertTrue(s.propose(op, track.copy(trackRef = "x".repeat(MAX_TRACK_REF_CHARS + 1)), 0).frames.isEmpty())
        assertTrue(s.state is PairingUiState.PairedIdle)
    }

    @Test fun refusedKeepsTheStationReason() {
        val s = waiting(WaitingPhase.Submitting)
        s.onFrame(PhoneFrame.Result(op, ResultCode.Refused, "Too soon"), 5)
        assertEquals(RequestOutcome.Refused("Too soon"), outcome(s))
    }

    @Test fun uncertainResultIsNeverShownAsAccepted() {
        val s = waiting(WaitingPhase.Submitting)
        s.onFrame(PhoneFrame.Result(op, ResultCode.Uncertain, null), 5)
        assertEquals(RequestOutcome.UncertainCheckQueue, outcome(s))
    }

    @Test fun declinedBeforeSubmittingMeansNothingWasSubmitted() {
        val s = waiting(WaitingPhase.PhoneConfirming)
        s.onFrame(PhoneFrame.Declined(op), 5)
        assertEquals(RequestOutcome.Declined, outcome(s))
    }

    @Test fun declinedAfterSubmittingIsIgnored() {
        val s = waiting(WaitingPhase.Submitting)
        s.onFrame(PhoneFrame.Declined(op), 5)
        assertEquals(WaitingPhase.Submitting, (s.state as PairingUiState.Waiting).phase)
    }

    @Test fun resultWithoutSubmittingIsNeverBelieved() {
        for (phase in listOf(null, WaitingPhase.PhoneConfirming)) {
            val s = waiting(phase)
            s.onFrame(PhoneFrame.Result(op, ResultCode.Accepted, null), 5)
            assertTrue(phase.toString(), s.state is PairingUiState.Waiting)
        }
    }

    @Test fun framesForAnotherOperationAreIgnored() {
        val s = waiting()
        val other = "f".repeat(32)
        s.onFrame(PhoneFrame.Awaiting(other), 1)
        s.onFrame(PhoneFrame.Submitting(other), 1)
        s.onFrame(PhoneFrame.Result(other, ResultCode.Accepted, null), 1)
        assertEquals(PairingUiState.Waiting(track, WaitingPhase.Sent), s.state)
    }

    @Test fun duplicateAndLateFramesDoNotChangeAFinishedResult() {
        val s = waiting(WaitingPhase.Submitting)
        s.onFrame(PhoneFrame.Result(op, ResultCode.Refused, "no"), 5)
        s.onFrame(PhoneFrame.Result(op, ResultCode.Accepted, null), 6)
        s.onFrame(PhoneFrame.Submitting(op), 7)
        assertEquals(RequestOutcome.Refused("no"), outcome(s))
    }

    @Test fun disconnectBeforeSubmittingMeansNothingWasSubmitted() {
        for (phase in listOf(null, WaitingPhase.PhoneConfirming)) {
            val s = waiting(phase)
            assertTrue(s.onDisconnect().close)
            assertEquals(RequestOutcome.PhoneUnreachableNothingSubmitted, outcome(s))
            s.acknowledgeResult()
            assertEquals(PairingUiState.Ended(EndReason.Disconnected), s.state)
        }
    }

    @Test fun disconnectAfterSubmittingIsUnknownAndNeverResent() {
        val s = waiting(WaitingPhase.Submitting)
        val r = s.onDisconnect()
        assertTrue(r.frames.isEmpty())
        assertEquals(RequestOutcome.UnknownCheckQueue, outcome(s))
    }

    @Test fun timeoutsFollowTheSamePhaseRule() {
        val sent = waiting()
        sent.tick(timeouts.confirmStartMillis - 1); assertTrue(sent.state is PairingUiState.Waiting)
        sent.tick(timeouts.confirmStartMillis)
        assertEquals(RequestOutcome.PhoneUnreachableNothingSubmitted, outcome(sent))

        val confirming = waiting(WaitingPhase.PhoneConfirming)
        confirming.tick(timeouts.phoneConfirmMillis - 1); assertTrue(confirming.state is PairingUiState.Waiting)
        confirming.tick(timeouts.phoneConfirmMillis)
        assertEquals(RequestOutcome.PhoneUnreachableNothingSubmitted, outcome(confirming))

        val submitting = waiting(WaitingPhase.Submitting)
        submitting.tick(timeouts.submitResultMillis - 1); assertTrue(submitting.state is PairingUiState.Waiting)
        submitting.tick(timeouts.submitResultMillis)
        assertEquals(RequestOutcome.UnknownCheckQueue, outcome(submitting))
    }

    @Test fun phaseClockRestartsAtEachPhase() {
        val s = waiting(now = 0)
        s.onFrame(PhoneFrame.Awaiting(op), 10_000)
        s.tick(10_000 + timeouts.confirmStartMillis) // would have expired under the Sent limit
        assertTrue(s.state is PairingUiState.Waiting)
    }

    @Test fun stateChangedEndsThePairingAndRevokes() {
        val s = waiting(WaitingPhase.PhoneConfirming)
        val r = s.onFrame(PhoneFrame.StateChanged("afm", "other"), 3)
        assertEquals(listOf<TvFrame>(TvFrame.Revoke), r.frames)
        assertTrue(r.close)
        assertEquals(PairingUiState.Ended(EndReason.StateChanged), s.state)
    }

    @Test fun phoneRevokeEndsWithoutEchoing() {
        val s = paired()
        val r = s.onFrame(PhoneFrame.Revoke, 3)
        assertTrue(r.frames.isEmpty() && r.close)
        assertEquals(PairingUiState.Ended(EndReason.RevokedByPhone), s.state)
    }

    @Test fun tvRevokeSendsRevokeAndIsIdempotent() {
        val s = paired()
        val r = s.revoke()
        assertEquals(listOf<TvFrame>(TvFrame.Revoke), r.frames)
        assertEquals(PairingUiState.Ended(EndReason.RevokedByTv), s.state)
        assertTrue(s.revoke().frames.isEmpty())
        assertFalse(s.isPaired)
    }

    @Test fun pingIsAnsweredAndPongIgnored() {
        val s = paired()
        assertEquals(listOf<TvFrame>(TvFrame.Pong), s.onFrame(PhoneFrame.Ping, 0).frames)
        assertTrue(s.onFrame(PhoneFrame.Pong, 0).frames.isEmpty())
    }

    @Test fun anotherProposalIsAllowedAfterAResult() {
        val s = waiting(WaitingPhase.Submitting)
        s.onFrame(PhoneFrame.Result(op, ResultCode.Accepted, null), 3)
        val next = "e".repeat(32)
        assertEquals(1, s.propose(next, track, 4).frames.size)
        s.onFrame(PhoneFrame.Result(op, ResultCode.Refused, "old"), 5) // old op stays ignored
        assertEquals(WaitingPhase.Sent, (s.state as PairingUiState.Waiting).phase)
    }
}
