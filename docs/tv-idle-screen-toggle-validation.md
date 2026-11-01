# Automatic idle screen preference — validation

Date: 2026-09-05. Owner approved adding an On/Off setting; default On.
Scope: implementation plus required physical Google TV Streamer debug acceptance.
Signing, distribution, and publication remain separate. The earlier local-only closure
did not satisfy the owner's standing physical-device testing requirement.

Current acceptance: **PASS on the physical Google TV Streamer**. The restarted On
run completed: dashboard at 590 seconds, full-screen idle visualizer at 615.29 seconds,
with all 23 samples PLAYING, active, Awake, and app foreground. Off, manual previews,
and both process-restart preference checks also passed. The interrupted first On run
remains recorded separately. Final engineering closure: `closure-fb473a8ca030`, READY.

## Behavior

General Options → Idle Screen now initially focuses **Automatic idle screen: On / Off**.
Off preserves the dashboard during playback; On starts a fresh ten-minute inactivity
period after leaving the menu. The choice is stored in device-local SharedPreferences
and restored into initial ViewModel state. Manual full-screen previews remain available
with Off selected. Playback, sleep timer, and foreground keep-screen-on behavior are unchanged.

## Objective evidence

- `heavy-gradle :app:compileDebugKotlin`: PASS.
- `heavy-gradle :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin`:
  PASS, 81 unit tests, zero failures/errors/skips; Android lint passed.
- Timer tests exercise the real coroutine helper with virtual time: exactly ten minutes,
  cancellation before expiry, Off while active, fresh delay after re-enable, menu and
  paused-state suppression, and preview independence from automatic mode.
- ViewModel tests exercise default On, Off restoration before collection after ViewModel
  recreation, toggling back On, and no playback/sleep-timer calls from the setting.
- Local Google TV API 36 emulator: SharedPreferences instrumentation PASS for default On
  and both values restored after repository recreation. This is repository recreation,
  not an independently observed cold-process restart.
- Local emulator D-pad test: PASS for initial toggle focus, Off selection, Down into the
  visualizer list, double-Select preview callback with automatic idle still Off, Up back
  to the toggle, and On selection. The test uses the real Compose picker and synthetic
  remote keys; it is not physical-remote or live-audio acceptance.
- First picker test failed because its wall-clock sleep did not advance the Compose test
  scheduler through the opening-key guard. Corrected to advance the Compose clock; added
  a frame boundary before requesting focus on the newly enabled control. Rerun passed.
- Initial debug and instrumentation APK packaging passed and was tested on the local emulator; subsequent physical installation is recorded below.

- Screenshot inspected at 1920×1080: readable On/Off control and helper text, clear
  focus highlight, visible style list. Compose capture does not include the GL SurfaceView;
  it is layout evidence only. Local logs, screenshot, review packages, and SHA-256 manifest
  are retained in `build/validation/automatic-idle-20260905/`.

## Engineering ledger

Change: `tv-automatic-idle-off-20260905`.
Initial independent review: `review-cycle-f9c33a3a091e`.
Targeted closure review: `review-cycle-ec3343e07dab`: all three reviewers COMPLETE,
with no outstanding findings. Broker closure `closure-59b38a3e1041`: **READY**,
no outstanding reasons or accepted evidence risks, for the original local-only scope.
That historical result was insufficient for physical acceptance; see the follow-up below.
Claude and Gemini reported no initial findings; DeepSeek reported one LOW test-clock
finding, CONFIRMED and fixed with two subsequent passing emulator runs.

The first broker evidence cycle `evidence-cycle-997bbb8886b4` retained two failures:
missing SDK location in the service environment, and a duplicate concurrent Gradle check
rejected by the repository build lock. The external evidence profile now supplies the
installed SDK path and relies on the full unit suite for navigation tests rather than
launching them concurrently a second time. Both failures have separate ENVIRONMENT_FAILURE
dispositions. Replacement `evidence-cycle-555b4cfb99d5` passed compile, all unit tests,
lint, and instrumentation compilation with intact broker evidence artifacts.

Physical Google TV Streamer testing, including cold-process restart and affected playback
behavior, is required before feature completion. Fire TV and broader release acceptance
remain separate checks. Prior physical acceptance records do not validate this new preference. Unrelated build-script and social-asset work was preserved.


## Physical Google TV Streamer follow-up (2026-09-05)

Owner clarified that the Streamer is always an acceptance target. `AGENTS.md` now
requires routine debug build/install/launch and affected physical testing during
approved development without another approval checkpoint.

- Verified device identity: Google TV Streamer, Android 14.
- Installed the separate debug package; the pre-existing release package was preserved.
- Installed APK bytes match local SHA-256
  `cbc79cce0574c0832afcf39162e446d019852aa2fd973a0f49eee101aabacb6d`.
