# TV05 physical-device validation

## September 6 — original abstract audio-reactive videos

Sixteen new 1080p original clips were added with preserved saved keys and existing ten candidates retained. Final debug APK SHA-256 `c7f3f7b7baa5e9466e828c014acb90536f361b48ddd4ac3fbd1502b6ef752e85` is installed and its on-device hash matches. Compile, 103 unit tests, lint, and final physical suite `OK (4 tests)` (181.238 s) pass. Normal Player observation first reproduced a real freeze at73 decoded buffers despite fresh PCM; the redundant ViewModel WhileSubscribed wrapper was removed, and the live test now uses the ViewModel rather than bypassing it. Final owner-operated observation passed with decoder output advancing to 534 frames and fresh PCM. See [full scoped evidence](abstract-video-integration/README.md).

Technical closure: `closure-5d453e26cd5e` — READY; final-source and closure reviews completed by all three reviewers.

## September 5 — restore uploaded clips after owner rejection

- Owner rejected the Canvas and material-shader experiments and explicitly requested
  returning to the uploaded clips. Restored all ten video assets/posters and original
  video implementation/routing; assets match all ten SHA-256 entries in the prior manifest.
- Rejected experiments moved to `docs/visualizer-candidates-2026-09-05/rejected-procedural-experiments/`;
  no procedural source or shader remains in active Android source sets.
- `heavy-gradle` compileDebugKotlin, testDebugUnitTest, assembleDebug,
  assembleDebugAndroidTest and lintDebug PASS (40 seconds); diff whitespace check PASS.
- Both debug APKs installed with `install -r` on physical Google TV Streamer.
  `VideoVisualizerViewTest` PASS (27.757 seconds): all ten clips decode changing frames,
  pause on silence, and recreate their surface after foreground re-entry under PCM fixtures.
- Restored APK SHA-256: `3c400556ddf81297484ad820ca25a9c13d8560ed20a9c6acc8b1512a8260812c`.
- Procedural control-plane change `tv-ten-original-scenes-20260905` is superseded by owner
  rejection, recorded in `convergence-ef871bee8b30`; its reviews do not approve the restored
  video path. No commit, push, signing or release performed. Clip redistribution provenance
  remains a separate release gate. Existing unrelated work and original visualizers preserved.

## September 5 — continuous beam parameters and stronger attack contrast

- Owner rejected the eight underlying poses even in shuffled order and approved replacing that
  menu, preserving the photographed rig and floor-wash intensity. `LaserCueChoreography` now
  generates continuous pan/slope/scatter, six aim offsets, fan spreads and bank weights on attacks.
  There is no eight-case pose selection. Positions settle between attacks; no timer drives sway.
- Attack threshold changed from max(.035, average*1.5) to max(.025, average*1.25), rising-edge
  threshold .008 to .006; the 320ms retrigger guard remains. Pulse decay is 240ms rather than 180ms.
  Overhead core strength uses .12 + .10*drive + .78*pulse, multiplied by bounded bank strength;
  fan width responds strongly to pulse. This increases contrast rather than maximum white output.
  Floor coefficients and stage assets unchanged; the low floor-bank activity range remains .35–1.
- Exact final source: compile/unit/assemble/lint PASS (44s); five controller tests pass, including
  64 distinct quantized six-beam poses and actual PCM synthetic percussion. Broker evidence
  `evidence-fe84b4be7c0b` PASS, artifact integrity verified. `git diff --check` PASS.
- APK SHA-256 `dac66693092e1ff41f85978b266a00389af349c35a5a22e3247ee7072f8be69d` installed with
  data-preserving update on physical Google TV Streamer. Reopened app and returned through the
  chooser to full-screen Laser Light Show. No other visualization was left selected.
- `/tmp/laser-continuous-live.mp4`: 44.638333 seconds H.264/AAC, scrcpy playback duplication.
  Audio mean -11.4dB, peak 0dB. Initial Opus-header warning during remux; MP4 audio decoded.
  `/tmp/laser-continuous-sequence.png` sampled across time shows varying asymmetric targets,
  spreads and brightness instead of the earlier named-pose menu. This is not a listening test,
  measured speaker/video latency, FPS benchmark, or owner aesthetic approval.
- Independent review `review-cycle-c74d6724dde8`: Claude no findings; Gemini's missing-draw and
  mismatched-vertex-shader claims withdrawn after full helper supplied in `review-cycle-8a279c9aff4e`.
  DeepSeek geometry claims sent for clarification in `review-cycle-fe0e4508a022`: aspect is H/W,
  so screen-space perpendicularity requires transforming NDC x; floor endpoint y=1.8 is outside
  the viewport regardless of x. Final clarification complete: remaining floor-width claim applies
  the overhead shader to the wrong path (floor calls ordinary triangleStrip); NOT APPLICABLE.
  Screen-space perpendicularity sweep: maximum dot error 4.44e-16. Recorded dispositions in
  `convergence-5988c7092ea8`; new technical closure `closure-580c44c87d1d`: READY.
