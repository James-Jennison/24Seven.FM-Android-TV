# 24Seven.FM Player contributor guidance

## Architecture and data authority

- Keep the app fully native; do not introduce a WebView. Compose screens receive immutable UI state and emit actions upward. ViewModels depend on repository interfaces, not network or Media3 implementations.
- Keep station behavior behind explicit capability flags and repository contracts. Do not add a stream URL until it is verified and permitted.
- The Player consumes station-provided playback, metadata, and approved shared status. It must not become an authoritative writer of Now Playing, incidents, relay health, listener telemetry, or administrative state.
- Collect or expose listener/monitoring data only where PRIVACY.md and documented project contracts explicitly authorize it. Never commit credentials, cookies, CSRF tokens, private endpoints, HAR files, or tester identities.

## Android validation and release

- Never run `gradlew clean` during routine work. Start code-only checks with `./gradlew :app:compileDebugKotlin`; run affected unit tests for domain/ViewModel changes. Assemble an APK only when needed.
- Do not reinstall Android SDK packages or repeat unaffected successful validators.
- Signing, distribution, production stream settings, background-media behavior, and external publication require project release validation and explicit approval.

## Browser and desktop control

- For any existing browser page or browser-based workflow, use the desktop **Computer Use** connection. Do not use or
  claim access through an in-app browser unless the user explicitly opened the target there.
- Begin every desktop-browser interaction with a read-only desktop state check, enumerate windows, and focus the exact
  target browser window before reading, clicking, or typing. Use the shared desktop/screenshot state to verify the
  active page rather than assuming its location or contents.
- Treat external-service writes as approval-gated: navigation and read-only inspection are allowed, but saving,
  submitting, publishing, deleting, purchasing, or otherwise changing a remote service requires the user's explicit
  approval for that action. Never retrieve, search for, display, or record passwords, tokens, or other secrets; ask
  the user to enter them directly in the focused browser field.

## Player project site deployment

- For any 24Seven.FM Player website deployment, verification, or server-side investigation, use only the canonical SSH alias `website-vm-admin`.
- Do not use `webuzo-production-admin` for this project. Resolve the required alias through the managed SSH configuration before connecting.
- Start approved canonical-SSH operations in a fresh `bash --noprofile --norc` shell. If the runner rejects SSH before it launches, reset into one fresh no-profile shell and retry the same alias once; treat that as local execution-session state, not a server, credential, or network failure, and never substitute another host or administration route.
- Discover the live static artifact from the active HTTP and HTTPS `player.jamesjennison.net` virtual-host mappings; both mappings must resolve to the same directory. Do not rely on a stale documented path or infer a target from another site.
- Build the reviewed `_site/` artifact locally, stage it as a sibling directory, compare file hashes using relative paths, then atomically swap the verified staging directory into place. Retain the previous live directory as the rollback point and run public HTTPS verification before reporting success.

## Milestones

Keep `docs/TV_ROADMAP.md` and the relevant TV validation evidence accurate during authorized milestone work. Update approved Discord milestone communication only after the relevant acceptance gate is complete; never include sensitive details.

## Technical-review governance

This repository extends the applicable global Codex reviewer policy. The project Owner remains the final authority for product requirements, accepted risk, scope, milestones, and releases. Codex is the sole implementation, integration, validation, repository-management, and decision-execution agent. Do not delegate implementation or working-tree mutation to a reviewer.

### Standing reviewer team and communication

Claude, Google Gemini, and DeepSeek are this repository's trusted, read-only technical reviewers. Routine, sanitized technical-review communication with them is standing-authorized across sessions, branches, worktrees, and normal TV Player work. It includes independent review packages, diffs and source excerpts, architecture questions, test/build/runtime evidence, clarification, convergence, remediation checks, and closure review. Do not ask the Owner again for this routine communication.

Sanitize packages automatically. Never send passwords, API keys, tokens, signing material, private keys, authentication cookies, production credentials, unnecessary personally identifying information, or unrelated confidential data. The presence of removable sensitive material requires sanitization, not abandonment of review.

