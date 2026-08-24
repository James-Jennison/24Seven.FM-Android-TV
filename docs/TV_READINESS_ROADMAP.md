# 24Seven.FM TV readiness roadmap

**Status:** In progress. R1 and the R2.1 controls are implemented in the debug TV build; their
broader idle, remote, accessibility, and release acceptance remains open. This roadmap sequences
the approved General Options direction with the remaining physical-device and release-acceptance
work. It does not authorize Play upload,
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
  Its 384 MB app-memory class automatically receives the constrained-device tier (30 fps render
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
- **VoiceView checks:** On the same Fire TV, dashboard queue/history panels no longer enter the
  spoken traversal. General Options opens as an accessibility dialog and transfers VoiceView
  focus to its pane and Idle Screen action rather than reading the underlying Live station.
- **Still open:** formal low-RAM classification evidence, safe playback-fallback observation, and
  release/Play-split verification.

## Product guardrails

- Keep the player anonymous, listener-first, and TV-native.
- Station selection remains on the Now Playing dashboard; it must not reappear in General Options.
- Do not add a graphic EQ, volume control, stream URL, account, network, or system-display setting.
  Those either duplicate Android TV controls or exceed the product boundary.
- Each preference must have a clear listener-facing effect, persist locally, be reachable by D-pad,
  and be reversible without changing playback or station selection.

## R1 — General Options foundations

**Goal:** Make General Options a small, predictable control surface.

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

- **Now Playing overlay:** Off, Brief on entry (default), or Always.
- **Reduce visual intensity:** reduces flashes and abrupt bursts while preserving measured audio
  response and the selected visualizer identity.
- **Idle delay:** retain the current 10-minute behavior and expose an explicit choice only if the
  existing fixed delay proves inadequate during acceptance. It must never be altered by focus
  traversal alone.

### R2.2 — Style behavior (after R2.1 acceptance)

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

Run these after each option milestone and record evidence in the existing validation documents.

1. **Navigation and layout** — verify safe-area layout at the 1080p logical viewport on the 4K
   Streamer, long labels, no edge clipping, selector scrolling, full-screen preview, and all
   thirteen visualizer styles.
2. **Remote coverage** — repeat the key paths using the Google TV remote and one separately
   identified Bluetooth/basic remote. Cover D-pad, Select, Back, Menu/Options where present, and
   media play/pause.
3. **Accessibility** — audibly verify TalkBack traversal from normal viewing distance, including
   the new options, visualizer picker, selected state, and action descriptions.
4. **Playback resilience** — retain the completed normal recovery evidence and capture a
   playback-error/fallback observation only if it can be induced without altering production stream
   configuration.
5. **Low-memory evidence** — use a device or emulator explicitly reporting low-RAM behavior;
   distinguish this from the existing 16 KB page-size emulator check.
6. **Idle acceptance** — prove the configured idle delay, cancellation/re-entry, and playback
   continuity without changing the selected station or sleep-timer state.

## R4 — Release artifact and Play readiness

This phase begins only with separate release approval.

1. Reconcile the exact release commit, release version, privacy/content-rating/listing inputs, and
   dedicated TV upload identity.
2. Build and validate the signed AAB with resource shrinking, R8, 64-bit, and 16 KB packaging
   checks.
3. Install the Play-generated split on a physical TV and repeat the core listener and options
   checks against that exact artifact.
4. Create tester cases and version-specific release notes only for the actual approved release
   candidate.

## Sequencing and completion

| Order | Milestone | Completion evidence |
| --- | --- | --- |
| 1 | R1 General Options foundations | Focused tests, clean static gate, Streamer D-pad/Back pass |
| 2 | R2.1 Overlay and comfort | Persistence tests and fixed/reduced visualizer physical pass |
| 3 | R2.2 Style behavior decision | Explicit product approval, then favorites/rotation acceptance |
| 4 | R3 Physical-TV closure | Updated TV03/TV05 validation evidence for every remaining row |
| 5 | R4 Release readiness | Exact signed AAB and Play-generated split evidence |

TV05 remains in progress until R3 and the physical portion of R4 are complete. TV06 remains
approval-gated and must not be treated as complete from debug-build evidence.
