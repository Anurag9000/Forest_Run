# Forest Run — Remediation Continuation (2026-08-01)

This dated continuation record supplements `docs/AUDIT_LEDGER.md`. It records the additional direct-to-`main` implementation completed during the August 1 exhaustive audit sweep and keeps unresolved work explicit rather than implying release readiness.

## Implemented in this continuation

### Strict JSON evidence boundary

- Added `scripts/strict_json.py` for bounded, ambiguity-free evidence parsing.
- Duplicate object keys are rejected instead of silently taking the final value.
- Literal `NaN`, `Infinity`, and `-Infinity` are rejected.
- Finite-looking JSON numbers that overflow Python floating point, such as `1e400`, are rejected after conversion.
- Oversized integer literals are normalized into `StrictJsonError` rather than escaping as interpreter-specific `ValueError` failures.
- UTF-8 BOMs, invalid UTF-8, empty or oversized inputs, non-object roots where an object is required, and excessive nesting are rejected.
- A string-aware nesting pre-scan rejects deeply nested input before Python's parser can hit recursion limits.
- Files are rejected if size, modification time, or inode changes while they are read.
- Added `scripts/verify_strict_json_evidence.py` to recursively preflight selected evidence files/directories with deduplication and a bounded file count.
- Canonical release preparation strictly parses graphics, metadata, screenshot, capture-session, final-sidecar, physical-acceptance, trace, aggregate, and machine-summary JSON before semantic verification.
- Added adversarial parser, directory-expansion, numeric-overflow, oversized-integer, duplicate-key, BOM, depth, and release-wrapper ordering tests.

### Physical-device acceptance hardening

- The acceptance policy can no longer shrink below five mandatory device classes:
  - older phone;
  - mid-range phone;
  - high-refresh phone;
  - cutout/unusual-aspect phone;
  - tablet.
- The policy can no longer omit seven mandatory scenarios:
  - fifteen-minute ordinary play;
  - all entities;
  - dense Bloom;
  - lifecycle recovery;
  - settings/accessibility;
  - Garden transactions;
  - ghost persistence.
- One physical device identity cannot satisfy multiple device classes.
- Only haptics may be marked `not_applicable`; every other manual check must pass.
- Manual-check and approval objects reject unknown keys.
- Reviewer identities are normalized with NFKC and case folding, so case or Unicode variants cannot count as distinct reviewers.
- Candidate artifacts cannot double as scenario evidence.
- Resolved path aliases and hard links cannot make one physical evidence file count more than once.
- Manifest, artifact, and evidence-file sizes are bounded; hashing is streaming and rejects files that change during the read.
- Acceptance draft, final manifest, and optional summary paths cannot overwrite one another.
- Manifest and summary publication is one rollback-safe transaction rather than two independent atomic writes.
- A forced second-file publication failure restores both previous outputs and removes transaction debris.
- Added sparse-file tests for oversized drafts, artifacts, and evidence without allocating gigabytes.
- Added `scripts/compile_device_acceptance_bundle.sh` as the strict operator entrypoint:
  1. strict-parse draft;
  2. compile and validate transactionally;
  3. strict-parse both outputs;
  4. independently revalidate the published manifest;
  5. validate every referenced deterministic trace against the candidate.
- Added `docs/DEVICE_ACCEPTANCE_COMPILATION.md` describing the canonical workflow, minimum matrix, identity preservation, bounds, publication semantics, and limits of automated validation.

### Physical evidence aggregation and baseline comparison

- Added `scripts/aggregate_device_acceptance.py`.
- Every input manifest is strictly parsed and independently accepted before aggregation.
- Reports summarize minimum, mean, and maximum p95 frame time, p99 frame time, slow-frame ratio, peak PSS, crashes, and ANRs:
  - globally;
  - for every mandatory device class.
- Reports include session counts, distinct physical-device counts, exact session IDs, duration distributions, and worst-case threshold headroom.
- Optional accepted-baseline comparison reports mean and maximum deltas globally and per device class.
- The tool intentionally does not invent an acceptable regression tolerance.
- Invalid or tampered evidence is rejected before any distribution is calculated.
- Output is strict finite JSON published through flushed atomic replacement.
- Added `scripts/aggregate_device_acceptance_bundle.sh` as the canonical operator path with:
  - strict candidate/baseline preflight;
  - candidate, baseline, and output alias rejection;
  - structural validation of referenced deterministic traces;
  - aggregation;
  - strict output verification.
