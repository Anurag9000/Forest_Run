# Forest Run — Exact Trace, Fairness, and Aggregation Audit

Date: 2026-08-02  
Repository: `Anurag9000/Forest_Run`  
Canonical branch: `main`

This supplement records the direct-to-`main` continuation after the August 1 post-merge and remediation audits. It is intentionally scoped to implementation that can be verified from source and generated evidence contracts. It does not assert that physical testing, signed-store delivery, or exact-head CI has completed.

## Repository topology verified during this tranche

- Work was committed directly to `main`.
- No development branch or pull request was created.
- The branch listing returned only `main`.
- No force-push or history rewrite was used.

## Closed implementation gaps

### 1. Exact deterministic scenario evidence

The earlier trace model recorded deterministic actions but did not bind evidence to every authored scenario and input field. The completed schema-v2 chain now provides:

- default bounded runtime recording in `DebugScenarioScript`;
- recording only after successful dispatch;
- immutable completed snapshots after scenario clear;
- exact replay matching through `DeterministicScenarioReplayContract`;
- rejection of incomplete, reordered, rescheduled, substituted, overflowed, or zero-action traces;
- `scenario_definition_sha256`, covering:
  - scenario name;
  - title and summary;
  - forced biome;
  - Bloom-start and ghost-playback policies;
  - ordered encounter time, entity, offset, and variant;
- `trace_contract_sha256`, additionally covering every authored deterministic input time and action;
- integer-microsecond event encoding;
- candidate commit and signed-artifact binding;
- payload SHA-256 and bounded atomic Android persistence.

### 2. Independent source reconstruction

`scripts/scenario_source_contract.py` independently reconstructs scenario and input definitions from checked-in Kotlin rather than trusting the runtime encoder.

The parser now:

- performs stable bounded source reads;
- handles balanced parentheses, strings, line comments, and nested block comments;
- parses signed decimal and exponent Float literals;
- converts through Float32 before canonicalization;
- mirrors Kotlin `roundToLong` for signed values, ties toward positive infinity, and `Long` saturation;
- permits signed finite encounter offsets while rejecting negative scenario time;
- fails closed on malformed or nonrepresentable source.

JVM and Python tests share fixed `CACTUS_READ` identities:

- scenario definition: `3246dd15f7e694d387d06430537bf1805e8d57a53a9bcd1bdc5dd13e929b524c`;
- trace contract: `edb682a29079ceaebf9c3e56c2f24362ce3335a0c1432e3803305a5dc2b58430`.

### 3. Mandatory trace gate

Canonical device-acceptance compilation and aggregation now require at least one valid, nonempty, exact schema-v2 trace.

A trace cannot pass merely because its file digest matches the manifest. Independent validation checks:

- exact root and event keys;
- candidate and artifact identity;
- source-reconstructed definition and input hashes;
- nonempty authored input script;
- exact event count;
- exact schedule and action at each sequence;
- chronological dispatch;
- nonnegative, exact lateness arithmetic;
- bounds and payload digest.

Tests include a forged trace whose action is changed and whose manifest digest is recomputed; aggregation still rejects it because authored semantics do not match.

### 4. Aggregation core parity and immutability

The shell wrapper was not left as the only security boundary. `scripts/aggregate_device_acceptance.py` itself now:

- strictly parses and accepts every manifest;
- requires exact traces;
- rejects candidate/baseline path or inode aliasing;
- verifies the manifest remains byte-, size-, timestamp-, and inode-identical across acceptance and trace validation;
- carries the parsed manifest's protected source set into publication instead of rereading it;
- prevents output from overwriting manifests, signed artifacts, or any evidence path, including symlink and hard-link aliases;
- rechecks output separation immediately before atomic replacement;
- emits strict finite JSON.

A mutation-in-the-middle test changes the manifest after trace validation and proves the aggregate fails closed.

### 5. Apples-to-apples hardware matrix

Broad device-class equality was insufficient for regression comparison. The aggregate now derives two anonymized identifiers:

- physical-device ID: NFKC/case-normalized manufacturer, model, and build fingerprint;
- device-profile ID: physical identity plus class, SDK, RAM, refresh rate, geometry, density, tablet, and cutout attributes.

