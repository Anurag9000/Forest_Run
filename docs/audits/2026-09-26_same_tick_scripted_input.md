# Forest Run — Same-tick deterministic gameplay input admission (2026-09-26)

## Ground truth

`GameView.updateBounded` ticks ordinary input at the start, updates game time, then updates the player and encounters and resolves collisions. Previously `runDebugScenarioScript` dispatched authored inputs *after* player physics, entity update, and collision resolution. Its trace truthfully recorded dispatch, but the actual jump/duck response was delayed until the next simulation frame and could not prevent an overlap during the current frame. This differs from the normal player's before-physics input path and from the intended deterministic authored action timing.

## Correction

After game time has advanced and Bloom/player presentation state is prepared, dispatch due scripted actions immediately before `player.update`. Keep the action trace, hold-duration fix, authored timestamps, and scenario fingerprints unchanged; execute player physics and entity/collision checks afterward in the same frame. A Python cross-layer source-order test proves there is exactly one scheduled dispatch and that `gameState.update -> runDebugScenarioScript -> player.update -> checkCollisions` remains ordered.

## Boundary

Frame-level timing correctness is source-addressable and testable, but the test does not assert physical-device input-to-photon latency or every scenario's human gameplay readability. Existing exact-head JVM/connected workflows and physical evidence remain separate requirements.
