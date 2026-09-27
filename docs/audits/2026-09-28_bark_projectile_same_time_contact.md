# Forest Run — Dog bark shockwave swept physical contact (2026-09-28)

## Source finding

The current production loop samples one bounded frame after both Player and Dog move. Dog's separately animated bark projectile and primary hazardous body previously checked only end-frame rectangles, unlike recently corrected Cactus/Lily/Hedgehog cores and moving Seed Orbs. The generic manager cannot sweep Dog's entire multi-part encounter box, but the Dog-owned collision query can handle its harmless-buddy versus hazardous-body modes and independent bark cores correctly. A falling or rising Player may overlap a moving bark shockwave during the interval while both endpoint rectangles miss. Conversely, sweeping the shockwave against only the final Player rectangle could invent a collision that occurred at different times.

## Change and regression

Each projectile reuses one stored previous rectangle and captures it before its existing movement. Its pure `collides` query retains end-frame overlap and additionally uses the established simultaneous four-edge `SweptCoreOverlap` when a valid Player motion sample is present. The HAZARD-only primary body likewise uses the existing EntityManager-captured previous core and Player motion samples; buddy mode still exits before any collision query. The Dog buddy mode, projectile visual, bark timing, velocity, damage severity, mercy proximity and existing entity identity remain unchanged. Three Robolectric tests create a real hazard Dog/bark projectile, move it one bounded step, distinguish true mid-frame contact from spatially overlapping sweeps at different instants, and verify a real swept hazardous body hit. Exact-head host/connected CI is the executable authority.

This does not claim full action-sequence fairness, all visual projectile silhouettes or real-device readability; it closes a sampled-contact gap for the separate bark core.
