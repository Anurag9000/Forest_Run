# Training-control self-scan closure — 2026-10-04

## Scope

Forest Run is a native Kotlin/Android game. It has no retained machine-learning
training surface on the audited `main` source. The correct training-control
behavior is therefore a fail-closed applicability certificate, **not** invented
GPU/optimizer jobs.

This follow-up closes a remaining authority-boundary gap in which the root
`run_all_training.py` and the entire `training_control/` directory were
excluded from the semantic source scan. Their known files were hash-bound, but
a newly introduced training-control source file or executable ML import in the
root entrypoint could have escaped the semantic detector while producing a new
certificate digest.

## Implemented correction

At commit `32f502d5852d632b981f6b9aef82b6e18efc42bc` the authority now:

- scans `run_all_training.py` as ordinary Python executable source;
- scans all repository-owned source under `training_control/`;
- includes training-control paths in the scope inventory;
- checks model-artifact suffixes under `training_control/` rather than
  exempting that directory;
- continues to mask Python docstrings/string literals/comments before lexical
  policy matching, so detector prose may name frameworks without becoming a
  false training finding;
- still catches executable direct and constant dynamic imports.

Commit `511958d79d26f4b215ad0c53f4a00755061f0074` adds explicit regressions
for a root-entrypoint PyTorch import, a newly added hidden TensorFlow source
under `training_control/`, a model artifact under that directory, and policy
strings that must remain non-executable prose.

## Executed evidence

GitHub Actions run
`37190394158` ("Training-control applicability audit") completed successfully
on commit `511958d79d26f4b215ad0c53f4a00755061f0074`.

Observed job evidence:

- exact candidate checkout succeeded;
- authority compilation succeeded;
- the applicability regression step ran **39 tests** and reported **OK**;
- certificate generation succeeded;
- artifact upload succeeded.

The emitted certificate reported:

- `status = pass`;
- `classification = no_retained_trainable_surface`;
- `ml_training_applicable = false`;
- 649 scanned source/configuration files;
- zero findings;
- eight retained Android dependency declarations;
- source manifest SHA-256
  `54307acf7765c6a8f7c70316d0598ebd64e8af7993b01cac7a8415cbd73ca2cf`;
- scope manifest SHA-256
  `cfc7419d605cebd60d59b97293ea76fb2cffc8791094ff1fba670f177d8aac71`;
- authority manifest SHA-256
  `d304ecb7e5b80124063b1524451b10169d5ec9d89dc9d614152fb3ba05b77099`.

The separate estate-local training-control run `37190394188` also completed
successfully on the same commit.

## What this closes

For the current Forest Run source snapshot, repository-level **ML training
applicability is closed as not applicable** with an executed fail-closed
authority. GPU-first training, optimizer state, CPU/GPU training parity,
distillation, model-family experiment matrices, and training checkpoint/resume
remain correctly marked not applicable because introducing those mechanisms
would create a new ML surface that does not exist in this game.

## What this does not close

This certificate does not certify gameplay correctness, release readiness,
physical-device performance, store delivery, human acceptance, production
signing, or future source changes. Those are governed by Forest Run's separate
Android/release evidence systems. The Android validation run for this exact
commit was still in progress when this audit note was written, so no result
from that run is claimed here.

Any later ML framework dependency, optimizer/training code, serialized model,
opaque executable artifact, or out-of-tree source symlink causes the current
not-applicable authority to fail closed until a real repository-specific ML
training architecture is introduced and audited.
