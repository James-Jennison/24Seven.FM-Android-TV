package com.codeframe78.twentyfourseven.player.ui

import com.codeframe78.twentyfourseven.player.playback.CalibratedPcmMeter
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class LaserCueChoreographyTest {
    @Test fun hitsDoNotReplayEightCuePlaylistOrRepeatRecentPatterns() {
        val cues = LaserCueChoreography()
        val poses = mutableSetOf<List<Int>>()
        cues.accept(0.0, 0f, .5f)
        repeat(64) { i ->
            cues.accept(i * .5 + .1, 0f, .5f)
            cues.accept(i * .5 + .12, .3f, .5f)
            cues.advance(.5f)
            poses.add((0..5).map { (cues.aim(it) * 100).toInt() })
            repeat(6) { assertTrue(cues.aim(it) in -3f..3f) }
            repeat(6) { assertTrue(cues.spread(it) in .18f.. .84f); assertTrue(cues.activity(it) in .4f..1f) }
        }
        assertEquals(64, poses.size)
    }
    @Test fun cuesOnlyAdvanceOnAttacksAndHoldWithoutPeriodicSway() {
        val cues = LaserCueChoreography()
        cues.accept(0.0, 0f, .5f)
        repeat(5) { beat ->
            cues.accept(beat * .5 + .1, 0f, .5f)
            cues.accept(beat * .5 + .12, .3f, .5f)
            cues.advance(.1f)
            assertEquals(beat + 1, cues.hits)
            assertTrue(cues.pulse > .5f)
        }
        repeat(600) { i -> cues.accept(2.7 + i / 60.0, 0f, .5f); cues.advance(1f / 60f) }
        assertEquals(5, cues.hits)
        val held = cues.aim(0)
        repeat(600) { cues.advance(1f / 60f) }
        assertEquals(held, cues.aim(0), .000001f)
    }

    @Test fun entryStaleSamplesSilenceAndResetDoNotInventBeats() {
        val cues = LaserCueChoreography()
        cues.accept(0.0, 1f, 1f)
        cues.accept(.1, 0f, 0f); cues.accept(.12, .8f, 0f)
        cues.accept(2.0, 1f, 1f)
        cues.accept(Double.NaN, 1f, 1f)
        cues.accept(1.0, 1f, 1f)
        assertEquals(0, cues.hits)
        cues.reset()
        cues.accept(3.0, 1f, 1f)
        assertEquals(0f, cues.pulse, 0f)
    }

    @Test fun refractoryLimitsRapidAttacksAndSustainedFluxDoesNotRetrigger() {
        val cues = LaserCueChoreography()
        cues.accept(0.0, 0f, 1f)
        repeat(100) { i -> cues.accept(.02 + i * .02, if (i % 2 == 0) .5f else 0f, 1f) }
        assertTrue(cues.hits in 1..7)
        val before = cues.hits
        repeat(100) { i -> cues.accept(2.1 + i * .02, .5f, 1f) }
        assertTrue(cues.hits <= before + 1)
    }

    @Test fun decodedPcmPercussionAdvancesAtKnownHitsNotBetweenThem() {
        val meter = CalibratedPcmMeter(48000)
        val cues = LaserCueChoreography()
        val random = Random(73)
        val detected = mutableListOf<Double>()
        repeat(48000 * 6) { frame ->
            val time = frame / 48000.0
            val phase = time % .5
            val envelope = if (time >= .5 && phase < .12) exp(-phase * 38).toFloat() else 0f
            val sample = envelope * (.45f * sin(time * 2 * Math.PI * 65).toFloat() + .3f * (random.nextFloat() * 2 - 1))
            meter.accept(sample, sample, sample)
            if (frame % 768 == 0) {
                val before = cues.hits
                cues.accept(time, meter.reading.transient, meter.reading.energy)
                cues.advance(.016f)
                if (cues.hits > before) detected.add(time)
            }
        }
        assertTrue("detections=$detected", detected.size in 10..11)
        detected.forEach { assertTrue("late/off-beat=$it", it % .5 < .1) }
    }
}
