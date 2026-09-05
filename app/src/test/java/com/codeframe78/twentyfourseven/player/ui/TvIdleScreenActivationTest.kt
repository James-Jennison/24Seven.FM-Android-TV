package com.codeframe78.twentyfourseven.player.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvIdleScreenActivationTest {
    @Test fun automaticActivationWaitsTenMinutes() = runTest {
        var active = false
        launch { updateTvIdleScreenActivation(true, false, false, true) { active = it } }
        advanceTimeBy(TvIdleScreenDelayMillis - 1)
        assertFalse(active)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(active)
    }

    @Test fun offCancelsPendingTimerAndReenableStartsFreshDelay() = runTest {
        var active = false
        val pending = launch { updateTvIdleScreenActivation(true, false, false, true) { active = it } }
        advanceTimeBy(TvIdleScreenDelayMillis / 2)
        pending.cancel()
        updateTvIdleScreenActivation(true, false, false, false) { active = it }
        advanceTimeBy(TvIdleScreenDelayMillis * 2)
        assertFalse(active)
        launch { updateTvIdleScreenActivation(true, false, false, true) { active = it } }
        advanceTimeBy(TvIdleScreenDelayMillis - 1)
        assertFalse(active)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(active)
        updateTvIdleScreenActivation(true, false, false, false) { active = it }
        assertFalse(active)
    }

    @Test fun offStillAllowsManualPreviewAndMenusAndPausedPlaybackPreventAutomaticActivation() = runTest {
        var active = false
        updateTvIdleScreenActivation(false, false, true, false) { active = it }
        assertTrue(active)
        updateTvIdleScreenActivation(true, true, true, false) { active = it }
        assertFalse(active)
        updateTvIdleScreenActivation(false, false, false, true) { active = it }
        advanceTimeBy(TvIdleScreenDelayMillis * 2)
        assertFalse(active)
        updateTvIdleScreenActivation(true, true, false, true) { active = it }
        advanceTimeBy(TvIdleScreenDelayMillis * 2)
        assertFalse(active)
    }
}
