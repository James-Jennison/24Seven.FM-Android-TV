# Lantern Drift, Signal Deck and Laser Light Show — photographic/native treatment

The owner expanded the scope of these three styles to receive the treatment used for Lava Lamp and DJ Soff Snep, then required an energetic full-screen laser show with light emitted by the upper truss fixtures. Stable saved keys remain `LanternDrift`, `VinylPulse` and `PulseQuilt`.

- Lantern Drift combines a photographic waterside scene with localized warm paper-lantern illumination and native reflection movement.
- Signal Deck combines a photographic instrument rack with full-resolution independent left/right calibrated RMS dBFS needles, explicitly decaying RMS maxima, 32 spectrum bands and correlation. Meter readings are native graphics, not baked into the photo.
- Laser Light Show combines a fixed photographic rig with four large projector fans and six additional upper-truss light sources. Normal mode has saturated fans, crossed ribbons, tunnels and sweeping curtains selected by measured PCM attacks. After the owner reported repetitive motion, the laser renderer was detached from the continuous ambient phase clock: only measured bass/level rises and detected attacks advance laser travel. Fast 22 ms attack / 100 ms release envelopes preserve quiet/loud contrast; bass changes fan width and sweep extent, stereo RMS difference steers aim, and attack pulses accent emitted light. A steady input settles rather than continuing a timed sweep routine. Every ray extends through its aim guide to the viewport boundary, filling the foreground and screen edges. All ten measured lens centers emit light; none of the six round overhead fixtures is merely decorative anymore.

Three generated photographic plates were created with built-in `image_gen`. Exact prompts are in `work/realistic-three/image-prompts.json`, source PNGs in `plates/`, and encoder/geometry/hash evidence alongside them. These are generated images, not filmed footage. Each embedded plate is a silent 1920×1080, 30 fps, 20-second static video. Actual reactive motion is supplied by native Player graphics; standalone MP4s do not contain the moving visualization. Full media decode and metadata validation passed; the three videos total about 18.4 MiB.

## Rendering and lifecycle

The camera stays fixed. The native scenes share the existing presentation tick and video owner; they add no player, recording path, microphone permission, MediaSession or listener telemetry. Laser geometry uses radial distance attenuation, batched paths and a bounded hardware light surface (480×270 on the physical 1920×1080 target) scaled over the full-resolution photo. Meter ink and lantern view geometry retain full resolution. Hardware drawing is directly asserted during live acceptance; the software path is also exercised by pixel tests.

Normal projectors draw 13 rays each; reduced mode draws 5 with lower brightness and slower movement. The six truss fixtures draw 3 rays each. Reduced colors remain stable and measured attacks do not trigger intensity accents. The photograph retains its exposure and calibrated text remains readable. Digital silence retains valid channels and calibrated floor readings; unavailable/stale PCM displays NO PCM. Phase/aim freeze without fresh audio, emitted light fades, and the internal onset pulse decays during gaps so it cannot revive spuriously on resume.

The output is validated on a 16:9 Google TV Streamer. Non16:9 fitting has not been validated; the existing photo and overlay mapping can stretch together. These results do not establish speaker/display latency or change the separate shared-core decode-clock/output-alignment boundary.

## Current physical evidence

Current isolated candidate: `106e0c7957adebdf6ec9a36f018fc3aef2a37134`. The owner's dirty checkout remains preserved. Compile, 152 unit tests (zero failures/errors/skips), lint and Android test compilation passed in `evidence-cycle-ff765df6cbc1`.

Eight current native/live tests passed on the verified physical Google TV Streamer in 134.619 s. They cover independent instrument response and bounds; lens registration including all six upper fixtures; four-edge laser coverage; distinct choreography patterns; translucent output without visible white flashes; reduced intensity; radial attenuation; stale/reset behavior; actual fresh station PCM; hardware drawing; fixed camera; pause and resumed decoding.

| Scene | Normal draw p95 | Reduced draw p95 | Decoder drops per measured 8 s window | Pause settle |
| --- | ---: | ---: | ---: | ---: |
| Lantern Drift |33.81 ms|33.53 ms|0|3.59 s|
| Signal Deck |33.46 ms|33.45 ms|0|3.43 s|
| Laser Light Show |33.51 ms|33.46 ms|0|2.48 s|

Normal laser hardware advanced 227 native draws and 240 decoder buffers during 8 s; p99 was 34.11 ms. The draw p95 acceptance limit is 44 ms. These are view-tree draw intervals with independent native/decoder advancement, not a locked 30 fps or acoustic synchronization claim. Native PixelCopy captures of the actual app window in both modes are under `work/realistic-three/screenshots-music/`; Codex visually confirmed the six upper-truss sources and full-screen rays. Text-only reviewers receive this as a Codex observation, not a claim they inspected the screenshots themselves.

Live normal laser control varied from 0.202 to 0.892 in the measured window; independent music-travel state advanced from 4.064 to 0.772 radians (wrapped). Reduced control varied from 0.210 to 0.936 before its separate intensity attenuation. Unit tests prove held audio state cannot run autonomous travel, distinct attacks/rises do move it, loud bass changes light rapidly, quiet input settles, repeated captures do not invent motion, and stale reentry does not invent a movement impulse. Native pixel tests additionally prove changing only the ambient phase cannot change the laser image, while a measured-drive attack changes geometry and increases total emitted alpha more than threefold. The software fallback retains the full bounded 480×270 light bitmap.

Five current loop regressions and installed artifact readback are pending; final reviewer closure is pending.

## Review and evidence integrity

Independent initial reviews completed in `review-cycle-673ebe5f8123`; energetic-source reviews completed in `review-cycle-4de111915391`. Prior findings and rejected recommendations remain immutable in the engineering ledger. Radial attenuation and all-pattern opacity coverage were corrected. The thin laser rendering margin was corrected with bounded GPU rendering and verified against the tighter 44 ms gate. Whole-photo dimming, invented first-sample onset and new test-only production accessors were not adopted; the evidence records the rationale and the non16:9 compatibility bound.

Earlier failed checks remain untouched: near-transparent 8-bit edge RGB rounding required a precise visible-white assertion; pause acceptance now polls actual decoder/light settling up to 6 s rather than assuming an action instantly empties queued PCM; full-resolution laser rendering failed the tighter performance gate and was replaced by the bounded layer. Passing evidence never overwrites those failures. The first music-revision test invocation also retains an incorrect class-name error and two fixture failures: coverage now supplies an explicit high-bass state, and the attenuation fixture explicitly sets laserTravel to its intended divergent angle instead of using the old ambient-phase multiplier. The unchanged edge/attenuation assertions pass. Earlier delivery proofs and screenshots are revision-bound; only current candidate evidence supports this delivery.

Current code evidence: `work/realistic-three/music-final-evidence-status.json`. Physical native/live results: `device-music-native-final.log` and `music-final-metrics.log`. Exact source and asset hashes: `candidate-manifest.json`. The baseline protects unrelated owner source. Debug testing only; no signing, publication or release.
