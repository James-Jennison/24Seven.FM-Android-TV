package com.codeframe78.twentyfourseven.player.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.codeframe78.twentyfourseven.player.domain.IonTunnelPalette
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class IonTunnelColorsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun remoteReachesEveryColorAndReturns() {
        val selected = mutableStateOf(IonTunnelPalette.AmberIce)
        var dismissed = false
        compose.setContent { TvIonTunnelColoursSheet(selected.value, { selected.value = it }, { dismissed = true }) }
        compose.mainClock.advanceTimeBy(250)
        compose.waitForIdle()
        for (palette in IonTunnelPalette.entries) {
            compose.onNodeWithText(palette.displayName).assertIsFocused()
                .performKeyInput { pressKey(Key.DirectionCenter) }
            compose.runOnIdle { assertEquals(palette, selected.value) }
            compose.onNodeWithText(palette.displayName).performKeyInput { pressKey(Key.DirectionDown) }
        }
        compose.onNodeWithText("Back").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertTrue(dismissed) }
    }
}
