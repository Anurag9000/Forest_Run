# Forest Run — Whole-Software Closure Audit (2026-09-20)

## Scope and method

This is the canonical September whole-software closure audit for Forest Run. The
repository is evaluated as a native Android game plus its persistence, evidence,
build, release, and store-delivery system—not as a generic ML repository. The
audit re-inventoried first-party source, tests, assets, workflows, release tools,
runtime owners, persistence/recovery boundaries, instrumentation evidence, and
canonical documentation, and traced changed cross-layer contracts before
classifying work as closed.

The final source-bearing checkpoint validated by GitHub Actions is
`7757b40b7e87b3d33412f9a070c068e92f62ccf2`.

## Repository inventory

The audited source-bearing tree contains 813 tracked files:

| Surface | Count |
| --- | ---: |
| Production Kotlin (`app/src/main/java`) | 187 |
| JVM/Robolectric Kotlin tests (`app/src/test`) | 226 |
| Android instrumentation Kotlin tests (`app/src/androidTest`) | 8 |
| Python files below `scripts/` | 173 |
| Shell scripts below `scripts/` | 8 |
| GitHub workflows | 3 |
| Runtime PNG sprites | 29 |
| Runtime raw audio files | 15 |
| Runtime fonts | 1 |

Only `main` exists and there are no open pull requests. The continuation from
the prior documented checkpoint
`e1de33bb90a41ffd9df6fdf1e8d52d234c77726e` to the final source-bearing
checkpoint contains 45 commits touching 24 files. Those changes are concentrated
in runtime lifecycle/debug ownership, deterministic-persistence isolation,
connected evidence, and their regression contracts rather than unrelated product
expansion.

## Defects and contradictions closed

### 1. Training-control applicability

Forest Run has no retained model-training surface. The root training-control path
therefore uses the checked-in repository-specific fail-closed authority rather
than interpreting ordinary application vocabulary as ML registries or downloading
a generic scientific controller. Dataset cohorts, GPU-first training, CPU/GPU
training parity, optimizer/loss/checkpoint surfaces, distillation, QAT/PTQ
training stages, pruning fine-tuning, model registries, training DAGs,
architecture search, and model-family matrices are explicitly N/A until a real
trainable surface exists.

Both local training-control workflows execute that authority and produce
candidate-bound certificates.

### 2. Android CI bootstrap

Android validation validates the hosted SDK and installs only the explicit API
36/build-tools and API-35 emulator packages required by the repository. It no
longer relies on the failing SDK action path that requested obsolete package
`tools`.

### 3. Posted-frame evidence

`GameThread.renderSurfaceFrame` fails if no Canvas is obtained and does not
swallow `unlockCanvasAndPost` failures. App input latency records a completed
posted frame only after the render function returns successfully.

### 4. Asset/runtime agreement

The runtime Wolf loader uses four frames, matching
`sprites/animals/wolf_4frames.png` and the authored source-asset contract. Five
bird base/flying byte-identical pairs remain explicit known alias groups; that
allowlist is provenance/creative-review metadata, not artistic or licensing
approval.

### 5. Release operator boundary

The Google Play operator documentation exposes
`scripts/prepare_main_release.sh` as the supported release-preparation boundary,
uses repository-relative documentation paths, and does not retain generated
`BUILD_SUMMARY.md` output as checked-in candidate evidence.

### 6. Animation ownership

A declaration-only orphan pass initially misclassified `playerStandUp`. The
compile gate exposed its real caller in `MainMenuScreen`; it is retained as the
willow-home `STANDING_UP` presentation. Gameplay jump partitions, hit/stumble,
and death/Rest mappings remain separate. The audit does not invent frame mappings
from unused ranges merely to consume every authored frame.

### 7. Runtime thread handoff and state ownership

`GameView` serializes update, draw, touch, accessibility, debug launch, and
runtime state transitions through its runtime monitor. Lifecycle pause requests a
bounded render-thread stop and exposes failure rather than pretending quiescence.
Resume/surface recreation uses a latest-request token and never intentionally
starts a replacement while a previous producer still owns the runtime. Surface
initialization is idempotent, so thread replacement does not reconstruct live
gameplay owners.

### 8. Latest-request debug launch

Debug launch remains disabled in non-debuggable runtimes. `MainActivity` owns a
latest-request token for retry/readiness, while `GameView` keeps at most one
latest deferred intent. A newer singleTask intent cancels any older deferred
scenario before validation. READY evidence requires exact applied
scenario/run-mode/game-state agreement; it is not emitted merely because a
dispatch was attempted.

### 9. Deterministic-run persistence isolation

Debug, screenshot-capture, and performance-profile runs exercise score, Seed,
Bloom, encounters, and presentation locally without making their progression
durable. `GameStateManager` retains a persistent high-score floor earned only
while persistence is authorized, so a later ordinary reset discards debug-only
score while preserving legitimate ordinary progress. Terminal outcome
persistence is exactly-once and recovery-journaled; non-persistent terminal
outcomes consume their token without later retroactive writes.

### 10. Cross-thread instrumentation evidence

Connected tests no longer rely on incidental JVM cache visibility for facts
produced by the runtime thread. Instrumentation-facing state now has explicit
publication where needed, including Bloom invincibility, current biome, Menu
phase, ghost readiness, render-thread identity, active encounter scenario,
active-entity count, frame counter, and a dedicated GhostRecorder frame count.
The test no longer polls the live mutable ghost-frame list while the producer is
appending.

High-risk direct mutations of live `GameStateManager`/`EntityManager` owners
use one stop–mutate–resume helper that requires a successful producer stop and
always resumes through `finally`.

### 11. Connected candidate binding

