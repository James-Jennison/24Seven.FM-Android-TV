# Visualization catalog reduction — 2026-09-07

Owner approved the recommended12removals.22 selectable visualizations remain. Retired enum keys/ordinals are retained only for compatibility; both preference repositories resolve retired selections to an available style. No renderer, playback, audio, automatic-idle, comfort or palette settings changed.

Removed: Spectral Silk, Electric Synapse, Orbital Pulse, Chromatic Blossom, Prismatic Passage, Aurora Veil, Stardust Ignition, Violet Circuit, Mirror Script, Orbit Bloom, Woven Current, Dusk Lattice.

12MP4s and12posters were moved from app source into `work/catalog-trim/retired-assets/`. Archived hashes match the original source. Total asset removal89.59MiB; video removal89.00MiB. The debug APK is415.82MiB versus the previously measured505.81MiB. Initial incremental packaging left unused ZIP gaps; only isolated candidate packaging outputs/state were archived and regenerated to obtain the actual size reduction. No `gradlew clean` and no signing/publication.

Saved selections resolve as follows (stable internal keys):

- `SpectralSilk` → `AuroraDrift`
- `ElectricSynapse` → `OscilloscopeTunnel`
- `OrbitalPulse` → `StarfieldWarp`
- `ChromaticBlossom` → `PrismGarden`
- `PrismaticPassage` → `IonTransit`
- `AuroraVeil` → `AuroraDrift`
- `StardustIgnition` → `StarfieldWarp`
- `VioletCircuit` → `LaserGrid`
- `SpectralMandala` → `NeonKaleidoscope`
- `ParticleNebula` → `StarfieldWarp`
- `RibbonReactor` → `ChromaticTerrain`
- `MoonlitTides` → `ContourIslands`

Validation: compile/lint/162unit tests PASS; focused physical Google TV Streamer test PASS. It checks22active/12retired, all34saved-key reads and writes, retained automatic-idle Off, retained-video openability, all12removedvideos absent,22-item picker caption and D-pad selection. Initial test's incorrect assumption that Down focuses the last selected row was corrected to use the first list row; no product navigation change was required. Both original failure and finalPASS logs retained.

Final candidate `e3dfa0f9fcbf77c76625f5890559b372cce8a8e0`; installed APK hash matches `work/catalog-trim/artifact-proof.json`. All root app files match the isolated candidate;204pre-existing app files/assets are byte-identical. Physical normal-menu navigation confirmed22choices and restored the previously selected Lantern Drift preview. Removal reduces packaged storage/download overhead; no claim of frame-rate gains on unchanged retained scenes.

Review: Grok/Gemini/DeepSeek cycle `review-cycle-dfc100f722be`; all three returned NO FINDINGS; `closure-ee157e10e2c9` is READY. Evidence cycle `evidence-cycle-ee2fa250a928`.
