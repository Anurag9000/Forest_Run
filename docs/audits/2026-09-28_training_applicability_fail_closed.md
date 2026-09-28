# Forest Run — fail-closed ML-training applicability continuation (2026-09-28)

## Repository-specific scope

Forest Run is a native Kotlin Android `SurfaceView`/Canvas game, not an ML training repository. The 878-file GitHub tree was complete (not truncated) when inventoried. The root `run_all_training.py` intentionally performs a source/dependency applicability check rather than fabricating optimizers, training datasets, CuPy substitutions, pressure-running jobs or model checkpoints.

The game still has independent gameplay, Garden/persistence, runtime telemetry, accessibility, Android build/package, physical-device, human/creative, privacy, Play-delivery and accountable release gates. A PASS here addresses only the **absence of retained ML training source**.

## Defects repaired

1. The original authority returned a no-training result even when the repository root was empty. It now requires the Android settings/build scripts, app build script, production manifest, `MainActivity`, `GameView`, actual scanned production Kotlin and nonempty Android dependencies before issuing an N/A certificate.
2. `run_all_training.py` previously computed the new certificate before replacing `.training_control/coverage_report.json`. If a new training marker or filesystem failure caused the audit to raise, an older `status=pass` could remain. The output path is now validated and invalidated *before* source inspection. A rejected audit writes `status=fail`, `classification=unresolved`, `ml_training_applicable=null`, no execution claim and a nonzero exit; even failed publication cannot leave the old PASS at the selected path.
3. The scanner used to exempt every `run_all_training.py` by basename, allowing a new nested script to evade inspection. Only the exact repository-root audit entrypoint is exempt.
4. The earlier source extensions omitted shell launchers and XML manifests. They are now inspected with the Kotlin, Java, Python, Gradle, JSON and other source/configuration text. Generated `artifacts/` and transient environment/cache folders are excluded so a failed generated report cannot make a later clean source falsely fail.

## Test receipts

`scripts/test_training_control_not_applicable.py` covers an empty tree, each absent required Android/build source, missing dependencies, generic application vocabulary, injected ML framework content, nested train-entrypoint aliases, shell/XML markers, generated artifact exclusion, stale PASS replacement and publication failure.

The actual GitHub Actions run [36366366530](https://github.com/Anurag9000/Forest_Run/actions/runs/36366366530) checked out commit `341abc26c21eaa26441f214020b42f050aa0d40d`, executed **12 tests successfully**, and issued a no-retained-training certificate scanning **633 source/configuration files** with **zero detected ML-training findings**. The repository-local certificate also completed successfully in run [36366366579](https://github.com/Anurag9000/Forest_Run/actions/runs/36366366579).

An earlier exact commit `5409b49d403357302fb457837951c0b3b215ded1` passed Android validation with host/packaging and API 35 connected emulator jobs in run [36363796716](https://github.com/Anurag9000/Forest_Run/actions/runs/36363796716). Later Android runs may be cancelled by `cancel-in-progress`, so this earlier result is **not** substituted as full-build evidence for the newer source commit.

## Remaining classification limits

The detector searches explicit ML/framework, optimizer, backpropagation and training-loop markers. It is a conservative, source-specific gate, not a formal proof against arbitrarily hidden or generated training. The normal Android game is not entitled to GPU-training tests by N/A classification. Physical device matrix, signed artifact/store delivery, human review, accessibility, privacy/legal decisions, gameplay quality, performance budgets and final release readiness remain separately gated. No unobserved physical-device session, human signoff, production store release or full feature audit is claimed.
