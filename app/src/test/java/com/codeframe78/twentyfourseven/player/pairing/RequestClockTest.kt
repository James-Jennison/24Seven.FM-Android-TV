package com.codeframe78.twentyfourseven.player.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RequestClockTest {
    private fun page(seconds: String, queue: String, word: String) = """
        <html><body><a href="/modules.php?name=Your_Account&op=logout">Logout</a>
        <div class="vip-request-clock" data-seconds="$seconds" data-queue-seconds="$queue" data-queue-status="Queued">
          <div>Next Request: <strong class="vip-request-value request-ready">$word</strong></div>
        </div></body></html>"""

    @Test fun aWaitingClockCarriesTheSeconds() {
        assertEquals(RequestClock(754, 1054), RequestClockParser.parse(page("754", "1054", "12:34")))
    }

    @Test fun aReadyClockIsZero() {
        assertEquals(RequestClock(0, null), RequestClockParser.parse(page("0", "", "Ready")))
    }

    @Test fun anUnexplainedZeroIsUnknownNotReady() {
        assertEquals(RequestClock(null, null), RequestClockParser.parse(page("0", "", "—")))
    }

    @Test fun outOfRangeSecondsAreUnknown() {
        assertEquals(RequestClock(null, null), RequestClockParser.parse(page("99999999", "-4", "x")))
    }

    @Test fun aSignInPageOrNoClockIsNull() {
        assertNull(RequestClockParser.parse("<html><input name=\"user_password\"></html>"))
        assertNull(RequestClockParser.parse("<html><body>hello</body></html>"))
    }

    @Test fun theOlderTextLayoutStillReads() {
        assertEquals(RequestClock(25 * 60, null), RequestClockParser.parse("<html><body>Request Wait: 25 Minutes</body></html>"))
        assertEquals(RequestClock(0, null), RequestClockParser.parse("<html><body>Your Request: Ready</body></html>"))
    }

    @Test fun formatsMinutesAndHours() {
        assertEquals("12:34", formatWait(754))
        assertEquals("1:02:03", formatWait(3723))
    }
}
