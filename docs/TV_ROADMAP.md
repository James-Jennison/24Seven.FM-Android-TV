# 24Seven.FM TV roadmap

## TV01 — Product and repository separation

Establish the TV application ID, product contract, independent roadmap, and disabled inherited mobile delivery paths.

**Status: Complete.** The Android TV application uses its own application ID, product contract,
repository, CI workflow, roadmap, and delivery boundary. Inherited mobile listener-account,
community, Cast, web-portal, and mobile release surfaces were removed from this repository.

## TV02 — Android TV platform foundation

Add the TV-only launcher manifest, banner/icon assets, landscape activity, Compose for TV root, and manifest validation.

**Status: Complete.** The API 34 physical-TV validation is recorded in
[tv02-device-validation.md](tv02-device-validation.md).

## TV03 — Listener-first playback

Deliver D-pad station selection, Now Playing, MediaSession playback controls, and safe live-radio behavior for all five permitted stations.

**Status: Complete.** Physical-TV evidence covers D-pad selection, live session state for all five permitted
stations, real Ethernet recovery, and extended audible playback across normal track/metadata transitions. The only
alternate-stream fallback is not eligible for a physical exercise: every permitted station has one
configured live stream, and inducing it would require a prohibited production configuration change.

## TV04 — Read-only library and device preferences

Add D-pad Queue/History, sleep timer, and privacy-safe local diagnostics. Do not expose authentication or mutations.

**Status: Complete.** All five stations' anonymous public Queue/History, D-pad sleep-timer set/cancel and expiry,
its process-bound policy, and privacy-safe local diagnostics have physical-TV evidence in
[tv04-queue-history-validation.md](tv04-queue-history-validation.md). The new General Options hierarchy now also
contains the sleep timer, idle-screen controls, and privacy-safe local diagnostics. Station selection remains on the
listener dashboard. Formal idle-screen and style-by-style physical-TV acceptance is tracked separately under TV05.

The approved follow-on options and physical-acceptance sequence is recorded in
[TV_READINESS_ROADMAP.md](TV_READINESS_ROADMAP.md). In particular, station selection remains on the listener
dashboard rather than in General Options.

## TV05 — TV acceptance

Certify focus traversal, Back behavior, TalkBack, basic/Bluetooth remote input, emulator and physical TV playback, network recovery, 1080p/4K layout, low-memory behavior, 64-bit/16 KB packaging, and all five stations.

**Status: Complete.** The Google TV Streamer and Insignia Fire TV passes cover D-pad focus,
Back-to-home with background playback, media-key pause/resume, Ethernet recovery, post-recovery
hidden-options access, sleep-timer expiry, VoiceView/TalkBack traversal, the additional physical Fire
remote, 1080p logical layout on 4K output, and automatic in-app idle activation. The API 35 16 KB
emulator also cold-launches the debug app, while a separate temporary emulator with
`ro.config.low_ram=true` validates the constrained visualizer branch. The signed AAB/APK gate passed and
Internal testing release `1 (0.1.0-tv-alpha01)` is published to Alpha Testers. Its Play-generated split
was installed and physically accepted on the Google TV Streamer and Dongle R 4K.

The closure order and acceptance evidence for these rows are defined in
[TV_READINESS_ROADMAP.md](TV_READINESS_ROADMAP.md).

## TV06 — Play alpha delivery

**Status: In progress.** Internal testing release `1 (0.1.0-tv-alpha01)` is published to Alpha Testers with
version-specific release notes. The Play-generated split is physically accepted on the Streamer and Dongle R 4K,
and ten of eleven setup tasks are saved. The provisioned support mailbox is receipt-verified; the conservative TV
Data Safety declaration, IARC 18+ rating, and **18 and over** audience gate are saved. Complete the store listing
against that exact artifact before completing the required test lifecycle.
