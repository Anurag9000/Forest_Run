package com.yourname.forest_run.entities

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.yourname.forest_run.engine.SpriteManager
import com.yourname.forest_run.engine.SpriteSheet
import com.yourname.forest_run.engine.GameConstants
import com.yourname.forest_run.engine.HapticManager
import com.yourname.forest_run.engine.SfxManager
import com.yourname.forest_run.systems.FxPreset
import com.yourname.forest_run.systems.ParticleEmitter
import com.yourname.forest_run.systems.ParticleManager
import com.yourname.forest_run.utils.MathUtils

/**
 * The player character — owns all physics, state transitions, squash/stretch,
 * and hitbox.  Phase 3 draws a debug coloured rectangle; Phase 6 will replace
 * that with the real sprite sheet.
 *
 * ── Physics overview ────────────────────────────────────────────────────────
 *  • All units are in **pixels per second** (or per second squared).
 *  • [update] must be called every frame with an accurate [deltaTime].
 *  • [groundY] is the pixel Y of the ground surface (top of the floor line).
 *    The player lands so their feet sit exactly on groundY.
 *
 * ── Jump overview ────────────────────────────────────────────────────────────
 *  • Tap  → short hop  ( ~40 % of MAX_JUMP_FORCE )
 *  • Hold → tall jump  ( up to MAX_JUMP_FORCE, proportional to hold duration )
 *  • At apex, gravity is reduced 40 % for [APEX_GRAVITY_DURATION_S] seconds.
 */