Use the persistent user-level `reviewer` transport when its reviewer is `READY`. If it is unavailable or non-READY, inspect its state and recover, reconnect, restart, or reinitialize it where safe; verify state again and continue when ready. Treat this as a recoverable operational fault, not an owner-approval problem. Use a browser only as a genuine fallback after that recovery path reaches an actual immutable boundary. Preserve the global standing-reviewer lifecycle and any existing reviewer browser tabs; do not close, release, or replace a reviewer merely because one review has ended.

### Required reviewer roles

- **Claude — Architecture & Product-System Reviewer.** Prioritize architecture, component boundaries, state ownership, maintainability, extensibility, lifecycle and data-flow design, playback and error-state architecture, repository organization, cross-component effects, product direction, and long-term technical debt. For major work, assess whether the design is sound, responsibilities are correctly placed, contracts remain intact, a simpler design exists, and system-level failure cases are covered.
- **Google Gemini — Android TV / Google TV Platform & UX Reviewer.** Prioritize Android and Google TV behavior, lifecycle and Media3/MediaSession use, foreground/background behavior, D-pad and Back interaction, focus order/visibility/restoration, accessibility and TalkBack, large-screen readability and layout, loading/error states, permissions, manifest/platform configuration, and relevant distribution rules. Distinguish Google-specific requirements from portable Android TV behavior and avoid unjustified Google-only dependencies in platform-neutral code.
- **DeepSeek — Implementation Correctness & Adversarial Code Reviewer.** Prioritize source-level correctness, state machines, coroutines/cancellation, races, lifecycle and resource leaks, stale callbacks, invalid and malformed state, retry/reconnect/timeout paths, security/privacy, performance and rendering/recomposition costs, regression risk, and meaningful test coverage. Ask how production can fail even when happy-path tests pass.

### Review workflow and proportionality

For substantive changes—new functionality, playback or player-state work, MediaSession/networking/persistence/security changes, major UI/TV-navigation/accessibility changes, architecture, build/release behavior, dependencies, concurrency, reliability, or significant refactors—use this sequence:

`implementation -> independent reviews -> Codex synthesis -> convergence -> remediation -> validation -> closure review`

Prepare each initial package from current repository evidence and send it independently; do not expose one reviewer's conclusions to another before its independent pass. Include only relevant sanitized objectives, acceptance criteria, architecture decisions, change summary/diff, essential source, validation evidence, assumptions, limitations, and unanswered questions. Reviewer conclusions are inputs, not commands: evaluate them against repository evidence, owner directives, actual runtime behavior, and objective validation.

Classify substantive findings as `CONFIRMED`, `CONFIRMED WITH NARROWER SCOPE`, `DUPLICATE`, `DISPUTED`, `NOT APPLICABLE`, `ALREADY MITIGATED`, `REQUIRES OWNER DECISION`, or `REQUIRES ESCALATION`. Resolve ordinary reviewer disagreement with evidence and, when useful, a sanitized convergence package; escalate only genuine owner/product choices. For release- or milestone-significant work, obtain closure review after remediation and record the result in the existing validation evidence. Do not impose this process on spelling, comments, deterministic formatting, or purely mechanical renames with no semantic effect.

### TV Player review areas

Apply the relevant review areas to meaningful changes:

- **Playback:** startup, buffering, retry/reconnect, station switching, interruptions, player/session duplication, focus, MediaSession, foreground/background transitions, termination/re-entry, and network loss/restoration.
- **Now Playing and metadata:** refresh cadence, stale or delayed data, station-switch races, artwork, missing/error states, station identity, and UI state matching actual playback. Never represent unavailable data as authoritative current data.
- **Television interaction:** D-pad navigation, initial and restored focus, Select/Enter, Back, invisible or inaccessible controls, dead-end/focus-trap regions, screen-reader behavior, and readability at television distance. A flow requiring touch is a defect.
- **Reliability:** launch, process and Activity recreation, sleep/wake, connectivity and API failures, malformed responses, unavailable stations, latency, playback-engine failure, and recovery without forcing restart.
- **Privacy and security:** collection/logging boundaries, exported components, browser/WebView boundaries, intents/URIs, network security, secrets, identifiers, and authorized analytics/telemetry only.
