# Forest Run training-control applicability closure — 2026-09-29

## Repository-specific conclusion

Forest Run is a native Kotlin/Android SurfaceView game. The retained source,
build manifests and application dependencies do not contain a machine-learning
training surface. GPU-first model training, CuPy substitution, optimizers,
training checkpoints and CPU/GPU training parity are therefore **not applicable**
to this repository. Adding synthetic ML jobs would be a false capability.

The local authority remains fail-closed: if a retained ML/training marker appears,
the repository-level certificate stops passing until Forest Run acquires a real,
repository-specific scientific/training DAG.

## False-pass hardening

The previous scanner could return an empty finding set for an empty or incomplete
repository tree. That absence was insufficient evidence to certify a real Android
application as non-trainable.

`training_control/forest_no_trainable_authority.py` now requires the concrete
Android application/build surface before certification:

- `settings.gradle.kts`;
- root and app `build.gradle.kts`;
- `app/src/main/AndroidManifest.xml`;
- `MainActivity.kt`;
- `engine/GameView.kt`;
- at least one production Kotlin source in the scanned surface;
- at least one retained Android dependency.

The low-level `audit(root)` function remains composable for synthetic scanner
fixtures, while `require_no_trainable_surface(root)` is the repository-level
certificate boundary and rejects empty, partial and dependency-free trees.

Regression coverage in `scripts/test_training_control_not_applicable.py`
explicitly covers an empty repository, removal of each required application
file, an empty dependency manifest, a real framework marker, ordinary
application vocabulary, and the actual root certificate.

## Executed evidence

Candidate commit `4708779c14388e458921675c5869953db6b6bdec` passed:

- Training-control applicability audit: GitHub Actions run `36523170411`;
- Estate local training-control certificate: GitHub Actions run `36523170375`;
- Android validation: GitHub Actions run `36523170374`.

The Android workflow completed host/release/lint/packaging, debug unit tests,
release hardening, native page-size inspection, R8/source immutability, and its
API 35 connected smoke/deterministic-evidence job.

That Android run also verifies the repaired Hyacinth mid-frame brush regression.
The prior failing test had hard-coded a maximum-speed travel assumption while a
fresh `GameStateManager` starts at base scroll speed. The test now derives the
required recovery-frame travel from the live brush width and current world speed,
so both endpoint samples genuinely miss and only swept contact can produce the
STUMBLE.

## Remaining release evidence

This closes only the repository's **training-control applicability** and the
specific candidate's automated Android validation. It does not manufacture or
replace the external product-release evidence already documented by Forest Run:
real production signing, Play internal-track delivery, representative physical
device sessions, human visual/gameplay/accessibility approval, privacy/store
declarations and accountable final release approval remain separate gates.
