# Forest Run — Random spawn lead survives temporary speed-debuff expiry (2026-09-28)

## Reproduced state-transition gap

Ordinary random staging used the *current effective* `gameState.scrollSpeed` to convert the action-reaction window into distance. A Hedgehog stumble slows the world to 50% for three seconds, but the Player recovers from STUMBLE sooner and random admission is allowed again in RUNNING/LANDING. If another action keeps admission blocked until late in that residual slow, the accumulated spawn gap is retained and a new encounter can stage while current speed is still halved. That encounter can then spend most of its approach after the debuff expires and the world returns to its undebuffed speed, consuming the supposedly safe lead too quickly.

## Correction

`GameStateManager` now exposes the canonical current distance-driven speed before temporary debuffs and uses the same helper internally for normal speed calculation. Random staging budgets action lead using the greater of the current effective speed and this recoverable undebuffed speed. This does not alter the actual debuff, speed curve, spawn-frequency gap, or encounter movement; it only prevents a transient slowdown from shrinking future reaction distance.

A compact-surface Robolectric regression reaches the 2,000 px/s ceiling, applies a near-expiry 50% slow, forces the already-accumulated production gap eligible, lets EntityManager stage one real random encounter, and verifies its captured pre-update physical core begins at least the undebuffed action-safe lead from the Player. It then verifies the world returns to the speed used for admission.

This remains a source-side action envelope rather than measured human reaction-time approval.
