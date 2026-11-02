package com.codeframe78.twentyfourseven.player.pairing

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** A station session handed over by the paired phone. The cookie values are credentials. */
internal class StationSession(
    val station: String,
    val account: String,
    val cookies: List<StoredCookie>,
    /** The gate the phone vouched for at hand-over time. */
    val gate: GateAttestation = GateAttestation(),
    /** When the phone vouched for the gate (epoch millis); the vouching expires so a revoked consent cannot linger. */
    val handedOverAtMillis: Long = 0L,
) {
    override fun toString() = "StationSession(station=$station, cookies=${cookies.size})"
}

internal class StoredCookie(val name: String, val value: String, val expiresAtEpochMillis: Long?) {
    override fun toString() = "StoredCookie(name=$name)"
}

internal interface StationSessionStore {
    /** Every stored session, one per station. */
    fun loadAll(): List<StationSession>
    fun load(station: String): StationSession? = loadAll().firstOrNull { it.station == station }
    /** Saves the station's session, replacing only that station's. */
    fun save(session: StationSession)
    /** Deletes one station's session on this TV only; it never asks the station to log the owner out. */
    fun clear(station: String)
    fun clearAll()
}

/**
 * AES-GCM encrypted with a non-exportable AndroidKeyStore key, in app-private preferences. It outlives a
 * pairing on purpose. The stored text never appears in logs; only counts and names do.
 */
internal class AndroidKeystoreStationSessionStore(
    context: Context,
    private val epochMillis: () -> Long = System::currentTimeMillis,
) : StationSessionStore {
    private val preferences = context.getSharedPreferences("tv_station_session", Context.MODE_PRIVATE)

    override fun loadAll(): List<StationSession> = SUPPORTED_STATIONS.mapNotNull { readStation(it) }

    private fun readStation(station: String): StationSession? {
        val encrypted = preferences.getString(keyFor(station), null)
            ?: legacySession(station)
            ?: return null
        val now = epochMillis()
        return try {
            val json = JSONObject(decrypt(encrypted))
            val array = json.getJSONArray("cookies")
            val cookies = (0 until array.length()).map { i ->
                val c = array.getJSONObject(i)
                StoredCookie(c.getString("name"), c.getString("value"), if (c.has("exp")) c.getLong("exp") else null)
            }.filter { it.expiresAtEpochMillis == null || it.expiresAtEpochMillis > now }
            if (cookies.isEmpty() || json.getString("station") != station) { clear(station); null }
            else StationSession(station, json.getString("account"), cookies, gateFrom(json.optJSONObject("gate")), json.optLong("at", 0L))
        } catch (_: Exception) {
            clear(station) // unreadable (key lost, corrupted): never keep a blob we cannot use
            null
        }
    }

    /** The first version stored a single session under one key; it is read once as that station's session and moved. */
    private fun legacySession(station: String): String? {
        val old = preferences.getString(LEGACY_KEY, null) ?: return null
        val owner = runCatching { JSONObject(decrypt(old)).getString("station") }.getOrNull() ?: return null
        if (owner != station) return null
        preferences.edit().putString(keyFor(station), old).remove(LEGACY_KEY).apply()
        return old
    }

    private fun gateFrom(o: JSONObject?) = if (o == null) GateAttestation() else
        GateAttestation(o.optBoolean("adult"), if (o.isNull("terms")) null else o.optString("terms"), o.optBoolean("visible"))

    override fun save(session: StationSession) {
        val json = JSONObject()
            .put("station", session.station)
            .put("account", session.account)
            .put("at", session.handedOverAtMillis)
            .put("gate", JSONObject().put("adult", session.gate.adult).put("terms", session.gate.termsVersion ?: JSONObject.NULL).put("visible", session.gate.communityVisible))
            .put("cookies", JSONArray().apply {
                session.cookies.forEach { c ->
                    put(JSONObject().put("name", c.name).put("value", c.value).apply { c.expiresAtEpochMillis?.let { put("exp", it) } })
                }
            })
        preferences.edit().putString(keyFor(session.station), encrypt(json.toString())).apply()
    }

    override fun clear(station: String) { preferences.edit().remove(keyFor(station)).apply() }

    override fun clearAll() {
        preferences.edit().apply { SUPPORTED_STATIONS.forEach { remove(keyFor(it)) }; remove(LEGACY_KEY) }.apply()
    }

    private fun keyFor(station: String) = "session_$station"

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return listOf(cipher.iv, encrypted).joinToString(".") { Base64.encodeToString(it, Base64.NO_WRAP) }
    }

    private fun decrypt(value: String): String {
        val parts = value.split('.', limit = 2)
        require(parts.size == 2)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        }
        return String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val LEGACY_KEY = "session"
        val SUPPORTED_STATIONS = com.codeframe78.twentyfourseven.player.domain.SUPPORTED_STATION_IDS.map { it.value }
        const val ALIAS = "tv-station-session-key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

/** Builds a session from a handed-over frame, applying only bounded, well-formed cookies. */
internal fun stationSessionFrom(frame: PhoneFrame.Session, nowMillis: Long): StationSession = StationSession(
    station = frame.station,
    account = frame.account,
    gate = frame.gate,
    handedOverAtMillis = nowMillis,
    cookies = frame.cookies.map { c ->
        // maxAge -1 is a browser-session cookie; cap long lifetimes at one year.
        val expires = if (c.maxAgeSeconds > 0) nowMillis + minOf(c.maxAgeSeconds, ONE_YEAR_SECONDS) * 1000 else null
        StoredCookie(c.name, c.value, expires)
    },
)

private const val ONE_YEAR_SECONDS = 365L * 24 * 3600
