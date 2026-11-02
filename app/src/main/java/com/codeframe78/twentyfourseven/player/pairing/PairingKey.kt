package com.codeframe78.twentyfourseven.player.pairing

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.net.InetAddress
import java.net.ServerSocket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.security.auth.x500.X500Principal

/**
 * One pairing's TLS identity: an EC P-256 key generated inside AndroidKeyStore with a self-signed
 * certificate. The phone pins the SHA-256 of the public key (SPKI), so the certificate itself carries
 * no trust. The key never leaves the keystore and is deleted when the pairing ends.
 *
 * Both digests are required: Conscrypt signs the TLS handshake with NONEwithECDSA, and a key limited to
 * SHA-256 fails every handshake (verified on the Google TV Streamer).
 */
internal class PairingKey private constructor(
    private val alias: String,
    val keyHash: ByteArray,
    private val sslContext: SSLContext,
) {
    /** TLS 1.3 only, bound to [address] so the listener is reachable on that interface and no other. */
    fun openServer(address: InetAddress): ServerSocket {
        val socket = sslContext.serverSocketFactory.createServerSocket(0, 8, address) as SSLServerSocket
        socket.enabledProtocols = arrayOf("TLSv1.3")
        return socket
    }

    fun delete() {
        runCatching { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(alias) }
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"

        /** TLS 1.3 needs Android 10; the phone only speaks TLS 1.3. */
        val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        fun generate(random: SecureRandom = SecureRandom()): PairingKey {
            val alias = "pairing-" + ByteArray(8).also(random::nextBytes).toHex()
            val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE).apply {
                initialize(
                    KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256)
                        .setCertificateSubject(X500Principal("CN=24seven-tv-pairing"))
                        .build(),
                )
            }.generateKeyPair()
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(store.getCertificate(alias).publicKey.encoded)
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(store, null)
            val context = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, null) }
            return PairingKey(alias, hash, context)
        }
    }
}
