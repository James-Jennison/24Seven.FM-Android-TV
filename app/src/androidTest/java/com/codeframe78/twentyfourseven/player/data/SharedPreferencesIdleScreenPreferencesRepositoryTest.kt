package com.codeframe78.twentyfourseven.player.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedPreferencesIdleScreenPreferencesRepositoryTest {
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
}
