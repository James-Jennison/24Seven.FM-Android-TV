# TV05 physical-device validation

Date: August 22, 2026

Status: Partial — primary navigation, TalkBack-visible semantics, background audio, and a 16 KB
debug-runtime launch passed.

## Device and build

- Device: Google TV Streamer (4K), wired Ethernet and ADB.
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
- The protected Linux signing helper's non-secret environment preflight passed. A signed release bundle was
  deliberately not built because the TV has no registered dedicated upload identity yet.

## Remaining TV05 acceptance

- Audibly confirm TalkBack's spoken traversal from the viewing position. The service and semantics were
  verified, but terminal inspection cannot prove the spoken output is understandable on the television.
- Basic and Bluetooth remote coverage beyond the current Google TV remote/ADB D-pad path. The device lists
  a Google TV Remote Keyboard with a Bluetooth address, but this pass did not establish which physical remote
  supplied the verified key events.
- Visual layout checks at 1080p and 4K from the viewing position, including no edge clipping.
- Low-memory behavior on a device reporting low-RAM status. This Streamer did not expose a configured
  low-RAM mode through `ro.config.low_ram`; the 16 KB emulator is a page-size check, not low-memory evidence.
- Signed-release AAB validation and Play-generated split installation after the dedicated TV upload identity
  is created and registered. The 16 KB debug-runtime launch does not prove signed bundle packaging.

This is debug-build device evidence only. It is not signed-release or Google Play delivery evidence.
