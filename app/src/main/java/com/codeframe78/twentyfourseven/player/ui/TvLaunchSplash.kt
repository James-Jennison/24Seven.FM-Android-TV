package com.codeframe78.twentyfourseven.player.ui

import android.graphics.drawable.Animatable
import android.widget.ImageView
import androidx.compose.animation.core.Animatable as FloatAnimatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.codeframe78.twentyfourseven.player.R
import kotlinx.coroutines.delay

/**
 * How long the finished launch mark stays up. The antenna animation runs for about 950 ms (see
 * `splash_icon_animated.xml`), so the hold covers it with a short beat to read the mark.
 */
internal const val TV_LAUNCH_SPLASH_HOLD_MILLIS = 1_100L
internal const val TV_LAUNCH_SPLASH_FADE_MILLIS = 300

/** Safety cap: if the window never gains focus the splash still leaves, so it cannot hold keys back indefinitely. */
internal const val TV_LAUNCH_SPLASH_MAX_MILLIS = 6_000L

/**
 * The app's own launch screen: the 24seven.FM antenna mark going on air over the launch background, drawn by the
 * app because Google TV does not render the system splash screen. It plays once per cold start, then fades out and
 * calls [onFinished] so the caller can remove it. The hold starts once the window has focus. It is decorative, so it exposes nothing to accessibility.
 */
@Composable
internal fun TvLaunchSplash(onFinished: () -> Unit) {
    val alpha = remember { FloatAnimatable(1f) }
    // Count the hold from when the window is actually focused (visible to the viewer). The first frame is composed well
    // before Google TV lets the app window appear (about a second on a release build), so a timer started at
    // composition would run out unseen.
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        if (!windowFocused) return@LaunchedEffect
        delay(TV_LAUNCH_SPLASH_HOLD_MILLIS)
        alpha.animateTo(0f, tween(TV_LAUNCH_SPLASH_FADE_MILLIS))
        onFinished()
    }
    LaunchedEffect(Unit) {
        delay(TV_LAUNCH_SPLASH_MAX_MILLIS)
        alpha.animateTo(0f, tween(TV_LAUNCH_SPLASH_FADE_MILLIS))
        onFinished()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha.value }
            .background(colorResource(R.color.splash_background))
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.size(240.dp),
            factory = { context ->
                ImageView(context).apply {
                    importantForAccessibility = ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO
                    val drawable = ContextCompat.getDrawable(context, R.drawable.splash_icon_animated)
                    setImageDrawable(drawable)
                    (drawable as? Animatable)?.start()
                }
            },
        )
    }
}
