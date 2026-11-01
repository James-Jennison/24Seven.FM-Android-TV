# DJ wall projection compositing

The original Soff Snep artwork remains in the booth video behind the native lighting overlay. Removed its bitmap-shaped erasure and central radial light suppression, so moving beams, laser fans, haze and washes cross the logo. Only the foreground deck retains its soft atmospheric occlusion. Original media, fixed camera, projector props and audio analysis are unchanged.

Validation: compile/lint and 112 unit tests passed. All 3 affected physical Google TV Streamer tests passed in 92.715 seconds, including 960 identity-camera checks, quiet/build/drop animation, deck seam, inactive projector envelopes/opacity, lifecycle/reduced intensity and real station PCM/video progress. Installed APK hash matches the built APK. Codex inspected build/drop screenshots: beams cross the lettering and cat artwork; stronger washes soften it without erasing the design. This is an overlay compositing correction, not a physically based projector simulation.

Technical review: all three closure reviewers returned NO FINDINGS in review-cycle-9317687d8f89; closure-048c9298b462 is READY. Added centered logo-region numerical coverage after initial review: build/drop RGB changes 34.61/56.01, artwork contrast 93.60/76.98, zero near-white pixels. The prior base-video SSIM check remains unrelated to the unchanged media. No D-pad input or publication performed.
