package com.codeframe78.twentyfourseven.player.ui

import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState
import kotlin.math.exp
import kotlin.math.max

internal data class LightTrailsFrame(val travel: Float = 0f, val energy: Float = 0f, val bass: Float = 0f, val attack: Float = 0f, val reduced: Boolean = false, val sky: Float = 0f, val sweep: Float = 0f)

/** Presentation-only movement from existing measured PCM; stale input cannot keep the trails moving. */
internal class LightTrailsDrive {
    var frame = LightTrailsFrame()
        private set
    private var bassAverage = 0f
    private var previousBass = 0f
    private var lastSample = 0L
    private var bassOnset = 0f
    fun reset() { frame = LightTrailsFrame(); bassAverage=0f; previousBass=0f; lastSample=0L; bassOnset=0f }
    fun update(audio: AudioVisualizationState, nowMs: Long, seconds: Float, reduced: Boolean): LightTrailsFrame {
        val fresh = audio.freshForVideo(nowMs)
        val present = fresh.channelCount > 0 && fresh.energy.isFinite() && fresh.energy > .001f
        val dt = if (seconds.isFinite()) seconds.coerceIn(0f, .05f) else 0f
        fun level(db: Float) = if (db.isFinite()) ((db + 48f) / 42f).coerceIn(0f, 1f) else 0f
        val e = if (present) max(level(fresh.leftDbfs), level(fresh.rightDbfs)) else 0f
        val b = if (present) level(fresh.calibratedBandDbfs.getOrElse(0) { -60f })*.35f + level(fresh.calibratedBandDbfs.getOrElse(1) { -60f })*.65f else 0f
        // Follow bass articulation relative to its recent level, rather than holding a
        // compressed/mastered track near maximum brightness for its entire duration.
        if (present && lastSample == 0L) { previousBass=b; bassAverage=b }
        bassAverage += (b-bassAverage)*(1f-exp(-dt/1.6f))
        bassOnset *= exp(-dt/.14f)
        if (present && fresh.sampledAtElapsedRealtimeMs != lastSample) {
            bassOnset=max(bassOnset,((b-previousBass).coerceAtLeast(0f)*9f).coerceAtMost(1f))
            previousBass=b; lastSample=fresh.sampledAtElapsedRealtimeMs
        }
        if (!present) { previousBass=0f; lastSample=0L; bassOnset=0f }
        val articulated = (b*.15f+(b-bassAverage).coerceAtLeast(0f)*4.5f+bassOnset*.75f).coerceIn(0f,1f)
        fun ease(old: Float, value: Float, seconds: Float): Float {
            val next = old + (value - old) * (1f - exp(-dt / seconds))
            return if (value == 0f && next < .0001f) 0f else next
        }
        val energy = ease(frame.energy, e, .25f)
        val bass = ease(frame.bass, if(present) articulated else 0f, if(articulated>frame.bass) .022f else .11f)
        val attack = ease(frame.attack, if (present && fresh.transient.isFinite()) max(bassOnset, fresh.transient.coerceIn(0f, 1f)*.35f) else 0f, .09f)
        // 29 world units repeat exactly; this is the period of the exposure lanes.
        val travel = if (present) (frame.travel + dt * (energy * 3f + bass * 23f + attack * 15f) * if (reduced) .22f else 1f) % 29f else frame.travel
        val skyTarget=if(present) level(fresh.calibratedBandDbfs.getOrElse(2){-60f})*.55f+level(fresh.calibratedBandDbfs.getOrElse(3){-60f})*.45f else 0f
        val sky=ease(frame.sky,skyTarget,.08f)
        // 2pi repeats every searchlight direction continuously, even on long sessions.
        val sweep=if(present) (frame.sweep+dt*(energy*.32f+bass*1.8f+attack*.65f)*(if(reduced) .2f else 1f)) % (Math.PI.toFloat()*2f) else frame.sweep
        frame = LightTrailsFrame(travel, energy, bass, attack, reduced, sky, sweep)
        return frame
    }
}
