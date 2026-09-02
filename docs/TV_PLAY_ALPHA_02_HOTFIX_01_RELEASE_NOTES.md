# 24Seven.FM TV Alpha 02 Hotfix 01

## Playback reliability hotfix

- Restores station playback for local app controls by granting the app's
  MediaSession controller the station-change command required to add the
  selected station's stream to the player.
- Shows a clear playback error if a controller connection fails or station
  switching is unavailable, rather than leaving the interface in a silent
  connecting state.

This hotfix does not expand the product scope or change the Android-TV-only
form-factor, station catalog, visualizer catalog, privacy posture, or Alpha 02
feature set.
