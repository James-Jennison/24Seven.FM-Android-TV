# TV04 Queue/History device validation

Completed Aug 20, 2026 against debug artifact `0.1.0-tv-alpha01-debug` on the Dongle R 4K
(Android API 34) at its 1920×1080 application viewport.

Additional device-preference validation was completed Aug 22, 2026 on the wired Google TV
Streamer (Android API 34). Its physical HDMI output is 3840×2160, while the Android application
viewport remains 1920×1080 at 320 dpi.

Status: Complete — the read-only library, local sleep timer, and privacy-safe local diagnostics
are physically validated. The completed low-RAM branch acceptance is recorded under TV05; the
separate release-delivery evidence remains under TV06.

## Verified

- The D-pad station strip selects a live stream; when playing, that station becomes the focused `Station - Live`
  pause toggle.
- StreamingSoundtracks.com returned public Queue and History data; the TV UI rendered `Track / Artist` labels.
- No member identifier, request message, authentication surface, or Chat data was rendered.
- Queue/History reads do not convert a cancelled earlier read into a displayed network error.
- The settled dashboard leaves station selection inside the 1080p safe area and has no separate bottom transport row.
- The redundant app title and network broadcast header were removed; the station title in Now Playing provides the
  visible context and leaves additional safe-area room for the read-only library.
- Now Playing, Queue, and History are presented together on one listener dashboard with only the `UP NEXT` and
  `RECENTLY PLAYED` display labels; no D-pad tab selection is required.
- The selected station changes the complete dashboard palette. SST was verified with the deep-blue score-radio
  treatment and 1980s.FM with its neon magenta/cyan treatment; the same station-palette contract covers Adagio,
  Death, and Entranced.
- The station selector renders the five station-provided logos as centered controls, without duplicating their names
  in text. A selected live station adds only a centered `LIVE` status label beneath its icon.
- The dashboard uses the 1080p TV safe area more fully: compact circular station-logo controls leave room for five
  visible `UP NEXT` and five visible `RECENTLY PLAYED` tracks without clipping.
- Now Playing combines its artwork and metadata inside one card and renders live stream metadata as `Track - Artist`.
- On foreground dashboard launch, the persisted last-selected station is selected and starts automatically; the
  physical device reported its 1980s.FM MediaSession as `PLAYING`.
- Selecting a station starts its permitted live stream. The physical device reported a PLAYING MediaSession with
  station metadata; current TV03 validation additionally verifies that the focused live-station control pauses and
  resumes the MediaSession rather than stopping a continuous live stream.
- The dashboard has no dedicated Sleep control. When a station selector gets focus, a compact `↓ OPTIONS` hint is
  shown for three seconds and then fades. Pressing Down from that focused selector opens the local Sleep timer and
  Device status dialog without changing the selector layout; Back closes it. A 15-minute timer was set from the
  Google TV Streamer and visibly reported its remaining time; reopening its dialog and selecting Cancel removed the
  timer and its MediaSession custom action.
- Device status opens only from the local Sleep dialog and renders a read-only diagnostic report. The physically
  inspected report included app/build, Android/device, selected station, allowlisted playback/network/audio-output
  state, and a bounded transition history. It intentionally excluded account data, messages, content, URLs,
  identifiers, raw errors, and logs.
- The Streamer's selector uses the network owner's original 500×500 station logos, pre-sized to 128px and 256px
  variants. At its 98px rendered icon size the 128px tier, paired with Compose `FilterQuality.High`, visibly
  removed the previously observed icon aliasing/shimmer.
- Before live artwork arrives after launch, the Now Playing card renders the selected station's faded logo with the
  decorative animated waveform. This no-artwork interstitial was visibly observed on the Google TV Streamer during
  initial stream connection and resolves cleanly to the live cover artwork.
- A D-pad-only Streamer pass selected all five permitted stations—StreamingSoundtracks.com, 1980s.FM, Adagio.FM,
  Death.FM, and Entranced.FM—into their live states. Each station rendered populated anonymous `UP NEXT` and
  `RECENTLY PLAYED` rows. Death.FM's compact public feed remained read-only and rendered without exposing any
  authentication or member data.
- The confirmed sleep-timer policy is process-bound: it remains active while the app is backgrounded and the
  playback service remains alive, but a force-stop or process death clears it. After setting a 15-minute timer,
  force-stopping and relaunching the app returned its dialog to duration choices with no remaining-time or Cancel
  action. No test timer remained active after that restart.
- Current-source revalidation is pending: the persisted timer-restoration path was removed after this evidence was
  recorded so the implementation matches the confirmed policy. Re-run the same force-stop/relaunch case on a
  supported TV device before treating this historical observation as acceptance for the new source revision.
- The project owner physically tested the live Streamer resilience path: Ethernet disconnect/reconnect recovered
  playback, and D-pad focus, Back, the hidden `↓ OPTIONS` path, media pause/resume, and sleep-timer expiry continued
  to behave correctly after recovery.

## Scope boundary

- Later Fire TV acceptance confirmed VoiceView traversal through General Options and the Idle Visualizer picker,
  including focus transfer into the dialog and exclusion of background queue/history content. A second physical
  Fire TV remote also completed the picker preview/Back route; its transport was not classified as Bluetooth.
- The Streamer has been checked at its 4K HDMI output but still presents a 1920×1080 logical app viewport, so this
  is not native-4K UI certification. Broader display evidence belongs to TV05; its low-RAM branch acceptance is
  complete there, while release-delivery evidence belongs to TV06, not this completed TV04 feature milestone.