This preserves correct semantics:

- changing refresh or geometry keeps the same physical identity but changes the comparison profile;
- changing OEM, model, or build fingerprint changes both;
- raw identity fields remain in the accepted manifest rather than being copied into the aggregate comparison surface.

Each class reports:

- physical-device count;
- anonymized physical IDs;
- anonymized profile IDs;
- exact session count and session IDs;
- metric distributions.

`comparison_matrix_sha256` covers sorted per-class profile IDs and session counts. Candidate/baseline comparison requires:

- identical device classes;
- identical exact trace-contract sets;
- identical session count per class;
- identical device-profile set per class;
- identical comparison-matrix hash.

### 6. Production spawn fairness envelope

`SpawnFairnessEnvelope` observes the actual production pacing chain:

- `GameConstants` speed curve;
- `DifficultyScaler.getSpawnGapPx`;
- `ReadabilityProfile` gap curve;
- `OpeningReadabilityGuide` through `SpawnPacing.requiredGapPx`.

It does not add another gameplay tuning curve.

Property tests cover more than 36,000 distance/time combinations and lock:

- finite bounded speed and gap values;
- effective gap never below the readability gap;
- guided opening reaction floors of 1.95 s, 1.78 s, and 1.58 s;
- exact opening-guide expiry;
- monotonic speed growth and maximum saturation;
- conservative malformed-input fallback;
- late-run minimum reaction time of `780 / 2000 = 0.39 s`.

## Additional runtime corrections retained

- malformed/nonpositive `GhostPlayer.update` deltas are complete no-ops;
- `EvaluationThrottle` is the sole Leitmotif tempo/Bloom timing authority;
- strict JSON rejects duplicate keys, nonfinite values, floating overflow, excessive nesting, and integers above the explicit 256-digit limit;
- aggregate output cannot alias any validated source;
- scenario trace storage verifies the payload digest before `AtomicFile` publication.

## Precisely isolated remaining source debt

The following work remains intentionally unclaimed because the available connector replaces whole files rather than applying verified narrow patches:

1. `RelationshipArcSystem.familiarityWarmth()` still needs substitution with the tested independent `FamiliarityWarmthScoring` result inside the approximately 1,400-line authored dialogue catalogue.
2. `ReturnMomentsSystem` still needs two narrow substitutions:
   - raw rough-run increment to `SafeProgressionArithmetic.saturatingIncrement`;
   - raw long-absence subtraction to `SafeProgressionArithmetic.elapsedAtLeast`.
3. `MainMenuScreen.onTap()` still needs its mapped one-line finite-coordinate admission guard inside the approximately 600-line art/UI owner.
4. `ParallaxBackground` and `GameView.update()` still rely on the render-thread finite-delta contract at large coordination boundaries.
5. `SaveManager.hasGhostRun()` remains an unused convenience method that does not recognize a recoverable `AtomicFile` backup even though real loading does.
6. `GameView` and distributed persistence ownership remain architectural decomposition debt; both should move only through behavior-preserving seams and exact-head test execution.

The pure helper implementations and tests for items 1–2 are already present; only their large-file call-site substitutions remain.

## External gates still required

- exact-head host/unit/Robolectric/lint/release/package workflow conclusion;
- exact-head connected-emulator workflow conclusion;
- real older, midrange, high-refresh, cutout/aspect, and tablet sessions;
- measured frame, memory, crash, ANR, thermal, battery, and long-session evidence;
- signed minified artifact using real release credentials;
- installation and internal-store delivery of that exact artifact;
- package/version/certificate/artifact receipt confirmation;
- final artwork, animation, screenshots, metadata, audio, haptic, reduced-motion, touch-latency, and accessibility approval;
- privacy, data-safety, content-rating, target-audience, and current store-policy review.

## Validation truth

The connected environment could not resolve `github.com` for a read-only local checkout, and no combined status checks were attached to the observed exact head during this audit. Therefore:

- source and tests were committed directly to `main`;
- source contracts and adversarial tests are present;
- exact-head green is **not** claimed;
- physical/store acceptance is **not** claimed;
- Forest Run remains a feature-rich alpha, not an upload-ready release candidate.