- Added `docs/DEVICE_ACCEPTANCE_AGGREGATION.md` and adversarial calculation, matrix, alias, tamper, and operator-ordering tests.

### Deterministic scenario trace and soak evidence

- Added `DeterministicScenarioTraceRecorder`, a bounded in-memory recorder that never owns gameplay or disk state.
- `DebugScenarioScript` now records by default; existing callers do not need to opt in.
- Only successfully dispatched actions are recorded. A dispatch exception leaves the action pending and emits no false evidence.
- Completed traces remain available after scenario clear as detached immutable snapshots.
- Added `DebugScenarioInputContract` for catalogue-wide validation of finite chronological schedules, balanced held inputs, and jump/duck exclusivity.
- Added candidate- and artifact-bound strict JSON trace encoding.
- Floating-point times are canonicalized to integer microseconds for byte-stable cross-device evidence and hashes.
- Added digest-checked Android `AtomicFile` persistence with deterministic scenario filenames.
- Added `scripts/validate_scenario_trace.py` to verify exact keys, schema, candidate/artifact binding, canonical scenario membership, event count, sequence, chronology, lateness arithmetic, action vocabulary, bounds, and payload digest.
- Added `scripts/validate_manifest_scenario_traces.py` so correctly hashed but structurally false trace files cannot pass through acceptance compilation or aggregation.
- Added `docs/DETERMINISTIC_SCENARIO_EVIDENCE.md` and runtime, codec, persistence, schema, manifest-binding, and failure-path tests.

### Runtime timing and malformed-input corrections

- `GhostPlayer.update()` now makes nonfinite or nonpositive deltas complete no-ops before elapsed time, frame selection, visibility, or dense-hazard suppression can change.
- Added regression coverage after the ghost is already visible to prove malformed updates cannot mutate playback state.
- Removed the duplicate raw nanosecond interval gate from `LeitmotifManager`.
- `EvaluationThrottle` is now the sole tempo/Bloom evaluation authority, retaining its rollback/reset-safe semantics.
- Added source contracts preventing reintroduction of `lastParameterUpdateNs` or raw subtraction gates.

### Progression and relationship arithmetic primitives

- Added `SafeProgressionArithmetic`:
  - negative restored counters normalize to zero;
  - increments saturate at an explicit maximum without overflow;
  - elapsed-time thresholds reject invalid timestamps and clock rollback;
  - nonnegative ordered timestamps use subtraction that cannot overflow.
- Added exhaustive negative, saturation, zero-maximum, rollback, and `Long.MAX_VALUE` tests.
- Added `FamiliarityWarmthScoring` with independent modifiers for:
  - stage;
  - three clean passes;
  - five clean passes;
  - two spares;
  - three-kindness streak;
  - five encounters.
- Added tests proving every modifier contributes independently and the authored `PERSONAL` and `BONDED` thresholds remain 5 and 7.
- The pure replacements are ready; the two large authored catalogue call sites still require a narrow verified patch and are listed below rather than falsely marked integrated.

### Candidate-bound store and release evidence

- Checked-in release assets receive structural validation rather than header-only checks:
  - PNG chunk order, CRC, IHDR, zlib stream, scanline geometry, and filename-declared frame divisibility;
  - SFNT table directory and required font tables;
  - Ogg page CRC/sequence/BOS/EOS and Vorbis/Opus identification;
  - WAV chunks, MP3 frame headers, and M4A box structure.
