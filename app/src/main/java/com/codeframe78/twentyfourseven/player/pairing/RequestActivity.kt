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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
                val accounts by controller.accounts.collectAsStateWithLifecycle()
                val request by controller.request.collectAsStateWithLifecycle()
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                    RequestScreen(
                        accounts = accounts,
                        preferredStation = intent.getStringExtra(EXTRA_STATION),
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
        if (BuildConfig.PAIRING_ENABLED) controller.refreshAccounts()
    }

    companion object {
        private const val EXTRA_STATION = "station"

        /** [station] is the station the TV is playing; it is chosen first when the TV is signed in to it. */
        fun intent(context: Context, station: String? = null) =
            Intent(context, RequestActivity::class.java).apply { station?.let { putExtra(EXTRA_STATION, it) } }
    }
}

@Composable
internal fun RequestScreen(
    accounts: List<StationAccount>,
    preferredStation: String?,
    request: TvRequestUi?,
    onPick: (CatalogTrack, String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onOpenPairing: () -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf<String?>(null) }
    // The listener's pick, else the station being played, else the first signed-in station.
    val account = accounts.firstOrNull { it.station == chosen }
        ?: accounts.firstOrNull { it.station == preferredStation }
        ?: accounts.firstOrNull()
    val station = account?.station
    // One browser per station, kept across a request's confirm and result screens so the listener returns to the same results.
    val browsers = remember { mutableMapOf<String, CatalogBrowser>() }
    val browser = station?.let { st -> browsers.getOrPut(st) { CatalogBrowser(StationRequestCatalog(), st, scope) } }
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
                        station = request.station,
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
        else -> CatalogScreen(
            station = station,
            browser = browser,
            onPick = { onPick(it, station) },
            onExit = onClose,
            stationChoices = if (accounts.size > 1) accounts.map { it.station } else emptyList(),
            onStation = { chosen = it },
        )
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
