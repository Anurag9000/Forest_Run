package com.yourname.forest_run.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Player
import com.yourname.forest_run.entities.PlayerState
import com.yourname.forest_run.systems.FxPreset
import com.yourname.forest_run.systems.GhostPlayer
import com.yourname.forest_run.systems.GhostRecorder
import com.yourname.forest_run.systems.ParticleManager
import com.yourname.forest_run.ui.FlavorTextManager
import com.yourname.forest_run.ui.GameOverScreen
import com.yourname.forest_run.ui.GardenScreen
import com.yourname.forest_run.ui.HUD
import com.yourname.forest_run.ui.MainMenuScreen
import com.yourname.forest_run.ui.DialogueBubbleManager
import com.yourname.forest_run.ui.RestQuoteManager

private const val TAG = "ForestRun"

/**
 * The top-level game view.
 *
 * Phases implemented:
 *  - Phase 0: SurfaceView + GameThread scaffold
 *  - Phase 1: 60 FPS loop with nanosecond deltaTime
 *  - Phase 2: [InputHandler] wired + on-screen debug panel
 *  - Phase 3: [Player] physics, state machine, squash/stretch, hitbox
 *  - Phase 4: [ParallaxBackground] 4-layer scroll, floor line
 *  - Phase 5: [GameStateManager] scroll/score/seeds/bloom; [HUD] drawn last
 *  - Phase 6: [SpriteManager] loaded, passed to Player
 *  - Phase 12: [EntityManager] spawner + collision loop live
 */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    @Volatile
    internal var debugFrameCounter: Long = 0

    // -----------------------------------------------------------------------
    // Engine
    // -----------------------------------------------------------------------
    private var gameThread: GameThread = GameThread(holder, this)

    // -----------------------------------------------------------------------
    // Input
    // -----------------------------------------------------------------------
    val inputHandler = InputHandler()
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // -----------------------------------------------------------------------
    // Phase 3: Player – initialized in surfaceCreated once we know screen size
    // -----------------------------------------------------------------------
    private lateinit var player: Player

    // -----------------------------------------------------------------------
    // Phase 4: Background
    // -----------------------------------------------------------------------
    private lateinit var parallaxBackground: ParallaxBackground

    // -----------------------------------------------------------------------
    // Phase 5: Game state + HUD
    // Phase 6: Sprite Manager
    // Phase 12: Entity Manager
    // Phase 17: GameOverScreen + run-state machine
    // -----------------------------------------------------------------------
    private lateinit var gameState: GameStateManager
    private lateinit var hud: HUD
    private lateinit var spriteManager: SpriteManager
    private lateinit var entityManager: EntityManager
    private lateinit var gameOverScreen: GameOverScreen

    // ── Run State (Phase 17) ──────────────────────────────────────────────
    @Volatile
    private var runState: RunState = RunState.PLAYING
    private val runResetManager    = RunResetManager()

    // ── App Game State (Phase 22) ─────────────────────────────────────────
    /** Top-level lifecycle state — MENU, GARDEN, PLAYING, BLOOM, REST. */
    @Volatile
    private var appState: AppGameState = AppGameState.MENU
    private lateinit var mainMenuScreen: MainMenuScreen
    private lateinit var gardenScreen: GardenScreen
    private var currentRestQuote: String = "The forest is waiting for a cleaner run."

    // Restart fade-to-black overlay
    private val restartFadePaint = Paint().apply { color = Color.BLACK }

    // Screen-flash overlay for MERCY_MISS (green border pulse)
    private var mercyFlashTimer = 0f
    private val mercyFlashDuration = 0.3f
    private val mercyFlashPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 20f
        color = Color.argb(200, 60, 240, 80)
    }

    // Phase 13: Night/dusk ambient darkness overlay
    private val ambientOverlayPaint = Paint().apply { color = Color.BLACK }

    // ── Phase 19: Ghost Run ───────────────────────────────────────────────
    private val ghostRecorder = GhostRecorder()
    private val ghostPlayer   = GhostPlayer()

    // -----------------------------------------------------------------------
    // Paint objects – created ONCE, never inside draw()
    // -----------------------------------------------------------------------
    private val bgPaint = Paint().apply {
        color = Color.BLACK; style = Paint.Style.FILL
    }

    // -----------------------------------------------------------------------
    // Screen dimensions
    // -----------------------------------------------------------------------
    var screenWidth:  Int = 0
        private set
    var screenHeight: Int = 0
        private set

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------
    init {
        holder.addCallback(this)
        wireInputCallbacks()
        setOnTouchListener { view, event ->
            val idx = event.actionIndex.coerceAtLeast(0)
            lastTouchX = event.getX(idx)
            lastTouchY = event.getY(idx)
            inputHandler.onTouch(view, event)
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        screenWidth  = width
        screenHeight = height

        // Phase 5: GameStateManager first (owns scroll speed)
        if (!::gameState.isInitialized) {
            gameState = GameStateManager(context)
        }

        // Phase 4: background (groundY used by Player)
        if (!::parallaxBackground.isInitialized) {
            parallaxBackground = ParallaxBackground(screenWidth, screenHeight)
        }

        // Phase 6: SpriteManager
        if (!::spriteManager.isInitialized) {
            spriteManager = SpriteManager(context)
        }

        // Phase 12: EntityManager (needs spriteManager and screen dimensions)
        if (!::entityManager.isInitialized) {
            entityManager = EntityManager(context, screenWidth.toFloat(), screenHeight.toFloat(), spriteManager)
        }

        // Phase 16: init FlavorTextManager pixel font
        FlavorTextManager.init(context)
        DialogueBubbleManager.init(context)

        // Phase 17: GameOverScreen
        if (!::gameOverScreen.isInitialized) {
            gameOverScreen = GameOverScreen(context, screenWidth, screenHeight)
        }

        // Phase 19: Load ghost run
        if (!ghostPlayer.hasGhost) {
            val frames = SaveManager.loadGhostRun(context)
            if (frames.isNotEmpty()) ghostPlayer.load(frames)
        }

        // Phase 20: Init audio managers
        LeitmotifManager.init(context)
        SfxManager.init(context)
        // Phase 22: Start with garden music in MENU, run music when PLAYING
        if (appState == AppGameState.MENU) {
            LeitmotifManager.transitionTo(LeitmotifManager.MusicState.MENU)
        } else {
            LeitmotifManager.playRunStart()
        }

        // Phase 21: Init haptics
        HapticManager.init(context)

        // Phase 22: MainMenuScreen
        if (!::mainMenuScreen.isInitialized) {
            mainMenuScreen = MainMenuScreen(context, spriteManager, screenWidth, screenHeight)
            mainMenuScreen.onGardenTap = { appState = AppGameState.GARDEN }
        }

        // Phase 23: GardenScreen
        if (!::gardenScreen.isInitialized) {
            gardenScreen = GardenScreen(context, spriteManager, screenWidth, screenHeight)
            gardenScreen.onBack = { appState = AppGameState.MENU }
            gardenScreen.load()
        }

        // Phase 5: HUD
        if (!::hud.isInitialized) {
            hud = HUD(context, screenWidth, screenHeight)
        }

        // Phase 3: Player
        if (!::player.isInitialized) {
            player = Player(screenWidth, screenHeight, spriteManager, parallaxBackground.groundY)
            wirePlayerToInput()
        }

        gameThread.isRunning = true
        if (gameThread.state == Thread.State.NEW) gameThread.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        screenWidth  = width
        screenHeight = height
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopThread()
    }

    fun pause() {
        stopThread()
        LeitmotifManager.pause()   // Phase 20
        if (::gameState.isInitialized) gameState.save()   // persist high score
    }

    fun resume() {
        LeitmotifManager.resume()  // Phase 20
        // Re-create thread (Java threads can't be restarted after stop)
        gameThread = GameThread(holder, this)
        // Thread starts when surfaceCreated fires again (or immediately if surface exists)
        if (holder.surface?.isValid == true) {
            gameThread.isRunning = true
            gameThread.start()
        }
    }

    private fun stopThread() {
        gameThread.isRunning = false
        var retry = true
        while (retry) {
            try { gameThread.join(); retry = false }
            catch (e: InterruptedException) { Thread.currentThread().interrupt() }
        }
    }

    // -----------------------------------------------------------------------
    // Input callback wiring
    // -----------------------------------------------------------------------

    private fun wireInputCallbacks() {
        // These callbacks run regardless of whether the player is initialized yet.
        inputHandler.onJumpReleased = {
            when {
                // Menu taps drive the menu screen
                appState == AppGameState.MENU -> {
                    if (::mainMenuScreen.isInitialized) {
                        mainMenuScreen.onTap(lastTouchX, lastTouchY)
                    }
                }
                appState == AppGameState.GARDEN -> {
                    if (::gardenScreen.isInitialized) {
                        gardenScreen.onTap(lastTouchX, lastTouchY)
                    }
                }
                // GAME_OVER tap begins restart
                runState == RunState.GAME_OVER -> {
                    runState = runResetManager.beginRestart()
                }
                else -> { /* handled by wirePlayerToInput when PLAYING */ }
            }
        }
    }

    /** Called once after [player] is initialized to attach physics callbacks. */
    private fun wirePlayerToInput() {
        val prev_pressed  = inputHandler.onJumpPressed
        val prev_released = inputHandler.onJumpReleased
        val prev_duck     = inputHandler.onDuckPressed
        val prev_duckEnd  = inputHandler.onDuckReleased

        inputHandler.onJumpPressed  = { prev_pressed?.invoke();           player.onJumpPressed() }
        inputHandler.onJumpHeld     = { holdSec -> player.onJumpHeld(holdSec) }
        inputHandler.onJumpReleased = { holdSec -> prev_released?.invoke(holdSec); player.onJumpReleased(holdSec) }
        inputHandler.onDuckPressed  = { prev_duck?.invoke();              player.onDuckPressed() }
        inputHandler.onDuckReleased = { prev_duckEnd?.invoke();           player.onDuckReleased() }
    }

    // -----------------------------------------------------------------------
    // Game loop – called by GameThread
    // -----------------------------------------------------------------------

    fun update(deltaTime: Float) {
        debugFrameCounter++
        // Phase 15: Advance camera shake
        CameraSystem.update(deltaTime)

        // Input tick
        inputHandler.tick(deltaTime)

        if (!::gameState.isInitialized) return

        // Phase 22: MENU state — update menu screen, gate physics
        if (appState == AppGameState.MENU) {
            if (::mainMenuScreen.isInitialized) {
                mainMenuScreen.update(deltaTime)
                if (mainMenuScreen.consumeStartRunRequest()) {
                    prepareFreshRun()
                    appState = AppGameState.PLAYING
                }
            }
            return   // No physics / entity updates while in menu
        }

        // Phase 23: GARDEN state
        if (appState == AppGameState.GARDEN) {
            if (::gardenScreen.isInitialized) {
                gardenScreen.refresh()
                gardenScreen.update(deltaTime)
            }
            return   // No physics while in garden
        }

        // Phase 17: State gate — freeze physics in DYING / GAME_OVER / RESTARTING
        when (runState) {
            RunState.DYING -> {
                val next = runResetManager.update(deltaTime, runState)
                if (next == RunState.GAME_OVER) runState = RunState.GAME_OVER
                CameraSystem.update(deltaTime)
                ParticleManager.update(deltaTime)
                FlavorTextManager.update(deltaTime)
                DialogueBubbleManager.update(deltaTime)
                if (::gameOverScreen.isInitialized) gameOverScreen.update(deltaTime)
                return
            }
            RunState.GAME_OVER -> {
                if (::gameOverScreen.isInitialized) gameOverScreen.update(deltaTime)
                return   // Hard stop — no physics updates
            }
            RunState.RESTARTING -> {
                val next = runResetManager.update(deltaTime, runState)
                restartFadePaint.alpha = runResetManager.restartFadeAlpha
                if (next == RunState.PLAYING && runResetManager.restartFadeAlpha >= 255) {
                    if (::entityManager.isInitialized && ::player.isInitialized &&
                        ::gameState.isInitialized) {
                        runResetManager.executeReset(gameState, entityManager, player)
                        ghostRecorder.reset()
                        reloadGhost()
                    }
                    if (::gardenScreen.isInitialized) gardenScreen.refresh()
                    appState = AppGameState.GARDEN
                    LeitmotifManager.transitionTo(LeitmotifManager.MusicState.MENU)
                    runState = RunState.PLAYING
                }
                return
            }
            RunState.PLAYING -> { /* fall through to physics */ }
        }

        // Phase 5: update game state (scroll speed lives here now)
        gameState.update(deltaTime)

        // Phase 4: update parallax with state's scroll speed
        if (::parallaxBackground.isInitialized)
            parallaxBackground.update(deltaTime, gameState.scrollSpeed)

        // Phase 5: update HUD
        if (::hud.isInitialized) hud.update(deltaTime, gameState)

        // Phase 3: update player physics
        // Phase 13: BiomeManager update (colours, entity pool)
        if (::entityManager.isInitialized) {
            entityManager.biomeManager.update(gameState.distanceMetres)
            if (::parallaxBackground.isInitialized) {
                val bm = entityManager.biomeManager
                parallaxBackground.applyBiomeColours(
                    bm.currentSkyTop, bm.currentSkyBottom,
                    bm.currentGround, bm.currentFoliage
                )
            }
        }

        if (!::player.isInitialized) return

        if (gameState.isBloomActive && !player.isInvincible) {
            player.activateBloom()
            LeitmotifManager.playBloom()
            SfxManager.playBloomActivate()
        } else if (!gameState.isBloomActive && player.isInvincible) {
            player.deactivateBloom()
            LeitmotifManager.endBloom(gameState.distanceMetres)
        }

        player.update(deltaTime, gameState.scrollSpeed)

        // Phase 12: EntityManager update (spawn, scroll, pass-detection)
        if (::entityManager.isInitialized) {
            entityManager.update(deltaTime, gameState, player)

            // Collision loop
            val collision = entityManager.checkCollisions(player, gameState)
            if (collision != null) {
                when (collision.result) {
                    CollisionResult.HIT -> {
                        player.triggerRest()  // emits HIT_BURST particles
                        CameraSystem.shakeHit()
                        SfxManager.playHit()          // Phase 20
                        LeitmotifManager.playRest()   // Phase 20
                        HapticManager.longPulse()     // Phase 21 — strong death feedback
                        // Phase 19: save ghost if this run is a new best distance
                        if (::gameState.isInitialized &&
                            gameState.distanceMetres > SaveManager.loadBestDistance(context)
                        ) {
                            SaveManager.saveGhostRun(context, ghostRecorder.snapshot())
                            SaveManager.saveBestDistance(context, gameState.distanceMetres)
                        }
                        entityManager.entityTypeOf(collision.entity)?.let { killerType ->
                            PersistentMemoryManager.recordHit(context, killerType)
                            currentRestQuote = RestQuoteManager.quoteFor(
                                context = context,
                                biome = entityManager.biomeManager.currentBiome,
                                killer = killerType
                            )
                        } ?: run {
                            currentRestQuote = RestQuoteManager.quoteFor(
                                context = context,
                                biome = entityManager.biomeManager.currentBiome,
                                killer = null
                            )
                        }
                        ghostRecorder.reset()
                        // Transition to DYING
                        if (::gameState.isInitialized) runResetManager.triggerDeath(gameState)
                        runState = RunState.DYING
                    }
                    CollisionResult.STUMBLE -> {
                        // User Prompt "accompanied by a screen-flash of the forest's dominant color"
                        player.triggerStumble()
                        mercyFlashTimer = mercyFlashDuration
                        val dominantColor = if (::entityManager.isInitialized) entityManager.biomeManager.currentFoliage else Color.rgb(255, 180, 200)
                        mercyFlashPaint.color = Color.argb(200, Color.red(dominantColor), Color.green(dominantColor), Color.blue(dominantColor))
                        SfxManager.playHit() // Non-lethal hit
                        CameraSystem.shakeHit()
                        HapticManager.mediumPulse()
                        // Stumble triggers brief invincibility via the player state machine
                        collision.entity.isActive = false // Despawn the animal we hit
                    }
                    CollisionResult.MERCY_MISS -> {
                        mercyFlashTimer = mercyFlashDuration
                        mercyFlashPaint.color = Color.argb(200, 60, 240, 80) // reset green
                        SfxManager.playMercyMiss()    // Phase 20
                        HapticManager.doubleTap()     // Phase 21 — close call buzz
                        // Stars burst at player centre
                        ParticleManager.emit(FxPreset.MERCY_STARS,
                            player.x + Player.BASE_WIDTH / 2f,
                            player.y + Player.BASE_HEIGHT / 2f)
                        CameraSystem.shakeMercyMiss()
                    }
                    CollisionResult.NONE -> { /* handled above, shouldn't reach here */ }
                }
            }

            // Tick down mercy flash
            if (mercyFlashTimer > 0f) mercyFlashTimer -= deltaTime
        }

        // Phase 14: Track bloom aura position with player
        if (::player.isInitialized && player.state == PlayerState.BLOOM) {
            // ParticleManager continuous emitters track emitter.x/.y at spawn time;
            // the aura emitter is recreated on each Bloom trigger so position is correct.
        }

        // Phase 19: Record ghost frame + advance ghost playback
        if (::player.isInitialized) ghostRecorder.record(deltaTime, player)
        ghostPlayer.update(deltaTime)

        // Phase 20: Music layer transition + tempo scaling
        if (::gameState.isInitialized) {
            LeitmotifManager.updateDistance(gameState.distanceMetres)
            LeitmotifManager.updateTempo(gameState.scrollSpeed)

            // Phase 21: 1000-point milestone haptic + camera nudge
            if (gameState.consumeMilestone()) {
                HapticManager.mediumPulse()
                CameraSystem.addTrauma(0.3f)   // gentle nudge on 1000-pt milestone
            }
        }

        // Flavor text float animation
        FlavorTextManager.update(deltaTime)
        DialogueBubbleManager.update(deltaTime)

        // Phase 14: Update all particles
        ParticleManager.update(deltaTime)
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        // 1. Black fill (never shakes — clean border always visible)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Phase 22: MENU renders its own full-screen scene
        if (appState == AppGameState.MENU) {
            if (::mainMenuScreen.isInitialized) mainMenuScreen.draw(canvas)
            return
        }

        // Phase 23: GARDEN renders plant meta-loop
        if (appState == AppGameState.GARDEN) {
            if (::gardenScreen.isInitialized) gardenScreen.draw(canvas)
            return
        }

        // 2–5: All gameplay layers wrapped in camera shake offset
        CameraSystem.applyTo(canvas) {

            // 2. Parallax background
            if (::parallaxBackground.isInitialized) parallaxBackground.draw(canvas)

            // 3. Entities (behind player, above background)
            if (::entityManager.isInitialized) {
                entityManager.draw(canvas)
                // 3b. Seed orbs — drawn above entities, below player
                val bloomFrac = if (::gameState.isInitialized)
                    gameState.bloomMeter / GameConstants.BLOOM_SEED_COUNT.toFloat()
                else 0f
                entityManager.drawOrbs(canvas, bloomFrac)
            }

            // 4. Ghost player (behind live player, 40% opacity white-blue) — Phase 19
            if (::spriteManager.isInitialized) ghostPlayer.draw(canvas, spriteManager, if (::player.isInitialized) player else null)

            // 5. Live Player
            if (::player.isInitialized) player.draw(canvas)

            // 5. World-space FX: flavor text + particles
            DialogueBubbleManager.draw(canvas)
            FlavorTextManager.draw(canvas)
            ParticleManager.draw(canvas)

        }   // ← camera shake scope ends here

        // 6. MERCY_MISS green border flash (screen-space — not shaken)
        if (mercyFlashTimer > 0f) {
            val alpha = ((mercyFlashTimer / mercyFlashDuration) * 200).toInt().coerceIn(0, 200)
            mercyFlashPaint.alpha = alpha
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), mercyFlashPaint)
        }

        // 7. Ambient night/dusk darkness overlay (screen-space)
        if (::entityManager.isInitialized) {
            val ambient = entityManager.biomeManager.ambientAlpha
            if (ambient > 0) {
                ambientOverlayPaint.alpha = ambient
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), ambientOverlayPaint)
            }
        }

        // 8. HUD — always screen-space, never shakes
        if (::hud.isInitialized && ::gameState.isInitialized)
            hud.draw(canvas, gameState)

        // 9. Game Over overlay (DYING: faint, GAME_OVER: full)
        if (runState == RunState.GAME_OVER || runState == RunState.DYING) {
            if (::gameOverScreen.isInitialized && ::gameState.isInitialized) {
                gameOverScreen.draw(
                    canvas          = canvas,
                    score           = gameState.score,
                    distanceM       = gameState.distanceMetres,
                    isNewHighScore  = gameState.isNewHighScore,
                    highScore       = gameState.highScore,
                    mercyHearts     = gameState.mercyHearts,
                    seedsCollected  = gameState.seedsThisRun,
                    restQuote       = currentRestQuote
                )
            }
        }

        // 10. RESTARTING — fade to black
        if (runState == RunState.RESTARTING) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), restartFadePaint)
        }
    }

    private fun prepareFreshRun() {
        if (!::entityManager.isInitialized || !::player.isInitialized || !::gameState.isInitialized) return
        runResetManager.executeReset(gameState, entityManager, player)
        entityManager.seedOpeningSequence()
        ghostRecorder.reset()
        reloadGhost()
        currentRestQuote = "The forest is waiting for a cleaner run."
        LeitmotifManager.playRunStart()
    }

    private fun reloadGhost() {
        ghostPlayer.reset()
        val frames = SaveManager.loadGhostRun(context)
        if (frames.isNotEmpty()) ghostPlayer.load(frames)
    }
}
