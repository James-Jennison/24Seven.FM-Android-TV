package com.codeframe78.twentyfourseven.player.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The first-draw wait is what bounds how long a splash that never draws can hold keys back. */
@OptIn(ExperimentalCoroutinesApi::class)
class TvLaunchSplashCapTest {
    @Test fun neverDrawnGivesUpAtTheCap() = runTest {
        val firstDraw = CompletableDeferred<Unit>()
        var result: Boolean? = null
        val job = launch { result = awaitSplashFirstDraw(firstDraw, TV_LAUNCH_SPLASH_MAX_MILLIS) }
        advanceTimeBy(TV_LAUNCH_SPLASH_MAX_MILLIS - 1)
        runCurrent()
        assertNull("still waiting just before the cap", result)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(false, result)
        assertEquals("virtual time spent is exactly the cap", TV_LAUNCH_SPLASH_MAX_MILLIS, currentTime)
        assertTrue(job.isCompleted)
    }

    @Test fun drawnBeforeTheCapReturnsTrueImmediately() = runTest {
        val firstDraw = CompletableDeferred<Unit>().apply { complete(Unit) }
        assertTrue(awaitSplashFirstDraw(firstDraw, TV_LAUNCH_SPLASH_MAX_MILLIS))
        assertEquals(0L, currentTime)
    }

    @Test fun drawnJustBeforeTheCapStillCounts() = runTest {
        val firstDraw = CompletableDeferred<Unit>()
        var result: Boolean? = null
        launch { result = awaitSplashFirstDraw(firstDraw, TV_LAUNCH_SPLASH_MAX_MILLIS) }
        advanceTimeBy(TV_LAUNCH_SPLASH_MAX_MILLIS - 10)
        firstDraw.complete(Unit)
        runCurrent()
        assertEquals(true, result)
        advanceTimeBy(TV_LAUNCH_SPLASH_MAX_MILLIS)
        runCurrent()
        assertEquals("a late cap must not overwrite the drawn result", true, result)
    }
}
