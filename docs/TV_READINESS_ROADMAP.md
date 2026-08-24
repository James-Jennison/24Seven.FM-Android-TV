# 24Seven.FM TV readiness roadmap

**Status:** In progress. R1 and the R2.1 controls are implemented in the debug TV build, and their
idle, remote, accessibility, and explicit low-RAM-branch acceptance is recorded below. Only the
external Play upload, listing/disclosure, and generated-split evidence remains open. This roadmap
does not authorize Play upload,
production configuration changes, account features, or stream changes.

## Current progress — 2026-08-23

- **Implemented:** General Options now groups Idle screen, Sleep timer, Accessibility & motion,
  and About & support. Device Status moved under About & support; station selection remains on the
  listener dashboard.
- **Implemented:** Now Playing overlay (Off, Brief on entry, Always) and Reduce visual intensity
  persist locally. Reduced intensity attenuates visual response only; decoded PCM remains the
  analysis source and calibrated meter inputs stay unmodified.
- **Streamer checks:** D-pad Down opened General Options; Select opened Accessibility & Motion;
  overlay cycling and reduced-intensity toggling rendered their selected state. The original
  Brief-overlay/full-intensity state was restored afterward. A playing session entered its idle
  visualizer after the configured ten-minute delay while its MediaSession remained `PLAYING`.
  From a full-screen preview, one Back press now returns visibly to the Idle Visualizer picker;
  the native GL view maps that key to the Compose preview-exit state instead of allowing it to
  dismiss the picker. The picker now opens visibly from General Options on one Select and labels
  the intentional double-Select gesture for full-screen preview. Reduced visual intensity also
  persisted across a Streamer relaunch, passed the deliberate preview/Back cycle, and was
  restored to its prior full-intensity state afterward. With reduced intensity enabled, a second
  unattended Streamer run entered the visualizer after the fixed ten-minute delay while the
  MediaSession remained `PLAYING`; the setting was then restored to full intensity.
- **Fire TV checks:** On the Insignia Fire TV (AFTDCT31, Fire OS 7 / Android 9), the physical
  remote's double-Select opened a full-screen preview and Back returned visibly to the picker.
  The strict Fire OS GLSL compiler is supported by normalizing shader source before compilation.
  Its verified 192 MB app-memory class automatically receives the constrained-device tier (30 fps render
  cadence and quarter-resolution feedback surfaces). Both physical viewers found the result
  slightly sluggish but acceptable; playback and the visualizer remained functional.
- **Two-device physical route check:** Using the physical remotes, the Google TV Streamer and
  Insignia Fire TV both opened the selected full-screen visualizer preview with double-Select and
  returned to the Idle Visualizer picker with one Back press. Post-return captures show the
  expected picker on both devices, and both MediaSessions remained `PLAYING`.
- **Two-device selector scroll check:** Physical D-pad navigation reached the thirteenth
  visualizer, DJ Console, on both devices. The picker scrolled it fully into view without edge
  clipping or activating a new selection; both MediaSessions remained `PLAYING`.
- **Two-device scroll-position cue:** The installed debug build renders a persistent muted track
  and mint position thumb beside the overflowing selector. Physical inspection confirmed it is
  visible on both devices and moves between the top and bottom selector positions.
- **Two-device picker dismissal:** One physical Back press from the picker returned both devices
  to General Options. The visible Sleep Timer value remained Off and both MediaSessions remained
  `PLAYING`.
- **Two-device options dismissal:** One further physical Back press returned both devices to the
  listener dashboard with 1980s.FM still selected. Both MediaSessions remained `PLAYING`.
- **Two-device Home/resume:** After a physical Home action, a ten-second background interval, and
  reopening the app, both devices returned to the 1980s.FM dashboard with the Player activity
  foreground and both MediaSessions still `PLAYING`.
- **Cross-device artwork treatment:** The artwork backdrop now uses the same clear 1.14× crop on
  every supported TV rather than an Android 12+-only blur effect. The debug build was installed
  on both devices and the Streamer result received physical approval.
- **Fire TV automatic idle acceptance:** With the Fire TV system screensaver disabled, an
  untouched ten-minute run entered the selected full-screen in-app visualizer while the Player
  remained foreground and its MediaSession remained `PLAYING`. A concurrent Streamer run is not
  counted: the device received HDMI-CEC active-source-loss events and paused before idle
  activation, without an app decoder, network, or sleep-timer error.
