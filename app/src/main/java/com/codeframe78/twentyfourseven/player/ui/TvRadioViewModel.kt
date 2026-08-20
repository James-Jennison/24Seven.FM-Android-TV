package com.codeframe78.twentyfourseven.player.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.codeframe78.twentyfourseven.player.domain.NowPlayingRepository
import com.codeframe78.twentyfourseven.player.domain.NowPlayingState
import com.codeframe78.twentyfourseven.player.domain.PlaybackController
import com.codeframe78.twentyfourseven.player.domain.PlaybackState
import com.codeframe78.twentyfourseven.player.domain.Station
import com.codeframe78.twentyfourseven.player.domain.StationId
import com.codeframe78.twentyfourseven.player.domain.StationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Listener-only state for Android TV. Keeping it separate from [MainViewModel] prevents TV
 * startup from restoring accounts or observing any authenticated community surface.
 */
data class TvRadioUiState(
    val stations: List<Station> = emptyList(),
    val selectedStation: Station? = null,
    val playback: PlaybackState = PlaybackState(),
    val nowPlaying: NowPlayingState = NowPlayingState(),
)

class TvRadioViewModel(
    private val stations: StationRepository,
    private val playback: PlaybackController,
    nowPlaying: NowPlayingRepository,
) : ViewModel() {
    val uiState: StateFlow<TvRadioUiState> = combine(
        stations.observeStations(),
        stations.observeSelectedStation(),
        playback.state,
        nowPlaying.observeNowPlaying(),
    ) { allStations, selectedStation, playbackState, nowPlayingState ->
        TvRadioUiState(
            stations = allStations,
            selectedStation = selectedStation,
            playback = playbackState,
            nowPlaying = nowPlayingState.takeIf { it.stationId == selectedStation.id }
                ?: NowPlayingState(stationId = selectedStation.id),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TvRadioUiState())

    init {
        viewModelScope.launch {
            stations.observeSelectedStation().collect(playback::selectStation)
        }
    }

    fun selectStation(id: StationId) = viewModelScope.launch {
        stations.selectStation(id)
    }

    fun play() = playback.play()
    fun pause() = playback.pause()
    fun stop() = playback.stop()

    class Factory(
        private val stations: StationRepository,
        private val playback: PlaybackController,
        private val nowPlaying: NowPlayingRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TvRadioViewModel(stations, playback, nowPlaying) as T
    }
}
