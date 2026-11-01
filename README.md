# 24Seven.FM TV

24Seven.FM TV is a separate, fully native Android TV listener application for the 24seven.FM network. It is not a mobile layout scaled to television, and it has its own application ID, release train, GitHub repository, and future Play listing.

The repository began from the verified Player Alpha 07 source baseline. That baseline is recorded in [the product contract](docs/TV_PRODUCT_CONTRACT.md); future mobile changes are not imported automatically.

## TV Alpha 01

The first alpha is intentionally listener-first:

- Five-station, anonymous live playback
- D-pad-first station selection and visible focus
- Now Playing metadata and artwork
- MediaSession playback controls
- Read-only Queue/History, sleep timer, and privacy-safe diagnostics as subsequent TV milestones

Sign-in, Chat, song requests, browser/email handoffs, notifications, commerce, and Cast are excluded until separately approved and certified for the TV experience.

## Development

```bash
bash ./gradlew :app:compileDebugKotlin
```

Branding and the shared-asset check with the mobile Player are described in [docs/BRANDING.md](docs/BRANDING.md).

See [the TV roadmap](docs/TV_ROADMAP.md) for the delivery sequence. Signing, Play delivery, and publication are always separately approval-gated.
