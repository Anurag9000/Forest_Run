# Forest Run — Hyacinth full encounter mechanics and visible brush truth (2026-09-27)

## Source-derived issue

The authoritative game canon requires Hyacinth to distinguish lethal core HIT, nonlethal brush STUMBLE, an avoided MERCY near miss, and CLEAN_PASS. The runtime previously returned `MERCY_MISS` on physical overlap with its visible `brushBox`. That incorrectly rewarded touching the brush as safe avoidance (especially after mercy became passage-completed), and left the advertised STUMBLE outcome unwired. `Entity.encounterBounds` also defaulted to the smaller lethal core, allowing pass resolution while the visibly drawn brush could still reach the player.

## Fix

The existing drawn geometry remains the source of truth: core overlap gives HIT; visible soft-brush overlap gives STUMBLE; a separate narrow halo outside the brush gives provisional MERCY_MISS, which `EntityManager` only awards after safe passage. `encounterBounds` now includes the whole brush. Initialize that geometry alongside the core and refresh it on each update. No new hidden dangerous rectangle, score tuning, audio file or art asset is invented.

Robolectric tests exercise all four query bands, collision arbitration's terminal STUMBLE, absence of later clean-pass reward, and postponement of passage until the brush clears. Check the exact resulting CI before claiming execution passed.

## Limits

The updated semantic/geometry contract does not prove frame-perfect physical-device presentation, sprite editing, haptic feel, or final human approval.
