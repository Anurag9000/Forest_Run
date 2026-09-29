# Forest Run — training-control and opening-reset validation (2026-09-29)

## Scope

This note records two independent corrections and their executed evidence:

1. the fail-closed repository authority for declaring ML/training controls not
   applicable to this native Android game; and
2. the stale opening-guidance reset regression discovered by the full Android
   validation workflow.

It does **not** replace physical-device, Play-delivery, human accessibility,
visual, privacy, signing, or final release acceptance.

## No-retained-training authority

`training_control/forest_no_trainable_authority.py` already scanned the live
application and dependency source for actual ML/training markers. The scanner
could, however, return an empty clean result for an empty/partial repository.
The certificate boundary now additionally requires the canonical Android
build/manifest/entrypoint source and at least one production Kotlin source plus
real Android dependencies before issuing
`no_retained_trainable_surface`.

Regression coverage explicitly proves that:
- an empty repository cannot be certified;
- each required app/build file is individually required;
- a dependency-free app fixture cannot be certified;
- a real ML framework marker still fails closed; and
- ordinary application vocabulary does not create false ML findings.

Current-head GitHub Actions:
- Training-control applicability audit run **36531498981**: success.
- Estate local training-control certificate run **36531498984**: success.

Therefore model training, CUDA-first training, optimizer checkpointing and
CPU/GPU training parity remain **not applicable to the current Forest Run
source**, rather than unimplemented training features.

## Opening reset regression

The full Android workflow found one stale test among 1,235 unit tests:
`GameStateManagerTest.reset run clears opening guidance state` still expected
the historical `Find The Stride` cue after `resetRun()`.

The product source had already intentionally aligned the authored opening:
normal play seeds **Duck first**, and the first guidance cue is
`Duck The Low Flyer`. Dedicated unit/integration tests and
`docs/audits/2026-09-29_opening_seeded_guidance_alignment.md` establish that
semantic contract. The reset test was corrected to require the canonical Duck
lesson after transient input state is cleared; gameplay code was not changed.

Commit: `76401c71cb7566e7c55e05010999fd9ad7e94d04`.

Executed validation on that commit:
- Android validation run **36531499165**: success.
- Host/release/lint/packaging job: success, including compile, all unit tests,
  lint, debug APK/androidTest APK, release bundle, R8/source immutability and
  packaged page-size inspection.
- API-35 connected smoke and deterministic-evidence job: success.

## Remaining gates

These successes are reproducible CI/emulator evidence, not physical-device or
production-release certification. The repository still requires its declared
real-device matrix, internal Play delivery/certificate binding, human gameplay
and accessibility review, final creative/rights/privacy/store approvals,
production signing identity and final accountable release decision.
