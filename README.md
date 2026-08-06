# Forest Run

Native Android endless runner in Kotlin using a custom `SurfaceView` game loop. Forest Run aims to be a handcrafted, personality-rich cottagecore journey rather than a minimal score chaser.

## Product Vision

The player begins beneath a willow, runs through five atmospheric biomes, collects Seeds, enters Bloom, practices mercy toward forest creatures, rests after failure, and returns to a persistent Garden. Repeated encounters change dialogue, sanctuary details, relationship history, and the emotional shape of later runs.

**Core loop:** willow ritual → run → soft failure/rest → Garden return → run again

## Current Status

**Source-ready feature-rich alpha with the primary correctness-remediation history consolidated on `main`.** The repository has a permanent application identity, current Android target, immutable exact-SHA host/emulator CI, broad invariant coverage, an obfuscated/resource-shrunk release bundle, versioned atomic ghost persistence, cutout-safe essential UI, persistent feedback controls, a physical performance-evidence harness, and a fail-closed physical-device/store acceptance validator.

The source-bearing baseline `f6d1fc1077326e160ddd829cd7279158793616eb` completed Android validation run `31080357879` successfully. The later architecture, release-integrity, build-authority, and privacy continuation head `cc24e86784bd0d6c53114c55e37ff447e1b55bc5` completed run `31105597864` successfully. The continuation passed 445 Python tests, complete Android compilation/JVM testing/lint/packaging, R8 verification, API 35 connected behavior, and post-validation source-immutability checks.

It is not yet a physically accepted or store-release-ready candidate because representative physical-device acceptance, measured performance evidence, signed-artifact installation, final visual/audio/haptic approval, internal-store delivery, complete screen-reader accessibility, and store/legal/policy work remain.

The canonical `main` branch includes:

- responsive tap/hold jumping and swipe-down arbitration;
- Bloom as an orthogonal power state, preserving airborne physics;
- one authoritative Bloom timer and exclusive conversion rewards;
- one terminal outcome per entity with deterministic collision priority;
- pure collision queries and selected-outcome side effects;
- a tested exhaustive lazy `CollisionOutcomeDispatcher` seam, pending safe live `GameView` wiring;
- allocation-free expanded mercy probes preserving custom safe-window geometry;
- all-entity clean-pass, debug-isolation, and persistence integration coverage;
- outcome-earned relationship Trust and Bond progression;
- collectible Seed Orbs staged ahead of the player;
- distance-based encounter spacing that remains stable as speed changes;
- live Eagle targeting and aligned flora/tree collision geometry;
- bounded gameplay input routing and interruption-safe render-thread shutdown;
- direct atomic Garden purchases with finite screen timing and fail-closed touch admission;
- independently composed and bounded sanctuary atmosphere modifiers;
- bounded/wrapped presentation queues and cached game-over composition;
- finite, capped Menu, HUD, Garden, and Rest presentation clocks;
- strict runtime asset checks and hardened audio/music lifecycle handling;
- 30 Hz, twenty-minute ghost capture with atomic off-thread persistence;
- ghost binary format v2 with magic/version headers, stable state codes, and legacy-file reads;
- recoverable ghost promotion with receipts, persistent manifests, monotonic best distance, and distance-bound SHA-256 identity;
- immutable persistence namespaces, same-namespace serial scheduling, and bounded cross-namespace concurrency;
- one aspect-preserving safe-content transform for menu, Garden, HUD, debug, and rest UI;
- persistent reduced-motion, audio, and haptic settings enforced at manager boundaries;
- versioned SharedPreferences repair with future-schema compatibility storage;
- coherent frame, workload, heap, and ghost-I/O profiling snapshots;
- root- and inode-safe final release-evidence indexing with an independent verifier;
- maintained GitHub Actions runtimes and one pinned direct-dependency authority;
- a source-backed offline privacy policy and automated permission/SDK contract;
- final application ID `com.anurag9000.forestrun`;
- API 36 host/release validation and API 35 connected validation on exact candidate SHAs;
- candidate-, artifact-, certificate-, internal-store-, device-, scenario-, threshold-, evidence-hash-, and reviewer-bound physical acceptance validation.

## Development Workflow

`main` is the only active branch and the sole source of repository truth.

