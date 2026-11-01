# Ion Transit: original metallic tunnel

Owner scope: use the existing Ion clip as the visual reference; match its cinematic tunnel, correct the camera angle, add independent color choices, make the walls visibly metallic, and rotate the tunnel. Original media and the stable Ion selection key are retained. The visible scene now uses native GLES; no WebView or playback ownership changes.

## Implemented behavior

- Off-axis camera: vanishing point approximately (442,518) at1280×720; banked foreground wall fills the upper right.
- Analytic cylindrical panels, beveled seams, brushed silver highlights, colored rail reflections, emissive blue/orange lights and depth haze.
- Continuous rotation and forward travel follow measured PCM energy; bass and attacks affect light intensity. Stale, missing or paused audio freezes travel/rotation. Reduced motion slows travel/rotation and lowers exposure.
- Menu → Tunnel Colors, when Ion is selected: Blue / Orange, Violet / Cyan, Emerald / Gold, Red / Ice. Stable preference names are independent of Lava Lamp colors; unknown saved names fall back to Blue / Orange.
- Final bounded scene buffer704×396 (compatibility640×360), filtered to the physical1080p display.

## Validation

Candidate `b78c4c52f9064eb6216c60bcd1bd02ab054c3f48`; root app source hashes match the isolated clean candidate. Installed main APK SHA256 `1ab4cc8abc608916ebd43872b02551fde891d0520808c5a5359c5049de5cc1b3`; installed test APK SHA256 `07fdae9d26bbd6527bad545dd31f42b42c132ac570959a38281317c65d2400bc`. Both read back from the verified Google TV Streamer.

Compile, lint,162 unit tests and Android test compilation PASS; broker evidence `evidence-cycle-54b31755dcaa`, artifact integrity verified. Debug main/test assembly PASS.

Final physical live shader test PASS: fresh actual radio PCM, movement, missing-audio freeze, four distinct GPU palette captures, reduced motion, real playback pause/resume.494 frames over12seconds (~41fps);120-sample timing average24.27ms,p95 27.31ms,max33.27ms; zero intervals over41.667ms in that window. This is not a60fps or universal zero-jank claim.

Unchanged palette UI passed a physical D-pad test covering every selection and Back. Unchanged preference implementation passed physical recreation/persistence, Lava independence and unknown-name fallback checks. Later changes touched only the shader, FBO dimensions and matching dimension assertion.

Actual GLES shader images at travel0 and24 show90° rotation (angular correlation0.928 versus -0.076 without rotation). Silver highlight masks contain20,048 and132,002 pixels. Two normal-app physical screenshots independently show rotated rail axes and moving broad metallic highlights with the fixed vanishing point. This establishes rendering behavior, not owner aesthetic acceptance.

Preserved failure evidence: initial960×540 buffer failed419 frames/12s; metallic768×432 revision failed444 frames/12s. The704×396 final revision passes the unchanged >450 frame gate. An initial grouped metadata regression was caught by unit tests and corrected before device work. Logs are retained in `work/ion-transit-original/`.

218 pre-existing app source/assets remain byte-identical. Accepted Lantern Drift, Laser Light Show, Signal Deck, Lava Lamp and DJ Soff Snep drawing paths and shared playback/audio implementations are preserved.

## Review and delivery

Initial independent cycle `review-cycle-f88926c919c9` completed with Claude, Gemini and DeepSeek. Current physical performance and explicit rotation evidence resolve the validation findings; lower-than-minSdk fallback and blend-state concerns are rejected with source evidence. A hypothetical fifth palette is outside the requested four-family scope. Gemini's initial60fps statement was rejected against actual measurements.

Final closure cycle `review-cycle-2c4486d937f9`: Claude, Gemini and DeepSeek each returned NO FINDINGS and supported the recorded dispositions. Broker `closure-cad242889327` is READY. Normal full-screen Ion preview is running on the Streamer and left undisturbed. No signing, distribution or publication.
