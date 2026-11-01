package com.codeframe78.twentyfourseven.player.analysis

import com.codeframe78.twentyfourseven.player.domain.MusicReactiveState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicConditionerTest {
    private fun MusicConditioner.frame(sample: Long, flux: Float = 0f, epoch: Long = 1,
                                      rate: Int = 48000, rms: Float = .2f): MusicReactiveState =
        update(epoch, sample, rate, rms, .5f, .4f, .3f, .2f, flux)

    @Test fun sustainedFluxProducesOneEventUntilRearmed() {
        val conditioner = MusicConditioner()
        conditioner.frame(480)
        assertEquals(1L, conditioner.frame(960, flux = .8f).onsetId)
        for (i in 3..100) assertEquals(1L, conditioner.frame(i * 480L, flux = .8f).onsetId)
        conditioner.frame(48480, flux = 0f)
        assertEquals(2L, conditioner.frame(48960, flux = .8f).onsetId)
    }

    @Test fun highFluxAtStartupDoesNotInventRepeatedOnsets() {
        val conditioner = MusicConditioner()
        for (i in 1..100) assertEquals(0L, conditioner.frame(i * 480L, flux = .8f).onsetId)
    }

    @Test fun epochChangeResetsSampleClockEnvelopesAndEvents() {
        val conditioner = MusicConditioner()
        conditioner.frame(480)
        conditioner.frame(960, flux = .8f)
        val actual = conditioner.frame(441, flux = .8f, epoch = 2, rate = 44100)
        val fresh = MusicConditioner().frame(441, flux = .8f, epoch = 2, rate = 44100)
        assertEquals(fresh, actual)
        assertEquals(1L, actual.sequence)
    }

    @Test fun invalidOrderingAndFormatRequireAnExplicitNewEpoch() {
        val conditioner = MusicConditioner()
        conditioner.frame(480)
        val invalid = listOf<() -> Unit>(
            { conditioner.frame(480) },
            { conditioner.frame(479) },
            { conditioner.frame(960, rate = 44100) },
            { conditioner.frame(960, epoch = 0) },
        )
        invalid.forEach { action ->
            var rejected = false
            try { action() } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        }
        assertEquals(2L, conditioner.frame(960).sequence)
    }

    @Test fun nonfiniteLevelsRemainBounded() {
        val state = MusicConditioner().update(1, 480, 48000, Float.NaN,
            Float.POSITIVE_INFINITY, Float.NaN, -1f, 2f, Float.NaN)
        listOf(state.amplitude, state.peak, state.bass, state.mid, state.treble,
            state.energy, state.onset).forEach { assertTrue(it.isFinite() && it in 0f..1f) }
        assertEquals(0L, state.onsetId)
    }
}
