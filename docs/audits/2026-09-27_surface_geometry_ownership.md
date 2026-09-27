# Forest Run — Coherent surface and safe-inset publication (2026-09-27)

## Finding

`GameView` serializes simulation update, draw, pointer decisions, debug reset and accessibility actions on `runtimeStateLock`. The surface-change callback and safe-area-inset setter were exceptions: each separately wrote geometry and rebuilt the shared `safeContentTransform` outside the monitor. Their independent callback threads could interleave, publishing a transform constructed from a mismatched width/height/inset tuple while draw/touch/accessibility read it. Volatile fields make a single publication visible; they do not make several independently written fields one consistent geometry transaction.

## Remediation

The two callbacks now acquire the same runtime monitor around their complete geometry mutation and `rebuildSafeContentTransform`. Surface initialization already owns that monitor. The transform remains published as one immutable/volatile object for readers. No thread join or `SurfaceHolder` operation occurs inside the newly guarded callback bodies.

The existing runtime-ownership source test now protects both callback sites, their monitor-before-write ordering, and the single publication helper. Exact-head host/connected execution remains the authority. Orientation/safe-area *visual* fit and physical touch-target verification still need hardware evidence.
