# Forest Run — Same-time moving Player and Orb pickup (2026-09-28)

## Source finding

The revised Orb sweeper retained a stationary-player assumption: it checked the Orb's preceding/current centre segment against only `player.hitbox` at the end of the frame. The live `Player.update` may move its hitbox upward or downward significantly during that interval; using only its final rectangle can falsely award an Orb whose horizontal crossing occurred before the Player entered the vertical pickup lane, or miss an Orb touched before the Player left that lane. Hazard cores already use simultaneous four-edge sweeps through `SweptCoreOverlap`.

## Source correction

`SeedOrb.checkCollection` accepts optional previous Player bounds. For a live motion sample it constructs previous and current square pickup cores from the stored Orb centres and delegates to the existing allocation-free, same-time `SweptCoreOverlap` solver. `SeedOrbManager.update` supplies `player.previousHitbox` only when `player.hasMotionSample` is true. If no valid player history exists, the previous stationary-hitbox swept behavior is retained. The Orb radius, bob animation, spawn curve, Seed/Bloom rewards and maximum count are unchanged.

Robolectric regressions exercise an endpoint-only false positive, a true mid-frame moving-player overlap, exactly-once collection and malformed historical bounds failing closed. Source tests alone do not establish human/physical-device pickup feel or every possible nonlinear trajectory. Verify exact-HEAD host/JVM/API35 CI.
