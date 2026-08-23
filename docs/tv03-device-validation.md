# TV03 physical-device validation

Date: August 22, 2026

Status: Partial — live playback selection, pause/resume, and real Ethernet recovery passed.

## Device and build

- Device: Google TV Streamer (4K), connected through wired Ethernet and ADB.
- Package: `com.codeframe78.twentyfourseven.tv.debug`.
- Surface: 1920×1080 logical rendering, displayed on the connected 4K television.

## Passed observations

- Cold launch returned to the listener dashboard with StreamingSoundtracks.com selected and playing.
- The D-pad focus landed on the station selector. The selected station exposed the accessibility action
  “Select to pause playback.”
- Pressing D-pad center on the live StreamingSoundtracks.com station changed its exposed state to
  “Select to start listening live”; pressing it again resumed playback.
- D-pad station selection was exercised across StreamingSoundtracks.com, 1980s.FM, Adagio.FM,
  Death.FM, and Entranced.FM. Each selected station reached the live selector state.
- Independent post-switch observations recorded live selector state for 1980s.FM and Adagio.FM;
  the completed sequence ended with Entranced.FM live.
- `dumpsys media_session` reported the app's active MediaSession in `PLAYING` state after the final
  station switch.
- A real wired-Ethernet interruption was performed, followed by a physical reconnection. The owner
  observed playback resume. ADB reconnected at the same endpoint, and the app's MediaSession again
  reported `PLAYING` with Entranced.FM exposed as live in the accessibility tree.

## Remaining TV03 acceptance

- Validate each station's audible output and metadata/artwork freshness with an observer at the TV.
- Capture an explicit playback-error/fallback case if one can be safely induced without changing
  production stream configuration.

This is debug-build device evidence only. It is not signed-release or Google Play delivery evidence.
