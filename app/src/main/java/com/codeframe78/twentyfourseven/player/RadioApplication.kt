package com.codeframe78.twentyfourseven.player

import android.app.Application
import com.codeframe78.twentyfourseven.player.data.BootstrapStationRepository
import com.codeframe78.twentyfourseven.player.data.InMemoryAudioVisualizationRepository
import com.codeframe78.twentyfourseven.player.data.PersistedNowPlayingRepository
import com.codeframe78.twentyfourseven.player.data.PlayerQueueRemoteDataSource
import com.codeframe78.twentyfourseven.player.data.PollingQueueRepository
import com.codeframe78.twentyfourseven.player.data.SharedPreferencesQueueSnapshotStore
import com.codeframe78.twentyfourseven.player.data.SharedPreferencesIdleScreenPreferencesRepository
import com.codeframe78.twentyfourseven.player.data.SharedPreferencesFirstUseRepository
import com.codeframe78.twentyfourseven.player.data.StationNowPlayingArtworkRepository
import com.codeframe78.twentyfourseven.player.data.SharedPreferencesStationPreferencesRepository
import com.codeframe78.twentyfourseven.player.domain.NowPlayingArtworkRepository
import com.codeframe78.twentyfourseven.player.domain.NowPlayingDetailsRepository
import com.codeframe78.twentyfourseven.player.domain.NowPlayingPublisher
import com.codeframe78.twentyfourseven.player.domain.NowPlayingRepository
import com.codeframe78.twentyfourseven.player.pairing.PairingController
import com.codeframe78.twentyfourseven.player.playback.Media3PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RadioApplication : Application() {
    val appContainer by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val nowPlayingStore = PersistedNowPlayingRepository(application)
    private val stationPreferences = SharedPreferencesStationPreferencesRepository(application)
    val firstUseRepository = SharedPreferencesFirstUseRepository(application)
    val idleScreenPreferences = SharedPreferencesIdleScreenPreferencesRepository(application)
    val audioVisualizationRepository = InMemoryAudioVisualizationRepository()

    val stationRepository = BootstrapStationRepository(stationPreferences)
    val nowPlayingRepository: NowPlayingRepository = nowPlayingStore
    val nowPlayingPublisher: NowPlayingPublisher = nowPlayingStore
    private val stationNowPlayingRepository = StationNowPlayingArtworkRepository()
    val nowPlayingArtworkRepository: NowPlayingArtworkRepository = stationNowPlayingRepository
    val nowPlayingDetailsRepository: NowPlayingDetailsRepository = stationNowPlayingRepository
    /** Public, anonymous Queue/History data. It never uses a station account or cookie jar. */
    val queueRepository by lazy {
        PollingQueueRepository(
            remote = PlayerQueueRemoteDataSource(),
            snapshots = SharedPreferencesQueueSnapshotStore(application),
        )
    }
    /** Created on first use; with the PAIRING_ENABLED flag off it never starts a listener. */
    internal val pairingController by lazy {
        PairingController(application, applicationScope, BuildConfig.PAIRING_ENABLED)
    }
    val playbackController by lazy {
        Media3PlaybackController(application)
    }

    init {
        // A build with the companion feature off must not keep a station login from an earlier build: nothing in it could clear one.
        if (!BuildConfig.PAIRING_ENABLED) {
            runCatching { com.codeframe78.twentyfourseven.player.pairing.AndroidKeystoreStationSessionStore(application).clearAll() }
        }
        // Prime public metadata independently of the live stream's first ICY metadata block.
        applicationScope.launch {
            stationRepository.observeSelectedStation().collectLatest { station ->
                runCatching { nowPlayingDetailsRepository.fetchNowPlaying(station.id) }
                    .getOrNull()
                    ?.let(nowPlayingPublisher::publish)
            }
        }
    }
}
