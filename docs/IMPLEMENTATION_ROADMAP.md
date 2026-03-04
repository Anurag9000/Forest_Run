# Forest_Run — Step-by-Step Implementation Roadmap

Each step below is **fully self-contained**. Complete it entirely — no `// TODO`, no placeholder, no stub — before moving to the next. When the last step is done, the game runs, feels great, and is ready to publish on Google Play.

---

## PHASE 0 — Android Studio Project Skeleton

> **Goal:** A running blank Android project with the correct structure, orientation, and full-screen setup. The app should launch, show a black landscape screen, and not crash.

### Step 0.1 — Create Project
- In Android Studio: New Project → Empty Activity.
- Package: `com.yourname.forest_run`
- Language: Kotlin, Min SDK: API 24, Target SDK: API 34.

### Step 0.2 — AndroidManifest.xml
Implement the final manifest exactly as specified in `ANDROID_SETUP.md`:
- `screenOrientation="sensorLandscape"`
- `configChanges="orientation|screenSize|keyboardHidden"`
- `android:immersive="true"` + `keepScreenOn="true"`
- `VIBRATE` permission.

### Step 0.3 — Gradle
- Add `gson:2.10.1` dependency.
- Set `targetSdk 34`, `minSdk 24`.
- Set Kotlin `jvmTarget = "17"`.

### Step 0.4 — Folder Structure
Create the exact package tree from `TECHNICAL_ARCHITECTURE.md`:
- `engine/`, `entities/flora/`, `entities/trees/`, `entities/birds/`, `entities/animals/`, `systems/`, `ui/`, `utils/`
- Create `app/src/main/assets/` folder.
- Create `app/src/main/res/raw/` folder for audio.

### Step 0.5 — Theme / Full-Screen
- `themes.xml`: `Theme.AppCompat.NoActionBar`, `windowFullscreen true`, status bar transparent.
- `MainActivity.kt`: Full-screen flags, immersive sticky, `setContentView(gameView)` (GameView will be a placeholder for now).

**Deliverable:** App launches in landscape, full-screen, no action bar, no crash.

---

## PHASE 1 — Core Game Loop Engine

> **Goal:** A `SurfaceView`-based game loop running at 60 FPS printing the current FPS to the canvas so you can verify it.

### Step 1.1 — GameThread.kt
- Dedicated `Thread` subclass.
- `isRunning: Boolean` flag.
- Fixed-timestep loop using `System.nanoTime()`.
- Calculates `deltaTime` in seconds (float).
- Sleeps the remaining frame budget after update + draw.
- `surfaceCreated`: starts thread. `surfaceDestroyed`: sets `isRunning = false` and calls `join()`.

### Step 1.2 — GameView.kt
- Extends `SurfaceView`, implements `SurfaceHolder.Callback`.
- Holds reference to `GameThread`.
- `update(deltaTime: Float)` — empty for now.
- `draw(canvas: Canvas)` — fills canvas black, draws current FPS in white text.
- `pause()` / `resume()` called from `MainActivity.onPause/onResume`.

### Step 1.3 — MathUtils.kt
- `lerp(a: Float, b: Float, t: Float): Float`
- `clamp(value: Float, min: Float, max: Float): Float`
- `sin` wrapper that accepts degrees as well as radians.

**Deliverable:** App shows a black screen with FPS counter. FPS is stable at 60 (or 120 on 120Hz devices — `deltaTime` compensates). No jank.

---

## PHASE 2 — Input System

> **Goal:** The game correctly detects tap, hold, and swipe-down from anywhere on the screen.

### Step 2.1 — InputHandler.kt
- Implements `View.OnTouchListener`.
- `ACTION_DOWN`: record `touchStartTime`, `touchStartY`, fire `onTouchStart()`.
- `ACTION_MOVE`: if `dy > swipeThreshold (100px)` → fire `onSwipeDown()`. Else → fire `onHold(duration)`.
- `ACTION_UP`: fire `onTouchRelease(totalDuration)`.
- Expose callbacks: `onJumpPressed`, `onJumpHeld`, `onJumpReleased`, `onDuckPressed`, `onDuckReleased`.
- Multi-touch: handle each pointer independently.

### Step 2.2 — Wire to GameView
- `GameView` creates `InputHandler` and passes callbacks to a `DEBUG_INPUT` log for now.
- Verify: short tap logs `"Jump: SHORT"`, long hold logs `"Jump: LONG 0.8s"`, swipe down logs `"DUCK"`.

**Deliverable:** Input is correctly classified. No missed taps. Swipe correctly differentiated from hold.

---

## PHASE 3 — Player Class & Physics

> **Goal:** A pixel rectangle representing the player that jumps, falls with gravity, ducks, and squash-and-stretches. No sprites yet.

### Step 3.1 — PlayerState Enum
```kotlin
enum class PlayerState { RUNNING, JUMP_START, JUMPING, APEX, FALLING, LANDING, DUCKING, BLOOM, REST }
```

### Step 3.2 — Player.kt — Physics
- `x`, `y`, `velocityY: Float`
- `GRAVITY = 2800f` (pixels/s²)
- `GROUND_Y` = screen height - player height - floor offset.
- `isGrounded: Boolean`
- `onJumpPressed()`: record hold start.
- `onJumpReleased(holdDuration)`: apply `velocityY = lerp(MIN_JUMP, MAX_JUMP, clamp(holdDuration / MAX_HOLD, 0,1))`, state → `JUMP_START`.
- `JUMP_START` lasts 2 frames, then → `JUMPING`.
- At apex (velocityY crosses 0): state → `APEX` for 1 frame, then → `FALLING`.
- Gravity reduced 40% for 0.2s at apex (floaty feel).
- On ground contact: `velocityY = 0`, state → `LANDING` for 3 frames, then → `RUNNING`.
- `onDuckPressed()`: state → `DUCKING`, `hitbox` compresses vertically.
- `onDuckReleased()`: return to `RUNNING` if grounded.

