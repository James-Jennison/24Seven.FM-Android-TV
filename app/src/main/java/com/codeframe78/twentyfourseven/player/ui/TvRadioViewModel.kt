package com.codeframe78.twentyfourseven.player.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.codeframe78.twentyfourseven.player.domain.NowPlayingRepository
import com.codeframe78.twentyfourseven.player.domain.NowPlayingState
import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationRepository
import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState
import com.codeframe78.twentyfourseven.player.domain.IdleScreenPreferences
import com.codeframe78.twentyfourseven.player.domain.IdleScreenPreferencesRepository
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerStyle
import com.codeframe78.twentyfourseven.player.domain.NowPlayingOverlayMode
import com.codeframe78.twentyfourseven.player.domain.PlaybackController
import com.codeframe78.twentyfourseven.player.domain.PlaybackState
import com.codeframe78.twentyfourseven.player.domain.QueueRepository
import com.codeframe78.twentyfourseven.player.domain.QueueState
import com.codeframe78.twentyfourseven.player.domain.Station
import com.codeframe78.twentyfourseven.player.domain.StationId
import com.codeframe78.twentyfourseven.player.domain.StationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Listener-only state for Android TV's anonymous playback surface. */
data class TvRadioUiState(
    val stations: List<Station> = emptyList(),
    val selectedStation: Station? = null,
    val playback: PlaybackState = PlaybackState(),
    val nowPlaying: NowPlayingState = NowPlayingState(),
    val queue: QueueState? = null,
    val diagnosticTransitions: List<DiagnosticTransition> = emptyList(),
    val idleScreen: IdleScreenPreferences = IdleScreenPreferences(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class TvRadioViewModel(
    private val stations: StationRepository,
    private val playback: PlaybackController,
    nowPlaying: NowPlayingRepository,
    private val queue: QueueRepository,
    private val idleScreenPreferences: IdleScreenPreferencesRepository,
    visualizationRepository: AudioVisualizationRepository,
) : ViewModel() {
    private val initialSelectedStation = stations.currentSelectedStation()
    private val initialUiState = TvRadioUiState(
        stations = stations.currentStations(),
        selectedStation = initialSelectedStation,
        nowPlaying = nowPlaying.current().takeIf { it.stationId == initialSelectedStation?.id }
            ?: NowPlayingState(stationId = initialSelectedStation?.id),
        queue = initialSelectedStation?.let { queue.cachedQueue(it.id) },
    )
    private val selectedQueue = stations.observeSelectedStation()
        .flatMapLatest { station -> queue.observeQueue(station.id) }
        // The first remote queue poll must never hold back station selection or a restored
        // now-playing snapshot. A non-matching placeholder simply leaves the columns loading.
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            QueueState(stationId = StationId("startup")),
        )

    private val listenerContent = combine(
        stations.observeStations(),
        stations.observeSelectedStation(),
        playback.state,
        nowPlaying.observeNowPlaying(),
        ::TvListenerContent,
    )

    private val diagnosticTransitions = playback.state
        .map { state ->
            DiagnosticTransition(
                playbackStatus = state.status,
                networkAvailable = state.networkAvailable,
                audioOutputKind = state.audioOutput.kind,
            )
        }
        .distinctUntilChanged()
        .runningFold(emptyList<DiagnosticTransition>()) { history, transition ->
            (history + transition).takeLast(TvDiagnosticTransitionLimit)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val idlePreferences = idleScreenPreferences.preferences

    val visualization: StateFlow<AudioVisualizationState> = visualizationRepository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AudioVisualizationState())

    val uiState: StateFlow<TvRadioUiState> = combine(
        listenerContent,
        selectedQueue,
        diagnosticTransitions,
        idlePreferences,
    ) { content, selectedQueueState, transitions, idlePreferences ->
        TvRadioUiState(
            stations = content.stations,
            selectedStation = content.selectedStation,
            playback = content.playback,
            nowPlaying = content.nowPlaying.takeIf { it.stationId == content.selectedStation.id }
                ?: NowPlayingState(stationId = content.selectedStation.id),
            queue = selectedQueueState.takeIf { it.stationId == content.selectedStation.id },
            diagnosticTransitions = transitions,
            idleScreen = idlePreferences,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialUiState)

    init {
        viewModelScope.launch {
            var hasStartedInitialStation = false
            stations.observeSelectedStation().collect { station ->
                playback.selectStation(station)
                if (!hasStartedInitialStation) {
                    hasStartedInitialStation = true
                    playback.play()
                }
            }
        }
    }

    fun selectStation(id: StationId) = viewModelScope.launch {
        stations.selectStation(id)
        playback.play()
    }

    fun pause() = playback.pause()

    fun setSleepTimer(durationMillis: Long) = playback.setSleepTimer(durationMillis)

    fun cancelSleepTimer() = playback.cancelSleepTimer()

    fun selectIdleVisualizer(style: IdleVisualizerStyle) = viewModelScope.launch {
        idleScreenPreferences.setVisualizer(style)
    }

    fun setNowPlayingOverlay(mode: NowPlayingOverlayMode) = viewModelScope.launch {
        idleScreenPreferences.setNowPlayingOverlay(mode)
    }

    fun setReduceVisualIntensity(enabled: Boolean) = viewModelScope.launch {
        idleScreenPreferences.setReduceVisualIntensity(enabled)
    }

    class Factory(
        private val stations: StationRepository,
        private val playback: PlaybackController,
        private val nowPlaying: NowPlayingRepository,
        private val queue: QueueRepository,
        private val idleScreenPreferences: IdleScreenPreferencesRepository,
        private val visualization: AudioVisualizationRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TvRadioViewModel(
                stations,
                playback,
                nowPlaying,
                queue,
                idleScreenPreferences,
                visualization,
            ) as T
    }
}

private data class TvListenerContent(
    val stations: List<Station>,
    val selectedStation: Station,
    val playback: PlaybackState,
    val nowPlaying: NowPlayingState,
)

private const val TvDiagnosticTransitionLimit = 5
