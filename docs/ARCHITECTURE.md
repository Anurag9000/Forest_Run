# Forest Run — Technical Architecture

**Language:** Kotlin  
**Rendering:** `SurfaceView` — custom 2D game loop  
**Build:** Gradle, `com.anurag9000.forestrun`, Min SDK 24, Target SDK 34, Java 17  
**Persistence:** local `SharedPreferences` only, no cloud sync in v1.0

---

## 1. Project Structure

```
app/src/main/java/com/anurag9000/forestrun/
├── MainActivity.kt
├── engine/          — game loop, state, input, systems, audio, haptics
├── entities/        — Player, Entity base, flora/, trees/, birds/, animals/
├── systems/         — particles, ghost, seed orbs
├── ui/              — HUD, GameOverScreen, GardenScreen, MainMenuScreen, dialogs
└── utils/           — math, asset helpers
```

Assets live in `app/src/main/assets/sprites/`. Audio in `res/raw/`.

### AndroidManifest Requirements

- `screenOrientation="sensorLandscape"`
- `configChanges="orientation|screenSize|keyboardHidden"`
- Immersive full-screen (no action bar, no status bar)
- `keepScreenOn`
- `VIBRATE` permission

---

## 2. Game Loop — GameThread & GameView

`GameThread` drives a 60 FPS loop on a dedicated background thread:

1. Compute `deltaTime` (nanoseconds → seconds, capped at 0.05s to prevent physics explosion on resume)
2. Call `GameView.update(deltaTime)`
3. Lock canvas → `GameView.draw(canvas)` → unlock/post
4. Sleep for remaining 16.67ms budget

`GameView` extends `SurfaceView` and implements `SurfaceHolder.Callback`. All game systems only initialize after `surfaceCreated()` fires, when screen dimensions are known.

**Performance rules (enforced in code):**
- No bitmap decoding inside the draw loop
- No `Paint` allocation inside the draw loop — all Paint objects created once at construction
- Entities are object-pooled via `EntityManager.recyclePool` (max 3 instances per type)
- Particle count is capped per emitter

---

## 3. App State Machine

```
MENU ──tap──► PLAYING ──hit──► DYING ──1.2s──► GAME_OVER ──tap──► RESTARTING ──fade──► GARDEN
  ▲                                                                                         │
  └─────────────────────────────────────────────────────────────────────────────────────────┘

MENU ──garden tap──► GARDEN ──run tap──► PLAYING
```

**`AppGameState`:** `MENU`, `GARDEN`, `PLAYING`  
**`RunState`:** `PLAYING`, `DYING`, `GAME_OVER`, `RESTARTING`

Both states live in `GameView` as `@Volatile` fields. The `DYING` state lasts 1.2s (rest animation plays), `RESTARTING` performs a 0.5s fade-to-black before routing back to Garden.

---

## 4. Player State Machine

**States:** `RUNNING`, `JUMP_START`, `JUMPING`, `APEX`, `FALLING`, `LANDING`, `DUCKING`, `BLOOM`, `STUMBLE`, `REST`

### Physics Constants

| Constant | Value |
|---|---|
| `GRAVITY` | 3000 px/s² |
| `MIN_JUMP_FORCE` | −900 px/s (quick tap) |
| `MAX_JUMP_FORCE` | −1800 px/s (full hold) |
| `MAX_HOLD_DURATION_S` | 0.6s |
| `APEX_GRAVITY_FACTOR` | 0.60× |
| `APEX_GRAVITY_DURATION_S` | 0.20s |
| `JUMP_START_DURATION_S` | 0.05s (squash hold before launch) |
| `LANDING_DURATION_S` | 0.07s |
| `DUCK_HEIGHT_FACTOR` | 0.55× |

### Jump Mechanics

`JUMP_START` holds for 0.05s (squash), then applies `MAX_JUMP_FORCE` unconditionally. Variable height is achieved via the "Mario abort": if `onJumpReleased()` fires while `velocityY < 0` during `JUMPING`, upward velocity is halved.

`APEX` is detected when `velocityY ≥ 0` during `JUMPING`. Reduced gravity applies for 0.20s for floaty feel.

### Squash & Stretch

| State | scaleX | scaleY |
|---|---|---|
| `JUMP_START` | 1.25 | 0.80 |
| `JUMPING` | 0.85 | 1.20 |
| `FALLING` | 0.90 | 1.15 |
| `LANDING` | 1.30 | 0.75 |
| `DUCKING` | 1.15 | 0.55 |