### Step 3.3 — Player.kt — Squash & Stretch
- `getScaleY()` / `getScaleX()` — return scale per state (see `VISUAL_FX_SPEC.md` tables).
- Applied via `canvas.scale(scaleX, scaleY, centerX, bottomY)` in draw.

### Step 3.4 — Player.kt — Draw (Debug Rectangle)
- Draw a coloured filled `Rect` scaled by squash/stretch values.
- Different colour per state (blue = running, yellow = jumping, red = ducking, green = landing).

### Step 3.5 — Hitbox
- `hitbox: RectF` updated every frame from `x`, `y`, `scaleX`, `scaleY`.
- Hitbox is slightly smaller than visual rect (10px inset all sides).

**Deliverable:** Rectangle that jumps with correct variable height, falls with gravity, has a floaty apex, squashes on takeoff/landing, compresses when ducking. Feels snappy and satisfying with no sprites needed yet.

---

## PHASE 4 — Parallax Background System

> **Goal:** 4 scrolling background layers. Each a different colour gradient for now. Loops seamlessly.

### Step 4.1 — ParallaxLayer.kt
- Holds: `bitmap: Bitmap`, `scrollSpeed: Float` (fraction of game speed), `x: Float`.
- `update(deltaTime, gameScrollSpeed)`: `x -= scrollSpeed * gameScrollSpeed * deltaTime`. When `x <= -bitmap.width`, `x += bitmap.width`.
- `draw(canvas)`: draw bitmap at `x` and at `x + bitmap.width` (ensures seamless loop).

### Step 4.2 — ParallaxBackground.kt
- Holds 4 `ParallaxLayer` instances.
- Layer speeds: 10%, 30%, 100%, 150% of `gameScrollSpeed`.
- For now, layers are solid colour rectangles (sky blue, dark green, medium green, light green strip).
- `update(deltaTime, scrollSpeed)` and `draw(canvas)` delegating to each layer.

### Step 4.3 — Floor Line
- Draw a floor line at `GROUND_Y` in brown — visible reference for the player to land on.

**Deliverable:** 4 coloured bands scroll at different speeds. Seamless looping (no visible seam). Floor line visible.

---

## PHASE 5 — HUD (Score / Seeds / Bloom Meter)

> **Goal:** A fully functional HUD drawn over the game. Score ticks up in real-time. Seed counter works. Bloom Meter fills.

### Step 5.1 — GameStateManager.kt (Partial)
- Tracks: `distanceTravelled`, `score`, `seedsCollected`, `bloomMeter` (0–10), `scrollSpeed`.
- `scrollSpeed` starts at `BASE_SPEED = 600f` px/s.
- `update(deltaTime)`: increments distance and score.

### Step 5.2 — HUD.kt
- **Score:** Top-right, white pixel font, format `"1,842 m"`.
- **Seed Counter:** Top-left icon + number. `"🌱 × 7"`.
- **Bloom Meter:** Left side, vertical bar. 10 segments. Each segment fills yellow-green when a seed is collected. Full bar pulses.
- **High Score Ghost Line:** Small text showing previous best below current score if current run is ahead.
- All drawn using `Canvas.drawText()` with a loaded `Typeface` (use `Typeface.createFromAsset` to load a pixel font — PressStart2P).

### Step 5.3 — Load PressStart2P Font
- Download `PressStart2P-Regular.ttf`, place in `assets/fonts/`.
- Load in `HUD.kt` with `Typeface.createFromAsset(context.assets, "fonts/PressStart2P-Regular.ttf")`.
- All HUD text uses this typeface.

**Deliverable:** Score ticks up live. Seed counter updates. Bloom Meter is visible and fills. Distance displayed in friendly format. Full-screen, no overlap with gameplay area.

---

## PHASE 6 — Sprite System & Player Sprites

> **Goal:** Player is drawn with real pixel-art sprites from a sprite sheet. All animation states transition correctly.

### Step 6.1 — SpriteSheetHelper.kt
```kotlin
object SpriteSheetHelper {
    fun split(bitmap: Bitmap, cols: Int, rows: Int = 1): Array<Bitmap>
    fun splitRow(bitmap: Bitmap, frames: Int): Array<Bitmap>
}
```

### Step 6.2 — AnimationState.kt
```kotlin
data class AnimationState(
    val frames: Array<Bitmap>,
    val fps: Float,
    val looping: Boolean
)
```
- `currentFrame(elapsed: Float): Bitmap` calculated from `floor(elapsed * fps) % frames.size`.

### Step 6.3 — Player Sprite Assets
Create or source the following sprite sheets (PNG, transparent background):
- `player_run.png` — 48 frames horizontal strip. 24fps loop.
- `player_jump.png` — 12 frames. one-shot.
- `player_duck.png` — 8 frames. hold last frame.
- `player_land.png` — 4 frames. one-shot, returns to run.
- `player_rest.png` — 24 frames. one-shot, hold last.
- `player_bloom.png` — 16 frames. loop during bloom.

Frame size recommended: 64×96px per frame (for a ~96px tall character on a 1080p screen, scale up 2×).

### Step 6.4 — Face Overlay System
- Separate sprite overlay: `player_face_default.png`, `player_face_jump.png`, `player_face_scared.png`, etc.
- 9 face states per `UNDERTALE_VIBE.md` Section 1.
- Drawn on top of body sprite at correct offset.
- `FaceManager.kt`: tracks current face state, transitions it based on `PlayerState` + nearby entity events.

### Step 6.5 — Wire Player Drawing
- Player `draw(canvas)` now draws the correct `AnimationState` frame.
- Apply squash/stretch via `canvas.scale()`.
- Draw face overlay on top.

