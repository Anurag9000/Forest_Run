# Forest Run — Bamboo full-span traversal rather than a momentary fit (2026-09-27)

## Finding and correction

The existing Bamboo reachability test checks that the Player body fits a 150px vertical gap at any **one** jump sample. But all five vertically blocked stalks must cross the stationary Player before safe passage. At the canonical 650px/s opening speed, the BALANCED 285.6px footprint plus a conservative 70px body is exposed for about 0.547s; a momentary fit does not prove a collision-free complete encounter.

Retain all five stalks, their independent rectangular hitboxes, four horizontal spacings, randomized deterministic centre and world-speed movement. Widen the shared vertical tunnel to 300px and sample centres 335–355px above the floor, intersecting the existing finite, reachable-body bounds. This is a specifically tested partial-jump trajectory through an uninterrupted corridor, not a free broad vertical placement or an invisible hazard. The visual safe guides derive from these same colliders.

## Actual regressions

One Robolectric test checks 96 seeded production centres at 720/760/1080/1320/1440px screen reference heights using a real 0.10s held Player jump. For each, it compares its longest uninterrupted collision-body fit against the **whole** Bamboo width plus the widest transient Player body at 650 and 2000px/s, including two frame guards. A second test updates real Player and swaying Bamboo instances over their full moving intersection and asserts no HIT, for four seeds in each compact/balanced/roomy reference class. Earlier one-instant vertical and invalid-geometry tests remain.

This establishes a bounded isolated flight, not all jump/duck strategies, mixed-encounter recovery, physical-device legibility, or human creative acceptance. CI on the final exact HEAD is still required.
