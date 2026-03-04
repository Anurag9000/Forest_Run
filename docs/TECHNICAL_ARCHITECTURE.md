# Forest_Run — Technical Architecture

Complete Kotlin/Android technical specification. This document defines the class architecture, engine, state machines, and all technical sub-systems needed to implement the game.

---

## 1. Project Structure

```
forest_run/
├── app/
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/com/yourname/forest_run/
│           │   ├── MainActivity.kt
│           │   ├── engine/
│           │   │   ├── GameView.kt          ← SurfaceView, main game loop
│           │   │   ├── GameThread.kt        ← Dedicated game loop thread
│           │   │   ├── GameStateManager.kt  ← MENU / PLAYING / BLOOM / REST states
│           │   │   ├── PhysicsEngine.kt     ← Gravity, collision, velocity
│           │   │   └── InputHandler.kt      ← Touch/swipe processing
│           │   ├── entities/
│           │   │   ├── Entity.kt            ← Base entity class
│           │   │   ├── Player.kt            ← Player state machine
│           │   │   ├── flora/
│           │   │   │   ├── Cactus.kt
│           │   │   │   ├── LilyOfValley.kt
│           │   │   │   ├── Hyacinth.kt
│           │   │   │   ├── Eucalyptus.kt
│           │   │   │   └── VanillaOrchid.kt
│           │   │   ├── trees/
│           │   │   │   ├── WeepingWillow.kt
│           │   │   │   ├── Jacaranda.kt
│           │   │   │   ├── Bamboo.kt
│           │   │   │   └── CherryBlossom.kt
│           │   │   ├── birds/
│           │   │   │   ├── Owl.kt
│           │   │   │   ├── Duck.kt
│           │   │   │   ├── Eagle.kt
│           │   │   │   ├── Tit.kt
│           │   │   │   └── Chickadee.kt
│           │   │   └── animals/
│           │   │       ├── Wolf.kt
│           │   │       ├── Cat.kt
│           │   │       ├── Fox.kt
│           │   │       ├── Hedgehog.kt
│           │   │       └── Dog.kt
│           │   ├── systems/
│           │   │   ├── EntityManager.kt     ← Spawning + pooling of all entities
│           │   │   ├── BiomeManager.kt      ← Biome transitions every 500m
│           │   │   ├── ParticleManager.kt   ← All particle emitters
│           │   │   ├── ParallaxBackground.kt← 4-layer parallax
│           │   │   ├── SwayComponent.kt     ← Sine-wave wind sway
│           │   │   ├── AudioManager.kt      ← Adaptive music + SFX
│           │   │   └── HapticManager.kt     ← Vibrator service wrapper
│           │   ├── ui/
│           │   │   ├── HUD.kt               ← Score, Bloom Meter, Seeds
│           │   │   ├── GameOverScreen.kt    ← REST state UI
│           │   │   └── GardenScreen.kt      ← Main menu / garden
│           │   └── utils/
│           │       ├── SpriteSheetHelper.kt ← Slice bitmap sprite sheets
│           │       ├── SaveManager.kt       ← SharedPreferences + JSON save
│           │       └── MathUtils.kt         ← lerp, sin helpers
│           └── assets/                      ← All .png sprite files
```

---

## 2. Engine — GameView & Game Loop

### GameView.kt (SurfaceView)

```kotlin
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private val gameThread: GameThread
    private val stateManager: GameStateManager
    private val inputHandler: InputHandler

    init {
        holder.addCallback(this)
        gameThread = GameThread(holder, this)
        stateManager = GameStateManager(context)
        inputHandler = InputHandler(stateManager)
        setOnTouchListener(inputHandler)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        gameThread.isRunning = true
        gameThread.start()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        gameThread.isRunning = false
        gameThread.join()
    }

    fun update(deltaTime: Float) {
        stateManager.update(deltaTime)
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        stateManager.draw(canvas)
    }
}
```

### GameThread.kt

```kotlin
class GameThread(private val holder: SurfaceHolder, private val view: GameView) : Thread() {

    var isRunning = false
    private val targetFPS = 60
    private val targetFrameTime = 1000L / targetFPS

    override fun run() {
        var lastTime = System.nanoTime()
        while (isRunning) {
            val now = System.nanoTime()
            val deltaTime = (now - lastTime) / 1_000_000_000f  // In seconds
            lastTime = now

            view.update(deltaTime)

            val canvas = holder.lockCanvas() ?: continue
            try {
                synchronized(holder) { view.draw(canvas) }
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }

            val sleepTime = targetFrameTime - (System.nanoTime() - now) / 1_000_000
            if (sleepTime > 0) sleep(sleepTime)
        }
    }
}
```