Run animation FPS is velocity-synced: mapped from base speed (24 fps) to max speed (32 fps).

### Hitbox

All four sides inset by `HITBOX_INSET = 10f` from the scaled sprite rect. Keeps collision forgiving while remaining physically honest.

---

## 5. Input Handler

`InputHandler` implements `View.OnTouchListener` and translates raw `MotionEvent`s into game callbacks:

- `onJumpPressed` — finger down (starts charge)
- `onJumpHeld(holdSec)` — called every game frame via `tick(deltaTime)` while held, after 0.05s threshold
- `onJumpReleased(holdSec)` — finger up (commits jump or Mario abort)
- `onDuckPressed` — swipe-down detected (80px threshold)
- `onDuckReleased` — finger lifted after duck

Multi-touch: only the primary pointer drives game input. Subsequent fingers are ignored.

`inputHandler.tick(deltaTime)` is called once per frame from `GameView.update()` to accumulate `holdDuration`.

---

## 6. GameStateManager

Single source of truth for all mutable per-run state. `GameView` owns one instance and passes it to every subsystem.

**Manages:**
- `scrollSpeed` — current px/s (ramped from `BASE_SCROLL_SPEED` to `MAX_SCROLL_SPEED` over distance)
- `distanceMetres` — total metres run
- `runTimeSeconds` — elapsed run time
- `score` / `exactScore` — fractional accumulation to prevent rounding loss
- `highScore` — loaded from `SaveManager`, updated when beaten
- `bloomMeter` / `isBloomActive` / `bloomTimer` — Bloom progression
- `seedsThisRun` / `lifetimeSeeds` — seed counts
- `speedDebuffMultiplier` / `speedDebuffTimer` — Hedgehog debuff
- `scoreMultiplier` — boosted by kindness/mercy rewards
- `openingInputState` — tracks first-30s input discovery for guidance chips

**Speed & score sync (Bug 2, fixed):** Both `distanceMetres` and the score `distanceDelta` use the same captured `speedThisFrame` value before the speed ramp is recalculated, ensuring score and distance are always frame-coherent.

### Bloom Lifecycle

`collectSeed()` increments `bloomMeter`. At 8 seeds: `bloomMeter = 0`, `isBloomActive = true`. After 6 seconds: `isBloomActive = false`. Player's `activateBloom()` / `deactivateBloom()` are triggered by `GameView` detecting the state change.

---

## 7. Entity System

### Base Class: `Entity`

All entities share `x`, `y`, `hitbox`, `isActive`, `hasBeenPassed`. `update(deltaTime, scrollSpeed)` scrolls left. `draw(canvas)` renders. `onCollision(player, gameState)` returns `CollisionResult`.

### EntityManager

Manages the full entity lifecycle:
- `activeEntities: MutableList<Entity>` — all current on-screen entities
- `recyclePool: Map<EntityType, MutableList<Entity>>` — up to 3 instances per type
- Spawn timer driven by `DifficultyScaler.getSpawnInterval(distance)` → `ReadabilityProfile`
- Pass detection: `entity.hitbox.right < playerPassX` (25% of screen width)
- On pass: `performUniqueAction()`, `recordCleanPass()`, authored dialogue bubble, optional Bloom conversion
- Collision: `checkCollisions()` skipped entirely during `isBloomActive`

**Bloom conversion on pass:** entity is despawned (`isActive = false`), `recordBloomConversion()` fires (awards 140 pts + 1 seed), world burst particles emit.

### SeedOrbManager

Separate manager for floating seed orbs. Spawns above entities on pass (60% base rate, higher for Lily/Dog/Wolf). Scrolls left, detects player overlap for collection.

---

## 8. BiomeManager

5 biomes cycle every 500 metres:

| Distance | Biome |
|---|---|
| 0–500m | `MEADOW` |
| 500–1000m | `ORCHARD` |
| 1000–1500m | `ANCIENT_GROVE` |
| 1500–2000m | `DUSK_CANYON` |
| 2000m+ | `NIGHT_FOREST` |

Each biome defines: sky gradient (top + bottom), ground colour, foliage colour, ambient darkness alpha, entity spawn pool, and a cached authored scene composition for the parallax background. Transitions apply smooth colour blending. Night Forest adds firefly density and enables `Owl` spawns.

---

## 9. Collision Classification

`Entity.onCollision()` returns one of:

- `NONE` — no overlap
- `MERCY_MISS` — hitboxes nearly overlapping but within `MERCY_WINDOW_FRAC` (18%) margin
- `STUMBLE` — non-lethal hit (entity-specific threshold — e.g. Hyacinth brush, some animal secondary states)
- `HIT` — lethal collision → triggers `DYING`

