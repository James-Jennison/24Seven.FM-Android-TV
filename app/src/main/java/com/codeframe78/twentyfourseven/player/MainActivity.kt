package com.codeframe78.twentyfourseven.player

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codeframe78.twentyfourseven.player.domain.StationId
import com.codeframe78.twentyfourseven.player.ui.DiagnosticEnvironment
import com.codeframe78.twentyfourseven.player.ui.stationFromVoiceQuery
import com.codeframe78.twentyfourseven.player.ui.TvLaunchSplash
import com.codeframe78.twentyfourseven.player.ui.TvRadioApp
import com.codeframe78.twentyfourseven.player.ui.TvRadioViewModel

/**
 * The Android TV-only entry point. Mobile activities and external-app handoffs are deliberately
 * absent from this surface; the TV alpha is an anonymous listener experience.
 */
class MainActivity : AppCompatActivity() {
    /** A "play ... on 24Seven.FM" request waiting for the player screen; the counter makes a repeat of the same words count. */
    private var voicePlay by mutableStateOf<VoicePlay?>(null)

    private data class VoicePlay(val station: StationId?, val serial: Int)
    private var voiceSerial = 0

    private fun readVoiceIntent(intent: Intent?) {
        // Debug-only: the intent filter is in the debug manifest, and an explicit intent from another app must not work in release either.
        if (!BuildConfig.DEBUG || intent?.action != "android.media.action.MEDIA_PLAY_FROM_SEARCH") return
        val station = stationFromVoiceQuery(intent.getStringExtra(android.app.SearchManager.QUERY))
        android.util.Log.i("TvVoice", "play from search: station=${station?.value}")
        voicePlay = VoicePlay(station, ++voiceSerial)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readVoiceIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The system launch screen (animated antenna mark) shows until the first frame where the platform renders it; Google TV
        // does not, so TvLaunchSplash plays the same mark in-app. No exit hand-off is attempted on TV.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readVoiceIntent(intent)
        val container = (application as RadioApplication).appContainer
        setContent {
            val viewModel: TvRadioViewModel = viewModel(
                factory = TvRadioViewModel.Factory(
                    container.stationRepository,
                    container.playbackController,
                    container.nowPlayingRepository,
                    container.queueRepository,
                    container.idleScreenPreferences,
                    container.audioVisualizationRepository,
                    container.firstUseRepository,
                ),
            )
            val state = viewModel.uiState.collectAsStateWithLifecycle().value
            voicePlay?.let { request ->
                LaunchedEffect(request) { viewModel.playByVoice(request.station); voicePlay = null }
            }
            var launchSplashVisible by rememberSaveable { mutableStateOf(true) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // While the launch splash covers the player, keys must not act on UI the viewer cannot see. Back still
                    // passes so the app can be left at any time.
                    .onPreviewKeyEvent { event -> launchSplashVisible && event.key != Key.Back },
            ) {
                TvRadioApp(
                    state = state,
                    visualization = viewModel.visualization,
                    diagnosticEnvironment = diagnosticEnvironment(),
                    onSelectStation = viewModel::selectStation,
                    onCompleteFirstUse = viewModel::completeFirstUse,
                    onSetFirstUsePendingStation = viewModel::setFirstUsePendingStation,
                    onPause = viewModel::pause,
                    onSetSleepTimer = viewModel::setSleepTimer,
                    onCancelSleepTimer = viewModel::cancelSleepTimer,
                    onSelectIdleVisualizer = viewModel::selectIdleVisualizer,
                    onSetNowPlayingOverlay = viewModel::setNowPlayingOverlay,
                    onSetIdleScreenStartDelay = viewModel::setIdleScreenStartDelay,
                    onSetAutomaticIdleScreenEnabled = viewModel::setAutomaticIdleScreenEnabled,
                    onSetReduceVisualIntensity = viewModel::setReduceVisualIntensity,
                    onSetLavaLampPalette = viewModel::setLavaLampPalette,
                    onSetIonTunnelPalette = viewModel::setIonTunnelPalette,
                )
                if (launchSplashVisible) TvLaunchSplash(onFinished = { launchSplashVisible = false })
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun diagnosticEnvironment(): DiagnosticEnvironment {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        return DiagnosticEnvironment(
            appVersion = packageInfo.versionName ?: "Unknown",
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                packageInfo.versionCode.toLong()
            },
            buildType = if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) "debug" else "release",
            sourceRevision = applicationInfo.metaData
                ?.getString("com.codeframe78.twentyfourseven.player.BUILD_REVISION")
                ?: "unverified",
            androidRelease = Build.VERSION.RELEASE ?: "Unknown",
            apiLevel = Build.VERSION.SDK_INT,
            deviceManufacturer = Build.MANUFACTURER ?: "Unknown",
            deviceModel = Build.MODEL ?: "Unknown",
        )
    }
}
