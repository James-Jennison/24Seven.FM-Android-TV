## Configurable visualizer start delay — 2026-11-01

Owner-requested addition: General Options → Idle Screen → Start delay cycles through
1, 2, 5, 10 (default), 15 and 30 minutes and saves the choice locally. A change starts
a fresh inactivity period. Automatic Off and immediate manual preview are preserved.
**Complete for development/debug scope.** Compile/lint, 169 units and 7 physical
Streamer tests pass; normal one-minute On/Off, persistence, D-pad and manual-preview
checks pass. Review closure `closure-d2ef92c11642` is READY. Longer durations are
not individually wall-clock timed. See [current validation](tv-idle-screen-toggle-validation.md#configurable-start-delay--2026-11-01-current-task).
No signing, distribution or publication.

Separate deferred diagnostic: early ADB-injected input during cold launch produced
no-focused-window ANRs. Cause and baseline attribution remain unverified; this settings
closure does not resolve or accept the anomaly. Investigate before broader readiness.

## Signal Deck distinct console treatment — 2026-09-06

Owner requested a visual identity different from EQ Spectrum and DJ Soff Snep. Signal Deck now replaces its 32-band spectrum with a stereo balance carriage, phase-correlation needle, and PCM/STEREO/ATTACK lamps beneath the existing analog RMS meters. Exact pixel tests prove spectrum and stage choreography inputs cannot change its display. 160 unit tests and 9 physical native/live tests pass; accepted Lantern/Laser and all other scenes remain unchanged. Independent review and targeted clarification are complete; broker closure is READY. Normal Signal preview is left full-screen. See [Signal Deck evidence](abstract-video-integration/signal-deck-identity.md).

## Lantern Drift owner preview accepted — 2026-09-06

Owner feedback on the current installed version: “This is much more what I was looking for.” Photographed shades now pulse visibly on measured bass; floating bodies follow smoothly blended water on display cadence. Physical Streamer normal/reduced p95 is17.14/16.89ms, with12 distinct physical checks and158 unit tests passing. Accepted Laser/Signal drawings are preserved. Full-screen live Lantern is left running; all three reviewers completed closure with no unresolved substantive findings. Broker closure is READY_WITH_RESIDUAL_RISK for bounded, unmeasured first-entry photo decode latency. See [current Lantern evidence](abstract-video-integration/lantern-bass-water.md). Earlier entries below describe their own historical revisions.

# 24Seven.FM TV roadmap

## September 6 — Lantern Drift water motion and bass response

The owner accepted Laser Light Show, then requested visible water motion and lanterns that float with the water and react to bass. A native photographic water mesh now couples waves, lantern/raft bobbing and rocking, warm bass pulses and reflections while keeping the camera/shoreline fixed. Compile/lint, 157 unit tests and 11 physical Streamer checks pass; normal draw p95 is 34.89 ms. Installed artifact hashes match and the live full-screen Lantern preview is left available for owner observation. Independent review is pending. See [implementation and evidence](abstract-video-integration/lantern-bass-water.md).

## September 6 — realistic Lantern Drift, Signal Deck and Laser Light Show

The owner expanded these three styles to photographic backgrounds and native PCM-driven details like Lava Lamp and DJ Soff Snep. Lantern illumination/reflections and calibrated rack meters retain fixed framing. Laser Light Show fills the screen from four projectors and six upper-truss fixtures. Following owner feedback about repetitive motion, lasers now use measured bass/level rises and attacks instead of the ambient phase clock, with faster brightness response and stronger musical contrast. Compile, 152 unit tests, lint and eight physical native/live tests pass; laser draw p95 is 33.51 ms with zero decoder drops in the measured window. Current loop regressions, artifact readback and final reviewer closure remain pending. See [scene and validation evidence](abstract-video-integration/realistic-three.md).

## September 6 — shared music-reactive foundation

Shared-core MVP implementation and Neon Kaleidoscope / Emerald Resonance proof mappings pass 144 unit tests and three physical Streamer tests. Neon frame-interval p95 improved from about 191 ms to 32 ms through bounded offscreen rendering. Lantern Drift, Signal Deck and Laser Light Show designs remain unchanged. All three reviewers closed source/performance findings. Owner acceptance of the decode-clock prototype remains pending; full speaker/display alignment is not validated. See [Phase 1 evidence](shared-music-reactivity-phase1.md). No release or publication.

## September 6 — original abstract videos and live PCM state

Sixteen original abstract clips now retain the named visualization keys and use decoded PCM for whole-video response. The shared video freeze was traced to a redundant WhileSubscribed ViewModel wrapper and corrected by exposing the already-hot audio state directly. Compile, 103 unit tests, lint, and four physical Streamer tests pass. Final owner-operated normal-Player observation passed with continuously advancing decoder output. See [integration evidence](abstract-video-integration/README.md). No release or publication.

Technical closure: `closure-5d453e26cd5e` — READY; final-source and closure reviews completed by all three reviewers.

## September 5 — owner-directed clip restoration

Owner rejected the ten procedural experiments and requested returning to the uploaded
clips. The ten additive video visualizers are restored; existing original visualizers
and the accepted Laser Light Show remain unchanged. Procedural experiments are archived
outside Android source sets, not accepted or distributed features. See TV05 evidence.

## TV01 — Product and repository separation

Establish the TV application ID, product contract, independent roadmap, and disabled inherited mobile delivery paths.

**Status: Complete.** The Android TV application uses its own application ID, product contract,
repository, CI workflow, roadmap, and delivery boundary. Inherited mobile listener-account,
community, Cast, web-portal, and mobile release surfaces were removed from this repository.

## TV02 — Android TV platform foundation

Add the TV-only launcher manifest, banner/icon assets, landscape activity, Compose for TV root, and manifest validation.

**Status: Complete.** The API 34 physical-TV validation is recorded in
[tv02-device-validation.md](tv02-device-validation.md).

## TV03 — Listener-first playback

Deliver D-pad station selection, Now Playing, MediaSession playback controls, and safe live-radio behavior for all five permitted stations.

**Status: Complete.** Physical-TV evidence covers D-pad selection, live session state for all five permitted
stations, real Ethernet recovery, and extended audible playback across normal track/metadata transitions. The only
alternate-stream fallback is not eligible for a physical exercise: every permitted station has one
configured live stream, and inducing it would require a prohibited production configuration change.

## TV04 — Read-only library and device preferences

Add D-pad Queue/History, sleep timer, and privacy-safe local diagnostics. Do not expose authentication or mutations.

**Status: Complete.** All five stations' anonymous public Queue/History, D-pad sleep-timer set/cancel and expiry,
its process-bound policy, and privacy-safe local diagnostics have physical-TV evidence in
[tv04-queue-history-validation.md](tv04-queue-history-validation.md). The new General Options hierarchy now also
contains the sleep timer, idle-screen controls, and privacy-safe local diagnostics. Station selection remains on the
listener dashboard. Formal idle-screen and style-by-style physical-TV acceptance is tracked separately under TV05.

The current source enforces the documented process-bound timer policy by removing persisted timer restoration.
On Aug 28, 2026, the exact set-timer, force-stop, relaunch, and reopen-Sleep-Timer case passed on a supported
Google TV device using the current debug source: the relaunched picker was inactive and the media session exposed
no Cancel sleep timer action.

The approved follow-on options and physical-acceptance sequence is recorded in
[TV_READINESS_ROADMAP.md](TV_READINESS_ROADMAP.md). In particular, station selection remains on the listener
dashboard rather than in General Options.

### Automatic idle screen preference (2026-09-05)

Owner-approved local addition: General Options → Idle Screen → Automatic idle screen:
On / Off, default On. The saved Off choice keeps the dashboard visible during playback
while preserving manual previews. Local checks and Google TV Streamer debug acceptance
passed, including both process-restart preferences, manual preview, and real ten-minute
Off/On behavior. Evidence and the separate publication boundary are tracked in
[tv-idle-screen-toggle-validation.md](tv-idle-screen-toggle-validation.md).

## TV05 — TV acceptance

Certify focus traversal, Back behavior, TalkBack, basic/Bluetooth remote input, emulator and physical TV playback, network recovery, 1080p/4K layout, low-memory behavior, 64-bit/16 KB packaging, and all five stations.

**Status: Complete.** The Google TV Streamer and Insignia Fire TV passes cover D-pad focus,
Back-to-home with background playback, media-key pause/resume, Ethernet recovery, post-recovery
hidden-options access, sleep-timer expiry, VoiceView/TalkBack traversal, the additional physical Fire
remote, 1080p logical layout on 4K output, and automatic in-app idle activation. The API 35 16 KB
emulator also cold-launches the debug app, while a separate temporary emulator with
`ro.config.low_ram=true` validates the constrained visualizer branch. The signed AAB/APK gate passed and
Internal testing release `1 (0.1.0-tv-alpha01)` is published to Alpha Testers. Its Play-generated split
was installed and physically accepted on the Google TV Streamer and Dongle R 4K.

The closure order and acceptance evidence for these rows are defined in
[TV_READINESS_ROADMAP.md](TV_READINESS_ROADMAP.md).

### Protected core visualizers

The following five styles are protected product fixtures: **Neon Kaleidoscope**,
**EQ Spectrum**, **Signal Deck**, **DJ Soff Snep**, and **Lava Lamp**. They must
remain in the TV catalog and retain their distinct identities. Future catalog work
may refine or replace other styles, but every added style must be original,
visually distinct from the protected set and its peers, audio-responsive where
appropriate, and physically validated on supported TV hardware before release.

Their stable persistence keys are `NeonKaleidoscope`, `MinimalSpectrum`,
`VinylPulse`, `DjConsole`, and `LavaLamp`; those keys and their enum ordering must
not be changed because existing local selections depend on them.

### Catalog direction for the remaining styles

The Streamer sweep establishes the following direction. This is a catalog-quality
decision, not a removal from the current build: replacement work remains additive
until a reviewed successor is physically accepted.

| Decision | Styles | Direction |
| --- | --- | --- |
| Keep | Fluid Bloom, Signal Scope, Mirror Script, Orbit Bloom, Chromafield, Light Trails, Psychedelic Scene, Contour Islands, Prism Garden, Lantern Drift | Retain their concepts and names. They are legible at television distance and materially distinct from the protected core. |
| Rebuild in place | Vector Switchboard, Echo Reef | Preserve the concepts and names, but rebuild their sparse line treatments into fuller, more immediately legible scenes without duplicating Neon Kaleidoscope or Light Trails. |
| Replace | Glass Current → Facet Forge; Deep Warp → Pulse Observatory; Signal Rain → Harmonic Gate; Ribbon Reactor → Woven Current; Moonlit Tides → Dusk Lattice; Pulse Quilt → Laser Light Show; Velvet Vortex → Magnetic Fields | The original successors are implemented on their existing stable persistence keys. They remain pending physical Streamer acceptance before this catalog decision can close. |

Successors must not repeat the protected core's identity or become another generic
wave, particle, equalizer, vortex, or lava treatment. Each requires a unique
silhouette, an obvious audio mapping, bounded per-frame work, and Google TV
Streamer physical acceptance before entering the catalog.

September 5 Laser Light Show: after owner rejection of weak synchronization and recurring sway,
the Streamer GLES path now advances held lighting cues from unsmoothed PCM spectral attacks.
Continuous audio/video evidence, PCM-based tests and scope limitations are in `tv05-device-validation.md`;
subjective musical acceptance remains distinct from technical checks.
Final-source technical review reached READY (`closure-f7df71e8b490`) after the authorized
DeepSeek retry completed. A subsequent live Streamer recording retained the laser selection
and audio; it is observational evidence, not owner approval of the choreography.
Owner subsequently rejected the underlying eight-pose vocabulary. The approved continuous-parameter
replacement is installed on Streamer, with stronger overhead attack contrast and unchanged artwork/
floor intensity coefficients. New evidence and review scope are recorded at the top of
`tv05-device-validation.md`; the earlier READY result does not cover this replacement.
The replacement has its own READY technical closure (`closure-580c44c87d1d`), with live
44.6-second audio/video evidence. Owner judgment of musical feel remains separate.

## TV08 — First-use listener entry

**Status: In progress.** A translucent, station-first overlay now opens for fresh local TV installs,
defaults to 1980s.FM, and defers audio until the listener explicitly starts. Its current Streamer
evidence is recorded in [tv08-first-use-validation.md](tv08-first-use-validation.md).

## TV06 — Play alpha delivery

**Status: Complete.** The dedicated **TV Closed (Android TV)** track is active for its assigned tester cohorts
in 177 countries/regions. Alpha 02, version code `2` (`0.1.0-tv-alpha02`), cleared review and was released to
testers. Its playback hotfix, version code `3` (`0.1.0-tv-alpha02-hotfix1`) from revision
`77e789d88d21cb8f5b1c3855cbe9a9ee2a7bf80b`, subsequently cleared review, deployed through Google Play, and
was installed and accepted on the Google TV Streamer. The hotfix restores local station playback without changing
the Android-TV-only product scope or Alpha 02 feature set.


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


### 2026-09-06 Ion Transit original metallic tunnel

Owner-requested original GLES tunnel replaces visible Ion video rendering while retaining its stable key and reference media. Banked lower-left camera, reflective metallic panels, PCM-driven forward flight and continuous rotation, and four independently saved tunnel color families. Final Streamer shader acceptance PASS (~41fps,p95 27.31ms); D-pad colors and independent persistence PASS; compile/lint/162 units PASS. All three closure reviewers NO FINDINGS; `closure-cad242889327` READY. Normal full-screen preview left running. See `abstract-video-integration/ion-transit-native-tunnel.md`.


### 2026-09-07 Visualization catalog reduced to 22

The 12 owner-approved removals reduce the debug APK from about 506 MiB to 416 MiB. Stable saved-key fallbacks preserve installed selections; retained scenes and all Lava palettes remain unchanged. Compile, lint, 162 unit tests and physical Streamer catalog/migration/asset/D-pad acceptance PASS. Grok, Gemini and DeepSeek returned NO FINDINGS; `closure-ee157e10e2c9` is READY. See `abstract-video-integration/catalog-trim.md`.


### 2026-09-07 APK media optimization

All 22 visualizations and four Lava palettes retained. Re-encoding the four 1080p30 Lava loops and excluding unused Ion/Lantern reference MP4s reduces the debug APK from 415.82 MiB to 148.34 MiB. Compile/lint/163 units and four physical Streamer acceptance tests PASS; every Lava palette completed its loop with zero dropped frames. Installed artifact hashes match. Reviewer closure pending. See `abstract-video-integration/apk-media-optimization.md`.

###2026-09-07 Light Trails cinematic city redesign

Owner-requested Vegas/NewYork-style city plate with native racing light exposures and music-driven reflections. Dedicated freshness-checked motion fixes missing-audio drift. 165unit tests and physical livePCM/pause/resume/GLresourceacceptance pass; ~52fps on Streamer. Targeted review/evidence closure pending. See `docs/abstract-video-integration/light-trails-city.md`.


### 2026-09-07 Light Trails bass and skyline response

Revised Light Trails after owner feedback: distinct bass-note attacks and gaps, six music-driven rooftop searchlights, and moving district illumination. All 166 unit tests and physical Streamer acceptance pass at about 48 fps. Current technical closure is pending under `tv-light-trails-bass-sky-20260907`; earlier city acceptance is superseded. See `docs/abstract-video-integration/light-trails-city.md`.


### 2026-09-07 Light Trails automatic station colors

Owner-approved bass/sky presentation now follows active station branding automatically. Full-screen and picker paths prioritize playback station identity; palette changes preserve motion. All 167 unit tests and the physical five-palette/fallback acceptance pass. Final evidence PASS and all three closure reviewers report no residual findings. Final change `tv-light-trails-station-colors-final-20260907` closed **READY** as `closure-6f0d90d89d98`; physical palette synchronization rerun passed in 52.589 seconds. Earlier Light Trails review entries are superseded. See the Light Trails validation document.


### 2026-09-09 — Laser Light Show background trial discarded

Owner discarded the moving/still performer background trial. Restored the accepted original laser stage for every station, retaining music-reactive lasers and all prior unrelated visualization work. Generated trial media remains archived outside the app. See [trial record](abstract-video-integration/laser-1980s-band.md). No release.
