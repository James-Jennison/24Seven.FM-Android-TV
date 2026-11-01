# Lantern Drift: visible bass pulse and smooth floating water

The owner accepted Laser Light Show, then requested visible water, bass-reactive paper lanterns and lanterns that move with the water. After an initial installed revision, the owner reported no noticeable pulsing and choppy waves. Following the current physical preview, the owner said: “This is much more what I was looking for.” Preserve this visual version.

## Current implementation

The existing photograph is rendered as a native 64×40 mesh. One wave field drives water, paper bodies, rafts and the positions of their glow. Floating bodies translate and rock at their waterline; smooth normalized weights blend overlapping regions. Sky, shoreline and outer borders stay fixed. No assets were regenerated.

Measured SUB/LOW PCM drives a bass attack envelope relative to a slow bass floor. Paper texture itself darkens between attacks and returns to full brightness on kicks; a warm glow and reflections reinforce the pulse. A 120 ms decay separates hits even on mastered radio. Steady bass settles instead of producing artificial periodic pulses. A separate 1.2 s envelope smooths water amplitude, avoiding beat-induced geometry jumps. Reduced intensity attenuates both motion and light.

Lantern Drift alone updates on display frames. Its opaque photo mesh owns readiness and bypasses the silent ExoPlayer decoder entirely. Other scenes retain their existing cadence and playback behavior. Cached mesh geometry, shade masks and reusable buffers bound render work. No audio ownership, capture, permissions, WebView, telemetry, release or publication changes occurred. Accepted Laser and Signal drawing code remains byte-for-byte unchanged.

## Current validation

Candidate snapshot: `60d69bb4cca387a2e83adf5ea29393752d2e55b4`. Main implementation is unchanged since `68c9fbec30ae6eaed17aa6442a6049db57ae539d`; later commits strengthen test fixtures, display-relative cadence checks, real bass pulse contrast and physical GPU pixel checks. All 226 source files match the isolated candidate; 216 protected baseline files remain unchanged. Installed main/test APK hashes match their validated artifacts. Owner dirty checkout was preserved.

Compile, lint and 158 unit tests pass with zero failures, errors or skips. Twelve distinct physical checks pass across the retained test runs on the Google TV Streamer at1920×1080. One older light fixture initially failed because it supplied generic light instead of the new bass pulse; its corrected targeted rerun passes. That failure remains immutable in `pulse-device.log`. Other source and test changes were revalidated as applicable rather than repeating unaffected checks.

| Mode | Native water frames /8s | Draw p95 | Draw p99 | Live bass pulse range |
| --- | ---: | ---: | ---: | ---: |
| Normal | 477 | 17.14 ms | 24.13 ms | 0.034–1.000 |
| Reduced | 480 | 16.89 ms | 19.24 ms | 0.018–1.000 |

The actual display refresh interval is16.67ms. New gates require p95≤1.5 refresh intervals and p99≤2 refresh intervals; both pass. Native Lantern allocates no video decoder (zero decoded video buffers). Pause settling measured3.59s including queued PCM and freshness/decay; resume passes. This does not claim acoustic speaker/display synchronization or zero missed frames.

Software pixel checks verify fixed sky, no mesh holes, moving water/paper/rafts and substantial brightness change in all four shades. A separate physical GPU PixelCopy test verifies every actual photographed shade brightens by more than50%, with hardware frame counters and dark/bright captures retained. Existing native Laser and Signal pixel tests also pass. Normal app full-screen Lantern was restored and left playing for owner observation; no device test or automatic scene cycling remains active.

## Review and limitations

Review cycle `review-cycle-79f76c3fd2a7` identified the wasted decoder/native readiness dependency and overlapping mask discontinuity; both were corrected. Cycle `review-cycle-7c1c3eefde86` prompted stricter display-cadence and physical GPU pulse acceptance. Its performance finding quoted superseded timing; current measured results resolve that concern without changing shared immutable audio state. Findings and Codex dispositions are retained separately.

Final targeted closure: `review-cycle-f1df21c64fea`; Claude, Gemini and DeepSeek all COMPLETE with no unresolved substantive findings. Broker closure `closure-158cd441a4a2` is READY_WITH_RESIDUAL_RISK (the LOW entry-decode risk below). Current evidence cycle `evidence-cycle-870ca64cb077` is PASS. A failed evidence launch from concurrent heavy-build locking is retained; later non-concurrent validation provides separate passing evidence.

Residual LOW risk: first-entry bitmap decode is synchronous and cached thereafter; entry latency has not been separately measured. Physical acceptance is1920×1080; non16:9 fitting remains unchanged and unverified. Foreground reflections intentionally extend outside the camera frame. Shared speaker/display alignment work is outside this visual change and no unrelated risk has been accepted.

Evidence and hashes: `work/lantern-bass-water/pulse-delivery-proof.json`, `pulse-final-metrics.log`, `pulse-gpu-metrics.log`, `pulse-final-device.log`, `pulse-gpu-device.log`, reviewer responses/dispositions and screenshots. Earlier uncached and cached implementation evidence remains historical and does not validate later source.
