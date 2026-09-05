package com.codeframe78.twentyfourseven.player.domain

import kotlinx.coroutines.flow.StateFlow

enum class IdleVisualizerStyle(val displayName: String, val description: String) {
    NeonKaleidoscope("Neon Kaleidoscope", "Mirrored neon ribbons and beat bursts"),
    AuroraDrift("Fluid Bloom", "Organic colour cells that merge and flow"),
    OscilloscopeTunnel("Signal Scope", "A precise layered live waveform"),
    SpectralMandala("Mirror Script", "Mirrored vector forms that redraw on attacks"),
    ParticleNebula("Orbit Bloom", "Reactive constellations and particle bursts"),
    // Preserve the enum key: it is the persisted selection contract.
    LiquidPrism("Facet Forge", "Reactive stained-glass apertures cut from the signal"),
    LaserGrid("Vector Switchboard", "Modular laser paths that reconfigure on beats"),
    // Preserve the enum key: it is the persisted selection contract.
    StarfieldWarp("Pulse Observatory", "A living celestial instrument tuned by the bass"),
    ChromaticTerrain("Chromafield", "A calm flowing chromatic surface"),
    LightTrails("Light Trails", "Long exposure-inspired audio strokes"),
    // Keep the stable enum key so existing saved selections migrate automatically.
    MinimalSpectrum("EQ Spectrum", "Classic 32-band LED analyzer with floating peak caps"),
    VinylPulse("Signal Deck", "Instrument-style meter arcs and peak needles"),
    // Keep the stable enum key so existing saved selections migrate automatically.
    DjConsole("DJ Soff Snep", "Live DJ deck with an audio-reactive neon tribute decal"),
    PsychedelicScene("Psychedelic Scene", "Kaleidoscopic colour petals that bloom with the music"),
    LavaLamp("Lava Lamp", "Full-frame fluid wax with selectable reactive colour palettes"),
    SignalRain("Harmonic Gate", "A luminous gate whose arches open with the spectrum"),
    ContourIslands("Contour Islands", "Bass-warped contour lines around glowing islands"),
    RibbonReactor("Woven Current", "Interlaced colour bands woven by the groove"),
    PrismGarden("Prism Garden", "Faceted petals rising from a reactive horizon"),
    MoonlitTides("Dusk Lattice", "A twilight field of expanding audio cells"),
    PulseQuilt("Beat Mosaic", "Large asymmetric tiles that answer the beat"),
    EchoReef("Echo Reef", "Branching neon reef growth fed by the spectrum"),
    VelvetVortex("Magnetic Fields", "Field lines bend around a bass-powered core"),
    LanternDrift("Lantern Drift", "Warm lantern light drifting with the groove"),
}

/**
 * Additive catalog metadata: style enum keys and ordinals remain the persistence contract.
 * The render profile describes the current pipeline, not a measured performance guarantee.
 */
enum class IdleVisualizerCategory(val displayName: String) {
    Signal("Signal & Data"),
    Geometric("Geometric"),
    Organic("Organic & Fluid"),
    Spatial("Spatial & Particle"),
    Ambient("Ambient Scene"),
}

enum class IdleVisualizerRenderProfile {
    Direct,
    Feedback,
}

data class IdleVisualizerMetadata(
    val category: IdleVisualizerCategory,
    val renderProfile: IdleVisualizerRenderProfile,
    /** Physical timing is required before this style can receive a device performance tier. */
    val requiresPhysicalBenchmark: Boolean = true,
)

val IdleVisualizerStyle.metadata: IdleVisualizerMetadata
    get() = when (this) {
        IdleVisualizerStyle.NeonKaleidoscope,
        IdleVisualizerStyle.SpectralMandala,
        IdleVisualizerStyle.LaserGrid,
        IdleVisualizerStyle.PsychedelicScene,
        IdleVisualizerStyle.RibbonReactor,
        IdleVisualizerStyle.PrismGarden,
        IdleVisualizerStyle.PulseQuilt -> IdleVisualizerMetadata(IdleVisualizerCategory.Geometric, IdleVisualizerRenderProfile.Direct)

        IdleVisualizerStyle.AuroraDrift,
        IdleVisualizerStyle.LiquidPrism,
        IdleVisualizerStyle.LavaLamp,
        IdleVisualizerStyle.ContourIslands,
        IdleVisualizerStyle.MoonlitTides,
        IdleVisualizerStyle.EchoReef -> IdleVisualizerMetadata(
            IdleVisualizerCategory.Organic,
            if (this == IdleVisualizerStyle.LavaLamp) IdleVisualizerRenderProfile.Feedback else IdleVisualizerRenderProfile.Direct,
        )

        IdleVisualizerStyle.OscilloscopeTunnel,
        IdleVisualizerStyle.MinimalSpectrum,
        IdleVisualizerStyle.VinylPulse,
        IdleVisualizerStyle.DjConsole,
        IdleVisualizerStyle.SignalRain -> IdleVisualizerMetadata(IdleVisualizerCategory.Signal, IdleVisualizerRenderProfile.Direct)

        IdleVisualizerStyle.ParticleNebula,
        IdleVisualizerStyle.StarfieldWarp,
        IdleVisualizerStyle.VelvetVortex -> IdleVisualizerMetadata(IdleVisualizerCategory.Spatial, IdleVisualizerRenderProfile.Direct)

        IdleVisualizerStyle.ChromaticTerrain,
        IdleVisualizerStyle.LanternDrift -> IdleVisualizerMetadata(IdleVisualizerCategory.Ambient, IdleVisualizerRenderProfile.Direct)

        IdleVisualizerStyle.LightTrails -> IdleVisualizerMetadata(IdleVisualizerCategory.Geometric, IdleVisualizerRenderProfile.Feedback)
    }

enum class NowPlayingOverlayMode(val displayName: String) {
    Off("Off"),
    Brief("Brief on entry"),
    Always("Always"),
}

/** A saved colour family for the Lava Lamp scene; names stay stable for local persistence. */
enum class LavaLampPalette(val displayName: String, val description: String) {
    Neon("Neon", "Hot pink, violet, cyan, and electric orange"),
    Sunset("Sunset", "Red, coral, orange, and golden yellow"),
    Arctic("Arctic", "Electric blue, aqua, ultraviolet, and white"),
    Acid("Acid", "Lime, chartreuse, cyan, and ultraviolet"),
}

data class IdleScreenPreferences(
    val automaticIdleScreenEnabled: Boolean = true,
    val visualizer: IdleVisualizerStyle = IdleVisualizerStyle.NeonKaleidoscope,
    val nowPlayingOverlay: NowPlayingOverlayMode = NowPlayingOverlayMode.Brief,
    val reduceVisualIntensity: Boolean = false,
    val lavaLampPalette: LavaLampPalette = LavaLampPalette.Neon,
)

interface IdleScreenPreferencesRepository {
    val preferences: StateFlow<IdleScreenPreferences>

    suspend fun setAutomaticIdleScreenEnabled(enabled: Boolean)

    suspend fun setVisualizer(style: IdleVisualizerStyle)

    suspend fun setNowPlayingOverlay(mode: NowPlayingOverlayMode)

    suspend fun setReduceVisualIntensity(enabled: Boolean)

    suspend fun setLavaLampPalette(palette: LavaLampPalette)
}
