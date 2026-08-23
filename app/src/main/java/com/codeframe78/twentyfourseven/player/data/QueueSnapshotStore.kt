package com.codeframe78.twentyfourseven.player.data

import android.content.Context
import com.codeframe78.twentyfourseven.player.domain.HistoryTrack
import com.codeframe78.twentyfourseven.player.domain.QueueLoadStatus
import com.codeframe78.twentyfourseven.player.domain.QueueState
import com.codeframe78.twentyfourseven.player.domain.QueueTrack
import com.codeframe78.twentyfourseven.player.domain.StationId
import org.json.JSONArray
import org.json.JSONObject

internal interface QueueSnapshotStore {
    fun read(stationId: StationId): QueueState?
    fun write(state: QueueState)
}

internal object NoOpQueueSnapshotStore : QueueSnapshotStore {
    override fun read(stationId: StationId): QueueState? = null
    override fun write(state: QueueState) = Unit
}

/** Caches only the five anonymous rows visible in each TV column for instant restart rendering. */
internal class SharedPreferencesQueueSnapshotStore(context: Context) : QueueSnapshotStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(stationId: StationId): QueueState? = runCatching {
        val encoded = preferences.getString(stationId.value, null) ?: return null
        val document = JSONObject(encoded)
        QueueState(
            stationId = stationId,
            status = QueueLoadStatus.Ready,
            upcoming = document.optJSONArray(KEY_UPCOMING).toQueueTracks(),
            recentlyPlayed = document.optJSONArray(KEY_RECENT).toHistoryTracks(),
            isStale = true,
        ).takeIf { it.upcoming.isNotEmpty() || it.recentlyPlayed.isNotEmpty() }
    }.getOrNull()

    override fun write(state: QueueState) {
        if (state.status != QueueLoadStatus.Ready) return
        val document = JSONObject()
            .put(KEY_UPCOMING, JSONArray().apply { state.upcoming.take(VISIBLE_ROW_LIMIT).forEach { put(it.toJson()) } })
            .put(KEY_RECENT, JSONArray().apply { state.recentlyPlayed.take(VISIBLE_ROW_LIMIT).forEach { put(it.toJson()) } })
        preferences.edit().putString(state.stationId.value, document.toString()).apply()
    }

    private fun JSONArray?.toQueueTracks(): List<QueueTrack> = buildList {
        this@toQueueTracks ?: return@buildList
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val title = item.optString(KEY_TITLE).takeIf(String::isNotBlank) ?: continue
            add(QueueTrack(
                position = item.optInt(KEY_POSITION, index + 1),
                displayTitle = title,
                artistName = item.optString(KEY_ARTIST).takeIf(String::isNotBlank),
                albumTitle = item.optString(KEY_ALBUM).takeIf(String::isNotBlank),
                durationLabel = item.optString(KEY_DURATION).takeIf(String::isNotBlank),
                artworkUrl = item.optString(KEY_ARTWORK).takeIf(String::isNotBlank),
            ))
        }
    }

    private fun JSONArray?.toHistoryTracks(): List<HistoryTrack> = buildList {
        this@toHistoryTracks ?: return@buildList
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val title = item.optString(KEY_TITLE).takeIf(String::isNotBlank) ?: continue
            add(HistoryTrack(
                displayTitle = title,
                artistName = item.optString(KEY_ARTIST).takeIf(String::isNotBlank),
                albumTitle = item.optString(KEY_ALBUM).takeIf(String::isNotBlank),
                durationLabel = item.optString(KEY_DURATION).takeIf(String::isNotBlank),
                artworkUrl = item.optString(KEY_ARTWORK).takeIf(String::isNotBlank),
            ))
        }
    }

    private fun QueueTrack.toJson() = JSONObject()
        .put(KEY_POSITION, position)
        .put(KEY_TITLE, displayTitle)
        .put(KEY_ARTIST, artistName)
        .put(KEY_ALBUM, albumTitle)
        .put(KEY_DURATION, durationLabel)
        .put(KEY_ARTWORK, artworkUrl)

    private fun HistoryTrack.toJson() = JSONObject()
        .put(KEY_TITLE, displayTitle)
        .put(KEY_ARTIST, artistName)
        .put(KEY_ALBUM, albumTitle)
        .put(KEY_DURATION, durationLabel)
        .put(KEY_ARTWORK, artworkUrl)

    private companion object {
        const val PREFERENCES_NAME = "public_queue_snapshot"
        const val VISIBLE_ROW_LIMIT = 5
        const val KEY_UPCOMING = "upcoming"
        const val KEY_RECENT = "recent"
        const val KEY_POSITION = "position"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_ALBUM = "album"
        const val KEY_DURATION = "duration"
        const val KEY_ARTWORK = "artwork"
    }
}
