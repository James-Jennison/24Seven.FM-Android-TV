package com.codeframe78.twentyfourseven.player.analysis

import com.codeframe78.twentyfourseven.player.domain.MusicReactiveState
import com.codeframe78.twentyfourseven.player.domain.ReactiveStatus
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

class SharedMusicAnalyzerTest {
    @Test fun chunkingCannotLeakFutureSpectrumIntoEarlierAmplitude() {
        val pcm = FloatArray(16000) { i ->
            val amplitude = if (i < 6000) .02 else .8
            (amplitude * sin(i / 2 * 2.0 * Math.PI * 440 / 48000)).toFloat()
        }
        fun analyze(chunkFrames: Int): List<MusicReactiveState> {
            val analyzer = SharedMusicAnalyzer(7, 48000, 2)
            val result = mutableListOf<MusicReactiveState>()
            var offset = 0
            while (offset < pcm.size) {
                val count = minOf(chunkFrames * 2, pcm.size - offset)
                analyzer.accept(pcm, offset, count, result::add)
                offset += count
            }
            return result
        }
        val whole = analyze(8000)
        assertEquals(whole, analyze(1))
        assertEquals(whole, analyze(137))
        assertTrue(whole.all { (it.spectralWindowEndSample ?: 0) <= it.windowEndSample })
        assertTrue(whole.take(4).all { it.status == ReactiveStatus.WarmingUp })
        assertEquals(0L, whole[4].onsetId)
        assertEquals((1L..whole.size.toLong()).toList(), whole.map { it.sequence })
    }

    @Test fun newEpochHasNoPriorEventsOrSpectrumAndSnapshotsDoNotMutate() {
        val analyzer = SharedMusicAnalyzer(1, 48000, 1)
        val states = mutableListOf<MusicReactiveState>()
        analyzer.accept(FloatArray(2400) { .5f }, emit = states::add)
        val snapshot = states.first().copy()
        analyzer.accept(FloatArray(4800), emit = states::add)
        assertEquals(snapshot, states.first())
        val fresh = mutableListOf<MusicReactiveState>()
        SharedMusicAnalyzer(2, 44100, 2).accept(FloatArray(882), emit = fresh::add)
        assertEquals(2L, fresh.single().epoch)
        assertEquals(441L, fresh.single().windowEndSample)
        assertNull(fresh.single().spectralWindowEndSample)
        assertEquals(0L, fresh.single().onsetId)
    }

    @Test fun invalidRangeDoesNotAdvanceEitherAnalyzer() {
        val analyzer = SharedMusicAnalyzer(0, 48000, 2)
        try {
            analyzer.accept(FloatArray(3)) { fail("Invalid input emitted state") }
            fail("Partial frame accepted")
        } catch (_: IllegalArgumentException) { }
        val states = mutableListOf<MusicReactiveState>()
        analyzer.accept(FloatArray(960), emit = states::add)
        assertEquals(480L, states.single().windowEndSample)
    }
}
