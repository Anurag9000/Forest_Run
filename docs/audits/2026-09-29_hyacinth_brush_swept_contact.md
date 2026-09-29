# Forest Run — Hyacinth visible brush swept STUMBLE contact (2026-09-29)

## Reproduced sampled-contact gap

Hyacinth has two physical zones: a narrow lethal core and a larger visible soft brush that is explicitly a nonlethal STUMBLE. EntityManager already sweeps Hyacinth's primary core as an unconditional HIT between frame samples, but Hyacinth's brush query used only the final `brushBox`. At the admitted 50 ms recovery step and 2,000 px/s world speed, the 64-ish px brush can translate 100 px; a Player positioned in the brush-only vertical band can be untouched at both endpoints while the visible brush crosses them mid-frame. The old path then reported no contact and could later grant a clean pass.

## Correction

Hyacinth now retains the previous real brush rectangle at the start of each update. Its pure collision query preserves direct core HIT first, direct brush STUMBLE second, then uses the existing exact same-time `SweptCoreOverlap` for previous/current Player and brush rectangles. EntityManager's primary-core sweep still outranks brush STUMBLE whenever the lethal core actually intersects during the same interval.

A Robolectric integration regression uses the live 2,000 px/s movement step, places a stationary sampled Player entirely in the brush-only vertical band, verifies both endpoint rectangles miss, and requires a single STUMBLE with no clean-pass or mercy reward.

## Boundary

This closes sampled physical contact for the existing drawn brush geometry. It does not alter Hyacinth art, sway, reward tuning, human readability, or physical-device acceptance.
