package com.codeframe78.twentyfourseven.player.data

import android.content.Context
import com.codeframe78.twentyfourseven.player.domain.IdleScreenPreferences
import com.codeframe78.twentyfourseven.player.domain.IdleScreenPreferencesRepository
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerStyle
import com.codeframe78.twentyfourseven.player.domain.NowPlayingOverlayMode
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
        val updated = mutablePreferences.value.copy(visualizer = style)
        sharedPreferences.edit().putString(KEY_VISUALIZER, style.name).apply()
        mutablePreferences.value = updated
    }

    override suspend fun setNowPlayingOverlay(mode: NowPlayingOverlayMode) {
        val updated = mutablePreferences.value.copy(nowPlayingOverlay = mode)
        sharedPreferences.edit().putString(KEY_NOW_PLAYING_OVERLAY, mode.name).apply()
        mutablePreferences.value = updated
    }

    override suspend fun setReduceVisualIntensity(enabled: Boolean) {
        val updated = mutablePreferences.value.copy(reduceVisualIntensity = enabled)
        sharedPreferences.edit().putBoolean(KEY_REDUCE_VISUAL_INTENSITY, enabled).apply()
        mutablePreferences.value = updated
    }

    private fun readPreferences(): IdleScreenPreferences {
        val style = sharedPreferences.getString(KEY_VISUALIZER, null)
            ?.let { stored -> IdleVisualizerStyle.entries.firstOrNull { it.name == stored } }
            ?: IdleVisualizerStyle.NeonKaleidoscope
        val overlay = sharedPreferences.getString(KEY_NOW_PLAYING_OVERLAY, null)
            ?.let { stored -> NowPlayingOverlayMode.entries.firstOrNull { it.name == stored } }
            ?: NowPlayingOverlayMode.Brief
        return IdleScreenPreferences(
            visualizer = style,
            nowPlayingOverlay = overlay,
            reduceVisualIntensity = sharedPreferences.getBoolean(KEY_REDUCE_VISUAL_INTENSITY, false),
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "idle-screen-preferences"
        const val KEY_VISUALIZER = "visualizer"
        const val KEY_NOW_PLAYING_OVERLAY = "now-playing-overlay"
        const val KEY_REDUCE_VISUAL_INTENSITY = "reduce-visual-intensity"
    }
}

class InMemoryIdleScreenPreferencesRepository(
    initial: IdleScreenPreferences = IdleScreenPreferences(),
) : IdleScreenPreferencesRepository {
    private val mutablePreferences = MutableStateFlow(initial)
    override val preferences: StateFlow<IdleScreenPreferences> = mutablePreferences.asStateFlow()

    override suspend fun setVisualizer(style: IdleVisualizerStyle) {
        mutablePreferences.value = mutablePreferences.value.copy(visualizer = style)
    }

    override suspend fun setNowPlayingOverlay(mode: NowPlayingOverlayMode) {
        mutablePreferences.value = mutablePreferences.value.copy(nowPlayingOverlay = mode)
    }

    override suspend fun setReduceVisualIntensity(enabled: Boolean) {
        mutablePreferences.value = mutablePreferences.value.copy(reduceVisualIntensity = enabled)
    }
}
