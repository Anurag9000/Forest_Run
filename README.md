# Forest Run

Native Android endless runner in Kotlin using a custom `SurfaceView` game loop. Forest Run is designed as a handcrafted cottagecore journey rather than a minimal score chaser: the player begins beneath a willow, crosses five changing biomes, collects Seeds, enters Bloom, practices mercy toward forest creatures, rests after failure, and returns to a persistent Garden whose state remembers earlier runs.

**Core loop:** willow ritual → five-biome run → soft failure/Rest → changed Garden → remembered next run

## Current status

**Source-ready feature-rich alpha.** The previously identified source-architecture remediation queue is implemented on `main`: collision-result dispatch, shared live collision effects, typed run-session transitions, application persistence facade adoption, a real virtual-node accessibility provider with coalesced announcements, canonical encounter-family catalogue coverage, and an ordinary-player fail-closed recovery UI are all live rather than merely test seams or future plans.

The exact source-bearing checkpoint `414bf30b36ce051f0d5ef75f6143ed6bf8fa5884` passed Android validation run `31297723150`: the full host/release/lint/package/R8/source-immutability job and API-35 connected-behavior/source-immutability job both succeeded. The current exhaustive closure and remaining-gates record is [`docs/audits/2026-08-09_source_completion_and_remaining_gates.md`](docs/audits/2026-08-09_source_completion_and_remaining_gates.md).

That does **not** make Forest Run a physically accepted release candidate or a store-ready production release. Source tooling now also captures extended physical diagnostics and compiles fail-closed human-acceptance plus release-governance evidence, but representative-device performance/fairness/accessibility runs, real signing and signed-install verification, internal-store delivery, final asset/audio/haptic review, privacy/store-policy decisions, dependency/licence/security review, and accountable final approvals remain external gates.

## Implemented product surface

- responsive tap/hold jumping and swipe-down duck arbitration with cancellation-safe input ownership;
- player run, jump, apex/fall, duck, stumble, Rest, restart, and Bloom presentation;
- five runtime biomes: Meadow, Orchard, Ancient Grove, Dusk Canyon, and Night Forest;
- exactly 19 encounter types across flora, trees, birds, and animals;
- deterministic collision severity/order independence and exactly one selected outcome per collision query;
- Seeds, Seed Orbs, Bloom, score, distance, mercy hearts, kindness chains, pacifist tiers, authored run summaries, and Rest quotes;
- outcome-earned relationship progression, persistent encounter/pass/hit/spare memory, forest mood, return moments, and story fragments;
- nine-plant persistent Garden with canonical economy and atomic purchases;
- persistent wardrobe/costume unlock and equip state;
- deterministic debug/fairness/readability scenarios for the full encounter roster;
- ghost capture/replay with recoverable best-run promotion, receipts/manifests, namespace isolation, monotonic distance, and strong content identity;
- particles, camera feedback, authored dialogue/flavour text, leitmotif/audio, haptics, reduced-motion/audio/haptic settings, and safe-content transforms;
- privacy-safe user recovery prompts plus separate debuggable maintenance tooling;
- performance telemetry, physical evidence collection, release/source integrity tooling, screenshot/metadata/graphics verification, SBOM/dependency evidence, and candidate-bound acceptance aggregation.

## Runtime ownership now implemented

### Collision outcomes

`EntityManager` performs pure collision arbitration. `CollisionOutcomeDispatcher` is the single result dispatcher for `HIT`, `STUMBLE`, `MERCY_MISS`, and `NONE` and delegates to:

- `TerminalHitImpactCoordinator` for immediate terminal impact ordering;
- `TerminalHitOutcomeCoordinator` for relationship feedback, summary/quote resolution, and terminal persistence completion;
- `NonTerminalCollisionOutcomeCoordinator` for STUMBLE and MERCY_MISS;
- one shared `LiveCollisionEffects` adapter for Player, ghost, flash, camera, audio, haptic, and particle effects.

`GameView` supplies live inputs once, receives the typed dispatch result, and does not own a private collision-result `when` or private terminal/nonterminal effect adapters.

### Run-session transitions

`RunSessionTransitionPlanner` is the pure transition table for Menu, Garden, Playing, DYING, GAME_OVER, and RESTARTING boundaries. `RunSessionTransitionCoordinator` executes ordered effects through `LiveRunSessionEffects`. `GameView` adopts the after-state only when the transition is valid and all required effects complete successfully.