- Started playback using the first-use Start listening control. Debug MediaSession
  reported PLAYING; this is runtime state evidence, not a claim of human-audible QA.
- Physical screenshots show default On, then Off with initial focus on the control.
- D-pad Down and double-Select opened the actual full-screen GL preview while Off.
  Back returned to the picker. Captures include the actual Streamer GL output.
- Force-stopped only the debug package and relaunched it. Process ID changed from
  31010 to 31482; General Options read back Automatic idle screen: Off and Sleep Timer:
  Off. This confirms Off survives a real process restart, extending the emulator evidence.
- On also survived a force-stop/relaunch, with process ID changing from 31482 to
  32221 and General Options again showing automatic activation after ten minutes.
- Off timed observation: PASS. At 615.37 seconds after the final Back input, the
  physical screenshot still showed the listening dashboard. All 23 sampled observations
  reported PLAYING, active session, Awake, and debug-app foreground.
- On timed observation: INTERRUPTED, not PASS. Playback was PLAYING at 240.41
  seconds and PAUSED at 270.39 seconds, while the debug app stayed foreground and
  the device remained Awake. Android MediaSessionService sent pause to both the
  debug and release packages at 12:11:44 device time. Current HDMI active source
  differs from the Streamer; the precise pause trigger is not proven. No automatic
  activation result is claimed from this interrupted run.
- At that point, the remaining condition was a full ten-minute On run with the Streamer
  as the TV input and playback active. Another active HDMI source was not overridden.
  The production timeout was not accelerated or changed; no preference file was edited.
- Evidence directory: `build/validation/automatic-idle-20260905/streamer/`.


The earlier `READY` closure is historical local-only evidence. The acceptance record was
reopened as `tv-idle-off-physical-acceptance`; intermediate closure `closure-725ebeceac21`
was `NOT_READY` while physical testing remained incomplete. That finding is now resolved
with the completed retry and final closure `closure-fb473a8ca030`, READY. Routine Streamer
debug testing is standing-authorized; no new installation approval is required.


Owner-requested playback restart: debug MediaSession returned to PLAYING and General
Options still showed automatic idle enabled with Sleep Timer Off. A fresh unaccelerated
On observation was started; its artifacts are separate in `streamer/on-retry-1/` so the
interrupted first attempt remains intact.


### Completed On retry

- Owner-requested playback restart returned the debug package to PLAYING.
- No input during the fresh production-timeout window. Screenshot `on-retry-1/on-590.png`
  shows the dashboard before ten minutes; `on-retry-1/on-615.png` shows the actual
  full-screen GL idle visualizer afterward.
- All 23 retry samples were PLAYING, active, Awake, and debug-app foreground; final
  sample at 615.29 seconds. The separate Off run ended at 615.37 seconds with dashboard
  still visible and all 23 samples valid.
- `verify_final_observations.py` passed exact APK hash, complete sample coverage,
  sample spacing, playback/focus/wake prerequisites, and screenshot artifact checks.
  The screenshot outcomes were separately visually inspected.
- Back from automatic idle returned to the dashboard; debug playback remained PLAYING.
  Playback was left running as requested, with automatic idle On and Sleep Timer Off.
- No app source changes were needed during physical acceptance; existing code-review
  results remain applicable. Added physical evidence and disposition are durable in
  convergence `convergence-8e22678cbdd9` and final closure `closure-fb473a8ca030`.
- These are on-hardware ADB D-pad tests, compositor screenshots, and sampled playback
  state. No hand-held remote, human-audible sound, physical HDMI panel viewing, Fire TV,
  or Play publication claim is made. HDMI routing was not changed.


## Configurable start delay — 2026-11-01 (current task)

**Current bounded outcome: COMPLETE.** General Options → Idle Screen → Start delay
saves 1, 2, 5, 10 (default), 15 or 30 minutes. Select cycles values; changing the
preference starts a fresh inactivity window. Automatic Off/manual previews remain
independent. The debug app is updated on the Google TV Streamer, with original
Automatic On / 10-minute / Ion Transit settings restored after acceptance.

Source-to-outcome: app artifact commit `c4bc6a43e4012f1263c6431620fa7f14fd0b36b4`;
final tests `9fd9f852e08df2520ee7c58c73cceffbcc9e8d99` (production/build-input diff
empty). Installed and local APK SHA-256:
`b7c140abbb80372815aaac41499e444bf722af7ba5f46cfa6642cdad111bc722`.
Protected build and exact embedded-revision validator PASS. Compile/lint PASS;
169 unit tests and 7 physical instrumentation tests PASS, zero failures. Normal
Player checks PASS for all six D-pad choices, one-minute process persistence,
unaccelerated representative activation, native wake, Automatic Off and manual preview.
Other durations are not individually wall-clock timed; all six exact timer boundaries
are unit-tested. No human-audible acceptance is inferred.

