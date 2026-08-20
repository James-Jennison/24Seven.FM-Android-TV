package com.codeframe78.twentyfourseven.player

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
