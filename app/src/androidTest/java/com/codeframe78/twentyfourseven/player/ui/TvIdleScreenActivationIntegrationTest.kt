package com.codeframe78.twentyfourseven.player.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState
import com.codeframe78.twentyfourseven.player.domain.IdleScreenPreferences
import com.codeframe78.twentyfourseven.player.domain.IdleScreenStartDelay
import com.codeframe78.twentyfourseven.player.domain.PlaybackState
import com.codeframe78.twentyfourseven.player.domain.PlaybackStatus
import com.codeframe78.twentyfourseven.player.domain.Station
import com.codeframe78.twentyfourseven.player.domain.StationId
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class TvIdleScreenActivationIntegrationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun realAppEffectRestartsForPreferenceChanges() {
        val station = Station(StationId("sst"), "Test station", "Test", "", null, "")
        val state = mutableStateOf(TvRadioUiState(
            stations = listOf(station), selectedStation = station,
            playback = PlaybackState(stationId = station.id, status = PlaybackStatus.Playing),
            idleScreen = IdleScreenPreferences(startDelay = IdleScreenStartDelay.OneMinute),
        ))
        val audio = MutableStateFlow(AudioVisualizationState())
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TvRadioApp(
                state = state.value, visualization = audio,
                diagnosticEnvironment = DiagnosticEnvironment(),
                onSelectStation = {}, onCompleteFirstUse = { _, _ -> },
                onSetFirstUsePendingStation = {}, onPause = {}, onSetSleepTimer = {},
                onCancelSleepTimer = {},
                onSetIdleScreenStartDelay = { delay ->
                    state.value = state.value.copy(idleScreen = state.value.idleScreen.copy(startDelay = delay))
                },
                onSetAutomaticIdleScreenEnabled = {}, onSelectIdleVisualizer = {},
                onSetNowPlayingOverlay = {}, onSetReduceVisualIntensity = {},
                onSetLavaLampPalette = {}, onSetIonTunnelPalette = {},
            )
        }
        compose.mainClock.advanceTimeBy(30_000)
        compose.onNodeWithText("UP NEXT", substring = true, useUnmergedTree = true).assertExists()
        // Exercise the actual keyed effect, without manually cancelling its coroutine.
        compose.runOnIdle {
            state.value = state.value.copy(idleScreen = state.value.idleScreen.copy(startDelay = IdleScreenStartDelay.TwoMinutes))
        }
        // Allow four display frames for recomposition/effect restart. Check 1s before
        // and 100ms after expiry so frame rounding cannot mimic a wiring failure.
        // Exact millisecond boundaries for all six choices are tested in the helper units.
        compose.mainClock.advanceTimeBy(64)
        compose.mainClock.advanceTimeBy(119_000)
        compose.onNodeWithText("UP NEXT", substring = true, useUnmergedTree = true).assertExists()
        compose.mainClock.advanceTimeBy(1_100)
        compose.onNodeWithText("UP NEXT", substring = true, useUnmergedTree = true).assertDoesNotExist()
        // Native D-pad wake/reset is checked separately in the normal Player on the
        // physical Streamer; Compose-only injected keys do not model its GL input path.
        // A changed preference intentionally starts a fresh window even when already active.
        compose.runOnIdle {
            state.value = state.value.copy(idleScreen = state.value.idleScreen.copy(startDelay = IdleScreenStartDelay.OneMinute))
        }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("UP NEXT", substring = true, useUnmergedTree = true).assertExists()
        compose.mainClock.advanceTimeBy(59_000)
        compose.onNodeWithText("UP NEXT", substring = true, useUnmergedTree = true).assertExists()
        compose.mainClock.advanceTimeBy(1_100)
        compose.onNodeWithText("UP NEXT", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }
}
