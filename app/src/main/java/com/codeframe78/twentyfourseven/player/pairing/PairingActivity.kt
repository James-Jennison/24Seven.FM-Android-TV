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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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
                val accounts by controller.accounts.collectAsStateWithLifecycle()
                // Outside a Surface the content colour is black; this screen draws on the theme background.
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                PairingScreen(
                    screen = screen,
                    accounts = accounts,
                    onSignOut = { station -> controller.signOutOnThisTv(station); if (controller.accounts.value.isEmpty()) controller.begin() },
                    onNewCode = controller::begin,
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
        if (BuildConfig.PAIRING_ENABLED) {
            // A TV that is already signed in shows its status and opens no listener; a code appears only when there is no session.
            // The stored sessions load off the main thread, so the decision waits for them; leaving the screen cancels it.
            startJob = lifecycleScope.launch {
                controller.loadAccounts()
                if (controller.accounts.value.isEmpty()) controller.begin()
            }
        }
    }

    private var startJob: kotlinx.coroutines.Job? = null

    override fun onStop() {
        startJob?.cancel()
        controller.end()
        super.onStop()
    }

    companion object {
        fun intent(context: Context) = Intent(context, PairingActivity::class.java)
    }
}

@Composable
internal fun PairingScreen(
    screen: PairingScreenState,
    accounts: List<StationAccount>,
    onSignOut: (String) -> Unit,
    onNewCode: () -> Unit,
    onDone: () -> Unit,
    onDisconnect: () -> Unit,
    onClose: () -> Unit,
) {
    PairingBackdrop {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        var addingStation by remember { mutableStateOf(false) }
        // A finished hand-over (a new account appearing) returns to the status list.
        LaunchedEffect(accounts.map { it.station to it.label }) { addingStation = false }
        if (accounts.isNotEmpty() && !addingStation) {
            AccountsStatus(accounts, onSignOut = onSignOut, onPairAnother = { addingStation = true; onNewCode() }, onClose = onClose)
            return@Column
        }
        when (screen) {
            is PairingScreenState.Unavailable -> Message(unavailableText(screen.why), "Back", onClose)
            PairingScreenState.Closed -> Message("Starting…", "Back", onClose)
            is PairingScreenState.Active -> when (val ui = screen.ui) {
                is PairingUiState.ShowingCode -> CodeScreen(screen.offer, onNewCode, onClose)
                is PairingUiState.PairedIdle -> Message(
                    "${ui.phone} is connected.\nApprove \"Sign this TV in\" on your phone to finish.",
                    primary = "Disconnect" to onDisconnect,
                )
                is PairingUiState.Waiting -> Message(
                    "${ui.track.title} — ${ui.track.artist}\n${waitingText(ui.phase)}",
                    primary = "Disconnect" to onDisconnect,
                )
                is PairingUiState.Finished -> Message("${ui.track.title}\n${outcomeText(ui.outcome)}", "OK", onDone)
                is PairingUiState.Ended -> Message(endedText(ui.reason), "New code" to onNewCode, "Back" to onClose)
            }
        }
    }
    }
}

@Composable
private fun AccountsStatus(accounts: List<StationAccount>, onSignOut: (String) -> Unit, onPairAnother: () -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(accounts.size) { runCatching { focus.requestFocus() } }
    Text("Signed in", style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(6.dp))
    Text("Request songs from General Options → Request a Song.", style = MaterialTheme.typography.titleMedium, color = PairingPalette.Muted)
    Spacer(Modifier.height(20.dp))
    accounts.forEach { account ->
        val expired = account.state == SignInState.SignedOut
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stationName(account.station), style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (expired) "${account.label} · the station ended this session, pair again" else account.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = PairingPalette.Muted,
                )
            }
            Chip(
                when (account.state) { SignInState.SignedIn -> "Signed in"; SignInState.SignedOut -> "Expired"; SignInState.Unknown -> "Checking…" },
                when (account.state) { SignInState.SignedIn -> PairingPalette.Green; SignInState.SignedOut -> PairingPalette.Amber; SignInState.Unknown -> PairingPalette.Lavender },
            )
            Pill("Sign out", onClick = { onSignOut(account.station) })
        }
    }
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Pill("Pair another station", filled = true, onClick = onPairAnother)
        // Back is the default focus: signing out must be a deliberate move.
        Pill("Back", onClick = onClose, modifier = Modifier.focusRequester(focus))
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
            Text(
                offer.typedCode.chunked(3).joinToString(" "),
                fontSize = 48.sp,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.semantics { contentDescription = "Pairing code " + offer.typedCode.toList().joinToString(", ") },
            )
            Text("${offer.host}   port ${offer.port}", style = MaterialTheme.typography.titleLarge)
            Text(
                "Security check: the phone must show ${offer.fingerprint}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { contentDescription = "Security check. The phone must show " + offer.fingerprint.filter { it != ' ' }.toList().joinToString(", ") },
            )
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

/** Short labels for crowded rows such as the station choices. */
internal fun stationShortName(id: String) = when (id) {
    "sst" -> "SST"
    "1980s" -> "1980s"
    "afm" -> "Adagio"
    "dfm" -> "Death"
    "efm" -> "Entranced"
    else -> id
}

internal fun stationName(id: String) = when (id) {
    "sst" -> "StreamingSoundtracks.com"
    "1980s" -> "1980s.FM"
    "afm" -> "Adagio.FM"
    "dfm" -> "Death.FM"
    "efm" -> "Entranced.FM"
    else -> id
}


internal fun tvRequestText(result: TvRequestResult) = when (result) {
    TvRequestResult.Queued -> "Requested. It is in the station's Queue."
    TvRequestResult.Received -> "Your request was sent to the DJ."
    is TvRequestResult.CheckQueue -> "${result.why} Check the Queue before requesting again. It was not resent."
    is TvRequestResult.Rejected -> result.message
    TvRequestResult.SignInExpired -> "The station asked for a sign-in, so the request was probably not made. Pair with your phone again, and check the Queue before requesting again."
    is TvRequestResult.NotAllowed -> "${result.why} Nothing was requested."
}

internal fun tvRequestHeadline(result: TvRequestResult) = when (result) {
    TvRequestResult.Queued -> "Requested"
    TvRequestResult.Received -> "Request received"
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
