# 24Seven.FM TV product contract

## Product boundary

24Seven.FM TV is an independently versioned Android TV application. It is a separate GitHub repository, Android application ID, Play listing, release train, and tester program from the mobile Player.

The source baseline is Player Alpha 07 commit `2130defab51857856f98dbb734e98b09a52b4c6a`. It is provenance only; no future mobile change is inherited automatically.

## TV Alpha 01 scope

The first TV alpha is an anonymous listener experience:

- Five permitted stations, station switching that starts the selected live stream, a selected-station `Station - Live` stop toggle, MediaSession controls, Now Playing artwork/metadata, public read-only Queue/History, and sleep timer.
- D-pad-only, landscape, high-contrast 10-foot UI with visible focus and safe margins.
- Device-local age/terms acknowledgement and privacy-safe diagnostics.

It excludes sign-in, Favorites, Chat, requests, reporting/contact handoffs, browser flows, account management, notifications, commerce, and Cast. Queue/History display only public track titles; member identifiers and messages are not shown. A later milestone must explicitly authorize and certify each excluded surface before it is exposed.

## Release boundary

The app uses application ID `com.codeframe78.twentyfourseven.tv` and begins at version `0.1.0-tv-alpha01` / version code 1. Signing, Play Console creation, tester recruitment, and every public or testing-track delivery remain separately approval-gated.
