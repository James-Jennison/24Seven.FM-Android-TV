package com.codeframe78.twentyfourseven.player.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException

class PairingProtocolTest {
    private val op = "0123456789abcdef0123456789abcdef"

    private fun parse(json: String) = PairingJson.parsePhoneFrame(json)
    private fun frame(json: String) = (parse(json) as ParsedFrame.Frame).frame

    @Test fun framesRoundTripThroughTheCodec() {
        val out = ByteArrayOutputStream()
        FrameCodec.write(out, """{"v":1,"type":"ping"}""")
        FrameCodec.write(out, """{"v":1,"type":"pong"}""")
        val input = ByteArrayInputStream(out.toByteArray())
        assertEquals("""{"v":1,"type":"ping"}""", FrameCodec.read(input))
        assertEquals("""{"v":1,"type":"pong"}""", FrameCodec.read(input))
        assertNull(FrameCodec.read(input))
    }

    @Test fun lengthIsBigEndianFourBytes() {
        val out = ByteArrayOutputStream()
        FrameCodec.write(out, """{"v":1}""")
        assertEquals(listOf<Byte>(0, 0, 0, 7), out.toByteArray().take(4))
    }

    @Test fun oversizeAndZeroLengthFramesAreRejected() {
        val big = byteArrayOf(0, 0, 0x40, 1) + ByteArray(MAX_FRAME_BYTES + 1)
        try { FrameCodec.read(ByteArrayInputStream(big)); fail() } catch (_: FrameException) {}
        try { FrameCodec.read(ByteArrayInputStream(byteArrayOf(0, 0, 0, 0))); fail() } catch (_: FrameException) {}
        try { FrameCodec.write(ByteArrayOutputStream(), "x".repeat(MAX_FRAME_BYTES + 1)); fail() } catch (_: FrameException) {}
    }

    @Test fun truncatedFramesAreAnError() {
        try { FrameCodec.read(ByteArrayInputStream(byteArrayOf(0, 0))); fail() } catch (_: EOFException) {}
        try { FrameCodec.read(ByteArrayInputStream(byteArrayOf(0, 0, 0, 9, 1, 2))); fail() } catch (_: EOFException) {}
    }

    @Test fun parsesEveryPhoneFrame() {
        val proof = "a".repeat(64)
        assertEquals(
            PhoneFrame.Pair(proof, "Pixel", "dj_x", "sst"),
            frame("""{"v":1,"type":"pair","proof":"$proof","phone":"Pixel","account":"dj_x","station":"sst"}"""),
        )
        assertEquals(PhoneFrame.Awaiting(op), frame("""{"v":1,"type":"awaiting","op":"$op"}"""))
        assertEquals(PhoneFrame.Declined(op), frame("""{"v":1,"type":"declined","op":"$op"}"""))
        assertEquals(PhoneFrame.Submitting(op), frame("""{"v":1,"type":"submitting","op":"$op"}"""))
        assertEquals(
            PhoneFrame.Result(op, ResultCode.Refused, "cooldown"),
            frame("""{"v":1,"type":"result","op":"$op","code":"refused","reason":"cooldown"}"""),
        )
        assertEquals(PhoneFrame.Result(op, ResultCode.Accepted, null), frame("""{"v":1,"type":"result","op":"$op","code":"accepted"}"""))
        assertEquals(PhoneFrame.StateChanged("afm", "x"), frame("""{"v":1,"type":"state_changed","station":"afm","account":"x"}"""))
        assertEquals(PhoneFrame.Revoke, frame("""{"v":1,"type":"revoke"}"""))
        assertEquals(PhoneFrame.Ping, frame("""{"v":1,"type":"ping"}"""))
        assertEquals(PhoneFrame.Pong, frame("""{"v":1,"type":"pong"}"""))
    }

    @Test fun otherVersionIsUnsupportedAndUnknownTypeIsIgnored() {
        assertEquals(ParsedFrame.UnsupportedVersion, parse("""{"v":2,"type":"ping"}"""))
        assertEquals(ParsedFrame.UnknownType, parse("""{"v":1,"type":"future_thing"}"""))
    }

    @Test fun malformedFramesAreRejected() {
        val bad = listOf(
            "not json", "[]", """{"type":"ping"}""", """{"v":"1","type":"ping"}""", """{"v":1}""",
            """{"v":1,"type":"awaiting"}""", """{"v":1,"type":"awaiting","op":"XYZ"}""",
            """{"v":1,"type":"awaiting","op":"${op.uppercase()}"}""",
            """{"v":1,"type":"result","op":"$op","code":"maybe"}""",
            """{"v":1,"type":"pair","proof":"abc","phone":"p","account":"a","station":"sst"}""",
            """{"v":1,"type":"pair","proof":"${"a".repeat(64)}","phone":"","account":"a","station":"sst"}""",
            """{"v":1,"type":"pair","proof":"${"a".repeat(64)}","phone":"p\u0007","account":"a","station":"sst"}""",
            """{"v":1,"type":"pair","proof":"${"a".repeat(64)}","phone":"${"p".repeat(65)}","account":"a","station":"sst"}""",
        )
        for (json in bad) assertTrue(json, parse(json) is ParsedFrame.Malformed)
    }

