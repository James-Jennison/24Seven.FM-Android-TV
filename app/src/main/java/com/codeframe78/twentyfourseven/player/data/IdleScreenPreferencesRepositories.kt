package com.codeframe78.twentyfourseven.player.data

import com.codeframe78.twentyfourseven.player.domain.IdleScreenStartDelay
import android.content.Context
import com.codeframe78.twentyfourseven.player.domain.IdleScreenPreferences
import com.codeframe78.twentyfourseven.player.domain.IdleScreenPreferencesRepository
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerStyle
import com.codeframe78.twentyfourseven.player.domain.IonTunnelPalette
import com.codeframe78.twentyfourseven.player.domain.LavaLampPalette
import com.codeframe78.twentyfourseven.player.domain.NowPlayingOverlayMode
import com.codeframe78.twentyfourseven.player.domain.availableStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SharedPreferencesIdleScreenPreferencesRepository(
    context: Context,
    preferencesName: String = PREFERENCES_NAME,
) : IdleScreenPreferencesRepository {
    private val sharedPreferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val mutablePreferences = MutableStateFlow(readPreferences())

    override val preferences: StateFlow<IdleScreenPreferences> = mutablePreferences.asStateFlow()

    override suspend fun setStartDelay(delay: IdleScreenStartDelay) {
        sharedPreferences.edit().putString(KEY_START_DELAY, delay.name).apply()
        mutablePreferences.value = mutablePreferences.value.copy(startDelay = delay)
    }

    override suspend fun setAutomaticIdleScreenEnabled(enabled: Boolean) {
        val updated = mutablePreferences.value.copy(automaticIdleScreenEnabled = enabled)
        sharedPreferences.edit().putBoolean(KEY_AUTOMATIC_IDLE_SCREEN_ENABLED, enabled).apply()
        mutablePreferences.value = updated
    }

    override suspend fun setVisualizer(style: IdleVisualizerStyle) {
        val updated = mutablePreferences.value.copy(visualizer = style.availableStyle)
        sharedPreferences.edit().putString(KEY_VISUALIZER, style.availableStyle.name).apply()
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

    override suspend fun setLavaLampPalette(palette: LavaLampPalette) {
        val updated = mutablePreferences.value.copy(lavaLampPalette = palette)
        sharedPreferences.edit().putString(KEY_LAVA_LAMP_PALETTE, palette.name).apply()
        mutablePreferences.value = updated
    }

    override suspend fun setIonTunnelPalette(palette: IonTunnelPalette) {
        sharedPreferences.edit().putString("ion-tunnel-palette", palette.name).apply()
        mutablePreferences.value = mutablePreferences.value.copy(ionTunnelPalette = palette)
    }

    private fun readPreferences(): IdleScreenPreferences {
        val style = sharedPreferences.getString(KEY_VISUALIZER, null)
            ?.let { stored -> IdleVisualizerStyle.entries.firstOrNull { it.name == stored } }
            ?: IdleVisualizerStyle.NeonKaleidoscope
        val overlay = sharedPreferences.getString(KEY_NOW_PLAYING_OVERLAY, null)
            ?.let { stored -> NowPlayingOverlayMode.entries.firstOrNull { it.name == stored } }
            ?: NowPlayingOverlayMode.Brief
        val lavaLampPalette = sharedPreferences.getString(KEY_LAVA_LAMP_PALETTE, null)
            ?.let { stored -> LavaLampPalette.entries.firstOrNull { it.name == stored } }
            ?: LavaLampPalette.Neon
        return IdleScreenPreferences(
            automaticIdleScreenEnabled = sharedPreferences.getBoolean(KEY_AUTOMATIC_IDLE_SCREEN_ENABLED, true),
            startDelay = sharedPreferences.getString(KEY_START_DELAY, null)
                ?.let { stored -> IdleScreenStartDelay.entries.firstOrNull { it.name == stored } }
                ?: IdleScreenStartDelay.TenMinutes,
            visualizer = style.availableStyle,
            nowPlayingOverlay = overlay,
            reduceVisualIntensity = sharedPreferences.getBoolean(KEY_REDUCE_VISUAL_INTENSITY, false),
            lavaLampPalette = lavaLampPalette,
            ionTunnelPalette = sharedPreferences.getString("ion-tunnel-palette", null)
                ?.let { stored -> IonTunnelPalette.entries.firstOrNull { it.name == stored } }
                ?: IonTunnelPalette.AmberIce,
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "idle-screen-preferences"
        const val KEY_AUTOMATIC_IDLE_SCREEN_ENABLED = "automatic-idle-screen-enabled"
        const val KEY_START_DELAY = "idle-screen-start-delay"
        const val KEY_VISUALIZER = "visualizer"
        const val KEY_NOW_PLAYING_OVERLAY = "now-playing-overlay"
        const val KEY_REDUCE_VISUAL_INTENSITY = "reduce-visual-intensity"
        const val KEY_LAVA_LAMP_PALETTE = "lava-lamp-palette"
    }
}

class InMemoryIdleScreenPreferencesRepository(
    initial: IdleScreenPreferences = IdleScreenPreferences(),
) : IdleScreenPreferencesRepository {
    private val mutablePreferences = MutableStateFlow(initial.copy(visualizer = initial.visualizer.availableStyle))
    override val preferences: StateFlow<IdleScreenPreferences> = mutablePreferences.asStateFlow()

    override suspend fun setStartDelay(delay: IdleScreenStartDelay) {
        mutablePreferences.value = mutablePreferences.value.copy(startDelay = delay)
    }

    override suspend fun setAutomaticIdleScreenEnabled(enabled: Boolean) {
        mutablePreferences.value = mutablePreferences.value.copy(automaticIdleScreenEnabled = enabled)
    }

    override suspend fun setVisualizer(style: IdleVisualizerStyle) {
        mutablePreferences.value = mutablePreferences.value.copy(visualizer = style.availableStyle)
    }

    override suspend fun setNowPlayingOverlay(mode: NowPlayingOverlayMode) {
        mutablePreferences.value = mutablePreferences.value.copy(nowPlayingOverlay = mode)
    }

    override suspend fun setReduceVisualIntensity(enabled: Boolean) {
        mutablePreferences.value = mutablePreferences.value.copy(reduceVisualIntensity = enabled)
    }

    override suspend fun setLavaLampPalette(palette: LavaLampPalette) {
        mutablePreferences.value = mutablePreferences.value.copy(lavaLampPalette = palette)
    }
    override suspend fun setIonTunnelPalette(palette: IonTunnelPalette) {
        mutablePreferences.value = mutablePreferences.value.copy(ionTunnelPalette = palette)
    }

}
