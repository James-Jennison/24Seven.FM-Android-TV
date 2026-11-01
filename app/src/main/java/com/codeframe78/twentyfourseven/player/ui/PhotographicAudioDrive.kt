package com.codeframe78.twentyfourseven.player.ui

import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerStyle
import kotlin.math.exp
import kotlin.math.max

internal fun IdleVisualizerStyle.isPhotographicNative() = this == IdleVisualizerStyle.LanternDrift ||
    this == IdleVisualizerStyle.VinylPulse || this == IdleVisualizerStyle.PulseQuilt

internal data class PhotographicFrame(
    val leftDbfs: Float = -60f,
    val rightDbfs: Float = -60f,
    // Decaying maxima of measured RMS dBFS, not sample-peak or true-peak readings.
    val leftPeak: Float = -60f,
    val rightPeak: Float = -60f,
    val bands: List<Float> = List(32) { 0f },
    val correlation: Float = 0f,
    val channels: Int = 0,
    val light: List<Float> = List(4) { 0f },
    val phase: Float = 0f,
    val onset: Float = 0f,
    val reduced: Boolean = false,
    val cue: Int = 0,
    val aim: List<Float> = List(4) { 0f },
    val fan: List<Float> = List(4) { .5f },
    val accent: Float = 0f,
    val laserLight: List<Float> = light,
    val laserTravel: Float = phase,
    val laserBass: Float = 0f,
    val laserEnergy: Float = 0f,
    val laserPan: Float = 0f,
    val lanternBass: Float = 0f,
    val lanternPulse: Float = 0f,
    val lanternWaterBass: Float = 0f,
)

/** Presentation state only. No audio ownership, normalization, background clock or synthetic beats. */
internal class PhotographicAudioDrive {
    private var frame = PhotographicFrame()
    private val laserCues = LaserCueChoreography()
    private var lastLaserSample = 0L
    private var previousBass = 0f
    private var previousLevel = 0f
    private var lanternFloor = 0f
    fun reset() {
        frame = PhotographicFrame(); laserCues.reset()
        lastLaserSample = 0L; previousBass = 0f; previousLevel = 0f; lanternFloor = 0f
    }

