# Forest Run — Return moments consume only after Garden presentation (2026-10-02)

## Reproduced source/spec violation

The authored contract says a Return Moment is consumed only when actually shown in the Garden and that preview APIs remain non-mutating. `GardenScreen.refresh()` previously called the mutating `ReturnMomentsSystem.resolveGardenMoment` immediately on Garden entry. That method advanced `lastGardenGreetingDay` and `lastActiveAtMs` before any Garden frame was drawn. If the Surface disappeared, the app was backgrounded/killed, or rendering failed between transition and first draw, the next visit could treat a greeting as already consumed even though the player never saw it.

## Correction

Return-moment selection is now pure. `GardenScreen.refresh()` prepares the same preview and records only an in-memory pending acknowledgement. The Garden acknowledges the moment after its title, line and visitor presentation commands have executed in the first actual `draw` call; subsequent frames do not acknowledge again. The compatibility `resolveGardenMoment` API still performs preview + acknowledgement for direct callers, while `previewGardenMoment` remains strictly non-mutating.

The acknowledgement reloads the current persisted ReturnMomentState and changes only the activity/greeting cursor, preserving the latest rough-run streak. Selection wording, priority ordering, visitors and sanctuary projection are unchanged.

## Regression

ReturnMomentsSystem tests prove previews are repeatable and state-identical until explicit acknowledgement. A Robolectric GardenScreen integration test proves `load()+refresh()` leaves durable ReturnMomentState untouched, the first rendered Garden frame consumes it, and a second frame is idempotent.

This closes source-level presentation ownership only. It does not claim a human actually perceived/read the frame; real accessibility, readability and physical-device acceptance remain external.