Terminal collision completion therefore hands off through `RunSessionEvent.TERMINAL_COLLISION_COMPLETED`; the session layer owns death triggering and the transition to `DYING`. Debug scenario/autostart publication also routes through the same owner via `DEBUG_PLAYING_STATE_REQUESTED`, which is explicitly accepted when idempotent without turning invalid ordinary events into successful no-ops.

### Persistence boundary

`ApplicationPersistenceFacade` is the shared live application mutation boundary for:

- exactly-once terminal run-outcome persistence;
- resolved encounter/pass/hit memory and collision relationship writes;
- Garden purchases;
- wardrobe/costume writes;
- feedback settings;
- recovery inspection/retry/discard operations.

Low-level durability remains intentionally separated among SharedPreferences, run-outcome recovery state, and AtomicFile ghost protocols. The facade does **not** claim a fake global ACID transaction across those independent recovery domains.

### Accessibility

The custom Canvas UI exposes a real Android virtual-node hierarchy:

- `GameAccessibilitySemantics` builds stable-ID semantic trees for Menu, accessibility Settings, Playing, Garden, and Rest;
- `GameAccessibilityActionRouter` rejects stale, disabled, unsupported, or malformed actions;
- `LiveGameAccessibilityActions` routes virtual actions to real session/input/persistence owners;
- `GameAccessibilityGeometry` binds settings/Garden nodes to the same layout planners used by touch UI and supplies bounded semantic regions for run/Rest controls;
- `GameAccessibilityNodeProvider` exposes virtual descendants, focus, state, bounds, clicks, and checkable settings without synthesizing fixed-coordinate touch events;
- framework accessibility events are emitted only while Android accessibility is enabled, while semantic queries/actions remain safe if it is off;
- all nine Garden plants and all wardrobe styles have truthful virtual state;
- Rest continuation is disabled until `GAME_OVER`;
- `AccessibilityAnnouncementPolicy` is live, sampled only under accessibility + touch exploration, and coalesces routine Playing distance to 100 m buckets with a 10 s minimum interval while prioritizing important surface/Bloom/Garden/settings changes.

Source integration is complete; representative physical TalkBack acceptance is still release-blocking.

### Encounter catalogue

`EncounterFamilyCatalogue` is the single canonical structural/derived inventory for all 19 encounter types. It owns only structural implementation identity and derives ordinary biome reachability, deterministic scenario/fairness coverage, authored variants, and relationship capability from their existing authorities. Tests require every type to remain biome-reachable, factory/asset-wired, deterministically covered, and backed by at least one focused single-type fairness/readability scenario.

### Recovery experience

`RecoveryEvidencePresentation`, `RecoveryEvidenceUserController`, and `RecoveryEvidenceDialogCoordinator` provide the ordinary-player recovery path. It exposes privacy-safe domain/status copy, performs safe retry without deletion, revalidates every action, and requires a second explicit confirmation before destructive discard. Raw recovery payloads, file paths, hashes, ghost frames, and exception details are not surfaced.

Debug/ADB recovery maintenance remains a separate acceptance/support surface using the same fail-closed domain rules.

## Development workflow

`main` is the only active development branch and the sole source of repository truth.

- Routine implementation is committed directly to `main`; do not create development PRs/branches for this project workflow.
- Preserve published history; do not force-push or rewrite it.
- Keep code, tests, configuration, specifications, and documentation coherent in the same implementation sequence.
- Read the exact current blob and use optimistic-lock SHAs for writes.
- Add focused regression/source contracts for corrected invariants.
- Permanent validation workflows are read-only and must never mutate or push repository source.
- Temporary exact migrations, when needed for a large file, must be narrow, self-verifying, and delete their own migration script/workflow in the descendant commit.

## Automated evidence

Permanent Android validation checks out the exact event SHA and runs:

- immutable-source/governance contracts;
- all Python release, provenance, screenshot, metadata, graphics, privacy, dependency, performance, connected-runner, accessibility, persistence, recovery, catalogue, and physical-acceptance tests under `scripts/test_*.py`;
- debug/release/unit/instrumentation Kotlin compilation;
- the complete JVM/Robolectric suite;
- debug and release lint;
- debug application + instrumentation APK packaging;
- minified/resource-shrunk release AAB construction;
- resolved dependency/SBOM evidence construction;
- 16 KB page-size/native-code package inspection;
- effective R8 application-class renaming verification;
- post-build source immutability;
- API 35 connected behavior and connected source immutability.

