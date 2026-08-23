# 24Seven.FM TV public-source inventory

The TV alpha reads only public, station-owned sources. It does not authenticate, post content, or use browser/account endpoints.

| Scope | Purpose | Source | Refresh boundary |
| --- | --- | --- | --- |
| Five stations | Live audio | Station-provided HTTPS `/live` stream | Media3 owned, continuous while playing |
| Five stations | Now Playing metadata | ICY headers from the selected live stream | Event-driven |
| Five stations | Current artwork | `/soap/FM24sevenJSON.php?action=GetCurrentlyPlaying` | Once per distinct title; failure never interrupts playback |
| Five stations | Station logo | Station-owned `/images/logos/logo.png`, packaged as pre-sized selector assets | Bundled app resource |
| SST, 1980s.FM, Adagio.FM, Entranced.FM | Public Queue and Recently Played | `/modules/Queue_Played/Queue_Played-gen.php` | Shared 60-second limiter |
| Death.FM | Public Queue and Recently Played | `/player.php?ajax_action=get_db_info&station=dfm&asin=` | Shared 60-second limiter |

Responses are bounded, station-scoped, and display only public track title, artist, album where supplied, artwork, and duration. Member names, request messages, credentials, sessions, and identifiers are excluded.
