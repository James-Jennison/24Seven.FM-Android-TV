package com.codeframe78.twentyfourseven.player.ui

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.codeframe78.twentyfourseven.player.MainActivity
import com.codeframe78.twentyfourseven.player.RadioApplication
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePcmAvailabilityTest {
    @Test fun playingStationPublishesFreshPcm() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val app = ApplicationProvider.getApplicationContext<RadioApplication>()
            var fresh = 0
            var audible = 0
            repeat(100) {
                val state = app.appContainer.audioVisualizationRepository.state.value
                if (state.channelCount > 0 && state.sampledAtElapsedRealtimeMs > 0 &&
                    SystemClock.elapsedRealtime() - state.sampledAtElapsedRealtimeMs < 500) fresh++
                if (state.energy > .001f) audible++
                Thread.sleep(100)
            }
            assertTrue("Live audible energy missing: $audible/100 observations", audible >= 50)
            assertTrue("Live station must deliver fresh PCM: $fresh/100 observations", fresh >= 50)
        }
    }
}