- Scope: Streamer GLES. Canvas fallback remains the prior implementation. No playback, signing,
  release, or unrelated work changed. No media was sent to reviewers; source review only.

## September 5 — replace weak beat offsets and recurring sine choreography

### Subsequent owner correction: fixed cue order still repetitive

- Post-review continuation: physical Streamer still showed full-screen Laser Light Show without
  navigation. Fresh `/tmp/laser-followup-live.mp4` is 34.640333 seconds, H.264/AAC, audio mean
  -12.3 dB / peak 0 dB. Captured with scrcpy playback duplication, leaving TV audio enabled.
  One-second sampled sequence `/tmp/laser-followup-sequence.png` shows changed cue order, left/right
  sweeps, crosses, and focus patterns. This does not measure perceived beat alignment, frame pacing,
  or establish owner visual acceptance. An initial Opus-header warning occurred during remux;
  the MP4 audio decoded. No source, installation, selection, commit, or release change was made.
- Replaced sequential eight-cue cycling with hit-driven xorshift selection mixed with measured
  transient/energy, excluding the two most recent modes. Each cue receives bounded attack-dependent
  spread and varying pan. This removes the fixed playlist, not a promise of infinitely unique shapes.
- Five controller tests pass, including 64 identical hits: no adjacent/two-back cue repeat, different
  first/second eight-cue blocks, at least six modes used, and positions bounded to [-3,3]. Existing
  actual PCM test and no-fake-hit/held-state tests retained.
- Compile/unit/assemble/lint passed in 40 seconds; APK update succeeded. SHA-256:
  `e012b5f0ae168a24212f7913b0031b51121209c78507ec8435545cbff2840c5d`.
- Reviews: `review-cycle-a45061365f86` and independent Gemini `review-cycle-4f8702a49c29`.
  Claude/Gemini report no findings on revised selector. Earlier DeepSeek initial cycle
  `review-cycle-fe77ab771fb9` failed with two execution timeouts; not counted as approval.
  Final DeepSeek revised-source job timed out, including the owner-requested retry of durable job
  `deepseek-17b77d26f738` (attempt 2, 300 seconds): four executions across the two cycles failed.
  Formal review closure is blocked by that provider, not counted as consent or approval.
  Further retry was rejected by the broker (`job is not retryable`). Read-only inspection confirmed
  an enforced two-attempt budget for DeepSeek jobs and a 300-second execution timeout; the service
  remains active. No attempt counters, service settings, or failed evidence were changed, and no
  duplicate cycle was submitted to bypass the limit. Recovery now requires separately scoped
  machine-global broker maintenance, not another TV app change.
  Owner subsequently approved broker repair and one additional attempt for this exact job.
  DeepSeek's bounded deadline was increased to 900 seconds (other providers remain 300 seconds);
  35 broker tests passed. Before attempt 3, the failed job snapshot and owner exception were
  recorded in `owner_retry_exceptions`; attempt counters were not reset. Attempt 3 completed in
  470.54 seconds, response `c832ac7994ba3d76e4b85ce0c87be4fdc08a8588267f0a5f08aea86d901b6673`.
  DeepSeek's MEDIUM shared-aim finding was confirmed with narrower scope: floor banks reuse offsets,
  but distinct calibrated origins and `endX = 1.8*x + 0.5*aim(bank)` refute identical endpoints.
  The six overhead fixtures index six aims; expanding or rejecting floor indices is not required.
  Disposition: `convergence-75adaa07c938`. Earlier fixed-playlist review was superseded by the
  completed final-source review, retaining failures. Broker closure `closure-f7df71e8b490`: READY.
  This is technical review closure, not owner approval of perceived beat synchronization.
  The app and playback were not changed for this recovery.
- Final physical recording `/tmp/laser-varied-hit-cues.mp4` (34.71 s, H.264/AAC remux of
  `/tmp/laser-varied-hit-cues.mkv`) and `/tmp/laser-varied-hit-cues-sequence.png` inspected across time:
  left/right banks, crosses, fans and focus cues occur in varied order, with varying aim and spread.
  Audio mean -11.4 dB, peak 0 dB; scrcpy duplication preserves TV audio. Initial Opus-header warning
  during remux, subsequent audio decoded. Tests/code demonstrate no fixed eight-cue playlist;
  this clip does not prove infinite non-repetition or subjective musical acceptance.

- Owner rejected the preceding concert iteration: barely perceptible synchronization and repeated
  outward/cross/outward choreography. Previous subjective acceptance is explicitly NOT established.
- GLES now consumes the existing PCM spectral-transient feature in queued `setAudioTarget`, before
  display-band smoothing. `LaserCueChoreography` advances eight held position cues only on detected
  attacks, with adaptive threshold, 320 ms retrigger guard, and 85 ms motion settling. No timed
  sine-wave aim remains in this style. Hits open the three-ray fan and accent beam width/brightness.
  Other styles, playback, PCM processing, and floor wash intensity coefficients are unchanged.
