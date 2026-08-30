# TV08 first-use overlay validation

**Status:** Google TV Streamer visual acceptance in progress.

## Implemented contract

- Fresh debug-app data opens one translucent first-use overlay with **1980s.FM** selected.
- The station choice remains silent and local to the overlay until **Start listening**.
- **Not now** and Back do not persist that choice and ask again after a new launch.
- Each of the five station options uses its existing local logo, short name, and persistent selected checkmark.
- The selected and focused states are separate. The initial focus target is **Start listening**.

## Evidence

- `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:assembleDebug`, and `git diff --check` passed after the compact icon-card implementation.
- The debug APK was installed on the Google TV Streamer only after clearing that debug package's local data.
- A 1920x1080 Streamer capture confirms the complete hero, five icon choices, selected checkmark, and action row are visible in the safe area without vertical clipping.

## Remaining device checks

- Exercise D-pad focus traversal and station selection without playback.
- Confirm Start commits the selected station and begins playback; confirm Not now remains session-only.