These automated checks prove source/build/emulator properties for their exact SHA. They do not substitute for physical-device, human accessibility/fairness, signing, or store-delivery acceptance.

## Remaining release blockers

The remaining work is now predominantly **candidate-bound and external**, not the old architecture queue:

- freeze an exact candidate and run deterministic scenarios plus ordinary play across a representative physical matrix: older phone, midrange phone, high-refresh phone, cutout/unusual-aspect phone, and tablet;
- capture and review p95/p99 frame time, slow frames, allocation/GC, PSS, I/O, audio-thread behavior, crashes/ANRs, thermal behavior, battery impact, long-session behavior, dense Bloom, and all-entity stress evidence;
- conduct human gameplay acceptance for touch latency, jump/hold/swipe feel, encounter fairness/telegraphs, safe areas, text/contrast, reduced motion, audio, haptics, lifecycle/process-death recovery, Garden/wardrobe continuity, and ghost readability;
- conduct current TalkBack testing on the exact signed candidate for focus order, labels/state, action reliability, announcement cadence, Garden/wardrobe/recovery flows, large font/display scale, lifecycle, cutout/aspect variants, and audio coexistence;
- provide real signing credentials, produce a signed minified build, verify certificate identity, install/update it, and smoke-test it on hardware;
- deliver through an internal Play track and verify receipt/update/store path on the exact candidate;
- visually approve final artwork/animation—including Wolf—plus screenshots, store graphics, procedural scenic layers, and the fixed-landscape product decision;
- approve final audio, haptic, reduced-motion, and accessibility presentation on hardware;
- publish the privacy policy at a stable HTTPS URL and complete final Data Safety, content rating, target audience, and current Play-policy review;
- finalize resolved dependency vulnerability review, dependency verification, licence attribution, source-code licence, asset licences/provenance, and signed-artifact provenance;
- enable/verify the intended private vulnerability-reporting path before describing it as available;
- freeze candidate-specific release notes/changelog only after the accepted candidate exists;
- compile and independently review the final physical/device/store acceptance manifest, aggregate, release-evidence index, evidence hashes, reviewer approvals, and release decision.

## Intentional limitations that remain

These are not hidden source TODOs and should not be misreported as solved:

- ghost/distance mismatches from before persistent manifests cannot be reconstructed retroactively;
- healthy legacy sidecars may remain legacy until strong validation/upgrade is needed;
- SHA-256 binds content/distance identity but is not authentication of a trusted writer;
- ghost recovery and non-ghost outcome recovery remain independent durability domains;
- automated emulator tests do not prove real-device timing, thermal, battery, screen-reader usability, or subjective fairness;
- further `GameView` decomposition should be driven by measured maintainability/device findings rather than broad behavior-risking rewrites.

## Documentation

