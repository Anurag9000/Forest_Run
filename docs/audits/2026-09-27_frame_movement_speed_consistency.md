# Forest Run — Single effective movement speed per simulation frame (2026-09-27)

## Source-to-runtime finding

The prior `GameStateManager.update` advanced recorded distance and fractional score with the *old* `scrollSpeed`, then recomputed and published the accelerated/debuffed `scrollSpeed` from the new distance. The live `GameView` and `EntityManager` consume this newly published value in the same update for parallax, entity and Orb movement. Consequently the rendered forest moved a different distance from the run's distance/score; the discrepancy was particularly visible for the first frame after a speed debuff was applied or expired. The authored specification requires a coherent captured movement state.

## Change

Compute the effective frame speed from the already-authoritative pre-frame distance and active debuff; publish it once and use it to advance the recorded distance and score. All subsequent world movement consumers of `gameState.scrollSpeed` therefore use the same effective frame speed. Distance-driven acceleration is intentionally sampled at the next simulation frame rather than retroactively applied to the current one. Existing debuff-timer semantics remain: a debuff whose timer expires during this frame lasts through this frame and clears for the next.

Robolectric tests verify the equality of published scroll movement and distance movement over 200 accelerating frames and through a 50ms debuff expiration boundary. No tuning constants or content data changed. Exact-head host/connected CI is the execution authority, not this source inspection alone.

## Acceptance boundary

Per-frame source consistency does not prove subjective pacing, physical-device input latency, frame pacing or final production acceptance.
