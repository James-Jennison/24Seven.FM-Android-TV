# 24Seven.FM TV architecture

The application is a native Kotlin/Compose Android TV listener. It uses unidirectional data flow:

```text
Compose TV UI -> TvRadioViewModel -> repository interfaces
                                      ^
                                      |
                       station/public-data implementations

Compose TV UI -> TvRadioViewModel -> PlaybackController -> Media3 service
```

`TvRadioApp` renders immutable listener state and emits station-selection, playback, and local sleep-timer actions upward. `TvRadioViewModel` coordinates the selected station, public Now Playing state, and public Queue/History state. It does not access network or Media3 APIs directly.

`RadioPlaybackService` owns the sole ExoPlayer and MediaSession. Each station selection replaces the live stream with that station's approved source; only one station can play at a time. MediaSession state remains available to the system while the app is backgrounded.

Station behavior is keyed by `StationId`. The TV alpha reads only approved public stream, ICY metadata, artwork, Queue, and History sources. It has no account, Chat, request, browser, commerce, notification, or Cast surface.
