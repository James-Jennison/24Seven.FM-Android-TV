package com.codeframe78.twentyfourseven.player.ui

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/** Render-thread owned; spectral onset accents, not an instrument classifier or BPM tracker. */
internal class LaserChoreography {
    var clock = 0f; private set
    var bassHit = 0f; private set
    var snareHit = 0f; private set
    private var previousBass = 0f
    private var previousSnare = 0f
    private var level = 0f
    private var initialized = false

    fun reset() {
        clock = 0f; bassHit = 0f; snareHit = 0f
        previousBass = 0f; previousSnare = 0f; level = 0f; initialized = false
    }

    fun update(seconds: Float, bass: Float, snare: Float, energy: Float) {
        if (!seconds.isFinite() || seconds <= 0f) return
        val dt = seconds.coerceAtMost(.05f)
        fun unit(v: Float) = if (v.isFinite()) v.coerceIn(0f, 1f) else 0f
        val b = unit(bass); val s = unit(snare); val e = unit(energy)
        if (!initialized) {
            previousBass = b; previousSnare = s; initialized = true
        }
        // Positive spectral slopes create short accents; sustained loud audio does not hold them.
        bassHit = max(bassHit * exp(-dt / .24f), ((b - previousBass) / dt * .12f).coerceIn(0f, 1f))
        snareHit = max(snareHit * exp(-dt / .11f), ((s - previousSnare) / dt * .10f).coerceIn(0f, 1f))
        previousBass = b; previousSnare = s
        level += (e - level) * (1f - exp(-dt / .65f))
        clock += dt * level * .38f
    }
}

/** Two overhead banks and counter-sweeping floor fixtures; integer harmonics also loop in Canvas. */
internal fun laserGroupCentre(time: Float, fixture: Int): Float {
    val opposing = (sin(time) + 1f) * .5f
    val right = fixture in 3..5 || fixture == 7
    val direction = if (right) 1f - 2f * opposing else 1f
    val floorDirection = if (fixture >= 6) -1f else 1f
    val offset = if (fixture < 6) (fixture % 3 - 1) * .16f else 0f
    return sin(time * 4f) * 2.8f * direction * floorDirection + offset
}
