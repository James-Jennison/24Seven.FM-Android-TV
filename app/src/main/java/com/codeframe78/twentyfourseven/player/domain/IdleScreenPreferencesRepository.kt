package com.codeframe78.twentyfourseven.player.domain

import kotlinx.coroutines.flow.StateFlow

enum class IdleVisualizerStyle(val displayName: String, val description: String) {
    NeonKaleidoscope("Neon Kaleidoscope", "Mirrored neon ribbons and beat bursts"),
    AuroraDrift("Fluid Bloom", "Organic colour cells that merge and flow"),
    OscilloscopeTunnel("Signal Scope", "A precise layered live waveform"),
    SpectralMandala("Mirror Script", "Mirrored vector forms that redraw on attacks"),
    ParticleNebula("Orbit Bloom", "Reactive constellations and particle bursts"),
    LiquidPrism("Glass Current", "Refractive full-frame liquid distortion"),
    LaserGrid("Vector Switchboard", "Modular laser paths that reconfigure on beats"),
    StarfieldWarp("Deep Warp", "Bass-driven forward motion through deep space"),
    ChromaticTerrain("Chromafield", "A calm flowing chromatic surface"),
    LightTrails("Light Trails", "Long exposure-inspired audio strokes"),
    // Keep the stable enum key so existing saved selections migrate automatically.
    MinimalSpectrum("EQ Spectrum", "Classic 32-band LED analyzer with floating peak caps"),
    VinylPulse("Signal Deck", "Instrument-style meter arcs and peak needles"),
}

data class IdleScreenPreferences(
    val visualizer: IdleVisualizerStyle = IdleVisualizerStyle.NeonKaleidoscope,
)

interface IdleScreenPreferencesRepository {
    val preferences: StateFlow<IdleScreenPreferences>

    suspend fun setVisualizer(style: IdleVisualizerStyle)
}
