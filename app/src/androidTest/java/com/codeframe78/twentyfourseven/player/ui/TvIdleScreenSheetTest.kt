package com.codeframe78.twentyfourseven.player.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerStyle
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class TvIdleScreenSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun remoteCanToggleOffAndPreviewWithoutReenablingAutomaticIdle() {
        val state = mutableStateOf(TvRadioUiState())
        var preview: IdleVisualizerStyle? = null
        compose.setContent {
            TvIdleScreenSheet(
                state = state.value,
                visualization = MutableStateFlow(AudioVisualizationState()),
                onSetIdleScreenStartDelay = { delay ->
                    state.value = state.value.copy(idleScreen = state.value.idleScreen.copy(startDelay = delay))
                },
                onSetAutomaticIdleScreenEnabled = { enabled ->
                    state.value = state.value.copy(idleScreen = state.value.idleScreen.copy(automaticIdleScreenEnabled = enabled))
                },
                onSelectVisualizer = { style ->
                    state.value = state.value.copy(idleScreen = state.value.idleScreen.copy(visualizer = style))
                },
                onPreviewFullScreen = { preview = it },
                onDismiss = {},
            )
        }
        // Allow the inherited opening-key guard to complete before interacting.
        compose.mainClock.advanceTimeBy(250)
        compose.waitForIdle()
        compose.onNodeWithText("Automatic idle screen: On").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Automatic idle screen: Off").assertIsFocused()
        val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "automatic-idle-off.png")
            .outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithText("Automatic idle screen: Off")
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Start delay: 10 minutes").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Start delay: 15 minutes").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNode(hasText(IdleVisualizerStyle.NeonKaleidoscope.displayName) and hasClickAction()).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNode(hasText(IdleVisualizerStyle.NeonKaleidoscope.displayName) and hasClickAction())
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle {
            assertEquals(IdleVisualizerStyle.NeonKaleidoscope, preview)
            assertFalse(state.value.idleScreen.automaticIdleScreenEnabled)
        }
        compose.onNode(hasText(IdleVisualizerStyle.NeonKaleidoscope.displayName) and hasClickAction())
            .performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Start delay: 15 minutes").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Automatic idle screen: Off").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Automatic idle screen: On").assertIsFocused()
    }
}
