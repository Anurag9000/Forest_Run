# Forest Run — No post-terminal mutation in the same simulation frame (2026-09-26)

## Defect

`TerminalHitOutcomeCoordinator.complete` builds the completed Rest summary and commits the run after `TerminalHitImpactCoordinator` has detached the ghost snapshot. Previously, `GameView.updateBounded` then continued past `TERMINAL_COLLISION_COMPLETED` in the same frame into `ghostRecorder.record(deltaTime, player)`, `consumePacifistReward`/Seed/score awards, and milestone consumption. That permitted live run state to drift after the authoritative terminal snapshot, and a detached recorder could acquire an unassociated post-death frame. The next frame's DYING state gate does not protect the remainder of the *current* update.

## Remediation and check

Immediately return from the named `updateBounded` function after the terminal summary is adopted and the typed death-transition event is applied. All terminal impact, relationship, copy, quote, persistence, sound and haptic ordering is preserved; death FX update under the existing DYING branch on subsequent frames. The established source-contract test now extracts the actual Kotlin terminal branch, verifies summary -> typed transition -> return ordering and guards that same-frame ghost/reward/milestone code is unreachable through that path. The exact resulting commit's JVM/instrumentation workflows remain the executable authority.

## Limits

This fix addresses terminal-frame ownership; it does not manufacture physical-device Rest feel or external release approval. A rare failed transition effect still requires separate observation/handling through the session coordinator's existing failure disposition rather than pretending a persisted summary can be rolled back.
