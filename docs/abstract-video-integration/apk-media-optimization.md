# APK media optimization — 2026-09-07

Owner authorized optimizing all four Lava Lamp videos and excluding the unused Ion Transit and Lantern Drift reference MP4s. All 22 visualizations, palette choices, posters, rendering behavior and audio ownership remain available.

## Size and encoding

Debug APK: **148.34 MiB**, down from **415.82 MiB** (267.48 MiB / 64.3% reduction). Four Lava assets now total 29.11 MiB instead of 275.88 MiB. Reference-video exclusion saves another 20.71 MiB.

| Palette | Original MiB | Optimized MiB | Full-loop SSIM |
|---|---:|---:|---:|
| Acid | 69.25 | 7.29 | 0.992859 |
| Neon | 67.56 | 6.29 | 0.993504 |
| Sunset | 69.36 | 7.47 | 0.993138 |
| Arctic | 69.71 | 8.05 | 0.991651 |

Encoding: libx264 slow, CRF21, maxrate8M/bufsize16M, High profile Level4.1, yuv420p, GOP60, faststart. All four remain 1920×1080,30fps,600frames,20seconds,video-only. CRF produced average bitrates below the proposed 5–8Mbps trial range while retaining measured quality; no resolution, frame-rate or duration reduction.

SSIM compared all 600 aligned frames per palette. Worst individual frame was Arctic300, SSIM0.989415. Inspected full-resolution original/encoded pairs at8seconds for all four palettes and the worst-scoring pair; minor lossy smoothing, no obvious framing/palette/lamp-detail regression. This is not lossless encoding or owner aesthetic acceptance.

## Native asset boundary

`VideoVisualizerAsset.usesBundledVideo` is false for Ion Transit and Lantern Drift. Their poster identities remain valid. Ion follows the existing native GLES dispatch; Lantern's existing native water branch returns before decoder creation. The decoder additionally filters out metadata without a bundled video. Encoded-video tests now select only actual encoded assets; catalog tests assert that both reference files are absent.

The six original videos are archived under `work/apk-media-optimization/originals/`, with hashes checked against the pre-change source. They are outside shipping app assets. Only isolated candidate generated APK output and incremental packaging state were archived before reassembly to avoid unused ZIP gaps. No Gradle clean, signing, release publication or unrelated changes.

## Evidence

Candidate `2752af930975a766d0bc5b910e6ec644c69dc491`. Installed main APK SHA256 `63bd1af40a5a4b519fb622405f4979c29578367f1e7e98a8538901adece3dd55` matches the local artifact; encoded video hashes inside the APK match evaluated media. Both reference MP4s are absent.

Compile/lint/163 unit tests PASS; debug main/test assembly PASS. Broker evidence `evidence-cycle-94dd831794f5` PASS with artifact integrity. Physical Google TV Streamer:4 tests PASS in197.623seconds. All four Lava palettes crossed a full loop, with Neon704/Sunset704/Arctic705/Acid704 rendered buffers and zero dropped buffers. Silence and foreground recreation passed. Actual live PCM playback,22-entry catalog/persistence/asset checks and native Ion movement/palette/pause/resume acceptance passed.

All root app source matches the isolated candidate.200pre-existing files/assets are byte-identical; only four media replacements, two media exclusions, one production metadata/decoder file and three associated test files changed.

Review cycle `review-cycle-b9457be04685` (Grok, Gemini, DeepSeek): pending. Normal Lantern Drift picker preview was verified with 245,631 changing water-region pixels between captures while its reference MP4 was absent. The owner’s saved Ion Transit selection was preserved and restored full-screen. No claim of runtime FPS improvement for unchanged renderers.
