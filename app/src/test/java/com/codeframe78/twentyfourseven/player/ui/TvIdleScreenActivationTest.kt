package com.codeframe78.twentyfourseven.player.ui

import com.codeframe78.twentyfourseven.player.domain.IdleScreenStartDelay
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
    @Test fun everyConfiguredDelayWaitsUntilItsExactBoundary() = runTest {
        for (delay in IdleScreenStartDelay.entries) {
            var active = false
            launch { updateTvIdleScreenActivation(true, false, false, true, delay) { active = it } }
            advanceTimeBy(delay.millis - 1)
            assertFalse(delay.name, active)
            advanceTimeBy(1)
            runCurrent()
            assertTrue(delay.name, active)
        }
    }

    @Test fun changedDelayCancelsOldTimerAndStartsFresh() = runTest {
        var active = false
        val old = launch { updateTvIdleScreenActivation(true, false, false, true, IdleScreenStartDelay.OneMinute) { active = it } }
        advanceTimeBy(30_000)
        old.cancel()
        launch { updateTvIdleScreenActivation(true, false, false, true, IdleScreenStartDelay.TwoMinutes) { active = it } }
        advanceTimeBy(119_999)
        assertFalse(active)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(active)
    }

    @Test fun automaticActivationWaitsTenMinutes() = runTest {
        var active = false
        launch { updateTvIdleScreenActivation(true, false, false, true) { active = it } }
        advanceTimeBy(IdleScreenStartDelay.TenMinutes.millis - 1)
        assertFalse(active)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(active)
    }

    @Test fun offCancelsPendingTimerAndReenableStartsFreshDelay() = runTest {
        var active = false
        val pending = launch { updateTvIdleScreenActivation(true, false, false, true) { active = it } }
        advanceTimeBy(IdleScreenStartDelay.TenMinutes.millis / 2)
        pending.cancel()
        updateTvIdleScreenActivation(true, false, false, false) { active = it }
        advanceTimeBy(IdleScreenStartDelay.TenMinutes.millis * 2)
        assertFalse(active)
        launch { updateTvIdleScreenActivation(true, false, false, true) { active = it } }
        advanceTimeBy(IdleScreenStartDelay.TenMinutes.millis - 1)
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
        advanceTimeBy(IdleScreenStartDelay.TenMinutes.millis * 2)
        assertFalse(active)
        updateTvIdleScreenActivation(true, true, false, true) { active = it }
        advanceTimeBy(IdleScreenStartDelay.TenMinutes.millis * 2)
        assertFalse(active)
    }
}
