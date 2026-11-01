package com.codeframe78.twentyfourseven.player.analysis

import org.junit.Assert.*
import org.junit.Test

class MusicAnalysisWorkerTest {
    private fun await(worker: MusicAnalysisWorker, end: Long) {
        val deadline = System.nanoTime() + 2_000_000_000L
        while ((worker.latest()?.windowEndSample ?: 0L) < end && System.nanoTime() < deadline) Thread.sleep(2)
        assertTrue("Analyzer did not deliver bounded work", (worker.latest()?.windowEndSample ?: 0L) >= end)
    }

    @Test fun workerPreservesCaptureTimeAndRejectsOldEpochAfterClear() {
        val worker = MusicAnalysisWorker()
        try {
            worker.configure(48000, 2)
            repeat(5) { block ->
                worker.offer(FloatArray(960) { if (it % 2 == 0) .5f else -.5f }, 960, 123L)
                await(worker, (block + 1) * 480L)
            }
            val state = worker.latest()!!
            assertEquals(123L, state.measuredAtElapsedRealtimeMs)
            assertEquals(.5f, state.peak, .0001f)
            assertTrue(state.amplitude > .8f)
            worker.clear()
            assertNull(worker.latest())
            worker.configure(44100, 1)
            worker.offer(FloatArray(441), 441, 456L)
            await(worker, 441)
            assertTrue(worker.latest()!!.epoch > state.epoch)
            assertEquals(44100, worker.latest()!!.sampleRateHz)
        } finally { worker.close() }
        assertNull(worker.latest())
    }

    @Test fun closedWorkerDoesNotScheduleOrExposeFurtherAudio() {
        val worker = MusicAnalysisWorker()
        worker.configure(48000, 1)
        worker.offer(FloatArray(1024) { .5f }, 1024, 100)
        worker.close()
        worker.offer(FloatArray(1024), 1024, 101)
        assertNull(worker.latest())
    }
}
