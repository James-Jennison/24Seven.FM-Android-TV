package com.codeframe78.twentyfourseven.player.pairing

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.compose.runtime.CompositionLocalProvider
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.Text
import com.codeframe78.twentyfourseven.player.BuildConfig
import com.codeframe78.twentyfourseven.player.RadioApplication
import kotlinx.coroutines.delay

/**
 * The pairing screen. It is reachable only when the PAIRING_ENABLED build flag is on, is not exported,
 * and the listener it controls exists only while this screen is in the foreground: it opens in
 * onStart and is torn down in onStop, whatever the pairing state. Playback keeps running behind it.
 */
class PairingActivity : AppCompatActivity() {
    private val controller get() = (application as RadioApplication).appContainer.pairingController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.PAIRING_ENABLED) { finish(); return }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val screen by controller.state.collectAsStateWithLifecycle()
                val account by controller.account.collectAsStateWithLifecycle()
                // Outside a Surface the content colour is black; this screen draws on the theme background.
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                PairingScreen(
                    screen = screen,
                    account = account,
                    onSignOut = controller::signOutOnThisTv,
                    onNewCode = controller::begin,
                    onTestRequest = { controller.propose(LINK_TEST_TRACK) },
                    onOpenRequests = { startActivity(RequestActivity.intent(this@PairingActivity)) },
                    onDone = controller::acknowledgeResult,
                    onDisconnect = controller::revoke,
                    onClose = ::finish,
                )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (BuildConfig.PAIRING_ENABLED) { controller.refreshAccount(); controller.begin() }
    }

    override fun onStop() {
        controller.end()
        super.onStop()
    }

    companion object {
        /**
         * A link test only: the phone cannot resolve this reference, so it answers `declined` and nothing is
         * ever submitted to a station. Real requests come from catalog search, which is not built yet.
         */
        internal val LINK_TEST_TRACK = ProposedTrack("sst", "TEST:0", "Link test", "24Seven.FM TV", "No request is sent")

        fun intent(context: Context) = Intent(context, PairingActivity::class.java)
    }
}

@Composable
internal fun PairingScreen(
    screen: PairingScreenState,
    account: StationAccount?,
    onSignOut: () -> Unit,
    onOpenRequests: () -> Unit,
    onNewCode: () -> Unit,
    onTestRequest: () -> Unit,
    onDone: () -> Unit,
    onDisconnect: () -> Unit,
    onClose: () -> Unit,
) {
    PairingBackdrop {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        val pairedStation = ((screen as? PairingScreenState.Active)?.ui as? PairingUiState.PairedIdle)?.station
        when (screen) {
            is PairingScreenState.Unavailable -> Message(unavailableText(screen.why), "Back", onClose)
            PairingScreenState.Closed -> Message("Starting…", "Back", onClose)
            is PairingScreenState.Active -> when (val ui = screen.ui) {
                is PairingUiState.ShowingCode -> CodeScreen(screen.offer, onNewCode, onClose)
                is PairingUiState.PairedIdle -> Message(
                    "Paired with ${ui.phone} (${ui.account}) on ${stationName(ui.station)}.\nThe phone must stay open on the Player.",
                    primary = "Request a song" to onOpenRequests,
                    secondary = "Disconnect" to onDisconnect,
                    tertiary = "Send link test" to onTestRequest,
                )
                is PairingUiState.Waiting -> Message(
                    "${ui.track.title} — ${ui.track.artist}\n${waitingText(ui.phase)}",
                    primary = "Disconnect" to onDisconnect,
                )
                is PairingUiState.Finished -> Message("${ui.track.title}\n${outcomeText(ui.outcome)}", "OK", onDone)
                is PairingUiState.Ended -> Message(endedText(ui.reason), "New code" to onNewCode, "Back" to onClose)
            }
        }
        if (account != null) {
            Spacer(Modifier.height(24.dp))
            Text(accountText(account), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (pairedStation == null) {
                    Pill("Request a song", filled = true, onClick = onOpenRequests)
                }
                Pill("Sign out on this TV", onClick = onSignOut)
            }
        }
    }
    }
}

@Composable
private fun Message(text: String, label: String, onClick: () -> Unit) = Message(text, primary = label to onClick)

@Composable
private fun Message(text: String, primary: Pair<String, () -> Unit>, secondary: Pair<String, () -> Unit>? = null, tertiary: Pair<String, () -> Unit>? = null) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(text) { runCatching { focus.requestFocus() } }
    Text(text, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(32.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Pill(primary.first, filled = true, onClick = primary.second, modifier = Modifier.focusRequester(focus))
        secondary?.let { Pill(it.first, onClick = it.second) }
        tertiary?.let { Pill(it.first, onClick = it.second) }
    }
}

