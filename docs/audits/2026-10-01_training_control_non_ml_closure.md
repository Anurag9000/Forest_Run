# Forest_Run training-control applicability closure — 2026-10-01

## Repository-specific conclusion

Forest_Run is a native Kotlin/Android SurfaceView game, not an ML training
repository. GPU-first *training*, CuPy substitutions, optimizer state,
early stopping, model checkpoints, training datasets and CPU/GPU training
parity are therefore not applicable to the current retained software.
Creating synthetic model jobs would be a false capability.

The repository-specific authority remains fail-closed: if a retained ML
framework, optimizer/backprop loop or training marker is introduced, the
certificate stops passing until a real scientific training architecture is
declared.

## Corrected evidence boundary

The prior authority could return an empty scanner result for an empty or
partial repository. That is not enough to prove that a real Android
application contains no training surface. The authority now requires the
actual application/build boundary before issuing a repository-wide
certificate:

- settings.gradle.kts
- root build.gradle.kts
- app/build.gradle.kts
- app/src/main/AndroidManifest.xml
- MainActivity.kt
- engine/GameView.kt
- at least one scanned production Kotlin source
- at least one retained Android dependency

Missing required source, an empty Android dependency manifest, or an
injected ML/training marker fails closed.

Implementation commits:
- `66cdfa55f703c2ce183d7e559ea5366fd3ee7bba` — require retained Android
  application/build evidence before certification.
- `e86195a69278210fbb820ce2a267a0d692f92ddb` — regression coverage for
  empty, partial, dependency-free and ML-injected fixtures.
- `2963386d5f40b5c7735fdd9d1de1e25ee969575d` — execute those tests in
  the training-control applicability workflow.

## Executed evidence on current main

Current main examined for this closure:
`3e1231b2bf3d7d1295fbb7beeba625812499f2b6`.

GitHub Actions:
- Training-control applicability audit:
  `36881190063` — **success**.
- Estate local training-control certificate:
  `36881190024` — **success**.
- Android validation:
  `36881190034` — **success**.

The applicability job explicitly executed:
- source compilation — success;
- `scripts/test_training_control_not_applicable.py` — **16 tests passed**;
- live repository certificate — success;
- certificate artifact upload — success.

The emitted live certificate reported:
- classification: `no_retained_trainable_surface`;
- `ml_training_applicable=false`;
- `finding_count=0`;
- `scanned_file_count=639`;
- `execution_claim_emitted=false`;
- eight retained Android test/runtime dependencies captured from
  `app/build.gradle.kts`.

This is meaningful evidence that the current retained source has no ML
training surface. It is **not** evidence that every gameplay/release feature
is production accepted, nor that physical-device/store/human acceptance is
complete. Those remain governed by Forest_Run's separate Android release and
acceptance evidence system.

## Status

Training/CUDA applicability: **CLOSED as not applicable for current retained
software**, with a fail-closed future-change gate.

Android product/release closure: separate and not implied by this document.
