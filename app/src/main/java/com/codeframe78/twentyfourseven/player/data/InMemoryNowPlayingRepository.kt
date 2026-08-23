package com.codeframe78.twentyfourseven.player.data

import android.content.Context
import com.codeframe78.twentyfourseven.player.domain.NowPlayingPublisher
import com.codeframe78.twentyfourseven.player.domain.NowPlayingRepository
import com.codeframe78.twentyfourseven.player.domain.NowPlayingState
import com.codeframe78.twentyfourseven.player.domain.StationId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryNowPlayingRepository : NowPlayingRepository, NowPlayingPublisher {
    private val state = MutableStateFlow(NowPlayingState())

    override fun observeNowPlaying(): Flow<NowPlayingState> = state.asStateFlow()

    override fun current(): NowPlayingState = state.value

    override fun publish(state: NowPlayingState) {
        this.state.value = state
    }

    override fun clear(stationId: StationId?) {
        state.value = NowPlayingState(stationId = stationId)
    }

}

/**
 * Stores the last public now-playing response locally so a normal TV restart can paint useful
 * artwork and labels before the stream emits its first ICY block. It contains no account data.
 */
class PersistedNowPlayingRepository(context: Context) : NowPlayingRepository, NowPlayingPublisher {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    override fun observeNowPlaying(): Flow<NowPlayingState> = state.asStateFlow()

    override fun current(): NowPlayingState = state.value

    override fun publish(state: NowPlayingState) {
        this.state.value = state
        persist(state)
    }

    override fun clear(stationId: StationId?) {
        // Keep a populated public snapshot in memory until fresh metadata replaces it. The TV
        // ViewModel scopes it to the selected station, so an old station's snapshot is never
        // rendered for a newly selected station.
        if (state.value.stationId == null) {
            state.value = NowPlayingState(stationId = stationId)
        }
    }

    private fun read(): NowPlayingState {
        val stationId = preferences.getString(KEY_STATION_ID, null)?.let(::StationId) ?: return NowPlayingState()
        return NowPlayingState(
            stationId = stationId,
            displayTitle = preferences.getString(KEY_DISPLAY_TITLE, null),
            artworkUrl = preferences.getString(KEY_ARTWORK_URL, null),
            artist = preferences.getString(KEY_ARTIST, null),
            album = preferences.getString(KEY_ALBUM, null),
            track = preferences.getString(KEY_TRACK, null),
        )
    }

    private fun persist(state: NowPlayingState) {
        val editor = preferences.edit()
        val stationId = state.stationId
        if (stationId == null || (state.displayTitle == null && state.artworkUrl == null)) {
            editor.clear().apply()
            return
        }
        editor.putString(KEY_STATION_ID, stationId.value)
            .putString(KEY_DISPLAY_TITLE, state.displayTitle)
            .putString(KEY_ARTWORK_URL, state.artworkUrl)
            .putString(KEY_ARTIST, state.artist)
            .putString(KEY_ALBUM, state.album)
            .putString(KEY_TRACK, state.track)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "public_now_playing_snapshot"
        const val KEY_STATION_ID = "station_id"
        const val KEY_DISPLAY_TITLE = "display_title"
        const val KEY_ARTWORK_URL = "artwork_url"
        const val KEY_ARTIST = "artist"
        const val KEY_ALBUM = "album"
        const val KEY_TRACK = "track"
    }
}