- `LaserCueChoreographyTest` feeds generated percussion through the actual `CalibratedPcmMeter`:
  10–11 known hits detected within 100 ms, with no between-hit detections. Tests also cover held
  positions, silence/steady input, entry/stale samples, reset, and retrigger limits. This is spectral
  attack detection, not an instrument classifier, tempo tracker, or measured speaker/video latency.
- Compile/unit/assemble passed in 21 seconds. Broker `evidence-2286950b7fb5` compile-unit-lint PASS
  with artifact integrity verified. `git diff --check` passed. Final debug APK SHA-256:
  `c233356b0a7c893c1f63b001ceca65b876dbdd96669a305231578b9c127b4e3a`.
- Physical Streamer update succeeded, full-screen Laser Light Show verified, app MediaSession PLAYING
  with error null. Continuous `/tmp/laser-hit-cues-live.mkv` (29.68 s) and MP4 remux
  `/tmp/laser-hit-cues-live.mp4` preserve live device audio through scrcpy audio duplication.
  Audio mean -9.5 dB, peak 0 dB; initial Opus packet parsing warning on extraction, subsequent audio
  decoded. Consecutive frames in `/tmp/laser-hit-cues-sequence.png` show distinct held patterns,
  cue transitions and stronger accents. No claim of listening to the clip or measured sustained FPS.
- Review change `tv-laser-hit-cues-20260905`: initial `review-cycle-fe77ab771fb9` plus independent
  Gemini `review-cycle-127c04927abd`. Clarifications `review-cycle-c5c27646fb77` and
  `review-cycle-e406915618d6` distinguish queued audio ingestion from per-render-frame advancement
  and fixed positive internal fixture indices. All reviewer evidence is source/text, not video.
- Scope limitation: Canvas compatibility rendering retains its previous scalar animation; this
  fix and physical acceptance evidence cover the Streamer's GLES path. Upstream feature coalescing,
  output latency and subjective musical quality are not proven by synthetic tests or contact sheets.

## September 5 — concert beams and continuous audio/video checks

- Scoped debug iteration, not a release or final owner aesthetic acceptance. Added smooth bounded
  GLES beam falloff, direct bass/snare-band onset aim offsets, and three narrow rays per overhead
  fixture. Low-level ray visibility uses a bounded square-root curve and near-white cores. Floor
  wash intensity is preserved. Canvas retains an approximate soft-cone treatment and scalar-audio rays.
- Final APK SHA-256: `67b449bf60e60081a2a6ab1fb469abc342e8e847e2fb210778d9f0b17be7a9ed`.
  Streamer update succeeded with data preserved; app MediaSession was PLAYING with no app error.
- `heavy-gradle :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`
  passed in 52 seconds. Tests cover same-clock audio aim changes, silence, bounds, simultaneous-hit
  headroom, low-level visibility, cue continuity and existing layout/audio contracts. `git diff --check` passed.
- Continuous physical-device recording, not just a still: `/tmp/laser-show-verified-with-audio.mkv`
  (29.63 seconds; H.264 and Opus), with portable remux `/tmp/laser-show-verified-with-audio.mp4`.
  Recorded using scrcpy playback audio duplication, keeping TV sound enabled. Audio mean -13.6 dB,
  peak approximately 0 dB. Initial Opus header parsing warning occurred during extraction; audio
  subsequently decoded. Consecutive half-second frames inspected in `/tmp/laser-show-verified-sequence.png`.
  Distinct narrow rays, changing aim/spread, and smooth overhead cones are visible. No claim of
  sample-accurate sync, measured sustained FPS, or subjective musical acceptance from frame inspection.
- Earlier 19.5-second audio capture `/tmp/concert-motion-with-audio.mkv` showed the rejected initial
  version. `/tmp/concert-after-with-audio.mkv` accidentally captured the dashboard after reinstall and
  is explicitly excluded from laser acceptance. The intermediate `/tmp/concert-final-lasers-with-audio.mkv`
  exposed dim cores, prompting the final visibility change above.
- Review control change `tv-concert-beams-20260905`: cycles `review-cycle-274f3fc4bf8a`,
  `review-cycle-9c46194e07a1`, and `review-cycle-203744804bff`, plus targeted Canvas clarification
  `review-cycle-6c5ad1e715e5`. Reviewers received sanitized source/text, NOT visual files.
  Saturation finding CONFIRMED and remediated; aspect-axis and narrow-ray-width findings NOT APPLICABLE
  against actual separate draw paths; onset bounds ALREADY MITIGATED by private clamped state.
  Final visual preference and perceived musical synchronization remain owner acceptance items.

Date: August 22–23, 2026

Status: Complete — physical navigation, visual, idle, accessibility, explicit Android low-RAM-branch,
and published Play-generated-split acceptance passed.

## Device and build

