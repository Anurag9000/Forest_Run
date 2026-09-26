# Forest Run — Authored deterministic hold durations (2026-09-26)

## Verified source-to-mechanic discrepancy

The four scripted scenarios author different held-jump start/end times in `DebugScenarioScript.stepsFor`, but `GameView.runDebugScenarioScript` always passed 0.35 seconds to `Player.onJumpReleased`. That argument controls the variable jump height through `Player.jumpVelocityForHold`, so the game did not physically execute its own authored release timing even as the trace recorded correct timestamps. Delayed frames that dispatch a start and end together must also preserve the authored hold duration rather than treating it as a tap.

## Fix and compatibility

`advanceTimed` emits an action with its scheduled timestamp and the validated start/end interval for a held-jump release. `GameView` supplies this actual interval to player physics. The previous action-only `advance` remains a wrapper over the same dispatch/trace code, and the authored `stepsFor` values, source fingerprint, and trace schema are unchanged. Dispatch failures continue to leave the current step pending without fabricated trace evidence.

JVM regression tests check all four scripted scenarios, dispatch partition invariance, exact authored release durations, and callback failure/retry. The fixed code does not imply physical-device scenario success or final creative approval.
