# TV and mobile branding

The TV app and the mobile Player are separate products with separate application IDs, release
trains and listings. They share one visual identity. The reference is the mobile Player's `main`
branch, read through git, never its checked-out working tree.

## Shared with the mobile Player (kept byte-identical)

`scripts/check-branding-parity.sh <mobile-repo> [ref=main]` is the single source of truth for the file
list. It reads each file with `git show <ref>:<path>` and prints the commit it resolved, so
uncommitted mobile work cannot skew the result. It exits non-zero on any difference or missing file
and never writes to either repository. It checks 26 files:

- launcher icon: the two adaptive-icon drawables, both `mipmap-anydpi-*` `ic_launcher.xml` files, the five
  density `ic_launcher.png` files and `app_logo.png`;
- colors: `values/colors.xml` (including the splash colors) and `values-night/colors.xml`;
- launch screen: `splash_icon.xml`, `splash_icon_animated.xml`, `splash_branding.png` and the ten
  `splash_*` animators;
- theme: `RadioTheme.kt`.

The script also prints the TV `HEAD` and refuses to pass while `app/src/main` (only that path, not the whole tree)
has uncommitted changes (use
`--allow-dirty` only while iterating), so a pass describes a commit. Last verified against mobile `main` commit `a309c1a5a6047815eefae5183985d39d0a3d025d` (versionName 1.0.2).
Mobile 1.0.3 is in flight, so re-run the script and re-copy when `main` moves. The mobile repository
has its own writer: change shared files there first, then copy them here unchanged.

## TV-specific

- **Launch screen.** Two layers play the same antenna mark. (1) The system splash uses the shared resources through
  `androidx.core:core-splashscreen` (`installSplashScreen()` in `MainActivity`; `Theme.TwentyFourSevenPlayer.Starting`
  in `values/styles.xml`; the branding image applies on Android 12+ via `values-v31`). The library is needed because
  the app supports minSdk 26 and the `windowSplashScreen*` theme attributes come from it; Android 12+ devices use the
  platform path. (2) Google TV does not render the system splash (the system creates it, but nothing appears on the
  Streamer), so `TvLaunchSplash` draws the same `splash_icon_animated` mark in-app once per cold start over
  `splash_background`. Nothing is timed from composition (the first frame takes over a second to draw on a release
  build): after the splash's own first draw it waits 350 ms for the launch transition, starts the animation, holds
  the mark for 1.5 s and fades for 300 ms (about 1.9 s on screen). A single coroutine owns this; if the splash is
  never drawn within 10 s it just leaves. A first-use dialog is a separate window and covers the splash on the very
  first run of a fresh install. It is decorative: it does not gate startup work, but while it is
  visible the activity swallows every key except Back, so a press cannot act on UI the viewer cannot see. The
  system-splash layer is wired and the system creates it, but it has never been observed on screen on the acceptance
  Streamer; the in-app layer is the one verified there. Neither layer copies mobile's exit hand-off, which lands on a
  hard-coded position for mobile's header logo.
- **Window background.** `tv_window_background` (`#0E0B14`, TV-only `values/tv_colors.xml`) is the
  dark-only TV window color. Mobile's `window_background` follows day/night and is not used here.
- **Not copied from mobile:** the DejaVu Sans Mono fonts (used only by mobile chat).

## Names

| | Release label | Debug label | Application ID |
| --- | --- | --- | --- |
| TV | `24Seven.FM TV` | `24Seven.FM TV (Debug)` | `com.codeframe78.twentyfourseven.tv` |
| Mobile | `24Seven.FM Player` | `24Seven.FM Player (Debug)` | unchanged, separate |

**Owner approved this naming scheme in chat on 2026-11-01** (release "24Seven.FM TV", debug
"24Seven.FM TV (Debug)"). It remains reversible by editing the two `appLabel` values. Labels are
`appLabel` in `app/build.gradle.kts`; the label check is manual (`aapt2 dump badging <apk>` shows
`application-label`). The alpha stage is carried by the version name, not the launcher label.
Application IDs never change as part of branding.

## TV banner

`app/src/main/res/drawable-xhdpi/tv_banner.png` (320x180) is TV-only. It is built from the shared
icon (`mipmap-xxxhdpi/ic_launcher.png`, 104 px at left) on the flat splash background `#160A23`, with
the "24Seven.FM" wordmark in white (`splash_mark`) and "TV" in lavender `#C9A8FF` (`splash_accent`) and
at least 16 px of right margin. Regenerate it from the shared icon; do not hand-edit pixels. The
original banner clipped the wordmark at the right edge.

The banner is TV-only (mobile has no counterpart), so the parity script does not cover it. It is
referenced by `android:banner="@drawable/tv_banner"` in the manifest and shipped in `drawable-xhdpi`,
the conventional leanback banner bucket (other densities are scaled). Known limitation: the Google TV
launcher on the acceptance Streamer shows only the round app icon on Home and the Apps tab and never
renders the banner, so the banner is verified from the packaged resource and a rendered image, not
seen on a launcher.

## Known lint noise from shared files

Copying mobile's resources unchanged adds two lint warnings that are accepted rather than fixed in TV, since
editing them would break parity: `VectorPath` on `splash_icon.xml` and `UnusedResources` on `window_background`
(TV uses `tv_window_background`).

## Not covered

Play listing assets (`docs/play-assets`), social images and typography beyond the banner are
outside this alignment and were not compared against mobile.
