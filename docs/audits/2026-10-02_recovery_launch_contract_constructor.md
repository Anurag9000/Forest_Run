# Forest Run — Recovery-maintenance launch contract survives GameView constructor evolution (2026-10-02)

## Exact-head failure

Android validation run 36989498179 on `69d22730` failed in the Python source-contract suite before Java/Kotlin setup. The failing test attempted to locate the literal `gameView = GameView(this)`, but production now constructs `GameView(this, ::requestSurfaceRecreation)`. The underlying launch ordering remains correct: save repair, then recovery maintenance, then feedback/runtime validation, then GameView construction.

## Correction

The source contract now anchors on the semantic assignment/construction prefix `gameView = GameView(` instead of the obsolete one-argument constructor spelling. It continues to assert that recovery maintenance occurs after `SaveIntegrityManager.repair` and before the GameView is constructed. No production runtime code or acceptance evidence is changed.

Exact-head CI must be rerun; this document does not convert the prior failed workflow into a pass.
