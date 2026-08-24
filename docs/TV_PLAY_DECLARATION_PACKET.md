# 24Seven.FM TV — Play declaration packet

Date: August 24, 2026  
Status: ten of eleven Play setup tasks saved from the published TV Alpha 01
artifact and the approved mobile Player metadata. Only the store listing
remains open.

This packet applies only to `com.codeframe78.twentyfourseven.tv`, version
`0.1.0-tv-alpha01` (version code `1`), from revision
`3c742c5f182e4bd0f8997d95feaf1cb5df620714`.  It is an independently listed
Android TV app.  The mobile Player is a reference for shared public contact and
network-level policy facts, not a substitute for this app's declarations.

## Artifact and product boundary

| Surface | Evidence-backed fact | Declaration consequence |
| --- | --- | --- |
| Product | Anonymous Android TV listener for five permitted public stations | No sign-in, reviewer account, account deletion, community, request, browser, commerce, notification, or Cast flow exists in this alpha. |
| Network | Live audio plus public Now Playing, artwork, Queue, and Recently Played reads | The selected station receives normal connection information needed for each request. The TV app does not read or persist an IP address. |
| Local storage | Last-selected station and sleep-timer state | This stays on-device until changed, app data is cleared, or the app is uninstalled. |
| Permissions | Internet, network state, foreground service, foreground media playback | The only foreground-service declaration is **Media playback**. No sensitive-data permission is present. |
| Dependencies and behavior | No advertising, analytics, crash-reporting, tracking, billing, social-login, or developer-backend surface | No ads, analytics, or tracking declaration is supported by this artifact. |

The source of truth for the product boundary is
[`TV_PRODUCT_CONTRACT.md`](TV_PRODUCT_CONTRACT.md), and the public disclosure
for this app is [`PRIVACY.md`](../PRIVACY.md).

## Reusable shared metadata

The following values are aligned with the mobile Player's established public
Play metadata and are also supported by this TV artifact:

| Play Console area | Saved TV answer — August 24, 2026 |
| --- | --- |
| Ads | **No** |
| Government apps | **No** |
| Financial features | **None** |
| Health apps | **None** |
| App category | **Music & Audio** |
| Public support contact | `24sevenplayer@jamesjennison.net` — entered in Play Console, provisioned, and receipt-verified by the owner after a one-time plain-text test on August 24, 2026 |
| App access | **All functionality is available without special access.** Do not enter reviewer credentials. |

The mobile Player's existing policy URL must **not** be copied automatically:
its contents describe account, community, and other mobile-only behavior that
this TV alpha excludes. The TV-specific public HTTPS policy is now published
at `https://player.jamesjennison.net/privacy/tv/` and was verified at both the
origin and public endpoint on August 24, 2026. Use that exact URL in Play
Console.

## Store listing

Use the exact listing copy in
[`TV_PLAY_ALPHA_01_RELEASE_NOTES.md`](TV_PLAY_ALPHA_01_RELEASE_NOTES.md) and
the validated Android TV assets in [`play-assets/`](play-assets/).  The current
listing must not claim an account, purchases, chat, requests, notifications, or
features outside Alpha 01.

## Target audience and content rating

The mobile Player owner selected **18+** for the mobile app. That shared product
reference was reconciled with the active TV form, and **18 and over** is now
saved for TV Alpha 01. The active form exposed no further minor-access choice
for this release.

The IARC questionnaire was submitted on August 24, 2026 using **All Other App
Types**, the approved support contact, and the conservative Death.FM answers
below. The generated territory ratings are Brazil **16+**, ESRB **Teen**, PEGI
**Parental Guidance**, USK **16+**, and **12+** in the generic territories.
Those IARC outcomes are distinct from the saved **18 and over** target-audience
gate; do not claim that IARC returned an Adults Only rating.

The classification was based only on these verified facts:

- Music & Audio radio is the core purpose; there is no game, social, dating,
  gambling, commerce, advertising, or user-generated-content surface.
- The app presents public station audio, song titles, artwork, Queue, and
  Recently Played data; it has no user image/video upload or public text entry.
- Death.FM publicly presents an extreme, black, brutal, technical, and death
  metal catalog; its published playlist includes explicit, violent, and sexual
  themes. The app does not itself create or curate user-generated content.

## Data safety and privacy gate

Do not copy the mobile Player's Data Safety worksheet.  It covers mobile-only
accounts, sessions, Chat, requests, searches, email handoffs, and diagnostics
that are absent from TV Alpha 01.

The TV-specific facts are:

- The app has no account, contact, commerce, telemetry, advertising,
  crash-reporting, tracking, location, microphone, camera, contacts, or broad
  storage data path.
- Device-local preferences are not transmitted.
- Public playback and metadata requests necessarily send normal connection
  information to the selected station or its delivery network. The app itself
  does not read or retain that information.
- The mobile network record says station operators use source IP for network
  access/security, but it does not establish retention, linkage, location
  inference, or processor details for this TV release.

The owner approved the active-form reconciliation and it is saved in Play
Console: approximate location is declared as collected and shared, required
for app functionality and fraud/security purposes, and not processed
ephemerally. The saved form declares no account creation, no external-account
login, no data-deletion mechanism, and not all data encrypted in transit. This
is a conservative declaration for direct station/network connection data, not
a claim that the TV app itself retains location data.

## Foreground-service declaration

**Use case:** Media playback — user-requested streaming radio continues through
the Media3 playback service while the app is backgrounded.

**Description:**

> 24Seven.FM TV streams one public live radio station selected by the listener.
> After the listener starts playback, a Media3 media-session service keeps that
> requested stream playing when the TV app is no longer foregrounded. Android
> system media controls provide pause, resume, and stop behavior. The service
> exists only for the active playback session.

The Console's **Media playback** use case is selected. The corresponding
current-candidate evidence is publicly hosted at
`https://player.jamesjennison.net/reviewer-media/24seven-tv-foreground-media-playback-review.mp4`:
a 21-second Google TV Streamer capture of the native Player with the selected
station visibly marked `LIVE`. Public HTTPS returned `200`, `video/mp4`, and a
byte-for-byte match to the reviewed local capture. The link is entered and
saved in Play Console for the selected **Media playback** use case; no PIP or
Other use case is selected.

## Console completion gate

1. **Complete:** saved the TV-specific public privacy-policy URL, public
   app-access/no-sign-in answer, Ads **No**, Government **No**, Financial
   features **None**, Health **None**, **Music & Audio** category, public
   support contact, and **18 and over** target age in Play Console. The
   published support mailbox is provisioned and receipt-verified by the owner
   after its one-time managed-transport test.
2. **Complete:** saved the store-listing copy and approved TV assets through
   Play Console.
3. **Complete:** submitted the IARC questionnaire and saved the generated
   territory ratings. The Content ratings declaration appears on the App
   content Actioned tab.
4. **Complete:** pasted and saved the hosted current-candidate video URL in
   the foreground-media declaration.
5. **Blocked externally:** App content's Need attention tab is empty and the
   store listing reports all required information, but Publishing overview
   continues to disable **Send app for review** after reload with its generic
   dashboard-required-steps message. The Dashboard only reports the distinct
   future production closed-test requirement.

Only sanitized completion evidence belongs in this repository. Do not commit
tester identities, reviewer details, Console captures containing private data,
or unlisted evidence links.