**Deliverable:** Animated pixel-art woman runs, jumps, ducks, and lands. Face changes expression contextually. All transitions are smooth.

---

## PHASE 7 — Base Entity System & SwayComponent

> **Goal:** The foundation for all 19 entities. A reusable base class and wind sway system.

### Step 7.1 — Entity.kt (Base Class)
```kotlin
abstract class Entity(val context: Context) {
    var x: Float; var y: Float
    var velocityX: Float; var velocityY: Float
    var hitbox: RectF
    var isActive: Boolean = true
    var swayComponent: SwayComponent? = null
    abstract fun update(deltaTime: Float, scrollSpeed: Float)
    abstract fun draw(canvas: Canvas)
    abstract fun performUniqueAction(player: Player, gameState: GameStateManager)
    abstract fun onCollision(player: Player, gameState: GameStateManager): CollisionResult
}
```

### Step 7.2 — SwayComponent.kt
- Implements the sine-wave formula: `xOffset = sin(time * speed) * intensity * globalWindMultiplier`
- Stores `speed`, `intensity`, `time` (accumulated deltaTime).
- `getOffset(deltaTime, windMultiplier): Float`

### Step 7.3 — CollisionResult Enum
```kotlin
enum class CollisionResult { NONE, HIT, MERCY_MISS }
```
- `MERCY_MISS` threshold = 12px around any side of hitbox.

### Step 7.4 — EntityType Enum
All 19 entity types listed.

### Step 7.5 — EntityFactory.kt
- Static `create(type: EntityType, startX: Float, screenHeight: Float): Entity`
- Instantiates correct subclass.

**Deliverable:** Base entity framework is solid. Factory creates any entity type. Sway produces visible oscillation when tested with a debug rectangle.

---

## PHASE 8 — All 5 Ground Flora Entities

> **Goal:** All 5 plants fully implemented with correct hitboxes, behaviours, sway, particles (placeholder), and collision results. Spawnable by the EntityManager.

### Step 8.1 — Cactus.kt
- Static. No sway. Pixel-art sprite (`flora_cactus.png`). Tight hitbox.
- `onCollision` → `HIT`.
- `performUniqueAction` → no-op.
- Déjà Vu integration: if `PersistentMemoryManager.getHits(CACTUS) >= 5`, render skull badge overlay.

### Step 8.2 — LilyOfValley.kt
- 2–4 flower cluster. Tiny hitbox (smaller than sprite).
- `SwayComponent(speed=1.5f, intensity=5f)`.
- If `dayPhase == NIGHT`: activate glow particle emitter from `ParticleManager`.
- `performUniqueAction`: double seed spawn rate above this entity.
- `onCollision` → `HIT`.

### Step 8.3 — Hyacinth.kt
- Always spawns as group of 3 in one logical `HyacinthCluster` wrapper.
- Each spike: `SwayComponent(speed=1.0f, intensity=7f)`.
- Brush collision (partial overlap, not full): apply `speedDebuff(0.5f, 3000)` → return `MERCY_MISS` with debuff.
- Full overlap → `HIT`.
- `performUniqueAction` → spawn group.

### Step 8.4 — Eucalyptus.kt
- Slanted sprite. Trapezoid hitbox (wider at top).
- `SwayComponent(speed=2.5f, intensity=6f)` — fast whip.
- Emit green leaf particles at sway peak.
- `onCollision` → `HIT`.

### Step 8.5 — VanillaOrchid.kt
- Two colliders: low vine body + overhead branch.
- Vine sways independently from branch.
- If player hits branch → `HIT`. If player hits vine → `HIT`. Window in between is safe.
- `performUniqueAction` → emit white sparkle particles on pass.

**Deliverable:** All 5 plants spawn in from the right, scroll left, have correct hitboxes, sway correctly, and trigger the right `CollisionResult`. Tested individually by temporarily forcing each into the spawn pool.

---

## PHASE 9 — All 4 Tree Entities

> **Goal:** All 4 trees fully implemented as overhead/space-constraining hazards.

### Step 9.1 — WeepingWillow.kt
- Full-height sprite. Leaf curtain hitbox = horizontal band, mid-height, 60% screen width.
- `SwayComponent(speed=0.5f, intensity=20f)` — slow wide curtain.
- When player enters shadow zone: apply subtle canvas darkening overlay.
- Leaf strand particles detach at sway peak.
- `onCollision` (body/curtain) → `HIT`.

### Step 9.2 — Jacaranda.kt
- Upper branch hitbox (top 30% of screen).
- `SwayComponent(speed=0.8f, intensity=15f)`.
- Constantly emits purple petal particles. Rate doubles at high `WindSpeed`.
- `performUniqueAction` → spawn full-screen petal curtain FX.

### Step 9.3 — Bamboo.kt
- 5 vertical stalks, full-screen height. Gap randomised per spawn.
- `SwayComponent(speed=3.0f, intensity=4f)` — stiff quick jitter.
- Hitboxes: 5 individual thin rectangles with one gap.
- If player passes through gap: `NONE`. If hits stalk: `HIT`.
- Near-gap pass (within 6px): `MERCY_MISS`.

### Step 9.4 — CherryBlossom.kt
- Mid-height branches, duck-under hitbox.
- Constantly emits pink petal drift (Petal Drift particle system).
- `performUniqueAction`: temporarily increase `globalWindSpeed` for 3 seconds, spike petal emission rate.

**Deliverable:** All 4 trees spawn and scroll. Weeping Willow requires duck. Bamboo requires gap-finding. Petals fall from Cherry Blossom and Jacaranda.

---

## PHASE 10 — All 5 Bird Entities

> **Goal:** All 5 birds flying with correct altitude AI and unique movement. Player must duck or jump accordingly.