`MERCY_MISS` awards mercy heart + kindness chain increment + score bonus.  
`STUMBLE` triggers brief player stumble animation + brief invincibility window + entity despawn.  
`HIT` saves ghost if new best distance, records run summary, triggers rest scene.

---

## 10. Parallax Background System

4 scroll layers at different speed ratios. `ParallaxBackground` additionally renders:

- Biome sky gradient (top to bottom)
- Canopy shade band
- Layered mist bands (density scaled by distance/biome)
- Drifting leaves (primary + backfill)
- Drifting petals (primary + trail)
- Fireflies and glow motes (dense in Night Forest)
- Horizon glow
- Gust-strength-driven world sway (visible wind ribbons)
- Subtle world-scale response to scroll speed and Bloom state
- Cached authored biome scene compositions (Meadow, Orchard, Ancient Grove, Dusk Canyon, Night Forest)
- Shared cinematic overlay finish (via `CinematicPolish`)

Menu, rest, and Garden use sanctuary-derived mist, lantern glow, ground-light bloom, and arrival badge presentation via `GardenSanctuaryPlanner`.

---

## 11. Sprite System

`SpriteSheet` plays animations from a single horizontally-packed bitmap strip. Supports:
- Variable `framesPerSec` (modified at runtime for velocity sync)
- Looping or one-shot playback
- `startFrame` offset for shared atlas bitmaps
- `copy()` so multiple entities share one bitmap without duplication

All entity animation instances are created once at `Player`/entity construction. No bitmap decoding inside the game loop.

---

## 12. Ghost System

`GhostRecorder` records `GhostFrame` (time, x, y, state, scaleX, scaleY) every frame. On new best distance, the snapshot is serialized and saved via `SaveManager`.