- Cross-owner tests lock the authored asset catalogue, runtime release validator, and Play-required audio list together.
- Store graphics are generated through staging and atomic publication, bound to the candidate SHA and all generator/font/sprite source hashes, and independently verified for exact file set, dimensions, mode, size, and digest.
- Store metadata is exact-set checked, candidate-bound, UTF-8/NFC/LF normalized, whitespace/control/template sanitized, length bounded, and per-file hashed.
- Screenshot capture validates the exact scenario readiness marker and resumed Activity before and after every screencap, then structurally validates and atomically sidecars each image.
- The raw capture set is independently revalidated before one atomic session manifest is published.
- Curated screenshot verification binds final images to the raw capture session and one candidate/APK/device/package/Activity identity.
- Release summaries are independently checked against current bundle/mapping hashes, candidate, screenshots, final application identity, R8 evidence, signing state, and dry-run disclosures.
- `prepare_main_release.sh` aligns `JAVA_HOME`, verifies source assets/graphics/metadata, quarantines prior summaries, restores them on failure, verifies the new summary, and rechecks local and canonical `origin/main` identity.
- Added `docs/STORE_EVIDENCE.md` for generation, finalization, verification, invalidation, and manual approval boundaries.

### Persistence, Garden, pacing, catalogue, and narrative contracts

- Persistent-memory selectors fail closed when caller minima are zero or negative.
- Repeat-killer severity uses `Long` arithmetic and deterministic tie-breaking.
- Known persistent string sets are bounded, sorted deterministically, and sanitized while unknown future preference keys remain preserved.
- `CostumeStyle.NONE` cannot persist as an unlocked reward or blank featured card.
- Relationship milestone repair accepts only the six tracked relationship entities.
- Garden displayed prices, purchase charging, startup repair bounds, and plant count are regression-locked to `GardenEconomy`.
- Opening spawn guidance replaces zero, negative, NaN, or infinite intervals with a conservative positive cadence.
- Biome and deterministic-scenario catalogues have executable ordering, uniqueness, coverage, and variant invariants.
- Player jump-force interpolation is finite, bounded, monotonic, and fail-closed for malformed holds.
- Story fragment and rest-quote catalogues have deterministic identifier, content, length, whitespace, and control-character invariants across rich state combinations.

## Validation truth

The implementation above is committed directly to `main`, but this environment still cannot provide a local exact checkout or reliably observe push-triggered GitHub Actions conclusions. Consequently:

- the latest tree is **not** described as exact-head green;
- newly added Python, JVM, Robolectric, Android, shell, and source-contract tests still require execution on one frozen head;
- structural validators prove internal consistency only when run against the real files;
- physical acceptance tooling does not manufacture device measurements, internal-track delivery, reviewer actions, or store approval.

## Remaining implementation debt

### Precisely isolated source call sites and large-owner boundaries

1. `RelationshipArcSystem.familiarityWarmth()` still contains the old conditional-arithmetic expression. `FamiliarityWarmthScoring` now implements and tests the correct independent sum, but the roughly 1,400-line authored dialogue catalogue requires a narrow verified call-site substitution rather than whole-file replacement.
2. `ReturnMomentsSystem.recordRunOutcome()` still uses raw `roughRunStreak + 1`, and long-absence detection still uses raw timestamp subtraction. `SafeProgressionArithmetic` now implements and tests both replacements, but the large authored return-moment catalogue still requires two narrow call-site substitutions.
3. `ParallaxBackground` and `GameView.update()` still rely on the production render-thread finite-delta contract at their large public coordination boundaries.
4. `MainMenuScreen.onTap()` can advance its ritual on a synthetic nonfinite direct-call coordinate after both hit regions compare false. Real Android `MotionEvent` coordinates are finite; the exact one-line admission guard is mapped but the 600-line art/UI owner should not be whole-file replaced through the current connector.
5. `SaveManager.hasGhostRun()` checks only the `AtomicFile` base path and does not validate or recognize a recoverable backup, while actual runtime loading correctly validates both. The unused convenience method remains cleanup debt.
6. `GameView` remains a large coordinator and persistence ownership remains distributed. Both should be decomposed only through behavior-preserving seams after exact-head validation stabilizes.

### Required external gates

- exact-head host/release test execution;
- exact-head connected-emulator behavior execution;
- representative physical-device matrix and approved measured thresholds;
- signed minified upload artifact using real credentials;
- direct installation and internal-track delivery of that exact artifact;
- certificate, package, version, and artifact receipt verification;
- final artwork, animation, screenshot, metadata, audio, haptic, accessibility, thermal, battery, and long-session review;
- privacy, data-safety, content-rating, target-audience, and current store-policy approval.

Until those implementation and external gates are closed, Forest Run remains a feature-rich alpha rather than an upload-ready release candidate.