### Step 10.1 — Duck.kt
- Spawns at waist/head height. Constant horizontal movement.
- Hitbox at exact head height.
- 0.5s before entering screen: play quack SFX.
- `onCollision` → `HIT`. Player must duck.

### Step 10.2 — Tit.kt
- Spawns in group of 3–5 as `TitGroup` wrapper.
- Group Y: `baseLine + sin(time * frequency) * amplitude`.
- All birds share same wave. Player jumps through the trough.
- `onCollision` any bird → `HIT`.

### Step 10.3 — Chickadee.kt
- Group of 2–4. Each changes altitude independently every `1.0 ± 0.3s`.
- Altitude snap with brief flutter animation.
- `onCollision` → `HIT`.

### Step 10.4 — Owl.kt
- Night only (check `dayPhase`). Spawns perched on branch, high-mid.
- `state = SLEEPING`.
- If `player.isJumping && owl.isOnScreen`: → `DIVING`, velocity diagonally down-forward.
- Amber glow eye particles always active.
- If player passes underneath without jumping and owl doesn't dive: no collision.

### Step 10.5 — Eagle.kt
- Spawns off top of screen.
- Plays screech SFX.
- Captures `targetY = player.y` at spawn moment.
- Calculates `velocity = normalize(targetPos - spawnPos) * DIVE_SPEED`.
- Dives and despawns when exiting screen bottom.
- `onCollision` → `HIT`.

**Deliverable:** All 5 birds behave as designed. Duck tests reaction. Owl punishes jumpers. Eagle requires reading the screech cue. Tits require rhythm. Chickadees are chaos.

---

## PHASE 11 — All 5 Animal Entities

> **Goal:** All 5 animals fully implemented with their unique AI behaviours, dialogue bubbles, and Spare/Mercy hooks.

### Step 11.1 — Cat.kt
- Static. Small hitbox. Ambient tail-flick animation.
- When player clears it cleanly: `awardKindnessBonus()` → double Seeds + ×2 multiplier + heart particles.
- Dialogue: `"Meow?"` bubble on clear.
- At 5 mercy hearts: Cat waves and exits. Player receives Spare bonus.
- Costume: hat overlay after 10 encounters, flower crown after 25 (via `PersistentMemoryManager`).

### Step 11.2 — Wolf.kt
- Spawns at screen left, slow walk.
- At `x < screenWidth * 0.5f`: play howl SFX + animation, then `velocityX *= 2.0f`.
- Dirt/dust particles during charge.
- Dialogue: `"GRRR..."` at howl. `"..."` (silence) if player clears.
- At 8 mercy hearts: Wolf stops, turns, and trots offscreen. Spare.
- Costume: scar overlay after 20 encounters.

### Step 11.3 — Fox.kt
- Moderate approach. Detection zone = 3× body width ahead.
- If `player.isJumping && fox.isWithinRange(player) && !fox.hasJumped`: Fox jumps.
- Dialogue: `"Heh."` on mirror. `"Next time..."` if player beats it.
- At 5 mercy hearts: Fox sits and waves, does NOT jump.
- Costume: scarf after 15 encounters.

### Step 11.4 — Hedgehog.kt
- Fast, tiny, very low. Hard to see in petal blinding.
- On collision: NOT game over. Apply `speedDebuff(0.5f, 3000ms)`. State → curl animation.
- Dialogue: `"Eep!"` on near-miss.
- Costume: sunglasses after 10 encounters.

### Step 11.5 — Dog.kt
- Static. 1s before spawn: play bark SFX cue.
- Every `barkInterval`: spawn `BarkProjectile` — a horizontal shockwave with its own `RectF` hitbox.
- **Running Buddy Mode (20% chance):** Dog runs beside player harmlessly for 3–5s. During buddy mode: periodic musical bark SFX pulses. Dog winks (2-frame anim), dashes ahead, despawns. Dialogue: `"BORF!"`, `"Hi!!"`, `"See ya!"`.
- Costume: bandana after 10 encounters, bowtie after 25.

**Deliverable:** All 5 animals pass manual testing. Fox mirror jump works. Wolf charge is telegraphed. Hedgehog doesn't kill but debuffs. Dog bark projectile is dodgeable. Running Buddy mode is charming. All dialogue bubbles fire.

---

## PHASE 12 — EntityManager & Spawner

> **Goal:** Entities spawn automatically at the right rate, scroll from right to left, and despawn when off-screen. Object pooling to avoid GC.

### Step 12.1 — EntityManager.kt
- Maintains `activeEntities: MutableList<Entity>`.
- `spawnTimer` accumulates per frame.
- When `spawnTimer >= spawnInterval`: pick a random entity from `currentSpawnPool`, spawn it at `screenWidth + 100f`.
- `update()`: update all entities, remove any where `x < -maxEntityWidth`.
- Object pooling: maintain a recycled pool per entity type to avoid allocation mid-run.

### Step 12.2 — DifficultyScaler.kt
```kotlin
object DifficultyScaler {
    fun getScrollSpeed(distance: Float): Float  // BASE + increment per 250m
    fun getSpawnInterval(distance: Float): Float // Decreases down to MIN floor
}
```

### Step 12.3 — Collision Loop
- In `GameStateManager.update()`: iterate `entityManager.activeEntities`, call `entity.onCollision(player)`.
- On `HIT`: transition to `REST` state, trigger screen shake, haptic long pulse.
- On `MERCY_MISS`: award mercy heart, spawn pink heart particle, fire flavour text.
- On `NONE`: check for Cat kindness zone.

### Step 12.4 — PersistentMemory Recording
- On each `MERCY_MISS` or `HIT` or entity pass: call `PersistentMemoryManager.recordEncounter(type)`.
- On `HIT`: call `recordHit(type)`.
- On Spare: call `recordSpared(type)`.

