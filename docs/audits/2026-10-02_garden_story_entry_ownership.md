# Forest Run — Garden initialization is read-only; story progression begins on entry (2026-10-02)

## Cross-layer defect

`GameView` constructs `GardenScreen` during ordinary runtime initialization while the application is still on the Menu and immediately calls `gardenScreen.load()`. That load path called `StoryFragmentSystem.gardenReflection`, `weatherThought`, and `creatureThought`. All three presentation helpers persist one or more Memory Page IDs. Therefore merely launching/initializing the game could advance Garden-derived story progression before the player entered the Garden.

The same refresh order also sampled `memoryPageCount` and built `GardenSanctuaryPlanner` *before* those helpers unlocked new pages. On a legitimate Garden entry the visible Memory Page count and sanctuary projection could therefore lag the newly earned durable state by one refresh.

## Repair

StoryFragmentSystem now exposes explicit read-only preview forms for Garden reflection, weather thought, and creature thought. They share the exact same selection/text code but skip Memory Page persistence. `GardenScreen.load()` uses those preview paths; it may hydrate display state at startup without awarding story progression. The real session transition already owns a `REFRESH_GARDEN` effect for every Menu->Garden and Rest->Garden entry, and that refresh invokes the mutating story forms.

On an actual Garden entry, story-context unlocks now happen before `memoryPageCount` and `GardenSanctuaryPlanner.build`, so the same frame projects the state it just earned. Wardrobe refreshes inside an already-open Garden use preview-only story calls and do not manufacture additional progression.

## Regression

A StoryFragmentSystem test proves all three preview APIs return authored text while leaving the entire unlocked-page set unchanged, then proves the mutating APIs earn pages. A GardenScreen integration test proves `load()` cannot earn a page on initialization, `refresh()` can on real Garden entry, and the screen's Memory Page count immediately equals durable storage.

This is source-level progression ownership. It does not claim a human viewed or approved the resulting story copy, art, layout, or accessibility on physical hardware.
