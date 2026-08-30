package com.codeframe78.twentyfourseven.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.codeframe78.twentyfourseven.player.R
import com.codeframe78.twentyfourseven.player.domain.PlaybackStatus
import com.codeframe78.twentyfourseven.player.domain.QueueLoadStatus
import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerStyle
import com.codeframe78.twentyfourseven.player.domain.NowPlayingOverlayMode
import com.codeframe78.twentyfourseven.player.domain.StationId
import com.codeframe78.twentyfourseven.player.ui.theme.stationPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.sin

/**
 * The first TV alpha deliberately exposes only anonymous listening. Its controls are standard TV
 * Material controls so focus is visible and every action works through a D-pad remote.
 */
@Composable
internal fun TvRadioApp(
    state: TvRadioUiState,
    visualization: StateFlow<AudioVisualizationState>,
    diagnosticEnvironment: DiagnosticEnvironment,
    onSelectStation: (StationId) -> Unit,
    onPause: () -> Unit,
    onSetSleepTimer: (Long) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onSelectIdleVisualizer: (IdleVisualizerStyle) -> Unit,
    onSetNowPlayingOverlay: (NowPlayingOverlayMode) -> Unit,
    onSetReduceVisualIntensity: (Boolean) -> Unit,
) {
    val style = tvStationStyle(state.selectedStation?.id)
    val hostView = LocalView.current
    var menuDestination by rememberSaveable(stateSaver = TvMenuDestinationSaver) {
        mutableStateOf<TvMenuDestination?>(null)
    }
    var idleVisualizerActive by remember { mutableStateOf(false) }
    var fullScreenPreviewRequested by remember { mutableStateOf(false) }
    var fullScreenPreviewStyle by remember { mutableStateOf<IdleVisualizerStyle?>(null) }
    var idleNowPlayingOverlayVisible by remember { mutableStateOf(false) }
    var interactionRevision by remember { mutableStateOf(0) }
    var consumeMenuOpeningDirectionUp by remember { mutableStateOf(false) }
    DisposableEffect(hostView, state.playback.status) {
        // Fire TV can launch its system dream well before the Player's ten-minute
        // in-app idle timer. While live audio is playing in the visible Player,
        // reserve the screen for the audio-reactive idle visualizer. Leaving the
        // app or stopping playback restores normal system screensaver behavior.
        hostView.keepScreenOn = state.playback.status == PlaybackStatus.Playing
        onDispose { hostView.keepScreenOn = false }
    }
    LaunchedEffect(state.playback.status, menuDestination, interactionRevision, fullScreenPreviewRequested) {
        if (fullScreenPreviewRequested && menuDestination == null) {
            idleVisualizerActive = true
        } else if (state.playback.status == PlaybackStatus.Playing && menuDestination == null) {
            idleVisualizerActive = false
            delay(TvIdleScreenDelayMillis)
            idleVisualizerActive = true
        } else {
            idleVisualizerActive = false
        }
    }
    LaunchedEffect(
        idleVisualizerActive,
        fullScreenPreviewRequested,
        state.idleScreen.nowPlayingOverlay,
    ) {
        idleNowPlayingOverlayVisible = false
        if (!idleVisualizerActive || fullScreenPreviewRequested) return@LaunchedEffect
        when (state.idleScreen.nowPlayingOverlay) {
            NowPlayingOverlayMode.Off -> Unit
            NowPlayingOverlayMode.Always -> idleNowPlayingOverlayVisible = true
            NowPlayingOverlayMode.Brief -> {
                idleNowPlayingOverlayVisible = true
                delay(TvNowPlayingOverlayBriefDurationMillis)
                idleNowPlayingOverlayVisible = false
            }
        }
    }
    val nowPlayingMarqueePhase by rememberInfiniteTransition(label = "TV Now Playing marquee")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(TvNowPlayingMarqueeCycleDurationMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "TV Now Playing marquee phase",
        )
    androidx.activity.compose.BackHandler(enabled = idleVisualizerActive) {
        if (fullScreenPreviewRequested) {
            // A full-screen view entered from the selector is a preview, not
            // the automatic idle screen. Return it to that selector so a user
            // can compare styles without reopening General Options.
            fullScreenPreviewRequested = false
            fullScreenPreviewStyle = null
            menuDestination = TvMenuDestination.IdleScreen
        } else {
            idleVisualizerActive = false
        }
        interactionRevision += 1
    }
    MaterialTheme(colorScheme = tvColorScheme(style)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TvInk)
                .onPreviewKeyEvent { event ->
                    // The native GL surface receives Back on key-up and delegates it
                    // to the stateful exit path below.  Do not let the generic
                    // "any key wakes idle" handling clear preview state on key-down
                    // first, otherwise the preview cannot return to its picker.
                    if (event.key == Key.Back) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown) {
                        interactionRevision += 1
                        if (idleVisualizerActive) {
                            idleVisualizerActive = false
                            fullScreenPreviewRequested = false
                            fullScreenPreviewStyle = null
                        }
                        if (event.key == Key.DirectionDown && menuDestination == null) {
                            menuDestination = TvMenuDestination.GeneralOptions
                            consumeMenuOpeningDirectionUp = true
                            return@onPreviewKeyEvent true
                        }
                    }
                    if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                    if (event.key == Key.DirectionDown && consumeMenuOpeningDirectionUp) {
                        consumeMenuOpeningDirectionUp = false
                        return@onPreviewKeyEvent true
                    }
                    when (event.key) {
                        Key.Menu -> {
                            menuDestination = if (menuDestination == null) {
                                TvMenuDestination.GeneralOptions
                            } else {
                                null
                            }
                            true
                        }

                        else -> false
                    }
                },
        ) {
            TvArtworkBackdrop(
                artworkUrl = state.nowPlaying.artworkUrl,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TvInk.copy(alpha = 0.42f)),
            )
            if (idleVisualizerActive) {
                TvIdleVisualizer(
                    style = fullScreenPreviewStyle ?: state.idleScreen.visualizer,
                    audioState = visualization,
                    stationName = state.selectedStation?.name,
                    reduceVisualIntensity = state.idleScreen.reduceVisualIntensity,
                    onExit = {
                        if (fullScreenPreviewRequested) {
                            fullScreenPreviewRequested = false
                            fullScreenPreviewStyle = null
                            menuDestination = TvMenuDestination.IdleScreen
                        } else {
                            idleVisualizerActive = false
                        }
                        interactionRevision += 1
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (idleNowPlayingOverlayVisible && !fullScreenPreviewRequested) {
                    TvIdleNowPlayingOverlay(
                        state = state,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 42.dp, bottom = 36.dp),
                    )
                }
            } else Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp, vertical = 27.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TvLibraryPanel(
                        eyebrow = "UP NEXT",
                        state = state,
                        tracks = state.queue?.upcoming?.take(5)?.map {
                            TvLibraryTrack(
                                title = it.displayTitle,
                                artistName = it.artistName,
                                albumTitle = it.albumTitle,
                                durationLabel = it.durationLabel,
                                artworkUrl = it.artworkUrl,
                            )
                        }.orEmpty(),
                        accentColor = style.secondary,
                        queued = true,
                        style = style,
                        modifier = Modifier
                            .weight(0.80f)
                            .fillMaxHeight(),
                    )
                    TvNowPlaying(
                        state = state,
                        style = style,
                        marqueePhase = nowPlayingMarqueePhase,
                        modifier = Modifier
                            .weight(1.36f)
                            .fillMaxHeight(),
                    )
                    TvLibraryPanel(
                        eyebrow = "RECENTLY PLAYED",
                        state = state,
                        tracks = state.queue?.recentlyPlayed?.take(5)?.map {
                            TvLibraryTrack(
                                title = it.displayTitle,
                                artistName = it.artistName,
                                albumTitle = it.albumTitle,
                                durationLabel = it.durationLabel,
                                artworkUrl = it.artworkUrl,
                            )
                        }.orEmpty(),
                        accentColor = style.accent,
                        queued = false,
                        style = style,
                        modifier = Modifier
                            .weight(0.80f)
                            .fillMaxHeight(),
                    )
                }
                TvStationBanner(
                    state = state,
                    onSelectStation = onSelectStation,
                    onPause = onPause,
                )
            }
            when (menuDestination) {
                TvMenuDestination.GeneralOptions -> {
                    TvGeneralOptionsSheet(
                        state = state,
                        onOpenSleepTimer = { menuDestination = TvMenuDestination.SleepTimer },
                        onOpenIdleScreen = { menuDestination = TvMenuDestination.IdleScreen },
                        onOpenAccessibilityAndMotion = { menuDestination = TvMenuDestination.AccessibilityAndMotion },
                        onOpenAboutAndSupport = { menuDestination = TvMenuDestination.AboutAndSupport },
                        onDismiss = { menuDestination = menuDestination.backDestination() },
                    )
                }

                TvMenuDestination.SleepTimer -> {
                TvSleepTimerDialog(
                    state = state,
                    onSet = {
                        onSetSleepTimer(it)
                        menuDestination = null
                    },
                    onCancel = {
                        onCancelSleepTimer()
                        menuDestination = null
                    },
                    onDismiss = { menuDestination = menuDestination.backDestination() },
                )
                }

                TvMenuDestination.IdleScreen -> {
                TvIdleScreenSheet(
                        state = state,
                        visualization = visualization,
                        onSelectVisualizer = onSelectIdleVisualizer,
                        onPreviewFullScreen = { previewStyle ->
                            fullScreenPreviewStyle = previewStyle
                            fullScreenPreviewRequested = true
                            menuDestination = null
                        },
                        onDismiss = { menuDestination = menuDestination.backDestination() },
                    )
                }

                TvMenuDestination.AccessibilityAndMotion -> {
                    TvAccessibilityAndMotionSheet(
                        preferences = state.idleScreen,
                        onSetNowPlayingOverlay = onSetNowPlayingOverlay,
                        onSetReduceVisualIntensity = onSetReduceVisualIntensity,
                        onDismiss = { menuDestination = menuDestination.backDestination() },
                    )
                }

                TvMenuDestination.AboutAndSupport -> {
                    TvAboutAndSupportSheet(
                        environment = diagnosticEnvironment,
                        onOpenDiagnostics = { menuDestination = TvMenuDestination.Diagnostics },
                        onDismiss = { menuDestination = menuDestination.backDestination() },
                    )
                }

                TvMenuDestination.Diagnostics -> {
                TvDiagnosticsDialog(
                    state = state,
                    environment = diagnosticEnvironment,
                    onDismiss = { menuDestination = menuDestination.backDestination() },
                )
            }

                null -> Unit
            }
        }
    }
}

