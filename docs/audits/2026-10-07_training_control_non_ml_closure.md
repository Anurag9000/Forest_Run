# Training-control non-ML closure — 2026-10-07

## Scope

Forest_Run is a native Kotlin Android game, not an ML training repository.
This closure covers only the repository-specific **training-control
applicability** question. It does not certify store release, physical-device
acceptance, human creative approval, signing, Play delivery, privacy/legal
approval, or every gameplay feature.

## Why CUDA/training controls are not applicable

The live repository retains ordinary Android/Kotlin application code,
release/evidence tooling, tests, assets and Gradle dependencies, but no
retained optimizer/model-training surface. Therefore creating GPU training
jobs, datasets, optimizer checkpoints, CPU/GPU training parity matrices or
ML model registries would manufacture capabilities that do not belong to
this product.

The authority is
`training_control/forest_no_trainable_authority.py`. It scans the
live source/dependency surface for ML frameworks, optimizer/backprop,
training-loop and ML dependency markers. The authority is deliberately
fail-closed: a real future ML/training marker invalidates the non-ML
classification until a repository-specific training architecture exists.

## Empty/partial-tree false-pass repair

A scanner that sees no files can trivially find no ML. That is not sufficient
evidence that Forest_Run is a non-ML Android application. The authority now
requires the retained Android/build surface before issuing a repository-wide
certificate, including:

- `settings.gradle.kts`
- root and app Gradle builds
- `app/src/main/AndroidManifest.xml`
- `MainActivity.kt`
- `engine/GameView.kt`
- production Kotlin source and Android dependencies

Tests cover an empty repository, individually missing required files,
dependency-free fixtures, ordinary non-training application vocabulary,
injected real framework markers, and the live root command.

## Executed evidence

At current main commit `ceae979ebaead2ffc9449dc95317917b1d63d6c7`:

- Training-control applicability audit run `37602964199`: **success**.
  - compilation: success
  - absent-tree/injected-marker regression step: success
  - **44 tests passed**
  - certificate generation: success
  - live certificate classification: `no_retained_trainable_surface`
  - scanned files reported by the certificate: **649**
  - findings: **0**
- Estate local training-control certificate run `37602964291`: **success**.
- Android validation run `37602964334`: **success**.

The certificate explicitly says `ml_training_applicable=false` and
`execution_claim_emitted=false`; it is an applicability/source certificate,
not fabricated model-training evidence.

## Closure status

**Training-control applicability: CLOSED/PASS for current main.**

This means:
- do not add ML/GPU training merely to satisfy an estate-wide CUDA policy;
- keep the fail-closed scanner active so a future ML surface invalidates
  this certificate;
- keep non-ML application/release engineering under its own independent
  verification tracks.

Still open outside this closure are the external/product release gates already
documented in Forest_Run: physical-device acceptance, human/creative
acceptance, signing identities, internal-store delivery, Play declarations,
privacy/legal approvals and final accountable release authorization.
