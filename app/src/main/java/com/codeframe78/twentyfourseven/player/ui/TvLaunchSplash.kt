package com.codeframe78.twentyfourseven.player.ui

import android.graphics.drawable.Animatable
import android.view.View
import android.widget.ImageView
import androidx.compose.animation.core.Animatable as FloatAnimatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.codeframe78.twentyfourseven.player.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How long the mark stays up once it is on screen. The antenna animation runs for about 950 ms (see
 * `splash_icon_animated.xml`), so the hold covers it with a short beat to read the finished mark.
 */
internal const val TV_LAUNCH_SPLASH_HOLD_MILLIS = 1_500L
internal const val TV_LAUNCH_SPLASH_FADE_MILLIS = 300

/**
 * Allowance for the launcher-to-app transition that follows the first drawn frame, so the mark animation starts once
 * the viewer can see it rather than underneath the transition.
 */
internal const val TV_LAUNCH_SPLASH_TRANSITION_MILLIS = 350L

/**
 * Safety cap on the wait for the splash's first draw. If it is never drawn the splash skips straight to leaving, so it
 * cannot hold keys back indefinitely: the whole lifetime is at most this wait plus the transition, hold and fade.
 */
internal const val TV_LAUNCH_SPLASH_MAX_MILLIS = 10_000L

/**
 * The app's own launch screen: the 24seven.FM antenna mark going on air over the launch background, drawn by the
 * app because Google TV does not render the system splash screen. It plays once per cold start, then fades out and
 * calls [onFinished] so the caller can remove it. It is decorative, so it exposes nothing to accessibility.
 *
 * Nothing is timed from composition. The first frame of the player UI takes over a second to lay out and draw on the
 * Streamer (a release build), so a timer started at composition would run out before Google TV shows the window. The
 * animation and the hold start from the splash's own first draw instead. A first-use dialog is a separate window and
 * covers the splash on the very first run.
 */
@Composable
internal fun TvLaunchSplash(onFinished: () -> Unit) {
    val context = LocalContext.current
    val drawable = remember { ContextCompat.getDrawable(context, R.drawable.splash_icon_animated)?.mutate() }
    val alpha = remember { FloatAnimatable(1f) }
    val firstDraw = remember { CompletableDeferred<Unit>() }
    var markVisible by remember { mutableStateOf(false) }
    DisposableEffect(drawable) { onDispose { (drawable as? Animatable)?.stop() } }
    // One coroutine owns the whole lifecycle, so exactly one path fades the splash and calls onFinished.
    LaunchedEffect(Unit) {
        val drawn = withTimeoutOrNull(TV_LAUNCH_SPLASH_MAX_MILLIS) { firstDraw.await() } != null
        if (drawn && drawable != null) {
            delay(TV_LAUNCH_SPLASH_TRANSITION_MILLIS)
            markVisible = true
            (drawable as? Animatable)?.start()
            delay(TV_LAUNCH_SPLASH_HOLD_MILLIS)
        }
        alpha.animateTo(0f, tween(TV_LAUNCH_SPLASH_FADE_MILLIS))
        onFinished()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha.value }
            // drawWithContent must stay outside background(): it then wraps the background and runs on every draw pass, and
            // completing the deferred is idempotent.
            .drawWithContent {
                drawContent()
                firstDraw.complete(Unit)
            }
            .background(colorResource(R.color.splash_background))
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.size(240.dp),
            factory = { viewContext ->
                ImageView(viewContext).apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    setImageDrawable(drawable)
                    visibility = View.INVISIBLE
                }
            },
            update = { it.visibility = if (markVisible) View.VISIBLE else View.INVISIBLE },
        )
    }
}
