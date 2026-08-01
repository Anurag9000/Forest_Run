# Forest Run — Remediation Continuation (2026-08-01)

This dated continuation record supplements `docs/AUDIT_LEDGER.md`. It records the additional direct-to-`main` implementation completed during the August 1 exhaustive audit sweep and keeps unresolved work explicit rather than implying release readiness.

## Implemented in this continuation

### Strict JSON evidence boundary

- Added `scripts/strict_json.py` for bounded, ambiguity-free evidence parsing.
- Duplicate object keys are rejected instead of silently taking the final value.
- Literal `NaN`, `Infinity`, and `-Infinity` are rejected.
- Finite-looking JSON numbers that overflow Python floating point, such as `1e400`, are rejected after conversion.
- UTF-8 BOMs, invalid UTF-8, empty or oversized inputs, non-object roots where an object is required, and excessive nesting are rejected.
- A string-aware nesting pre-scan rejects deeply nested input before Python's parser can hit recursion limits.
- Files are rejected if size, modification time, or inode changes while they are read.
- Added `scripts/verify_strict_json_evidence.py` to recursively preflight selected evidence files/directories with deduplication and a bounded file count.
- Canonical release preparation now strictly parses graphics, metadata, screenshot, capture-session, final-sidecar, and new machine-summary JSON before semantic verification.
- Added adversarial parser, directory-expansion, numeric-overflow, duplicate-key, BOM, depth, and release-wrapper ordering tests.

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
  4. independently revalidate the published manifest.
- Added `docs/DEVICE_ACCEPTANCE_COMPILATION.md` describing the canonical workflow, minimum matrix, identity preservation, bounds, publication semantics, and limits of automated validation.

### Candidate-bound store and release evidence

- Checked-in release assets now receive structural validation rather than header-only checks:
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

### Persistence, Garden, pacing, and catalogue contracts

- Persistent-memory selectors fail closed when caller minima are zero or negative.
- Repeat-killer severity uses `Long` arithmetic and deterministic tie-breaking.
- Known persistent string sets are bounded, sorted deterministically, and sanitized while unknown future preference keys remain preserved.
- `CostumeStyle.NONE` cannot persist as an unlocked reward or blank featured card.
- Relationship milestone repair accepts only the six tracked relationship entities.
- Garden displayed prices, purchase charging, startup repair bounds, and plant count are regression-locked to `GardenEconomy`.
- Opening spawn guidance replaces zero, negative, NaN, or infinite intervals with a conservative positive cadence.
- Biome and deterministic-scenario catalogues have executable ordering, uniqueness, coverage, and variant invariants.
- Player jump-force interpolation is finite, bounded, monotonic, and fail-closed for malformed holds.

### Narrative catalogue integrity

- Added `StoryFragmentCatalogueInvariantTest` using public StoryFragment APIs only.
- Every emitted contextual page and history mark must have:
  - a safe lowercase identifier;
  - identifier length within the persisted 128-character boundary;
  - nonblank trimmed title/body or title/line;
  - no duplicate identifier within one visible catalogue.
- The suite exercises optional weathering pages, rich warm relationship history, active run-history pages/marks, repeat-friend and biome-friendship pages, empty history, and strained-bond history.
- Added `RestQuoteCatalogueInvariantTest`, enumerating every last-killer value, forest mood, pacifist route tier, and low/high run profile.
- Rest quotes must be deterministic, nonblank, trimmed, bounded, and free of unsafe control characters.

## Validation truth

The implementation above is committed directly to `main`, but this environment still cannot provide a local exact checkout or observe push-triggered GitHub Actions conclusions. Consequently:

- the latest tree is **not** described as exact-head green;
- newly added Python, JVM, Robolectric, Android, shell, and source-contract tests still require execution on one frozen head;
- structural validators prove internal consistency only when run against the real files;
- physical acceptance tooling does not manufacture device measurements, internal-track delivery, reviewer actions, or store approval.

## Remaining implementation debt

### Precisely isolated monolithic defects

1. `RelationshipArcSystem.familiarityWarmth()` still contains Kotlin conditional-arithmetic precedence that prevents the intended accumulated warmth score and leaves deeper `PERSONAL`/`BONDED` dialogue branches unreachable. The function is isolated, but the surrounding roughly 1,400-line authored dialogue catalogue still needs a safe patch-capable checkout or prior decomposition.
2. `ReturnMomentsSystem.recordRunOutcome()` still uses raw `roughRunStreak + 1`, and long-absence detection still uses raw timestamp subtraction. Persistence clamps ordinary stored state, but source arithmetic should use explicit saturating/overflow-safe helpers.
3. `LeitmotifManager` retains a duplicate raw nanosecond interval check after the wrap/reset-safe `EvaluationThrottle`; this matters only under artificial monotonic-clock rollback/wrap but should be removed during audio-owner decomposition.
4. `ParallaxBackground` and `GameView.update()` still rely on the production render-thread finite-delta contract at their large public coordination boundaries.
5. `MainMenuScreen.onTap()` can advance its ritual on a synthetic non-finite direct-call coordinate after both hit regions compare false. Real Android MotionEvent coordinates are finite; the safe fix belongs in the large menu owner once line-level patching is available.
6. `GhostPlayer.update()` maps malformed deltas to zero but can still recompute visibility suppression on that call. Production deltas are bounded; a future narrow admission guard should make malformed direct calls complete no-ops.
7. `SaveManager.hasGhostRun()` checks only the AtomicFile base path and does not validate or recognize a recoverable backup, while actual runtime loading correctly validates both. The unused convenience method remains cleanup debt.

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