- Devices: Google TV Streamer (4K), wired Ethernet and ADB; Insignia Fire TV (Fire OS 7 /
  Android 9) with its physical remote.
- Package: `com.codeframe78.twentyfourseven.tv.debug`.
- Display: physical 3840×2160 output with a 1920×1080 logical override; Android scales the app
  surface to the connected 4K television.

## Passed observations

- Back returned focus to Google TV Home. The app's MediaSession remained `PLAYING` in the background.
- A warm relaunch restored the listener dashboard with StreamingSoundtracks.com exposed as live.
- Initial station-selector focus landed on StreamingSoundtracks.com. D-pad right moved focus to
  1980s.FM; D-pad left returned it to the first station.
- The hardware media play/pause key changed the active MediaSession from `PLAYING` to `PAUSED` and
  then back to `PLAYING`.
- The project owner physically tested playback recovery after an Ethernet disconnect/reconnect. D-pad focus, Back,
  the selector's hidden `↓ OPTIONS` route, media pause/resume, and sleep-timer expiry remained correct after recovery.
- The 1080p logical layout remained within its TV safe area on the 4K output path.
- TalkBack was temporarily enabled on the Streamer, bound successfully, and exposed the active station as
  “StreamingSoundtracks.com, live. Select to pause playback,” the inactive station actions, Now Playing
  artwork, and the current title/artist. The device was restored to its original state with TalkBack disabled.
- The existing `24Seven_API_35_16K` emulator booted with a 16,384-byte page size. The current debug APK
  installed and cold-launched successfully on that runtime.
- On the Fire TV, physical-remote double-Select opened a full-screen visualizer preview and one Back press
  returned to the Idle Visualizer picker. The same route passed on the Streamer; both MediaSessions remained
  `PLAYING` after return.
- Fire TV VoiceView audibly traversed the expected General Options and Idle Visualizer controls. Queue and history
  content behind the dialog was excluded from spoken traversal, and focus moved into the dialog rather than reading
  the active station underneath.
- The selected visualizer entered automatically on an untouched ten-minute Fire TV run while playback remained
  `PLAYING`. A prior Streamer run likewise reached automatic idle activation with `PLAYING` continuity.
- Physical review on both displays found the 1920×1080 logical layout within the 4K-output safe area, with no
  observed picker or dashboard edge clipping. The Streamer's richer visualizer tier was subsequently reviewed and
  accepted as smoother; Fire retained its 192 MB compatibility tier.
- A temporary read-only API 26 emulator booted with `ro.config.low_ram=true`. The debug Player launched,
  entered the Idle Screen picker, and rendered the constrained full-screen visualizer without fatal, GL,
  shader, or memory errors during sustained observation. This is an application low-RAM-branch validation,
  not a CDD-certified low-RAM hardware profile.
- The dedicated `24Seven.FM TV` Play application was created for
  `com.codeframe78.twentyfourseven.tv`. Its upload identity is retained only as an encrypted local recovery
  package outside this repository.
- The protected Linux signing helper built and independently verified the first signed AAB and release APK from
  committed revision `3c742c5f182e4bd0f8997d95feaf1cb5df620714`. The signed manifest declares package
  `com.codeframe78.twentyfourseven.tv`, version code `1`, version `0.1.0-tv-alpha01`, and that exact build
  revision. The release AAB signature verified. Play Console accepted and published that AAB to the Android-TV-only
  Internal testing track for Alpha Testers with its ReTrace mapping attachment. No signing secret, certificate
  fingerprint, or artifact hash is recorded here.
- The Play-delivered package, installed by `com.android.vending`, was verified on the Google TV Streamer and
  Dongle R 4K as version code `1` / `0.1.0-tv-alpha01` with base, ABI, language, and density splits. Each device
  launched the visible TV `MainActivity` and reported the app's live MediaSession in `PLAYING`; the project owner
  completed physical acceptance of both delivered builds.

This document combines debug-build device evidence, local signed-artifact evidence, and Play-delivered split
acceptance. It is not production-release evidence.

## Alpha 02 playback hotfix delivery — September 2, 2026

- The protected signer produced the Android TV-only version-code-3 bundle
  `0.1.0-tv-alpha02-hotfix1` from committed revision
  `77e789d88d21cb8f5b1c3855cbe9a9ee2a7bf80b`; its upload signature was verified before Play upload.
- Google Play accepted the bundle with its ReTrace mapping attachment, completed review, and deployed it through
  the active **TV Closed** track.
- The owner installed the Play-delivered hotfix on the Google TV Streamer and confirmed station playback now
  works as expected.

## Current-source addendum — August 29, 2026

