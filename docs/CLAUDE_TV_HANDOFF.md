# 24Seven.FM TV handoff to Claude

Ownership transition recorded on November 1, 2026, under the Owner's direct instruction. **Claude is the assigned TV sole writer for the focused-window wait in acceptance scripts and its targeted validation, after Codex's final record and write-stop confirmation.** Codex performs only that final administrative recording and then stops TV writes. Product code, pairing, requests/authentication, LCARS and release work remain outside this assignment.

## Ownership and next authorized action

The Owner's direct authorization is:

> Transfer TV sole-writer ownership to Claude after Codex records the transition and stops writes. Authorize only the focused-window wait in acceptance scripts and its targeted validation. Keep product code, pairing, requests, LCARS, and release work outside scope. Preserve the benchmark snapshot and evidence.

`AGENTS.md` now records this scoped exception to its earlier Codex-only writing rule. The earlier draft is preserved in `work/idle-start-delay/claude-handoff-before-transfer.md`. This is a separately Owner-assigned task, with one writer; the completed Codex delay task stays closed. No review, privacy, device, credential, build, signing or release gate is waived.

The Owner relayed Claude's acknowledgment that it read the packet and had not changed files or begun work. No direct Claude correspondence, exact runtime/model identification, or instruction reload was verified by Codex. The Owner will convey the completed handoff/stop confirmation; Claude must read the updated local instructions before starting. This TV assignment does not grant writes to the mobile tree from this task.

**Next authorized action for Claude:** inspect the existing acceptance entrypoints, add a bounded wait for the intended app/activity's focused window before input, and run targeted validation. Preserve device data and unrelated apps, verify the exact artifact/device, and fail clearly without sending input if readiness is not reached. Do not force slower startup or run a broader ANR stress experiment as part of this assignment.

## Current TV baseline

| Item | Reconciled state |
| --- | --- |
| Repository | `/mnt/faststorage/24Seven.FM-Android-TV` |
| Branch | `codex/tv-automatic-idle-setting` |
| Local and freshly verified remote HEAD | `781421df7c79f83a27a388c1828f56206632d429` |
| Application checkpoint | `6c85f826ab53413b265cea28f5c601f81a04bdce` — visualizers, media, tests, docs and saved start delay |
| Guidance checkpoint | `781421df7c79f83a27a388c1828f56206632d429` — existing instructions and repository checkpoint |
| Remote branch | `origin/codex/tv-automatic-idle-setting` in `James-Jennison/24Seven.FM-Android-TV` |
| Working tree at original preparation entry | Clean |
| Transition record state | Updated `AGENTS.md` and this handoff are local and uncommitted; remote HEAD remains the checkpoint above |
| Local evidence | Ignored `/work/`, approximately 6 GiB at the commit checkpoint; retained on disk, not tracked in Git |

The accumulated app/media work and project documentation are committed and pushed. The current application/build inputs were checked against tested snapshot `9fd9f852e08df2520ee7c58c73cceffbcc9e8d99` and match. Commit IDs do not imply a new APK was installed from HEAD: the installed artifact retains its own identity below.

Preserve source, bundled media, documentation, clearly labeled rejected experiment archives, nested acceptance worktrees, original failures, and immutable broker evidence. Do not clean/reset/rebase or remove ignored evidence to simplify the handoff. Another machine receiving only Git will not receive `/work/`; any necessary evidence transfer needs a scoped, sanitized file inventory and verification.

## Installed artifact and applicable validation

| Item | Identity or result |
| --- | --- |
| App build commit embedded in APK | `c4bc6a43e4012f1263c6431620fa7f14fd0b36b4` |
| Final test source commit | `9fd9f852e08df2520ee7c58c73cceffbcc9e8d99` |
| Production/build-input difference between those snapshots | Empty; the descendant changes tests only |
| Debug package | `com.codeframe78.twentyfourseven.tv.debug` |
| Local and freshly read installed APK SHA-256 | `b7c140abbb80372815aaac41499e444bf722af7ba5f46cfa6642cdad111bc722` |
| Physical target | Verified Google TV Streamer; read-only package/hash check matched during preparation |
| Test APK SHA-256 | `e722b6e45213de06dbea1f05848228bf7f877f5a04378371356e221f775cf56d` |
| Recorded compile, lint and Android test compilation | PASS |
| Recorded unit suite | 169 tests, zero failures/errors/skips |
| Recorded focused physical instrumentation | Seven tests, zero failures, 184.003 seconds |

