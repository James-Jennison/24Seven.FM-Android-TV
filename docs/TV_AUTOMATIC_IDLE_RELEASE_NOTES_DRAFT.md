# Automatic idle screen setting — draft release notes

Status: prepared for the next owner-approved TV release. No version assigned and no
signing, distribution, or publication performed for this change.

## Listener-facing changes

- Choose **General Options → Idle Screen → Automatic idle screen: Off** to keep the
  listening dashboard visible during playback.
- Automatic idle remains On by default and starts after ten minutes of inactivity.
- Your choice is saved across app restarts. Manual visualizer previews remain available
  when automatic idle is Off.

## Acceptance and release boundary

Unit tests, Android lint, emulator interaction tests, and physical Google TV Streamer
behavior checks passed. Physical checks covered both saved values after process restart,
manual preview while Off, and unaccelerated ten-minute Off and On behavior.
See [validation evidence](tv-idle-screen-toggle-validation.md) for exact scope and limitations.

These results establish feature behavior. Any newly packaged candidate must pass the
[artifact provenance gate](ANDROID_ARTIFACT_RELEASE_GATE.md) and applicable device/release
checks before being represented as an accepted release artifact. Signing and distribution
require a separate owner-approved release scope and version.
