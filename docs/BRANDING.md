# TV and mobile branding

The TV app and the mobile Player are separate products with separate application IDs, release
trains and listings. They share one visual identity.

## Shared with the mobile Player (kept byte-identical)

Launcher icon (foreground, monochrome, `mipmap-*`), the `app_logo.png` mark, `colors.xml`
(`launcher_background` `#160A23`) and `RadioTheme.kt`. `scripts/check-branding-parity.sh
<mobile-checkout>` compares exactly these 12 files read-only and exits non-zero on any
difference or missing file. It never writes to either tree. The mobile repository has its own
writer; change shared files there first, then copy them here unchanged.

## Names

| | Release label | Debug label | Application ID |
| --- | --- | --- | --- |
| TV | `24Seven.FM TV` | `24Seven.FM TV (Debug)` | `com.codeframe78.twentyfourseven.tv` |
| Mobile | `24Seven.FM Player` | `24Seven.FM Player (Debug)` | unchanged, separate |

Labels are `appLabel` in `app/build.gradle.kts`. The alpha stage is carried by the version name
(for example `0.1.0-tv-alpha02-hotfix1`), not by the launcher label. Application IDs never change
as part of branding.

## TV banner

`app/src/main/res/drawable-xhdpi/tv_banner.png` (320x180) is TV-only. It is built from the shared
logo and palette: a `#1F1033` to `#160A23` vertical gradient, the 104 px logo at left, the
"24Seven.FM" wordmark in white and "TV" in the logo's accent lavender `#BE9FED`, with at least
16 px of right margin. Regenerate it from `app_logo.png`; do not hand-edit pixels. The previous
banner clipped the wordmark at the right edge.

## Not covered

Play listing assets (`docs/play-assets`), social images and typography beyond the banner are
outside this alignment and were not compared against mobile.
