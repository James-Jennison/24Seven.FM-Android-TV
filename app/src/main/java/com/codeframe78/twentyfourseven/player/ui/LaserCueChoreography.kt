package com.codeframe78.twentyfourseven.player.ui

import kotlin.math.exp
import kotlin.math.max

/** GL-thread owned. Cues advance on measured PCM spectral attacks, never on a timer. */
internal class LaserCueChoreography {
    private val positions = FloatArray(6) { (it - 2.5f) * .65f }
    private val destinations = positions.copyOf()
    private val spreads = FloatArray(6) { .35f }
    private val banks = FloatArray(6) { 1f }
    private var lastSample = Double.NaN
    private var lastHit = Double.NEGATIVE_INFINITY
    private var previousFlux = 0f
    private var averageFlux = 0f
    private var sequence = 0x13579bdf
    var hits = 0; private set
    var pulse = 0f; private set

    fun reset() {
        lastSample = Double.NaN; lastHit = Double.NEGATIVE_INFINITY
        previousFlux = 0f; averageFlux = 0f; hits = 0; pulse = 0f
        sequence = 0x13579bdf
        repeat(6) { positions[it] = (it - 2.5f) * .65f; destinations[it] = positions[it]; spreads[it] = .35f; banks[it] = 1f }
    }

    fun accept(seconds: Double, transient: Float, energy: Float) {
        if (!seconds.isFinite()) return
        val flux = if (transient.isFinite()) transient.coerceIn(0f, 1f) else 0f
        val level = if (energy.isFinite()) energy.coerceIn(0f, 1f) else 0f
        val dt = seconds - lastSample
        if (!lastSample.isFinite() || dt > .5) {
            lastSample = seconds; previousFlux = flux; averageFlux = flux
            return // Entry or resumed stale stream is not a beat.
        }
        if (dt <= 0) return
        lastSample = seconds
        val threshold = max(.025f, averageFlux * 1.25f)
        if (level > .01f && flux > threshold && flux - previousFlux > .006f && seconds - lastHit >= .32) {
            lastHit = seconds
            pulse = 1f
            hits = if (hits == Int.MAX_VALUE) 1 else hits + 1
            // Continuous parameters, not an index into a finite menu of poses.
            // Common aim/slope coordinate the rig; independent deviations prevent locked banks.
            val pan = randomUnit() * 2.8f - 1.4f
            val slope = randomUnit() * 1.5f - .75f
            val scatter = .3f + randomUnit() * (1f + flux * .5f)
            repeat(6) { i ->
                destinations[i] = (pan + (i - 2.5f) * slope + (randomUnit() * 2f - 1f) * scatter).coerceIn(-2.8f, 2.8f)
                spreads[i] = .18f + randomUnit() * .65f
                banks[i] = .4f + randomUnit() * .6f
            }
        }
        averageFlux += (flux - averageFlux) * (1f - exp((-dt / .8).toFloat()))
        previousFlux = flux
    }

    fun advance(seconds: Float) {
        if (!seconds.isFinite() || seconds <= 0f) return
        val dt = seconds.coerceAtMost(1f)
        val blend = 1f - exp(-dt / .085f)
        repeat(6) { positions[it] += (destinations[it] - positions[it]) * blend }
        pulse *= exp(-dt / .24f)
    }

    private fun randomUnit(): Float {
        sequence = sequence xor (sequence shl 13)
        sequence = sequence xor (sequence ushr 17)
        sequence = sequence xor (sequence shl 5)
        return (sequence ushr 8) / 16777215f
    }

    fun aim(fixture: Int): Float = positions[fixture % 6]
    fun spread(fixture: Int): Float = spreads[fixture % 6]
    fun activity(fixture: Int): Float = banks[fixture % 6]
    // Floor wash retains its previous intensity range; stronger accents are overhead only.
    fun floorActivity(bank: Int): Float = if ((bank + hits % 3) % 3 == 0) .35f else 1f
}
