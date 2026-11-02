package com.codeframe78.twentyfourseven.player.pairing

import org.json.JSONException
import org.json.JSONObject
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * Companion pairing protocol v1, TV side. The mobile repository holds the single copy of the
 * protocol document; this file only parses and builds its frames. Nothing here does I/O beyond
 * the stream it is handed, and nothing here is reachable until the pairing flag is on.
 */
internal const val PAIRING_PROTOCOL_VERSION = 1
internal const val MAX_FRAME_BYTES = 16 * 1024
internal const val MAX_LABEL_CHARS = 64
internal const val MAX_REASON_CHARS = 512
internal const val MAX_TEXT_CHARS = 256
internal const val MAX_TRACK_REF_CHARS = 128

class FrameException(message: String) : Exception(message)

/** 4-byte big-endian length followed by a UTF-8 JSON object. */
internal object FrameCodec {
    fun write(out: OutputStream, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_FRAME_BYTES) throw FrameException("frame too large")
        out.write(
            byteArrayOf(
                (bytes.size ushr 24).toByte(),
                (bytes.size ushr 16).toByte(),
                (bytes.size ushr 8).toByte(),
                bytes.size.toByte(),
            ),
        )
        out.write(bytes)
        out.flush()
    }

    /** Returns the next frame text, or null at a clean end of stream before any length byte. */
    fun read(input: InputStream): String? {
        val header = ByteArray(4)
        var read = 0
        while (read < 4) {
            val n = input.read(header, read, 4 - read)
            if (n < 0) {
                if (read == 0) return null
                throw EOFException("truncated frame header")
            }
            read += n
        }
        val length = ((header[0].toInt() and 0xff) shl 24) or
            ((header[1].toInt() and 0xff) shl 16) or
            ((header[2].toInt() and 0xff) shl 8) or
            (header[3].toInt() and 0xff)
        if (length < 2 || length > MAX_FRAME_BYTES) throw FrameException("bad frame length")
        val body = ByteArray(length)
        var got = 0
        while (got < length) {
            val n = input.read(body, got, length - got)
            if (n < 0) throw EOFException("truncated frame body")
            got += n
        }
        return String(body, Charsets.UTF_8)
    }
}

enum class ResultCode { Accepted, Refused, Uncertain }

/** Frames the phone sends to the TV. */
internal sealed interface PhoneFrame {
    data class Pair(val proof: String, val phone: String, val account: String, val station: String) : PhoneFrame
    data class Awaiting(val op: String) : PhoneFrame
    data class Declined(val op: String) : PhoneFrame
    data class Submitting(val op: String) : PhoneFrame
    data class Result(val op: String, val code: ResultCode, val reason: String?) : PhoneFrame
    data class StateChanged(val station: String, val account: String) : PhoneFrame
    data object Revoke : PhoneFrame
    data object Ping : PhoneFrame
    data object Pong : PhoneFrame
}

/** Frames the TV sends to the phone. */
internal sealed interface TvFrame {
    data class PairOk(val session: String) : TvFrame
    data class PairRefused(val reason: PairRefusal) : TvFrame
    data class Propose(
        val op: String,
        val station: String,
        val trackRef: String,
        val title: String,
        val artist: String,
        val album: String,
    ) : TvFrame
    data object Revoke : TvFrame
    data object Ping : TvFrame
    data object Pong : TvFrame
    data class Error(val code: String) : TvFrame
}

enum class PairRefusal(val wire: String) {
    Expired("expired"),
    BadProof("bad_proof"),
    AlreadyPaired("already_paired"),
}

internal sealed interface ParsedFrame {
    data class Frame(val frame: PhoneFrame) : ParsedFrame
    /** A different protocol version: answer `error: unsupported_version` and close. */
    data object UnsupportedVersion : ParsedFrame
    /** A well-formed frame of a type this version does not know: ignore it. */
    data object UnknownType : ParsedFrame
    data class Malformed(val reason: String) : ParsedFrame
}

