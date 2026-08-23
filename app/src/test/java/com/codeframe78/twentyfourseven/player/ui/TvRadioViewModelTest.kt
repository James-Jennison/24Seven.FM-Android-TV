package com.codeframe78.twentyfourseven.player.ui

import com.codeframe78.twentyfourseven.player.data.BootstrapStationRepository
import com.codeframe78.twentyfourseven.player.data.InMemoryStationPreferencesRepository
import com.codeframe78.twentyfourseven.player.data.InMemoryAudioVisualizationRepository
import com.codeframe78.twentyfourseven.player.data.InMemoryIdleScreenPreferencesRepository
import com.codeframe78.twentyfourseven.player.domain.LocalStationPreferences
import com.codeframe78.twentyfourseven.player.domain.NowPlayingRepository
import com.codeframe78.twentyfourseven.player.domain.NowPlayingState
import com.codeframe78.twentyfourseven.player.domain.HistoryTrack
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerStyle
import com.codeframe78.twentyfourseven.player.domain.PlaybackController
import com.codeframe78.twentyfourseven.player.domain.PlaybackState
import com.codeframe78.twentyfourseven.player.domain.PlaybackStatus
import com.codeframe78.twentyfourseven.player.domain.QueueLoadStatus
import com.codeframe78.twentyfourseven.player.domain.QueueRepository
import com.codeframe78.twentyfourseven.player.domain.QueueState
import com.codeframe78.twentyfourseven.player.domain.QueueTrack
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        val viewModel = TvRadioViewModel(
            BootstrapStationRepository(), playback, nowPlaying, FakeQueueRepository(),
            InMemoryIdleScreenPreferencesRepository(), InMemoryAudioVisualizationRepository(),
        )
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertEquals("sst", playback.selectedStation?.id?.value)
        assertEquals(1, playback.playCalls)
        nowPlaying.state.value = NowPlayingState(StationId("afm"), "Other station")
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.nowPlaying.displayTitle)

        viewModel.selectStation(StationId("afm"))
        advanceUntilIdle()
        assertEquals("afm", playback.selectedStation?.id?.value)
        assertEquals(2, playback.playCalls)
        assertEquals("Other station", viewModel.uiState.value.nowPlaying.displayTitle)
    }

    @Test
    fun `TV starts the persisted last station once on dashboard startup`() = runTest(dispatcher) {
        val playback = FakePlaybackController()
        val repository = BootstrapStationRepository(
            InMemoryStationPreferencesRepository(
                LocalStationPreferences(lastStationId = StationId("efm")),
            ),
        )

        TvRadioViewModel(
            repository, playback, FakeNowPlayingRepository(), FakeQueueRepository(),
            InMemoryIdleScreenPreferencesRepository(), InMemoryAudioVisualizationRepository(),
        )
        advanceUntilIdle()

        assertEquals("efm", playback.selectedStation?.id?.value)
        assertEquals(1, playback.playCalls)
    }

    @Test
    fun `TV pause delegates without authenticated dependencies`() = runTest(dispatcher) {
        val playback = FakePlaybackController()
        val viewModel = TvRadioViewModel(
            BootstrapStationRepository(),
            playback,
            FakeNowPlayingRepository(),
            FakeQueueRepository(),
            InMemoryIdleScreenPreferencesRepository(),
            InMemoryAudioVisualizationRepository(),
        )
        advanceUntilIdle()

        viewModel.pause()

        assertEquals(1, playback.pauseCalls)
    }

    @Test
    fun `TV sleep timer delegates without authenticated dependencies`() = runTest(dispatcher) {
        val playback = FakePlaybackController()
        val viewModel = TvRadioViewModel(
            BootstrapStationRepository(),
            playback,
            FakeNowPlayingRepository(),
            FakeQueueRepository(),
            InMemoryIdleScreenPreferencesRepository(),
            InMemoryAudioVisualizationRepository(),
        )
        advanceUntilIdle()

        viewModel.setSleepTimer(30L * 60L * 1_000L)
        viewModel.cancelSleepTimer()

        assertEquals(listOf(30L * 60L * 1_000L), playback.sleepTimerDurations)
        assertEquals(1, playback.cancelSleepTimerCalls)
    }

    @Test
    fun `TV idle screen exposes all thirteen styles and retains the selected visualizer`() = runTest(dispatcher) {
        val preferences = InMemoryIdleScreenPreferencesRepository()
        val viewModel = TvRadioViewModel(
            BootstrapStationRepository(),
            FakePlaybackController(),
            FakeNowPlayingRepository(),
            FakeQueueRepository(),
            preferences,
            InMemoryAudioVisualizationRepository(),
        )
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertEquals(13, IdleVisualizerStyle.entries.size)
        viewModel.selectIdleVisualizer(IdleVisualizerStyle.DjConsole)
        advanceUntilIdle()

        assertEquals(IdleVisualizerStyle.DjConsole, viewModel.uiState.value.idleScreen.visualizer)
    }

    @Test
    fun `TV diagnostics retain only allowlisted playback transition fields`() = runTest(dispatcher) {
        val playback = FakePlaybackController()
        val viewModel = TvRadioViewModel(
            BootstrapStationRepository(),
            playback,
            FakeNowPlayingRepository(),
            FakeQueueRepository(),
            InMemoryIdleScreenPreferencesRepository(),
            InMemoryAudioVisualizationRepository(),
        )
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        playback.emit(
            PlaybackState(
                status = PlaybackStatus.Error,
                errorMessage = "https://private.example/?token=not-for-diagnostics",
                networkAvailable = true,
            ),
        )
        advanceUntilIdle()

        val transition = viewModel.uiState.value.diagnosticTransitions.last()
        assertEquals(PlaybackStatus.Error, transition.playbackStatus)
        assertTrue(transition.networkAvailable)
        assertFalse(transition.toString().contains("private.example"))
        assertFalse(transition.toString().contains("not-for-diagnostics"))
    }

    @Test
    fun `TV loads anonymous Queue and History alongside Now Playing`() = runTest(dispatcher) {
        val queue = FakeQueueRepository()
        val viewModel = TvRadioViewModel(
            BootstrapStationRepository(),
            FakePlaybackController(),
            FakeNowPlayingRepository(),
            queue,
            InMemoryIdleScreenPreferencesRepository(),
            InMemoryAudioVisualizationRepository(),
        )
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertEquals(listOf(StationId("sst")), queue.observedStations)
        assertEquals("1. Public track", viewModel.uiState.value.queue?.upcoming?.single()?.let { "${it.position}. ${it.displayTitle}" })
        assertEquals("Public history", viewModel.uiState.value.queue?.recentlyPlayed?.single()?.displayTitle)
    }

    private class FakeNowPlayingRepository : NowPlayingRepository {
        val state = MutableStateFlow(NowPlayingState())
        override fun observeNowPlaying() = state
        override fun current(): NowPlayingState = state.value
    }

    private class FakePlaybackController : PlaybackController {
        private val mutableState = MutableStateFlow(PlaybackState())
        override val state: StateFlow<PlaybackState> = mutableState
        var selectedStation: Station? = null
        var playCalls = 0
        var pauseCalls = 0
        var stopCalls = 0
        val sleepTimerDurations = mutableListOf<Long>()
        var cancelSleepTimerCalls = 0

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

        override fun setSleepTimer(durationMillis: Long) {
            sleepTimerDurations += durationMillis
        }

        override fun cancelSleepTimer() {
            cancelSleepTimerCalls++
        }

        fun emit(value: PlaybackState) {
            mutableState.value = value
        }
    }

    private class FakeQueueRepository : QueueRepository {
        val observedStations = mutableListOf<StationId>()
        private val states = mutableMapOf<StationId, MutableStateFlow<QueueState>>()

        override fun observeQueue(stationId: StationId): StateFlow<QueueState> {
            observedStations += stationId
            return states.getOrPut(stationId) {
                MutableStateFlow(
                    QueueState(
                        stationId = stationId,
                        status = QueueLoadStatus.Ready,
                        upcoming = listOf(QueueTrack(1, "Public track")),
                        recentlyPlayed = listOf(HistoryTrack("Public history")),
                    ),
                )
            }
        }

        override suspend fun refresh(stationId: StationId) = Unit

        override suspend fun currentQueue(stationId: StationId): QueueState =
            states[stationId]?.value ?: QueueState(stationId)
    }
}
