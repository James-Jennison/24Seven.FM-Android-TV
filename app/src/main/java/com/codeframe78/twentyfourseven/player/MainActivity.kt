package com.codeframe78.twentyfourseven.player

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codeframe78.twentyfourseven.player.ui.MainViewModel
import com.codeframe78.twentyfourseven.player.ui.TvRadioApp

/**
 * The Android TV-only entry point. Mobile activities and external-app handoffs are deliberately
 * absent from this surface; the TV alpha is an anonymous listener experience.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as RadioApplication).appContainer
        setContent {
            val viewModel: MainViewModel = viewModel(
                factory = MainViewModel.Factory(
                    container.stationRepository,
                    container.playbackController,
                    container.nowPlayingRepository,
                    container.queueRepository,
                    container.authRepository,
                    container.chatRepository,
                    container.songRequestRepository,
                    container.favoriteTracksRepository,
                    container.listenerActivityRepository,
                    container.communitySafetyRepository,
                    container.communityNotificationRepository,
                ),
            )
            val state = viewModel.uiState.collectAsStateWithLifecycle().value
            TvRadioApp(
                state = state,
                onSelectStation = viewModel::selectStation,
                onPlay = viewModel::play,
                onPause = viewModel::pause,
                onStop = viewModel::stop,
            )
        }
    }
}
