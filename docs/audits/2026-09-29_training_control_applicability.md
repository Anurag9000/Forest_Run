# Forest Run training-control applicability closure — 2026-09-29

## Repository purpose

Forest Run is a native Kotlin/Android SurfaceView game. Its production runtime
is application/game code; the retained repository contains no model-training
pipeline, optimizer, ML dataset, trainable model registry, CUDA training job,
or scientific experiment DAG. CUDA-first training requirements are therefore
**not applicable** to this repository unless a future change introduces a real
ML surface.

This classification is repository-specific. It does not borrow a PASS from a
different repository and it does not manufacture synthetic trainers merely to
satisfy an account-wide template.

## Fail-closed authority

The canonical local authority is
`training_control/forest_no_trainable_authority.py`, invoked through
`run_all_training.py --training-control-audit`.

The authority now requires all of the following before issuing
`no_retained_trainable_surface`:

- the real Gradle project and app build files;
- the production Android manifest;
- the real `MainActivity.kt` and `GameView.kt` entry/runtime files;
- at least one scanned production Kotlin source;
- at least one declared Android dependency;
- zero retained source markers for PyTorch/TensorFlow/JAX/sklearn,
  optimizers/backprop/training loops or known ML dependency coordinates;
- zero retained model artifacts with extensions such as `.tflite`,
  `.onnx`, `.pt`, `.pth`, `.ckpt`, `.safetensors`, `.keras`,
  `.mlmodel` or `.mlpackage`.

The scan includes Kotlin/Java, Gradle, Python, JS/TS, XML, JSON/YAML/TOML,
shell and Windows script sources. Generated evidence, virtual environments,
build output, documentation and historical final-art source are excluded from
the live runtime/source classification so their text cannot self-poison the
certificate.

Dependency evidence is no longer limited to `implementation(...)`; the
inventory recognizes standard and flavor/build-type Gradle dependency
configurations including implementation/API/compile-only/runtime-only,
annotation processors, KAPT/KSP and `*Implementation`/`*Api` variants.
Unrelated Gradle function calls are not misreported as dependencies.

## Regression behavior

`scripts/test_training_control_not_applicable.py` verifies, among other
cases:

- an empty repository cannot receive a no-training certificate;
- deleting any required app/build file fails closed;
- an empty Android dependency manifest fails closed;
- a real ML framework marker fails the scan;
- a retained model artifact in the runtime tree fails the scan;
- ML dependencies under non-`implementation` Gradle scopes fail;
- documentation-only model-named fixture files do not become runtime surfaces;
- generated audit artifacts do not poison later scans;
- only the root training-control launcher is exempt from self-scanning;
- stale PASS output is removed/replaced when a later audit or publication
  fails.

## Executed evidence

GitHub Actions run
`36519989042` (`Training-control applicability audit`) executed on
commit `2448fe254a9842c06a1fcd2d178b8a2820924e0e` and completed
successfully.

Recorded evidence from the job:

- Python 3.11 test execution: **16 tests, OK**;
- live repository classification:
  `no_retained_trainable_surface`;
- live finding count: **0**;
- scanned source/configuration files: **637**;
- dependency inventory: **8** runtime/test Android dependency declarations;
- applicability certificate upload completed successfully.

Earlier run `36519892250` deliberately failed after a regression fixture
contained the literal ML dependency marker being tested. The fixture was
changed to construct the marker at runtime; this demonstrates the live scanner
also audits its own test source instead of silently exempting it.

The current Git tree also contains **zero** retained files with the model
artifact suffixes guarded by the authority.

## Closure and nonclaims

For the account-wide CUDA/training requirement, Forest Run is closed as
**NOT APPLICABLE / fail-closed monitored**, not as a CUDA-trained repository.

This does **not** claim:

- physical Android-device release acceptance;
- Play Store production acceptance;
- human creative/accessibility approval;
- full game correctness from this one audit;
- that future ML additions remain automatically exempt.

If a real ML framework, model artifact, trainable dependency or optimizer
surface is introduced, the authority is designed to stop issuing the
not-applicable certificate until Forest Run receives a real repository-specific
training architecture and corresponding CUDA/CPU policy.
