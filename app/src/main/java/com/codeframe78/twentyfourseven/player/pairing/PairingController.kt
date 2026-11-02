package com.codeframe78.twentyfourseven.player.pairing

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.SystemClock
import com.codeframe78.twentyfourseven.player.domain.SUPPORTED_STATION_IDS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.InetAddress

/** Why a pairing cannot be offered right now. */
enum class PairingUnavailable { Disabled, UnsupportedAndroid, NoLocalNetwork, StartFailed }

internal sealed interface PairingScreenState {
    data class Unavailable(val why: PairingUnavailable) : PairingScreenState
    /** Idle: no listener exists. */
    data object Closed : PairingScreenState
    data class Active(val offer: PairingOffer, val ui: PairingUiState) : PairingScreenState
}

/**
 * Starts and ends the pairing for the pairing screen. A listener exists only between [begin] and [end]
 * (or the end of the pairing itself), so nothing runs while the screen is not showing.
 */
internal sealed interface TvRequestUi {
    data class Confirming(val track: CatalogTrack, val station: String) : TvRequestUi
    data class Sending(val track: CatalogTrack) : TvRequestUi
    data class Done(val track: CatalogTrack, val station: String, val result: TvRequestResult) : TvRequestUi
}

internal data class StationAccount(val label: String, val station: String, val state: SignInState)