private const val TvIdleScreenDelayMillis = 10 * 60 * 1_000L
private const val TvNowPlayingOverlayBriefDurationMillis = 5_000L
private const val TvVisualizerDoubleActivateWindowMillis = 650L

@Composable
private fun TvGeneralOptionsSheet(
    state: TvRadioUiState,
    onOpenSleepTimer: () -> Unit,
    onOpenIdleScreen: () -> Unit,
    onOpenAccessibilityAndMotion: () -> Unit,
    onOpenAboutAndSupport: () -> Unit,
    onDismiss: () -> Unit,
) {
    TvOptionsSheet(title = "General Options", onDismiss = onDismiss) { firstAction ->
        TvOptionAction(
            title = "Idle Screen",
            summary = "${state.idleScreen.visualizer.displayName} · starts after 10 minutes",
            onClick = onOpenIdleScreen,
            modifier = Modifier.focusRequester(firstAction),
        )
        TvOptionAction(
            title = "Sleep Timer",
            summary = if (state.playback.sleepTimer.isActive) {
                "Pauses in ${formatSleepTimerRemaining(state.playback.sleepTimer.remainingMillis)}"
            } else {
                "Off"
            },
            onClick = onOpenSleepTimer,
        )
        TvOptionAction(
            title = "Accessibility & Motion",
            summary = if (state.idleScreen.reduceVisualIntensity) {
                "Reduced visual intensity · ${state.idleScreen.nowPlayingOverlay.displayName} overlay"
            } else {
                state.idleScreen.nowPlayingOverlay.displayName + " overlay"
            },
            onClick = onOpenAccessibilityAndMotion,
        )
        TvOptionAction(
            title = "About & Support",
            summary = "Version, privacy, licenses, and device status",
            onClick = onOpenAboutAndSupport,
        )
        TvOptionAction(title = "Close", summary = "Return to the player", onClick = onDismiss)
    }
}

