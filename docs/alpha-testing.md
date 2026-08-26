# 24Seven.FM TV alpha testing

## Test scope

The TV alpha is an anonymous listener dashboard for StreamingSoundtracks.com, 1980s.FM, Adagio.FM, Death.FM, and Entranced.FM. Test with a D-pad remote on a landscape Android TV or Google TV device.

## Core checks

1. Open the app and confirm the last-selected station resumes automatically.
2. Move focus across all five selector icons and select a station; confirm its own live stream, palette, metadata, Queue, and Recently Played data appear.
3. Press D-pad Down from a focused selector, set and cancel the local sleep timer, then use Back to return to the dashboard.
4. Use the hardware media key to pause and resume playback; press Back to verify background MediaSession playback.
5. Disconnect and reconnect the network while playing; confirm recovery and usable focus/navigation.
6. Confirm long library labels truncate cleanly and no UI is clipped at the screen edges.
7. When artwork is briefly unavailable, confirm the selected-station faded-logo and animated-waveform fallback appears and resolves to live artwork.

## Feedback

Include the app version, TV/device model, Android version, selected station, exact D-pad steps, and whether the issue repeats. Do not include credentials, private identifiers, network addresses, cookies, or full network captures.
