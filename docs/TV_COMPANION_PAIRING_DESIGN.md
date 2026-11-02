# TV companion pairing: TV half (design and status)

Status: draft, 2026-11-02. Scope: the Owner said in chat on 2026-11-02 "TV pairing work is now in scope". The repository `AGENTS.md` still lists pairing as excluded and has not been updated to match; the Owner's chat directive is the authority until it is. Nothing here is implemented. Release, signing and publication stay out of scope. The mobile half is owned by the mobile writer; see the mobile draft `docs/tv-companion-pairing-design.md` (Player PR 145). The mobile repository is read-only for this project, and protocol changes are negotiated through the mobile session.

## What the TV does and does not do

- The TV stays anonymous. It never holds credentials, cookies, tokens or an account identity; it shows only a phone-supplied display label.
- The TV searches the public catalog and presents a selected album and track. It never submits a station request itself. The phone submits through its existing safety gate.
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
