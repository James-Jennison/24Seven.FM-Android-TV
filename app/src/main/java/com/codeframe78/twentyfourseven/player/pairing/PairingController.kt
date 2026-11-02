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
internal class PairingController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val enabled: Boolean,
) {
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