- **VoiceView checks:** On the same Fire TV, dashboard queue/history panels no longer enter the
  spoken traversal. General Options opens as an accessibility dialog and transfers VoiceView
  focus to its pane and Idle Screen action rather than reading the underlying Live station.
- **Runtime tier verification:** The Fire TV reports a 192 MB heap class; the Dongle R 4K reports
  256 MB; and the Google TV Streamer reports 384 MB. None reports Android's `isLowRamDevice`
  property, so the renderer uses a 256 MB compatibility boundary: Fire and the Dongle receive the
  constrained tier and Streamer the richer tier. The Streamer's richer tier was physically reviewed
  and approved as noticeably smoother.
- **Explicit low-RAM emulator acceptance:** A temporary, read-only API 26 emulator booted with
  `ro.config.low_ram=true`, the property Android uses for its low-RAM classification. The debug
  Player launched, navigated to the Idle Screen picker, and rendered the constrained full-screen
  visualizer for a sustained observation with no fatal, GL, shader, or memory error. This validates
  the application's low-RAM branch; it is not a CDD-certified hardware profile.
  Playback-error fallback cannot be physically observed today because every permitted
  station provides exactly one live stream; inducing it would require a prohibited production
  stream configuration change.
- **Still open:** Play upload, listing/disclosure completion, and Play-split verification.

## Product guardrails

- Keep the player anonymous, listener-first, and TV-native.
- Station selection remains on the Now Playing dashboard; it must not reappear in General Options.
- Do not add a graphic EQ, volume control, stream URL, account, network, or system-display setting.
  Those either duplicate Android TV controls or exceed the product boundary.
- Each preference must have a clear listener-facing effect, persist locally, be reachable by D-pad,
  and be reversible without changing playback or station selection.

## R1 — General Options foundations

**Goal:** Make General Options a small, predictable control surface.

**Status: Complete.** The D-pad/Back route, local sleep-timer behavior, and listener-dashboard
station-selection boundary passed focused checks and physical-TV acceptance.

### Intended top level

1. **Idle screen** — existing style picker and future visualizer behavior.
2. **Sleep timer** — existing local timer, set/cancel semantics unchanged.
3. **Accessibility & motion** — visual comfort controls only.
4. **About & support** — app information and read-only diagnostics.

**Implementation work**

- Preserve the current Down/Menu entry point, focus order, and Back behavior.
- Move the existing Device Status route under About & support without making it authoritative or
  exposing private data.
- Add version, privacy, and open-source-license entries where the platform supplies the source
  information locally.

**Acceptance gate**

- D-pad and Back traversal work from every row with no accidental sleep-timer selection.
- Existing playback, station selection, sleep-timer expiry, and idle-screen selection regressions
  are covered by focused tests and one physical-TV pass.

## R2 — Idle visualizer preferences

**Goal:** Let listeners tailor the idle experience without turning it into a control panel.

### R2.1 — Overlay and comfort (first)

**Status: Complete.** The overlay and reduced-intensity settings persist locally. Fixed-style
preview, ten-minute idle activation, and Back-to-selector behavior passed on the Streamer and
Fire TV without interrupting playback.

- **Now Playing overlay:** Off, Brief on entry (default), or Always.
- **Reduce visual intensity:** reduces flashes and abrupt bursts while preserving measured audio
  response and the selected visualizer identity.
- **Idle delay:** retain the current 10-minute behavior and expose an explicit choice only if the
  existing fixed delay proves inadequate during acceptance. It must never be altered by focus
  traversal alone.

### R2.2 — Style behavior (after R2.1 acceptance)

**Status: Deferred.** Favorites, rotation cadence, and random selection need an explicit product
decision; no unapproved style-behavior feature is implied by the completed fixed-style acceptance.

- **Fixed selected style** remains the default and preserves the existing picker.
- **Rotate favorites** rotates only styles the listener explicitly marks as favorites.
- **Random** is opt-in and uses the same allowed-style/favorites policy.

Favorites, rotation cadence, and randomization remain out of the first preference release unless
R2.1 passes its physical-TV acceptance with no idle/remote regressions.

**Acceptance gate**

- Settings persist across relaunch and are represented by immutable UI state.
- The active visualizer remains driven by decoded playback PCM; comfort mode adjusts presentation,
  not the audio-analysis source.