The existing protected build rejected dirty/untracked source, embedded its exact HEAD revision, and passed the repository artifact validator. A separate clean worktree at the APK's exact build commit passed the validator against that APK and the Streamer. Explicit manifest extraction and installed-byte hash comparison support the linkage. This is the documented local build/provenance chain, not a signed attestation or reproducible-build certification.

Preparation rechecked the local APK hash, installed debug APK hash, remote HEAD, source equivalence, and ledger readiness. It did **not** rebuild, reinstall, launch, or rerun behavioral tests. Earlier behavioral results remain bound to their recorded candidate and conditions.

The completed setting is General Options → Idle Screen → Start delay: **1, 2, 5, 10 (default), 15 or 30 minutes**, saved locally. Select cycles values. A changed setting starts a fresh inactivity window; Automatic Off and immediate manual preview remain independent.

Recorded normal-Player checks cover all six D-pad choices, one-minute persistence across process restart, dashboard at 40–42.316 seconds and full-screen visualizer at 66–68.354 seconds with one minute selected, native Right wake while playback stayed PLAYING, Automatic Off beyond 67 seconds, and immediate manual preview while Off. Automatic On, 10 minutes and the original Ion Transit style were restored. Other delays were not individually timed against a physical wall clock; exact coroutine boundaries for every value passed unit tests. No human-audible or new Owner UX acceptance is inferred.

### Evidence and review pointers

Start with `work/idle-start-delay/outcome.json` and [the current validation record](tv-idle-screen-toggle-validation.md). The broader source history is in [TV_ROADMAP.md](TV_ROADMAP.md), [TV_READINESS_ROADMAP.md](TV_READINESS_ROADMAP.md), [the visualizer integration records](abstract-video-integration/README.md), and [shared music reactivity evidence](shared-music-reactivity-phase1.md). Earlier milestone entries retain their own scope; their dates and partial states do not reopen the completed delay task.

- Final change: `tv-idle-start-delay-20261101-evidence`.
- Original closure: `closure-d2ef92c11642`, **READY / TECHNICALLY_READY / REVIEW_APPROVED**.
- Fresh readiness snapshot during preparation: `closure-21c03fbb31e1`, same readiness, no unresolved material findings. Owner acceptance remains not recorded; release eligibility remains not granted.
- Final provenance review: `review-cycle-2af3b5e151eb`; evidence-profile review: `review-cycle-4c2fa166edb6`.
- Earlier cycles retained: `review-cycle-eed479068c0c`, `review-cycle-e143761d7d3a`, `review-cycle-70358c423652`.
- Receipt integrity: `evidence-cycle-5539f6155307`, actual command PASS. It checks saved source/test/artifact receipts; it does not rerun Gradle or device tests on the broker.
- Local files: `final-physical-tests.log`, `installed-artifact.json`, `artifact-provenance-verified.log`, `apk-embedded-revision.txt`, `package-consistency.json`, `final-source.patch`, original/sanitized report directories, and raw reviewer responses under `work/idle-start-delay/`.
- This preparation's checks and attribution: `work/idle-start-delay/handoff-verification.json` and `handoff-review-readiness.json`.

The exact closing panel was Synthetic GLM-5.3, Together MiniMax-M3.1-Flash and Synthetic DeepSeek-V4.1-Flash-1017. The Owner's replacement-panel exception applied to this delay change. It is not blanket permission for future TV changes. Before substantive future work, reconcile the repository's older named roster with the current global policy and live broker contract. Keep explicit selection, exact identities, contributor exclusions and required review counts; do not silently substitute or repeat already-completed reviews.

