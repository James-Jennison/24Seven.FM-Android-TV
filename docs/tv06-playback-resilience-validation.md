# TV06 — Playback resilience tranche

**Status:** Code and focused device validation complete; live-audio switching remains a separate
physical acceptance gate. This record does not claim true audio crossfade or production release
readiness.

## Scope

- Service-owned playlist replacement boundary for every `setMediaItem` and `setMediaItems` player
  entry point exposed through the MediaSession.
- Explicit continuous-Icecast buffering profile: 12 s minimum, 45 s maximum, 1.5 s startup, and
  3 s rebuffer.
- Defensive cancellation/reset of outgoing metadata work before playlist replacement, and
  shutdown guards before the MediaSession and player are released.

## Validation

- `bash ./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest --no-daemon --console=plain`
  completed successfully.
- On the Google TV Streamer (`192.168.1.187:5555`), the focused
  `RadioPlaybackServiceTest` instrumentation suite passed: 5 tests in 61.959 seconds. It covers
  service recreation, sleep-timer behavior, plural playlist replacement, and a subsequent singular
  `setMediaItem` replacement without preparing a network stream.
- `git diff --check` completed cleanly.

## Reviewer convergence

- **Claude:** initially identified the missing singular `setMediaItem` interception. All three
  singular and all three plural player methods now pass through `StationSwitchCoordinator`; Claude
  closed the finding. The possible double metadata reset is already prevented by the existing
  same-media identity guard.
- **DeepSeek:** requested testable buffering-policy ownership and teardown defense. The
  `LiveIcecastLoadControl` profile now owns the exact values, and service shutdown blocks late
  switch/metadata callbacks before listener removal and resource release. DeepSeek closed all
  findings within this tranche.
- **Gemini:** reviewed the finalized service-only package through the standing browser fallback and
  reported no findings. It confirmed the single-player ownership, all six playlist-entry
  interception methods, explicit load-control injection, and shutdown ordering within scope.

## Deliberately not claimed

- Measured live-stream station-switch latency or audible continuity.
- Standby connections, dual-player mixing, or true crossfade.
- Any account, request, QR, Home, Assistant, DreamService, or visualizer work.
