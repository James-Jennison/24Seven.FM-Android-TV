# TV02 physical-device validation

**Completed:** August 20, 2026 at 11:48:45 AM PDT (UTC-07:00)
**Artifact:** local debug APK `0.1.0-tv-alpha01-debug`
**Device:** Dongle R 4K, Android API 34, Leanback-only, 1920 x 1080

## Evidence

- `:app:assembleDebug` succeeded and `TvRadioViewModelTest` passed (2 tests, 0 failures).
- The installed debug APK launched cold through its Leanback activity. The application surface was resumed and drawn
  at 1920 x 1080.
- D-pad focus moved into the station row, then from StreamingSoundtracks.com to 1980s.FM. Selecting 1980s.FM
  updated the displayed station without requiring authentication.
- The physical layout exposed a 1080p overlap between the station row and the Play/Stop controls. TV padding,
  spacing, and artwork size were reduced; the rebuilt APK showed all station chips and both controls fully visible.
- With explicit approval for live-stream validation, the D-pad Play action reached the active Media3 session for
  1980s.FM. The session reported `PLAYING`, local media audio attributes, and current station metadata.
- After Home returned the dongle to its launcher, that media session remained `PLAYING`. Returning to the app and
  activating D-pad Stop returned the UI to Idle and the session to `NONE`.

## Scope boundary

This is physical-device evidence for TV02 and a partial TV03 playback pass. It does not certify audio heard at the
display, Bluetooth remote behavior, recovery, 4K, low-memory behavior, accessibility, packaging, or the other four
permitted stations; those remain TV05 or later-gate work.