@Composable
private fun TvAccessibilityAndMotionSheet(
    preferences: com.codeframe78.twentyfourseven.player.domain.IdleScreenPreferences,
    onSetNowPlayingOverlay: (NowPlayingOverlayMode) -> Unit,
    onSetReduceVisualIntensity: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    TvOptionsSheet(title = "Accessibility & Motion", onDismiss = onDismiss) { firstAction ->
        TvOptionAction(
            title = "Now Playing Overlay",
            summary = preferences.nowPlayingOverlay.displayName,
            onClick = {
                val next = NowPlayingOverlayMode.entries[
                    (preferences.nowPlayingOverlay.ordinal + 1) % NowPlayingOverlayMode.entries.size
                ]
                onSetNowPlayingOverlay(next)
            },
            modifier = Modifier.focusRequester(firstAction),
        )
        TvOptionAction(
            title = "Reduce Visual Intensity",
            summary = if (preferences.reduceVisualIntensity) {
                "On · softens visual response while preserving PCM analysis"
            } else {
                "Off · uses the full visual response"
            },
            onClick = { onSetReduceVisualIntensity(!preferences.reduceVisualIntensity) },
            selected = preferences.reduceVisualIntensity,
        )
        TvOptionAction("Back", "Return to General options", onDismiss)
    }
}

@Composable
private fun TvAboutAndSupportSheet(
    environment: DiagnosticEnvironment,
    onOpenDiagnostics: () -> Unit,
    onDismiss: () -> Unit,
) {
    TvOptionsSheet(title = "About & Support", onDismiss = onDismiss) { firstAction ->
        TvOptionInfo(
            title = "App Version",
            summary = "${environment.appVersion} (${environment.versionCode}) · ${environment.buildType}",
        )
        TvOptionInfo(
            title = "Privacy",
            summary = "Anonymous listening; local diagnostics exclude accounts, URLs, identifiers, and logs",
        )
        TvOptionInfo(
            title = "Open-Source Licenses",
            summary = "Available in Android system app information",
        )
        TvOptionAction(
            "Device Status",
            "Local playback information",
            onOpenDiagnostics,
            modifier = Modifier.focusRequester(firstAction),
        )
        TvOptionAction("Back", "Return to General options", onDismiss)
    }
}