**Deliverable:** Entities spawn continuously. Difficulty increases over time (verifiable by watching speed + spawn density increase). Collisions correctly classify HIT vs MERCY_MISS and trigger correct consequences.

---

## PHASE 13 — Biome System

> **Goal:** Every 500m the biome transitions — background, spawn pool, ambient particles, and music layer all change.

### Step 13.1 — Biome Enum + BiomeManager.kt
- 6 biomes as defined in `ENTITY_DATABASE.md Section 5`.
- `getBiomeAt(distance)`: `(distance / 500f).toInt() % 6` maps to biome.
- `getPoolForBiome(biome): List<EntityType>`.

### Step 13.2 — BiomeTransition
- On every 500m threshold crossing: fire `onBiomeTransition(newBiome)`.
- Transition effects:
  - Background Layer 2 bitmap swaps (crossfade over 3 seconds using alpha lerp).
  - `EntityManager.currentSpawnPool` updated.
  - `ParticleManager` ambient emitters updated (e.g., petal drift on in Spring Orchard, off in Ancient Grove).
  - Music crossfade (next phase).
  - `WindSpeed` jumps to biome default level.

### Step 13.3 — Day/Night Phase
- Tracked by distance range per `VISUAL_FX_SPEC.md Section 5`.
- `ColorFilter` matrix lerped over 5 seconds between phases.
- Applied to entire canvas via `Paint.colorFilter`.
- Night phase: Owl spawning enabled. Lily of Valley glow enabled. Firefly particles enabled.

**Deliverable:** Run for 500m and visually see the world change. Background shifts, new entity types appear, ambient particles change, lighting shifts colour. Confirmed with at least 2 full biome transitions in one run.

---

## PHASE 14 — Full Particle System

> **Goal:** All 9 particle systems from `VISUAL_FX_SPEC.md` implemented and triggering correctly.

### Step 14.1 — Particle.kt & ParticleEmitter.kt
```kotlin
data class Particle(var x, y, vx, vy, life, maxLife, size, colour, alpha)
class ParticleEmitter(val config: EmitterConfig) {
    fun emit(count: Int, originX, originY)
    fun update(deltaTime)
    fun draw(canvas, paint)
}
```

### Step 14.2 — ParticleManager.kt
- Singleton managing all active `ParticleEmitter` instances.
- Cap: 200 active particles max (oldest expire first over cap).
- Named emitters: `PETAL_DRIFT_PINK`, `PETAL_DRIFT_PURPLE`, `DUST_KICK`, `POLLEN_TRAIL`, `SEED_ORB`, `BLOOM_TRAIL`, `FIREFLY`, `LILY_GLOW`, `KINDNESS_BURST`.
- `update(deltaTime)`, `draw(canvas)`.

### Step 14.3 — Wire All Particle Triggers
| Particle System | Trigger |
|---|---|
| Petal Drift (Pink) | Spring Orchard + Cherry Blossom biomes active |
| Petal Drift (Purple) | Violet Path + Jacaranda active |
| Dust Kick | `player.isGrounded` changes `false → true` |
| Pollen Trail | Continuous while `RUNNING` |
| Seed Orb burst | Seed collected |
| Bloom Trail | `GameState == BLOOM_STATE` |
| Firefly | `dayPhase == TWILIGHT or NIGHT` |
| Lily Glow | Night + LilyOfValley on screen |
| Kindness burst | Cat Kindness Bonus triggers |

**Deliverable:** All 9 particle systems fire at the correct game moments. No performance drop (stays at 60 FPS with particles active). Petal drift is constant and beautiful.

---

## PHASE 15 — Camera Effects

> **Goal:** Screen shake on collision. Camera zoom-out at high speed.

### Step 15.1 — CameraManager.kt
- `shakeIntensity: Float`, `shakeDuration: Float`, `shakeElapsed: Float`.
- `triggerShake(intensity, duration)`.
- `update(deltaTime)`: decrement elapsed, dampen intensity.
- `getOffset(): Pair<Float, Float>` — random offset clamped to current intensity.
- Applied in `GameView.draw()` via `canvas.translate(offset.x, offset.y)` before all drawing.

### Step 15.2 — Speed-Based Zoom
- `zoomLevel = max(0.8f, 1.0f - (scrollSpeed - BASE_SPEED) * 0.0003f)`
- Applied via `canvas.scale(zoomLevel, zoomLevel, screenCenterX, screenCenterY)` on Layer 1 only.

### Step 15.3 — Wire Triggers
- `triggerShake(8f, 0.5f)` on `HIT`.
- `triggerShake(4f, 0.3f)` on 1000-point milestone.

**Deliverable:** Collision causes visible satisfying shake. World visually "grows" at higher speeds.

---

## PHASE 16 — Flavour Text System

> **Goal:** Floating text pops appear on game events, and the REST screen shows contextual Determination quotes.

### Step 16.1 — FlavorTextManager.kt
Full implementation as specified in `UNDERTALE_VIBE.md`:
- `FlavorText` data class: `text, x, y, alpha, lifetime, elapsed, colour`.
- `spawn(text, x, y, colour)`.
- `update(deltaTime)`: float upward, fade out.
- `draw(canvas, paint)`.
- `getRestQuote(biome, lastKiller): String` — priority: killer → biome → universal quote pool.

### Step 16.2 — Wire All Triggers
| Game Event | Flavour Text |
|---|---|
| Close Call / Mercy Miss | `"Boop!"`, `"Zoom!"`, `"Phew~"` (random) |
| Seed collected | `"+"` |
| Cat cleared | `"Meow?"` from cat |
| Wolf charges | `"GRRR..."` from wolf |
| Dog bark fires | `"BORF!"` |
| Eagle screech | `"!"` |
| Bloom activates | `"BLOOM!"` |
| Pacifist biome | `"FRIEND!"` (large, centred) |
| Repeat killer | `"Again?"` (from entity) |
| Fox beats player | `"Heh."` |

