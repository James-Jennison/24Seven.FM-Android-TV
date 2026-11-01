package com.codeframe78.twentyfourseven.player.domain

import kotlinx.coroutines.flow.StateFlow

enum class IdleVisualizerStyle(val displayName: String, val description: String) {
    NeonKaleidoscope("Neon Kaleidoscope", "Mirrored neon ribbons and beat bursts"),
    AuroraDrift("Fluid Bloom", "Organic colour cells that merge and flow"),
    OscilloscopeTunnel("Signal Scope", "Luminous particle waves respond to the decoded audio"),
    SpectralMandala("Mirror Script", "Mirrored electrical light responds to spectral attacks"),
    ParticleNebula("Orbit Bloom", "Reactive constellations and particle bursts"),
    // Preserve the enum key: it is the persisted selection contract.
    LiquidPrism("Facet Forge", "Reactive stained-glass apertures cut from the signal"),
    LaserGrid("Vector Switchboard", "Modular laser paths that reconfigure on beats"),
    // Preserve the enum key: it is the persisted selection contract.
    StarfieldWarp("Pulse Observatory", "A living celestial instrument tuned by the bass"),
    ChromaticTerrain("Chromafield", "Iridescent glass folds catch light and roll with the music"),
    LightTrails("Light Trails", "Cinematic light exposures over rain-darkened streets"),
    // Keep the stable enum key so existing saved selections migrate automatically.
    MinimalSpectrum("EQ Spectrum", "Classic 32-band LED analyzer with floating peak caps"),
    VinylPulse("Signal Deck", "Vintage broadcast console with stereo needles, balance dial, phase gauge and signal lamps"),
    // Keep the stable enum key so existing saved selections migrate automatically.
    DjConsole("DJ Soff Snep", "Realistic music-reactive DJ booth with the original Soff Snep tribute"),
    PsychedelicScene("Psychedelic Scene", "Kaleidoscopic colour petals that bloom with the music"),
    LavaLamp("Lava Lamp", "A wall of glowing glass lava lamps in four music-reactive colour themes"),
    SignalRain("Harmonic Gate", "A luminous gate whose arches open with the spectrum"),
    ContourIslands("Contour Islands", "Bass-warped contour lines around glowing islands"),
    RibbonReactor("Woven Current", "Interlaced colour bands woven by the groove"),
    PrismGarden("Prism Garden", "Faceted petals rising from a reactive horizon"),
    MoonlitTides("Dusk Lattice", "A twilight field of expanding audio cells"),
    // Preserve the enum key: existing Beat Mosaic selections become Laser Light Show.
    PulseQuilt("Laser Light Show", "Full-screen laser fans, tunnels and sweeping curtains choreographed by the music"),
    EchoReef("Echo Reef", "Branching neon reef growth fed by the spectrum"),
    VelvetVortex("Magnetic Fields", "Field lines bend around a bass-powered core"),
    LanternDrift("Lantern Drift", "Photographic paper lanterns with music-driven glow and water reflections"),
    // Audition candidates are appended: existing persisted keys and ordinals stay stable.
    EmeraldResonance("Emerald Resonance", "Green plasma opens and brightens with the music"),
    AuroraVeil("Aurora Veil", "Soft aurora curtains breathe with the spectrum"),
    SpectralSilk("Spectral Silk", "Blue-violet particle curtains flex with the mids"),
    OrbitalPulse("Orbital Pulse", "Concentric lights expand on the bass"),
    ChromaticBlossom("Chromatic Blossom", "Luminous petals open with the groove"),
    ElectricSynapse("Electric Synapse", "Electric filaments surge with spectral attacks"),
    StardustIgnition("Stardust Ignition", "A starburst swells with bass and treble"),
    VioletCircuit("Violet Circuit", "Neon chevrons flex and glow with the beat"),
    PrismaticPassage("Prismatic Passage", "A triangular neon passage advances with the bass"),
    IonTransit("Ion Transit", "A cinematic metallic tunnel with blue-orange light rails and music-driven flight"),
}