    @Test fun tvFramesEncodeWithVersionAndType() {
        val propose = PairingJson.encode(TvFrame.Propose(op, "sst", "A1:99", "T", "Ar", "Al"))
        assertEquals(
            """{"v":1,"type":"propose","op":"$op","station":"sst","trackRef":"A1:99","title":"T","artist":"Ar","album":"Al"}""".length,
            propose.length,
        )
        assertTrue(propose.contains("\"type\":\"propose\"") && propose.contains("\"v\":1"))
        assertTrue(PairingJson.encode(TvFrame.PairRefused(PairRefusal.BadProof)).contains("\"reason\":\"bad_proof\""))
        assertTrue(PairingJson.encode(TvFrame.Error("unsupported_version")).contains("unsupported_version"))
    }

    private fun session(cookies: String, extra: String = "") =
        PairingJson.parsePhoneFrame("""{"v":1,"type":"session","station":"sst","account":"dj_x","cookies":$cookies$extra}""")

    @Test fun sessionFrameParsesAndHidesValues() {
        val parsed = session("""[{"name":"user","value":"a%2Fb=c","maxAge":3600},{"name":"PHPSESSID","value":"zz","maxAge":-1}]""")
        val frame = (parsed as ParsedFrame.Frame).frame as PhoneFrame.Session
        assertEquals(2, frame.cookies.size)
        assertEquals("a%2Fb=c", frame.cookies[0].value)
        assertFalse(frame.toString().contains("a%2Fb"))
        assertFalse(frame.toString().contains("zz"))
    }

    @Test fun sessionFrameRejectsHostileCookies() {
        listOf(
            """[]""",
            """[{"name":"a b","value":"x"}]""",
            """[{"name":"a","value":"x;y"}]""",
            """[{"name":"a","value":"x y"}]""",
            """[{"name":"a","value":"x\r\nSet-Cookie: z"}]""",
            """[{"name":"a","value":"1"},{"name":"a","value":"2"}]""",
            """[{"name":"a"}]""",
        ).forEach { assertTrue(it, session(it) is ParsedFrame.Malformed) }
        val many = (1..17).joinToString(",", "[", "]") { """{"name":"c$it","value":"v"}""" }
        assertTrue(session(many) is ParsedFrame.Malformed)
    }

    @Test fun gateAttestationIsOnlyTrustedWhenCompleteAndCurrent() {
        fun gate(extra: String) = ((session("""[{"name":"a","value":"b"}]""", extra) as ParsedFrame.Frame).frame as PhoneFrame.Session).gate
        assertFalse(gate("").allowsContributing)
        assertTrue(gate(""","adultAgeGate":true,"termsVersion":"2026-07-18","communityVisible":true""").allowsContributing)
        assertFalse(gate(""","adultAgeGate":true,"termsVersion":"2025-01-01","communityVisible":true""").allowsContributing)
        assertFalse(gate(""","adultAgeGate":false,"termsVersion":"2026-07-18","communityVisible":true""").allowsContributing)
        assertFalse(gate(""","adultAgeGate":true,"termsVersion":"2026-07-18","communityVisible":false""").allowsContributing)
        assertFalse(gate(""","adultAgeGate":"true","termsVersion":"2026-07-18","communityVisible":true""").allowsContributing)
    }

    @Test fun nestedGateObjectAndRefusalReasons() {
        fun gate(g: String) = ((session("""[{"name":"a","value":"b"}]""", ""","gate":$g""") as ParsedFrame.Frame).frame as PhoneFrame.Session).gate
        val ok = gate("""{"adultAgeGate":true,"termsVersion":"2026-07-18","communityVisible":true}""")
        assertTrue(ok.allowsContributing); assertNull(ok.refusalReason)
        assertEquals("gate_not_passed", gate("""{"adultAgeGate":false,"termsVersion":"2026-07-18","communityVisible":true}""").refusalReason)
        assertEquals("gate_not_passed", gate("""{"adultAgeGate":true,"termsVersion":"2026-07-18","communityVisible":false}""").refusalReason)
        assertEquals("terms_out_of_date", gate("""{"adultAgeGate":true,"termsVersion":"2025-01-01","communityVisible":true}""").refusalReason)
        assertEquals("gate_not_passed", gate("""{}""").refusalReason)
    }

    @Test fun deeplyNestedJsonIsRejectedBeforeParsing() {
        val deep = "[".repeat(5000) + "]".repeat(5000)
        assertTrue(PairingJson.parsePhoneFrame("""{"v":1,"type":"ping","x":$deep}""") is ParsedFrame.Malformed)
        // Brackets inside strings do not count as nesting.
        val ok = PairingJson.parsePhoneFrame("""{"v":1,"type":"ping","x":"[[[[[[[[[[[[[[[[[[[[[["}""")
        assertTrue(ok is ParsedFrame.Frame)
    }
}