- The current debug source expanded the picker from thirteen to fifteen fixed styles with `Psychedelic Scene`
  and `Lava Lamp`. On the Google TV Streamer, both rows were D-pad reachable and their double-Select previews
  opened full screen while live decoded audio played. The Lava selection now renders as a full-display,
  edge-to-edge molten reservoir rather than a narrow lamp-shaped inset, one giant blob, or a scatter of
  equally sized shapes. The final Streamer shader has no white-hot core or blast-style halo; slow buoyant
  wax separates from the continuous lower reservoir and can rejoin it. The visible `Lava Lamp Colors`
  General Options row opens four persisted colour palettes (Neon, Sunset, Arctic, and Acid), and Back
  returns to General Options. The former `DJ Console`
  display name now reads `DJ Soff Snep` and uses the owner-provided neon mark as a subtle local watermark over
  the live deck. This is Streamer-only debug evidence; no Play artifact, Fire TV, or release status is implied.
- Current local source additionally appends nine scenes, reaching 24 styles: `Signal Rain`, `Contour Islands`,
  `Ribbon Reactor`, `Prism Garden`, `Moonlit Tides`, `Pulse Quilt`, `Echo Reef`, `Velvet Vortex`, and
  `Lantern Drift`. On the Google TV Streamer debug APK, D-pad scrolling reached all 24 rows with the visible
  position thumb and `24 styles` cue; `Lantern Drift` opened from the final row through the double-Select
  full-screen route without a fatal, EGL, or GL-invalid log entry. The other eight added scenes have selector
  visibility evidence only, so this note makes no claim that all nine received individual full-screen acceptance.

## Commit preflight — August 30, 2026

- Fresh Google TV Streamer captures show the `DJ Soff Snep` neon mark as a deliberately whole-image,
  low-alpha watermark without a hard visible tile at the deck edge. The persisted enum key remains
  `DjConsole`, while every visible label resolves through `DJ Soff Snep`.
- A full-screen Lava Lamp capture shows the continuous lower wax reservoir, a visibly necked rising
  body, and a separate buoyant drop without a white-hot fireball core. Returning through Back exposes
  the visible `Lava Lamp Colors` row and the Neon, Sunset, Arctic, and Acid choices.
- Source inspection confirms each of the nine appended GLES scenes consumes decoded audio bands for
  geometry, opacity, width, motion, or colour. The Canvas fallback is unreachable for this app's
  `minSdk = 26` because the GLES path is selected from Android O onward.

## Google TV Streamer follow-up — August 30, 2026

- On the Google TV Streamer debug build, each appended scene (`Signal Rain`, `Contour Islands`,
  `Ribbon Reactor`, `Prism Garden`, `Moonlit Tides`, `Pulse Quilt`, `Echo Reef`, `Velvet Vortex`,
  and `Lantern Drift`) was individually opened through the D-pad double-Select full-screen route.
  Every scene rendered a distinct full-display composition, and one Back press returned to the
  Idle Visualizer selector after each preview.
- A post-pass device log scan contained no fatal exception or GL-invalid entry attributable to
  `com.codeframe78.twentyfourseven.tv.debug`. This is Google TV Streamer-only debug acceptance;
  Fire TV was deliberately not tested in this follow-up, and no release claim is implied.

## Google TV emulator renderer compatibility — August 30, 2026

- The current debug APK was installed on two Android 36 Google TV x86_64 emulator profiles using
  the SwiftShader GLES translator: 1920×1080 and 3840×2160. Each profile opened General Options,
  entered the Idle Visualizer picker, selected all 24 rows once, and reached the final `Lantern
  Drift` row. The captured log scans contained no fatal exception, EGL/GL error, shader-compilation
  error, or app-process crash.
- On both profiles, a Home action followed by an explicit Activity resume returned the Player
  activity without any of those renderer errors. The 4K profile was launched with 1 GB emulator
  memory and reported 988,164 kB total RAM.
- This is renderer compatibility and lifecycle evidence only. SwiftShader does not reproduce the
  vendor GPU drivers, firmware, thermal behavior, or frame pacing of Sony, TCL, Hisense, or other
  physical Android TV hardware; no cross-manufacturer performance conclusion is implied.

## Google TV Streamer Lava renderer pacing — August 30, 2026

- The debug APK was installed and exercised on the physical Google TV Streamer (Android 14), with
  the Player rendering at 1920×1080 and the display reporting 60.0 Hz. After 120 Lava Lamp preview
  callbacks, Device Status reported callback interval average/p95/maximum of 27.5/30.0/34.6 ms,
  CPU draw average/maximum of 1.1/3.7 ms, and zero callbacks beyond the refresh-aware 41.7 ms
  budget.
- The previous full-resolution Lava field measurement on the same device was 55.2/117.7/128.8 ms
  callback interval with 40 callbacks beyond that budget. The retained implementation renders the
  heavy Lava field into the existing capped scene buffer and presents it as one filtered full-frame
  sample; the public renderer remains the established `GLSurfaceView` path. A TextureView trial was
  not retained because it visibly corrupted Compose overlay text on this device.
- These values describe app render-callback pacing and CPU draw duration, not GPU frame time or a
  vendor-wide guarantee. The physical result closes the observed Lava Lamp pacing regression for
  this Google TV Streamer configuration; vendor-hardware coverage remains limited to the emulator
  compatibility evidence above. The refresh-aware budget uses the display-reported refresh rate as
  its pacing reference; it does not assert that HDMI/VRR behavior or vendor compositor timing is
  identical on other devices.

