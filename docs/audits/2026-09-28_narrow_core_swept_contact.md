# Forest Run — Narrow hazard body tunneling at bounded recovery frames (2026-09-28)

## Source-derived reproduction

A legal GameThread/FrameInputAdmission recovery step is 0.05 seconds at a maximum 2,000 px/s scroll: static scroll-bound cores advance up to 100 px. At the minimum legal thin-sprite geometry, the Lily's 70px body with 18% horizontal inset has only 44.8px of solid horizontal collision width; the grounded Player's width is 72 - 2*10 = 52px. Their combined 96.8px intersection span is smaller than one admitted displacement. Hedgehog's minimum 50px body with 8% inset has a 42px core and moves at 1.15 times scroll speed (up to 115px per recovery step). Endpoint-only probes could miss genuine body contact, then resolve a provisional mercy as an avoided hit or later a clean pass.

## Scoped repair

Capture the Player's actual primary hitbox at the start of each admitted physics update and each entity's primary core before its update. The EntityManager runs the native pure collision query first and, for unconditional single-body Cactus/Lily HIT and Hedgehog STUMBLE only, compares it to an exact-time swept core overlap, selecting the stronger result through the existing priority owner. The four interpolated strict edge inequalities must have a nonempty COMMON time window. This prevents a broad union-AABB diagonal false positive, does not enlarge the current-frame hitbox or mercy band, and lets actual HIT/STUMBLE supersede provisional mercy. Motion history is unavailable until a genuine sample exists and resets for the Player's next run. Multi-part trees/flocks/window hazards, staged Owl/Eagle, buddy Dog and its projectiles are deliberately not treated as one solid aggregate.

## Execution and limits

Pure regression tests cover a missed end-to-end horizontal crossing, a diagonal spatial-union false positive, strict grazing/nonfinite input, and a moving Player. Robolectric manager tests exercise actual Lily and Hedgehog at max-speed, checking no endpoint contact and correct terminal outcomes without ordinary pass/mercy double credit. Obtain exact HEAD host/JVM/API-35 CI before claiming test execution. This scoped correction is not a certified continuous collision system for all nineteen authored encounter families or all player-trajectory and device scenarios.

The real integration fixture uses a valid 4-frame intentionally thin test sprite to exercise the smallest supported production sizing rule; this does not assert that the currently installed Lily artwork is exactly 70 px wide.\n