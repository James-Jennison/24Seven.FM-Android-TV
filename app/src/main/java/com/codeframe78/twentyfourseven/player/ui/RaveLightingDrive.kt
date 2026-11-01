package com.codeframe78.twentyfourseven.player.ui

import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState
import kotlin.math.*

internal enum class RaveSection { QUIET, GROOVE, BUILD, DROP }

internal data class RaveLightingFrame(
    val energy: Float = 0f, val bass: Float = 0f, val mids: Float = 0f, val highs: Float = 0f,
    val kick: Float = 0f, val accent: Float = 0f, val build: Float = 0f, val drop: Float = 0f,
    val white: Float = 0f, val motion: Float = 0f, val beatPhase: Float = 0f,
    val bpm: Float = 0f, val confidence: Float = 0f, val cue: Int = 0,
    val section: RaveSection = RaveSection.QUIET, val reduced: Boolean = false, val palette: Int = 0,
)

/** Presentation-only music inference. No PCM ownership, recording or playback control.
 * New measurements drive onset/tempo/section decisions; render ticks only interpolate cues.
 */
internal class RaveLightingDrive {
    private var lastSample = 0L
    private var lastBeat = 0L
    private var lastWhite = 0L
    private var lastDrop = 0L
    private var period = 500f
    private var confidence = 0f
    private var beatCount = 0
    private var colourCue = 0
    private var fast = 0f
    private var slow = 0f
    private var previousBass = 0f
    private var previousTransient = 0f
    private var fluxMean = .04f
    private var bass = 0f
    private var mids = 0f
    private var highs = 0f
    private var kick = 0f
    private var accent = 0f
    private var white = 0f
    private var drop = 0f
    private var build = 0f
    private var motion = 0f
    private var phase = 0f
    private var quietSeconds = 0f
    private var buildArmed = false
    private var section = RaveSection.QUIET

    fun reset() {
        lastSample = 0L; lastBeat = 0L; lastWhite = 0L; lastDrop = 0L
        period = 500f; confidence = 0f; beatCount = 0; colourCue = 0; fast = 0f; slow = 0f
        previousBass = 0f; previousTransient = 0f; fluxMean = .04f
        bass = 0f; mids = 0f; highs = 0f; kick = 0f; accent = 0f; white = 0f
        drop = 0f; build = 0f; motion = 0f; phase = 0f; quietSeconds = 0f
        buildArmed = false; section = RaveSection.QUIET
    }

