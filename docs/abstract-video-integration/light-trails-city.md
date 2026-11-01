# Light Trails: cinematic city redesign — 2026-09-07

Owner direction: replace generic Light Trails with cinematic reflective light trails, a glitzy Vegas/New York skyline, and a more exciting musical presentation. Keep the existing selection identity and other 21 styles.

The new native GLES scene uses a generated photographic city plate, eight racing cyan/violet/pink/gold exposures, broad wet-road reflections, moving anamorphic glints, and bass-responsive city highlights. It ships no new video. A dedicated freshness-checked controller freezes travel on absent/stale/future PCM and slows reduced-motion travel to22percent. Glow changes are bounded separately in reduced mode. GPU texture/program resources are allocated on entry and released on render-thread style exit.

The effect is rendered at576×324 (compatibility480×270) and filtered to the television output. This bounds GPU work but also softens the photographic backdrop. The source plate is retained in drawable-nodpi; no other scene render resolution was changed.

## Current evidence

- Candidate `706ea84770bc1caff4832d5e074d4fb2323dba21` in `work/realistic-three/candidate`.
- APK SHA-256 `ab973c3e5a4a216ed0564ef63f900efe6113f97f00de88bfca5bd109564fbdb2`; 152.21MiB. No signing/distribution/publication performed.
- 165 unit tests pass. Compilation and main/test APK assembly pass.
- Physical Google TV Streamer: `LightTrailsAcceptanceTest` passed in43.855s: actual fresh radioPCM, movement, absentinput/pause settling, resume, reducedmotion capture, pixel content, and3prior-VBO/release/re-entry cycles with no GL errors.
- 627 rendered frames in12s (~52.25fps); sampledGLp95 19.512ms, max21.908ms, zero intervals over41.667ms. This is callback timing, not directGPUutilization or a60fpsclaim.
- 206preexisting appsource/assets remain hash-identical; root/candidate allsource hashes match. Raw evidence under `work/light-trails-redesign/`.
- Earlier sharedphase absentPCM test failed and led to the dedicated controller. A later fixed3second radio readiness test failed; a bounded actualPCMreadiness wait corrected that test. Failed logs retained; successful evidence does not rewrite them.

## Review state

Initial cycle `review-cycle-63b73e5456eb`: all3reviewers returned. VBO state hardening implemented; other findings were rejected with caller/pass context or superseded by the dedicated finite-input controller. Immutable records and separate dispositions reside on change `tv-light-trails-city-final-20260907`.

Current acceptance change `tv-light-trails-city-acceptance-20260907`, targeted closure review `review-cycle-0a0ebb8c8b43`, evidence `evidence-cycle-350273ec7f36`: pending at this document revision. Owner aesthetic acceptance is not implied by technical evidence. Light Trails is left full-screen on the Streamer with playback confirmed PLAYING.


## Bass-line articulation and sky lighting revision

The owner found the first city version too subdued, specifically calling out bass lines in music such as Under Pressure and the sky lighting. The subsequent revision tracks low-frequency note attacks against a recent bass average, with a 22 ms attack and 110 ms release. Repeated source frames do not retrigger note attacks. Gaps between notes now reduce trail width and brightness substantially, rather than leaving the scene at a nearly constant intensity. This is measured low-frequency articulation, not instrument isolation or song recognition.

Six rooftop searchlight fans use a separate mid/high-band envelope for sky illumination, bass to open their beams, and an audio-driven sweep. Building highlights also vary across districts. Both movement clocks stop on missing, stale, or future audio; reduced motion bounds the speed and modulation independently.

Current candidate `28010272fdd92289e9b7adef88064f2232170be4`; installed APK SHA-256 `46480904f02d5149e874cf430b6fdbb020bd71e469023122b48a35bb378c8563`. All 166 unit tests pass, including repeated bass-note attacks and gaps with otherwise constant track energy. Physical Streamer acceptance passed in 43.981 seconds: 579 rendered frames in 12 seconds (about 48.25 fps), sampled p95 frame interval 21.552 ms, maximum 23.487 ms, zero intervals over 41.667 ms. Actual PCM, pause/resume, missing input, reduced-motion capture, and prior-VBO/release/re-entry checks passed. The installed APK hash matches the candidate, and 210 preexisting files remain unchanged.

This supersedes the earlier candidate and its closure task. Old durable run `autonomous-run-20260907-5b9808bf` was cancelled; its remaining worker was stopped before new source mutations. Current review change `tv-light-trails-bass-sky-20260907`, initial review `review-cycle-3412fd723b7a`, evidence `evidence-cycle-12d7970f60cf`: closure pending. Evidence and images reside in `work/light-trails-bass-sky`. The current Light Trails preview is left full-screen; subjective acceptance remains with the owner.


## Automatic station palettes

The owner approved the articulated bass and sky revision, then requested scene colors based on the actively playing station. Light Trails now uses the existing station brand palette for its trails, rooftop beams, roadside lights, and photographed colored illumination. Neutral architectural detail is retained. Both normal full-screen and picker-preview paths prefer `playback.stationId`, falling back to the selected station only when no playback station exists. A color update does not reset travel or recreate the scene. Other visualizers and their palette settings are unaffected.

| Station | Scene colors |
|---|---|
| SST | Gold / blue |
| 1980s.fm | Pink / cyan |
| adagio.fm | Amber / coral |
| death.fm | Lavender / indigo |
| entranced.fm | Mint / teal |
| Unknown / unavailable ID | Existing default lavender / violet |

The first audio sample now seeds the bass baseline to avoid inventing a note attack on entry/resume. A repeated-sample regression test verifies that onset decays instead of retriggering. The owner's approved note response remains intact.

Candidate `5d33df7dd384baba5331cbbdca8adb64c08d3dde`; installed APK SHA-256 `6cb55da400d757989fe784fef3616cc58d85c84781705227c6cfe85ac5592687`. All 167 unit tests and the physical acceptance test pass. The latter rendered all five station IDs plus unknown with visible color differences and no motion reset, alongside actual PCM/pause/resume and GL lifecycle checks. It passed in 54.322 seconds, with 556 rendered frames in 12 seconds (~46.33 fps), sampled p95 interval 22.491 ms, maximum 24.042 ms, and no intervals over 41.667 ms. These tests exercise palette ID changes; they do not claim streaming playback acceptance of every station. Root/candidate hashes agree, the installed APK matches, and 208 preexisting files are unchanged from the start of this extension.

Current change `tv-light-trails-station-colors-20260907`, review `review-cycle-4ec5d4b7ab99`, evidence `evidence-cycle-b5746ca958ff`. Evidence PASS; independent review closure pending. Current station preview is full-screen, with the observed automatic-idle setting left Off. Raw proof is under `work/light-trails-station-colors/`.


The final station-palette test replaces a fixed delay with verification of the expected GL-thread palette and two rendered-frame advances before PixelCopy. The physical rerun passed in 52.589 seconds. Candidate `1acca4e9880f4264d54149d229756abe8e91c320` changes only that test from the prior candidate; the production APK is unchanged. Final closure change `tv-light-trails-station-colors-final-20260907`, review `review-cycle-e2c3f5db7f93`, evidence `evidence-cycle-a1699209f6d5` are complete. Grok, Gemini, and DeepSeek returned no residual findings; evidence passed. Engineering closure `closure-6f0d90d89d98` is **READY**. Earlier pending entries above are historical and superseded by this final closure. The accepted LOW test-timing finding has a separate resolved disposition and retained initial evidence.