/** Retired enum keys remain stable for installed preferences and renderer dispatch. */
val IdleVisualizerStyle.availableStyle: IdleVisualizerStyle
    get() = when (this) {
        IdleVisualizerStyle.SpectralSilk -> IdleVisualizerStyle.AuroraDrift
        IdleVisualizerStyle.ElectricSynapse -> IdleVisualizerStyle.OscilloscopeTunnel
        IdleVisualizerStyle.OrbitalPulse -> IdleVisualizerStyle.StarfieldWarp
        IdleVisualizerStyle.ChromaticBlossom -> IdleVisualizerStyle.PrismGarden
        IdleVisualizerStyle.PrismaticPassage -> IdleVisualizerStyle.IonTransit
        IdleVisualizerStyle.AuroraVeil -> IdleVisualizerStyle.AuroraDrift
        IdleVisualizerStyle.StardustIgnition -> IdleVisualizerStyle.StarfieldWarp
        IdleVisualizerStyle.VioletCircuit -> IdleVisualizerStyle.LaserGrid
        IdleVisualizerStyle.SpectralMandala -> IdleVisualizerStyle.NeonKaleidoscope
        IdleVisualizerStyle.ParticleNebula -> IdleVisualizerStyle.StarfieldWarp
        IdleVisualizerStyle.RibbonReactor -> IdleVisualizerStyle.ChromaticTerrain
        IdleVisualizerStyle.MoonlitTides -> IdleVisualizerStyle.ContourIslands
        else -> this
    }

val AvailableIdleVisualizerStyles: List<IdleVisualizerStyle> =
    IdleVisualizerStyle.entries.filter { it.availableStyle == it }

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
    Video,
}

data class IdleVisualizerMetadata(
    val category: IdleVisualizerCategory,
    val renderProfile: IdleVisualizerRenderProfile,
    /** Physical timing is required before this style can receive a device performance tier. */
    val requiresPhysicalBenchmark: Boolean = true,
)

val IdleVisualizerStyle.metadata: IdleVisualizerMetadata
    get() {
        val original = when (this) {
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
                if (this == IdleVisualizerStyle.LavaLamp) IdleVisualizerRenderProfile.Video else IdleVisualizerRenderProfile.Direct,
            )

            IdleVisualizerStyle.OscilloscopeTunnel,
            IdleVisualizerStyle.MinimalSpectrum,
            IdleVisualizerStyle.VinylPulse,
            IdleVisualizerStyle.SignalRain -> IdleVisualizerMetadata(IdleVisualizerCategory.Signal, IdleVisualizerRenderProfile.Direct)

            IdleVisualizerStyle.DjConsole -> IdleVisualizerMetadata(IdleVisualizerCategory.Signal, IdleVisualizerRenderProfile.Video)

            IdleVisualizerStyle.ParticleNebula,
            IdleVisualizerStyle.StarfieldWarp,
            IdleVisualizerStyle.VelvetVortex -> IdleVisualizerMetadata(IdleVisualizerCategory.Spatial, IdleVisualizerRenderProfile.Direct)

            IdleVisualizerStyle.ChromaticTerrain,
            IdleVisualizerStyle.LanternDrift -> IdleVisualizerMetadata(IdleVisualizerCategory.Ambient, IdleVisualizerRenderProfile.Direct)

            IdleVisualizerStyle.LightTrails -> IdleVisualizerMetadata(IdleVisualizerCategory.Spatial, IdleVisualizerRenderProfile.Direct)

            IdleVisualizerStyle.EmeraldResonance,
            IdleVisualizerStyle.AuroraVeil,
            IdleVisualizerStyle.SpectralSilk,
            IdleVisualizerStyle.ChromaticBlossom -> IdleVisualizerMetadata(IdleVisualizerCategory.Organic, IdleVisualizerRenderProfile.Video)

            IdleVisualizerStyle.OrbitalPulse,
            IdleVisualizerStyle.ElectricSynapse,
            IdleVisualizerStyle.StardustIgnition -> IdleVisualizerMetadata(IdleVisualizerCategory.Spatial, IdleVisualizerRenderProfile.Video)

            IdleVisualizerStyle.IonTransit -> IdleVisualizerMetadata(IdleVisualizerCategory.Spatial, IdleVisualizerRenderProfile.Direct)

            IdleVisualizerStyle.VioletCircuit,
            IdleVisualizerStyle.PrismaticPassage -> IdleVisualizerMetadata(IdleVisualizerCategory.Geometric, IdleVisualizerRenderProfile.Video)
        }
        return if (this in AbstractVideoStyles ||
            this == IdleVisualizerStyle.VinylPulse || this == IdleVisualizerStyle.PulseQuilt) original.copy(renderProfile = IdleVisualizerRenderProfile.Video) else original
    }