class Player(
    screenWidth: Int,
    screenHeight: Int,
    private val spriteManager: SpriteManager,
    groundYOverride: Float = -1f    // -1 = use default (82% of screen); set by ParallaxBackground
) {

    // -----------------------------------------------------------------------
    // Configuration constants
    // -----------------------------------------------------------------------
    companion object {
        // ── Visual size ──────────────────────────────────────────────────
        const val BASE_WIDTH  = 72f   // px  (Phase 6: match sprite frame size)
        const val BASE_HEIGHT = 100f  // px

        // ── Physics ──────────────────────────────────────────────────────
        const val GRAVITY              = 3000f   // px/s²  (standard)
        const val APEX_GRAVITY_FACTOR  = 0.60f   // gravity multiplied by this at apex
        const val APEX_GRAVITY_DURATION_S = 0.20f

        // ── Jump force ───────────────────────────────────────────────────
        const val MIN_JUMP_FORCE      = -900f    // quick tap
        const val MAX_JUMP_FORCE      = -1800f   // full hold
        const val MAX_HOLD_DURATION_S = 0.60f    // hold ceiling

        // ── Duck ─────────────────────────────────────────────────────────
        const val DUCK_HEIGHT_FACTOR  = 0.55f    // height shrinks to 55 %

        // ── Frame budget ─────────────────────────────────────────────────
        /**  Duration (s) of JUMP_START squash before launch. */
        const val JUMP_START_DURATION_S = 0.05f   // ~3 frames @ 60 fps
        /** Duration (s) of LANDING squash before returning to RUNNING. */
        const val LANDING_DURATION_S    = 0.07f   // ~4 frames @ 60 fps

        // ── Hitbox inset (px on each side) ───────────────────────────────
        const val HITBOX_INSET = 10f
    }

    // -----------------------------------------------------------------------
    // Position & velocity
    // -----------------------------------------------------------------------
    var x: Float = 0f
    var y: Float = 0f            // top-left corner of the bounding rect
    var velocityY: Float = 0f

    // Ground Y: feet rest here → y = groundY - BASE_HEIGHT
    var groundY: Float = 0f
        private set

    val isGrounded: Boolean get() = y >= groundY - BASE_HEIGHT - 0.5f

    // -----------------------------------------------------------------------
    // State machine
    // -----------------------------------------------------------------------
    var state: PlayerState = PlayerState.RUNNING
        private set

    private var stateTimer: Float = 0f    // seconds spent in the current transient state
    private var apexTimer:  Float = 0f    // time spent at apex (low-gravity window)

    // -----------------------------------------------------------------------
    // Bloom
    // -----------------------------------------------------------------------
    var isInvincible: Boolean = false
    private var bloomTimer: Float = 0f
    private val BLOOM_DURATION_S = 5f

    // -----------------------------------------------------------------------
    // Hitbox
    // -----------------------------------------------------------------------
    val hitbox = RectF()

    // -----------------------------------------------------------------------
    // Animations (Phase 6)
    // -----------------------------------------------------------------------
    private val animRun       = spriteManager.playerRun.copy()
    private val animJumpStart = spriteManager.playerJumpStart.copy()
    private val animJumping   = spriteManager.playerJumping.copy()
    private val animApex      = spriteManager.playerApex.copy()
    private val animFalling   = spriteManager.playerFalling.copy()
    private val animLanding   = spriteManager.playerLanding.copy()
    private val animDuck      = spriteManager.playerDuck.copy()
    private val animHit       = spriteManager.playerHit.copy()
    private val animDeath     = spriteManager.playerDeath.copy()
    // Bloom uses the running animation but invincible.
    // Rest uses the ducking animation stopped on the last frame.

    private var currentAnimation: SpriteSheet = animRun
    private val drawRect = RectF()

    // -----------------------------------------------------------------------
    // Squash / stretch
    // -----------------------------------------------------------------------
    val scaleX: Float get() = when (state) {
        PlayerState.JUMP_START -> 1.25f
        PlayerState.JUMPING    -> 0.85f
        PlayerState.FALLING    -> 0.90f
        PlayerState.LANDING    -> 1.30f
        PlayerState.DUCKING    -> 1.15f
        else                   -> 1.00f
    }

    val scaleY: Float get() = when (state) {
        PlayerState.JUMP_START -> 0.80f
        PlayerState.JUMPING    -> 1.20f
        PlayerState.FALLING    -> 1.15f
        PlayerState.LANDING    -> 0.75f
        PlayerState.DUCKING    -> DUCK_HEIGHT_FACTOR
        else                   -> 1.00f
    }

    /** Effective width this frame (after scale). */
    val currentWidth:  Float get() = BASE_WIDTH  * scaleX
    /** Effective height this frame (after scale). */
    val currentHeight: Float get() = BASE_HEIGHT * scaleY

    // -----------------------------------------------------------------------
    // Init
    // -----------------------------------------------------------------------
    init {
        // Player runs at 25 % from the left edge
        x        = screenWidth * 0.25f - BASE_WIDTH / 2f
        groundY  = if (groundYOverride > 0f) groundYOverride else screenHeight * 0.82f
        y        = groundY - BASE_HEIGHT
        updateHitbox()
    }

    // -----------------------------------------------------------------------
    // Input interface (called from GameView callbacks)
    // -----------------------------------------------------------------------

    /** Called when the finger first touches the screen. */
    fun onJumpPressed() {
        // Only accept a new jump if we are grounded and not already in a jump
        if (state == PlayerState.RUNNING || state == PlayerState.LANDING) {
            transitionTo(PlayerState.JUMP_START)
        }
    }

    /**
     * Called every frame while the finger is held.
     * We don't need the holdSec here because we commit the force on release.
     */
    fun onJumpHeld(@Suppress("UNUSED_PARAMETER") holdSec: Float) {
        // Physics applied on release; nothing to do each-frame for now.
    }

    /**
     * Commit the jump with [holdSec] seconds of charge.
     * Force is applied immediately at JUMP_START; release just triggers JUMPING.
     */
    fun onJumpReleased(@Suppress("UNUSED_PARAMETER") holdSec: Float) {
        if (state == PlayerState.JUMP_START) {
            transitionTo(PlayerState.JUMPING)
        } else if (state == PlayerState.JUMPING && velocityY < 0f) {
            // "Mario" variable jump: if user lets go while still rising, cut upward velocity by half
            velocityY *= 0.5f
        }
        // Audio and haptics handled in updatePhysics when force is applied
    }

    fun onDuckPressed() {
        if (state == PlayerState.RUNNING || state == PlayerState.LANDING) {
            transitionTo(PlayerState.DUCKING)
        }
    }

    fun onDuckReleased() {
        if (state == PlayerState.DUCKING) {
            transitionTo(PlayerState.RUNNING)
        }
    }

    // -----------------------------------------------------------------------
    // Bloom
    // -----------------------------------------------------------------------
    private var bloomAuraEmitter: ParticleEmitter? = null

    fun activateBloom() {
        isInvincible = true
        bloomTimer   = 0f
        transitionTo(PlayerState.BLOOM)
        // Register continuous aura emitter
        val aura = FxPreset.BLOOM_AURA.build(x + BASE_WIDTH / 2f, y + BASE_HEIGHT / 2f)
        bloomAuraEmitter = ParticleManager.addContinuous(aura)
    }

    // -----------------------------------------------------------------------
    // REST / Game-over
    // -----------------------------------------------------------------------
    fun triggerRest() {
        velocityY = 0f
        y         = groundY - BASE_HEIGHT
        transitionTo(PlayerState.REST)
        isInvincible = false
        // Stop bloom aura if it was active
        bloomAuraEmitter?.let { ParticleManager.removeContinuous(it) }
        bloomAuraEmitter = null
        // DEATH particle burst
        ParticleManager.emit(FxPreset.DEATH_EXPLOSION, x + BASE_WIDTH / 2f, y + BASE_HEIGHT / 2f)
    }

    /** Triggers a non-lethal stumble animation. */
    fun triggerStumble() {
        if (state != PlayerState.REST && state != PlayerState.BLOOM) {
            transitionTo(PlayerState.STUMBLE)
        }
    }

    /** Called on run restart to restore the player to a clean running state. */
    fun reset() {
        y = groundY - BASE_HEIGHT
        velocityY = 0f
        isInvincible = false
        bloomAuraEmitter?.let { ParticleManager.removeContinuous(it) }
        bloomAuraEmitter = null
        transitionTo(PlayerState.RUNNING)
    }

    // -----------------------------------------------------------------------
    // Update (called every game frame)
    // -----------------------------------------------------------------------
    fun update(deltaTime: Float, scrollSpeed: Float = 400f) {
        stateTimer += deltaTime
        when (state) {
            PlayerState.REST    -> { /* frozen */ }
            PlayerState.BLOOM   -> updateBloom(deltaTime)
            PlayerState.DUCKING -> updateDucking(deltaTime)
            PlayerState.STUMBLE -> updateStumble(deltaTime)
            else                -> updatePhysics(deltaTime)
        }
        updateHitbox()
        
        // Velocity Sync: Scale run animation FPS based on scroll speed
        if (state == PlayerState.RUNNING || state == PlayerState.BLOOM) {
            // Base speed ~400 -> 24 fps. Max speed ~800 -> 32 fps
            val targetFps = MathUtils.map(scrollSpeed, GameConstants.BASE_SCROLL_SPEED, GameConstants.MAX_SCROLL_SPEED, 24f, 32f)
            animRun.framesPerSec = targetFps
        }
        
        currentAnimation.update(deltaTime)
    }

    private fun updateStumble(deltaTime: Float) {
        // Character flails while still moving forward with ground
        if (currentAnimation.isFinished || stateTimer >= 0.8f) {
            transitionTo(PlayerState.RUNNING)
        }
        // Basic ground physics (clamps Y)
        if (y < groundY - BASE_HEIGHT) {
             velocityY += GRAVITY * deltaTime
             y += velocityY * deltaTime
        } else {
             y = groundY - BASE_HEIGHT
             velocityY = 0f
        }
    }

    // -----------------------------------------------------------------------
    // Private – state updates
    // -----------------------------------------------------------------------

    private fun updatePhysics(deltaTime: Float) {
        stateTimer += deltaTime

        when (state) {

            PlayerState.JUMP_START -> {
                // Hold the squash on the ground for JUMP_START_DURATION_S
                // If the timer expires, auto-transition to JUMPING and apply MAX jump force.
                if (stateTimer >= JUMP_START_DURATION_S && y >= groundY - BASE_HEIGHT - 1f) {
                    velocityY = MAX_JUMP_FORCE
                    SfxManager.playJump()   // Phase 20
                    HapticManager.shortPulse() // Phase 21
                    transitionTo(PlayerState.JUMPING)
                }
            }

            PlayerState.JUMPING -> {
                // Apply gravity
                velocityY += GRAVITY * deltaTime
                y += velocityY * deltaTime

                // Crossing zero velocity → apex
                if (velocityY >= 0f && state == PlayerState.JUMPING) {
                    apexTimer = 0f
                    transitionTo(PlayerState.APEX)
                }
            }

            PlayerState.APEX -> {
                // Reduced gravity for floaty feel
                apexTimer += deltaTime
                velocityY += GRAVITY * APEX_GRAVITY_FACTOR * deltaTime
                y += velocityY * deltaTime

                if (apexTimer >= APEX_GRAVITY_DURATION_S || velocityY > 100f) {
                    transitionTo(PlayerState.FALLING)
                }
            }

            PlayerState.FALLING -> {
                velocityY += GRAVITY * deltaTime
                y += velocityY * deltaTime
                checkLanding()
            }

            PlayerState.LANDING -> {
                if (stateTimer >= LANDING_DURATION_S) {
                    transitionTo(PlayerState.RUNNING)
                }
            }

            PlayerState.RUNNING -> { /* ground locomotion – no vertical physics */ }

            PlayerState.STUMBLE -> { /* handled in updateStumble */ }

            else -> {}
        }

        // Clamp to ground (safety net for frame overshoots)
        if (y > groundY - BASE_HEIGHT) {
            y         = groundY - BASE_HEIGHT
            velocityY = 0f
            if (state == PlayerState.FALLING || state == PlayerState.APEX) {
                SfxManager.playLand()   // Phase 20
                transitionTo(PlayerState.LANDING)
            }
        }
    }

    private fun updateDucking(deltaTime: Float) {
        // While ducking, the character stays on the ground; no vertical physics.
        y = groundY - currentHeight    // currentHeight uses DUCK_HEIGHT_FACTOR
    }

    private fun updateBloom(deltaTime: Float) {
        bloomTimer += deltaTime
        if (bloomTimer >= BLOOM_DURATION_S) {
            isInvincible = false
            transitionTo(PlayerState.RUNNING)
        }
        // Bloom is grounded (she runs at high speed but physics still apply)
        updatePhysics(deltaTime)
    }

    private fun checkLanding() {
        if (y >= groundY - BASE_HEIGHT) {
            y         = groundY - BASE_HEIGHT
            velocityY = 0f
            transitionTo(PlayerState.LANDING)
        }
    }

    // -----------------------------------------------------------------------
    // Private – helpers
    // -----------------------------------------------------------------------

    private fun transitionTo(newState: PlayerState) {
        // Emit particle FX on certain transitions
        val footX = x + BASE_WIDTH / 2f
        val footY = y + BASE_HEIGHT
        when (newState) {
            PlayerState.JUMP_START -> ParticleManager.emit(FxPreset.JUMP_DUST, footX, footY)
            PlayerState.LANDING    -> ParticleManager.emit(FxPreset.LAND_THUD, footX, footY)
            PlayerState.DUCKING    -> ParticleManager.emit(FxPreset.SLIDE_GRASS, footX, footY)
            PlayerState.BLOOM      -> { /* Aura registered separately in activateBloom() */ }
            else -> { /* No FX */ }
        }

        state      = newState
        stateTimer = 0f

        // Swap to the correct animation and reset it to frame 0
        currentAnimation = when (state) {
            PlayerState.RUNNING    -> animRun
            PlayerState.JUMP_START -> animJumpStart
            PlayerState.JUMPING    -> animJumping
            PlayerState.APEX       -> animApex
            PlayerState.FALLING    -> animFalling
            PlayerState.LANDING    -> animLanding
            PlayerState.DUCKING    -> animDuck
            PlayerState.STUMBLE    -> animHit
            PlayerState.BLOOM      -> animRun       // Bloom uses run animation
            PlayerState.REST       -> animDeath     // Game over sits down (death sprite)
        }
        currentAnimation.reset()

        if (state == PlayerState.REST) {
            // Force it to stay on the final frame of the duck animation
            currentAnimation.setFrame(currentAnimation.frameCount - 1)
        }
    }

    private fun updateHitbox() {
        // Hitbox is centred on the player rect with HITBOX_INSET on all sides
        val left   = x + (BASE_WIDTH  - currentWidth)  / 2f + HITBOX_INSET
        val top    = y + (BASE_HEIGHT - currentHeight) / 2f + HITBOX_INSET
        val right  = left + currentWidth  - HITBOX_INSET * 2f
        val bottom = top  + currentHeight - HITBOX_INSET * 2f
        hitbox.set(left, top, right, bottom)
    }

    // -----------------------------------------------------------------------
    // Draw (Phase 6: animated sprites via SpriteSheet)
    // -----------------------------------------------------------------------

    // Paint pool — none needed for debug artifacts (removed in Phase 27)

    fun draw(canvas: Canvas) {
        val cx = x + BASE_WIDTH  / 2f   // horizontal centre
        val fy = y + BASE_HEIGHT         // feet Y (squash/stretch pivot)

        // Y-Offsetting logic: "The Passing Position (Frame 12 and 36): Body rises slightly (+8px) compared to contact."
        var yOffset = 0f
        if ((state == PlayerState.RUNNING || state == PlayerState.BLOOM) && 
            (currentAnimation.currentFrame == 11 || currentAnimation.currentFrame == 35)) {
             yOffset = -8f // Up is negative Y in Android Canvas
        }

        canvas.save()
        canvas.scale(scaleX, scaleY, cx, fy)
        drawRect.set(x, y + yOffset, x + BASE_WIDTH, y + BASE_HEIGHT + yOffset)
        currentAnimation.draw(canvas, drawRect)
        canvas.restore()
    }
}
