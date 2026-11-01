# TV and mobile branding

The TV app and the mobile Player are separate products with separate application IDs, release
trains and listings. They share one visual identity.

## Shared with the mobile Player (kept byte-identical)

`scripts/check-branding-parity.sh <mobile-checkout>` is the single source of truth for the list. It
compares exactly 12 files read-only: the two adaptive-icon drawables (`app_icon_foreground.xml`,
`app_icon_monochrome.xml`), `app_logo.png`, both `mipmap-anydpi-*` `ic_launcher.xml` files, the five
density `ic_launcher.png` files, `values/colors.xml` (`launcher_background` `#160A23`) and
`RadioTheme.kt`. It exits non-zero on any difference or missing file and never writes to either
tree. Run it against a clean mobile checkout; a dirty tree may not represent the mobile
repository's committed state. The mobile repository has its own writer: change shared files there
first, then copy them here unchanged.

## Names

| | Release label | Debug label | Application ID |
| --- | --- | --- | --- |
| TV | `24Seven.FM TV` | `24Seven.FM TV (Debug)` | `com.codeframe78.twentyfourseven.tv` |
| Mobile | `24Seven.FM Player` | `24Seven.FM Player (Debug)` | unchanged, separate |

**This naming scheme is a provisional writer default; the Owner has not explicitly approved it.** It
is reversible by editing the two `appLabel` values, and release publication should wait for that
approval. Labels are `appLabel` in `app/build.gradle.kts`; the label check is manual
(`aapt2 dump badging <apk>` shows `application-label`). The alpha stage is carried by the version name
(for example `0.1.0-tv-alpha02-hotfix1`), not by the launcher label. Application IDs never change
as part of branding.

## TV banner

`app/src/main/res/drawable-xhdpi/tv_banner.png` (320x180) is TV-only. It is built from the shared
logo and palette: a `#1F1033` to `#160A23` vertical gradient, the 104 px logo at left, the
"24Seven.FM" wordmark in white and "TV" in the logo's accent lavender `#BE9FED`, with at least
16 px of right margin. Regenerate it from `app_logo.png`; do not hand-edit pixels. The previous
banner clipped the wordmark at the right edge.

The banner is TV-only (mobile has no counterpart), so `check-branding-parity.sh` does not cover it.
It is referenced by `android:banner="@drawable/tv_banner"` in the manifest and shipped in
`drawable-xhdpi`, the conventional leanback banner bucket (other densities are scaled). Known
limitation: the Google TV launcher row used for acceptance shows the app icon, not the banner, so
the banner has been verified from the packaged resource and a rendered image, not seen on a
launcher.

## Not covered

Play listing assets (`docs/play-assets`), social images and typography beyond the banner are
outside this alignment and were not compared against mobile.
