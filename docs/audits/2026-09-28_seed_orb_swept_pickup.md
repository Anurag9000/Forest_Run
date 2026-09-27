# Forest Run — Swept Seed Orb pickup at bounded max-speed frames (2026-09-28)

## Reproduction from current owners

`FrameInputAdmission` permits one bounded 50 ms recovery step, and `GameConstants.MAX_SCROLL_SPEED` is 2,000 px/s. An Orb consequently advances 100 px in one valid update. Its core has a 52 px diameter; a jumping Player's horizontal hitbox is `72 * 0.85 - 20 = 41.2` px wide. Their combined sampled overlap span is only 93.2 px. `SeedOrbManager.update` moved the Orb first and called the former endpoint-only `SeedOrb.checkCollection` after movement. An Orb could cross the actual pickup region without overlapping on either sampled endpoint, making a properly approached collectible disappear without its Seed/Bloom reward.

## Remediation

Remember the previous core centre before each admitted update. Keep the exact existing instantaneous rectangular pickup, and when that misses, intersect the previous-to-current centre segment against the Player's hitbox inflated by the *same 26 px core radius* on each axis. A no-allocation two-axis slab interval check prevents a diagonal union-AABB false pickup. Collection remains an exclusive terminal claim, and expiration/offscreen state still wins. Malformed Player rectangles fail closed. Normal 16 ms updates and all presentation geometry are unchanged; no enlarged stationary pickup or reward-rate tuning.

Four Robolectric cases cover an actual 100 px tunneling move with both endpoint cores disjoint, vertical separation, diagonal projection overlap without concurrent physical overlap, and a lifetime-expired Orb. Current sweep is relative to the sampled Player hitbox and linearly interpolates the short bob between frame centres; full continuous Player/bob trajectory certification is *not* claimed. The final exact-head host and API-35 workflow is authoritative for execution.

## Acceptance boundary

This fixes a verifiable collectible-sampling hole. It does not prove all Orb placements are human reachable across mixed encounters, device input latency or physical game feel.