## Google TV Streamer catalog pass — August 30, 2026

- With 1980s.FM actively playing, the Streamer picker rendered each catalog preview selected during
  the current pass: Fluid Bloom, Signal Scope, Mirror Script, Orbit Bloom, Facet Forge, Vector
  Switchboard, Pulse Observatory, Chromafield, Light Trails, EQ Spectrum, Signal Deck, DJ Soff
  Snep, Psychedelic Scene, Lava Lamp, Harmonic Gate, Contour Islands, Woven Current, Prism Garden,
  Dusk Lattice, Beat Mosaic, Echo Reef, Magnetic Fields, and Lantern Drift. The device exposed no
  playback error category during the pass. Neon Kaleidoscope is an owner-protected fixture and was
  expressly left unchanged; it is not recharacterized by this catalog pass.
- The first brighter Magnetic Fields implementation rendered visibly but failed the Streamer timing
  check: 36.1 ms callback p95, 38.7 ms CPU-draw maximum, and three callbacks over the 41.7 ms
  display-derived budget. It was remediated by batching each field line into one filled-triangle
  mesh instead of issuing a draw for every short segment.
- The remediated debug APK was rebuilt, unit-tested, installed, and exercised with playback
  `PLAYING`. Device Status then recorded Magnetic Fields at 16.9 ms callback p95, 8.0 ms CPU-draw
  maximum, and zero callbacks over the 41.7 ms budget. This is a Google TV Streamer debug result;
  it is not a vendor-wide GPU performance guarantee.

## Google TV Streamer Laser Light Show replacement — September 5, 2026

Historical geometric-rig pass; superseded visually by the photographic-rig revision below.

- The debug APK was installed on the physical Google TV Streamer (Android 14 / API 34) while
  1980s.FM playback was active. `PulseQuilt` remained the persisted style key at ordinal 20, but
  the selector displayed `Laser Light Show`; the legacy preference value `PulseQuilt` was also
  exercised by the on-device instrumentation test and resolved to that relabeled style.
- Final revision: a full-width cross-braced truss and two towers support six overhead projector
  cabinets, with two additional floor cabinets. Each projector emits seven straight sweeping rays;
  layered beam geometry and floor spill provide depth without introducing another render pass.
  The Canvas gallery artwork uses the same physical-rig composition. No other scene renderer changed.
- The exact debug APK SHA-256 is
  `f4a2dd960ebeae8284d90db8ee45bb0b62ac547bf1cb0ee16573c177d02752a4`.
  It was installed successfully, launched, and opened through D-pad selection and double-Select.
  Two inspected full-screen captures (`/tmp/laser-final-fullscreen.png` and
  `/tmp/laser-final-motion.png`) show different fan angles with the same fixed equipment.
  MediaSession remained active and `PLAYING(3)`. A PID-scoped log scan found no fatal exception,
  EGL_BAD, GL_INVALID, out-of-memory, or ANR entry in the sampled output.
- Final compile, unit tests, lint, and Android-test compilation passed under broker evidence
  `evidence-61712cc54cc8`; `heavy-gradle :app:assembleDebug` also passed. The preceding concurrent
  validation attempt failed on the heavy-build lock (exit 75); that failure is retained with an
  environment-failure disposition, not relabeled as a pass.
- Initial independent reviews (`review-cycle-0f6f3503116d`) found an out-of-scope chooser preview
  regression. The original chooser was restored exactly. The blend-state concern was not applicable:
  scene dispatch resets additive blending every frame, and the laser restores that same default.
  Final source review `review-cycle-1425e72a70d3` completed with all three reviewers reporting no
  remaining source findings. Reviewers received source, not images;
  visual inspection was performed locally on actual Streamer captures, not attributed to reviewers.
- This pass establishes physical Streamer rendering, moving beam fans, playback continuity and
  persistence compatibility. SurfaceFlinger returned no usable scene latency samples, so the earlier
  revision's timing numbers are not reused. Sustained frame-rate and Homatics performance remain
  unmeasured; no locked-60-FPS or vendor-wide performance claim is made.

## Photographic-style laser rig — September 5, 2026

- Replaced the procedural equipment with generated photographic-style stage artwork: textured
  aluminum trusses, suspended projector housings, mounting hardware, and glass apertures. See
  [artwork provenance and exact prompt](laser-stage-artwork.md). Straight animated beam fans remain
  live geometry, aligned to the eight aperture positions; the artwork contains no baked beams.
- Bitmap decoding runs off the UI/GL threads. A completed decode uploads once to a cached GLES
  texture; normal rendering uses the full asset, compatibility/Canvas uses sample 2, and gallery
  thumbnails use sample 4. Loading failure is latched rather than retried every frame. Leaving the
  style cancels pending work and releases its texture.
