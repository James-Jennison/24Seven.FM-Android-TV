package com.codeframe78.twentyfourseven.player.ui

import kotlin.math.sin

/** Smooth cue envelopes shared by GLES and the Canvas compatibility path. */
internal fun concertActivity(time: Float, fixture: Int): Float {
    val cue = ((sin(time * 2f + (fixture % 3) * 2.094395f) + .25f) / 1.25f).coerceIn(0f, 1f)
    return cue * cue * (3f - 2f * cue)
}

internal fun concertAim(time: Float, fixture: Int): Float {
    val movement = .5f + .5f * sin(time)
    val anchor = (fixture - 2.5f) * .38f
    val sweep = sin(time * 4f) * 2.8f * (if (fixture < 3) 1f else -1f)
    // Alternate a held focus with a coordinated opposing sweep, without position jumps.
    return anchor + sweep * (if (fixture % 2 == 0) movement else 1f - movement)
}

internal fun concertLaserAccent(time: Float): Float {
    val cue = ((sin(time * 2f) - .82f) / .18f).coerceIn(0f, 1f)
    return cue * cue * (3f - 2f * cue)
}

internal fun concertAudioAim(time: Float, fixture: Int, bassHit: Float, snareHit: Float): Float =
    concertAim(time, fixture) + (if (fixture < 3) 1f else -1f) *
        (bassHit.coerceIn(0f, 1f) * .9f - snareHit.coerceIn(0f, 1f) * .45f)

internal fun concertLaserStrength(drive: Float, bassHit: Float, snareHit: Float): Float =
    kotlin.math.sqrt(drive.coerceIn(0f, 1f) * .45f + bassHit.coerceIn(0f, 1f) * .20f + snareHit.coerceIn(0f, 1f) * .35f)
