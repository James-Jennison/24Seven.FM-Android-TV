package com.codeframe78.twentyfourseven.player.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Local-only acknowledgement for the TV's first-listen introduction. */
interface FirstUseRepository {
    val hasCompletedFirstUse: Boolean
    val pendingStationId: StationId?

    fun observeCompleted(): Flow<Boolean>
    suspend fun setPendingStation(stationId: StationId)
    suspend fun markCompleted()
}

/** Keeps existing non-production constructors listener-ready unless they opt into first-use coverage. */
object CompletedFirstUseRepository : FirstUseRepository {
    override val hasCompletedFirstUse: Boolean = true
    override val pendingStationId: StationId? = null
    override fun observeCompleted(): Flow<Boolean> = flowOf(true)
    override suspend fun setPendingStation(stationId: StationId) = Unit
    override suspend fun markCompleted() = Unit
}
