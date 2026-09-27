# Forest Run — Full moving Willow/Jacaranda duck underpasses (2026-09-27)

## Concrete source-to-mechanic defect

Previous tests moved a real Player to the one marked low rectangle and established standing-HIT/duck-NONE there. However, both solid trunk boxes extended to the ground at tree centre; the marked rectangle was restricted to the *right* side of the trunk. The player has a fixed horizontal run position while the full tree scrolls left. Therefore a successful early duck beside the trunk could still be followed by an unavoidable trunk HIT as its centre crossed the player; a one-instant geometry test cannot prove complete passage.

## Repair

The full horizontal marked underpass now spans the visible branch/curtain from inset-left to inset-right including the trunk. An actual Player collision body wholly inside this lower channel is exempted before the trunk and upper branch/curtain collision query. Standing bodies cannot fit vertically and still hit. The channel overlay and its markers render **after** the rotated sprite with high opacity to show the low cutout visibly across decorative trunk pixels. Outside the channel the trunk and canopy remain dangerous, and the encounter's aggregate bounds continue to delay final pass until all geometry has cleared. The source sprite is retained; no new binary art or hidden physics is introduced.

Two Robolectric end-to-end traversals move the actual grounded crouching Player and scrolling tree together at base and maximum scroll speeds on 720/1080/1440 reference heights, asserting trunk crossing without any HIT, eventual terminal CLEAN_PASS or MERCY, and exactly one reward. Existing one-instant standing/duck/near-hit tests remain. Exact HEAD's host and connected CI is required. This does not substitute for artist-reviewed underpass graphics, human readability or arbitrary mixed encounter-chain feasibility.