**Key point:** All movement uses `deltaTime` multiplication to remain **frame-rate independent** (works identically on 60Hz and 120Hz displays).

---

## 3. Game State Machine — GameStateManager

```kotlin
enum class GameState {
    MENU,       // Garden view with seated character
    PLAYING,    // Active gameplay  
    BLOOM_STATE,// 5-second invincibility power-up
    REST        // Game over — character sits, results shown
}
```

### State Transitions

```
MENU → PLAYING        : Player double-taps
PLAYING → BLOOM_STATE : Bloom Meter reaches 10 Seeds
BLOOM_STATE → PLAYING : After 5 seconds
PLAYING → REST        : Collision detected (Wolf/Cactus/Eagle)
REST → MENU           : Player taps "Run Again" or after 3 seconds
```

### GameStateManager.kt (pseudocode)

```kotlin
class GameStateManager(context: Context) {

    var currentState: GameState = GameState.MENU
    var distanceTravelled: Float = 0f
    var score: Int = 0
    var seedsCollected: Int = 0
    var bloomMeter: Int = 0
    var scrollSpeed: Float = 10f

    val player: Player
    val entityManager: EntityManager
    val biomeManager: BiomeManager
    val particleManager: ParticleManager
    val parallaxBackground: ParallaxBackground
    val audioManager: AudioManager
    val hapticManager: HapticManager

    fun update(deltaTime: Float) {
        when (currentState) {
            GameState.PLAYING, GameState.BLOOM_STATE -> updatePlaying(deltaTime)
            GameState.REST -> updateRest(deltaTime)
            else -> {}
        }
    }

    private fun updatePlaying(deltaTime: Float) {
        distanceTravelled += scrollSpeed * deltaTime
        score += (scrollSpeed * deltaTime).toInt()

        // Dynamic difficulty
        scrollSpeed = BASE_SPEED + (distanceTravelled / 250f) * SPEED_INCREMENT

        // Biome transitions
        if (distanceTravelled % 500f < scrollSpeed * deltaTime) {
            biomeManager.triggerBiomeTransition()
        }

        // Update systems
        player.update(deltaTime)
        entityManager.update(deltaTime, scrollSpeed)
        particleManager.update(deltaTime)
        parallaxBackground.update(deltaTime, scrollSpeed)
        checkCollisions()
    }
}
```

---

## 4. Player State Machine — Player.kt

```kotlin
enum class PlayerState {
    RUNNING,
    JUMP_START,   // 2 frames of squash before launch
    JUMPING,      // Ascending
    APEX,         // Peak of arc
    FALLING,      // Descending
    LANDING,      // 3 frames of squash on impact
    DUCKING,      // Swipe-down slide
    BLOOM,        // Invincible power-up state
    REST          // Sitting, game over
}
```

### Jump Physics

```kotlin
// Variable jump height based on touch duration
var jumpHoldTime: Float = 0f
val minJumpForce = -600f
val maxJumpForce = -1100f

fun onJumpPressed() {
    jumpHoldTime = 0f
}

fun onJumpHeld(deltaTime: Float) {
    jumpHoldTime += deltaTime
}

fun onJumpReleased() {
    val t = min(jumpHoldTime / MAX_HOLD_DURATION, 1f)
    velocityY = lerp(minJumpForce, maxJumpForce, t)
    state = PlayerState.JUMP_START
}

// Gravity
velocityY += GRAVITY * deltaTime
y += velocityY * deltaTime

// Ground check
if (y >= GROUND_Y) {
    y = GROUND_Y
    velocityY = 0f
    isGrounded = true
    state = PlayerState.LANDING
}
```

### Squash & Stretch Application

```kotlin
fun getScaleY(): Float = when (state) {
    PlayerState.JUMP_START -> 0.8f
    PlayerState.JUMPING    -> 1.2f
    PlayerState.FALLING    -> 1.15f
    PlayerState.LANDING    -> 0.75f
    PlayerState.DUCKING    -> 0.7f
    else                   -> 1.0f
}

fun getScaleX(): Float = when (state) {
    PlayerState.JUMP_START -> 1.25f
    PlayerState.JUMPING    -> 0.85f
    PlayerState.LANDING    -> 1.3f
    PlayerState.DUCKING    -> 1.15f
    else                   -> 1.0f
}
```

---

## 5. Entity System

### Base Entity.kt

```kotlin
abstract class Entity(context: Context) {

    var x: Float = 0f
    var y: Float = 0f
    var velocityX: Float = 0f
    var velocityY: Float = 0f
    var hitbox: RectF = RectF()
    var currentAnimation: String = ""
    var isActive: Boolean = true

    var swayComponent: SwayComponent? = null  // Null for non-flora
    var particleEmitter: ParticleEmitter? = null

    abstract fun update(deltaTime: Float, scrollSpeed: Float)
    abstract fun draw(canvas: Canvas)
    abstract fun performUniqueAction(player: Player)
    abstract fun onCollision(player: Player)
}
```