### Step 16.3 — Dialogue Bubble UI
- `DialogueBubble.kt`: draws a small pixel-art speech bubble sprite at entity position + offset.
- Entity speech drawn in PressStart2P font inside bubble.
- Lifetime: 1.5s, fades out.

**Deliverable:** Every listed game event produces correct floating text. REST screen shows a different, contextual quote every run. Entity dialogue bubbles render above the correct entity.

---

## PHASE 17 — Mercy & Pacifist Systems

> **Goal:** Close Calls are rewarded. Accumulating mercy hearts triggers Spare events. Full-biome pacifist gives Friendship Bonus.

### Step 17.1 — MercySystem.kt
- `mercyHeartCount: Int` (per run, resets on REST).
- `onMercyMiss()`: increment, check thresholds, trigger spare events.
- Threshold effects from `UNDERTALE_VIBE.md Section 3.2`.
- `spareEntity(entity)`: trigger spare animation → entity waves → exits peacefully → awards bonus.

### Step 17.2 — PacifistTracker.kt
- Per-biome: track if **any** animal interaction closer than 1.5× mercy threshold occurred.
- On biome transition: if biome was clean → `triggerFriendshipBonus()`.
- Friendship Bonus: screen heart flash, `"FRIEND!"` large text, +300 score, reduced next biome spawn rate.

**Deliverable:** Mercy hearts visibly accumulate (shown as small hearts in HUD). At 5 hearts, next Fox encounter is Spared — observable and charming. Completing a biome cleanly gives Friendship Bonus.

---

## PHASE 18 — Persistent Memory System

> **Goal:** The game remembers entities across sessions. Costumes evolve. Déjà Vu fires.

### Step 18.1 — PersistentMemoryManager.kt
- `EntityMemory` data class: `type, totalEncounters, totalSpared, totalHitByPlayer`.
- Store as JSON in `SharedPreferences` via Gson.
- `recordEncounter`, `recordSpared`, `recordHit`.
- `getMemory(type): EntityMemory`.

### Step 18.2 — CostumeOverlay.kt
- `getCostume(type, encounters): Bitmap?` — returns correct overlay bitmap based on thresholds.
- All costume bitmaps loaded from `assets/costumes/`:
  - `cat_hat.png`, `cat_crown.png`, `dog_bandana.png`, `dog_bowtie.png`, `fox_scarf.png`, `wolf_scar.png`, `hedgehog_shades.png`, `owl_charm.png`, `cactus_skull.png`
- Each entity `draw()` method checks for costume and draws overlay at correct anchor.

### Step 18.3 — Déjà Vu System
- On run start: read `SaveManager.getLastKiller()`.
- In `EntityManager`: when spawning entity of same type as last killer, schedule `FlavorTextManager.scheduleDelayed("Again?", entity, 0.8f)`.
- `scheduleDelayed`: holds pending flavour texts keyed to entity position.

**Deliverable:** Kill two runs in a row with Cactus → third run, Cactus floats `"Again? Really?"`. Cat has a hat after 10 encounters. Dog has a bowtie after 25. Changes persist across app restarts.

---

## PHASE 19 — Ghost Run System

> **Goal:** A transparent ghost of the player's personal best run plays alongside the current run.

### Step 19.1 — GhostRecorder.kt
- Records `GhostFrame(x, y, animFrame, faceState, timestamp)` every frame during `PLAYING` state.
- On new personal best: serialize frame list to binary file in `filesDir`.

### Step 19.2 — GhostPlayer.kt
- Loads ghost file on run start (if exists).
- `update(deltaTime)`: advance playback position by deltaTime.
- `draw(canvas)`: draw player sprite at ghost position at 40% opacity, with white-blue `ColorFilter`.
- When ghost reaches its recorded endpoint: play wave animation (2-frame goodbye), fade out with sparkle particles.

### Step 19.3 — Ghost vs Current Distance UI
- Small indicator in HUD: `"Best: 1,842m"` with delta to ghost position `"+42m ahead"` or `"-15m behind"`.

**Deliverable:** On second run and beyond, ghost appears at run start. Falls behind or overtakes based on performance. Waves goodbye at its endpoint. Motivating and haunting at the same time.

---

## PHASE 20 — Leitmotif Audio System

> **Goal:** All music states play. The 5-note Forest Leitmotif (E-G-A-C-B) is present in every track. Tempo scales with speed.

### Step 20.1 — Audio Files
Produce or source these tracks (`.ogg` format, in `res/raw/`):
- `music_garden.ogg` — garden/menu. Slow solo piano playing the 5-note phrase.
- `music_run_layer1.ogg` — drum beat only. Hi-hat rhythm subtly taps the leitmotif pattern.
- `music_run_layer2.ogg` — add bass + flute playing the melody clearly.
- `music_run_layer3.ogg` — full orchestral. Faster tempo.
- `music_bloom.ogg` — triumphant orchestral swell of the leitmotif.
- `music_rest.ogg` — slow single-note music box, one note at a time.

All SFX listed in `ANDROID_SETUP.md Section 6` must be present.

### Step 20.2 — LeitmotifManager.kt (AudioManager)
- Uses `MediaPlayer` for looping music layers.
- `crossFadeTo(newTrack, durationMs)`: fade old track out, new track in simultaneously.
- `setPlaybackSpeed(speed)`: use `MediaPlayer.PlaybackParams` to scale tempo proportionally.
  - Formula: `playbackSpeed = 1.0f + (scrollSpeed - BASE_SPEED) / BASE_SPEED * 0.8f` (max 1.8×).
