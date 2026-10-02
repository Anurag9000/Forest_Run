# Forest Run no-training applicability authority audit — 2026-10-02

## Scope and decision

Forest Run is a native Kotlin/Android SurfaceView game. The retained source
contains no machine-learning model-training pipeline, optimizer, training
dataset/cohort, model-family experiment DAG, training checkpoint/resume system,
or CUDA training workload. The repository therefore must not receive invented
GPU/model-training jobs merely to satisfy an account-wide training controller.

The repository-specific authority remains fail closed: if a retained ML,
optimizer, model artifact, opaque uninspected code artifact, notebook, shell
entrypoint, or ML dependency appears, the no-training certificate is rejected
until that new surface is explicitly engineered and audited.

## Hardened source evidence

The authority in `training_control/forest_no_trainable_authority.py` now:

- requires the real Android build/manifest/application entrypoint surface before
  any repository-wide "not applicable" decision can be issued;
- rejects required source supplied through symlinks and rejects other retained
  source symlinks without dereferencing external targets;
- scans Kotlin/Java/Python/JS/TS, Gradle/TOML/YAML/JSON/XML, shell/batch/PowerShell,
  properties, ProGuard, extensionless Gradle wrapper and standard dependency
  manifest files;
- parses Jupyter notebooks and scans **code cells only**, so Markdown discussion
  is not mistaken for executable ML while malformed notebooks fail closed;
- ignores Python comments/string prose but detects direct and supported dynamic
  framework imports such as `importlib.import_module` and `__import__`;
- detects retained ML model artifacts including TFLite, ONNX, Torch checkpoints,
  safetensors, Keras and Apple model packages outside documentation;
- does not let the historical `Final_Assets (2)` product-assets directory hide
  a future model artifact;
- rejects uninspected executable archives/native libraries (AAR/JAR/SO/DLL/
  dylib/wheel) except the explicit Gradle wrapper JAR required to build Android;
- inventories Android dependency configurations beyond only `implementation`;
- binds parsed source bytes to `source_manifest_sha256`;
- binds the relevant repository path/type inventory to
  `scope_manifest_sha256`, preventing an old PASS from appearing current after
  a newly added unscanned/model path;
- binds the exact root launcher and local authority modules to
  `authority_manifest_sha256`;
- cross-binds the scan, dataset-cohort N/A certificate and root certificate to
  those source/scope/authority snapshots;
- removes stale prior PASS output before auditing, and publishes a failure
  certificate if the live applicability check fails.

Generated audit evidence, build outputs, virtual environments and documentation
are excluded from executable-source semantics so the authority does not poison
itself with its own prior reports. Documentation model filenames remain prose,
not runtime model evidence.

## Executed verification

GitHub Actions run **37015111339** on commit
`e65224fbf111b4319da7fbace28a0c7f6f86042b` completed successfully.
The workflow compiled the local authority, ran
`scripts/test_training_control_not_applicable.py`, and reported:

- **30 tests run**
- **OK**
- final no-retained-trainable-surface certificate emitted successfully.

The estate-local training-control certificate run **37015110857** also
completed successfully on the same commit.

The Android validation workflow for that commit was still pending when this
audit note was written. The no-training certificate does not replace Android
build/lint/unit/instrumentation, real-device, store-delivery or human acceptance.

## What this does not claim

- It is not CUDA execution evidence.
- It does not claim CPU/GPU parity, because there is no retained ML training
  workload to compare.
- It does not claim final game/release readiness.
- It does not manufacture synthetic datasets, optimizers or ML experiments.
- It does not waive future review: adding a genuine ML/training surface must
  invalidate this classification and introduce a real repository-specific
  training architecture rather than extending this exemption.