- Final debug APK SHA-256:
  `01480c2dc04c277a55a386148b1cef644ebe39b055eef2ee8c291af2be3a5d94`.
  Compile, unit tests, lint, and Android-test compilation passed in `evidence-8a8bc406a27a`;
  `heavy-gradle :app:assembleDebug` passed separately. This exact APK was installed successfully
  on the physical Google TV Streamer and opened full screen using D-pad/Select.
- Codex inspected `/tmp/laser-realistic-final.png` and `/tmp/laser-realistic-motion.png`: realistic
  fixed equipment, lens-aligned beams, and different straight fan angles. MediaSession was active
  and `PLAYING(3)`; the sampled app-PID logs contained no fatal exception, EGL_BAD, GL_INVALID,
  out-of-memory, or ANR entry. The device was left on this scene.
- Final targeted review is tracked by `review-cycle-c2c5bd998a49`. Reviewers received source and
  textual validation evidence, not the images; visual inspection is not attributed to them.
  Claude confirmed remediation with one optional LOW transition-latency observation; Gemini
  reported no findings. DeepSeek's final review failed twice on the 300-second transport timeout.
  Final three-reviewer closure remains blocked; earlier review is not approval of later source.
- Rapid re-entry can wait behind one non-interruptible native bitmap decode on the dedicated
  executor. Cancelled queued tasks skip decoding; UI/GL do not block waiting for it. This is a
  low residual transition-latency risk, not a measured frame-rate regression. Sustained timing and
  Homatics performance remain unmeasured. No release or publication was performed.

## Outward laser sweep — September 5, 2026

- Removed the artificial floor endpoints and their impact glow. Beam endpoints now lie beyond
  the viewport at clip Y ±2.4; wider lateral fans and increasing beam width suggest travel toward
  the viewer. Side-edge exits are intentional, not floor termination. The photographic rig and
  aperture positions remain unchanged. Canvas uses offscreen endpoints and local `clipRect` bounds.
- Final APK SHA-256 `e6a2d5fd5d3ace408ecfb23cb2ca13db7258e193c48b081dad30d1b8ce252175`
  built and installed on the physical Google TV Streamer. Compile/unit/assemble passed, with
  compile/unit/lint/Android-test compilation independently recorded PASS in `evidence-753300a40703`.
- Codex inspected `/tmp/laser-outward-wait.png` and `/tmp/laser-outward-motion.png` at different
  sweep phases: beams continue across the pictured floor to screen boundaries without a horizontal
  cutoff. The chooser thumbnail was bounded. MediaSession was active/PLAYING; sampled PID 9108 logs
  showed no fatal exception, EGL_BAD, GL_INVALID, OOM, or ANR match. Full-screen laser was left open.
- All three source reviewers completed `review-cycle-4b1fbbe032f3` and clarification cycle
  `review-cycle-6f778829b2ce`. Pixel-metric normal math and default Canvas bounds were confirmed.
  Codex rejected mandatory bottom-only beam exits as contrary to the requested full-screen sweep;
  Canvas/GLES clock identity is out of scope (fallback preserves periodic integer harmonics).
  Reviewers received text evidence, not images. No sustained FPS/Homatics claim or release action.
# September 5 — slim foreground LED bar

- Owner requested smaller foreground housing, explicitly preserving wash intensity.
- Final debug APK SHA-256: `bd3136991443bcd17207dbe6d38827f5214e4e4f929e2b7b95bc1c9a7db7738a`.
- `heavy-gradle :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug`: PASS, 21 seconds.
- Layout test: 19 lenses mapped once, odd final bank handled, all lenses inside wash bases; 1 test, zero failures/errors.
- Slim layout compile/unit/lint/android-test compile evidence `evidence-81e4bac2786c`: PASS. Subsequent change only restored original alpha coefficients; final compile/unit/assemble re-run above.
- Physical Google TV Streamer: `adb install -r` Success, app MediaSession PLAYING with no app error, full-screen Laser Light Show screenshot inspected at `/tmp/laser-slim-fullscreen.png`. Slim bar and aligned aperture glows visible; six overhead fans retained. Device left on this visualization.
- Current asset and exact prompts: `docs/laser-stage-artwork.md`.
- Reviewer cycles: `review-cycle-dc8db92c79d3`, `review-cycle-fe55e78f579d`; targeted DeepSeek build closure `review-cycle-2d787cb81127` NO FINDINGS. Claude/Gemini final restoration NO FINDINGS. DeepSeek duplicate-lens test concern disputed with actual guarded candidate loop and passing test; reduced-alpha concern superseded by owner-directed restoration.
- Broker change `tv-laser-slim-20260905`, convergence `convergence-64bf3b67293e`, closure `closure-ba81d23f3db7` READY for this scoped change.
- Not a universal performance or aesthetic acceptance claim. No measured audio/video correlation or locked-FPS benchmark. Earlier choreography delta-time concerns remain outside this housing/intensity correction.


