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