@Composable
private fun CodeScreen(offer: PairingOffer, onNewCode: () -> Unit, onClose: () -> Unit) {
    val qr = remember(offer) { renderQr(offer.qrPayload(), 560).asImageBitmap() }
    var remaining by remember(offer) { mutableLongStateOf(offer.expiresAtMillis - SystemClock.elapsedRealtime()) }
    LaunchedEffect(offer) {
        while (true) {
            remaining = offer.expiresAtMillis - SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(offer) { runCatching { focus.requestFocus() } }
    Row(horizontalArrangement = Arrangement.spacedBy(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(
            qr,
            contentDescription = null,
            modifier = Modifier.size(300.dp).semantics { contentDescription = "Pairing QR code. Use the typed code if you cannot scan." },
        )
        Column(modifier = Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pair your phone", style = MaterialTheme.typography.headlineLarge)
            Text("Open 24Seven.FM Player on your phone, choose Pair a TV, and scan this code.", style = MaterialTheme.typography.bodyLarge)
            Text("Or type this code on the phone:", style = MaterialTheme.typography.bodyLarge)
            Text(offer.typedCode.chunked(3).joinToString(" "), fontSize = 48.sp, style = MaterialTheme.typography.displaySmall)
            Text("${offer.host}   port ${offer.port}", style = MaterialTheme.typography.titleLarge)
            Text("Security check: the phone must show ${offer.fingerprint}", style = MaterialTheme.typography.titleLarge)
            Text(
                if (remaining > 0) "Code expires in ${remaining / 60000}:${"%02d".format(remaining / 1000 % 60)}" else "Code expired",
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Pill("New code", filled = true, onClick = onNewCode, modifier = Modifier.focusRequester(focus))
                Pill("Back", onClick = onClose)
            }
        }
    }
}

internal fun unavailableText(why: PairingUnavailable) = when (why) {
    PairingUnavailable.Disabled -> "Phone pairing is not available in this build."
    PairingUnavailable.UnsupportedAndroid -> "Phone pairing needs Android 10 or later."
    PairingUnavailable.NoLocalNetwork -> "Connect this TV to your home network (Wi-Fi or Ethernet) to pair a phone."
    PairingUnavailable.StartFailed -> "Pairing could not start. Try again."
}

internal fun waitingText(phase: WaitingPhase) = when (phase) {
    WaitingPhase.Sent -> "Asking your phone…"
    WaitingPhase.PhoneConfirming -> "Waiting for you to approve on your phone"
    WaitingPhase.Submitting -> "Your phone is sending the request…"
}

internal fun outcomeText(outcome: RequestOutcome) = when (outcome) {
    RequestOutcome.Accepted -> "Request sent."
    is RequestOutcome.Refused -> "The station did not accept it" + outcome.reason.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
    RequestOutcome.Declined -> "Cancelled on your phone. Nothing was sent."
    RequestOutcome.PhoneUnreachableNothingSubmitted -> "Your phone could not be reached. Nothing was sent."
    RequestOutcome.UnknownCheckQueue -> "Not sure whether it was sent. Check the Queue before trying again."
    RequestOutcome.UncertainCheckQueue -> "The station's answer was unclear. Check the Queue before trying again."
}

internal fun endedText(reason: EndReason) = when (reason) {
    EndReason.CodeExpired -> "The code expired."
    EndReason.TooManyBadProofs -> "Too many wrong attempts. Start again with a new code."
    EndReason.RevokedByPhone -> "Your phone ended the pairing."
    EndReason.RevokedByTv -> "Disconnected."
    EndReason.StateChanged -> "The station or account changed on your phone, so the pairing ended."
    EndReason.Disconnected -> "Your phone disconnected."
    EndReason.ProtocolError, EndReason.UnsupportedVersion -> "The phone and this TV could not agree on how to talk. Update both apps."
}

internal fun stationName(id: String) = when (id) {
    "sst" -> "StreamingSoundtracks.com"
    "1980s" -> "1980s.FM"
    "afm" -> "Adagio.FM"
    "dfm" -> "Death.FM"
    "efm" -> "Entranced.FM"
    else -> id
}

internal fun accountText(account: StationAccount) = when (account.state) {
    SignInState.SignedIn -> "Signed in as ${account.label} on ${stationName(account.station)}."
    SignInState.SignedOut -> "The session for ${account.label} on ${stationName(account.station)} has expired. Pair with your phone again."
    SignInState.Unknown -> "Signed in as ${account.label} on ${stationName(account.station)} (not checked yet)."
}

internal fun tvRequestText(result: TvRequestResult) = when (result) {
    TvRequestResult.Queued -> "Requested. It is in the station's Queue."
    is TvRequestResult.CheckQueue -> "${result.why} Check the Queue before requesting again. It was not resent."
    is TvRequestResult.Rejected -> result.message
    TvRequestResult.SignInExpired -> "The station says you are signed out. Pair with your phone again. Nothing was requested."
    is TvRequestResult.NotAllowed -> "${result.why} Nothing was requested."
}

internal fun tvRequestHeadline(result: TvRequestResult) = when (result) {
    TvRequestResult.Queued -> "Requested"
    is TvRequestResult.CheckQueue -> "Check the Queue"
    is TvRequestResult.Rejected -> "The station said no"
    TvRequestResult.SignInExpired -> "Pair with your phone again"
    is TvRequestResult.NotAllowed -> "Nothing was requested"
}

/** Artwork on the left, the track and the decision on the right. */
@Composable
internal fun TrackCard(
    track: CatalogTrack,
    station: String,
    headline: String,
    note: String,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>? = null,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(headline) { runCatching { focus.requestFocus() } }
    Row(horizontalArrangement = Arrangement.spacedBy(48.dp), verticalAlignment = Alignment.CenterVertically) {
        CoverArt(coverUrl(station, track.albumId), 320.dp, track.title)
        Column(Modifier.width(760.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(headline, style = MaterialTheme.typography.titleLarge, color = PairingPalette.Lavender)
            Text(track.title, style = MaterialTheme.typography.displaySmall, maxLines = 2)
            track.artist?.let { Text(it, style = MaterialTheme.typography.headlineSmall, color = PairingPalette.Muted, maxLines = 1) }
            track.albumTitle?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = PairingPalette.Muted, maxLines = 1) }
            Spacer(Modifier.height(8.dp))
            Text(note, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Pill(primary.first, filled = true, onClick = primary.second, modifier = Modifier.focusRequester(focus))
                secondary?.let { Pill(it.first, onClick = it.second) }
            }
        }
    }
}
