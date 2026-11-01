package com.codeframe78.twentyfourseven.player.ui

import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState
import kotlin.math.exp
import kotlin.math.max

internal data class IonTunnelFrame(val travel: Float = 0f, val energy: Float = 0f, val bass: Float = 0f, val attack: Float = 0f, val reduced: Boolean = false)

/** Presentation-only movement from existing measured PCM; stale input cannot keep flying. */
internal class IonTunnelDrive {
    var frame = IonTunnelFrame()
        private set
    fun reset() { frame = IonTunnelFrame() }
    fun update(audio: AudioVisualizationState, nowMs: Long, seconds: Float, reduced: Boolean): IonTunnelFrame {
        val fresh = audio.freshForVideo(nowMs)
        val present = fresh.channelCount > 0 && fresh.energy.isFinite() && fresh.energy > .001f
        val dt = if (seconds.isFinite()) seconds.coerceIn(0f, .05f) else 0f
        fun level(db: Float) = if (db.isFinite()) ((db + 48f) / 42f).coerceIn(0f, 1f) else 0f
        val e = if (present) max(level(fresh.leftDbfs), level(fresh.rightDbfs)) else 0f
        val b = if (present) max(level(fresh.calibratedBandDbfs.getOrElse(0) { -60f }), level(fresh.calibratedBandDbfs.getOrElse(1) { -60f })) else 0f
        fun ease(old: Float, value: Float, seconds: Float): Float {
            val next = old + (value - old) * (1f - exp(-dt / seconds))
            return if (value == 0f && next < .0001f) 0f else next
        }
        val energy = ease(frame.energy, e, .25f)
        val bass = ease(frame.bass, b, .18f)
        val attack = ease(frame.attack, if (present && fresh.transient.isFinite()) fresh.transient.coerceIn(0f, 1f) else 0f, .09f)
        // 96 world units repeat exactly; this is the shared period of all tunnel materials.
        val travel = if (present) (frame.travel + dt * (1.8f + energy * 7f + bass * 3f) * if (reduced) .22f else 1f) % 96f else frame.travel
        frame = IonTunnelFrame(travel, energy, bass, attack, reduced)
        return frame
    }
}
