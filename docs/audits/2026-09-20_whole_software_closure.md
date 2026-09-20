# Forest Run — Whole-Software Closure Audit (2026-09-20)

## Scope and method

This audit is the September continuation of the repository-specific completion
work. It treats Forest Run as an Android game and release system, not as a generic
ML repository. The live `main` tree was re-inventoried across production code,
tests, assets, workflows, release tooling, persistence/evidence systems, and
canonical documentation before any new work was classified as necessary.

The source-bearing checkpoint validated by GitHub Actions is
`2e79500c577fb3fa86f6e99cad5a872a69dcbff2`.

## Repository inventory

The audited tree contains 806 tracked files:

| Surface | Count |
| --- | ---: |
| Production Kotlin (`app/src/main/java`) | 187 |
| JVM/Robolectric Kotlin tests (`app/src/test`) | 226 |
| Android instrumentation Kotlin tests (`app/src/androidTest`) | 8 |
| Python files below `scripts/` | 167 |
| Shell scripts below `scripts/` | 8 |
| GitHub workflows | 3 |
| Runtime PNG sprites | 29 |
| Runtime raw audio files | 15 |
| Runtime fonts | 1 |

Repository organization is also closed: `main` is the only branch and there
are no open pull requests.

## Defects found and closed in this continuation

### 1. Training-control applicability was conceptually wrong

The prior generic controller scanned normal application vocabulary such as
registries, metrics, tasks, stages, and strategies as if it proved trainable ML
surfaces. That is invalid for this repository.

The root command now delegates to the checked-in
`training_control/forest_no_trainable_authority.py` fail-closed audit. It emits
a local certificate stating that training is not applicable only while the
repository remains free of retained ML/training framework and training-entrypoint
markers. A future real ML surface therefore turns the certificate red instead of
being silently ignored.

The local dataset-cohort certificate derives from the same authority. GPU-first
training, CPU/GPU parity, optimizers, losses, checkpoints/resume, distillation,
QAT/PTQ training stages, pruning fine-tuning, model registries, experiment
matrices, training DAGs, architecture search, and model-family coverage are
explicitly N/A rather than synthetic PASS claims.

Both training workflows now execute this repository-local authority. Exact-SHA
runs `35494183607` and `35494183606` passed.

### 2. Android CI SDK bootstrap was broken

The previous SDK action path attempted to install obsolete package `tools` and
failed before the real build. The workflow now validates the Android SDK already
provided by the hosted runner and explicitly installs the platform/build-tools
and emulator packages needed by the repository.

This was validated on the exact source checkpoint by the successful Java 21,
hosted-SDK validation, API 36 toolchain installation, Gradle setup, wrapper
validation, host build and API-35 connected execution in run `35494183684`.

### 3. Render latency evidence could be false-positive

`GameThread.renderSurfaceFrame` previously swallowed exceptions from
`unlockCanvasAndPost`, after which the outer loop recorded
`InputLatencyTelemetryRegistry.recordFrameRendered(...)`. That allowed a failed
surface post to be represented as a completed posted frame.

The render helper now fails when no Canvas is obtained and lets post failures
propagate through the existing render-failure boundary. The latency completion
call therefore occurs only after a successful render function return.

### 4. Wolf sprite frame contract disagreed with the authored asset

The repository ships `sprites/animals/wolf_4frames.png`, documentation describes
the Wolf sheet as four-frame, and source-asset validation treats the filename as
a four-frame authored contract. `SpriteManager` alone was loading it as eight
frames. The runtime loader now uses four frames and a regression test prevents
the mismatch from returning.

### 5. Release documentation exposed a bypass and stale evidence

The Google Play README previously told operators to call the lower-level
`scripts/prepare_play_release.py` helper directly and linked the publishing
checklist through a workstation-specific absolute path. It also coexisted with a
checked-in generated build summary.

The README now exposes only `bash scripts/prepare_main_release.sh` as the
candidate release boundary, uses a repository-relative checklist link, and states
that summaries are generated candidate evidence. The stale tracked
`release/google-play/BUILD_SUMMARY.md` was removed. Contract tests now scan the
operator-facing release documents for direct helper command lines and workstation
absolute paths.

