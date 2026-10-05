# Forest_Run no-retained-training-surface closure audit — 2026-10-05

## Classification

Forest_Run is a native Android/Kotlin SurfaceView game. The repository-level
training controller must **not** invent optimizers, datasets, CUDA jobs,
checkpoint schemas, experiment matrices or model registries for ordinary game
code. The correct training-control behavior is a fail-closed applicability
authority that proves the current repository has no retained ML/training
surface.

This audit closes only that classification contract. It is not Android release
acceptance, physical-device validation, graphics/performance certification,
store approval, signing approval or gameplay-completeness evidence.

## Fail-closed authority

The current authority binds and verifies:

- required Android/build entry files, which must be regular repository-owned
  files rather than absent paths or symlinks;
- production Kotlin source presence and actual Android dependency entries;
- source bytes, repository-owned scope inventory and the exact authority code
  through independent SHA-256 manifests;
- Python executable syntax, dynamic literal imports, notebooks, shell/XML/
  Gradle/TOML/JSON/property sources and dependency manifests;
- retained standard ML artifacts including TFLite, ONNX, Torch checkpoints,
  Keras/CoreML/HDF5/PB and Python/scikit serialized forms;
- uninspected executable binaries (with the Gradle wrapper JAR as the explicit
  build-tool exception);
- product/runtime archives such as ZIP/TAR/TGZ/7z which otherwise could hide
  trainers, model files or native libraries from the text scanner;
- source symlinks and malformed notebooks.

The root certificate deletes any earlier PASS before scanning current source and
writes an explicit FAIL certificate if applicability cannot be proven. The
dataset-cohort N/A certificate must match the same source/scope/authority
manifests before the root can publish PASS.

## Latest hardening

Commit `95caa315994d640f5a51041af598807a0f5670be` added fail-closed
classification for retained opaque source archives and serialized
`.joblib`, `.pkl`, `.pickle` and `.npz` model/policy artifacts.
Commit `a5830d0774394ef57d8046aeed510077ef4564c7` added regressions
covering those hidden-runtime cases while preserving documentation archives
outside the product-source authority.

## Executed evidence

GitHub Actions run `37312819760` used an assigned GitHub runner. At the
observed checkpoint its substantive steps had completed successfully:

1. exact candidate checkout;
2. Python setup;
3. compile of local applicability authority;
4. absent/partial Android tree and injected-training regression suite;
5. live no-retained-trainable-surface certification;
6. applicability certificate upload.

Only post-action cleanup remained in progress at that instant. This is genuine
execution evidence for the **source applicability contract**. It does not prove
anything about physical Android devices or release acceptance.

## Remaining repository work

Training-control requirements that presuppose an ML training surface remain
not applicable unless future source adds one. Forest_Run's remaining work is
its native Android/game/release evidence: physical-device performance,
connected tests, human/accessibility review, final creative and rights
approval, production signing/store delivery, privacy/store declarations and
other externally accountable release gates already tracked by the repository.
