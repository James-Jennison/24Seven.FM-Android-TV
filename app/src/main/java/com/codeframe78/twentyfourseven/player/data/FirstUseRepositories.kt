package com.codeframe78.twentyfourseven.player.data

import android.content.Context
import com.codeframe78.twentyfourseven.player.domain.FirstUseRepository
import com.codeframe78.twentyfourseven.player.domain.StationId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SharedPreferencesFirstUseRepository(context: Context) : FirstUseRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(preferences.getBoolean(KEY_COMPLETED, false))

    override val hasCompletedFirstUse: Boolean
        get() = state.value

    override val pendingStationId: StationId?
        get() = preferences.getString(KEY_PENDING_STATION_ID, null)?.let(::StationId)

    override fun observeCompleted(): Flow<Boolean> = state.asStateFlow()

    override suspend fun setPendingStation(stationId: StationId) {
        preferences.edit().putString(KEY_PENDING_STATION_ID, stationId.value).apply()
    }

    override suspend fun markCompleted() {
        preferences.edit().putBoolean(KEY_COMPLETED, true).apply()
        state.value = true
    }

    private companion object {
        const val PREFERENCES_NAME = "tv_first_use"
        const val KEY_COMPLETED = "completed"
        const val KEY_PENDING_STATION_ID = "pending_station_id"
    }
}

class InMemoryFirstUseRepository(
    initiallyCompleted: Boolean = true,
) : FirstUseRepository {
    private val state = MutableStateFlow(initiallyCompleted)

    override val hasCompletedFirstUse: Boolean
        get() = state.value

    override var pendingStationId: StationId? = null
        private set

    override fun observeCompleted(): Flow<Boolean> = state.asStateFlow()

    override suspend fun setPendingStation(stationId: StationId) {
        pendingStationId = stationId
    }

    override suspend fun markCompleted() {
        state.value = true
    }
}
