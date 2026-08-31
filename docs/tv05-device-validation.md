# TV05 physical-device validation

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
