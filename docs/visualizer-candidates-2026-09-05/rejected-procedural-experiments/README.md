# Ten original reference-inspired visualizations

Owner correction: the clips are inspiration, never the visualization itself.
The current implementation renders native Canvas scenes from geometry and live PCM
envelopes. No clip, extracted poster, video decoder, or second media player is used.
All existing styles, saved enum names, and the accepted Laser Light Show are preserved.

| Reference | Original scene | Construction |
| --- | --- | --- |
| 10187 | Emerald Resonance | Mirrored interference membranes |
| 55598 | Aurora Veil | Overlapping flowing curtain folds and star dust |
| 121032 | Spectral Silk | Deforming particle fabric |
| 121536 | Orbital Pulse | Tilted counter-rotating dotted constellations |
| 121756 | Chromatic Blossom | Layered curved translucent petals |
| 121759 | Electric Synapse | Branching tendrils with traveling light nodes |
| 121770 | Stardust Ignition | Perspective particle trails |
| 144257 | Violet Circuit | Staggered chevron rails and light packets |
| 165233 | Prismatic Passage | Twisting triangular depth passages |
| 165234 | Ion Transit | Segmented cylindrical traffic lanes |

`media-manifest.json` retains source provenance. `source-contact-sheet.jpg` contains
the source samples inspected at 10%, 50%, and 90%; it is not output from the new renderer.
`reference-media/` preserves the former bundled reference copies and posters outside
Android source sets. `superseded-video-implementation/` preserves the replaced video
implementation and its historical documentation/tests for recovery, not execution.
Do not ship these reference files as app assets or interpret the old documentation
as current behavior. Redistribution permission for the clips is not established.

Current validation: debug compilation, unit suite, APK assembly and lint passed.
Physical Streamer rendering, live-audio observation, performance, and independent
review are still pending; deterministic fixture tests do not prove those gates.
