package com.codeframe78.twentyfourseven.player.pairing

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RequestSubmitterTest {
    private val good = GateAttestation(adult = true, termsVersion = CURRENT_COMMUNITY_TERMS_VERSION, communityVisible = true)
    private fun session(gate: GateAttestation = good, station: String = "sst") =
        StationSession(station, "dj_x", listOf(StoredCookie("PHPSESSID", "secret-value", null)), gate)
    private val track = CatalogTrack("B00000JC79", "224947", "Peter Gunn", "Henry Mancini", "Peter Gunn", "2:00", true)
    private var calls = 0
    private var requested: String? = null
    private fun submitter(enabled: Boolean = true, html: String? = "<p>Request Successful</p>", queued: Boolean = true, fail: Boolean = false) =
        RequestSubmitter(enabled, { uri, _, _ -> calls++; requested = uri.toString(); if (fail) throw IOException("x"); html!! }, { _, _, _ -> queued })

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
}