Independent review: exact GLM-5.3 / MiniMax-M3.1-Flash / DeepSeek-V4.1-Flash-1017,
with Owner's current-chat panel exception. Final change
`tv-idle-start-delay-20261101-evidence`, final review `review-cycle-2af3b5e151eb`,
closure `closure-d2ef92c11642`: **READY / TECHNICALLY_READY**, no unresolved material
findings. Actual receipt-integrity evidence PASS; local execution and supplied-evidence
review remain distinct. Full current record: `work/idle-start-delay/outcome.json`.

Remaining outside this feature: early injected-key startup no-focused-window ANRs
remain an unattributed, unresolved diagnostic; investigate separately before broader
readiness claims. Longer wall-clock values, Owner UX acceptance, and any signing,
distribution/publication/release gates are not inferred. No release action occurred.
Existing unrelated dirty work is preserved. Earlier checkpoints below are attempt
history, superseded by this current summary; failed evidence remains intact.

Writer: Codex. Authority: Owner request to make start delay configurable, plus
standing debug installation/Streamer testing scope. Scope: saved 1, 2, 5, 10
(default), 15 and 30 minute choices in General Options → Idle Screen. Select
cycles choices; changing the preference restarts the idle countdown. Automatic
Off and manual previews remain independent. Existing dirty work is preserved.

Candidate: isolated acceptance snapshot `d1eef39039716d87d57b1b9aaf5dcef7c0f759ec`;
scoped delta, pre-change copies and source hashes are in `work/idle-start-delay/`.
Required gates: compile, units, lint, Android test compilation; independent review;
physical Streamer D-pad/persistence/real idle activation checks. No release or publication.

Current evidence: compile, lint, Android test compilation PASS; 169 unit tests,
zero failures/errors/skips. Initial protected build could not initialize the Gradle
configuration cache; retry with `--no-configuration-cache` passed. No cache keys were read.
Initial ADB authentication failed; Owner approved debugging and the reconnected target
identified as Google TV Streamer. Physical acceptance is pending candidate APK.

Owner approved the shared replacement panel for this change only, resolving the
repository's older exact Grok/Gemini requirement without changing global policy.
Frozen review: `tv-idle-start-delay-20261101`, cycle `review-cycle-eed479068c0c`,
exact GLM-5.3 / MiniMax-M3.1-Flash / DeepSeek-V4.1-Flash-1017 routes. The broker
requires source files on its own host; a separate sanitized input snapshot binds the
scoped Kotlin files and inspected AGENTS.md. It supplies source review only; local
build and physical evidence remain mandatory and are not replaced by that profile.

Checkpoint: implementation and local checks passed; APK build, physical checks and
review pending. Owner interventions: legitimate debugging approval and scoped review
panel decision; durations unknown. Actual provider charges and acceptance time unknown.

Review/validation continuation: six first-candidate physical instrumentation tests
passed (all preference repository tests, D-pad picker and catalog navigation). Review
identified an obsolete constant and integration-test coverage gap; removed the constant,
added regular imports and a real TvRadioApp Compose test. First integration compilation
failed on unnecessary assertion imports; corrected. Its first physical run failed because
UP NEXT is in the unmerged semantics tree; the query was corrected. These are test
construction failures, not evidence of a passing timer test. Failed log is preserved.
The Streamer switched to YouTube; input stopped and Owner authorized an uninterrupted
session. A target-verification helper now checks foreground package before every key.

Corrected integration retry proved pending preference-change timing through the real
Compose effect, then failed its wake assertion after Compose-only key injection. The
visualizer uses a native GL surface, so the next test injects the D-pad event through
Android instrumentation's real input path. No runtime code was changed for that test.
The failed retry remains `work/idle-start-delay/timer-integration-physical-retry.log`.

Normal Player follow-up: all six delay values were reached via native D-pad Select
with wrap-around (10→15→30→1→2→5→10). The 1-minute choice survived a force-stop
and launch; General Options visibly read “Ion Transit · starts after 1 minute.”
Early key injection during launch produced Android input-focus ANRs; waiting for
the rendered/focused dashboard allowed General Options to open normally. Those
failed attempts are not passing cold-launch evidence; root cause remains unproven.
A real unaccelerated one-minute normal-Player observation is in progress.

