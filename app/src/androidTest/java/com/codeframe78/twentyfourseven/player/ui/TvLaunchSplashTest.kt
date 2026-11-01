package com.codeframe78.twentyfourseven.player.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The in-app launch splash must finish exactly once, not before its hold has run, and not hang on. Time is driven by
 * the Compose test clock, so these checks are deterministic. The splash is removed from composition when it finishes,
 * as MainActivity does, so a second completion would show up as a count above one. The never-drawn cap path is covered
 * by the JVM test TvLaunchSplashCapTest because a composed splash always draws.
 */
class TvLaunchSplashTest {
    @get:Rule val compose = createComposeRule()

    private val visibleMillis = TV_LAUNCH_SPLASH_TRANSITION_MILLIS + TV_LAUNCH_SPLASH_HOLD_MILLIS
    private val fadeMillis = TV_LAUNCH_SPLASH_FADE_MILLIS.toLong()

    private fun setSplash(onFinish: () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            var visible by remember { mutableStateOf(true) }
            if (visible) TvLaunchSplash(onFinished = { visible = false; onFinish() })
        }
    }

    @Test fun staysUpThroughTheHoldThenFinishesOnce() {
        var finished = 0
        setSplash { finished++ }

        compose.mainClock.advanceTimeBy(visibleMillis - 200)
        assertEquals("must not finish while the mark is still held", 0, finished)

        compose.mainClock.advanceTimeBy(200 + fadeMillis + 200)
        assertEquals("finishes after hold and fade", 1, finished)

        compose.mainClock.advanceTimeBy(TV_LAUNCH_SPLASH_MAX_MILLIS + TV_LAUNCH_SPLASH_DRAWN_MAX_MILLIS)
        assertEquals("exactly one completion, even long after both caps would have elapsed", 1, finished)
    }

    @Test fun finishesWithinTheDrawnPathBoundNotTheCap() {
        var finished = 0
        setSplash { finished++ }

        compose.mainClock.advanceTimeBy(visibleMillis - 200)
        assertEquals(0, finished)
        compose.mainClock.advanceTimeBy(200 + fadeMillis + 500)
        assertEquals(1, finished)
    }
}