internal class PairingController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val enabled: Boolean,
    private val store: StationSessionStore = AndroidKeystoreStationSessionStore(context),
    private val accountCheck: StationAccountCheck = HomePageAccountCheck(),
    private val submitter: RequestSubmitter = RequestSubmitter(
        enabled = com.codeframe78.twentyfourseven.player.BuildConfig.TV_REQUESTS_ENABLED,
        fetch = HttpSessionPageFetch(diag = { android.util.Log.i("PairingHost", "tv request: $it") }),
        queue = QueueCheck { station, _, songId ->
            val payload = com.codeframe78.twentyfourseven.player.data.PlayerQueueRemoteDataSource()
                .fetchForVerification(com.codeframe78.twentyfourseven.player.domain.StationId(station))
            payload.upcoming.any { it.songId == songId }
        },
        onReplyText = { android.util.Log.i("PairingHost", "tv request: $it") },
    ),
) {
    /** The TV's own request flow (only with TV_REQUESTS_ENABLED): confirm on the TV, send once, show the result. */
    private val _request = MutableStateFlow<TvRequestUi?>(null)
    val request: StateFlow<TvRequestUi?> = _request

    /** The listener picked a track: ask them to confirm on the TV. Nothing is sent yet. */
    fun beginTvRequest(track: CatalogTrack, station: String) {
        if (_request.value != null) return
        _request.value = TvRequestUi.Confirming(track, station)
    }

    /** The listener confirmed. Sends exactly once; a second call while sending or after a result does nothing. */
    fun confirmTvRequest() {
        val current = _request.value as? TvRequestUi.Confirming ?: return
        _request.value = TvRequestUi.Sending(current.track)
        scope.launch {
            val session = runCatching { store.load(current.station) }.getOrNull()
            val result = submitter.submit(session, current.track, current.station)
            android.util.Log.i("PairingHost", "tv request: ${result::class.simpleName}")
            _request.value = TvRequestUi.Done(current.track, current.station, result)
        }
    }

    fun dismissTvRequest() {
        if (_request.value !is TvRequestUi.Sending) _request.value = null
    }

    /** Every station this TV is signed in to, one account per station; they outlive any pairing. */
    private val _accounts = MutableStateFlow<List<StationAccount>>(emptyList())
    val accounts: StateFlow<List<StationAccount>> = _accounts
    private var closeJob: kotlinx.coroutines.Job? = null

    /** Loads the stored sessions (no network) so the screens can show them, then checks each with its station. */
    fun refreshAccounts() {
        if (!enabled) return
        val stored = runCatching { store.loadAll() }.getOrDefault(emptyList())
        val previous = _accounts.value.associateBy { it.station }
        _accounts.value = stored.map { StationAccount(it.account, it.station, previous[it.station]?.takeIf { a -> a.label == it.account }?.state ?: SignInState.Unknown) }
        stored.forEach(::verify)
    }

    /** Deletes one station's session on this TV only. It does not ask the station to log out, which would end the phone's session too. */
    fun signOutOnThisTv(station: String) {
        store.clear(station)
        _accounts.value = _accounts.value.filterNot { it.station == station }
        android.util.Log.i("PairingHost", "session cleared on this TV: station=$station")
    }

    fun signOutAllOnThisTv() {
        store.clearAll()
        _accounts.value = emptyList()
        android.util.Log.i("PairingHost", "all sessions cleared on this TV")
    }

    private fun setAccount(account: StationAccount) {
        _accounts.value = (_accounts.value.filterNot { it.station == account.station } + account).sortedBy { it.station }
    }

    private fun verify(session: StationSession) {
        scope.launch {
            val state = accountCheck.check(session)
            android.util.Log.i("PairingHost", "session check: station=${session.station} signedIn=${state == SignInState.SignedIn} (${state.name})")
            if (_accounts.value.any { it.station == session.station && it.label == session.account }) setAccount(StationAccount(session.account, session.station, state))
        }
    }

    private fun acceptSession(frame: PhoneFrame.Session): String? {
        if (StationOrigins.of(frame.station) == null) return "unsupported_station"
        frame.gate.refusalReason?.let { return it }
        val session = stationSessionFrom(frame, System.currentTimeMillis())
        return try {
            store.save(session)
            setAccount(StationAccount(session.account, session.station, SignInState.Unknown))
            verify(session)
            // A phone signed in to several stations sends one frame each, so the link stays a little longer after each hand-over.
            closeJob?.cancel()
            val pairing = host
            closeJob = scope.launch { kotlinx.coroutines.delay(HANDOVER_GRACE_MILLIS); if (host === pairing) end() }
            null
        } catch (_: Exception) {
            "storage_failed"
        }
    }

    private val _state = MutableStateFlow<PairingScreenState>(if (enabled) PairingScreenState.Closed else PairingScreenState.Unavailable(PairingUnavailable.Disabled))
    val state: StateFlow<PairingScreenState> = _state
    private var host: PairingHost? = null
    private var key: PairingKey? = null
    private var watcher: kotlinx.coroutines.Job? = null

    fun begin() {
        end()
        if (!enabled) return
        if (!PairingKey.isSupported) { _state.value = PairingScreenState.Unavailable(PairingUnavailable.UnsupportedAndroid); return }
        val address = localAddress(context) ?: run { _state.value = PairingScreenState.Unavailable(PairingUnavailable.NoLocalNetwork); return }
        try {
            val newKey = PairingKey.generate()
            val newHost = PairingHost(
                parentScope = scope,
                openServer = { newKey.openServer(address) },
                keyHash = newKey.keyHash,
                hostAddress = address.hostAddress.orEmpty(),
                tvName = tvName(),
                supportedStations = SUPPORTED_STATION_IDS.mapTo(mutableSetOf()) { it.value },
                monotonicMillis = SystemClock::elapsedRealtime,
                epochMillis = System::currentTimeMillis,
                log = { android.util.Log.i("PairingHost", it) },
                acceptSession = ::acceptSession,
            )
            key = newKey
            host = newHost
            newHost.start()
            watcher = scope.launch { newHost.state.collect { ui -> _state.value = PairingScreenState.Active(newHost.offer, ui) } }
            _state.value = PairingScreenState.Active(newHost.offer, newHost.state.value)
        } catch (_: Exception) {
            end()
            _state.value = PairingScreenState.Unavailable(PairingUnavailable.StartFailed)
        }
    }

    fun propose(track: ProposedTrack) { host?.propose(track) }
    fun acknowledgeResult() { host?.acknowledgeResult() }
    fun revoke() { host?.revoke() }

    /** Stops listening, drops every connection and deletes the key. */
    fun end() {
        if (host != null) android.util.Log.i("PairingHost", "controller.end: pairing screen left or restarted")
        // A pending hand-over timer belongs to the pairing being ended; it must never close a later one.
        closeJob?.cancel()
        closeJob = null
        watcher?.cancel()
        watcher = null
        host?.close()
        host = null
        key?.delete()
        key = null
        if (enabled && _state.value is PairingScreenState.Active) _state.value = PairingScreenState.Closed
    }

    private fun tvName(): String = (Build.MODEL ?: "TV").trim().take(MAX_LABEL_CHARS).ifEmpty { "TV" }

    companion object {
        private const val HANDOVER_GRACE_MILLIS = 20_000L

        /** A private-network IPv4 address on the active network, or null (not on a home network). */
        fun localAddress(context: Context): InetAddress? {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
            val network = cm.activeNetwork ?: return null
            val props = cm.getLinkProperties(network) ?: return null
            return props.linkAddresses.map { it.address }
                .firstOrNull { it is Inet4Address && (it.isSiteLocalAddress || it.isLinkLocalAddress) }
        }
    }
}