## Preserved product and operational boundaries

The app stays native, without a WebView. Compose receives immutable state and emits actions; ViewModels depend on repository interfaces. Station capabilities and verified/permitted stream contracts stay authoritative. The app consumes station playback/public metadata and must not become an administrative writer of Now Playing, incidents, relay health or listener telemetry.

Preserve the protected visualizers **Neon Kaleidoscope, EQ Spectrum, Signal Deck, DJ Soff Snep and Lava Lamp**, their stable persistence keys/order, and the other accepted scene behavior. Do not reinterpret a historical rejected experiment as an accepted feature. Check each scene's own evidence before changing it.

Use the protected build entrypoints and `heavy-gradle` for memory-intensive Android work. Start code changes with Kotlin compilation; run affected tests and required checks. Do not run routine `gradlew clean`, reinstall unaffected SDK packages, or repeat unaffected successful validators. Bind builds and acceptance to exact source, explicit settings and artifact hashes.

Physical Google TV Streamer acceptance remains required for TV behavior changes. Once development is authorized, routine debug build/install/launch and affected testing are already allowed; preserve device data and unrelated apps. Required human actions remain human gates. Signing, distribution, production stream settings, background-media changes and external publication remain separately approved release work. Device testing does not grant those approvals.

Existing approved provider routes, allowance/credit limits and contributor/reviewer separation remain applicable. No new credentials, provider/subscription changes, purchases, station correspondence or deployment are authorized by preparation.

Keep existing browser/desktop and VM boundaries. Website work has a separate source of truth in `/mnt/faststorage/24Seven.FM-Player/AGENTS.md`, section Player project site deployment; it does not become authorized by this handoff or by a possible future QR design.

## Startup diagnostic and the authorized script correction

Early ADB-injected input during cold launch previously produced no-focused-window ANRs. The Owner subsequently relayed Claude's diagnostic report below. It is retained as reported evidence; Codex has not independently rebuilt the reltest snapshot, rerun its timing tests, or reproduced its cleanup.

| Reported observation | Result and limit |
| --- | --- |
| Benchmark source | Snapshot of HEAD `781421d` on GTHost, with the release variant changed to a `.reltest` package ID and debug-keystore signing |
| Reported APK hash | `1e11518f…5618` only; obtain the full digest and exact snapshot/build modifications before reusing it as acceptance identity |
| Release variant as installed | 1.22–1.26 seconds |
| Release variant after profile compilation | 0.94–0.95 seconds |
| Earlier debug timing | About 5.0 seconds |
| Early-key retry | One immediate D-pad key; about 5.0-second launch; no new ANR |
| Focus-wait control | No ANR, but the early-key condition also passed, so prevention was not demonstrated |
| Original failures | Three ANRs on reported 7.4–7.5-second launches; baseline mechanism remains partly confirmed |
| Cleanup reported by Claude | Only temporary `…tv.reltest` removed; existing release/debug APKs reported unchanged |
| Compilation state | Earlier debug dexopt change is retained: reported `run-from-apk` before and `verify` now. Do not describe all device state as untouched or silently restore it |
| Preserved remote snapshot | `gthost-build01:/home/jjennison/relbench-781421df7c79f83a27a388c1828f56206632d429` |

The copy initially lacked the executable bit on `gradlew`; Claude reported rerunning through bash. That does not establish a source-tree executable-bit change. Preserve the snapshot and its evidence; deletion is not authorized.

These measurements demonstrate fast startup for that modified release-test candidate under the reported conditions. They do not isolate debug compilation as the sole cause or establish a deterministic ANR reproduction. No product startup change is authorized. Keep the ANR unattributed; describe focused-window waiting as acceptance automation readiness handling, not as a proven product ANR fix.