- States and their tracks:
  - `MENU` → `music_garden`
  - `PLAYING` (early) → `music_run_layer1`
  - `PLAYING` (mid, 500m+) → `music_run_layer2`
  - `PLAYING` (late, 1500m+) → `music_run_layer3`
  - `BLOOM_STATE` → `music_bloom` (then crossfades back)
  - `REST` → `music_rest`

### Step 20.3 — SFX Manager
- Simple wrapper with `SoundPool` for short SFX (jump, land, seed ping, bark, screech, howl).
- All triggered from corresponding entity/player methods.

**Deliverable:** Music is present in every state. Audio crossfades smoothly between states. Tempo is audibly faster in late game vs early game. Leitmotif phrase is recognisably present in every track (even REST).

---

## PHASE 21 — HapticManager

> **Goal:** All haptic triggers fire correctly and feel satisfying.

### Step 21.1 — HapticManager.kt
Full implementation from `TECHNICAL_ARCHITECTURE.md Section 10`:
- `shortPulse()` — 40ms. For jumps.
- `longPulse()` — 200ms. For Bloom activation and Game Over.
- `doubleTap()` — pattern `[0, 30, 50, 30]`. For Close Call.
- `mediumPulse()` — 100ms. For 1000-point milestone.
- Graceful fallback for devices without haptic support.

**Deliverable:** Every haptic event fires correctly. Tested on physical device. No crashes on emulator (graceful skip).

---

## PHASE 22 — Full Game State System (MENU / PLAYING / BLOOM / REST)

> **Goal:** All 4 game states work end-to-end. The full session lifecycle from `GDD.md Section 3` is playable.

### Step 22.1 — Menu / Garden State
- Animated woman sitting under Weeping Willow.
- Ambient forest audio plays.
- `First tap`: stand-up animation. `Second tap`: begin run.
- Garden shows unlocked plant icons in background.

### Step 22.2 — PLAYING State (Full)
- All systems active: parallax scroll, entity spawn, HUD, physics, particles, haptics, audio.
- Biome transitions every 500m.
- Difficulty scales continuously.

### Step 22.3 — BLOOM State
- 5-second duration tracked by timer.
- Character glow + petal trail active.
- Player hitbox disabled (`isInvincible = true`).
- All entities passed through convert to Seed orbs.
- Background saturation boosted +50%.
- Music → `music_bloom`, then crossfade back.
- After 5s: normal play resumes.

### Step 22.4 — REST State
- Triggered by `HIT` CollisionResult (non-Hedgehog).
- Screen shake fires.
- Character plays sit-down animation.
- Background continues to scroll (gently).
- REST screen overlay draws after 1 second:
  - Distance, Seeds, High Score.
  - FlavorText REST quote (contextual).
  - "Tap to Run Again" (PressStart2P font).
- `SaveManager.saveHighScore()` and `saveGardenProgress()` called.
- `PersistentMemoryManager` flushes to disk.
- `GhostRecorder` saves if new best.

**Deliverable:** Full play-through works. Start → run → bloom → die → REST → restart → repeat. No crashes. No state leaks. All transitions are smooth.

---

## PHASE 23 — Garden Screen (Meta-Loop)

> **Goal:** The garden screen shows unlocked plants, the next locked plant greyed out, and Seeds spent to unlock.

### Step 23.1 — GardenScreen.kt
- Renders garden background (parallax, slow scroll).
- Draws each unlocked plant entity in its garden placement.
- Draws next locked plant greyed-out at 30% opacity with "N seeds to unlock" label.
- Seed counter shown prominently.

### Step 23.2 — Plant Unlock Flow
- Tap on locked plant → seeds deducted if sufficient → unlock animation (plant grows from ground, bloom burst particles) → plant added to garden.
- All 9 unlockable plants from `GDD.md Section 10` accessible.

### Step 23.3 — SaveManager Integration
- Garden state saved/loaded on every transition.
- Lifetime seeds persist across sessions.

**Deliverable:** Garden visually grows over multiple sessions. Unlocking a plant feels satisfying. The game has a meta-reason to keep playing.

---

## PHASE 24 — Real Background Artwork

> **Goal:** Replace all placeholder colour rectangles with final pixel-art background layers.

### Step 24.1 — Background Layer Assets
Create all 6 background bitmaps listed in `ANDROID_SETUP.md Section 5` (bg_ files).  
Each is a **seamlessly looping horizontal strip** at device resolution (e.g., 2× screen width).

- `bg_layer1_mountains.png` — sky gradient + distant mountain silhouette.
- `bg_layer2_trees_[biome].png` — one per biome (6 files), mid-tone tree silhouettes.
- `bg_layer3_ground.png` — main ground path texture, seamless.
- `bg_layer4_foreground.png` — close-up grass strip, partially transparent, soft-blurred.

### Step 24.2 — Biome Crossfade
- Layer 2 crossfade fully wired: `alpha` lerps from 1→0 on old, 0→1 on new over 3s.
- Both drawn simultaneously during transition.

**Deliverable:** The game looks like a real game. No coloured rectangles remain. Biome transitions are visually beautiful.

---

## PHASE 25 — Polish Pass

> **Goal:** Every small detail that separates a 4-star app from a 5-star app.

### Step 25.1 — Floaty Apex (if not already tuned)
- Verify apex gravity reduction: for 0.2s at jump peak, gravity = 60% of normal. Feels cloud-like.

### Step 25.2 — Landing Impact Grass Reaction
- When player lands, floor grass sprites dip and spring back (2-frame animation on foreground layer).

### Step 25.3 — `"Nature's Grace"` Screen Flash
- On Mercy Miss: screen border flashes vibrant green for 0.3s.

