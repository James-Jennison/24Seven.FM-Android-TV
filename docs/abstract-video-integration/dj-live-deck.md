# Live DJ deck

The deck now has larger rotating platter markings, incoming audio-history displays, bass/mid/high/energy meter strips, musical button glows and moving colored reflections. The camera, deck housings, original Soff Snep logo and full-width trusses remain fixed.

[Watch the on-device preview](/home/jjennison/Documents/Codex/2026-09-06/us/outputs/dj-live-deck/live-deck-preview.mp4) · [Build screenshot](/home/jjennison/Documents/Codex/2026-09-06/us/outputs/dj-live-deck/build.png) · [Drop screenshot](/home/jjennison/Documents/Codex/2026-09-06/us/outputs/dj-live-deck/drop.png)

The silent preview was recorded on the Google TV Streamer using a deterministic quiet/build/drop audio-state fixture. Recording started after the performance measurement window. In the Player, the effects use the live station signal. Both displays represent that same signal, not two independent tracks or raw waveform recordings.

Fine labels, screens and meters retain full photographic resolution in the existing equipment view. Broad reflections use the existing bounded lighting layer. Shared history geometry and batched meter drawing reduce rendering work. No additional view, audio session, decoder or timer was added. This is a 2D surface treatment over photographic artwork.

The [base MP4](/home/jjennison/Documents/Codex/2026-09-06/us/outputs/dj-live-deck/dj-soff-snep.mp4) is a static photographic plate in the existing silent 1080p30, 20-second media pipeline. Live deck and lighting motion appears in the native Player and the captured preview; it is not baked into that MP4. The original photograph/logo and their placement are preserved. Renderer source is included in source/.

Validation: compilation, lint and 112 unit tests passed. All five final physical Streamer tests passed in 126.429 seconds, including independent meter-band response, platter/reflection motion, LCD history, stale PCM, reduced white effects, composite lighting/lifecycle, fixed camera and live station playback. The decoder crossed its complete loop with 706 rendered buffers and zero dropped buffers. The decoder test identifies both native layers by type and checks changes only in the foreground deck over the real decoded photograph. Final screenshots were inspected for photographic alignment; the pixel tests do not claim automatic whole-perimeter registration.

The initial meter test sampled an LED gap. That test-authoring error was corrected with a meaningful whole-strip comparison; its original failed log is retained. All five final tests passed together afterward.

[Final device tests](/home/jjennison/Documents/Codex/2026-09-06/us/outputs/dj-live-deck/device-tests.log) · [Decoder counters](/home/jjennison/Documents/Codex/2026-09-06/us/outputs/dj-live-deck/counters.log) · [Build evidence](/home/jjennison/Documents/Codex/2026-09-06/us/outputs/dj-live-deck/build-evidence.json) · [Installed APK/source proof](/home/jjennison/Documents/Codex/2026-09-06/us/outputs/dj-live-deck/installed-proof.json) · [Changes](/home/jjennison/Documents/Codex/2026-09-06/us/outputs/dj-live-deck/changes.patch)

Performance improved from initial p95/p99 32/38 ms to 30/34 ms while retaining sharp details. Occasional tail frames exceed the approximately 33.3 ms presentation budget. Raw new-style/legacy jank remains 42.15%/60.22%; this is a disclosed limitation, not a 60 fps or zero-jank guarantee. [Timing evidence](/home/jjennison/Documents/Codex/2026-09-06/us/outputs/dj-live-deck/final-fixture-timing-gfxinfo.txt). Low residual risk: risk-5b4cc8b1d2df.

Closure: Claude, Gemini and DeepSeek COMPLETE with NO FINDINGS. change-0e645d9c3eb2 / review-cycle-24985e99de09; closure-c333065b5bb6 READY_WITH_RESIDUAL_RISK for the disclosed LOW performance limitation. Local debug installation only; no D-pad input, signing or publication.