Targeted validation should establish that the script waits for the correct focused window before sending input, rejects the wrong target, and times out with a clear failure without sending input when readiness is not reached. Exercise the normal verified Streamer path and retain actual results against script/source/artifact identities. Do not repeat unrelated successful suites, install a release-test package merely to remeasure startup, change compilation state, or force an ANR to claim validation. Any further diagnostic or implementation scope needs Owner direction.

## Mobile repository checkpoint

The Owner reports Claude owns `/mnt/faststorage/24Seven.FM-Player` in its mobile task. This bounded TV assignment does not extend that separate scope into this task. Read-only inspection during preparation observed branch `codex/tablet-player-visual-refresh`, HEAD `54db7a813e1f2b54420128b0b5c18fa097d9593a`, with these existing changes:

- Modified `app/src/main/java/com/codeframe78/twentyfourseven/player/domain/CommunityNotificationRepository.kt` and `TrackRequestStatus.kt`.
- Modified `app/src/test/java/com/codeframe78/twentyfourseven/player/domain/ChatMentionTrackerTest.kt` and `TrackRequestAvailabilityResolverTest.kt`.
- Untracked `privacy-site/assets/project/` and `privacy-site/privacy/`.

These belong to the mobile work in flight. They were not edited, staged, built, tested, committed or reset here. Recheck state with its writer before cross-repository work; the branch name alone does not establish current agent ownership. Mobile and TV remain separate repositories, application identities and release processes.

## Phone and TV request investigation

### Listener suggestions and the requested experience

The side chat received listener feedback suggesting a small LCARS Easter egg and easier song requests without typing a login on the TV. The clarified request idea was to scan a TV QR code, sign in on the phone, then either request there or authorize TV-side library search and explicit submission. The example spoken query was “play 2001 a space odyssey track 1.” These are suggestions and feasibility evidence, not implementation approval. The LCARS idea remains separate; no assets or code from another LCARS/PADD project were accessed or transferred for this packet.

### Current source and important reuse limits

The side investigation found mobile title/album/artist/genre search, album/track results, request confirmation/submission, favorites, chat and station-specific authentication. Request/auth implementations live in `:app`, not an already reusable shared library. Coordinate extraction or adaptation with the mobile writer; do not assume drop-in reuse.

Selected facts were rechecked here: the main mobile manifest has no ACTION_VIEW/BROWSABLE entry point for requests or pairing; `MainActivity.onNewIntent` handles internal Chat station selection. `NetworkSongRequestRepository.confirmRequest` validates station/account confirmation context, then directly calls `remote.submit`. `MainViewModel.confirmSongRequest` first checks `communitySafety.canContributeCommunityContent`. A future companion handler must preserve that safety gate rather than call the repository directly. Current source wins over older M33 prose describing prechecks or reloads that no longer occur.

The side scan found no phone/TV pairing, NSD/custom command transport, credential delegation, or request/account-linking Cast message channel in either app. Existing Cast is playback-oriented. Both station models agree on `sst`, `1980s`, `afm`, `dfm`, `efm` and aliases. These negative transport findings are carried from the side investigation, not a new exhaustive scan in this preparation.

TV is currently anonymous/listener-first and has no auth/request repositories. [PRIVACY.md](../PRIVACY.md) excludes account, member, chat and request data. Adding an authenticated TV/companion request flow changes those contracts and requires explicit approved scope and appropriate review.

### Request correctness and session boundaries

A request action changes station state. Never prefetch or open a request-action URL as a harmless catalog link. Confirmation must identify station, account and actual selected track, retain the community safety gate, and respect server eligibility/cooldowns and public attribution. Only SST optional request messages are explicitly supported by the current transport; do not generalize them to every station.

Do not automatically retry a submission. An uncertain result stays uncertain and directs the listener to Queue. Existing per-station mutex/no-retry behavior is not distributed or crash-proof idempotency. A future protocol needs operation identity, duplicate suppression and ambiguous-outcome handling, without promising exactly-once station execution. Disconnect/restart must not silently queue and retry a mutation later.

