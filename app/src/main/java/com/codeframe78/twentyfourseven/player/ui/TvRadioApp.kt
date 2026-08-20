package com.codeframe78.twentyfourseven.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.codeframe78.twentyfourseven.player.R
import com.codeframe78.twentyfourseven.player.domain.PlaybackStatus
import com.codeframe78.twentyfourseven.player.domain.StationId

/**
 * The first TV alpha deliberately exposes only anonymous listening. Its controls are standard TV
 * Material controls so focus is visible and every action works through a D-pad remote.
 */
@Composable
internal fun TvRadioApp(
    state: MainUiState,
    onSelectStation: (StationId) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 64.dp, vertical = 40.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = "24Seven.FM TV",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Live radio from the 24Seven.FM network",
                    style = MaterialTheme.typography.titleMedium,
                )
                TvNowPlaying(state)
                Text(
                    text = "Choose a station",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    state.stations.forEach { station ->
                        val selected = station.id == state.selectedStation?.id
                        Button(
                            onClick = { onSelectStation(station.id) },
                            modifier = Modifier
                                .weight(1f)
                                .semantics {
                                    contentDescription = if (selected) {
                                        "${station.name}, selected"
                                    } else {
                                        station.name
                                    }
                                },
                        ) {
                            Text(
                                text = station.shortName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.playback.status == PlaybackStatus.Playing) {
                        Button(onClick = onPause) { Text("Pause") }
                    } else {
                        Button(onClick = onPlay) { Text("Play") }
                    }
                    Button(onClick = onStop) { Text("Stop") }
                    Text(
                        text = state.playback.status.name.lowercase().replaceFirstChar(Char::titlecase),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun TvNowPlaying(state: MainUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = state.nowPlaying.artworkUrl,
            contentDescription = "Now playing artwork",
            contentScale = ContentScale.Crop,
            fallback = painterResource(R.drawable.app_logo),
            error = painterResource(R.drawable.app_logo),
            placeholder = painterResource(R.drawable.app_logo),
            modifier = Modifier.size(236.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = state.selectedStation?.name ?: "Loading station",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.nowPlaying.displayTitle ?: "Waiting for live metadata",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Use the directional pad to choose a station, then select Play.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Start,
            )
        }
    }
}
