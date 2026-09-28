# Forest Run training-control applicability audit — 2026-09-28

## Scope

Forest Run is a native Kotlin/Android SurfaceView game. Its repository-level
training-control question is whether retained ML/optimizer/training code exists,
not whether game rendering or Android device execution can use a GPU. Inventing
PyTorch/CuPy/model-training jobs for this repository would be a false capability.

## Hardened authority

The fail-closed authority in
`training_control/forest_no_trainable_authority.py` now requires a real
Android application source/build surface before it can certify that ML training
is not applicable. An empty or partial repository is no longer allowed to pass
merely because there is nothing to scan.

Required retained evidence includes:
- root and app Gradle configuration;
- AndroidManifest;
- MainActivity and GameView production sources;
- at least one production Kotlin source in the scanned application tree;
- retained Android implementation dependencies.

The scanner still fails closed when it finds framework, optimizer, backward,
gradient, training-loop or ML-dependency markers.

## Executed CI evidence

On current main commit `d461353aa0b7e4f7bf455eb001ebf8a7640fd4f6`,
GitHub Actions run `36386648800` completed successfully on an assigned
runner. Its applicability regression step ran **12 tests** and reported
`OK`. The emitted certificate reported:
- `classification=no_retained_trainable_surface`;
- `ml_training_applicable=false`;
- zero findings;
- 635 scanned source/configuration files;
- retained Android dependencies;
- no training execution claim.

The independent estate-local certificate workflow
`36386648793` also succeeded, and the repository's broader Android
validation run `36386648828` succeeded at the same commit.

## What this closes

For the current commit, model-training, optimizer, training datasets,
CUDA-first *training*, CPU/GPU training parity, ML checkpoint/resume and model
registry controls are truthfully **not applicable**. The absence conclusion is
backed by the retained source/dependency authority and an executed CI regression,
rather than by an empty-tree assumption.

## What this does not close

This is not a physical-device, rendering-GPU, gameplay, store, signing, legal,
human-acceptance or production-release certificate. Forest Run's own evidence
pipeline still treats physical device/store delivery, creative approval,
signing identity and human acceptance as external/final gates. The training
classification should not be used to imply those unrelated product gates pass.
