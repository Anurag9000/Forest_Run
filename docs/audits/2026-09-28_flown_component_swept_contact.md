# Forest Run — Individual flock-bird swept contact (2026-09-28)

## Source-derived missed collision

The general entity sweep correctly excludes Tit/Chickadee aggregate hitboxes because the marked trough or flutter pocket is intentionally unoccupied and must not become one solid collision rectangle. Their real three-to-five or two-to-four component bird rectangles, however, had only endpoint HIT checks. At the legal 50 ms/2,000 px/s world step, a flock translates 100 px while a falling Player can cross a bird: prior and final pairs can both be disjoint even when their interpolated physical bodies overlap at the same instant. A later pass might award MERCY/CLEAN_PASS after an actual sampled-away contact.

## Remedy

Both species now retain one preceding RectF per existing bird before movement/altitude changes, with the same counts and no per-frame RectF allocation. After the current endpoint test, use the existing strict simultaneous SweptCoreOverlap on each individual current/previous bird and Player, with the established valid-sample gates. The pure onCollision query still returns the first physical HIT before any near-miss and does not sweep aggregate or decorative guide/pocket boxes. Do not retune altitude, rhythm, random selection, collision geometry, reward or visual safe space.

One integrated Robolectric fixture runs real maximum speed, real manager motion-sample admission and a falling Player against each flock. It checks all bird endpoint pairs miss, each prior component snapshot is captured exactly, one intervening HIT wins and no mercy/clean pass is awarded. Existing safe-passage and full-player-trough/pocket tests remain authoritative for visibly safe lanes. Exact-head host/API35 CI required. This closes scoped flock-component tunneling, not all other multipart/animated encounters or human/device acceptance.
