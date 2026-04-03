# Forest Run

Native Android endless runner in Kotlin using a custom `SurfaceView` game loop. A handcrafted, personality-rich forest journey — not a prototype.

## What It Is

A lush cottagecore endless runner with Ghibli × Stardew Valley tone. The player runs through five atmospheric biomes, collecting seeds, activating Bloom power states, practicing mercy on the creatures she meets, growing a personal garden, and slowly earning the forest's trust across many sessions.

**Core loop:** run → soft failure → rest reflection → Garden return → run again

## What's Built

Every engine feature is fully implemented:

- `SurfaceView` render loop, 60 FPS, frame-independent `deltaTime`
- Player state machine: run, jump (variable height + Mario abort), duck, apex hover, squash/stretch, Bloom, rest, stumble
- 19 entity classes across flora, trees, birds, and animals — each with unique behavior, personality, and authored payoff
- Collision resolution: `HIT`, `STUMBLE`, `MERCY_MISS`, `NONE` with proximity-based mercy window
- Five-biome cycle (Meadow → Orchard → Ancient Grove → Dusk Canyon → Night Forest)
- Seeds, Bloom meter (8 seeds → 6s invincibility), Bloom conversion, Bloom spectacle
- Mercy hearts + pacifist route tiers (Kind, Merciful, Peaceful) carrying through rest, Garden, and persistence
- Ghost replay with context-aware visibility policy
- Forest memory: mood system, relationship arcs (Cat/Fox/Wolf/Dog/Owl/Eagle), return moments, story fragments, session arc composition, sanctuary planner
- Garden screen with plant unlock, wardrobe, sanctuary atmosphere, and carry-home state
- HUD: score, distance, seeds, Bloom meter, mercy hearts
- Camera shake (trauma-based, correctly fires once per frame)
- Particle system with BLOOM, MERCY, DEATH, SEED, DUST, and ambient presets
- Haptics, audio (adaptive music with leitmotif signatures), dialogue bubbles, flavor text
- Local persistence: high score, lifetime seeds, ghost run, garden state, relationships, emotional memory
- Debug scenario launcher mirroring the full device acceptance checklist

## What Remains

**All remaining items are hardware validation or store pipeline.** No engine features are missing.

See [`docs/RELEASE.md`](docs/RELEASE.md) for the complete checklist.

## Documentation

| File | Contents |
|---|---|
| [`docs/GAME_DESIGN.md`](docs/GAME_DESIGN.md) | Vision, design pillars, session lifecycle, all systems, all 19 entities |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Runtime structure, all engine systems, constants, wiring |
| [`docs/RELEASE.md`](docs/RELEASE.md) | Build commands, device acceptance checklist, open items |

## Build & Test

```bash
bash gradlew testDebugUnitTest
bash gradlew assembleDebug
bash gradlew assembleDebugAndroidTest
bash gradlew connectedDebugAndroidTest
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Canonical Runtime Truth

- **5 biomes:** `MEADOW`, `ORCHARD`, `ANCIENT_GROVE`, `DUSK_CANYON`, `NIGHT_FOREST`
- **Bloom:** 8 seeds → 6 seconds invincibility
- **Input:** tap jump, hold for higher jump, swipe-down duck — gesture anywhere
- **Failure flow:** run → rest summary → fade → Garden → run
- **Package:** `com.yourname.forest_run`, Min SDK 24, Target SDK 34, Kotlin, sensorLandscape
