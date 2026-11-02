package com.codeframe78.twentyfourseven.player.pairing

/** The first 64 bits of the pairing key hash as four groups of four hex digits, e.g. `2d71 1642 b726 b044`. */
internal fun pairingFingerprint(keyHash: ByteArray): String {
    require(keyHash.size == 32) { "key hash must be 32 bytes" }
    return keyHash.take(8).joinToString("") { "%02x".format(it) }.chunked(4).joinToString(" ")
}