| File | Purpose |
|---|---|
| [`docs/GAME_DESIGN.md`](docs/GAME_DESIGN.md) | Product vision, entity mechanics, session lifecycle, and design rules |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Current runtime ownership, persistence, recovery, CI, and remaining evidence debt |
| [`docs/ACCESSIBILITY.md`](docs/ACCESSIBILITY.md) | Live virtual-node/announcement architecture and physical TalkBack acceptance |
| [`docs/ENCOUNTER_CATALOGUE.md`](docs/ENCOUNTER_CATALOGUE.md) | Canonical 19-type structural and derived content catalogue |
| [`docs/RECOVERY_USER_EXPERIENCE.md`](docs/RECOVERY_USER_EXPERIENCE.md) | Ordinary-player recovery state/action/confirmation model |
| [`docs/RECOVERY_EVIDENCE_MAINTENANCE.md`](docs/RECOVERY_EVIDENCE_MAINTENANCE.md) | Low-level fail-closed recovery evidence and debug/support maintenance |
| [`docs/PERFORMANCE.md`](docs/PERFORMANCE.md) | Physical profiling protocol and threshold/evidence procedure |
| [`docs/DEVICE_ACCEPTANCE.md`](docs/DEVICE_ACCEPTANCE.md) | Candidate identity, device/scenario evidence, thresholds, approvals, and release decision |
| [`docs/HUMAN_ACCEPTANCE.md`](docs/HUMAN_ACCEPTANCE.md) | Candidate-bound gameplay, TalkBack/accessibility, and presentation review matrix |
| [`docs/INSTALLED_CANDIDATE_IDENTITY.md`](docs/INSTALLED_CANDIDATE_IDENTITY.md) | Measured Play-delivered package/split/signing identity and five-device installed matrix |
| [`docs/RELEASE_GOVERNANCE_EVIDENCE.md`](docs/RELEASE_GOVERNANCE_EVIDENCE.md) | Security, licensing, privacy, store, provenance, release-note, and final decision evidence |
| [`docs/RELEASE_READINESS.md`](docs/RELEASE_READINESS.md) | Final cross-layer physical/install/Play/human/governance/index readiness gate |
| [`docs/RELEASE.md`](docs/RELEASE.md) | Correctness, validation, packaging, hardware, signing, and store checklist |
| [`docs/RELEASE_EVIDENCE_INDEX.md`](docs/RELEASE_EVIDENCE_INDEX.md) | Final evidence-set construction and independent verification |
| [`docs/SUPPLY_CHAIN_AND_SBOM.md`](docs/SUPPLY_CHAIN_AND_SBOM.md) | Dependency/SBOM/provenance boundaries |
| [`docs/SECURITY_AND_LICENSING_GOVERNANCE.md`](docs/SECURITY_AND_LICENSING_GOVERNANCE.md) | Security reporting and licensing release gates |
| [`PRIVACY.md`](PRIVACY.md) | Source-backed offline privacy/data behavior |
| [`docs/AUDIT_LEDGER.md`](docs/AUDIT_LEDGER.md) | Chronological remediation history with a current reconciliation preface |
| [`docs/audits/2026-08-09_source_completion_and_remaining_gates.md`](docs/audits/2026-08-09_source_completion_and_remaining_gates.md) | Current exhaustive source-closure checkpoint and remaining candidate/external gates |
| [`docs/audits/2026-08-06_documentation_reconciliation_audit.md`](docs/audits/2026-08-06_documentation_reconciliation_audit.md) | Prior full documentation reconciliation and reconstructed mission |

Dated audit documents are provenance records. Later source/tests/current canonical docs supersede historical “remaining” statements without rewriting history.

## Build and test

CI uses Java 21 while Android source targets Java 17 bytecode. Android API 36 must be installed for host compilation; connected validation currently exercises API 35.

```bash
python3 -m unittest discover -s scripts -p 'test_*.py'
./gradlew compileDebugKotlin compileDebugUnitTestKotlin compileReleaseKotlin compileDebugAndroidTestKotlin
./gradlew testDebugUnitTest
./gradlew lintDebug lintRelease
./gradlew assembleDebug assembleDebugAndroidTest bundleRelease
./gradlew connectedDebugAndroidTest   # requires an emulator/device
```

Physical performance evidence on an authorized device:

```bash
bash scripts/collect_performance_profiles.sh
```

Validate a completed candidate-bound physical/store evidence bundle:

```bash
python3 scripts/validate_device_acceptance.py \
  release-evidence/device-acceptance.json \
  --summary-output release-evidence/device-acceptance-summary.json
```

Canonical release preparation from the exact clean `origin/main` tip:

```bash
bash scripts/prepare_main_release.sh
```

The release wrapper rejects dirty, detached, non-`main`, stale, and unpushed worktrees, freezes the matching candidate SHA, runs the release preparer, and verifies local `main`/`origin/main` identity afterward.

Expected outputs:

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Instrumentation APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
- Release bundle: `app/build/outputs/bundle/release/app-release.aab`
- R8 mapping: `app/build/outputs/mapping/release/mapping.txt`

## Canonical runtime direction

- **Application ID:** `com.anurag9000.forestrun`
- **Canonical branch:** `main`
- **Biomes:** five runtime biomes
- **Bloom target:** eight Seeds; six-second active window
- **Input intent:** tap short jump, hold higher jump, swipe down duck
- **Encounter invariant:** exactly one selected collision outcome per query/entity interaction
- **Failure flow:** run → Rest summary → Garden → next run
- **Ghost format:** v2 stable state codes with legacy read compatibility
- **Display orientation:** fixed landscape pending final product/device acceptance
- **Release signing:** external Gradle properties/environment variables only; credentials are never committed