## Chromafield — Liquid Spectrum (September 6)

The owner-approved replacement gives Chromafield an original 20-second iridescent glass-fold loop, a new poster and description, and 9% maximum bass expansion (previously 4.5%). The `ChromaticTerrain` key is retained. Other styles and the corrected hot PCM state path are unchanged. This supersedes the earlier 16-second Chromafield media record. See `docs/abstract-video-integration/chromafield-liquid-spectrum.md` for current validation.

### 2026-09-06 Lava Lamp video replacement

Thirteen-lamp 1960s psychedelic lounge loops preserve all four palette choices and gently smoothed PCM response. See `abstract-video-integration/lava-lamp-video.md`. Compile/lint/104 unit tests and both physical Streamer tests passed; all four themes completed full loops with zero dropped frames. The stronger wall-motion refinement passed all four full loops with zero dropped frames and actual live PCM acceptance. All three reviewers returned no findings; closure-5c43e1b86a1a is READY.


### 2026-09-06 DJ Soff Snep realistic video replacement

Photographic music-reactive booth with the unchanged original logo. See `abstract-video-integration/dj-soff-snep-video.md`. Compile/lint/105 unit tests and both physical Streamer tests passed. Final DJ booth includes scrolling LCDs and blinking panel/mixer indicators. Full loop: 703 frames, zero drops, wrap observed. All three final reviewers returned no findings; closure-1420ab1dead4 is READY.


### 2026-09-06 DJ Soff Snep real-time rave lighting

Native PCM-driven beams, laser fans, haze, washes and kick backlights preserve the original booth and artwork. Soft-mask compositing replaces rejected hard cutouts. Compile/lint/112 unit tests and all three physical Streamer tests pass. The bounded hardware light layer measured p95 25 ms at the existing 30 Hz target; slower tail frames remain disclosed. All three final reviewers returned no findings; closure-e04f21df78b7 is READY. See `abstract-video-integration/dj-rave-lighting.md`.


### 2026-09-06 DJ laser projector anchoring

Four laser fans now share their origins with visible recessed projector lenses. Lower units have cabinet feet/contact shadows; upper units have truss yokes/clamps. Opaque equipment remains through haze and unlit silence. Original media and audio/controller paths are unchanged. Compile/lint/112 unit tests and all four physical tests pass; loop704 buffers/zero drops, seam6.98, fixture timing p95=25ms. Focused inactive-glow and opaque-mount bitmap checks also pass. All three final reviewers cleared the change; closure-549829f1b387 is READY. See `abstract-video-integration/dj-laser-projectors.md`.


### 2026-09-06 Fixed DJ booth camera

Removed only DjConsole's bass expansion coefficient (0.03 to0). Camera transform stays identity while native lights and deck/video motion react to audio. Normal/reduced unit framing assertions and960 physical quiet/build/drop identity checks passed; live PCM/video/light movement passed. Compile/lint/112 units and3 affected physical tests passed. All three reviewers returned no findings; closure-c626acf972dd is READY.


### 2026-09-06 DJ logo behind the lighting

Removed the logo-specific alpha erasure and radial light suppression so beams, haze and washes cross the original rear-wall artwork. Foreground deck fade, fixed camera, projector props and media remain unchanged. Compile/lint/112 units and 3 physical Streamer tests passed, including 960 identity-camera checks. Centered logo captures show changing light with retained contrast and no near-white clipping. All three closure reviewers returned no findings; closure-048c9298b462 is READY. See `abstract-video-integration/dj-wall-projection.md`.


2026-09-06 — Full-width DJ overhead trusses and logo clearance: three continuous photographic rows support all 28 LED pucks. Original logo scaled to 85% and lowered. Cached puck face removes per-frame clipping; mount aspect corrected. Compile/lint/112 units and four physical Streamer tests PASS (122.388s); complete loop 704 rendered buffers, zero drops. Timing p95/p99 27/31ms at 30Hz; raw new-style/legacy jank 44.32%/9.92%, not a zero-jank claim. Three independent closure reviews NO FINDINGS; change-75539020f0ae, closure-bb00cedd5b0e READY. Debug installed; no D-pad, signing or publication. See docs/abstract-video-integration/dj-photo-fixtures.md.


2026-09-06 — Live DJ deck realism: full-resolution rotating platter markings, PCM history displays, segmented band/energy meters and button glows; bounded moving material reflections share the existing native light surface. Static photograph/rig/logo and fixed camera preserved. Compile/lint/112 units PASS; five final Streamer tests PASS in126.429s; complete decoder loop706buffers/0drops. Timing p95/p99=30/34ms, rawjank42.15%/legacy60.22% disclosed, LOW residual performance risk. Three closure reviewers NO FINDINGS; change-0e645d9c3eb2, closure-c333065b5bb6 READY_WITH_RESIDUAL_RISK. Debug installed/reopened, no D-pad/publication. Details: docs/abstract-video-integration/dj-live-deck.md.
