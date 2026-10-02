# Forest Run — SurfaceView pixel resize cannot leave stale logical systems (2026-10-02)

## Cross-layer defect

`SurfaceResizePolicy` correctly states that Player physics, EntityManager spawn bounds, background ground geometry, Menu/Garden layouts, HUD and Rest composition are constructed from the initial surface dimensions and therefore require coherent Activity recreation after a genuine resize. `MainActivity.onConfigurationChanged` enforced that for configuration-size changes.

The independent `SurfaceHolder.Callback.surfaceChanged` path did not. Once systems were initialized it overwrote only `GameView.screenWidth/screenHeight` and the safe-content transform. A real SurfaceView pixel-size change that did not arrive as a configuration change could therefore leave the newly reported surface dimensions disagreeing with every dimension-bound runtime owner.

## Correction

GameView now evaluates the same `SurfaceResizePolicy` inside the runtime-state monitor before publishing any changed dimensions. Initial startup, same-size callbacks and invalid/transient sizes retain the existing in-place transform update. A real post-initialization size change keeps the old coherent logical tuple and posts a host recreation request *after releasing* the runtime monitor.

MainActivity supplies that callback and funnels both configuration and SurfaceView resize paths through one guarded `requestSurfaceRecreation` method, preventing duplicate recreation requests while the old Activity is being replaced. No thread join or Activity lifecycle call occurs under the game-state monitor.

Regression coverage extends the pure resize policy to a pixel-only change and the runtime ownership source contract to require policy-before-mutation and callback-after-monitor ordering.

## Boundary

This closes coordinate ownership for detectable surface-size changes. It does not replace physical cutout/aspect-ratio/tablet acceptance or prove OEM-specific Surface lifecycle timing.
