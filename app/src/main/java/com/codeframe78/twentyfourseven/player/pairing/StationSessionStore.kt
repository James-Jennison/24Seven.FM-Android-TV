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
) {
    override fun toString() = "StationSession(station=$station, cookies=${cookies.size})"
}

internal class StoredCookie(val name: String, val value: String, val expiresAtEpochMillis: Long?) {
    override fun toString() = "StoredCookie(name=$name)"
}

internal interface StationSessionStore {
    fun load(): StationSession?
    fun save(session: StationSession)
    /** Deletes the session on this TV only; it never asks the station to log the owner out. */
    fun clear()
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

    override fun load(): StationSession? {
        val encrypted = preferences.getString(KEY, null) ?: return null
        val now = epochMillis()
        return try {
            val json = JSONObject(decrypt(encrypted))
            val array = json.getJSONArray("cookies")
            val cookies = (0 until array.length()).map { i ->
                val c = array.getJSONObject(i)
                StoredCookie(c.getString("name"), c.getString("value"), if (c.has("exp")) c.getLong("exp") else null)
            }.filter { it.expiresAtEpochMillis == null || it.expiresAtEpochMillis > now }
            if (cookies.isEmpty()) { clear(); null } else StationSession(json.getString("station"), json.getString("account"), cookies)
        } catch (_: Exception) {
            clear() // unreadable (key lost, corrupted): never keep a blob we cannot use
            null
        }
    }

    override fun save(session: StationSession) {
        val json = JSONObject()
            .put("station", session.station)
            .put("account", session.account)
            .put("cookies", JSONArray().apply {
                session.cookies.forEach { c ->
                    put(JSONObject().put("name", c.name).put("value", c.value).apply { c.expiresAtEpochMillis?.let { put("exp", it) } })
                }
            })
        preferences.edit().putString(KEY, encrypt(json.toString())).apply()
    }

    override fun clear() { preferences.edit().remove(KEY).apply() }

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
        const val KEY = "session"
        const val ALIAS = "tv-station-session-key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

/** Builds a session from a handed-over frame, applying only bounded, well-formed cookies. */
internal fun stationSessionFrom(frame: PhoneFrame.Session, nowMillis: Long): StationSession = StationSession(
    station = frame.station,
    account = frame.account,
    cookies = frame.cookies.map { c ->
        // maxAge -1 is a browser-session cookie; cap long lifetimes at one year.
        val expires = if (c.maxAgeSeconds > 0) nowMillis + minOf(c.maxAgeSeconds, ONE_YEAR_SECONDS) * 1000 else null
        StoredCookie(c.name, c.value, expires)
    },
)

private const val ONE_YEAR_SECONDS = 365L * 24 * 3600
