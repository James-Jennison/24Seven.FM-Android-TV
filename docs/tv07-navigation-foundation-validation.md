# TV07 Navigation Foundation Validation

## Scope

This tranche introduces a typed modal navigation model for the existing Android
TV settings surfaces. It retains the current Compose dialogs/sheets rather than
introducing a second navigation framework.

- `General Options` returns to the player dashboard.
- Every settings detail route returns to `General Options` on Back.
- The selected route is saved/restored through `rememberSaveable`.
- Unknown restored route tokens safely return to the dashboard.

The adjacent visualizer and Lava Lamp palette work was intentionally preserved
as separate, pre-existing working-tree changes.

## Objective validation

| Check | Result |
| --- | --- |
| `bash ./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest --no-daemon --console=plain` | Passed before review remediation. |
| `bash ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon --console=plain` | Passed after saver coverage was added. |
| `git diff --check` | Passed. |
| Google TV Streamer 4K (`192.168.1.187:5555`) | Current debug build installed successfully. D-pad Down opened General Options; Select opened Idle Visualizer; Android Back returned to General Options with `Idle Screen` focused. |

The device check validates the visible route and focus behavior. It does not
claim process-death or configuration-change instrumentation coverage; those
paths are covered at the pure route/saver unit-test level.

## Review record

| Reviewer | Initial result | Resolution / closure |
| --- | --- | --- |
| Claude — architecture | Raised a possible Lava Lamp color-picker parent-route concern. | `DISPUTED`: current source has General Options as the sole picker entry point and the sheet’s Back action explicitly returns there. Closure: no findings. |
| Gemini — Android TV platform | Reported a possible discarded `backDestination()` return value. | `NOT APPLICABLE`: every production callback explicitly assigns `menuDestination = menuDestination.backDestination()`. Closure: no findings. |
| DeepSeek — adversarial correctness | Found missing serialization/restoration test coverage for the route saver. | `CONFIRMED` and fixed: pure token serialization plus tests cover null/dashboard, every known route, and an unknown retired token. Closure: no findings. |

## Remaining limitations

- This is a stable modal-route foundation, not a full multi-screen Navigation
  Compose migration.
- Playback, stream configuration, Home recommendations, and visualizer rendering
  behavior were outside this tranche.
