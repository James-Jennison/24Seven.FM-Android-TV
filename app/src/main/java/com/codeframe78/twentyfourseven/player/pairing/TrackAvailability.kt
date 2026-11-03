package com.codeframe78.twentyfourseven.player.pairing

import java.util.Locale

/** The same wording the phone app shows beside a track: green "Request Now", otherwise why not. */
internal enum class TrackStatus(val label: String, val canRequest: Boolean = false) {
    Available("Request Now", true),
    RecentlyPlayed("Track Recently Played"),
    InQueue("Track Recently Played"),
    SignIn("Sign In to Request"),
    Cooldown("Request Cooldown Active"),
    Membership("VIP Membership Required"),
    LimitReached("Request Limit Reached"),
    Unavailable("Requests Temporarily Unavailable"),
    RequestedHere("Request Received"),
}

/** Ported from the phone app's classification of the station's own availability message. */
internal fun classifyTrackStatus(track: CatalogTrack, requestedFromThisTv: Boolean = false): TrackStatus {
    if (requestedFromThisTv) return TrackStatus.RequestedHere
    if (track.eligible) return TrackStatus.Available
    val text = track.note?.trim()?.lowercase(Locale.ROOT).orEmpty()
    return when {
        text.isEmpty() -> TrackStatus.Unavailable
        listOf("last played", "played recently", "recently played", "requestable again").any(text::contains) -> TrackStatus.RecentlyPlayed
        listOf("track is already in queue", "track is already in the queue", "track is currently in queue").any(text::contains) -> TrackStatus.InQueue
        listOf("sign in", "log in", "login").any(text::contains) -> TrackStatus.SignIn
        listOf("vip", "membership", "member only").any(text::contains) -> TrackStatus.Membership
        listOf("request limit", "maximum requests").any(text::contains) -> TrackStatus.LimitReached
        listOf("cooldown", "wait before", "too soon").any(text::contains) -> TrackStatus.Cooldown
        else -> TrackStatus.Unavailable
    }
}