@Composable
private fun TvIdleNowPlayingOverlay(state: TvRadioUiState, modifier: Modifier = Modifier) {
    val title = state.nowPlaying.displayTitle ?: state.selectedStation?.name ?: "Live radio"
    val subtitle = state.nowPlaying.artist?.takeIf { it.isNotBlank() }
        ?: state.selectedStation?.name?.takeIf { it != title }
    Column(
        modifier = modifier
            .width(590.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(TvInk.copy(alpha = .72f))
            .border(1.dp, TvCream.copy(alpha = .24f), RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TvCream,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = TvCream.copy(alpha = .78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TvIdleScreenSheet(
    state: TvRadioUiState,
    visualization: StateFlow<AudioVisualizationState>,
    onSelectVisualizer: (IdleVisualizerStyle) -> Unit,
    onPreviewFullScreen: (IdleVisualizerStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedAction = remember { FocusRequester() }
    val styles = IdleVisualizerStyle.entries.toList()
    val selectorListState = rememberLazyListState()
    var previewStyle by remember { mutableStateOf(state.idleScreen.visualizer) }
    var lastActivatedStyle by remember { mutableStateOf<IdleVisualizerStyle?>(null) }
    var lastActivatedAtMillis by remember { mutableStateOf(0L) }
    var selectorInteractive by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    LaunchedEffect(state.idleScreen.visualizer) {
        previewStyle = state.idleScreen.visualizer
        selectorListState.scrollToItem(styles.indexOf(state.idleScreen.visualizer).coerceAtLeast(0))
    }
    LaunchedEffect(Unit) {
        // The Select key that opens this screen ends after composition. Leave the
        // picker non-interactive for that single inherited gesture, then place
        // focus on the selected style. A listener's next Select is therefore
        // always an intentional first half of the double-press preview gesture.
        delay(120)
        selectorInteractive = true
        selectedAction.requestFocus()
    }
    Box(
        modifier = Modifier.fillMaxSize().background(TvInk),
    ) {
        TvIdleVisualizer(
            style = previewStyle,
            audioState = visualization,
            stationName = state.selectedStation?.name,
            reduceVisualIntensity = state.idleScreen.reduceVisualIntensity,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(TvInk.copy(alpha = .08f), TvInk.copy(alpha = .12f), TvInk.copy(alpha = .72f)),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .width(590.dp)
                .padding(start = 42.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                previewStyle.displayName,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = TvCream,
            )
            Text(
                previewStyle.description,
                style = MaterialTheme.typography.bodyMedium,
                color = TvCream.copy(alpha = .82f),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(420.dp)
                .fillMaxHeight()
                .background(TvInk.copy(alpha = .82f))
                .border(1.dp, TvCream.copy(alpha = .20f))
                .padding(horizontal = 22.dp, vertical = 30.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Idle Visualizer",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = TvCream,
            )
            Text(
                "Choose a style to preview · press Select twice for full screen",
                style = MaterialTheme.typography.bodySmall,
                color = TvCream.copy(alpha = .68f),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 10.dp),
                    state = selectorListState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(styles, key = { it.name }) { style ->
                        TvVisualizerListRow(
                            style = style,
                            selected = style == state.idleScreen.visualizer,
                            enabled = selectorInteractive,
                            onClick = {
                                previewStyle = style
                                onSelectVisualizer(style)
                                val now = android.os.SystemClock.elapsedRealtime()
                                val isDoubleActivation = lastActivatedStyle == style &&
                                    now - lastActivatedAtMillis <= TvVisualizerDoubleActivateWindowMillis
                                if (isDoubleActivation) {
                                    lastActivatedStyle = null
                                    lastActivatedAtMillis = 0L
                                    onPreviewFullScreen(style)
                                } else {
                                    lastActivatedStyle = style
                                    lastActivatedAtMillis = now
                                }
                            },
                            onPreview = { previewStyle = style },
                            modifier = if (style == state.idleScreen.visualizer) {
                                Modifier.focusRequester(selectedAction)
                            } else {
                                Modifier
                            },
                        )
                    }
                }
                TvVisualizerScrollIndicator(
                    state = selectorListState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 2.dp),
                )
            }
        }
    }
}

/** A persistent, non-focusable cue that the TV selector continues beyond its viewport. */
@Composable
private fun TvVisualizerScrollIndicator(
    state: LazyListState,
    modifier: Modifier = Modifier,
) {
    val layoutInfo = state.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).coerceAtLeast(1)
    val averageItemHeight = visibleItems
        .takeIf { it.isNotEmpty() }
        ?.map { it.size }
        ?.average()
        ?.toFloat()
        ?: return
    val itemCount = layoutInfo.totalItemsCount
    if (itemCount == 0 || (!state.canScrollBackward && !state.canScrollForward)) return

    val itemSpacing = with(LocalDensity.current) { 4.dp.toPx() }
    val contentHeight = (averageItemHeight * itemCount) + (itemSpacing * (itemCount - 1))
    val thumbFraction = (viewportHeight / contentHeight).coerceIn(.18f, .92f)
    val scrollRange = (contentHeight - viewportHeight).coerceAtLeast(1f)
    val scrollOffset = (state.firstVisibleItemIndex * (averageItemHeight + itemSpacing)) +
        state.firstVisibleItemScrollOffset
    val scrollFraction = (scrollOffset / scrollRange).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier.width(7.dp),
    ) {
        val thumbHeight = maxHeight * thumbFraction
        val thumbOffset = (maxHeight - thumbHeight) * scrollFraction
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(2.dp)
                .fillMaxHeight()
                .background(TvCream.copy(alpha = .22f), CircleShape),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = thumbOffset)
                .width(5.dp)
                .height(thumbHeight)
                .background(Color(0xFF7FFFE1).copy(alpha = .88f), CircleShape),
        )
    }
}

@Composable
private fun TvVisualizerListRow(
    style: IdleVisualizerStyle,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(31.dp)
            .onFocusChanged { if (it.isFocused) onPreview() },
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = TvCream,
            focusedContainerColor = TvCream.copy(alpha = .12f),
            focusedContentColor = TvCream,
        ),
        shape = ButtonDefaults.shape(RoundedCornerShape(5.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = if (selected) 1.dp else 0.dp,
                    color = TvCream.copy(alpha = .74f),
                    shape = RoundedCornerShape(5.dp),
                )
                .padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp)),
            ) {
                TvVisualizerPreview(style = style, modifier = Modifier.fillMaxSize())
            }
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(
                        if (selected) Color(0xFF7FFFE1) else TvCream.copy(alpha = .30f),
                        CircleShape,
                    ),
            )
            Text(
                style.displayName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TvOptionsSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable (FocusRequester) -> Unit,
) {
    val firstAction = remember { FocusRequester() }
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    LaunchedEffect(title) { firstAction.requestFocus() }
    // A visual overlay leaves VoiceView's accessibility focus on the station tile beneath it.
    // A real dialog creates the modal window boundary that Android accessibility services use to
    // announce the pane and traverse only its actions.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TvInk.copy(alpha = .86f))
                .semantics {
                    paneTitle = title
                    isTraversalGroup = true
                },
            contentAlignment = Alignment.CenterEnd,
        ) {
            Column(
                modifier = Modifier
                    .width(470.dp)
                    .fillMaxHeight()
                    .clip(TvPanelShape)
                    .background(TvInk.copy(alpha = .97f))
                    .border(1.dp, TvCream.copy(alpha = .35f), TvPanelShape)
                    .padding(horizontal = 30.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = TvCream,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                content(firstAction)
            }
        }
    }
}

