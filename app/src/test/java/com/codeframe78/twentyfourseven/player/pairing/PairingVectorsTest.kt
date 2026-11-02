package com.codeframe78.twentyfourseven.player.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vectors computed independently by the mobile side with Python `hmac` (key hash = 32 bytes of 0x11). */
class PairingVectorsTest {
    private val keyHash = ByteArray(32) { 0x11 }
    private val stations = setOf("sst")

    private fun pairs(secret: ByteArray, proof: String): Boolean {
        val s = PairingSession(secret, keyHash, 1000, stations, { "s" }, PairingTimeouts())
        s.onFrame(PhoneFrame.Pair(proof, "p", "a", "sst"), 0)
        return s.state is PairingUiState.PairedIdle
    }

    @Test fun qrSecretVector() {
        assertTrue(pairs(ByteArray(16), "fbe4b1db0b7ee3c740efa1e88e5a3bd37e9e63d5792752f5064b43555f97b0c9"))
    }

    @Test fun typedCodeVector() {
        assertTrue(pairs("123456".toByteArray(Charsets.US_ASCII), "a9b701cbd81460b50517eeec120d8a1f5771fce14aae158b549edec95d60936f"))
    }

    @Test fun proofIsNotAcceptedForTheOtherSecret() {
        assertEquals(false, pairs("123456".toByteArray(Charsets.US_ASCII), "fbe4b1db0b7ee3c740efa1e88e5a3bd37e9e63d5792752f5064b43555f97b0c9"))
    }

    @Test fun fingerprintIsFourGroupsOfFour() {
        val hash = ByteArray(32) { if (it < 8) intArrayOf(0x2d, 0x71, 0x16, 0x42, 0xb7, 0x26, 0xb0, 0x44)[it].toByte() else 0 }
        assertEquals("2d71 1642 b726 b044", pairingFingerprint(hash))
    }
}
