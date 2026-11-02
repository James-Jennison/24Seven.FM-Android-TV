# TV companion pairing: TV half (design draft, not implemented)

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

## Verified on the Streamer (2026-11-02, read-only probes)

- The Streamer is on Ethernet (`eth0`, 192.168.1.187/24), SDK 34.
- A listener on the device accepted a TCP connection from the workstation on the LAN (toybox `nc`, shell uid, port 5599, then removed). This shows no firewall blocks inbound LAN TCP; it does not prove the same for an app uid.
- The workstation's mDNS browser sees the Streamer (`_meshcop._udp`), so multicast works on this network at the OS level.

## Not yet verified

- App-uid inbound TCP, `NsdManager` registration and resolution, and multicast behavior while the Streamer sleeps/wakes.
- Wi-Fi-connected Streamer behavior.
- That the Google TV keyboard dictates into an in-app text field without microphone access (the handoff cites documentation only).
- Reachability when the phone is on a different VLAN or guest network (expected to fail, and must show a clear error).

## Plan

1. Settle the protocol shapes with the mobile session. Message shapes are not final until the Owner settles its open decisions (transport, availability, stations/accounts, release flag).
2. Small spike on the Streamer: a throwaway debug build with a TLS listener and a QR, built on GTHost, to confirm app-uid inbound TCP, dictation, and the keystore certificate.
3. Implement behind a build flag that is off by default, with unit tests and a fake-phone peer: pairing state machine, expiry, duplicate operations, revocation, ambiguous results.
4. `PRIVACY.md`, the Play declaration packet and release notes for Owner approval before the flag is turned on.
5. Review under the active R6 panel (CRITICAL: networking, security, new permission surface), then joint physical acceptance with the mobile app on the Streamer and a phone.

## Constraints that still apply

- No WebView, no credentials on the TV, no new stream URLs.
- A new inbound listener needs a manifest and network-security review; `INTERNET` and `ACCESS_NETWORK_STATE` exist today and no other permission is expected to be needed.
- No automatic retry of a submission, and no TV-side persistence of the phone's secrets beyond the pairing credential.