- Routine work is committed directly to `main`; no development branches or pull requests are created.
- Each commit must be coherent and include the relevant code, tests, configuration, specifications, and documentation.
- Existing history is preserved. Do not force-push, rewrite, squash away, or otherwise replace published history.
- Read the exact current blob before replacing a file and use optimistic-lock SHAs for repository writes.
- Add focused regression coverage for each corrected invariant.
- Permanent validation workflows are read-only and must never modify or push source.
- Historical closed or merged pull-request pages may remain in GitHub, but they are not active development surfaces and have no surviving source branches.

## Implemented System Surface

- custom `SurfaceView` render loop and frame-time-based simulation;
- player run, jump, duck, fall, land, stumble, rest, and Bloom presentation;
- 19 entity classes across flora, trees, birds, and animals;
- five-biome cycle: Meadow, Orchard, Ancient Grove, Dusk Canyon, Night Forest;
- score, distance, Seeds, Bloom, mercy, pacifist route, and run-summary systems;
- persistent Garden, plants, wardrobe, relationships, forest mood, return moments, and story fragments;
- ghost replay, particles, camera feedback, dialogue, flavour text, audio, and haptics;
- deterministic encounter scenarios, store-support scripts, physical profiling tools, and candidate-bound acceptance evidence.

## Automated Evidence

Permanent read-only CI checks out and records the exact event SHA, then runs:

- all Python release, provenance, screenshot, metadata, graphics, privacy, dependency, performance, connected-runner, and physical-acceptance tests under `scripts/test_*.py`;
- debug, release, unit-test, and instrumentation Kotlin compilation;
- the complete JVM/Robolectric invariant suite;
- debug and release lint;
- debug application and instrumentation APK assembly;
- minified/resource-shrunk unsigned AAB construction;
- effective R8 application-class renaming verification;
- API 35 connected behavioural tests.

A successful host job proves compilation, JVM/Robolectric correctness, lint, packaging, R8, and source immutability for that exact SHA. Connected-emulator evidence is tracked separately because runner boot or ADB failures can occur before application tests start. Neither automated result substitutes for representative physical-device acceptance.

The large hardware-capture and performance-profile tests are compiled but intentionally excluded from ordinary emulator CI. They must be run on representative physical devices.

## Remaining Release Blockers

- run deterministic scenarios and ordinary play on representative physical devices;
- capture and review frame-time, allocation/GC, memory, I/O, audio-thread, thermal, battery, and long-run evidence;
- establish evidence-based performance thresholds and repair any material hotspots found;
- validate touch latency, transformed safe-content readability, feedback settings, audio, haptics, lifecycle recovery, and density behavior on phones/tablets/cutouts/unusual aspects;
- implement and test TalkBack/Canvas semantic navigation for essential menu, settings, Garden, Rest, and run state;
- provide real signing credentials and smoke-test the signed, minified artifact;
- install through an internal store track and verify the store delivery path;
- capture, curate, and manually approve final store screenshots and metadata;
- visually verify artwork and animation frame counts, including the Wolf sheet;
- publish the privacy policy at a stable HTTPS URL and align final Data Safety answers;
- revalidate current store policy, content rating, target audience, and submission requirements;
- generate trusted Gradle dependency-verification metadata, a resolved SBOM, licence attribution, vulnerability review, and signed-artifact provenance;
- enable private vulnerability reporting before publishing a formal security-reporting policy;
- select explicit source-code and asset licensing;
- write candidate-specific release notes and a changelog entry;
- decide whether the remaining procedural scenic layers and fixed-landscape policy are final art/product choices;
- produce and independently verify the final candidate-bound acceptance manifest, aggregate, release-evidence index, and approvals.

Bounded source architecture work remains desirable but is not presently evidenced as a release-blocking behavior defect:

- wire the tested `CollisionOutcomeDispatcher` into `GameView` through a safe exact patch-capable checkout;
- move live terminal/nonterminal effect adapters behind a smaller runtime boundary;
- extract run-session state transition and subsystem sequencing incrementally;
- add an application-level persistence facade without misrepresenting separate stores as one global transaction;
- complete descriptor-bound evidence snapshots and evidence-parent symlink rejection as defense in depth;
- consider a local data-oriented encounter catalogue only after physical acceptance stabilizes behavior.

See [`docs/RELEASE.md`](docs/RELEASE.md) for the evidence-backed exit checklist, [`docs/DEVICE_ACCEPTANCE.md`](docs/DEVICE_ACCEPTANCE.md) for the physical/store evidence contract, and [`docs/audits/2026-08-06_architecture_release_integrity_privacy_audit.md`](docs/audits/2026-08-06_architecture_release_integrity_privacy_audit.md) for the latest implementation and remaining-work reconciliation.

## Documentation

