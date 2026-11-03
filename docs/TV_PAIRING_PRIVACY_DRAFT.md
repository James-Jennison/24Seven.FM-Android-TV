# DRAFT: privacy and Play data-safety wording for phone pairing

Status: **approved by the Owner on 2026-11-03 and applied to PRIVACY.md (the Play data-safety table below is still a proposal; not published).**
Pairing and TV requests are off in release builds (`PAIRING_ENABLED` / `TV_REQUESTS_ENABLED`),
so the current PRIVACY.md stays accurate until this feature is released. Apply this text
in the same change that enables pairing.

## Proposed PRIVACY.md changes

Replace the sentence "No account, member, chat, request, or payment data is collected or
exposed in the TV alpha." with:

> The TV app does not have its own accounts and never asks for a password. If you choose
> to pair a phone, the 24Seven.FM phone app can hand the TV the sign-in session it already
> holds for a station, so the TV can search that station's catalog and send song requests
> for you.

Add under "Data handled":

> - **Paired-phone station session (optional, off unless you pair).** The session
>   (an account name and the station's sign-in cookies) is stored encrypted on the TV, with
>   a key that cannot leave the device's secure keystore. It is sent only to the station
>   that issued it, over HTTPS, to check you are signed in, to browse its catalog and to
>   submit a request you chose. It is never sent to the app's developers or any third party.
>   Pairing happens over your local network with a one-time code shown on the TV; the TV
>   keeps no record of the phone beyond the session itself.
> - **Community-safety confirmation.** The phone tells the TV that you have already passed
>   the station's age and community-terms checks. The TV stores that confirmation with the
>   session and requires a fresh one after 30 days.

Add under "Android permissions": **Local network listening** (only while the pairing screen
is open; the listener closes as soon as a phone is paired) and note the camera is used by
the *phone* to scan the TV's QR code, not by the TV.

Add under "Retention and deletion":

> The station session stays on the TV until you choose "Sign out on this TV", the
> station-side session expires, the confirmation is 30 days old, or app data is cleared or
> the app is uninstalled. Signing out on the TV removes only the TV's copy; it does not sign
> you out on the station or the phone.

Revise the "contains no ... access" sentence: the app still has no analytics, advertising,
microphone, camera, location or contacts access; it now accepts local-network connections
while the pairing screen is open.

## Voice (Speak and "play ... on 24Seven.FM")

Add under "Data handled":

> - **Voice search (optional).** The Speak button on the Request a Song screen opens the TV's own speech
>   recognizer. The app does not record audio, has no microphone permission and receives only the words the
>   recognizer returns, which it uses as a search on the station's public catalog (the same as typed text).
>   Speech handling is done by the TV's voice service under its own privacy terms.
> - **Voice playback commands.** When you ask the TV's assistant to play a station on 24Seven.FM, the assistant
>   passes the station name to the app, which starts that station. Nothing is stored.

Keep the "no microphone access" sentence accurate: the app itself still requests no microphone permission.

Play data-safety: no new data type is collected by the app for voice (audio is handled by the system
recognizer, not the app); recognised search text is sent only to the station the listener is searching.

## Play Console data-safety answers (proposed)

| Question | Answer | Basis |
|---|---|---|
| Collects personal info | Yes, **user IDs/account name**, optional | Station account name is stored on-device |
| Shared with third parties | No | Sent only to the station the user is signed in to, at the user's direction (service the user requested) |
| Processed ephemerally / stored | Stored on device only | Encrypted, no developer server |
| Required or optional | Optional | Only after the user pairs a phone |
| Encrypted in transit | Yes | TLS to the station; TLS 1.3 with a pinned key on the LAN link |
| Deletion | User can remove on the TV ("Sign out on this TV") or by clearing app data | |
| Credentials (passwords) | Not collected | The TV never sees a password, only an existing session |

## Open points for the Owner

1. Is "account name" acceptable to describe the stored label, or should the TV store no
   name at all? (It is shown only on the sign-in status screen.)
2. Confirm the 30-day limit is the figure to publish.
3. Wording must match the final release behaviour; re-check before applying.