### SwayComponent.kt

```kotlin
class SwayComponent(
    val speed: Float,
    val intensity: Float
) {
    private var time: Float = 0f

    fun getXOffset(deltaTime: Float, globalWindMultiplier: Float): Float {
        time += deltaTime
        return sin(time * speed) * intensity * globalWindMultiplier
    }
}
```

Usage in draw:

```kotlin
override fun draw(canvas: Canvas) {
    val xOffset = swayComponent?.getXOffset(deltaTime, globalWindSpeed) ?: 0f
    // Apply xOffset to TOP of sprite only; base stays at x
    canvas.save()
    // Skew or draw top-portion with xOffset
    canvas.restore()
}
```

---

## 6. EntityManager & Spawner

### EntityManager.kt

```kotlin
class EntityManager(context: Context) {

    private val activeEntities: MutableList<Entity> = mutableListOf()
    private var spawnTimer: Float = 0f
    var currentSpawnPool: List<EntityType> = BiomeManager.HOME_GROVE_POOL
    var spawnInterval: Float = 2.0f  // Decreases with difficulty

    fun update(deltaTime: Float, scrollSpeed: Float) {
        spawnTimer += deltaTime
        if (spawnTimer >= spawnInterval) {
            spawnTimer = 0f
            spawnRandom()
        }

        activeEntities.forEach { it.update(deltaTime, scrollSpeed) }
        activeEntities.removeAll { it.x < -it.hitbox.width() * 2 }  // Despawn off-screen
    }

    private fun spawnRandom() {
        val type = currentSpawnPool.random()
        val entity = EntityFactory.create(type, startX = screenWidth + 50f)
        activeEntities.add(entity)
    }

    fun updateSpawnPool(biome: Biome) {
        currentSpawnPool = BiomeManager.getPoolForBiome(biome)
        // Smooth transition — don't immediately clear active entities
    }
}
```

### EntityFactory.kt

```kotlin
object EntityFactory {
    fun create(type: EntityType, startX: Float): Entity = when (type) {
        EntityType.CACTUS         -> Cactus(startX)
        EntityType.LILY_OF_VALLEY -> LilyOfValley(startX)
        EntityType.HYACINTH       -> Hyacinth(startX)
        EntityType.EUCALYPTUS     -> Eucalyptus(startX)
        EntityType.VANILLA        -> VanillaOrchid(startX)
        EntityType.WEEPING_WILLOW -> WeepingWillow(startX)
        EntityType.JACARANDA      -> Jacaranda(startX)
        EntityType.BAMBOO         -> Bamboo(startX)
        EntityType.CHERRY_BLOSSOM -> CherryBlossom(startX)
        EntityType.OWL            -> Owl(startX)
        EntityType.DUCK           -> Duck(startX)
        EntityType.EAGLE          -> Eagle(startX)
        EntityType.TIT            -> Tit(startX)
        EntityType.CHICKADEE      -> Chickadee(startX)
        EntityType.WOLF           -> Wolf(startX)
        EntityType.CAT            -> Cat(startX)
        EntityType.FOX            -> Fox(startX)
        EntityType.HEDGEHOG       -> Hedgehog(startX)
        EntityType.DOG            -> Dog(startX)
    }
}
```

---

## 7. BiomeManager

```kotlin
enum class Biome {
    HOME_GROVE,
    SPRING_ORCHARD,
    ANCIENT_GROVE,
    VIOLET_PATH,
    FLOWERING_MEADOW,
    STORMY_RIDGE
}

object BiomeManager {
    val HOME_GROVE_POOL    = listOf(CACTUS, CAT, DOG, DUCK, TIT)
    val SPRING_ORCHARD_POOL= listOf(HYACINTH, LILY_OF_VALLEY, BAMBOO, CHERRY_BLOSSOM, HEDGEHOG, FOX, CHICKADEE, TIT)
    val ANCIENT_GROVE_POOL = listOf(EUCALYPTUS, CACTUS, WEEPING_WILLOW, WOLF, HEDGEHOG, EAGLE, OWL)
    val VIOLET_PATH_POOL   = listOf(VANILLA, HYACINTH, JACARANDA, FOX, CAT, OWL, DUCK)
    val FLOWERING_MEADOW_POOL = listOf(LILY_OF_VALLEY, HYACINTH, VANILLA, CHERRY_BLOSSOM, FOX, CAT, CHICKADEE)
    val STORMY_RIDGE_POOL  = listOf(BAMBOO, WEEPING_WILLOW, CACTUS, EUCALYPTUS, WOLF, DOG, EAGLE, OWL)

    fun getPoolForBiome(biome: Biome): List<EntityType> = when (biome) {
        Biome.HOME_GROVE       -> HOME_GROVE_POOL
        Biome.SPRING_ORCHARD   -> SPRING_ORCHARD_POOL
        Biome.ANCIENT_GROVE    -> ANCIENT_GROVE_POOL
        Biome.VIOLET_PATH      -> VIOLET_PATH_POOL
        Biome.FLOWERING_MEADOW -> FLOWERING_MEADOW_POOL
        Biome.STORMY_RIDGE     -> STORMY_RIDGE_POOL
    }

    fun getBiomeAt(distance: Float): Biome = when ((distance / 500f).toInt() % 6) {
        0 -> Biome.HOME_GROVE
        1 -> Biome.SPRING_ORCHARD
        2 -> Biome.ANCIENT_GROVE
        3 -> Biome.VIOLET_PATH
        4 -> Biome.FLOWERING_MEADOW
        5 -> Biome.STORMY_RIDGE
        else -> Biome.HOME_GROVE
    }
}
```