`scripts/run_connected_validation.sh` now requires the requested full candidate
SHA and a clean worktree before execution, then rechecks HEAD and source
cleanliness afterward. Ordinary local invocations also verify canonical
`origin/main` before and after the connected run. GitHub Actions checks out
`${{ github.sha }}` with credentials disabled and skips only the remote-main
check appropriate to detached CI; exact local SHA and clean-tree requirements
remain active.

Expected Gradle/Android outputs are ignored by repository policy, so the final
clean-tree check detects source/evidence drift rather than ordinary build
artifacts.

### 12. Ghost test reset quiescence

`GhostPersistenceManager.clearMemoryForTests()` is synchronized, blocks new
promotion admission, and requires all namespace-scoped pending writes to quiesce
within a bounded 30-second reset window. It fails closed if they do not. Only
after successful quiescence does it clear in-memory publications, pending-write
ownership, and telemetry. `InstrumentationStateReset` then clears primary and
current compatibility preferences, recovery journals, feedback preferences, and
all `ghost_run*` artifacts.

Worker completion does not require the manager monitor, so the synchronized wait
does not deadlock the writer it is draining.

### 13. All-entity connected proof geometry

A strengthened API-35 proof first exposed a deterministic 18/19 roster. The
missing type was `Eagle`, not because production spawn/update was broken, but
because the test used an index-growing X coordinate. Eagle intentionally
deactivates when `x > screenWidth + 150`, and index 13 had been staged roughly
three thousand pixels beyond that boundary.

The final proof stages every `EntityType` at the shared valid pre-entry position
`screenWidth + 100`, performs one `EntityManager.update` while the runtime
producer is stopped, asserts the exact nineteen-type set and count, then resumes
and independently verifies live frame progression. Direct inspection of all
nineteen concrete entity update implementations confirms this common staging
position is valid for the one-update proof.

## Exact-head automated evidence

All automated claims below are tied to source-bearing checkpoint
`7757b40b7e87b3d33412f9a070c068e92f62ccf2`.

GitHub Actions Android validation run `35510934262` completed successfully.

The host/release/lint/package job passed:

- immutable source contracts and the complete Python `scripts/test_*.py` suite;
- source immutability checks;
- Java 21 and hosted Android SDK validation;
- API 36/build-tools installation;
- Gradle setup and wrapper validation;
- candidate-bound declared dependency and resolved SBOM evidence;
- debug, release, unit-test, and instrumentation compilation;
- JVM/Robolectric tests;
- debug/release lint;
- debug app and instrumentation APK assembly;
- release AAB build;
- packaged native page-size inspection;
- R8/source immutability verification;
- validation artifact publication.

The API-35 connected job passed:

- exact candidate checkout with credentials disabled;
- Java 21 and hosted SDK validation;
- API-35 platform/system-image/emulator installation;
- KVM permission setup;
- the repository's clean-tree/exact-SHA connected-validation runner;
- connected smoke and deterministic evidence tests, including the corrected
  nineteen-entity one-update proof;
- connected artifact publication.

The training-control applicability audit `35510934112` and estate-local
training-control certificate `35510934149` also passed on the same SHA.

## Re-audit closure result

The implemented product retains the previously audited runtime surfaces:
gesture-anywhere run input, finite player physics, five biomes, nineteen encounter
families, Bloom, one-shot collision outcomes, Seed/Seed-Orb economy, Garden
progression, wardrobe, relationship memory, return/session memory, Forest Journal
projection, ghosts, persistence/recovery, accessibility, audio/haptics,
deterministic debug/capture scenarios, performance/input-latency telemetry,
screenshot/visual evidence, supply-chain evidence, and release/store
orchestration.

The final tree search returned no live TODO/FIXME markers, workstation `/home/`
or `/Users/` paths, direct `prepare_play_release.py` operator reference, or
the explicit credential-marker strings used by the closure scan. GitHub code
search was not used to prove positive orphanhood because its index returned zero
for some identifiers known to exist; cross-layer owner traversal remained the
required basis for deletion decisions.

After the final source-bearing re-audit and exact-head execution, no additional
source-addressable correctness defect, missing promised player feature, broken
owner contract, or justified architecture addition was substantiated. This does
not authorize speculative feature growth or declaration-only orphan deletion.

## Intentionally unresolved external gates

Source/repository closure is not production acceptance. These still require real
candidate-bound evidence or accountable human decisions:

- representative physical-device coverage, including constrained, current
  mid-range, high-refresh, cutout/unusual-aspect and tablet coverage where
  supported;
- human fairness/readability and TalkBack/accessibility acceptance;
- p95/p99 frame performance, memory/GC/audio-thread/ghost-I/O, thermal, battery
  and long-session evidence on representative hardware;
- production upload/signing credentials, a signed artifact, certificate
  verification, direct installation, Play internal-track delivery and
  store-delivered update verification;
- final sprite/animation/background/UI/icon/wordmark/screenshot/music/SFX/haptic
  creative approval;
- creator/source/licence/attribution and dependency/source-code licensing
  decisions;
- privacy-policy hosting, Play data-safety/content-rating/target-audience and
  current policy declarations;
- private vulnerability-reporting repository configuration;
- final independent evidence review, accountable release-owner approval and a
  production tag.

These remain open because source code and emulator CI cannot truthfully manufacture
their evidence.

## Closure classification

**Source implementation / repository engineering:** closed for the audited goals.

**Automated host + API-35 connected validation:** passed on exact source-bearing
checkpoint `7757b40b7e87b3d33412f9a070c068e92f62ccf2`.

**Physical/human/legal/store/production acceptance:** open by design until the
required real candidate-bound evidence exists.