/** Stable persisted keys for the remaining owner-approved abstract video replacements. */
val AbstractVideoStyles: Set<IdleVisualizerStyle> = setOf(
    IdleVisualizerStyle.AuroraDrift,
    IdleVisualizerStyle.OscilloscopeTunnel,
    IdleVisualizerStyle.LiquidPrism,
    IdleVisualizerStyle.LaserGrid,
    IdleVisualizerStyle.StarfieldWarp,
    IdleVisualizerStyle.ChromaticTerrain,
    IdleVisualizerStyle.PsychedelicScene,
    IdleVisualizerStyle.SignalRain,
    IdleVisualizerStyle.ContourIslands,
    IdleVisualizerStyle.PrismGarden,
    IdleVisualizerStyle.EchoReef,
    IdleVisualizerStyle.VelvetVortex,
)

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

enum class IonTunnelPalette(val displayName: String, val description: String) {
    AmberIce("Blue / Orange", "Ice-blue rails and molten amber reflections"),
    VioletCyan("Violet / Cyan", "Ultraviolet panels and bright cyan light"),
    EmeraldGold("Emerald / Gold", "Emerald light with warm golden reflections"),
    CrimsonIce("Red / Ice", "Crimson rails and cold white-blue highlights"),
}

enum class IdleScreenStartDelay(val minutes: Int, val displayName: String) {
    OneMinute(1, "1 minute"),
    TwoMinutes(2, "2 minutes"),
    FiveMinutes(5, "5 minutes"),
    TenMinutes(10, "10 minutes"),
    FifteenMinutes(15, "15 minutes"),
    ThirtyMinutes(30, "30 minutes");

    val millis: Long get() = minutes * 60_000L
    fun next(): IdleScreenStartDelay = entries[(ordinal + 1) % entries.size]
}

data class IdleScreenPreferences(
    val automaticIdleScreenEnabled: Boolean = true,
    val startDelay: IdleScreenStartDelay = IdleScreenStartDelay.TenMinutes,
    val visualizer: IdleVisualizerStyle = IdleVisualizerStyle.NeonKaleidoscope,
    val nowPlayingOverlay: NowPlayingOverlayMode = NowPlayingOverlayMode.Brief,
    val reduceVisualIntensity: Boolean = false,
    val lavaLampPalette: LavaLampPalette = LavaLampPalette.Neon,
    val ionTunnelPalette: IonTunnelPalette = IonTunnelPalette.AmberIce,
)

interface IdleScreenPreferencesRepository {
    val preferences: StateFlow<IdleScreenPreferences>

    suspend fun setStartDelay(delay: IdleScreenStartDelay)

    suspend fun setAutomaticIdleScreenEnabled(enabled: Boolean)

    suspend fun setVisualizer(style: IdleVisualizerStyle)

    suspend fun setNowPlayingOverlay(mode: NowPlayingOverlayMode)

    suspend fun setReduceVisualIntensity(enabled: Boolean)

    suspend fun setLavaLampPalette(palette: LavaLampPalette)

    suspend fun setIonTunnelPalette(palette: IonTunnelPalette)
}
