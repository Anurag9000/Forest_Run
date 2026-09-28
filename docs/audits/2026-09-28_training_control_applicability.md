# Forest Run training-control applicability audit — 2026-09-28

## Conclusion

Forest Run is a native Kotlin/Android game and has no retained machine-learning
training surface in the current `main` source. GPU-first ML training,
NumPy/CuPy substitution, optimizer/loss/checkpoint parity and scientific
training DAG controls are therefore **not applicable** to this repository.
This result is not permission to skip ordinary Android/game GPU, graphics,
performance, device, release or physical acceptance work.

## Fail-closed authority correction

The previous scanner could truthfully report no ML markers from an empty or
partial tree, but an empty tree is not evidence about the real application.
The repository-specific authority now requires the actual Android build and
runtime anchors before it can issue a no-training certificate:

- `settings.gradle.kts`
- repository and app `build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `MainActivity.kt`
- `engine/GameView.kt`
- at least one scanned production Kotlin file
- at least one retained Android application dependency

Removing any required source or all Android dependencies makes the authority
fail closed. The composable scanner remains usable by fixtures so injected
Torch/TensorFlow/JAX/sklearn/optimizer/backprop/training-loop markers can be
tested independently.

Implementation:
- authority commit `66cdfa55f703c2ce183d7e559ea5366fd3ee7bba`
- regression commit `e86195a69278210fbb820ce2a267a0d692f92ddb`
- workflow commit `2963386d5f40b5c7735fdd9d1de1e25ee969575d`

## Executed evidence on current main

Current candidate commit:
`5d0e96a31fb0c2fdb1cceae3775b5db7c15eaa95`.

GitHub Actions run `36369240169` completed successfully on an assigned
GitHub runner. The fail-closed regression suite ran **12 tests** and reported
`OK`. The subsequent repository certificate reported:

- classification: `no_retained_trainable_surface`
- ML training applicable: `false`
- scanned files: **634**
- training findings: **0**
- Android dependencies: AppCompat and AndroidX Core KTX
- execution claim emitted: `false`

The certificate artifact was successfully uploaded and is bound to the exact
candidate SHA. On the same candidate, `Estate local training-control
certificate` run `36369240203` and `Android validation` run
`36369240231` also completed successfully.

## Scope boundary

This closes the repository's **ML-training applicability classification** for
the cited source revision. It does not claim:

- Play Store delivery or production signing readiness;
- physical-device, accessibility, thermal, frame-pacing or battery acceptance;
- artwork, licensing, privacy or human review approval;
- correctness of every gameplay subsystem merely because Android CI passed;
- future no-ML status after source/dependency changes.

The fail-closed workflow runs on every `main` push, so any later retained
training marker or missing required application surface must invalidate the
certificate until reviewed.