`GhostPlayer` replays the saved run with a context-aware visibility policy:
- Delays reveal at run start (avoids immediate visual confusion)
- Suppresses after player collisions (ghost doesn't crowd recovery)
- Fades by overlap when live player and ghost occupancy are close
- Suppresses during dense hazard windows
- Re-enters smoothly after suppression

Ghost has no gameplay hitbox. Rendered in white-blue at 40% opacity.

---

## 13. Camera System

`CameraSystem` (singleton) implements trauma-based screen shake:
- `addTrauma(amount)` accumulates shake intensity
- `update(deltaTime)` decays trauma each frame
- `applyTo(canvas, block)` translates/rotates canvas before drawing the gameplay layer
- HUD and screen overlays are drawn **after** the camera scope — they never shake

Shake triggers: `shakeHit()`, `shakeBloom()`, `shakeBloomChain(tier)`, `shakeMercyMiss()`, `addTrauma(0.3f)` on milestones.

**Bug 1 (fixed):** `CameraSystem.update()` was previously called twice per frame during the `DYING` state (once unconditionally at the top of `GameView.update()`, once inside the `DYING` branch). The duplicate call was removed — shake now decays at the designed rate.

---

## 14. Particle System

`ParticleManager` manages both one-shot emit pools and continuous emitters. All particle types are defined as `FxPreset` enum entries and built via `FxPreset.build(x, y)`.

**Presets include:** `JUMP_DUST`, `LAND_THUD`, `SLIDE_GRASS`, `DEATH_EXPLOSION`, `BLOOM_ACTIVATE`, `BLOOM_AURA`, `BLOOM_TRAIL`, `BLOOM_CONVERT`, `BLOOM_WORLD_BURST`, `MERCY_STARS`, `HIT_BURST`, `SEED_COLLECT`, `PETAL_DRIFT`, `POLLEN_BURST`.

Continuous emitters (`addContinuous`) are attached to the player during Bloom and removed on `deactivateBloom()` or `reset()`.

---

## 15. HUD

`HUD` draws: score (pixel font), distance, seed count, Bloom meter, mercy hearts.

Bloom meter has three visual states driven by `BloomPresentation.hudPresentation()`:
- Near-ready: distinct charge indicator
- Active: power-state framing with remaining time
- Afterglow: settling fade after the window

Opening guidance chips for the first 30 seconds are driven by `OpeningReadabilityGuide` and `OpeningInputState`.

---

## 16. Flavor Text & Dialogue System

`FlavorTextManager` spawns short floating world-space text labels that drift upward and fade.

`DialogueBubbleManager` spawns anchored speech bubbles above entities or the player. Supports deterministic trigger-keyed short-line variation so ordinary clean-pass moments have authored breadth instead of one fixed label.

All in-run authored text (collision, mercy-miss, clean-pass, milestone, progress) is centralized in `RunFlavorPresentation`. Entity-family flavor (flora, trees, birds, animals) is centralized in `FloraEncounterFlavor`, `TreeEncounterFlavor`, `BirdEncounterFlavor`, `AnimalEncounterFlavor`.

---

## 17. Mercy & Pacifist Systems

`MercySystem` tracks: `mercyHearts`, `nearMisses`, `kindnessChain` per run.  
`PacifistTracker` tracks: `cleanPassesThisRun`, `sparedThisRun`, `hitsThisRun`, per-biome state, biome friendship.

Both reset on `resetRun()`. `PacifistTracker.currentRouteTier()` computes the canonical tier from live run stats. Pending rewards (`PacifistReward`) are queued and consumed once per frame via `consumeReward()`.

---

## 18. Persistence — SaveManager

All persistence is local `SharedPreferences` + JSON serialization:

| Key | Type |
|---|---|
| High score | Int |
| Lifetime seeds | Int |
| Garden unlocks | BitSet / Int flags |
| Active costume | String key |
| Ghost run | JSON (`List<GhostFrame>`) |
| Best distance | Float |
| Last run summary | JSON (`RunSummary`) |
| Relationship stages | JSON per creature |
| Encounter memory | JSON (hit/spare/encounter counts) |
| Repeated-history snapshot | JSON (kindness streak, repeat-killer, biome friendship, route tier) |
| Fragment unlock marks | JSON |
| Return state | JSON |

`SaveManager.save()` is called immediately on death (`RunResetManager.triggerDeath()`) to survive process kill.

---

## 19. Audio System

`LeitmotifManager` manages music state transitions with explicit named motif signatures:

| State | Signature |
|---|---|
| `MENU` | Soft ambient, slow |
| `RUN_EARLY` | Minimal rhythm |
| `RUN_MID` | Flute layer in |
| `RUN_LATE` | Full layered, faster |
| `BLOOM` | Orchestral peak |
| `REST` | Music-box coda |

Bloom has distinct `ready` / `convert` / `sustain` / `fade` audio handling. Tempo scales with `scrollSpeed`.

`SfxManager` manages pooled one-shot SFX playback. All audio is fire-and-forget from game logic.

---

## 20. Haptic Manager

`HapticManager` wraps `VibrationEffect` with four patterns:
- `shortPulse()` — jump
- `mediumPulse()` — milestone, stumble
- `longPulse()` — death/HIT
- `doubleTap()` — MERCY_MISS close call
- `bloomSurge()` — Bloom activation

---

## 21. Difficulty Scaling

`DifficultyScaler` is stateless. `getSpawnInterval(distance)` delegates to `ReadabilityProfile.spawnInterval()` which is the central source of truth for pacing.

`getSpawnPool(distance, biomeManager)` returns the biome-specific pool when a `BiomeManager` is available. Falls back to distance-tiered pools (`POOL_EARLY`, `POOL_MID`, `POOL_LATE`) for test contexts.

`openingSpawnInterval()`, `openingSpawnPool()`, `shouldLockRandomOpeningSpawns()` apply the first-30-second guided layer via `OpeningReadabilityGuide`.

---

## 22. Debug Tools (Debug Build Only)

`EncounterDirector` manages deterministic scenario playback when `FLAG_DEBUGGABLE` is set.

**Scenarios mirror the device acceptance checklist exactly:**  
`OPENING_READABILITY`, `BLOOM_SHOWCASE`, `GHOST_READABILITY`, `REST_LOOP`, `CACTUS_READ`, `LILY_GLOW`, `HYACINTH_BRUSH`, `EUCALYPTUS_WHIP`, `ORCHID_WINDOW`, `WILLOW_CURTAIN`, `JACARANDA_PETALS`, `BAMBOO_GAP`, `CHERRY_GUST`, `DUCK_TEACH`, `TIT_WAVE`, `CHICKADEE_SWERVE`, `OWL_DIVE`, `EAGLE_MARK`, `CAT_KINDNESS`, `FOX_MIRROR`, `WOLF_CHARGE`, `HEDGEHOG_DEBUFF`, `DOG_HAZARD`, `DOG_BUDDY`

`DebugEncounterOverlay` renders a HUD panel in-game for scenario cycling. `DebugScenario` scripts inject timed input events for deterministic scenario replay.
