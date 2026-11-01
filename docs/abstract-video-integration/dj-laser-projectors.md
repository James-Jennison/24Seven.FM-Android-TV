# DJ laser projector anchoring

The owner identified four fan origins that looked like light emerging from bare speakers. `RaveLightingView` now uses shared lens coordinates for the fans and visible shaded projector housings. The lower pair rests on rear cabinets with feet/contact shadows; the upper pair is moved onto the truss with explicit hanging yokes and clamps. Recessed apertures, vents, fasteners and small status indicators give the origins a physical source.

Opaque equipment is drawn after the light-layer haze and masks, so drops do not dissolve the bodies into the beams. At zero audio energy, the light layer is skipped while the unlit equipment remains. Existing background/release/style-switch visibility is unchanged. The original video, logo, controller and PCM path are unchanged. Equipment is native shaded geometry on the existing bounded hardware surface, not newly generated photographic footage.

Physical screenshot inspection confirms the new origins and supports. Final build/device/reviewer closure is recorded in the associated output evidence; all three final reviewers completed with no substantive findings; closure-549829f1b387 is READY. No D-pad input or publication.

A shared activation gate keeps each aperture/status light consistent with fan visibility. A physical Android bitmap regression verifies residual cue envelopes cannot light an inactive fixture and that mounts remain fully opaque across quiet and bright haze. [Android Paint.setColor(int)](https://developer.android.com/reference/android/graphics/Paint) replaces the alpha component as well as RGB; the proposed alpha-leak finding was rejected on documented API behavior and rendered-pixel evidence, and the originating reviewer accepted that rejection during final closure.
