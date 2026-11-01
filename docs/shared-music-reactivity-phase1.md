# Shared music reactivity — Phase 1 validation

Owner authorized the shared core and two proof mappings on September 6, 2026. Neon Kaleidoscope (OpenGL) and Emerald Resonance (video) are the proofs. Lantern Drift, Signal Deck and Laser Light Show retain their pre-task style-specific code and helpers; the baseline comparison passes. No clip replacement, BPM, beat-phase, structure/ML, microphone, recording, transmission, signing or publication was added.

## Implementation

The sole station player feeds the existing Media3 processor. A bounded four-block capture queue gives a single analysis worker owned PCM copies (maximum 1024 frames per block). Overflow invalidates the analysis epoch and drops analysis work, never playback bytes. Format changes become active on flush. Shutdown invalidates worker data and clears the application-owned repository.

The pure Kotlin shared core averages channel powers (so inverted stereo does not cancel), computes approximately 10 ms RMS/peak windows and 2048-point Hann FFTs with 1024-frame hops, and conditions bass/mid/treble, rolling energy and onset envelopes. Epochs, sample positions, separate spectral-window positions, sequences and onset IDs are explicit. Normalized controls are separate from existing calibrated dBFS measurements. Legacy calibration remains unchanged, including its synchronous FFT; this compatibility choice adds DSP work and is not a callback-cost guarantee.

Immutable shared state reaches the hot repository without a collector-dependent wrapper. Typed mappers provide distinct Neon and Emerald behavior. GL audio handoff coalesces to the latest snapshot; stale shared data settles to static rather than reusing old audio. Reduced-intensity mappings remain bounded.

## Timing boundary

State is explicitly `DecodeClock`. Its elapsed time is the time of bounded PCM capture, not speaker playback or display presentation. No sink estimate is populated, and no audible latency threshold is claimed. The output-timing prototype, route-specific acoustic/display measurement, and baseline CPU/allocation/underrun comparison remain acceptance work. Software freshness cannot substitute for those measurements. This document does not declare the entire timing milestone complete.

## Evidence

Evidence and preserved failed attempts are under `work/shared-reactivity-phase1/`. Main-checkout pre-existing changes were preserved. Debug artifacts are built from an isolated committed snapshot with a source SHA-256 manifest; the main dirty checkout is not represented as a clean commit.

- Integrated and remediated compilation, unit tests, lint, and Android test compilation pass. Latest direct run: 35 suites, 144 tests, zero failures/errors.
- Protected style comparison: `host-protected-designs.py` passes.
- Candidate wrapper now explicitly invokes tracked non-executable Bash scripts. Its new untracked-input check is verified using a disposable Git repository before Gradle runs. Earlier permission-denied build attempts remain recorded.
- Initial reviews: `review-cycle-f33bf591a491`; targeted remediation: `review-cycle-f8617af4c8ef`. Immutable findings and separate Codex dispositions are recorded in the broker. Follow-up findings were resolved through measured closure review `review-cycle-adc584ff8efd`; all three reviewers returned no remaining findings for the implemented MVP.
- Final committed candidate: `0b682a1fbff8484754c5ac72b3b9e917c4d407ec`. Three native Android instrumentation tests pass on the verified physical Streamer: byte-identical PCM16/float output across buffer sizes; repository clear on close; live Neon + Emerald rendering, pause and fresh FFT recovery. The final candidate was installed and launched.
- Final Streamer observation: 1920×1080 surface on a 60 Hz display; shared capture freshness p95 71 ms; Neon 120-frame window average 31.05 ms, p95 32.19 ms, maximum 34.11 ms; zero frames over the 41.67 ms diagnostic threshold. Test gate p95 ≤50 ms remains active. These are GL callback intervals, not GPU execution or sound-to-screen lag.
- Pre-fix Neon p95 was 191.93 ms with the shared mapping and 190.91 ms with the legacy mapping. Bounding the existing analytical shader to a 640×360 scene buffer (with filtered presentation) brought two measured post-fix runs to p95 32.26 and 32.19 ms. No shader design was replaced. The captured pattern was visually inspected without blank output, broken framebuffer or stretching.
- The native acceptance harness uses bounded lifecycle discovery and main-thread initialization, without virtual Compose clock or Espresso idle waits. Earlier failing/aborted harness attempts remain in the work evidence directory; they were not rewritten as passes.
- Broker validation: `evidence-cycle-4b6ef813c175` PASS (compile, 144 tests, lint, Android compile); `evidence-cycle-af59135f997d` PASS (current source/candidate manifest, clean committed snapshot, physical test result, and live installed app/test APK hashes). Both evidence artifacts have verified integrity. The general repository Evidence Profile was restored after the delivery-specific check.

## Orchestration

The repaired supervisor runtime was verified, but successor run `autonomous-run-20260906-b1dcdaa3` exhausted its continuation retry count and is FAILED. Its worker stopped; the interactive root completed subsequent implementation and validation. No independently running worker is claimed by this record.

## Delivery identity and acceptance boundary

Installed debug APK SHA-256: `bf4a0787c3d41e19fb424c296371bf12cdb6ef5f166cafd950a4beff2adef77f`. The installed instrumentation APK also matches its local artifact. `verify-delivery.py` checks the source manifest and performs read-only installed APK hash verification.

Implementation evidence closure: `closure-42d778b84d74` — `READY_WITH_RESIDUAL_RISK`. This covers the implemented MVP and measured rendering correction only. The unaccepted timing risk is recorded as `risk-d80061398c0b`; this closure does not accept the full M1/M2 output-alignment milestones.

**Owner acceptance remains pending for the decode-clock prototype.** Route-specific speaker/display alignment is unmeasured, and a sink-aligned feature timeline is not implemented. Accepting the prototype with this known boundary is an Owner decision under repository governance. Otherwise keep full Phase 1 timing acceptance open until synchronized external audio/display measurement and any required alignment implementation are complete.
