# Forest Run — Tree near-miss fixtures and full trunk collision (2026-09-27)

## Exact-head failing evidence

Android run `36334229005` at `5e3c5282` compiled the project and ran 1,156 JVM tests; `JacarandaTest.jacaranda keeps a readable underside lane below the petal veil` and `WeepingWillowTest.willow keeps an explicit duck lane below the curtain` failed. Their third probes used a nearly full-width rectangle below the upper branch/curtain, intersecting the centre trunk; the recent full-traversal fix intentionally preserves a solid trunk **outside** the highlighted crouch-only cutout. Those probes cannot truthfully expect MERCY.

## Precise correction

Each test now positions a small positive-area rectangle immediately below the upper branch/curtain's far-right edge, horizontally outside the centre trunk and outside the highlighted safe lane. It asserts no intersection with either hard collision box and then expects the padded near-miss result. A separate box through the centre trunk at the same vertical level must produce HIT. The already-present real-player standing/duck checks and full scrolling traversal at three reference heights and both speed limits are retained, unchanged.

No production hitbox is weakened and no change in authored geometry or gameplay rewards is introduced. Require successful exact-resulting-head JVM, package/R8 and API-35 checks; graphics and physical-player perception remain separate.
