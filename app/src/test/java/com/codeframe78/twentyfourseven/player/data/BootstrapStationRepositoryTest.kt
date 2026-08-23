package com.codeframe78.twentyfourseven.player.data

import com.codeframe78.twentyfourseven.player.domain.LocalStationPreferences
import com.codeframe78.twentyfourseven.player.domain.StationId
import com.codeframe78.twentyfourseven.player.domain.StartupStationMode
import com.codeframe78.twentyfourseven.player.domain.StreamFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BootstrapStationRepositoryTest {
    private val repository = BootstrapStationRepository()

    @Test
    fun `TV catalog contains the five anonymous network stations`() = runTest {
        val stations = repository.observeStations().first()

        assertEquals(
            listOf("sst", "1980s", "afm", "dfm", "efm"),
            stations.map { it.id.value },
        )
        assertEquals(
            listOf(
                "https://streamingsoundtracks.com/live",
                "https://1980s.fm/live",
                "https://adagio.fm/live",
                "https://death.fm/live",
                "https://entranced.fm/live",
            ),
            stations.map { it.streams.single().url },
        )
        stations.forEach { station ->
            assertEquals(StreamFormat.Aac, station.streams.single().format)
            assertEquals(192, station.streams.single().bitrateKbps)
        }
    }

    @Test
    fun `last opened station is restored before the TV catalog emits`() = runTest {
        val preferences = InMemoryStationPreferencesRepository(
            LocalStationPreferences(
                startupMode = StartupStationMode.LastSelected,
                lastStationId = StationId("dfm"),
            ),
        )

        val selected = BootstrapStationRepository(preferences).observeSelectedStation().first()

        assertEquals(StationId("dfm"), selected.id)
    }

    @Test
    fun `station selection persists only supported station ids`() = runTest {
        val preferences = InMemoryStationPreferencesRepository()
        val repository = BootstrapStationRepository(preferences)

        repository.selectStation(StationId("afm"))
        repository.selectStation(StationId("unknown"))

        assertEquals(StationId("afm"), preferences.current.lastStationId)
    }
}
