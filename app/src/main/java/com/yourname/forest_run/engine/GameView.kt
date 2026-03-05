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
import com.yourname.forest_run.ui.FlavorTextManager
import com.yourname.forest_run.ui.HUD

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

    // -----------------------------------------------------------------------
    // Engine
    // -----------------------------------------------------------------------
    private var gameThread: GameThread = GameThread(holder, this)

    // -----------------------------------------------------------------------
    // Input
    // -----------------------------------------------------------------------
    val inputHandler = InputHandler()

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
    // -----------------------------------------------------------------------
    private lateinit var gameState: GameStateManager
    private lateinit var hud: HUD
    private lateinit var spriteManager: SpriteManager
    private lateinit var entityManager: EntityManager

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

    // -----------------------------------------------------------------------
    // FPS tracking
    // -----------------------------------------------------------------------
    private var fpsFrameCount = 0
    private var fpsElapsed    = 0f
    private var currentFps    = 0

    // -----------------------------------------------------------------------
    // Debug: last few input events (ring buffer of 5)
    // -----------------------------------------------------------------------
    private val inputLog = ArrayDeque<String>(6)

    // -----------------------------------------------------------------------
    // Paint objects – created ONCE, never inside draw()
    // -----------------------------------------------------------------------
    private val bgPaint = Paint().apply {
        color = Color.BLACK; style = Paint.Style.FILL
    }

    private val fpsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 30f; typeface = Typeface.MONOSPACE
    }

    private val fpsLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 40, 220, 100); textSize = 30f; typeface = Typeface.MONOSPACE
    }

    // Debug panel background
    private val debugBgPaint = Paint().apply {
        color = Color.argb(160, 0, 0, 0); style = Paint.Style.FILL
    }

    // Debug panel text
    private val debugTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 26f; typeface = Typeface.MONOSPACE
    }

    // Jump indicator (yellow box)
    private val jumpBoxPaint = Paint().apply {
        color = Color.argb(200, 255, 220, 0); style = Paint.Style.FILL
    }

    // Duck indicator (cyan box)
    private val duckBoxPaint = Paint().apply {
        color = Color.argb(200, 0, 220, 255); style = Paint.Style.FILL
    }

    // Neutral indicator box
    private val neutralBoxPaint = Paint().apply {
        color = Color.argb(120, 80, 80, 80); style = Paint.Style.FILL
    }

    private val indicatorRect = RectF()

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
        setOnTouchListener(inputHandler)
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
        if (::gameState.isInitialized) gameState.save()   // persist high score
    }

    fun resume() {
        // Re-create thread (Java threads can't be restarted after stop)
        gameThread = GameThread(holder, this)
        // Thread starts when surfaceCreated fires again (or immediately if surface exists)
        if (holder.surface.isValid) {
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
        // Phase 2: logging only (runs regardless of player init order)
        inputHandler.onJumpPressed  = { Log.d(TAG, "INPUT → JUMP PRESSED");  addInputLog("▲ Jump pressed") }
        inputHandler.onJumpHeld     = { _ -> /* logged in wirePlayerToInput */ }
        inputHandler.onJumpReleased = { holdSec ->
            val type = if (holdSec < 0.12f) "TAP" else "HOLD(${String.format("%.2f", holdSec)}s)"
            Log.d(TAG, "INPUT → JUMP RELEASED [$type]")
            addInputLog("▲ Jump $type")
        }
        inputHandler.onDuckPressed  = { Log.d(TAG, "INPUT → DUCK");     addInputLog("▼ Duck") }
        inputHandler.onDuckReleased = { Log.d(TAG, "INPUT → DUCK END"); addInputLog("▼ Duck end") }
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

    private fun addInputLog(msg: String) {
        if (inputLog.size >= 5) inputLog.removeFirst()
        inputLog.addLast(msg)
    }

    // -----------------------------------------------------------------------
    // Game loop – called by GameThread
    // -----------------------------------------------------------------------

    fun update(deltaTime: Float) {
        // FPS bookkeeping
        fpsFrameCount++
        fpsElapsed += deltaTime
        if (fpsElapsed >= 1f) {
            currentFps    = fpsFrameCount
            fpsFrameCount = 0
            fpsElapsed   -= 1f
        }

        // Input tick
        inputHandler.tick(deltaTime)

        if (!::gameState.isInitialized) return

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
        player.update(deltaTime)

        // Phase 12: EntityManager update (spawn, scroll, pass-detection)
        if (::entityManager.isInitialized) {
            entityManager.update(deltaTime, gameState, player)

            // Collision loop
            val collision = entityManager.checkCollisions(player, gameState)
            if (collision != null) {
                when (collision.result) {
                    CollisionResult.HIT -> {
                        // Force player into REST state (game over for this run)
                        player.triggerRest()
                        // Phase 15: triggerShake(8f, 0.5f) goes here
                    }
                    CollisionResult.MERCY_MISS -> {
                        // Green border flash
                        mercyFlashTimer = mercyFlashDuration
                    }
                    CollisionResult.NONE -> { /* handled above, shouldn't reach here */ }
                }
            }

            // Tick down mercy flash
            if (mercyFlashTimer > 0f) mercyFlashTimer -= deltaTime
        }

        // Flavor text float animation
        FlavorTextManager.update(deltaTime)
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        // 1. Black fill (clean slate every frame)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 2. Parallax background
        if (::parallaxBackground.isInitialized) parallaxBackground.draw(canvas)

        // 3. Entities (behind player, above background)
        if (::entityManager.isInitialized) entityManager.draw(canvas)

        // 4. Player (above entities)
        if (::player.isInitialized) player.draw(canvas)

        // 5. Floating flavor text
        FlavorTextManager.draw(canvas)

        // 6. MERCY_MISS green border flash
        if (mercyFlashTimer > 0f) {
            val alpha = ((mercyFlashTimer / mercyFlashDuration) * 200).toInt().coerceIn(0, 200)
            mercyFlashPaint.alpha = alpha
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), mercyFlashPaint)
        }

        // 6b. Ambient night/dusk darkness overlay (Phase 13)
        if (::entityManager.isInitialized) {
            val ambient = entityManager.biomeManager.ambientAlpha
            if (ambient > 0) {
                ambientOverlayPaint.alpha = ambient
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), ambientOverlayPaint)
            }
        }

        // 7. HUD (always above gameplay, above all overlays)
        if (::hud.isInitialized && ::gameState.isInitialized)
            hud.draw(canvas, gameState)

        // 8. Debug overlays (always topmost)
        drawFps(canvas)
        drawInputDebugPanel(canvas)
        drawInputStateIndicator(canvas)
    }

    // -----------------------------------------------------------------------
    // Private draw helpers
    // -----------------------------------------------------------------------

    private fun drawFps(canvas: Canvas) {
        val label = "FPS: "
        val x = 24f; val y = 52f
        canvas.drawText(label, x, y, fpsLabelPaint)
        canvas.drawText(currentFps.toString(), x + fpsLabelPaint.measureText(label), y, fpsPaint)
    }

    /**
     * Draws the last 5 input events as scrolling log in bottom-left.
     * Shows gesture string and hold duration live.
     */
    private fun drawInputDebugPanel(canvas: Canvas) {
        val panelW = 420f
        val lineH  = 34f
        val lines  = inputLog.size + 2  // log lines + header + live status
        val panelH = lines * lineH + 16f

        val left   = 16f
        val bottom = height.toFloat() - 16f
        val top    = bottom - panelH

        // Panel background
        canvas.drawRoundRect(left, top, left + panelW, bottom, 8f, 8f, debugBgPaint)

        var ty = top + lineH
        debugTextPaint.color = Color.argb(200, 100, 255, 150)
        canvas.drawText("── INPUT DEBUG ──", left + 12f, ty, debugTextPaint)
        debugTextPaint.color = Color.WHITE

        ty += lineH
        // Live status line: show player state if available, else input state
        val playerStateStr = if (::player.isInitialized) "[${player.state.name}]" else ""
        val liveStatus = when {
            inputHandler.isDucking      -> "▼ DUCKING $playerStateStr"
            inputHandler.isChargingJump -> "▲ CHARGING ${String.format("%.2f", inputHandler.holdDuration)}s $playerStateStr"
            else                        -> "· idle $playerStateStr"
        }
        debugTextPaint.color = if (inputHandler.isDucking) Color.CYAN
                               else if (inputHandler.isChargingJump) Color.YELLOW
                               else Color.GRAY
        canvas.drawText(liveStatus, left + 12f, ty, debugTextPaint)
        debugTextPaint.color = Color.WHITE
        ty += 4f

        for (entry in inputLog) {
            ty += lineH
            canvas.drawText(entry, left + 12f, ty, debugTextPaint)
        }
    }

    /**
     * Draws a coloured square in bottom-right showing current input state at a glance.
     * YELLOW = jumping, CYAN = ducking, GREY = idle.
     */
    private fun drawInputStateIndicator(canvas: Canvas) {
        val size  = 80f
        val right  = width.toFloat()  - 24f
        val bottom = height.toFloat() - 24f
        indicatorRect.set(right - size, bottom - size, right, bottom)

        val paint = when {
            inputHandler.isChargingJump -> jumpBoxPaint
            inputHandler.isDucking      -> duckBoxPaint
            else                        -> neutralBoxPaint
        }
        canvas.drawRoundRect(indicatorRect, 12f, 12f, paint)

        val label = when {
            inputHandler.isChargingJump -> "▲"
            inputHandler.isDucking      -> "▼"
            else                        -> "·"
        }
        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 40f; typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(label, indicatorRect.centerX(), indicatorRect.centerY() + 14f, lp)
    }
}
