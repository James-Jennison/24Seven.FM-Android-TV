package com.codeframe78.twentyfourseven.player.analysis

import java.util.ArrayDeque

/**
 * Bounded, in-memory handoff only. No executor or callback runs under this monitor. The
 * producer copies at most [maxBlockFrames] frames per offer; the consumer owns each polled
 * array exclusively and must not republish it. At most [capacity] blocks are retained here.
 * These capacity defaults are prototype budgets, not measured device performance claims.
 *
 * On overload, discard queued PCM and the incoming block, and begin a new analysis epoch.
 * Never splice audio across a gap. An already polled old-epoch result must be rejected by
 * the publisher against [epoch]. Flush/format changes use [configure], including same-format
 * station changes. Frame indices are relative to an analysis epoch, never speaker time.
 */
internal class BoundedPcmCapture(
    private val capacity: Int = 4,
    private val maxBlockFrames: Int = 1024,
) {
    init {
        require(capacity in 1..16)
        require(maxBlockFrames in 1..4096)
    }

    internal data class Block(
        val epoch: Long,
        val sampleRateHz: Int,
        val channelCount: Int,
        val startFrame: Long,
        val capturedAtElapsedRealtimeMs: Long,
        val samples: FloatArray,
    )

    private val blocks = ArrayDeque<Block>(capacity)
    private var generation = 0L
    private var rate = 0
    private var channels = 0
    private var nextFrame = 0L

    val epoch: Long @Synchronized get() = generation
    val hasPending: Boolean @Synchronized get() = blocks.isNotEmpty()

    @Synchronized
    fun configure(sampleRateHz: Int, channelCount: Int): Long {
        require(sampleRateHz in 100..384000)
        require(channelCount in 1..32)
        advanceEpoch()
        rate = sampleRateHz
        channels = channelCount
        return generation
    }

    /** Stops capture and invalidates in-flight analysis without retaining an old format. */
    @Synchronized
    fun clear() {
        advanceEpoch()
        rate = 0
        channels = 0
    }

    /**
     * False means disabled or overloaded. Invalid ranges fail before any mutation.
     * The caller must split larger inputs into bounded whole-frame offers. Clock is the
     * capture callback clock only; the worker must preserve it rather than restamp old PCM.
     */
    @Synchronized
    fun offer(
        samples: FloatArray,
        capturedAtElapsedRealtimeMs: Long,
        offset: Int = 0,
        length: Int = samples.size - offset,
    ): Boolean {
        require(offset >= 0 && length >= 0 && offset <= samples.size - length)
        require(capturedAtElapsedRealtimeMs >= 0)
        if (channels == 0) return false
        require(length % channels == 0)
        val frames = length / channels
        require(frames <= maxBlockFrames)
        if (frames == 0) return true
        if (blocks.size == capacity || nextFrame > Long.MAX_VALUE - frames) {
            advanceEpoch()
            return false
        }
        blocks.addLast(Block(generation, rate, channels, nextFrame,
            capturedAtElapsedRealtimeMs, samples.copyOfRange(offset, offset + length)))
        nextFrame += frames
        return true
    }

    @Synchronized
    fun poll(): Block? = blocks.pollFirst()

    private fun advanceEpoch() {
        check(generation < Long.MAX_VALUE) { "Analysis epoch exhausted" }
        generation++
        blocks.clear()
        nextFrame = 0L
    }
}
