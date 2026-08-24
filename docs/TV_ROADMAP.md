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

**Status: In progress.** Physical-TV evidence now covers D-pad selection, live session state for all five permitted
stations, real Ethernet recovery, and extended audible playback across normal track/metadata transitions. The only
remaining TV03 item is a safely observed playback-error/fallback case, if one can be induced without changing
production stream configuration.

## TV04 — Read-only library and device preferences

Add D-pad Queue/History, sleep timer, and privacy-safe local diagnostics. Do not expose authentication or mutations.

**Status: In progress.** All five stations' anonymous public Queue/History, D-pad sleep-timer set/cancel and expiry,
its process-bound policy, and privacy-safe local diagnostics have physical-TV evidence in
[tv04-queue-history-validation.md](tv04-queue-history-validation.md). The new General Options hierarchy now also
contains the sleep timer, idle-screen controls, and privacy-safe local diagnostics. Station selection remains on the
listener dashboard, while formal 10-minute idle and style-by-style physical-TV acceptance evidence remains under TV05.

The approved follow-on options and physical-acceptance sequence is recorded in
[TV_READINESS_ROADMAP.md](TV_READINESS_ROADMAP.md). In particular, station selection remains on the listener
dashboard rather than in General Options.

## TV05 — TV acceptance

Certify focus traversal, Back behavior, TalkBack, basic/Bluetooth remote input, emulator and physical TV playback, network recovery, 1080p/4K layout, low-memory behavior, 64-bit/16 KB packaging, and all five stations.

**Status: In progress.** The Google TV Streamer pass confirms D-pad focus, Back-to-home with
background playback, media-key pause/resume, Ethernet recovery, post-recovery hidden-options access, and
sleep-timer expiry; the API 35 16 KB emulator also cold-launches the debug app. Audible TalkBack, additional
physical remote, visual-layout, low-memory, and signed-release/Play-split evidence remain open.

The closure order and acceptance evidence for these rows are defined in
[TV_READINESS_ROADMAP.md](TV_READINESS_ROADMAP.md).

## TV06 — Play alpha delivery

After explicit approval: reconcile rights/privacy/content-rating/listing data against the exact signed build, create TV assets and tester cases, then complete internal/closed testing and Play-generated split verification.
