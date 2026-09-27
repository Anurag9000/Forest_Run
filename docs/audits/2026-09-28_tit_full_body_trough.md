# Forest Run — Tit rhythm trough matches real Player geometry (2026-09-28)

## Defect

`TitGroup.update` drew its trough guide at `0.42 * birdH` high (~26px at the baseline 62px bird). The actual airborne Player hitbox can be 100px high; no full collision body could fit within the highlighted reward lane. `keptBeat` nevertheless became true on any 1px intersection with that strip, granting bonus points and a Seed without the actual threading implied by the visual cue. The old unit test inserted a synthetic 44px-wide, strip-height Player rectangle rather than verifying the real jump.

## Repair and regression

Set the guide height to the current `Player.BASE_HEIGHT` plus the existing readability staging margin; this fits the full airborne collision body and remains entirely below the birds at the authored wave extremes. Require complete containment of the live hitbox for `keptBeat`. Preserve the canonical Tit wave, collision hitboxes, spacing, SFX, spawn policy and score amounts.

The Robolectric test advances a real Player through the current jump physics in 10ms steps, checks that at least one actually sampled frame fits the highlighted trough without a bird hit, rejects a grazing partial overlap, and verifies full containment earns the intended single Seed. This proves instantaneous full-body and single-wave-window coherence, not arbitrary mixed encounter traversal, human readability or the physical-device release gate.