Mobile sessions are locally encrypted and station/origin isolated; backup and device transfer exclusions were checked. Local logout must not be described as revoking an exported session copy elsewhere. Neither app globally permits cleartext traffic. Do not broadly enable HTTP, disable TLS checks, copy cookies or turn the phone into a generic authenticated URL-fetch proxy as a shortcut.

Useful mobile source pointers under `/mnt/faststorage/24Seven.FM-Player/`:

| Contract or path | Source |
| --- | --- |
| Request orchestration | `app/src/main/java/com/codeframe78/twentyfourseven/player/data/NetworkSongRequestRepository.kt`, especially `confirmRequest` around line 166 |
| Safety and confirmation | `app/src/main/java/com/codeframe78/twentyfourseven/player/ui/MainViewModel.kt`, `confirmSongRequest` around line 524 |
| Station mutation transport | `app/src/main/java/com/codeframe78/twentyfourseven/player/data/StationSongRequestRemoteDataSource.kt` |
| Session isolation/storage | `data/AuthSessionStore.kt`, `data/StationAuthSessionCoordinator.kt`, `data/NetworkAuthRepository.kt` under the same package root |
| Interfaces | `domain/SongRequestRepository.kt`, `domain/AuthRepository.kt`, `domain/CommunitySafetyRepository.kt` |
| Existing external entry points | `app/src/main/AndroidManifest.xml`, `MainActivity.kt` around line 280 |
| Existing Cast | `playback/CastPlaybackCoordinator.kt` and `PlayerCastOptionsProvider.kt` under the package root |

### Bounded anonymous station checks from the side chat

The side chat reports an initial sandbox failure and automatic approval rejection, followed by Owner approval of the exact bounded anonymous checks and a successful run. For each of `streamingsoundtracks.com`, `1980s.fm`, `adagio.fm`, `death.fm` and `entranced.fm`:

| Anonymous operation | Side-reported result |
| --- | --- |
| GET `/modules.php?name=Requests` | HTTP 200, HTML, within a 512 KiB response bound |
| GET `/.well-known/oauth-authorization-server` | HTTP 404 |

This establishes reachable public library pages and no authorization metadata advertised at that exact standard location. It does **not** establish that OAuth/device authorization, a custom endpoint or unpublished API is unavailable. Authenticated behavior, token issuance, portability, IP/device binding and backend source were not tested. No station staff was contacted, no credentials were read and no song request was submitted. These checks were not repeated during preparation.

Historical mobile administrator permission is not automatically permission for TV delegation, credential export or a hosted authenticated proxy. Any future station communication or authenticated test needs its own applicable authorization.

## Proposed product paths requiring future approval

| Path | Delivered experience if approved and implemented | Key dependency or limit |
| --- | --- | --- |
| QR opens mobile Requests | Sign in/search/request on phone | Mobile deep-link work required; no TV-side search/submission |
| Paired mobile companion | TV catalog search and explicit confirmation; reachable phone executes authenticated request | Secure pairing and command protocol; initial foreground phone session is a credible first scope, not an implemented feature |
| Independent TV authorization | Phone approves; TV later operates without reachable phone | Supported station-issued device authorization or separately approved auth design; capability unverified |
| Session cookie copying | Potential reuse of broad station session authority on TV | Untested; changes credential/privacy boundaries and revocation semantics; not the recommended default |
| Developer-hosted authenticated proxy | Server performs station operations | New session custody, privacy, hosting and reliability obligations; not justified as a shortcut |

A proposed companion flow is: temporary TV QR → explicit phone approval of the named TV, station, account and narrow request permission → TV public catalog search/confirmation → phone checks authority/safety and submits → station result returns to TV. Credentials stay on the phone. An encrypted authenticated channel, expiry, disconnect/revocation, station/account binding, protocol versioning, operation identity and duplicate handling are required design constraints; the protocol itself is not selected or approved.

A reachable LAN can include Ethernet TV and Wi-Fi phone. Guest isolation, VPNs, address changes and permission denial need clear errors. Phone reachability is a product tradeoff: existing playback/Chat services do not establish a reliable sleeping/background companion. A foreground companion session and reliable background support are separate scopes. “Pair once, then phone sleeps forever” is not an established capability.

