package com.codeframe78.twentyfourseven.player

import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codeframe78.twentyfourseven.player.ui.DiagnosticEnvironment
import com.codeframe78.twentyfourseven.player.ui.TvRadioApp
import com.codeframe78.twentyfourseven.player.ui.TvRadioViewModel

/**
 * The Android TV-only entry point. Mobile activities and external-app handoffs are deliberately
 * absent from this surface; the TV alpha is an anonymous listener experience.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
