package com.codeframe78.twentyfourseven.player

import android.app.Application
import com.codeframe78.twentyfourseven.player.data.BootstrapStationRepository
import com.codeframe78.twentyfourseven.player.data.InMemoryNowPlayingRepository
import com.codeframe78.twentyfourseven.player.data.StationNowPlayingArtworkRepository
import com.codeframe78.twentyfourseven.player.data.SharedPreferencesStationPreferencesRepository
import com.codeframe78.twentyfourseven.player.domain.NowPlayingArtworkRepository
import com.codeframe78.twentyfourseven.player.domain.NowPlayingPublisher
import com.codeframe78.twentyfourseven.player.domain.NowPlayingRepository
import com.codeframe78.twentyfourseven.player.playback.Media3PlaybackController

class RadioApplication : Application() {
    val appContainer by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
    private val nowPlayingStore = InMemoryNowPlayingRepository()
    private val stationPreferences = SharedPreferencesStationPreferencesRepository(application)

    val stationRepository = BootstrapStationRepository(stationPreferences)
    val nowPlayingRepository: NowPlayingRepository = nowPlayingStore
    val nowPlayingPublisher: NowPlayingPublisher = nowPlayingStore
    private val stationNowPlayingRepository = StationNowPlayingArtworkRepository()
    val nowPlayingArtworkRepository: NowPlayingArtworkRepository = stationNowPlayingRepository
    val playbackController by lazy {
        Media3PlaybackController(application)
    }
}
