# Forest Run — Return-moment presentation keeps one local-day identity (2026-10-02)

## Edge-case defect

The Garden now defers Return Moment consumption until the first rendered frame, but preview and acknowledgement still sampled `System.currentTimeMillis()` independently. If a Garden transition prepared a daily/absence moment immediately before local midnight and the first render occurred immediately after midnight, the player could see the moment selected for day N while persistence marked day N+1 as already greeted. The next legitimate day-N+1 Garden entry would then lose its daily greeting.

## Correction

`GardenScreen.refresh()` captures one non-negative wall-clock timestamp when the real Garden entry prepares the Return Moment. The preview is selected with that timestamp and the pending acknowledgement stores it alongside the moment. The first rendered frame acknowledges using exactly the same timestamp, then clears both pending ownership and the timestamp. Non-entry `load()`/wardrobe refreshes cannot replace a still-pending moment or its day identity.

The ReturnMomentsSystem API is unchanged; it already accepts an explicit `nowMs`. This is presentation transaction ownership, not a new clock or calendar policy.

## Regression

The GardenScreen integration test now reads the prepared timestamp, proves entry itself is still non-mutating, renders the first frame, and requires persisted `lastActiveAtMs` to equal the exact prepared timestamp rather than a newly sampled draw time. Existing LocalDayClock tests remain authoritative for local-calendar day IDs.

This does not replace physical-device or human validation.