    fun update(audio: AudioVisualizationState, nowMs: Long, seconds: Float, reduced: Boolean): PhotographicFrame {
        val fresh = audio.freshForVideo(nowMs)
        val dt = if (seconds.isFinite()) seconds.coerceIn(0f, .1f) else 0f
        fun unit(v: Float) = if (v.isFinite()) v.coerceIn(0f, 1f) else 0f
        fun db(v: Float) = if (v.isFinite()) v.coerceIn(-60f, 0f) else -60f
        val available = fresh.channelCount > 0
        val present = available && unit(fresh.energy) > .001f
        val left = if (available) db(fresh.leftDbfs) else -60f
        val right = if (available) db(fresh.rightDbfs) else -60f
        fun ease(old: Float, target: Float): Float {
            val value = old + (target - old) * (1f - exp(-dt / .3f))
            return if (target == 0f && value < .0001f) 0f else value
        }
        val light = List(4) { i ->
            val band = fresh.calibratedBandDbfs.getOrNull(i) ?: -60f
            ease(frame.light[i], if (present) (db(band) + 60f) / 60f else 0f)
        }
        // Freeze spatial phase immediately on absent/stale PCM; emitted light eases away.
        val phase = if (present) (frame.phase + dt * (.12f + light.average().toFloat() * .65f)) % 6.2831855f else frame.phase
        if (present) {
            laserCues.accept(fresh.sampledAtElapsedRealtimeMs / 1000.0, unit(fresh.transient), unit(fresh.energy))
        }
        // Lasers have a fast attack/release envelope; the lanterns keep their gentle .3s glow.
        // Absolute dBFS mapping preserves quiet/loud contrast instead of compressing it away.
        fun laserLevel(value: Float) = ((db(value) + 48f) / 42f).coerceIn(0f, 1f).let { it * it }
        fun quick(old: Float, target: Float): Float {
            val value = old + (target - old) * (1f - exp(-dt / if (target > old) .022f else .10f))
            return if (target == 0f && value < .0001f) 0f else value
        }
        val rawBass = if (present) max(laserLevel(fresh.calibratedBandDbfs.getOrNull(0) ?: -60f),
            laserLevel(fresh.calibratedBandDbfs.getOrNull(1) ?: -60f)) else 0f
        val rawLevel = if (present) laserLevel(max(left, right)) else 0f
        val laserLight = List(4) { i -> quick(frame.laserLight[i],
            if (present) laserLevel(fresh.calibratedBandDbfs.getOrNull(i) ?: -60f) else 0f) }
        var travel = frame.laserTravel
        if (present && fresh.sampledAtElapsedRealtimeMs > lastLaserSample) {
            if (lastLaserSample > 0 && fresh.sampledAtElapsedRealtimeMs - lastLaserSample <= 500L) {
                // Motion integrates measured rises and actual attacks, not elapsed wall time.
                // A steady tone therefore settles instead of running a repeating light routine.
                val impulse = max(0f, rawBass - previousBass) * 2.4f +
                    max(0f, rawLevel - previousLevel) * 1.2f +
                    if (laserCues.hits != frame.cue) .55f else 0f
                travel = (travel + impulse * if (reduced) .25f else 1f) % 6.2831855f
            }
            lastLaserSample = fresh.sampledAtElapsedRealtimeMs
            previousBass = rawBass; previousLevel = rawLevel
        } else if (!present) lastLaserSample = 0L
        // Age the stored attack pulse even while paused; re-entry must not revive an old accent.
        // Visible aim and phase below remain frozen when PCM is absent.
        laserCues.advance(dt)
        val bassGlow = frame.lanternBass + (rawBass - frame.lanternBass) *
            (1f - exp(-dt / if (rawBass > frame.lanternBass) .04f else .28f))
        // Bass contrast against a slow floor makes individual kicks visible on mastered radio.
        // No periodic pulse: steady bass settles, and absent PCM decays to darkness.
        val bassAttack = if (present) ((rawBass - lanternFloor) * 5f).coerceIn(0f, 1f) else 0f
        lanternFloor += (rawBass - lanternFloor) * (1f - exp(-dt / .65f))
        val pulse = max(bassAttack, frame.lanternPulse * exp(-dt / .12f))
        val waterBass = frame.lanternWaterBass + (rawBass - frame.lanternWaterBass) * (1f - exp(-dt / 1.2f))
        frame = PhotographicFrame(
            leftDbfs = left, rightDbfs = right,
            leftPeak = max(left, frame.leftPeak - dt * 12f).coerceAtLeast(-60f),
            rightPeak = max(right, frame.rightPeak - dt * 12f).coerceAtLeast(-60f),
            bands = List(32) { if (present) unit(fresh.bands.getOrNull(it) ?: 0f) else 0f },
            correlation = if (present && fresh.stereoCorrelation.isFinite()) fresh.stereoCorrelation.coerceIn(-1f, 1f) else 0f,
            channels = if (available) fresh.channelCount.coerceIn(1, 2) else 0,
            light = light, phase = phase, onset = ease(frame.onset, if (present) unit(fresh.transient) else 0f),
            reduced = reduced,
            cue = laserCues.hits,
            aim = if (present) List(4) { laserCues.aim(it) / 2.8f } else frame.aim,
            fan = if (present) List(4) { laserCues.spread(it) } else frame.fan,
            accent = if (present) laserCues.pulse else quick(frame.accent, 0f),
            laserLight = laserLight, laserTravel = travel,
            laserBass = quick(frame.laserBass, rawBass),
            laserEnergy = quick(frame.laserEnergy, rawLevel),
            laserPan = if (present) ((right - left) / 24f).coerceIn(-1f, 1f) else frame.laserPan,
            lanternBass = if (rawBass == 0f && bassGlow < .0001f) 0f else bassGlow,
            lanternPulse = if (pulse < .0001f) 0f else pulse,
            lanternWaterBass = waterBass,
        )
        return frame
    }
}

internal object PhotographicGeometry {
    const val Width = 1672f
    const val Height = 941f
    val lenses = listOf(340f to 160f, 1357f to 160f, 220f to 630f, 1450f to 632f)
    // Six round fixtures suspended from the top truss, measured against the photo plate.
    val trussLenses = listOf(500f to 137f, 625f to 137f, 745f to 137f,
        900f to 137f, 1060f to 137f, 1195f to 137f)
    val laserFixtures = lenses + trussLenses
    val lanterns = listOf(floatArrayOf(283f, 460f, 110f, 75f, 625f), floatArrayOf(774f, 425f, 42f, 44f, 520f),
        floatArrayOf(1120f, 654f, 175f, 165f, 860f), floatArrayOf(1417f, 425f, 50f, 48f, 510f))
    /** Extend a projected ray beyond the screen edge, not just to its stage guide point. */
    fun rayExitFactor(x: Float, y: Float, dx: Float, dy: Float): Float {
        if (!dx.isFinite() || !dy.isFinite() || (dx == 0f && dy == 0f)) return 0f
        val tx = when { dx > 0f -> (Width + 8f - x) / dx; dx < 0f -> (-8f - x) / dx; else -> Float.POSITIVE_INFINITY }
        val ty = when { dy > 0f -> (Height + 8f - y) / dy; dy < 0f -> (-8f - y) / dy; else -> Float.POSITIVE_INFINITY }
        return minOf(tx, ty).coerceAtLeast(0f)
    }
    fun meterAngle(dbfs: Float): Float = 210f + (dbfs.coerceIn(-60f, 0f) + 60f) * 2f
}
