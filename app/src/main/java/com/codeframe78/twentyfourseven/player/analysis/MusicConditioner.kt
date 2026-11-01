package com.codeframe78.twentyfourseven.player.analysis

import com.codeframe78.twentyfourseven.player.domain.MusicReactiveState
import com.codeframe78.twentyfourseven.player.domain.ReactiveStatus
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max

/** Single analyzer-thread owner. All time constants are seconds, independent of render cadence. */
internal class MusicConditioner {
    private var amplitude = 0f
    private var bass = 0f
    private var mid = 0f
    private var treble = 0f
    private var energy = 0f
    private var previous = 0f
    private var onset = 0f
    private var onsetId = 0L
    private var onsetSample = 0L
    private var lastSample = 0L
    private var initialized = false
    private var currentEpoch: Long? = null
    private var sampleRate = 0
    private var sequence = 0L
    private var fluxArmed = false

    fun reset() {
        amplitude = 0f; bass = 0f; mid = 0f; treble = 0f; energy = 0f
        previous = 0f; onset = 0f; onsetId = 0; onsetSample = 0; lastSample = 0; initialized = false
        currentEpoch = null; sampleRate = 0; sequence = 0; fluxArmed = false
    }

    fun update(epoch: Long, sample: Long, rate: Int, rms: Float, peak: Float,
               bassLevel: Float, midLevel: Float, trebleLevel: Float, flux: Float): MusicReactiveState {
        require(rate > 0 && sample > 0)
        require(currentEpoch == null || epoch >= currentEpoch!!)
        if (currentEpoch != epoch) {
            reset()
            currentEpoch = epoch
            sampleRate = rate
        }
        // A format change starts a new epoch; duplicates/out-of-order frames are adapter errors.
        require(rate == sampleRate && sample > lastSample)
        val dt = (sample - lastSample).toFloat() / rate
        fun unit(v: Float) = if (v.isFinite()) v.coerceIn(0f, 1f) else 0f
        val raw = if (rms.isFinite() && rms > .001f) ((20f * log10(rms) + 60f) / 60f).coerceIn(0f, 1f) else 0f
        fun follow(old: Float, target: Float, attack: Float, release: Float) =
            old + (target - old) * (1f - exp(-dt / if (target > old) attack else release))
        amplitude = follow(amplitude, raw, .008f, .12f)
        bass = follow(bass, unit(bassLevel), .025f, .20f)
        mid = follow(mid, unit(midLevel), .035f, .16f)
        treble = follow(treble, unit(trebleLevel), .015f, .10f)
        energy = follow(energy, raw, .25f, .70f)
        onset *= exp(-dt / .10f)
        // Startup establishes a baseline. Events are generic onsets, never identified instruments/beats.
        val normalizedFlux = unit(flux)
        val fluxAttack = initialized && fluxArmed && normalizedFlux > .13f
        if (normalizedFlux <= .08f) fluxArmed = true
        else if (normalizedFlux > .13f) fluxArmed = false
        val strength = max((raw - previous - .06f) * 3f,
            if (fluxAttack) (normalizedFlux - .08f) * 2f else 0f).coerceIn(0f, 1f)
        if (initialized && raw > .10f && strength > .10f &&
            (onsetId == 0L || sample - onsetSample >= (rate * .10f).toLong())) {
            onset = strength; onsetId++; onsetSample = sample
        }
        previous = raw; lastSample = sample; initialized = true
        sequence++
        return MusicReactiveState(epoch = epoch, sequence = sequence, sampleRateHz = rate,
            windowEndSample = sample, status = if (raw == 0f) ReactiveStatus.Silence else ReactiveStatus.Active,
            amplitude = amplitude, peak = unit(peak), bass = bass, mid = mid, treble = treble,
            energy = energy, onset = onset, onsetId = onsetId, onsetSample = onsetSample)
    }
}