---

## 8. Input Handler

```kotlin
class InputHandler(private val stateManager: GameStateManager) : View.OnTouchListener {

    private var touchStartTime: Long = 0L
    private var touchStartY: Float = 0f
    private val swipeThreshold = 100f

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartTime = System.currentTimeMillis()
                touchStartY = event.y
                stateManager.player.onJumpPressed()
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - touchStartY
                if (dy > swipeThreshold) {
                    stateManager.player.startDuck()  // Swipe down = duck
                } else {
                    stateManager.player.onJumpHeld((System.currentTimeMillis() - touchStartTime) / 1000f)
                }
            }
            MotionEvent.ACTION_UP -> {
                stateManager.player.onJumpReleased()
                stateManager.player.stopDuck()
            }
        }
        return true
    }
}
```

---

## 9. Sprite Sheet Helper

```kotlin
object SpriteSheetHelper {
    fun splitSpriteSheet(bitmap: Bitmap, frames: Int): Array<Bitmap> {
        val frameWidth = bitmap.width / frames
        return Array(frames) { i ->
            Bitmap.createBitmap(bitmap, i * frameWidth, 0, frameWidth, bitmap.height)
        }
    }
}
```

Usage:

```kotlin
val runSheet = BitmapFactory.decodeAsset(context.assets, "player_run.png")
val runFrames = SpriteSheetHelper.splitSpriteSheet(runSheet, 48)
```

---

## 10. Haptic Manager

```kotlin
class HapticManager(context: Context) {
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    fun shortPulse() = vibrate(40L)       // Jump
    fun longPulse() = vibrate(200L)       // Bloom / Game Over
    fun doubleTap() {                     // Close Call
        vibrate(longArrayOf(0, 30, 50, 30), -1)
    }

    private fun vibrate(ms: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(ms)
        }
    }

    private fun vibrate(pattern: LongArray, repeat: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, repeat))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(pattern, repeat)
        }
    }
}
```

---

## 11. Save Manager

```kotlin
object SaveManager {
    private const val PREFS_KEY = "forest_run_prefs"
    private const val KEY_HIGH_SCORE = "high_score"
    private const val KEY_LIFETIME_SEEDS = "lifetime_seeds"
    private const val KEY_GARDEN_JSON = "garden_json"

    fun saveHighScore(context: Context, score: Int) {
        context.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)
            .edit().putInt(KEY_HIGH_SCORE, score).apply()
    }

    fun getHighScore(context: Context): Int =
        context.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)
            .getInt(KEY_HIGH_SCORE, 0)

    fun saveGardenProgress(context: Context, garden: GardenData) {
        val json = Gson().toJson(garden)
        context.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)
            .edit().putString(KEY_GARDEN_JSON, json).apply()
    }
}
```

---

## 12. Dynamic Difficulty Curve

```kotlin
object DifficultyScaler {
    const val BASE_SPEED = 10f
    const val SPEED_INCREMENT = 0.04f   // Per 250m
    const val BASE_SPAWN_INTERVAL = 2.0f
    const val MIN_SPAWN_INTERVAL = 0.6f

    fun getScrollSpeed(distance: Float): Float =
        BASE_SPEED + (distance / 250f) * SPEED_INCREMENT

    fun getSpawnInterval(distance: Float): Float =
        max(MIN_SPAWN_INTERVAL, BASE_SPAWN_INTERVAL - (distance / 500f) * 0.1f)
}
```

---

*Document version: 1.0 | Project: Forest_Run | Last updated: 2026-03-04*
