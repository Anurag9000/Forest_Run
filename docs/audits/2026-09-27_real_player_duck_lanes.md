# Forest Run — Real Player foot anchor and tree duck-lane truth (2026-09-27)

## Source-to-visual discrepancy

`Player.draw` scales the original body about `feetY = y + BASE_HEIGHT`, but `updateDucking` moved `y` to `groundY-currentHeight`. With the canonical 0.55 crouch scale that moves the drawn feet 45px *below* ground. `updateHitbox` also applied a vertical centre offset even though rendering scales about the feet, creating state-dependent collision offsets from the visible silhouette. Willow's hanging curtain and Jacaranda's branch ended well above the normal standing collision top, so their advertised duck lesson was not enforced. Existing encounter tests substituted tiny hand-set rectangles rather than a live Player.

## Correction and bounded verification

Maintain the standing `y = groundY-BASE_HEIGHT` foot anchor while ducking, and align hitbox top/bottom with the same foot-centred scale used by the drawable. Keep horizontal centring and existing collision inset. Lower Willow's visibly drawn curtain bottom and Jacaranda's visibly drawn branch bottom so standing collides but a real grounded duck remains in the nonhazardous marked lane. The highlighted rectangles continue to be derived from the collision geometry and avoid each trunk.

New Robolectric tests check running/jumping/ducking foot transform and hitbox alignment, plus standing-HIT -> duck-inside-highlight-NONE on COMPACT (720px), BALANCED (1080px), and ROOMY (1440px) reference heights for both trees. The old geometry-band tests are retained. Exact resulting commit's host and connected CI are required before automated closure. These source and test assertions do not establish human readability, final licensed art, physical input comfort, or all mixed encounter-sequence feasibility.
