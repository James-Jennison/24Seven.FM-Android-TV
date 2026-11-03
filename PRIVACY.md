# Privacy notice for 24Seven.FM TV

Last updated: November 3, 2026.

24Seven.FM TV is a native Android TV listener application for the five public 24Seven.FM stations. It is a listener-first experience; listening needs no account.

## Data handled

- The selected station receives the normal network connection information required to play its public live stream and retrieve public Now Playing, artwork, Queue, and Recently Played data under that station's own practices. The app does not collect or persist an IP address.
- The app stores the last-selected station and a local sleep-timer preference on the device so it can restore the listener experience. The TV app does not have its own accounts and never asks for a password. If you choose to pair a phone, the 24Seven.FM phone app can hand the TV the sign-in session it already holds for a station, so the TV can search that station's catalog and send song requests for you.
- The optional local Device status view contains only app/build information, coarse device and Android details, the selected station, bounded playback/network/audio state, and a short non-sensitive transition history. It excludes account data, messages, URLs, identifiers, raw errors, and logs.
- **Paired-phone station session (optional, off unless you pair).** The session (an account name and the station's sign-in cookies) is stored encrypted on the TV, with a key that cannot leave the device's secure keystore. It is sent only to the station that issued it, over HTTPS, to check you are signed in, to browse its catalog, to read its request wait times and to submit a request you chose. It is never sent to the app's developers or any third party. Pairing happens over your local network with a one-time code shown on the TV; the TV keeps no record of the phone beyond the session itself.
- **Community-safety confirmation.** The phone tells the TV that you have already passed the station's age and community-terms checks. The TV stores that confirmation with the session and requires a fresh one after 30 days.
- **Voice search (optional).** The Speak button on the Request a Song screen opens the TV's own speech recognizer. The app does not record audio, has no microphone permission and receives only the words the recognizer returns, which it uses as a search on the station's public catalog, the same as typed text. Speech handling is done by the TV's voice service under its own privacy terms.

The app contains no advertising, analytics, crash-reporting, tracking SDKs, microphone access, camera access, location access, contacts access, or broad storage access. While the pairing screen is open it accepts local-network connections from your phone; the camera used to scan the TV's QR code is the phone's, not the TV's.

## Android permissions

- **Internet and network state:** live audio and public station information.
- **Foreground media playback and media session controls:** uninterrupted live listening and Android system playback controls.
- **Wake lock:** only while Media3 maintains an active live stream.
- **Local network listening:** only while the pairing screen is open; the listener closes as soon as a phone is paired.

## Retention and deletion

The last-selected station and local timer state remain on the device until changed, app data is cleared, or the app is uninstalled. Public station responses are held only as needed for the active listener experience. The station session stays on the TV until you choose "Sign out on this TV", the station-side session expires, the confirmation is 30 days old, or app data is cleared or the app is uninstalled. Signing out on the TV removes only the TV's copy; it does not sign you out on the station or the phone. Station-side logs and retention are controlled by the relevant station or network operator.

This is pre-release software. Do not include personal information, credentials, or network captures in feedback.
