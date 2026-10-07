# Forest_Run training-control applicability closure — 2026-10-07

## Classification

Forest_Run is a native Kotlin/Android game and does not retain a machine-learning
training surface. Training-specific controls such as CUDA-first model training,
optimizer/checkpoint state, ML dataset cohorts, CPU/GPU training parity and
experiment matrices are therefore **not applicable**. This classification is
repository-specific; it is not inferred from the other repositories.

## Fail-closed authority

The repository's authority is
`training_control/forest_no_trainable_authority.py`. It scans the retained
source/dependency surface for concrete ML/training markers and now also refuses
to certify an absent or structurally incomplete Android application.

A valid no-training certificate requires these retained application anchors:

- `settings.gradle.kts`
- root and app Gradle build files
- `app/src/main/AndroidManifest.xml`
- `MainActivity.kt`
- `engine/GameView.kt`
- at least one production Kotlin source
- at least one declared Android implementation/api/compileOnly dependency

An empty repository can therefore no longer produce a false "no training"
certificate merely because the scanner finds nothing.

## Executed validation on current main

Current main at the time of this closure was
`d95296bcbe446bd0be4bf06e671e9cd10c226646`.

GitHub Actions evidence:

- Training-control applicability audit run `37608174800`: **success**.
  The job compiled the local authority, executed
  `scripts/test_training_control_not_applicable.py`, including absent/partial
  source and injected-training-marker regressions, then emitted the repository
  certificate.
- Estate local training-control certificate run `37608174801`: **success**.
- Android validation run `37608174903`: **success** for both:
  - host/release/lint/package validation;
  - API 35 connected smoke and deterministic evidence.

The current authority, test and workflow source blobs were reread from main:
`3b91907b5d08896ac73fd939d3de816094a21126`,
`f1bab0558d030959e0822724de160f3569b317d0`, and
`f229c965eb660b1ab6239fdb1615b7a5965d9c1c`.

## What this closes

Training-control applicability is **PASS / not-applicable by evidence**:
the repository does not need or benefit from fabricated ML/CUDA training jobs,
and future retained ML/training markers fail the certificate until a genuine
repository-specific training architecture is added.

The current Android source also passed the repository's host validation,
packaging/lint/release-hardening job and API 35 connected validation.

## What this does not close

This certificate is not a blanket production-release approval. The repository's
own device/store evidence policy still requires candidate-bound physical-device
coverage, human/accessibility review, signing/store identity, legal/licensing
decisions, privacy/store declarations and accountable release approval where
those evidence layers are required. No ML/GPU training claim is made because
no retained ML training system exists.