Unaccelerated normal-Player activation PASS: with 1 minute saved and Automatic On,
the dashboard was visible during capture at 40.000–42.316 seconds after leaving
General Options; Ion Transit was full-screen at 66.000–68.354 seconds. Debug
MediaSession was separately observed PLAYING during and after the check. The sampling
script's state parser did not recognize Android's PLAYING(3) spelling and recorded
unknown; those unknown fields are preserved, not rewritten. Real native D-pad Right
woke the dashboard without stopping playback. Installed APK SHA-256 matches local:
`b7c140abbb80372815aaac41499e444bf722af7ba5f46cfa6642cdad111bc722`.

The Compose integration test is now limited to actual keyed-effect preference changes
(pending and already-active cases). Native GL wake is covered separately by the above
normal-Player physical evidence; it is not inferred from Compose-only key injection.

Automatic Off normal-Player check PASS: saved delay remained 1 minute; the dashboard
remained visible beyond 67 seconds (run completed at 69.827 seconds), with the Off
control captured before dismissal. This extends the initial picker/coroutine tests.
Native manual-preview and final integration checks are the next acceptance steps.

Normal manual-preview check PASS with Automatic Off: double-Select on the unchanged
Ion Transit selection opened full-screen immediately; one Back returned to the picker.
Automatic On and the original 10-minute value were restored and visually verified.
Production/build inputs at test commit `9fd9f852e08df2520ee7c58c73cceffbcc9e8d99`
are identical to app artifact commit `c4bc6a43e4012f1263c6431620fa7f14fd0b36b4`
(`git diff` for main sources/build inputs is empty). Only instrumentation tests changed.
The final package's full source, generated diff and frozen candidate SHA-256 agree;
`work/idle-start-delay/package-consistency.json` retains that deterministic check.

The source-only broker profile submitted evidence cycle `evidence-cycle-c7eb5f3d700f`
with zero remote commands. This is explicitly not build/device evidence. Local JUnit
XML and lint reports are preserved with hashes under `work/idle-start-delay/local-reports/`;
local and physical gates remain mandatory outside the broker source-only profile.

Final focused physical instrumentation PASS: **7 tests, zero failures**, 184.003s,
on the Streamer. Exact frozen test source: `9fd9f852e08df2520ee7c58c73cceffbcc9e8d99`.
The real TvRadioApp effect restart passed for both pending and already-active preference
changes. Earlier failed test runs remain separate. Final closure review is
`review-cycle-70358c423652`, change `tv-idle-start-delay-20261101-v4`; waiting for
all three exact reviewers. Normal Player was restored after instrumentation with
`am start -W` and a rendered-window wait; its normal dashboard was visually verified.

Final feature review `review-cycle-70358c423652`: all three exact reviewers COMPLETE,
no blocking production defect. Individual longer wall-clock values remain explicitly
unverified; optional frame-tolerance improvements and the separate startup anomaly
are retained. Ledger closure `closure-0ab28f263404` correctly stayed NOT_READY because
the source-only profile had no deterministic check. It is preserved, not rewritten.

Evidence correction (agent rework; Owner intervention time not applicable/unknown):
a linked immutable snapshot, `tv-idle-start-delay-20261101-evidence`, keeps identical
feature source and the approved three-reviewer panel and adds a real receipt-integrity
command. It verifies saved/sanitized JUnit counts, lint success, the final 7-test device
log, scoped-source hashes, generated diff and installed-artifact receipt. It does not
rerun Gradle/device tests or claim independent execution of them. Original reports
remain local; transferred JUnit omits properties/system stdout/stderr. Final evidence
cycle: `evidence-cycle-5539f6155307`; profile-correction review: `review-cycle-4c2fa166edb6`.

Receipt integrity PASS on the broker: `evidence-cycle-5539f6155307`, evidence
`evidence-c198ff015745`, exit 0, intact log. Evidence-profile reviewers completed
`review-cycle-4c2fa166edb6`; platform review requested stronger source/APK linkage.
Codex disposition: CONFIRMED WITH NARROWER SCOPE (missing presentation of existing
guard evidence, not a code/binary defect). Created a clean read-only source worktree
at exact app commit c4bc6a4 and reran `scripts/verify-android-artifact.sh` against
the exact APK and Streamer: PASS. Explicit aapt manifest extraction confirms
BUILD_REVISION=c4bc6a43e4012f1263c6431620fa7f14fd0b36b4. Installed/local APK hashes
already match. Guard sources, actual output and metadata are retained under
`work/idle-start-delay/`; targeted clarification cycle `review-cycle-2af3b5e151eb`.
No signed-attestation or reproducible-rebuild claim was added.

Final ledger reconciliation: receipt evidence and all exact reviewer jobs passed.
A raw-finding fingerprint link was missing from the normalized material provenance
finding; added an immutable linked record and copied its verified disposition without
rewriting prior evidence. Final closure `closure-d2ef92c11642` is READY with no reasons.
