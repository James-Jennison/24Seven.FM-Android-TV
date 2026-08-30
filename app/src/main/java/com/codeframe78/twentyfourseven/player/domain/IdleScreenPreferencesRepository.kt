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
    // Keep the stable enum key so existing saved selections migrate automatically.
    DjConsole("DJ Soff Snep", "Live DJ deck with an audio-reactive neon watermark"),
    PsychedelicScene("Psychedelic Scene", "Kaleidoscopic colour petals that bloom with the music"),
    LavaLamp("Lava Lamp", "Full-frame fluid wax with selectable reactive colour palettes"),
    SignalRain("Signal Rain", "Falling spectrum threads that splash on bass hits"),
    ContourIslands("Contour Islands", "Bass-warped contour lines around glowing islands"),
    RibbonReactor("Ribbon Reactor", "Braided colour ribbons tightened by the groove"),
    PrismGarden("Prism Garden", "Faceted petals rising from a reactive horizon"),
    MoonlitTides("Moonlit Tides", "Layered wave crests shaped by low frequencies"),
    PulseQuilt("Pulse Quilt", "A calm six-by-four quilt of audio-flexing tiles"),
    EchoReef("Echo Reef", "Branching neon reef growth fed by the spectrum"),
    VelvetVortex("Velvet Vortex", "A deep bass vortex with soft colour eddies"),
    LanternDrift("Lantern Drift", "Warm lantern light drifting with the groove"),
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
    val visualizer: IdleVisualizerStyle = IdleVisualizerStyle.NeonKaleidoscope,
    val nowPlayingOverlay: NowPlayingOverlayMode = NowPlayingOverlayMode.Brief,
    val reduceVisualIntensity: Boolean = false,
    val lavaLampPalette: LavaLampPalette = LavaLampPalette.Neon,
)

interface IdleScreenPreferencesRepository {
    val preferences: StateFlow<IdleScreenPreferences>

    suspend fun setVisualizer(style: IdleVisualizerStyle)

    suspend fun setNowPlayingOverlay(mode: NowPlayingOverlayMode)

    suspend fun setReduceVisualIntensity(enabled: Boolean)

    suspend fun setLavaLampPalette(palette: LavaLampPalette)
}
