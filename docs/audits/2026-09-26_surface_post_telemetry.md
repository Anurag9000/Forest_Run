# Forest Run — Surface-only posted-frame telemetry (2026-09-26)

## Verified contract gap

`GameThread`'s production constructor renders with `SurfaceHolder.lockCanvas` and returns only after `unlockCanvasAndPost`. The injected internal/test constructor supplies an arbitrary `() -> Unit` callback (default no-op). Previously *both* paths called `InputLatencyTelemetryRegistry.recordFrameRendered` after the callback returned, allowing a synthetic callback with no surface post to close a touch-to-render latency sample.

## Change

The injected constructor defaults to `postsToSurface=false`; only the production `SurfaceHolder` constructor enables the post signal. This preserves all render timing and exception propagation but aligns the input-latency sample with an actual successful surface post. A JVM regression test opens a pending input measurement, runs an injected render callback, verifies no sample and no swallowed thread failure, and confirms the pending action remains available for a real explicit post notification.

## Boundary

This measures application touch-to-post time, not touch-to-photon or display scanout. Physical instrumentation and device performance acceptance remain external.
