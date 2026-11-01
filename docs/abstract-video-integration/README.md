# Original abstract videos with decoded audio response

The sixteen original v3 abstract videos are bundled locally at 1920×1080, 30 fps, 16 seconds per loop. They retain the existing saved style keys. The ten preexisting video candidates and unrelated renderer work are preserved.

Decoded station PCM drives whole-video speed, scale, selected mid-band shear, and brightness. Reduced intensity lowers the response. The clips are silent and their decoder owns neither audio focus nor a MediaSession. Individual prerecorded objects are not independently driven by instruments. Missing or stale PCM pauses the local decoder; presentation envelopes settle smoothly.

## Corrected live-state lifetime

The real station repository was publishing fresh PCM, but `TvRadioViewModel.visualization` wrapped that already-hot StateFlow in `stateIn(WhileSubscribed(5_000))`. Video renderers poll `.value` without collecting, so the wrapper never starts or stops after the last collector leaves. Its stale measurement then pauses the video. Normal-Player observation reproduced this: rendered buffers stopped at 73 while repository audio age was 4 ms and energy was .725.

The ViewModel now exposes the repository StateFlow directly. The original Compose frame timer remains. A proposed view-owned timer was tested, failed to address the cause, and was removed. A unit regression verifies current values without collectors, after collector cancellation and 6000 ms, and after clearing PCM.

## Media mapping

| Display name | Preserved key |
|---|---|
| Fluid Bloom | `AuroraDrift` |
| Signal Scope | `OscilloscopeTunnel` |
| Mirror Script | `SpectralMandala` |
| Orbit Bloom | `ParticleNebula` |
| Facet Forge | `LiquidPrism` |
| Vector Switchboard | `LaserGrid` |
| Pulse Observatory | `StarfieldWarp` |
| Chromafield | `ChromaticTerrain` |
| Psychedelic Scene | `PsychedelicScene` |
| Harmonic Gate | `SignalRain` |
| Contour Islands | `ContourIslands` |
| Woven Current | `RibbonReactor` |
| Prism Garden | `PrismGarden` |
| Dusk Lattice | `MoonlitTides` |
| Echo Reef | `EchoReef` |
| Magnetic Fields | `VelvetVortex` |

Media hashes and full decode metadata are in [media-manifest.json](media-manifest.json). All sixteen files were decoded completely (480 frames each); source hashes were checked against the v3 master manifest. Total new media is approximately 78 MB.

## Validation scope

- Final compile, 103 unit tests, lint, debug assembly, and Android test assembly passed. No failures, errors, or skipped unit tests.
- Debug APK SHA-256: `c7f3f7b7baa5e9466e828c014acb90536f361b48ddd4ac3fbd1502b6ef752e85`.
- Physical target: verified Google TV Streamer. Debug update preserves app data.
- Final physical suite: `OK (4 tests)`, 181.238 seconds. All 26 clips advance actual decoder-buffer counters, move, stop on silence, and recreate surfaces. Synthetic PCM verifies bass versus treble and stale-state pause. Fluid Bloom runs 65 seconds through repeated loops. A 30-second live station test uses the actual `TvRadioViewModel.visualization` and asserts advancing decoder output after the old subscriber timeout.
- Final-source review: Claude, Gemini and DeepSeek completed `review-cycle-19ef1ca97a96` with no findings on the flow fix. Normal-Player owner-operated observation passed: 15 samples, output advanced to 534 frames, playWhenReady/moving stayed true, final PCM age 3 ms. Broker closure `closure-5d453e26cd5e` is READY; all three final closure reviews completed with no findings.
- Installed app SHA-256 was read from the Streamer and exactly matches the local APK hash above.
- Earlier short device tests passed but were insufficient: the owner subsequently reproduced the live freeze. Their results remain distinct from final acceptance. New checks count actual rendered output buffers, run multiple loops, and use the actual ViewModel audio path.
- Task evidence is retained under `work/audio-reactive-v3/`. Broker change: `change-d13c2c4a643e`.

This is a dirty-working-tree development build, not a signed release or published artifact. Source baseline and hashes preserve the distinction from the preexisting user work. No Google Play or production release action was taken. No sustained FPS benchmark or subjective musical/aesthetic acceptance is claimed.


## Chromafield — Liquid Spectrum (September 6)

The owner-approved replacement gives Chromafield an original 20-second iridescent glass-fold loop, a new poster and description, and 9% maximum bass expansion (previously 4.5%). The `ChromaticTerrain` key is retained. Other styles and the corrected hot PCM state path are unchanged. This supersedes the earlier 16-second Chromafield media record. See `docs/abstract-video-integration/chromafield-liquid-spectrum.md` for current validation.
