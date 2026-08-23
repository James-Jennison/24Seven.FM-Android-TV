package com.codeframe78.twentyfourseven.player.data

import android.content.Context
import com.codeframe78.twentyfourseven.player.domain.IdleScreenPreferences
import com.codeframe78.twentyfourseven.player.domain.IdleScreenPreferencesRepository
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SharedPreferencesIdleScreenPreferencesRepository(
    context: Context,
) : IdleScreenPreferencesRepository {
    private val sharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutablePreferences = MutableStateFlow(readPreferences())

    override val preferences: StateFlow<IdleScreenPreferences> = mutablePreferences.asStateFlow()

    override suspend fun setVisualizer(style: IdleVisualizerStyle) {
        val updated = IdleScreenPreferences(style)
        sharedPreferences.edit().putString(KEY_VISUALIZER, style.name).apply()
        mutablePreferences.value = updated
    }

    private fun readPreferences(): IdleScreenPreferences {
        val style = sharedPreferences.getString(KEY_VISUALIZER, null)
            ?.let { stored -> IdleVisualizerStyle.entries.firstOrNull { it.name == stored } }
            ?: IdleVisualizerStyle.NeonKaleidoscope
        return IdleScreenPreferences(style)
    }

    private companion object {
        const val PREFERENCES_NAME = "idle-screen-preferences"
        const val KEY_VISUALIZER = "visualizer"
    }
}

class InMemoryIdleScreenPreferencesRepository(
    initial: IdleScreenPreferences = IdleScreenPreferences(),
) : IdleScreenPreferencesRepository {
    private val mutablePreferences = MutableStateFlow(initial)
    override val preferences: StateFlow<IdleScreenPreferences> = mutablePreferences.asStateFlow()

    override suspend fun setVisualizer(style: IdleVisualizerStyle) {
        mutablePreferences.value = IdleScreenPreferences(style)
    }
}
