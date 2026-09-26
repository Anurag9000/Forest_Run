# Forest Run — Deterministic encounter population matches its authored definition (2026-09-26)

## Finding

`EncounterScenario.WOLF_CHARGE` and `EAGLE_MARK` each define two ordered encounters and those steps are incorporated into scenario fingerprints. `GameView.prepareEncounterScenario` additionally spawned an immediate Wolf or Eagle outside the director. Consequently actual runs received three instances although the scenario definition and candidate trace described two, and the extra near-player creature could preempt the mechanic meant to be demonstrated by the authored stages. The earlier Eagle offscreen-despawn fix already allows its authored far-right staging to survive.

## Change

Remove the two unauthored warmup spawns; their authored director steps, timing, variants and RNG remain unchanged. `REST_LOOP` deliberately retains its special immediate Cactus to force a terminal/recovery transition. A Python source-contract test checks that it is the sole direct spawn in `prepareEncounterScenario` and verifies Wolf/Eagle continue to have two authored steps each. No scenario fingerprints or authored input trace schedules change.

## Boundaries

This source correction prevents population drift; it is not a claim that all scenario mechanics, frame-perfect movement, live-device readability or human creative acceptance have been established. Use the exact resulting commit's automated workflow as validation authority.