- Full-screen preview, idle activation, and Back-to-selector behavior are verified for the fixed
  and reduced-intensity paths.

## R3 — Physical-TV acceptance closure

**Status: Complete for the locally testable acceptance scope.** Evidence is recorded in
`tv03-device-validation.md`, `tv04-queue-history-validation.md`, and
`tv05-device-validation.md`.

1. **Navigation and layout — complete.** The 1080p logical viewport was reviewed on 4K output;
   dashboard and picker clipping were not observed, and D-pad scrolling reached all thirteen
   visualizer styles on both physical TVs.
2. **Remote coverage — complete.** The Google TV Streamer remote and a separate physical Fire TV
   remote covered D-pad, Select, Back, options, and media play/pause paths. The Fire remote was
   not classified as Bluetooth.
3. **Accessibility — complete.** Streamer TalkBack and Fire TV VoiceView traversed the listener
   dashboard and General Options/Idle Visualizer paths without exposing background Queue/History
   content behind the dialog.
4. **Playback resilience — complete within the permitted boundary.** Ethernet recovery and normal
   playback continuity passed. Alternate-stream fallback cannot be induced because every permitted
   station has one configured production stream; changing that configuration is prohibited.
5. **Low-memory evidence — complete.** A temporary API 26 emulator explicitly booted with
   `ro.config.low_ram=true` and sustained the constrained visualizer branch. This is application
   branch evidence, not a CDD-certified hardware profile.
6. **Idle acceptance — complete.** Both physical devices covered the configured ten-minute idle
   activation, full-screen preview/Back return, and playback continuity; the independent local
   sleep-timer expiry path also passed.

## R4 — Release artifact and Play readiness

**Status: In progress — listing/disclosure lifecycle.** Internal testing release
`1 (0.1.0-tv-alpha01)` is published to Alpha Testers. The exact local signed-artifact gate, signing
verification, R8/resource shrinking, 64-bit, and 16 KB checks passed, and Play accepted the AAB with
its ReTrace mapping attachment. The Play-generated split was installed and physically accepted on the
Google TV Streamer and Dongle R 4K. Listing/disclosure reconciliation remains incomplete.

1. **Exact-build record — complete.** The first artifact is version
   `0.1.0-tv-alpha01` / code `1` from revision `3c742c5f182e4bd0f8997d95feaf1cb5df620714`.
   Privacy and the objective app-content declarations are now saved in Play
   Console. Content rating, Data Safety, and store listing still require
   reconciliation in the external Play workflow against that exact artifact.
2. **Local artifact gate — complete.** The protected helper built and verified the signed AAB/APK
   with resource shrinking, R8, 64-bit, and 16 KB packaging checks; Play accepted the published
   internal-test AAB and its ReTrace mapping attachment.
3. **Play-generated split acceptance — complete.** The Internal testing AAB was installed from
   Google Play on the Google TV Streamer and Dongle R 4K. Both devices launched the delivered split,
   exposed the expected TV activity, and entered a live `PLAYING` MediaSession; physical acceptance
   was completed on both devices.
4. **Track material — partially complete.** The Alpha Testers list is assigned, the
   version-specific internal-test notes were published, and eight of eleven Play
   setup tasks are saved: privacy policy, app access, ads, target audience,
   government, financial, health, and category/contact. The entered support
   mailbox is provisioned and receipt-verified by the owner after its one-time
   managed-transport test. Content rating, Data Safety, and store listing remain
   open; tester-install evidence is complete.

## Sequencing and completion

| Order | Milestone | Completion evidence |
| --- | --- | --- |
| 1 | R1 General Options foundations | Complete — focused checks and Streamer D-pad/Back pass |
| 2 | R2.1 Overlay and comfort | Complete — persistence plus fixed/reduced visualizer physical pass |
| 3 | R2.2 Style behavior decision | Deferred pending explicit product approval |
| 4 | R3 Physical-TV closure | Complete for the permitted local scope; fallback configuration remains unchanged |
| 5 | R4 Release readiness | In progress — published and device-accepted Internal release; 8/11 setup tasks saved, with content rating, Data Safety, and store listing open |

TV05 is complete. TV06 remains approval-gated and must not be treated as complete from an
Internal-track publication and device acceptance alone.
