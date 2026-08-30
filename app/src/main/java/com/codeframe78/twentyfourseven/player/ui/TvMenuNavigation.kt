package com.codeframe78.twentyfourseven.player.ui

import androidx.compose.runtime.saveable.Saver

/** Typed menu graph for the dashboard's modal TV routes. */
internal enum class TvMenuDestination {
    GeneralOptions,
    SleepTimer,
    IdleScreen,
    LavaLampColours,
    AccessibilityAndMotion,
    AboutAndSupport,
    Diagnostics,
}

internal fun TvMenuDestination?.backDestination(): TvMenuDestination? = when (this) {
    null -> null
    TvMenuDestination.GeneralOptions -> null
    TvMenuDestination.SleepTimer,
    TvMenuDestination.IdleScreen,
    TvMenuDestination.LavaLampColours,
    TvMenuDestination.AccessibilityAndMotion,
    TvMenuDestination.AboutAndSupport,
    TvMenuDestination.Diagnostics,
    -> TvMenuDestination.GeneralOptions
}

internal fun TvMenuDestination?.saveableRouteToken(): String = this?.name ?: DashboardRouteToken

internal val TvMenuDestinationSaver = Saver<TvMenuDestination?, String>(
    save = { destination -> destination.saveableRouteToken() },
    restore = { token ->
        if (token == DashboardRouteToken) null
        else TvMenuDestination.entries.firstOrNull { destination -> destination.name == token }
    },
)

private const val DashboardRouteToken = "dashboard"