### Step 25.4 — Slow-Motion Sparkle (Perfect Dodge)
- When Mercy Miss clearance is pixel-perfect (≤ 4px): game runs at 30% speed for 0.5s, gold sparkle particle burst, score +50, `"Close!"` flavour text in gold.

### Step 25.5 — Pre-launch Squash Anticipation
- `JUMP_START` state: 2 frames of downward squash before launching. This is the "windup" that makes the jump feel powerful.

### Step 25.6 — Seed Magnet
- During `BLOOM_STATE`: all Seed orbs on screen are attracted toward the player (`velocity toward player * magnetStrength`).

### Step 25.7 — Environment Wind Audio
- Subtle wind ambient SFX loops continuously, volume scales with `globalWindSpeed`.

### Step 25.8 — Icon & Splash Screen
- App icon: pixel-art woman silhouette mid-jump against a forest.
- Splash screen: logo animation — `"Forest_Run"` text grows from a seed particle.

**Deliverable:** Every small detail listed here is implemented. Play the game and feel the difference.

---

## PHASE 26 — Performance Audit

> **Goal:** Stable 60 FPS on a mid-range Android device (e.g., Snapdragon 680) across all biomes and effects.

### Step 26.1 — Bitmap Pre-loading
- All bitmaps loaded once in `init` blocks. Zero bitmap decoding during the game loop.
- All `Paint` and `Rect` objects created once, reused.

### Step 26.2 — Particle Cap Enforcement
- Verify 200-particle cap is respected. Profile with Android Memory Profiler.

### Step 26.3 — Entity Object Pooling
- Verify entity pooling is working. No GC spikes (visible in Android CPU Profiler as periodic dips).

### Step 26.4 — Canvas Layer Ordering
- Verify draw order: Layer1 → Layer2 → Layer3 → Entities → Player → Layer4 → Particles → HUD → FlavorText → DialagueBubbles.

### Step 26.5 — Test Device Matrix
Run and verify gameplay on:
- Low-end device (API 24, slow CPU).
- Mid-range device (API 30).
- High-refresh device (120Hz) — verify `deltaTime` prevents double-speed gameplay.

**Deliverable:** Game holds 60 FPS across all tested devices. Android Profiler shows no memory leaks.

---

## PHASE 27 — Google Play Preparation

> **Goal:** The app is fully publishable on the Google Play Store.

### Step 27.1 — App Signing
- Generate signed release APK / AAB (`Build → Generate Signed Bundle`).
- Create and secure keystore file.

### Step 27.2 — Release Build Config
- `isMinifyEnabled = true` in release build type.
- ProGuard rules for Gson (add `@Keep` or ProGuard Gson rules).
- Remove all debug logs (`BuildConfig.DEBUG` guard on all `Log.*` calls).

### Step 27.3 — Store Listing Assets
- **App Icon:** 512×512px PNG.
- **Feature Graphic:** 1024×500px — scenic forest art with character running.
- **Screenshots:** 6 landscape screenshots covering: Garden, Running (Day), Running (Night), Bloom State, REST screen, Biome transition.
- **Short Description** (80 chars): `"A living forest endless runner. Run. Jump. Spare the Fox."`
- **Full Description:** Full game description covering all biomes, entities, and Undertale-inspired mechanics.

### Step 27.4 — Content Rating
- Complete IARC questionnaire on Play Console. Expected: Everyone (E) rating.

### Step 27.5 — Privacy Policy
- Create simple privacy policy page (no personal data collected — local only).
- Link in Play Console.

### Step 27.6 — Final Checklist
- [ ] `versionCode` = 1, `versionName` = "1.0.0"
- [ ] No internet permission in manifest (not needed).
- [ ] Target SDK = 34.
- [ ] 64-bit ABI support: `abiFilters "arm64-v8a", "x86_64"` in build.gradle.
- [ ] AAB uploaded to Play Console.
- [ ] Internal test track live and tested on 3+ real devices.

**Deliverable:** App is live on the Google Play Internal Test Track. Install it on your phone from the store. It works.

---

## Summary — Step Completion Order

| Phase | What Gets Done | Playable? |
|---|---|---|
| 0 | Project skeleton, orientation locked | Black screen loads |
| 1 | 60 FPS game loop | FPS counter visible |
| 2 | Touch input classified | Logs correct input |
| 3 | Player physics | Rectangle jumps/ducks |
| 4 | 4-layer parallax | World scrolls |
| 5 | HUD | Score ticks, meter visible |
| 6 | Player sprites + face | Animated character |
| 7 | Entity base + sway | Placeholder boxes sway |
| 8 | 5 flora entities | First real obstacles |
| 9 | 4 tree entities | Overhead hazards |
| 10 | 5 bird entities | Aerial danger |
| 11 | 5 animal entities | Full AI roster |
| 12 | Spawner + difficulty | Endless run works |
| 13 | Biome system | World changes every 500m |
| 14 | All particles | Forest is alive |
| 15 | Camera FX | Shake + zoom working |
| 16 | Flavour text | Popups + REST quotes |
| 17 | Mercy + pacifist | Spare system works |
| 18 | Persistent memory | Costumes, déjà vu |
| 19 | Ghost run | Best run playback |
| 20 | Full audio + leitmotif | Music in every state |
| 21 | Haptics | Physical feedback |
| 22 | Full game state machine | Complete session lifecycle |
| 23 | Garden screen | Meta-loop works |
| 24 | Real artwork | Looks like a real game |
| 25 | Polish pass | Feels like an indie gem |
| 26 | Performance audit | 60 FPS on real hardware |
| 27 | Google Play prep | Publishable |

---

> **Rule:** Complete each phase fully before starting the next. Every line of code in a phase works, is tested manually, and has no `// TODO`. Zero compromises.

*Document version: 1.0 | Project: Forest_Run | Last updated: 2026-03-04*
