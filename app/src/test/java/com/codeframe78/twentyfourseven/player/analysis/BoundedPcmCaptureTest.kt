package com.codeframe78.twentyfourseven.player.analysis

import org.junit.Assert.*
import org.junit.Test

class BoundedPcmCaptureTest {
    @Test fun ownsItsCopyAndPreservesFrameAndCaptureClocks() {
        val queue = BoundedPcmCapture(maxBlockFrames = 4)
        val epoch = queue.configure(48000, 2)
        val input = floatArrayOf(9f, 1f, -1f, .5f, -.5f, 9f)
        assertTrue(queue.offer(input, 123, 1, 4))
        input.fill(0f)
        assertTrue(queue.offer(FloatArray(2), 124))
        val first = queue.poll()!!
        assertEquals(epoch, first.epoch)
        assertEquals(0L, first.startFrame)
        assertEquals(123L, first.capturedAtElapsedRealtimeMs)
        assertArrayEquals(floatArrayOf(1f, -1f, .5f, -.5f), first.samples, 0f)
        assertEquals(2L, queue.poll()!!.startFrame)
        assertNull(queue.poll())
    }

    @Test fun overloadInvalidatesQueuedAndInFlightWorkWithoutJoiningAcrossGap() {
        val queue = BoundedPcmCapture(capacity = 1)
        queue.configure(48000, 1)
        queue.offer(FloatArray(10), 1)
        val inFlight = queue.poll()!!
        queue.offer(FloatArray(10), 2)
        assertFalse(queue.offer(FloatArray(10), 3))
        assertTrue(inFlight.epoch != queue.epoch)
        assertNull(queue.poll())
        assertTrue(queue.offer(FloatArray(10), 4))
        val afterGap = queue.poll()!!
        assertEquals(queue.epoch, afterGap.epoch)
        assertEquals(0L, afterGap.startFrame)
        assertEquals(4L, afterGap.capturedAtElapsedRealtimeMs)
    }

    @Test fun configureAndClearInvalidateEvenSameFormatAndDisableCapture() {
        val queue = BoundedPcmCapture()
        assertFalse(queue.offer(floatArrayOf(1f), 0))
        val epoch = queue.configure(48000, 1)
        queue.offer(floatArrayOf(1f), 1)
        assertTrue(queue.configure(48000, 1) > epoch)
        assertNull(queue.poll())
        queue.configure(44100, 2)
        queue.offer(FloatArray(2), 2)
        val block = queue.poll()!!
        assertEquals(44100, block.sampleRateHz)
        assertEquals(2, block.channelCount)
        assertEquals(0L, block.startFrame)
        queue.clear()
        assertTrue(queue.epoch > block.epoch)
        assertFalse(queue.offer(FloatArray(2), 3))
        assertNull(queue.poll())
    }

    @Test fun invalidInputAndEmptyOffersDoNotDropQueuedWorkOrAdvanceClock() {
        val queue = BoundedPcmCapture(capacity = 1, maxBlockFrames = 2)
        val epoch = queue.configure(48000, 2)
        queue.offer(FloatArray(2), 1)
        for (invalid in listOf(FloatArray(3), FloatArray(6))) {
            try {
                queue.offer(invalid, 2)
                fail("Accepted malformed or oversized input")
            } catch (_: IllegalArgumentException) { }
        }
        assertTrue(queue.offer(FloatArray(0), 3))
        assertEquals(epoch, queue.epoch)
        assertEquals(0L, queue.poll()!!.startFrame)
        queue.offer(FloatArray(2), 4)
        assertEquals(1L, queue.poll()!!.startFrame)
    }
}
