package com.codeframe78.twentyfourseven.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvMenuNavigationTest {
    @Test
    fun `General Options backs to the dashboard`() {
        assertNull(TvMenuDestination.GeneralOptions.backDestination())
    }

    @Test
    fun `all detail menu routes back to General Options`() {
        listOf(
            TvMenuDestination.SleepTimer,
            TvMenuDestination.IdleScreen,
            TvMenuDestination.LavaLampColours,
            TvMenuDestination.AccessibilityAndMotion,
            TvMenuDestination.AboutAndSupport,
            TvMenuDestination.Diagnostics,
        ).forEach { route ->
            assertEquals(TvMenuDestination.GeneralOptions, route.backDestination())
        }
    }

    @Test
    fun `route saver preserves known routes and safely discards unknown saved values`() {
        assertEquals("dashboard", null.saveableRouteToken())
        TvMenuDestination.entries.forEach { destination ->
            assertEquals(destination.name, destination.saveableRouteToken())
            assertEquals(destination, TvMenuDestinationSaver.restore(destination.name))
        }
        assertNull(TvMenuDestinationSaver.restore("dashboard"))
        assertNull(TvMenuDestinationSaver.restore("removed-route"))
    }
}
