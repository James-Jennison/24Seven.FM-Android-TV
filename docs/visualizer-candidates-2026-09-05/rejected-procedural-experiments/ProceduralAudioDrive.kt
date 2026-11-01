package com.codeframe78.twentyfourseven.player.ui

import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerStyle
import kotlin.math.exp

internal fun IdleVisualizerStyle.isReferenceInspired() = when (this) {
    IdleVisualizerStyle.EmeraldResonance, IdleVisualizerStyle.AuroraVeil,
    IdleVisualizerStyle.SpectralSilk, IdleVisualizerStyle.OrbitalPulse,
    IdleVisualizerStyle.ChromaticBlossom, IdleVisualizerStyle.ElectricSynapse,
    IdleVisualizerStyle.StardustIgnition, IdleVisualizerStyle.VioletCircuit,
    IdleVisualizerStyle.PrismaticPassage, IdleVisualizerStyle.IonTransit -> true
    else -> false
}

/** Presentation envelope only: never owns or writes playback state. */
internal class ProceduralAudioDrive {
    var low = 0f; private set
    var mid = 0f; private set
    var high = 0f; private set
    var hit = 0f; private set
    fun reset() { low = 0f; mid = 0f; high = 0f; hit = 0f }
    fun update(audio: AudioVisualizationState, seconds: Float, reduced: Boolean) {
        val dt = if (seconds.isFinite()) seconds.coerceIn(0f, .1f) else 0f
        val present = audio.channelCount > 0 && audio.energy.isFinite() && audio.energy > .001f
        val gain = if (reduced) .25f else 1f
        fun band(i: Int): Float {
            val db = audio.calibratedBandDbfs.getOrNull(i) ?: -60f
            return if (present && db.isFinite()) ((db + 60f) / 54f).coerceIn(0f, 1f) * gain else 0f
        }
        fun smooth(v: Float, target: Float) = v + (target-v) * (1f-exp(-dt / if(target>v) .025f else .20f))
        low = smooth(low, (band(0)+band(1))*.5f)
        mid = smooth(mid, band(2)); high = smooth(high, band(3))
        val target = if(present && audio.transient.isFinite()) audio.transient.coerceIn(0f,1f)*gain else 0f
        hit = smooth(hit,target)
    }
}
