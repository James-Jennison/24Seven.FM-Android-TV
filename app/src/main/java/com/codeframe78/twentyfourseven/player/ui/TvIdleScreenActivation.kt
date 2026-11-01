package com.codeframe78.twentyfourseven.player.ui

import kotlinx.coroutines.delay
import com.codeframe78.twentyfourseven.player.domain.IdleScreenStartDelay

/** Runs in a keyed LaunchedEffect so interaction and preference changes cancel pending activation. */
internal suspend fun updateTvIdleScreenActivation(
    playing: Boolean,
    menuOpen: Boolean,
    previewRequested: Boolean,
    automaticEnabled: Boolean,
    startDelay: IdleScreenStartDelay = IdleScreenStartDelay.TenMinutes,
    setActive: (Boolean) -> Unit,
) {
    if (previewRequested && !menuOpen) {
        setActive(true)
    } else {
        setActive(false)
        if (playing && !menuOpen && automaticEnabled) {
            delay(startDelay.millis)
            setActive(true)
        }
    }
}
