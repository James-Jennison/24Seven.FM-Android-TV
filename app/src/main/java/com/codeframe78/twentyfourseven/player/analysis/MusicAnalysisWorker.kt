package com.codeframe78.twentyfourseven.player.analysis

import com.codeframe78.twentyfourseven.player.domain.MusicReactiveState
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** One serial DSP owner, bounded capture and at most one scheduled drain. Never owns playback. */
internal class MusicAnalysisWorker : AutoCloseable {
    private val capture = BoundedPcmCapture()
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "TV music analysis").apply { isDaemon = true }
    }
    private val draining = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val latest = AtomicReference<MusicReactiveState?>(null)
    // Accessed only by executor thread.
    private var analyzer: SharedMusicAnalyzer? = null
    private var analyzerEpoch = -1L

    fun configure(rate: Int, channels: Int) { capture.configure(rate, channels); latest.set(null) }
    fun clear() { capture.clear(); latest.set(null) }

    fun offer(samples: FloatArray, length: Int, capturedAtMs: Long) {
        if (closed.get()) return
        capture.offer(samples, capturedAtMs, length = length)
        schedule()
    }

    /** Reject an old result even if it completed after a flush/overflow. No PCM escapes. */
    fun latest(): MusicReactiveState? = latest.get()?.takeIf { it.epoch == capture.epoch && !closed.get() }

    private fun schedule() {
        if (closed.get() || !draining.compareAndSet(false, true)) return
        try {
            executor.execute {
                try {
                    while (!closed.get()) {
                        val block = capture.poll() ?: break
                        if (block.epoch != capture.epoch) continue
                        if (block.epoch != analyzerEpoch) {
                            analyzer = SharedMusicAnalyzer(block.epoch, block.sampleRateHz, block.channelCount)
                            analyzerEpoch = block.epoch
                        }
                        analyzer!!.accept(block.samples) { frame ->
                            if (!closed.get() && frame.epoch == capture.epoch) {
                                latest.set(frame.copy(measuredAtElapsedRealtimeMs = block.capturedAtElapsedRealtimeMs))
                            }
                        }
                    }
                } finally {
                    draining.set(false)
                    // Close the offer-vs-empty-drain race without a task per buffer.
                    if (capture.hasPending && !closed.get()) schedule()
                }
            }
        } catch (_: RejectedExecutionException) {
            draining.set(false) // Concurrent close; no playback work is affected.
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            clear()
            executor.shutdownNow()
        }
    }
}
