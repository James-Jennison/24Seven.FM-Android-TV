# TV companion pairing: TV half (design and status)

Status: implemented behind off-by-default flags, 2026-11-02; the sections below marked superseded were decisions before the Owner's later directives (session hand-over, phone-vouched gate, several stations). Scope: the Owner said in chat on 2026-11-02 "TV pairing work is now in scope". The repository `AGENTS.md` still lists pairing as excluded and has not been updated to match; the Owner's chat directive is the authority until it is. The sections below up to "Implementation status" are the original design; the implementation follows it, with the later Owner decisions recorded at the end. Release, signing and publication stay out of scope. The mobile half is owned by the mobile writer; see the mobile draft `docs/tv-companion-pairing-design.md` (Player PR 145). The mobile repository is read-only for this project, and protocol changes are negotiated through the mobile session.

## What the TV does and does not do

- (Superseded by the Owner's session hand-over decision, below.) The TV used to stay anonymous; it now holds the phone's per-station station-site session, encrypted, and shows the account label for each.
- The TV searches the public catalog. (Superseded) With `TV_REQUESTS_ENABLED` it submits a request itself from the stored session after a TV confirm screen; the phone's vouched gate is re-checked each time and expires after 30 days.
- The TV is not an authority on the result. It displays what the phone returns, and an uncertain outcome stays uncertain and points to the Queue.
- Playback is never interrupted by pairing screens.

## Decisions (TV side)

| Topic | Choice | Why |
| --- | --- | --- |
| Transport | TCP + TLS, length-prefixed JSON frames, TV is the server | No new dependency (`SSLServerSocket`), small surface |
| Server certificate | Self-signed key generated per pairing in AndroidKeyStore (`KeyGenParameterSpec` with a certificate subject); pairing token is the SHA-256 of its public key, carried in the QR | The scan authenticates the key, so a LAN attacker cannot MITM after pairing |
| Client auth | A 128-bit one-time secret from the QR, sent as the first frame; on success the TV issues a long-lived pairing id and secret the phone stores | Binds the session to the person who saw the screen |
| Lifetime | Listener opens only while the pairing or requests screen is in the foreground; it closes on Stop and on process death. No background service | Matches foreground phone and the privacy boundary |
| Discovery | QR carries host, port and key hash; typed code plus IP under the QR as a fallback. NSD/mDNS only as a later optimization | Guest Wi-Fi and client isolation break mDNS |
| QR rendering | Prefer ZXing core (needs a `gradle/verification-metadata.xml` entry and a THIRD_PARTY_NOTICES line); a hand-written encoder is the alternative | Not yet chosen |
| Operation identity | Operation id, protocol version and expiry on every message; duplicate ids are answered from the stored result, never re-run | Required by the mobile draft |

## Screens and states

1. **Unpaired:** QR with countdown, regenerate, typed-code fallback.
2. **Paired idle:** phone label, station, and Revoke.
3. **Search:** dictation or keyboard, loading, empty, error, offline.
4. **Confirm:** exact track, station, "waiting for phone approval", Cancel.
5. **Result:** accepted, refused with the station's reason, phone unreachable (no submission happened), uncertain (check the Queue).
6. **Revoked, expired, signed out:** return to 1.

Every screen must be D-pad and Back navigable, with no touch-only step.

## Verified on the Streamer (2026-11-02)

Read-only probes, then a throwaway spike (a debug-only activity built in a GTHost clone with package suffix `.spike`, installed on the Google TV Streamer, SDK 34, Ethernet, and uninstalled afterwards; the spike is not in the repository):

- A TLS 1.3 server socket with an AndroidKeyStore EC P-256 key and its self-signed certificate (`KeyGenParameterSpec.setCertificateSubject`) accepted a workstation LAN connection as a normal app, with no permission beyond `INTERNET`.
- The SHA-256 of the served certificate's public key (SPKI) matched the hash the app logged, so the QR key hash pins correctly.
- A 4-byte length plus JSON frame arrived intact (the server received it; the reply leg of my throwaway echo was not checked).
- **The key must allow `DIGEST_NONE` as well as `SHA256`.** With SHA-256 only, Conscrypt's TLS handshake fails with "I/O error during system call" because it signs with `NONEwithECDSA`. This is a real implementation requirement.
- `NsdManager.registerService` succeeded and the workstation's mDNS browser resolved the service with the Streamer's address and port. NSD is therefore a viable optional discovery path on Ethernet.
- Sleep/wake is **not verified**: `KEYCODE_SLEEP` left the Streamer awake, so the listener's behavior across standby is still unknown.

## Not yet verified

- Keyboard dictation into an in-app text field (needs a human at the remote; the spike activity had a field for it).
- Listener behavior across real standby and wake, and Wi-Fi-connected Streamer behavior.
- Phone on a different VLAN or guest network (expected to fail, and must show a clear error).

## trackRef (open item with the mobile session)

The mobile `SongRequestPageParser` shows the identifiers the public Requests pages use: a station-specific album id (`asin` query value) and a numeric `songID`. Proposed `trackRef` = `<albumId>:<songId>`, opaque to the TV, validated by the phone against the paired station. The TV never forwards request URLs or button links. The TV needs only its own read-only search and album parsing (jsoup is already a dependency) to obtain these ids and the display title, artist and album. Eligibility on the page (`requestbutton_request`) is advisory only; the phone and station decide.

## Plan

1. Settle the protocol shapes with the mobile session. Message shapes are not final until the Owner settles its open decisions (transport, availability, stations/accounts, release flag).
2. Spike: done for TLS, keystore certificate and NSD (see above); dictation and standby remain, with the QR encoder choice.
3. Implement behind a build flag that is off by default, with unit tests and a fake-phone peer: pairing state machine, expiry, duplicate operations, revocation, ambiguous results.
4. `PRIVACY.md`, the Play declaration packet and release notes for Owner approval before the flag is turned on.
5. Review under the active R6 panel (CRITICAL: networking, security, new permission surface), then joint physical acceptance with the mobile app on the Streamer and a phone.

## Constraints that still apply

- No WebView, no credentials on the TV, no new stream URLs.
- A new inbound listener needs a manifest and network-security review; `INTERNET` and `ACCESS_NETWORK_STATE` exist today and no other permission is expected to be needed.
- No automatic retry of a submission, and no TV-side persistence of the phone's secrets beyond the pairing credential.

## Implementation status (2026-11-02, local commits, not pushed, not reviewed)

Built behind `BuildConfig.PAIRING_ENABLED`, which is false in release builds and in debug builds unless `TWENTYFOURSEVEN_PAIRING_ENABLED=true` is set at build time. With the flag off there is no menu entry, no listener and no activity use.

- `pairing/PairingProtocol.kt`, `PairingSession.kt`, `PairingHost.kt`, `PairingKey.kt`, `PairingController.kt`, `PairingActivity.kt`, `PairingQr.kt`, `PairingFingerprint.kt`. Menu entry "Pair a Phone" under General Options only with the flag.
- Frames and proofs follow the mobile `docs/tv-companion-pairing-protocol.md`; the independent HMAC vectors pass. QR code lifetime is 120 s; typed code and QR secret both prove the same pairing.
- Verified: 59 new JVM tests (codec, parser, state machine, vectors, loopback host); full TV unit suite 221 passed on GTHost; on the Streamer, a fake phone completed TLS 1.3, fingerprint match, pair, ping and propose/declined, and the listener was gone after Back.
- Not verified: a camera scan, a real phone, dictation, standby, catalog search (not built), real requests, independent reviews (CRITICAL panel), `PRIVACY.md` and Play declaration updates.

## Session hand-over (Owner decision, 2026-11-02; supersedes the phone-approves-each-request flow)

Owner (chat, 2026-11-02): the paired phone hands its station session to the TV, which then searches and requests itself. The Owner confirmed in chat that they told the phone session to send this instruction to the TV session. This reverses two earlier statements in this document: the TV is no longer anonymous, and it no longer relies on the phone's per-request approval.

- Protocol (v1, additive): after `pair_ok` the phone may send one `session` frame (`station`, `account` label, up to 16 cookies of name, value and maxAge). The TV answers `session_ok` or `session_refused` with a reason. A second hand-over is refused (`already_handed_over`), a different station is refused (`wrong_station`), and a session frame before pairing closes the connection.
- The origin comes from the TV's own station table, never from the phone. Cookie names and values are validated (no spaces, `;`, `,`, quotes, control characters; at most 4096 characters) and cookie values are never logged, shown or kept in diagnostics (`toString` of both cookie types hides them).
- Storage: AES-GCM with a non-exportable AndroidKeyStore key in app-private preferences, surviving the pairing. Expired cookies are dropped on load and an unreadable blob is deleted.
- "Sign out on this TV" deletes the stored session locally only. The TV never requests `op=logout`.
- Read-only check built: after a hand-over, and when the pairing screen opens, the TV fetches the station home page with the session and logs `signedIn=true/false` by the logout-link test. When the station treats the session as expired the screen says to pair with the phone again.
- **Gate (Owner decision 2026-11-02, phone vouches):** the `session` frame carries `gate{adultAgeGate,termsVersion,communityVisible}`. The TV refuses with `gate_not_passed` or `terms_out_of_date` (exact match against `CURRENT_COMMUNITY_TERMS_VERSION`, 2026-07-18) and stores nothing, keeps the gate with the session, and re-checks it on every submit.
- **Submission code (built, unwired, off):** `RequestSubmitter` behind `BuildConfig.TV_REQUESTS_ENABLED` (false unless a debug build sets `TWENTYFOURSEVEN_TV_REQUESTS_ENABLED=true` together with the pairing flag). One same-origin GET of the Req link, never retried, unclear outcomes say check the Queue. Not wired to any screen and no TV confirm screen yet. Superseded text follows.
- **Earlier note:** submitting requests from the TV, the TV confirm screen, and the community-safety gate. The Player's `canContributeCommunityContent` needs the age gate, accepted terms version and visible-content setting, all held in the phone app's own preferences. The TV has none of that state, so the gate must be re-established on the TV or attested by the phone before any write; that is an open decision. No request is sent to a station without the Owner's explicit say-so on the day.
- Status: not reviewed. `PRIVACY.md` and the Play data-safety wording need the Owner's approval because the TV now stores a station session credential.

### Several stations (Owner decision 2026-11-02)

A phone signed in to several stations hands over one session per station. The TV keeps one encrypted session per station (the old single-session blob is migrated on first read), accepts one `session` frame per station per pairing (`already_handed_over` repeats are refused, an unknown station is `unsupported_station`; the pair frame's station no longer limits it), and keeps the link open for 20 seconds after each hand-over so the next frame can arrive. The pairing screen lists every signed-in station with its account and a per-station sign-out; Request a Song shows a station row when more than one is signed in and starts on the station the TV is playing.

### Independent review round 1 (cycle review-cycle-e1bb599a2858, candidate 1d27289) and dispositions

Fixed after the review: the hand-over grace timer is cancelled by `end()` and bound to its pairing (arch-1, plat-1, deep-5); reply classification checks the station's verdict before the word "login" (arch-2, plat-7); sessions are wiped at start-up when the feature flag is off (arch-3); the listener closes once a phone is paired (arch-4); the phone's vouching expires after 30 days (arch-5); station origins have one table and the Done screen shows the station the request went to (arch-6, arch-8); stale code removed (arch-7); a session is never sent to another station's origin, enforced inside the fetch (plat-3); station reply text is no longer logged (plat-9, deep-3); JSON nesting is bounded before parsing, closing a pre-pairing crash vector (plat-14); at most two unpaired connections per source address (deep-2); the pairing code and fingerprint have spoken descriptions (deep-10).

Accepted or disputed, with reasons: the QR carries the one-time secret and key hash by design of the shared protocol, is shown only on the owner's own TV for 120 seconds and is single-use (deep-1); a station session cannot be proven by the TV, so a stranger who obtained the live code could hand over their own station account, which the TV then shows by name (plat-2, REQUIRES OWNER DECISION: an on-TV accept step); station TLS uses the platform trust store, like the mobile Player (deep-4); the single queue check after the one request GET is read-only and bounded by the same timeouts, not a retry (plat-6); release builds hard-code both flags to false (plat-4); the session key is a non-exportable KeyStore key, backups are disabled and an unreadable blob is deleted (plat-8, deep-6); the account list is loaded synchronously before the listener decision (plat-11); the catalog keeps one browser per station (plat-13); the typed code allows three guesses per 120-second code on the local network, recorded as a known bound (deep-2, arch-9).

Open: PRIVACY.md and Play data-safety wording, Owner acceptance, TalkBack and lint runs, and a closure review of the fixes.
