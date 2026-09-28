# Forest Run — Compact buddy Dog departure resolves before cull (2026-09-28)

## Reproduced lifecycle defect

Buddy Dog is harmless, accompanies the Player for a bounded duration, then enters `BUDDY_DASH` at roughly `screenWidth * 0.25 + 110`. The dash translates at five times world scroll speed. On a 640 px landscape at the supported 2,000 px/s ceiling and legal 50 ms recovery frame, that is a 500 px step: x=270 -> x=-230. `Dog.update` previously marked the Dog inactive at the offscreen threshold, and `EntityManager.update` removes inactive entities before its later collision/pass arbitration. The still-PENDING buddy could therefore disappear without CLEAN_PASS, its unique buddy reward, or a final encounter outcome.

## Correction

Only an unresolved `BUDDY_DASH` defers offscreen deactivation through the current update. EntityManager then sees the Dog wholly behind the Player and resolves the ordinary exclusive CLEAN_PASS path, which invokes the existing buddy reward exactly once. On the next update, the now-resolved departed Dog is culled normally. Hazard Dog behavior, bark projectiles, collision severity, buddy duration/dialogue, reward amounts and persistence policy are unchanged.

A Robolectric integration test uses a 640x360 landscape, production maximum speed and 50 ms steps. It forces only the buddy timer boundary, verifies the 500 px dash survives pending resolution, requires CLEAN_PASS and increased score/Seeds, then verifies normal next-frame retirement.

Exact-head Android/JVM/API-35 validation is required. Physical compact-device feel and visual departure timing remain human/device acceptance work.
