# TV05 physical-device validation

Date: August 22–23, 2026

Status: Partial — all available physical navigation, visual, idle, and accessibility acceptance passed;
only explicit Android low-RAM-device and release evidence remain.

## Device and build

- Devices: Google TV Streamer (4K), wired Ethernet and ADB; Insignia Fire TV (Fire OS 7 /
  Android 9) with its physical remote.
- Package: `com.codeframe78.twentyfourseven.tv.debug`.
- Display: physical 3840×2160 output with a 1920×1080 logical override; Android scales the app
  surface to the connected 4K television.

## Passed observations

- Back returned focus to Google TV Home. The app's MediaSession remained `PLAYING` in the background.
- A warm relaunch restored the listener dashboard with StreamingSoundtracks.com exposed as live.
- Initial station-selector focus landed on StreamingSoundtracks.com. D-pad right moved focus to
  1980s.FM; D-pad left returned it to the first station.
- The hardware media play/pause key changed the active MediaSession from `PLAYING` to `PAUSED` and
  then back to `PLAYING`.
- The project owner physically tested playback recovery after an Ethernet disconnect/reconnect. D-pad focus, Back,
  the selector's hidden `↓ OPTIONS` route, media pause/resume, and sleep-timer expiry remained correct after recovery.
- The 1080p logical layout remained within its TV safe area on the 4K output path.
- TalkBack was temporarily enabled on the Streamer, bound successfully, and exposed the active station as
  “StreamingSoundtracks.com, live. Select to pause playback,” the inactive station actions, Now Playing
  artwork, and the current title/artist. The device was restored to its original state with TalkBack disabled.
- The existing `24Seven_API_35_16K` emulator booted with a 16,384-byte page size. The current debug APK
  installed and cold-launched successfully on that runtime.
- On the Fire TV, physical-remote double-Select opened a full-screen visualizer preview and one Back press
  returned to the Idle Visualizer picker. The same route passed on the Streamer; both MediaSessions remained
  `PLAYING` after return.
- Fire TV VoiceView audibly traversed the expected General Options and Idle Visualizer controls. Queue and history
  content behind the dialog was excluded from spoken traversal, and focus moved into the dialog rather than reading
  the active station underneath.
- The selected visualizer entered automatically on an untouched ten-minute Fire TV run while playback remained
  `PLAYING`. A prior Streamer run likewise reached automatic idle activation with `PLAYING` continuity.
- Physical review on both displays found the 1920×1080 logical layout within the 4K-output safe area, with no
  observed picker or dashboard edge clipping. The Streamer's richer visualizer tier was subsequently reviewed and
  accepted as smoother; Fire retained its 192 MB compatibility tier.
- A temporary read-only API 26 emulator booted with `ro.config.low_ram=true`. The debug Player launched,
  entered the Idle Screen picker, and rendered the constrained full-screen visualizer without fatal, GL,
  shader, or memory errors during sustained observation. This is an application low-RAM-branch validation,
  not a CDD-certified low-RAM hardware profile.
- The protected Linux signing helper's non-secret environment preflight passed. A signed release bundle was
  deliberately not built because the TV has no registered dedicated upload identity yet.

## Remaining TV05 acceptance

- Signed-release AAB validation and Play-generated split installation after the dedicated TV upload identity
  is created and registered. The 16 KB debug-runtime launch does not prove signed bundle packaging.

This is debug-build device evidence only. It is not signed-release or Google Play delivery evidence.
