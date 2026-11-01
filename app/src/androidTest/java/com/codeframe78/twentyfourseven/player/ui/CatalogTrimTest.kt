package com.codeframe78.twentyfourseven.player.ui

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.input.key.Key
import androidx.test.core.app.ApplicationProvider
import com.codeframe78.twentyfourseven.player.data.SharedPreferencesIdleScreenPreferencesRepository
import com.codeframe78.twentyfourseven.player.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CatalogTrimTest {
    @get:Rule val compose = createComposeRule()
    @Test fun installedCatalogAndLegacyPreferencesAreSafe() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "catalog-trim-acceptance"
        val stored = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val removed = IdleVisualizerStyle.entries.filter { it !in AvailableIdleVisualizerStyles }
        assertEquals(12, removed.size)
        assertEquals(22, AvailableIdleVisualizerStyles.size)
        try {
            for (style in IdleVisualizerStyle.entries) {
                stored.edit().clear().putString("visualizer", style.name)
                    .putBoolean("automatic-idle-screen-enabled", false).commit()
                val repo = SharedPreferencesIdleScreenPreferencesRepository(context, name)
                assertTrue(repo.preferences.value.visualizer in AvailableIdleVisualizerStyles)
                if (style in AvailableIdleVisualizerStyles) assertEquals(style, repo.preferences.value.visualizer)
                assertFalse(repo.preferences.value.automaticIdleScreenEnabled)
                repo.setVisualizer(style)
                val restored = SharedPreferencesIdleScreenPreferencesRepository(context, name)
                assertEquals(repo.preferences.value.visualizer, restored.preferences.value.visualizer)
                assertTrue(stored.getString("visualizer", null) in AvailableIdleVisualizerStyles.map { it.name })
            }
            for (style in AvailableIdleVisualizerStyles) {
                style.videoAsset()?.takeIf { it.usesBundledVideo }?.let { asset -> context.assets.open("visualizers/${asset.fileName}.mp4").close() }
            }
            val retiredFiles = listOf("ion_transit", "lantern_drift_realistic", "spectral_silk", "electric_synapse", "orbital_pulse", "chromatic_blossom",
                "prismatic_passage", "aurora_veil", "stardust_ignition", "violet_circuit", "abstract_mirror_script",
                "abstract_orbit_bloom", "abstract_woven_current", "abstract_dusk_lattice")
            val packaged = context.assets.list("visualizers")!!.toSet()
            for (file in retiredFiles) assertFalse(file, "$file.mp4" in packaged)
        } finally { stored.edit().clear().commit() }
        var selected: IdleVisualizerStyle? = null
        compose.setContent {
            TvIdleScreenSheet(TvRadioUiState(idleScreen = IdleScreenPreferences(visualizer = IdleVisualizerStyle.NeonKaleidoscope)),
                MutableStateFlow(AudioVisualizationState()), {}, {}, { selected = it }, {}, {})
        }
        compose.mainClock.advanceTimeBy(250)
        compose.waitForIdle()
        compose.onNodeWithText("22 styles · select to preview · press Select twice for full screen").assertExists()
        compose.onNodeWithText("Automatic idle screen: On").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Start delay: 10 minutes").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNode(hasText("Neon Kaleidoscope") and hasClickAction()).assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(IdleVisualizerStyle.NeonKaleidoscope, selected) }
    }
}
