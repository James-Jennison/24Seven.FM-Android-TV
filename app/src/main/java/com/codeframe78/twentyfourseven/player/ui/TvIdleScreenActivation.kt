package com.codeframe78.twentyfourseven.player.ui

import kotlinx.coroutines.delay

internal const val TvIdleScreenDelayMillis = 10 * 60 * 1_000L

/** Runs in a keyed LaunchedEffect so interaction and preference changes cancel pending activation. */
internal suspend fun updateTvIdleScreenActivation(
    playing: Boolean,
    menuOpen: Boolean,
    previewRequested: Boolean,
    automaticEnabled: Boolean,
    setActive: (Boolean) -> Unit,
) {
    if (previewRequested && !menuOpen) {
        setActive(true)
    } else {
        setActive(false)
        if (playing && !menuOpen && automaticEnabled) {
            delay(TvIdleScreenDelayMillis)
            setActive(true)
        }
    }
}