internal object PairingJson {
    private val OP = Regex("[0-9a-f]{32}")
    private val PROOF = Regex("[0-9a-f]{64}")

    fun parsePhoneFrame(text: String): ParsedFrame {
        val json = try {
            JSONObject(text)
        } catch (_: JSONException) {
            return ParsedFrame.Malformed("not a JSON object")
        }
        val version = json.opt("v")
        if (version !is Int) return ParsedFrame.Malformed("missing version")
        if (version != PAIRING_PROTOCOL_VERSION) return ParsedFrame.UnsupportedVersion
        val type = json.opt("type") as? String ?: return ParsedFrame.Malformed("missing type")
        return when (type) {
            "pair" -> {
                val proof = json.string("proof", 64) ?: return bad("proof")
                if (!PROOF.matches(proof)) return bad("proof")
                val phone = json.label("phone") ?: return bad("phone")
                val account = json.label("account") ?: return bad("account")
                val station = json.string("station", 16) ?: return bad("station")
                ParsedFrame.Frame(PhoneFrame.Pair(proof, phone, account, station))
            }
            "awaiting" -> json.op()?.let { ParsedFrame.Frame(PhoneFrame.Awaiting(it)) } ?: bad("op")
            "declined" -> json.op()?.let { ParsedFrame.Frame(PhoneFrame.Declined(it)) } ?: bad("op")
            "submitting" -> json.op()?.let { ParsedFrame.Frame(PhoneFrame.Submitting(it)) } ?: bad("op")
            "result" -> {
                val op = json.op() ?: return bad("op")
                val code = when (json.opt("code")) {
                    "accepted" -> ResultCode.Accepted
                    "refused" -> ResultCode.Refused
                    "uncertain" -> ResultCode.Uncertain
                    else -> return bad("code")
                }
                val reason = if (json.has("reason")) json.string("reason", MAX_REASON_CHARS) ?: return bad("reason") else null
                ParsedFrame.Frame(PhoneFrame.Result(op, code, reason))
            }
            "state_changed" -> {
                val station = json.string("station", 16) ?: return bad("station")
                val account = json.label("account") ?: return bad("account")
                ParsedFrame.Frame(PhoneFrame.StateChanged(station, account))
            }
            "revoke" -> ParsedFrame.Frame(PhoneFrame.Revoke)
            "ping" -> ParsedFrame.Frame(PhoneFrame.Ping)
            "pong" -> ParsedFrame.Frame(PhoneFrame.Pong)
            else -> ParsedFrame.UnknownType
        }
    }

    fun encode(frame: TvFrame): String {
        val json = JSONObject().put("v", PAIRING_PROTOCOL_VERSION)
        when (frame) {
            is TvFrame.PairOk -> json.put("type", "pair_ok").put("session", frame.session)
            is TvFrame.PairRefused -> json.put("type", "pair_refused").put("reason", frame.reason.wire)
            is TvFrame.Propose -> json.put("type", "propose")
                .put("op", frame.op)
                .put("station", frame.station)
                .put("trackRef", frame.trackRef)
                .put("title", frame.title)
                .put("artist", frame.artist)
                .put("album", frame.album)
            TvFrame.Revoke -> json.put("type", "revoke")
            TvFrame.Ping -> json.put("type", "ping")
            TvFrame.Pong -> json.put("type", "pong")
            is TvFrame.Error -> json.put("type", "error").put("error", frame.code)
        }
        return json.toString()
    }

    private fun bad(field: String) = ParsedFrame.Malformed("bad $field")

    /** A string field of 1..[max] characters with no control characters, else null. */
    private fun JSONObject.string(name: String, max: Int): String? {
        val value = opt(name) as? String ?: return null
        if (value.isEmpty() || value.length > max) return null
        if (value.any { it.isISOControl() }) return null
        return value
    }

    private fun JSONObject.label(name: String) = string(name, MAX_LABEL_CHARS)

    private fun JSONObject.op(): String? = string("op", 32)?.takeIf(OP::matches)
}
