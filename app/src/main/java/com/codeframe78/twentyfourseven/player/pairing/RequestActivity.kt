package com.codeframe78.twentyfourseven.player.pairing

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import com.codeframe78.twentyfourseven.player.BuildConfig
import com.codeframe78.twentyfourseven.player.RadioApplication

/**
 * Search the station's library and request a track, using the session the phone handed over. It never opens
 * the pairing listener: it only needs the stored session, so a phone does not have to be nearby. Reachable only
 * with the companion feature flag on, and it sends nothing unless the separate TV-requests flag is on too.
 */
class RequestActivity : AppCompatActivity() {
    private val controller get() = (application as RadioApplication).appContainer.pairingController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.PAIRING_ENABLED) { finish(); return }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val account by controller.account.collectAsStateWithLifecycle()
                val request by controller.request.collectAsStateWithLifecycle()
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                    RequestScreen(
                        account = account,
                        request = request,
                        onPick = controller::beginTvRequest,
                        onConfirm = controller::confirmTvRequest,
                        onDismiss = controller::dismissTvRequest,
                        onOpenPairing = { startActivity(PairingActivity.intent(this@RequestActivity)); finish() },
                        onClose = ::finish,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (BuildConfig.PAIRING_ENABLED) controller.refreshAccount()
    }

    companion object {
        fun intent(context: Context) = Intent(context, RequestActivity::class.java)
    }
}

@Composable
internal fun RequestScreen(
    account: StationAccount?,
    request: TvRequestUi?,
    onPick: (CatalogTrack, String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onOpenPairing: () -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val station = account?.station
    // Kept across a request's confirm and result screens so the listener returns to the same results.
    val browser = remember(station) { station?.let { CatalogBrowser(StationRequestCatalog(), it, scope) } }
    when {
        request != null -> PairingBackdrop {
            Column(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 16.dp), verticalArrangement = Arrangement.Center) {
                when (request) {
                    is TvRequestUi.Confirming -> TrackCard(
                        track = request.track,
                        station = request.station,
                        headline = "Request this track?",
                        note = "This sends the request to ${stationName(request.station)} now.",
                        primary = "Request" to onConfirm,
                        secondary = "Cancel" to onDismiss,
                    )
                    is TvRequestUi.Sending -> Text("Sending the request…", style = MaterialTheme.typography.headlineMedium)
                    is TvRequestUi.Done -> TrackCard(
                        track = request.track,
                        station = station.orEmpty(),
                        headline = tvRequestHeadline(request.result),
                        note = tvRequestText(request.result),
                        primary = "OK" to onDismiss,
                    )
                }
            }
        }
        account == null || browser == null || station == null -> NeedsSession(
            "Pair your phone first so this TV can sign in to the station and request songs.",
            "Pair a Phone", onOpenPairing, onClose,
        )
        account.state == SignInState.SignedOut -> NeedsSession(
            "The station says this TV's session has expired. Pair your phone again to sign in.",
            "Pair a Phone", onOpenPairing, onClose,
        )
        else -> CatalogScreen(station, browser, onPick = { onPick(it, station) }, onExit = onClose)
    }
}

@Composable
private fun NeedsSession(text: String, action: String, onAction: () -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(text) { runCatching { focus.requestFocus() } }
    PairingBackdrop {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 64.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Text("Request a Song", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(16.dp))
            Text(text, style = MaterialTheme.typography.headlineSmall, color = PairingPalette.Muted)
            Spacer(Modifier.height(32.dp))
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Pill(action, filled = true, onClick = onAction, modifier = Modifier.focusRequester(focus))
                Pill("Back", onClick = onClose)
            }
        }
    }
}