## Android platform constraints

Primary documentation was checked during this preparation for the following points:

- Android TV Gboard can transcribe speech into a text field without the Player requesting microphone access. A proposed first flow is Library → dictate query → choose the real album/track → explicitly confirm Request. Physical acceptance must distinguish in-app dictation from a remote button opening system Assistant. No new voice test was performed. [Android TV keyboard documentation](https://developer.android.com/training/tv/get-started/onscreen-keyboard)
- Global Assistant/media-search integration is a separate feature. A station request enters a shared queue; it is not on-demand playback. Do not silently turn “play” commands into public submissions or promise immediate playback. [Assistant media integration](https://developer.android.com/media/implement/assistant)
- Credential Manager documentation lists Android TV saved-password and cross-device passkey support with API 34+ and GMS 2026W30+ requirements. This does not transfer an existing station session or establish station passkey/federation support. Mobile and TV have different package IDs; do not rename packages to force sharing. Actual device/GMS compatibility remains to be checked for any future design, including non-GMS TV support. [Authentication across form factors](https://developer.android.com/identity/form-factors)
- Both apps currently target SDK 36. Android's current guidance distinguishes SDK 37+ local-network enforcement from lower targets; do not impose the newer permission model blindly or add its runtime permission before targeting it. Future target changes require the applicable permission/system-mediated discovery design and denial behavior. [Local network permission](https://developer.android.com/privacy-and-security/local-network-permission)
- Android background restrictions require a separately validated availability/lifecycle design; existing phone services are not evidence for permanent companion reachability. [Background task restrictions](https://developer.android.com/develop/background-work/background-tasks/bg-work-restrictions)
- OAuth device authorization is a standard pattern for input-constrained devices. It requires server support and does not prove that any station implements it. [RFC 8628](https://www.rfc-editor.org/rfc/rfc8628.html)

Additional primary references supplied by the side investigation: [TV searchable content](https://developer.android.com/training/tv/discovery/searchable), [verified App Links](https://developer.android.com/training/app-links/verify-applinks), and [NSD](https://developer.android.com/develop/connectivity/wifi/use-nsd). These three pages were not revalidated during preparation.

## Decisions before any feature implementation

The Owner needs to choose whether the desired outcome is phone-only Requests, TV UI with a reachable phone, or independent TV operation. Confirm which stations/accounts may participate and whether the appropriate station authority permits delegation. Device authorization capability remains unverified; do not ask for new credentials or declare it unavailable without a fresh authorized check of the exact supported route.

An approved feature scope would also need explicit request/safety confirmation semantics, phone foreground/background expectations, pairing revocation, privacy changes, and physical acceptance criteria. Keep the LCARS Easter egg as a separate optional increment. Neither suggestion is an already-authorized next implementation step.

## Completed transition and recipient brief

The Owner assignment is now recorded in `AGENTS.md`. Codex's final administrative verification and write-stop marker are recorded in `work/idle-start-delay/ownership-transfer.json`. After that record, Codex stops TV writes; Claude is the sole writer for the bounded script task. No agent-to-agent message is implied by the local record.

Claude should read the updated instructions, preserve pending handoff records and all local/remote evidence, then implement only the focused-window wait and targeted validation. Do not reopen completed delay acceptance, write product code, alter credentials/provider settings, implement any proposal above, or treat this assignment as release approval. Broader TV work remains unassigned until the Owner names its scope.

Final recipient message for the Owner to convey:

> The Owner's TV sole-writer assignment is recorded in AGENTS.md and the handoff. Codex has completed the administrative record and stopped TV writes. You may now implement only the focused-window wait in acceptance scripts and its targeted validation. Read the updated AGENTS.md and docs/CLAUDE_TV_HANDOFF.md first. Preserve the benchmark snapshot and all evidence. Product code, pairing, requests/authentication, LCARS, broader startup optimization and release work remain outside scope.
