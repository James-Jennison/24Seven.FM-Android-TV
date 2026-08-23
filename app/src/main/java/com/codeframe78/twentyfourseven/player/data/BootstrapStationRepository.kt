package com.codeframe78.twentyfourseven.player.data

import com.codeframe78.twentyfourseven.player.domain.Station
import com.codeframe78.twentyfourseven.player.domain.StationId
import com.codeframe78.twentyfourseven.player.domain.StationRepository
import com.codeframe78.twentyfourseven.player.domain.LocalStationPreferences
import com.codeframe78.twentyfourseven.player.domain.StationPreferencesRepository
import com.codeframe78.twentyfourseven.player.domain.StartupStationMode
import com.codeframe78.twentyfourseven.player.domain.StreamFormat
import com.codeframe78.twentyfourseven.player.domain.StreamVariant
import com.codeframe78.twentyfourseven.player.domain.canonicalized
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

class BootstrapStationRepository(
    private val preferences: StationPreferencesRepository = InMemoryStationPreferencesRepository(),
) : StationRepository {
    private val stations = listOf(
        station(
            id = "sst",
            name = "StreamingSoundtracks.com",
            shortName = "SST",
            description = "Movie, game, TV and anime scores",
            domain = "streamingsoundtracks.com",
            logoUrl = "https://streamingsoundtracks.com/images/logos/logo-sst-v200x200.png",
            websiteDomain = "www.streamingsoundtracks.com",
        ),
        station(
            id = "1980s",
            name = "1980s.FM",
            shortName = "1980s",
            description = "Music from the 1980s",
            domain = "1980s.fm",
            logoUrl = "https://1980s.fm/images/logos/1980s_logo-200x200.png",
        ),
        station(
            id = "afm",
            name = "Adagio.FM",
            shortName = "Adagio",
            description = "Classical and light music",
            domain = "adagio.fm",
            logoUrl = "https://adagio.fm/images/logos/logo-afm-200x200.png",
        ),
        station(
            id = "dfm",
            name = "Death.FM",
            shortName = "Death",
            description = "Extreme metal",
            domain = "death.fm",
            logoUrl = "https://death.fm/images/logos/logo-dfm-200x200.png",
        ),
        station(
            id = "efm",
            name = "Entranced.FM",
            shortName = "Entranced",
            description = "Trance and electronic music",
            domain = "entranced.fm",
            logoUrl = "https://entranced.fm/images/logos/logo-efm-g200x200.png",
        ),
    )
    private val selected = MutableStateFlow(resolveStartupStation(preferences.current))

    override fun observeStations(): Flow<List<Station>> = flowOf(stations)
    override fun observeSelectedStation(): Flow<Station> = selected.asStateFlow()
    override fun currentStations(): List<Station> = stations
    override fun currentSelectedStation(): Station = selected.value
    override fun observeStationPreferences(): Flow<LocalStationPreferences> = preferences.observePreferences()

    override suspend fun selectStation(stationId: StationId) {
        val station = stations.firstOrNull { it.id == stationId.canonicalized() } ?: return
        selected.value = station
        preferences.recordLastStation(station.id)
    }

    override suspend fun useLastStationAtStartup() {
        preferences.setStartupPreference(StartupStationMode.LastSelected)
    }

    override suspend fun setStartupStation(stationId: StationId) {
        val canonical = stationId.canonicalized()
        if (stations.none { it.id == canonical }) return
        preferences.setStartupPreference(StartupStationMode.Fixed, canonical)
    }

    private fun resolveStartupStation(saved: LocalStationPreferences): Station {
        val candidates = when (saved.startupMode) {
            StartupStationMode.Fixed -> listOfNotNull(saved.defaultStationId, saved.lastStationId)
            StartupStationMode.LastSelected -> listOfNotNull(saved.lastStationId, saved.defaultStationId)
        }
        return candidates.firstNotNullOfOrNull { candidate ->
            stations.firstOrNull { it.id == candidate.canonicalized() }
        }
            ?: stations.first()
    }

    private companion object {
        fun streams(domain: String) = listOf(
            StreamVariant("https://$domain/live", "Live stream", priority = 0, format = StreamFormat.Aac, bitrateKbps = 192),
        )

        fun station(
            id: String,
            name: String,
            shortName: String,
            description: String,
            domain: String,
            logoUrl: String,
            websiteDomain: String = domain,
        ) = Station(
            id = StationId(id),
            name = name,
            shortName = shortName,
            description = description,
            logoUrl = logoUrl,
            websiteUrl = "https://$websiteDomain/",
            streams = streams(domain),
        )
    }
}
