# Laser stage artwork

## Slim foreground revision — current

Owner rejected the tall foreground housing as visually dominant. Current asset is
`app/src/main/res/drawable-nodpi/laser_stage_led_slim.png` (1672 x 941), generated with
the built-in image tool. It retains a foreground position but reduces the housing
to a shallow batten. Owner explicitly rejected reducing wash intensity: final GL
and Canvas retain wash `(.003 + drive*.023)*(.3 + chase*.7)` and lens glow
`.12 + drive*.65*(.3 + chase*.7)`.

The final generated image has 19 lenses despite the requested 18. `LaserStageLayout`
shares measured pixel positions and handles the odd final lens without duplicating it.
Previous sibling images are retained but not decoded by the visualization.

Slimming prompt:

> Precise-object-edit. The foreground LED bar dominates too much. Change ONLY this bottom LED bar: make it a slim low-profile batten at the extreme bottom front lip, occupying ONLY bottom 7 percent of image (top at93 percent, bottom100 percent), still nearly edge-to-edge and clearly in FRONT of side truss feet. One row of18 small recessed unlit glass lenses on slightly angled top panel, lens centers near96 percent image height. Reduce housing height by about60 percent from input and make it dark charcoal with restrained reflections. Keep stage floor visible behind it. DO NOT move it to rear stage. Preserve all overhead projectors/lens coordinates, side trusses, black backdrop, perspective and framing unchanged. Photorealistic, subtle supporting equipment, not main focal point. No beams/glow/text.

Final lens-row edit prompt:

> Keep this image exactly as it is, especially slim low-profile foreground LED bar housing size, position, angle, stage and all overhead rigging. Change ONLY the bar's lens row: replace the many small lenses with EXACTLY EIGHTEEN (18) evenly spaced recessed dark oval lenses, one row, across same bar. Count eighteen, no extra lenses. Unlit dark glass, no beams/glow. Keep housing small/subtle. Everything else unchanged.

Final generated output: `exec-46d5d84f-3dbd-4f29-977c-6eb3b4220836.png`.

## Foreground LED revision — September 5, 2026

Current consumed asset: `app/src/main/res/drawable-nodpi/laser_stage_led_foreground.png`
(1672 x 941), generated using the built-in image-generation tool. Prior sibling assets
are preserved. The bar overlaps the truss bases in the foreground, with the floor
receding behind it and a substantial sloped lens panel and front face.
Actual generated lens count is 18, not the initially requested 16. Both renderers
use measured image-space centers approximately `x=.0465+.0534*i, y=.875`.
Nine paired banks produce live wash; six overhead laser fans remain. No light beams
are baked into the image. Additive GL wash contrast comes from low opacity, not draw order.

Final built-in edit prompt (input was the first enlarged bar revision):

> Precise object edit: keep overhead truss and all six hanging projectors EXACTLY unchanged. Move this enlarged foreground LED bar DOWN so its front face reaches the BOTTOM EDGE of the image, leaving NO floor visible in front of it. Place it clearly in front of both side truss bases, nearly edge to edge. Tilt the lens panel upward about 35 degrees, showing a broad sloping TOP panel with dark lenses; not a vertical front-facing row. Keep the existing row of 18 evenly spaced dark lenses. Top of housing about 86 percent image height, lens centers about 92 percent height, front bottom edge at100 percent. Stage floor visible only BEHIND bar, receding toward dark rear stage. All glass unlit, no beams or glow. Same photorealism, composition, dimensions. Change ONLY foreground bar position and tilt.

The generated placement differs from requested coordinates; integration uses the actual
measured centers above. Source output: `exec-1aa0d13a-c483-4e51-b804-bf88f710adf6.png`.

Generated with the built-in image-generation tool on September 5, 2026.
Asset: `app/src/main/res/drawable-nodpi/laser_stage_realistic.png` (1672 x 941).
This is generated photographic-style artwork, not a photograph of a particular venue.
The backplate contains no beams; all beams and aperture glow are rendered live.

## Generation prompt

Use case: product-mockup. Create a photorealistic 16:9 wide concert laser stage BACKPLATE for an Android TV visualization. No lasers or light beams: animated beams will be composited separately. Entire physical goalpost stage rig visible nearly edge to edge, black empty stage interior. Real silver aluminum box truss across top and at both sides, round tubes, welded cross braces, specular highlights, real depth and joints. Six professional black laser projector cabinets suspended evenly across overhead truss, two additional laser cabinets sitting at lower left and lower right on dark matte stage floor. All eight cabinets face camera, visible round glass laser apertures, machined housing edges, cooling vents, bolts, U-shaped mounting yokes, safety cables and clamps. Cabinet positions: upper six laser apertures at normalized image x .12 .272 .424 .576 .728 .88 and y .164; lower two apertures x .12 .88 and y .8925. Important exact aperture alignment for animated compositing. Black interior should remain unobstructed. Front camera straight on, slight visible top/side housing faces for depth, realistic low-key photographic lighting with neutral cool white metallic highlights, not blue cartoon. Upper truss near y .06, side truss near x .035 and .965. Floor horizon near y .92. Real concert equipment photographed on a stage, NOT vector art, NOT diagram, NOT simple gray geometric rectangles. No people, text, logos, screens, colored light beams, smoke, or bright lens glow. Resolution landscape 1920x1080 or equivalent 16:9.

## Integration

The generated apertures differ from the requested coordinates. Actual image centers were measured
and used instead: upper image-space x .144/.284/.427/.5715/.716/.8565, y .2135;
lower x .1465/.854, y .899. GLES converts these to clip space; Canvas uses image space.
Both paths fill the available rectangle consistently.

GLES asynchronously decodes on a single process-wide background executor, then caches one
non-mipmapped, linearly filtered texture (approximately 6 MiB at full size,
approximately 1.5 MiB in the low-memory tier). It releases the decoded CPU bitmap after upload
and releases the texture on leaving the scene. Pending decode is cancelled on exit/context loss;
the next entry rebuilds it. Canvas decoding uses Dispatchers.IO, with half-size fallback and
quarter-size gallery images on each axis. The static backplate replaces per-frame equipment geometry.
