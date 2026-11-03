package com.codeframe78.twentyfourseven.player.pairing

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackAvailabilityTest {
    private fun track(eligible: Boolean, note: String? = null) = CatalogTrack("A1", "1", "T", null, null, null, eligible, note)

    @Test fun eligibleIsRequestNow() = assertEquals(TrackStatus.Available, classifyTrackStatus(track(true)))
    @Test fun recentlyPlayedNote() = assertEquals(TrackStatus.RecentlyPlayed, classifyTrackStatus(track(false, "Last played 20 minutes ago")))
    @Test fun cooldownNote() = assertEquals(TrackStatus.Cooldown, classifyTrackStatus(track(false, "Request cooldown ends: 5:00")))
    @Test fun unknownNote() = assertEquals(TrackStatus.Unavailable, classifyTrackStatus(track(false, "Something else")))
    @Test fun requestedHereWinsOverEligible() = assertEquals(TrackStatus.RequestedHere, classifyTrackStatus(track(true), requestedFromThisTv = true))
}