### 6. Player animation ownership required cross-layer reconciliation

A declaration-only orphan pass initially misclassified `playerStandUp`. The
compile gate exposed the real caller: `MainMenuScreen` consumes it during the
willow-home `STANDING_UP` ritual.

The owner was restored and explicitly documented as Menu presentation rather than
a gameplay locomotion state. The source contract now verifies both sides of the
ownership edge. This is an example of why the closure audit evaluates actual
callers and workflows rather than deleting apparently unused declarations in
isolation.

The audit deliberately does not remap unseen jump-strip frames based on filename
or unused ranges. Final visual suitability of the derived Menu rise is a creative
acceptance question.

### 7. Bird source aliases were investigated rather than rewritten

Five bird base/flying pairs are byte-identical. The existing runtime asset audit
already declares those exact pairs as known alias groups and rejects any newly
introduced unexpected duplicate group. Creative provenance documentation also
states that the allowlist is not artistic or licensing approval.

Accordingly, no speculative replacement art was synthesized. Distinct final
flight art, if desired, remains a human creative decision with provenance and
rights evidence.

## Exact-head automated evidence

GitHub Actions run `35494183684` passed both jobs on
`2e79500c577fb3fa86f6e99cad5a872a69dcbff2`.

The host job passed:

- immutable source contracts;
- the complete Python `scripts/test_*.py` suite;
- source immutability checks;
- Java 21 setup;
- hosted Android SDK validation;
- API 36/build-tools installation;
- Gradle setup and wrapper validation;
- candidate-bound dependency/SBOM evidence;
- debug/release/unit/instrumentation compilation;
- JVM/Robolectric tests;
- debug and release lint;
- debug app and instrumentation APK assembly;
- release AAB build;
- package page-size inspection;
- R8/source immutability verification;
- artifact publication.

The connected job passed:

- Java 21 and hosted Android SDK setup;
- API 35 platform/system-image/emulator installation;
- KVM permission setup;
- the repository's connected validation runner;
- deterministic connected evidence collection and artifact publication.

The two training-control certificate workflows also passed on the same SHA.

## Capability closure result

The current implemented product still includes the previously closed runtime
surfaces: gesture-anywhere run input, finite player physics, five biomes,
nineteen encounter families, Bloom, one-shot collision outcomes, Seed/Seed-Orb
economy, Garden progression, wardrobe, relationship memory, return/session
memory, Forest Journal projection, ghosts, persistence/recovery, accessibility,
audio/haptics, deterministic debug/capture scenarios, performance/input-latency
telemetry, screenshot/visual evidence, supply-chain evidence, and release/store
orchestration.

The final re-audit did not substantiate another source-only correctness defect,
missing promised player feature, broken owner contract, or justified architecture
addition after the continuation fixes above.

## Intentionally unresolved external gates

Source closure must not be relabeled production release acceptance. The following
still require real external evidence or accountable decisions:

- representative physical-device acceptance, including constrained, current
  mid-range, high-refresh, cutout/unusual-aspect and tablet coverage where
  supported;
- human fairness/readability and TalkBack/accessibility acceptance;
- p95/p99 frame performance, memory, GC, audio-thread, ghost-I/O, thermal,
  battery and long-session evidence on representative hardware;
- production upload/signing credentials, signed artifact construction,
  certificate verification, direct install, Play internal-track delivery and
  store-delivered update verification;
- final sprite/animation/background/UI/icon/wordmark/screenshot/music/SFX/haptic
  creative approval;
- creator/source/licence/attribution and dependency/source-code licensing
  decisions;
- privacy-policy hosting, Play data-safety/content-rating/target-audience and
  current policy declarations;
- private vulnerability-reporting repository configuration;
- final independent evidence review, release-owner approval and production tag.

These items stay open precisely because source code cannot truthfully manufacture
their evidence.

## Closure classification

**Source implementation / repository engineering:** closed for the audited goals.

**Automated host + API-35 connected validation:** passed on the exact
source-bearing checkpoint identified above.

**Physical/human/legal/store/production acceptance:** open by design until real
candidate-bound evidence exists.