| File | Contents |
|---|---|
| [`docs/GAME_DESIGN.md`](docs/GAME_DESIGN.md) | Product vision, runtime-honest entity mechanics, session lifecycle, and design rules |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Current runtime structure, ownership, persistence, CI, and architectural debt |
| [`docs/PERFORMANCE.md`](docs/PERFORMANCE.md) | Physical-device profiling protocol, report collection, and threshold procedure |
| [`docs/DEVICE_ACCEPTANCE.md`](docs/DEVICE_ACCEPTANCE.md) | Candidate identity, device/scenario coverage, evidence hashes, thresholds, approvals, and release decision |
| [`docs/RELEASE.md`](docs/RELEASE.md) | Correctness, validation, packaging, hardware, signing, and store checklist |
| [`docs/RELEASE_EVIDENCE_INDEX.md`](docs/RELEASE_EVIDENCE_INDEX.md) | Final evidence-set construction, independent verification, alias protection, and review procedure |
| [`PRIVACY.md`](PRIVACY.md) | Source-backed offline data, permissions, local retention, deletion, and future-change policy |
| [`docs/AUDIT_LEDGER.md`](docs/AUDIT_LEDGER.md) | Detailed remediation history and invariant ledger; dated evidence statements remain historical |
| [`docs/audits/2026-08-06_architecture_release_integrity_privacy_audit.md`](docs/audits/2026-08-06_architecture_release_integrity_privacy_audit.md) | Collision seam, evidence verification, CI/dependency authority, privacy, exact validation, and expanded remaining queue |
| [`docs/audits/2026-08-06_documentation_reconciliation_audit.md`](docs/audits/2026-08-06_documentation_reconciliation_audit.md) | Complete documentation inventory, reconstructed mission, current done/partial/external matrix, contradictions, and next order |
| [`docs/audits/2026-08-06_exact_head_audit.md`](docs/audits/2026-08-06_exact_head_audit.md) | Exact-head implementation audit immediately preceding the final green validation sequence |
| [`docs/audits/2026-08-01_post_merge_audit.md`](docs/audits/2026-08-01_post_merge_audit.md) | Reconstructed original mission, merged-PR audit, remaining work, and assessed additions |

## Build and Test

CI runs on Java 21 while Android source remains compiled to Java 17 bytecode. Android API 36 must be installed.

```bash
python3 -m unittest discover -s scripts -p 'test_*.py'
bash gradlew compileDebugKotlin compileReleaseKotlin compileDebugAndroidTestKotlin
bash gradlew testDebugUnitTest
bash gradlew lintDebug lintRelease
bash gradlew assembleDebug assembleDebugAndroidTest
bash gradlew bundleRelease
bash gradlew connectedDebugAndroidTest   # requires an emulator/device
```

Physical performance evidence on one authorized device:

```bash
bash scripts/collect_performance_profiles.sh
```

Validate a completed, candidate-bound physical-device/store evidence bundle:

```bash
python3 scripts/validate_device_acceptance.py \
  release-evidence/device-acceptance.json \
  --summary-output release-evidence/device-acceptance-summary.json
```

Canonical release preparation from the exact clean canonical `origin/main` tip:

```bash
bash scripts/prepare_main_release.sh
```

The wrapper rejects dirty, detached, non-`main`, stale, and unpushed worktrees. It freshly fetches `origin/main`, freezes the full matching candidate SHA, runs the existing Play release preparer, and verifies that both local `main` and `origin/main` still equal that SHA afterward. Dry-run options accepted by `prepare_play_release.py`, such as `--skip-build` or `--allow-unsigned`, may be passed through the wrapper.

Expected build outputs:

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Instrumentation APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
- Release bundle: `app/build/outputs/bundle/release/app-release.aab`
- R8 mapping: `app/build/outputs/mapping/release/mapping.txt`

## Canonical Runtime Direction

- **Application ID:** `com.anurag9000.forestrun`
- **Canonical branch:** `main`
- **Biomes:** five runtime biomes
- **Bloom target:** eight Seeds and a six-second active window
- **Input intent:** tap for a short jump, hold for a higher jump, swipe down to duck
- **Encounter invariant:** exactly one terminal outcome per entity
- **Failure flow:** run → rest summary → fade → Garden → next run
- **Ghost format:** v2 stable state codes, with read compatibility for legacy ordinal files
- **Manifest orientation:** fixed landscape pending final device/product acceptance
- **Release signing:** external Gradle properties or environment variables; credentials are never committed