@Composable
private fun TvOptionAction(
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.colors(
            containerColor = if (selected) TvCream.copy(alpha = .16f) else TvCream.copy(alpha = .07f),
            contentColor = TvCream,
            focusedContainerColor = TvCream,
            focusedContentColor = TvInk,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(summary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TvOptionInfo(title: String, summary: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TvCream.copy(alpha = .05f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TvCream)
        Text(summary, style = MaterialTheme.typography.bodySmall, color = TvCream.copy(alpha = .74f))
    }
}

internal fun tvTrackLabel(displayTitle: String, artistName: String?): String {
    val cleanTitle = displayTitle.trim()
    val cleanArtist = artistName?.trim().orEmpty()
    return if (cleanArtist.isNotEmpty() && !cleanArtist.equals(cleanTitle, ignoreCase = true)) {
        "$cleanTitle / $cleanArtist"
    } else {
        cleanTitle
    }
}

internal fun tvNowPlayingTrackArtistLabel(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    val separator = value.indexOf(" - ")
    return if (separator > 0 && separator < value.lastIndex - 2) {
        val artist = value.substring(0, separator).trim()
        val track = value.substring(separator + 3).trim()
        "$track - $artist"
    } else {
        value
    }
}

internal fun formatSleepTimerRemaining(remainingMillis: Long): String {
    val totalSeconds = (remainingMillis.coerceAtLeast(0L) + 999L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

@Composable
private fun TvArtworkBackdrop(artworkUrl: String?, modifier: Modifier = Modifier) {
    var retainedArtwork by remember { mutableStateOf<Painter?>(null) }
    if (artworkUrl != null) {
        AsyncImage(
            model = rememberTvArtworkRequest(artworkUrl),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = retainedArtwork,
            error = retainedArtwork,
            onSuccess = { retainedArtwork = it.painter },
            modifier = modifier
                .graphicsLayer {
                    scaleX = 1.14f
                    scaleY = 1.14f
                },
        )
    }
}

@Composable
private fun TvNowPlaying(
    state: TvRadioUiState,
    style: TvStationStyle,
    marqueePhase: Float,
    modifier: Modifier = Modifier,
) {
    var retainedArtwork by remember { mutableStateOf<Painter?>(null) }
    val artworkUrl = state.nowPlaying.artworkUrl?.trim()?.takeIf(String::isNotEmpty)
    var showNoArtworkInterstitial by remember(artworkUrl) { mutableStateOf(artworkUrl == null) }
    val rawMetadata = tvNowPlayingMetadata(state.nowPlaying)
    val metadata = rawMetadata.copy(title = rawMetadata.title ?: "Waiting for live metadata")
    Column(
        modifier = modifier
            .padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
    ) {
        Box(
            modifier = Modifier
                .size(270.dp)
                .graphicsLayer {
                    scaleX = 1.14f
                    scaleY = 1.14f
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                .align(Alignment.CenterHorizontally)
                .clip(TvPanelShape),
        ) {
            TvNoArtworkInterstitial(
                stationId = state.selectedStation?.id,
                style = style,
                modifier = Modifier.fillMaxSize(),
            )
            if (artworkUrl != null && !showNoArtworkInterstitial) {
                AsyncImage(
                    model = rememberTvArtworkRequest(artworkUrl),
                    contentDescription = "Now playing artwork",
                    contentScale = ContentScale.Crop,
                    placeholder = retainedArtwork,
                    onSuccess = {
                        retainedArtwork = it.painter
                        showNoArtworkInterstitial = false
                    },
                    onError = { showNoArtworkInterstitial = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .offset(y = 30.dp)
                .padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            TvNowPlayingMarqueeText(
                text = metadata.title.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TvCream,
                phase = marqueePhase,
            )
            TvNowPlayingMarqueeText(
                text = metadata.artist ?: "—",
                style = MaterialTheme.typography.titleMedium,
                color = TvCream.copy(alpha = 0.9f),
                phase = marqueePhase,
            )
        }
    }
}

@Composable
private fun TvNoArtworkInterstitial(
    stationId: StationId?,
    style: TvStationStyle,
    modifier: Modifier = Modifier,
) {
    val visualizerPhase by rememberInfiniteTransition(label = "TV station interstitial waveform")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(TvStationInterstitialWaveformCycleMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "TV station interstitial waveform phase",
        )
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(style.backdropTop, TvInk.copy(alpha = 0.92f), style.backdropBottom),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.76f)
                .height(108.dp)
                .align(Alignment.Center)
                .graphicsLayer(alpha = 0.42f),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            TvStationInterstitialWaveformHeights.forEachIndexed { index, heightFraction ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(tvAnimatedWaveformHeight(heightFraction, index, visualizerPhase))
                        .clip(RoundedCornerShape(2.dp))
                        .background(style.accent.copy(alpha = 0.84f)),
                )
            }
        }
        stationId?.let { id ->
            Image(
                bitmap = ImageBitmap.imageResource(tvStationSelectorLogoResource(id, 256)),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alpha = 0.48f,
                filterQuality = FilterQuality.High,
                modifier = Modifier
                    .size(148.dp)
                    .clip(TvTrackShape),
            )
        }
    }
}

@Composable
private fun TvStationBanner(
    state: TvRadioUiState,
    onSelectStation: (StationId) -> Unit,
    onPause: () -> Unit,
) {
    val livePulse by rememberInfiniteTransition(label = "TV live station pulse")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2_400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "TV live station pulse phase",
        )
    var showOptionsHint by remember { mutableStateOf(false) }
    var optionsHintGeneration by remember { mutableStateOf(0) }
    LaunchedEffect(optionsHintGeneration) {
        if (optionsHintGeneration == 0) return@LaunchedEffect
        showOptionsHint = true
        delay(TvStationOptionsHintDurationMillis)
        showOptionsHint = false
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        val selectorStations = state.stations.sortedBy { it.name }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
        selectorStations.forEach { station ->
            val selected = station.id == state.selectedStation?.id
            val playing = selected && state.playback.status == PlaybackStatus.Playing
            val palette = stationPalette(station.id)
            var focused by remember(station.id) { mutableStateOf(false) }
            val focusScale by animateFloatAsState(
                targetValue = if (focused) 1.04f else 1f,
                animationSpec = tween(180),
                label = "TV station focus scale ${station.id.value}",
            )
            val liveHaloBaseColor = if (station.id.value == "dfm") {
                Color(0xFFFF5158)
            } else {
                palette.secondary
            }
            val iconHaloColor = if (playing) {
                liveHaloBaseColor.copy(
                    alpha = if (station.id.value == "dfm") {
                        0.30f + (livePulse * 0.20f)
                    } else {
                        0.22f + (livePulse * 0.18f)
                    },
                )
            } else {
                palette.glow.copy(alpha = 0.14f)
            }
            val iconHaloScale = if (playing) 0.94f + (livePulse * 0.12f) else 1f
            Box(
                modifier = Modifier.size(102.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (focused) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .background(TvCream.copy(alpha = 0.12f), TvTrackShape)
                    )
                }
                Button(
                    onClick = {
                        if (playing) onPause() else onSelectStation(station.id)
                    },
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = TvCream,
                        focusedContainerColor = Color.Transparent,
                        focusedContentColor = TvCream,
                        pressedContainerColor = Color.Transparent,
                        pressedContentColor = TvInk,
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor = palette.secondary.copy(alpha = 0.55f),
                    ),
                    shape = ButtonDefaults.shape(TvTrackShape),
                    modifier = Modifier
                        .size(90.dp)
                        .onFocusChanged {
                            focused = it.isFocused
                            if (it.isFocused) optionsHintGeneration += 1
                        }
                        .graphicsLayer {
                            scaleX = focusScale
                            scaleY = focusScale
                        }
                        .semantics {
                            contentDescription = if (selected) {
                                if (playing) {
                                    "${station.name}, live. Select to pause playback."
                                } else {
                                    "${station.name}, selected. Select to start listening live."
                                }
                            } else {
                                "${station.name}. Select to start listening live."
                            }
                        },
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(62.dp)
                                        .graphicsLayer {
                                            scaleX = iconHaloScale
                                            scaleY = iconHaloScale
                                        }
                                        .background(iconHaloColor, TvTrackShape)
                                        .blur(7.dp),
                                )
                                val selectorLogoSizePx = with(LocalDensity.current) { 52.dp.roundToPx() }
                                Image(
                                    bitmap = ImageBitmap.imageResource(
                                        tvStationSelectorLogoResource(station.id, selectorLogoSizePx),
                                    ),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    filterQuality = FilterQuality.High,
                                    modifier = Modifier.size(52.dp),
                                )
                            }
                            if (playing) Text("LIVE", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        }
        AnimatedVisibility(
            visible = showOptionsHint,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(animationSpec = tween(160)),
            exit = fadeOut(animationSpec = tween(300)),
        ) {
            Text(
                text = "↓ OPTIONS",
                modifier = Modifier
                    .clip(CircleShape)
                    .background(TvInk.copy(alpha = 0.72f))
                    .border(1.dp, TvCream.copy(alpha = 0.34f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = TvCream.copy(alpha = 0.86f),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun TvSleepTimerDialog(
    state: TvRadioUiState,
    onSet: (Long) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val firstAction = remember { FocusRequester() }
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    LaunchedEffect(Unit) { firstAction.requestFocus() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvInk.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(620.dp)
                .clip(TvPanelShape)
                .background(TvInk.copy(alpha = 0.94f))
                .border(1.dp, TvCream.copy(alpha = 0.38f), TvPanelShape)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Sleep Timer",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TvCream,
            )
            Text(
                text = if (state.playback.sleepTimer.isActive) {
                    "Playback pauses in ${formatSleepTimerRemaining(state.playback.sleepTimer.remainingMillis)}."
                } else {
                    "Pause live playback after a selected interval."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = TvCream.copy(alpha = 0.82f),
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(15, 30, 45, 60).forEachIndexed { index, minutes ->
                    Button(
                        onClick = { onSet(minutes * 60_000L) },
                        modifier = if (index == 0) Modifier.focusRequester(firstAction) else Modifier,
                    ) { Text("$minutes min") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.playback.sleepTimer.isActive) {
                    Button(onClick = onCancel) { Text("Cancel timer") }
                }
                Button(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

@Composable
private fun TvDiagnosticsDialog(
    state: TvRadioUiState,
    environment: DiagnosticEnvironment,
    onDismiss: () -> Unit,
) {
    val closeAction = remember { FocusRequester() }
    val report = remember(
        environment,
        state.selectedStation?.name,
        state.playback,
        state.diagnosticTransitions,
    ) {
        buildDiagnosticReport(
            environment = environment,
            stationName = state.selectedStation?.name,
            playback = state.playback,
            transitions = state.diagnosticTransitions,
        )
    }
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    LaunchedEffect(Unit) { closeAction.requestFocus() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvInk.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(760.dp)
                .clip(TvPanelShape)
                .background(TvInk.copy(alpha = 0.94f))
                .border(1.dp, TvCream.copy(alpha = 0.38f), TvPanelShape)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Device Status",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TvCream,
            )
            Text(
                "Local, privacy-safe playback information",
                style = MaterialTheme.typography.bodyLarge,
                color = TvCream.copy(alpha = 0.82f),
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                report.lineSequence()
                    .filter { it.isNotBlank() }
                    .forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = TvCream.copy(alpha = 0.86f),
                        )
                    }
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.focusRequester(closeAction),
            ) { Text("Close") }
        }
    }
}

@Composable
private fun TvLibraryPanel(
    eyebrow: String,
    state: TvRadioUiState,
    tracks: List<TvLibraryTrack>,
    accentColor: Color,
    queued: Boolean,
    style: TvStationStyle,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(12.dp)
            // Queue and history are supporting visual context. Exposing every text line makes
            // VoiceView traverse titles, artists, albums, and durations for each station before
            // the listener can reach station controls or General Options. Keep both panels
            // visual-only; the current station and Now Playing remain accessible elsewhere.
            .clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "$eyebrow ${if (queued) "↑" else "↓"}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = TvCream,
        )
        when (state.queue?.status) {
            QueueLoadStatus.Loading, null -> Text(
                text = "Loading station data…",
                style = MaterialTheme.typography.titleMedium,
                color = style.secondary,
            )
            QueueLoadStatus.Error -> Text(
                text = state.queue.errorMessage ?: "Station data is unavailable.",
                style = MaterialTheme.typography.titleMedium,
                color = style.accent,
            )
            QueueLoadStatus.Unavailable -> Text(
                text = "Station data is unavailable.",
                style = MaterialTheme.typography.titleMedium,
                color = style.secondary.copy(alpha = 0.72f),
            )
            QueueLoadStatus.Ready -> {
                if (tracks.isEmpty()) {
                    Text(
                        text = "No public tracks are available right now.",
                        style = MaterialTheme.typography.titleMedium,
                        color = style.secondary.copy(alpha = 0.72f),
                    )
                } else {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        repeat(TvVisibleLibraryTrackSlots) { index ->
                            val track = tracks.getOrNull(index)
                            if (track == null) {
                                Spacer(Modifier.height(TvLibraryTrackSlotHeight))
                            } else {
                                TvTrackRow(
                                    track = track,
                                    index = index + 1,
                                    accentColor = accentColor,
                                    style = style,
                                    fallbackArtworkUrl = state.selectedStation?.logoUrl,
                                )
                            }
                        }
                    }
                }
                state.queue.errorMessage?.let { error ->
                    Text(error, style = MaterialTheme.typography.bodyLarge, color = style.accent)
                }
            }
        }
    }
}

@Composable
private fun TvTrackRow(
    track: TvLibraryTrack,
    index: Int,
    accentColor: Color,
    style: TvStationStyle,
    fallbackArtworkUrl: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TvTrackShape)
            .background(TvInk.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.size(width = 38.dp, height = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = index.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
            )
            track.durationLabel?.takeIf(String::isNotBlank)?.let { duration ->
                Text(
                    text = duration,
                    style = MaterialTheme.typography.labelSmall,
                    color = TvCream.copy(alpha = 0.64f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
        AsyncImage(
            model = track.artworkUrl ?: fallbackArtworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            fallback = painterResource(R.drawable.app_logo),
            error = painterResource(R.drawable.app_logo),
            placeholder = painterResource(R.drawable.app_logo),
            modifier = Modifier
                .size(44.dp)
                .clip(TvTrackShape)
                .background(style.glow),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            TvEllipsizedMetadataText(
                text = track.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = TvCream,
            )
            track.artistName?.takeIf(String::isNotBlank)?.let { artist ->
                TvEllipsizedMetadataText(
                    text = artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = TvCream.copy(alpha = 0.82f),
                )
            }
            track.albumTitle?.takeIf(String::isNotBlank)?.let { album ->
                TvEllipsizedMetadataText(
                    text = album,
                    style = MaterialTheme.typography.labelSmall,
                    color = TvCream.copy(alpha = 0.66f),
                )
            }
        }
    }
}

@Composable
private fun TvEllipsizedMetadataText(
    text: String,
    style: TextStyle,
    color: Color,
    fontWeight: FontWeight? = null,
    centered: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = style.copy(fontWeight = fontWeight),
        color = color,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun TvNowPlayingMarqueeText(
    text: String,
    style: TextStyle,
    color: Color,
    phase: Float,
    fontWeight: FontWeight? = null,
) {
    val edgeFadeWidthPx = with(LocalDensity.current) { 16.dp.toPx() }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val edgeFadeFraction = (edgeFadeWidthPx / size.width).coerceIn(0f, 0.5f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            edgeFadeFraction to Color.Black,
                            (1f - edgeFadeFraction) to Color.Black,
                            1f to Color.Transparent,
                        ),
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        val textStyle = style.copy(fontWeight = fontWeight)
        val textMeasurer = rememberTextMeasurer()
        val measuredText = remember(text, textStyle) {
            textMeasurer.measure(
                text = AnnotatedString(text),
                style = textStyle,
                overflow = TextOverflow.Clip,
                softWrap = false,
                maxLines = 1,
                constraints = Constraints(maxWidth = Constraints.Infinity),
            )
        }
        val availableWidth = with(LocalDensity.current) { maxWidth.roundToPx() }
        val travelDistance = (measuredText.size.width - availableWidth).coerceAtLeast(0) +
            with(LocalDensity.current) { 24.dp.roundToPx() }
        Text(
            text = text,
            modifier = Modifier
                // The marquee viewport clips this composable. Let the text itself retain its
                // intrinsic width so that translating it can reveal the entire title rather
                // than translating an already-clipped first line.
                .wrapContentWidth(unbounded = true)
                .graphicsLayer {
                translationX = if (measuredText.size.width > availableWidth) {
                    -travelDistance * phase
                } else {
                    (availableWidth - measuredText.size.width) / 2f
                }
            },
            style = textStyle,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun rememberTvArtworkRequest(artworkUrl: String?): ImageRequest {
    val context = LocalPlatformContext.current
    return remember(context, artworkUrl) {
        ImageRequest.Builder(context)
            .data(artworkUrl)
            .crossfade(TvArtworkCrossfadeMillis)
            .build()
    }
}

private data class TvLibraryTrack(
    val title: String,
    val artistName: String?,
    val albumTitle: String?,
    val durationLabel: String?,
    val artworkUrl: String?,
)

private data class TvNowPlayingMetadata(
    val title: String,
    val artist: String?,
    val album: String?,
)

private data class TvNowPlayingDetails(
    val title: String?,
    val artist: String?,
    val album: String?,
)

private fun tvCollapsedNowPlayingLabel(metadata: TvNowPlayingMetadata): String = listOfNotNull(
    metadata.title.takeIf(String::isNotBlank),
    metadata.artist?.takeIf(String::isNotBlank),
).joinToString(" · ")

private fun tvNowPlayingMetadata(nowPlaying: com.codeframe78.twentyfourseven.player.domain.NowPlayingState): TvNowPlayingDetails {
    val track = nowPlaying.track?.trim()?.takeIf(String::isNotEmpty)
    val artist = nowPlaying.artist?.trim()?.takeIf(String::isNotEmpty)
    val album = nowPlaying.album?.trim()?.takeIf(String::isNotEmpty)
    if (track != null || artist != null || album != null) {
        return TvNowPlayingDetails(track, artist, album)
    }
    val rawTitle = nowPlaying.displayTitle?.trim().orEmpty()
    val separator = rawTitle.indexOf(" - ")
    return if (separator > 0 && separator < rawTitle.lastIndex - 2) {
        TvNowPlayingDetails(
            title = rawTitle.substring(separator + 3).trim(),
            artist = rawTitle.substring(0, separator).trim(),
            album = null,
        )
    } else {
        TvNowPlayingDetails(rawTitle.takeIf(String::isNotBlank), null, null)
    }
}

private data class TvStationStyle(
    val accent: Color,
    val secondary: Color,
    val glow: Color,
    val backdropTop: Color,
    val backdropBottom: Color,
)

private fun tvStationStyle(stationId: StationId?): TvStationStyle {
    val palette = stationPalette(stationId)
    val (backdropTop, backdropBottom) = when (stationId?.value) {
        "sst" -> Color(0xFF000000) to Color(0xFF000044)
        "1980s" -> Color(0xFF160827) to Color(0xFF05051B)
        "afm" -> Color(0xFF1E0D0A) to Color(0xFF2D120F)
        "dfm" -> Color(0xFF08070D) to Color(0xFF110F24)
        "efm" -> Color(0xFF041B1D) to Color(0xFF05252D)
        else -> Color(0xFF100C18) to Color(0xFF1D1330)
    }
    return TvStationStyle(
        accent = palette.accent,
        secondary = palette.secondary,
        glow = palette.glow,
        backdropTop = backdropTop,
        backdropBottom = backdropBottom,
    )
}

private fun tvStationSelectorLogoResource(stationId: StationId, renderedSizePx: Int): Int {
    val use256PxAsset = renderedSizePx > 128
    return when (stationId.value) {
        "sst" -> if (use256PxAsset) R.drawable.station_logo_sst_256 else R.drawable.station_logo_sst
        "1980s" -> if (use256PxAsset) R.drawable.station_logo_1980s_256 else R.drawable.station_logo_1980s
        "afm" -> if (use256PxAsset) R.drawable.station_logo_adagio_256 else R.drawable.station_logo_adagio
        "dfm" -> if (use256PxAsset) R.drawable.station_logo_death_256 else R.drawable.station_logo_death
        "efm" -> if (use256PxAsset) R.drawable.station_logo_entranced_256 else R.drawable.station_logo_entranced
        else -> R.drawable.app_logo
    }
}

private fun tvColorScheme(style: TvStationStyle) = darkColorScheme(
    primary = style.secondary,
    onPrimary = TvInk,
    primaryContainer = style.glow,
    onPrimaryContainer = TvCream,
    secondary = style.accent,
    onSecondary = TvInk,
    background = style.backdropTop,
    onBackground = TvCream,
    surface = style.backdropBottom,
    onSurface = TvCream,
    surfaceVariant = style.glow,
    onSurfaceVariant = style.secondary,
    border = style.secondary,
    borderVariant = style.glow,
)

private val TvInk = Color(0xFF000000)
private val TvCream = Color(0xFFFFFFEE)
private val TvPanelShape = RoundedCornerShape(6.dp)
private val TvTrackShape = RoundedCornerShape(3.dp)
private const val TvNowPlayingMarqueeCycleDurationMillis = 18_000
private const val TvArtworkCrossfadeMillis = 450
private const val TvNowPlayingOverlayDurationMillis = 4_500L
private const val TvStationOptionsHintDurationMillis = 3_000L
private const val TvStationInterstitialWaveformCycleMillis = 2_400
private const val TvVisibleLibraryTrackSlots = 5
private val TvLibraryTrackSlotHeight = 60.dp
private val TvStationInterstitialWaveformHeights = listOf(
    0.28f, 0.42f, 0.66f, 0.88f, 0.58f, 0.74f, 0.96f, 0.64f, 0.46f, 0.70f, 0.34f,
)

private fun tvAnimatedWaveformHeight(baseHeight: Float, index: Int, phase: Float): Float {
    val offset = index / TvStationInterstitialWaveformHeights.size.toFloat()
    val pulse = ((sin((phase + offset) * (2f * PI.toFloat())) + 1f) / 2f)
    return (baseHeight * (0.64f + (pulse * 0.36f))).coerceAtLeast(0.18f)
}