    fun update(audio: AudioVisualizationState, nowMs: Long, seconds: Float, reduced: Boolean): RaveLightingFrame {
        val dt = if (seconds.isFinite()) seconds.coerceIn(0f, .1f) else 0f
        fun unit(v: Float) = if (v.isFinite()) v.coerceIn(0f, 1f) else 0f
        val fresh = audio.sampledAtElapsedRealtimeMs > 0 && nowMs >= audio.sampledAtElapsedRealtimeMs &&
            nowMs - audio.sampledAtElapsedRealtimeMs <= 500 && audio.channelCount > 0 && unit(audio.energy) > .001f
        if (!fresh) {
            // Missing/stale PCM is darkness, not a free-running light show.
            reset()
            return RaveLightingFrame(reduced = reduced)
        }
        kick *= exp(-dt / .16f); accent *= exp(-dt / .09f)
        white *= exp(-dt / .055f); drop *= exp(-dt / 1.9f)
        val stamp = audio.sampledAtElapsedRealtimeMs
        if (stamp != lastSample) {
            val elapsed = if (lastSample == 0L) .033f else ((stamp - lastSample) / 1000f).coerceIn(.001f, .5f)
            val entry = lastSample == 0L || stamp < lastSample || stamp - lastSample > 500
            if (entry) { lastBeat = 0; confidence = 0f; buildArmed = false }
            fun band(i: Int): Float {
                val value = audio.calibratedBandDbfs.getOrNull(i) ?: -60f
                return if (value.isFinite()) ((value + 60f) / 54f).coerceIn(0f, 1f) else 0f
            }
            val b = band(0) * .35f + band(1) * .65f
            val m = band(2); val h = band(3)
            // Calibrated amplitude establishes intensity; spectral distribution shapes motion.
            var upper = 0f
            for (i in 20 until 32) upper += unit(audio.bands.getOrElse(i) { 0f }) / 12f
            val level = b * .5f + m * .3f + h * .2f
            if (entry) { fast = level; slow = level; kick = 0f; accent = 0f; drop = 0f; white = 0f; build = 0f }
            val oldFast = fast
            fun follow(old: Float, target: Float, tau: Float) = old + (target - old) * (1f - exp(-elapsed / tau))
            fast = follow(fast, level, .18f); slow = follow(slow, level, 5f)
            bass = follow(bass, b, .07f); mids = follow(mids, m, .18f); highs = follow(highs, max(h, upper), .055f)
            val flux = unit(audio.transient)
            val bassRise = b - previousBass
            val onset = !entry && (bassRise > .075f || flux - previousTransient > .035f && flux > max(.07f, fluxMean * 1.35f))
            val kickEvent = onset && b > .3f && (lastBeat == 0L || stamp - lastBeat >= 260)
            quietSeconds = if (fast < .28f) quietSeconds + elapsed else max(0f, quietSeconds - elapsed * .3f)
            build = follow(build, ((fast - slow - .035f) * 3.5f).coerceIn(0f, 1f), .7f)
            if (build > .28f && fast > .35f) buildArmed = true
            if (kickEvent) {
                val interval = (stamp - lastBeat).toFloat()
                if (lastBeat != 0L && interval in 300f..1500f) {
                    var folded = interval
                    while (folded > 900f) folded *= .5f
                    val agreement = abs(folded - period) / period
                    period = if (confidence < .3f) folded else period * .8f + folded * .2f
                    confidence = (confidence + if (agreement < .18f) .18f else -.12f).coerceIn(0f, 1f)
                }
                lastBeat = stamp; phase = 0f
                beatCount = if (beatCount == 1_000_000) 0 else beatCount + 1
                // Mid-band colour decisions land on musical cue boundaries, avoiding
                // palette flicker from individual spectrum-bin fluctuations.
                if (beatCount % 4 == 0) colourCue = (beatCount / 8 + (m * 3.99f).toInt()) % 4
                kick = (.35f + b * .65f).coerceAtMost(1f)
                val release = (buildArmed || quietSeconds > 1f) && level > .64f && (level - oldFast > .10f || bassRise > .18f)
                if (release && stamp - lastDrop > 3000) {
                    drop = 1f; lastDrop = stamp; buildArmed = false; quietSeconds = 0f
                }
                // Bright spatial bursts on selected kicks; never a timer-only global strobe.
                if (!reduced && stamp - lastWhite >= 400 && (drop > .25f || fast > .75f && beatCount % 4 == 0)) {
                    white = max(drop, fast * .65f); lastWhite = stamp
                }
            }
            if (!entry && flux - previousTransient > .04f && h > .25f) accent = flux
            fluxMean = follow(fluxMean, flux, 1.2f)
            previousBass = b; previousTransient = flux; lastSample = stamp
        }
        if (lastBeat != 0L && nowMs - lastBeat > 1800) confidence *= exp(-dt / .8f)
        phase = (phase + dt * 1000f / period) % 1f
        motion = (motion + dt * (.06f + mids * .5f + build * .5f + drop * 1.1f) * if (reduced) .35f else 1f) % 4096f
        section = when { drop > .25f -> RaveSection.DROP; build > .25f -> RaveSection.BUILD; fast > .28f -> RaveSection.GROOVE; else -> RaveSection.QUIET }
        val gain = if (reduced) .28f else 1f
        return RaveLightingFrame(fast * gain, bass * gain, mids * gain, highs * gain,
            kick * gain, accent * gain, build * gain, drop * gain, if (reduced) 0f else white,
            motion, phase, if (confidence >= .3f) 60000f / period else 0f, confidence,
            beatCount / 8, section, reduced, colourCue)
    }
}
