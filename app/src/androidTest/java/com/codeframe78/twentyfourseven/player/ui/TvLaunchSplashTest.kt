package com.codeframe78.twentyfourseven.player.ui

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The in-app launch splash must finish exactly once, not before its hold has run, and not hang on. Time is driven by
 * the Compose test clock, so these checks are deterministic. The never-drawn cap path is covered by the JVM test
 * TvLaunchSplashCapTest because a composed splash always draws.
 */
class TvLaunchSplashTest {
    @get:Rule val compose = createComposeRule()

    private val visibleMillis = TV_LAUNCH_SPLASH_TRANSITION_MILLIS + TV_LAUNCH_SPLASH_HOLD_MILLIS

    @Test fun staysUpThroughTheHoldThenFinishesOnce() {
        var finished = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { TvLaunchSplash(onFinished = { finished++ }) }

        compose.mainClock.advanceTimeBy(visibleMillis - 200)
        assertEquals("must not finish while the mark is still held", 0, finished)

        compose.mainClock.advanceTimeBy(200 + TV_LAUNCH_SPLASH_FADE_MILLIS.toLong() + 200)
        assertEquals("finishes after hold and fade", 1, finished)

        compose.mainClock.advanceTimeBy(TV_LAUNCH_SPLASH_MAX_MILLIS + 5_000)
        assertEquals("exactly one completion even after the safety cap would have elapsed", 1, finished)
    }

    @Test fun finishesWithinTheDrawnPathBoundNotTheCap() {
        var finished = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { TvLaunchSplash(onFinished = { finished++ }) }

        compose.mainClock.advanceTimeBy(visibleMillis + TV_LAUNCH_SPLASH_FADE_MILLIS.toLong() + 500)
        assertEquals(1, finished)
    }
}
