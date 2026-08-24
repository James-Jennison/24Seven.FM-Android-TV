# 24Seven.FM TV — Play declaration packet

Date: August 24, 2026  
Status: prepared from the published TV Alpha 01 artifact and the approved
mobile Player metadata; save or submit only against the active Play Console
wording and with owner attestation.

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

| Play Console area | Proposed TV answer |
| --- | --- |
| Ads | **No** |
| Government apps | **No** |
| Financial features | **None** |
| Health apps | **None** |
| App category | **Music & Audio** |
| Public support contact | `24sevenplayer@jamesjennison.net` |
| App access | **All functionality is available without special access.** Do not enter reviewer credentials. |

The mobile Player's existing policy URL must **not** be copied automatically:
its contents describe account, community, and other mobile-only behavior that
this TV alpha excludes. Publish a public HTTPS page whose contents accurately
match this repository's `PRIVACY.md`, then use that TV-specific URL in Play
Console.

## Store listing

Use the exact listing copy in
[`TV_PLAY_ALPHA_01_RELEASE_NOTES.md`](TV_PLAY_ALPHA_01_RELEASE_NOTES.md) and
the validated Android TV assets in [`play-assets/`](play-assets/).  The current
listing must not claim an account, purchases, chat, requests, notifications, or
features outside Alpha 01.

## Target audience and content rating

The mobile Player owner selected **18+** for the mobile app.  That is a useful
shared product reference, but it is not a completed TV declaration.  Proposed
TV posture: select **18+ only**, because this release is not designed for
children and public radio streams may include adult themes.  Confirm the active
Console question wording and any minor-access setting immediately before saving.

For the IARC questionnaire, answer only these verified facts:

- Music & Audio radio is the core purpose; there is no game, social, dating,
  gambling, commerce, advertising, or user-generated-content surface.
- The app presents public station audio, song titles, artwork, Queue, and
  Recently Played data; it has no user image/video upload or public text entry.
- Do not infer frequency or intensity of mature station programming from the
  mobile Player. Answer the active rating questions conservatively from the
  stations' actual public programming.

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

Whether the active Data Safety form treats that direct station/network
transmission as app-collected user data must be reconciled against its current
question wording before any answer is saved. Do not claim **No data collected**,
end-to-end ephemeral processing, a deletion mechanism, or all-data-encrypted
in-transit without that reconciliation and the owner attestation.

## Foreground-service declaration

**Use case:** Media playback — user-requested streaming radio continues through
the Media3 playback service while the app is backgrounded.

**Description:**

> 24Seven.FM TV streams one public live radio station selected by the listener.
> After the listener starts playback, a Media3 media-session service keeps that
> requested stream playing when the TV app is no longer foregrounded. Android
> system media controls provide pause, resume, and stop behavior. The service
> exists only for the active playback session.

Before saving, provide the current Console-required evidence from the exact
published candidate without exposing tester details or private diagnostics.

## Console completion gate

1. Publish a TV-specific public HTTPS privacy-policy page matching `PRIVACY.md`.
2. Confirm the 18+ target audience and the exact active IARC answers.
3. Reconcile the current Data Safety form with the direct station/network
   connection-data path and obtain owner attestation.
4. Save the objective no-ads/government/financial/health, category, contact,
   app-access, and store-listing values through Play Console.
5. Complete any required foreground-media declaration using current exact
   candidate evidence.

Only sanitized completion evidence belongs in this repository. Do not commit
tester identities, reviewer details, Console captures containing private data,
or unlisted evidence links.
