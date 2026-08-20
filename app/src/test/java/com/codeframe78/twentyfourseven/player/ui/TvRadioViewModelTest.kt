package com.codeframe78.twentyfourseven.player.ui

import com.codeframe78.twentyfourseven.player.data.BootstrapStationRepository
import com.codeframe78.twentyfourseven.player.domain.NowPlayingRepository
import com.codeframe78.twentyfourseven.player.domain.NowPlayingState
import com.codeframe78.twentyfourseven.player.domain.PlaybackController
import com.codeframe78.twentyfourseven.player.domain.PlaybackState
import com.codeframe78.twentyfourseven.player.domain.Station
import com.codeframe78.twentyfourseven.player.domain.StationId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvRadioViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `TV state exposes only selected station playback and matching now playing`() = runTest(dispatcher) {
        val nowPlaying = FakeNowPlayingRepository()
        val playback = FakePlaybackController()
        val viewModel = TvRadioViewModel(BootstrapStationRepository(), playback, nowPlaying)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertEquals("sst", playback.selectedStation?.id?.value)
        nowPlaying.state.value = NowPlayingState(StationId("afm"), "Other station")
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.nowPlaying.displayTitle)

        viewModel.selectStation(StationId("afm"))
        advanceUntilIdle()
        assertEquals("afm", playback.selectedStation?.id?.value)
        assertEquals("Other station", viewModel.uiState.value.nowPlaying.displayTitle)
    }

    @Test
    fun `TV playback actions delegate without authenticated dependencies`() = runTest(dispatcher) {
        val playback = FakePlaybackController()
        val viewModel = TvRadioViewModel(
            BootstrapStationRepository(),
            playback,
            FakeNowPlayingRepository(),
        )
        advanceUntilIdle()

        viewModel.play()
        viewModel.pause()
        viewModel.stop()

        assertEquals(1, playback.playCalls)
        assertEquals(1, playback.pauseCalls)
        assertEquals(1, playback.stopCalls)
    }

    private class FakeNowPlayingRepository : NowPlayingRepository {
        val state = MutableStateFlow(NowPlayingState())
        override fun observeNowPlaying() = state
    }

    private class FakePlaybackController : PlaybackController {
        override val state: StateFlow<PlaybackState> = MutableStateFlow(PlaybackState())
        var selectedStation: Station? = null
        var playCalls = 0
        var pauseCalls = 0
        var stopCalls = 0

        override fun selectStation(station: Station) {
            selectedStation = station
        }

        override fun play() {
            playCalls++
        }

        override fun pause() {
            pauseCalls++
        }

        override fun stop() {
            stopCalls++
        }

        override fun setSleepTimer(durationMillis: Long) = Unit
        override fun cancelSleepTimer() = Unit
    }
}
