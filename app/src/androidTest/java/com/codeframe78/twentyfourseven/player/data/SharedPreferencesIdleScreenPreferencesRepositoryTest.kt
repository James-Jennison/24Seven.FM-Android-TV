package com.codeframe78.twentyfourseven.player.data

import com.codeframe78.twentyfourseven.player.domain.IdleScreenStartDelay
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedPreferencesIdleScreenPreferencesRepositoryTest {
    @Test fun delayDefaultsPersistsAndUnknownValueFallsBack() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "idle-start-delay-test"
        val stored = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        stored.edit().clear().commit()
        try {
            val repository = SharedPreferencesIdleScreenPreferencesRepository(context, name)
            assertEquals(IdleScreenStartDelay.TenMinutes, repository.preferences.value.startDelay)
            repository.setAutomaticIdleScreenEnabled(false)
            for (delay in IdleScreenStartDelay.entries) {
                repository.setStartDelay(delay)
                val restored = SharedPreferencesIdleScreenPreferencesRepository(context, name).preferences.value
                assertEquals(delay, restored.startDelay)
                assertFalse(restored.automaticIdleScreenEnabled)
            }
            stored.edit().putString("idle-screen-start-delay", "unknown").commit()
            assertEquals(IdleScreenStartDelay.TenMinutes,
                SharedPreferencesIdleScreenPreferencesRepository(context, name).preferences.value.startDelay)
        } finally { stored.edit().clear().commit() }
    }

    @Test fun legacyPulseQuiltSelectionResolvesToLaserLightShow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "laser-light-show-preferences-test"
        val stored = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        stored.edit().clear().putString("visualizer", "PulseQuilt").commit()
        try {
            val repository = SharedPreferencesIdleScreenPreferencesRepository(context, name)
            assertEquals(IdleVisualizerStyle.PulseQuilt, repository.preferences.value.visualizer)
            assertEquals(20, repository.preferences.value.visualizer.ordinal)
            assertEquals("Laser Light Show", repository.preferences.value.visualizer.displayName)
        } finally {
            stored.edit().clear().commit()
        }
    }

    @Test fun defaultsOnAndBothChoicesSurviveRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "automatic-idle-preferences-test"
        val stored = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        stored.edit().clear().commit()
        try {
            val repository = SharedPreferencesIdleScreenPreferencesRepository(context, name)
            assertTrue(repository.preferences.value.automaticIdleScreenEnabled)
            repository.setAutomaticIdleScreenEnabled(false)
            assertFalse(SharedPreferencesIdleScreenPreferencesRepository(context, name).preferences.value.automaticIdleScreenEnabled)
            repository.setAutomaticIdleScreenEnabled(true)
            assertTrue(SharedPreferencesIdleScreenPreferencesRepository(context, name).preferences.value.automaticIdleScreenEnabled)
        } finally {
            stored.edit().clear().commit()
        }
    }
    @Test fun tunnelPalettePersistsIndependentlyAndUnknownValuesUseReferenceColors() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "ion-tunnel-palette-test"
        val stored = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        stored.edit().clear().commit()
        try {
            val repository = SharedPreferencesIdleScreenPreferencesRepository(context, name)
            repository.setLavaLampPalette(com.codeframe78.twentyfourseven.player.domain.LavaLampPalette.Acid)
            for (palette in com.codeframe78.twentyfourseven.player.domain.IonTunnelPalette.entries) {
                repository.setIonTunnelPalette(palette)
                val restored = SharedPreferencesIdleScreenPreferencesRepository(context, name).preferences.value
                assertEquals(palette, restored.ionTunnelPalette)
                assertEquals(com.codeframe78.twentyfourseven.player.domain.LavaLampPalette.Acid, restored.lavaLampPalette)
            }
            stored.edit().putString("ion-tunnel-palette", "unknown").commit()
            assertEquals(com.codeframe78.twentyfourseven.player.domain.IonTunnelPalette.AmberIce,
                SharedPreferencesIdleScreenPreferencesRepository(context, name).preferences.value.ionTunnelPalette)
        } finally { stored.edit().clear().commit() }
    }

}
