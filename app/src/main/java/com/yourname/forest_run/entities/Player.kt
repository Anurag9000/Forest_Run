package com.yourname.forest_run.entities

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.yourname.forest_run.engine.GameConstants
import com.yourname.forest_run.engine.HapticManager
import com.yourname.forest_run.engine.SfxManager
import com.yourname.forest_run.engine.SpriteManager
import com.yourname.forest_run.engine.SpriteSheet
import com.yourname.forest_run.systems.FxPreset
import com.yourname.forest_run.systems.ParticleEmitter
import com.yourname.forest_run.systems.ParticleManager
import com.yourname.forest_run.utils.MathUtils

/**
 * Player locomotion, animation, hitbox, and Bloom presentation.
 *
 * Bloom is deliberately an orthogonal power flag rather than a locomotion
 * state. This lets gravity, jumping, falling, landing, and ducking continue
 * normally while the player is invincible.
 */
class Player(
    screenWidth: Int,
    screenHeight: Int,
    private val spriteManager: SpriteManager,
    groundYOverride: Float = -1f
) {
    companion object {
        const val BASE_WIDTH = 72f
        const val BASE_HEIGHT = 100f

        const val GRAVITY = 3000f
        const val APEX_GRAVITY_FACTOR = 0.60f
        const val APEX_GRAVITY_DURATION_S = 0.20f

        const val MIN_JUMP_FORCE = -900f
        const val MAX_JUMP_FORCE = -1800f
        const val MAX_HOLD_DURATION_S = 0.60f

        const val DUCK_HEIGHT_FACTOR = 0.55f
        const val JUMP_START_DURATION_S = 0.05f
        const val LANDING_DURATION_S = 0.07f
        const val HITBOX_INSET = 10f

        /** Desired upward velocity after release for a given hold duration. */
        fun jumpVelocityForHold(holdSeconds: Float): Float {
            val fraction = (holdSeconds / MAX_HOLD_DURATION_S).coerceIn(0f, 1f)
            return MIN_JUMP_FORCE + (MAX_JUMP_FORCE - MIN_JUMP_FORCE) * fraction
        }
    }

    var x: Float = screenWidth * 0.25f - BASE_WIDTH / 2f
    var y: Float
    var velocityY: Float = 0f

    var groundY: Float = if (groundYOverride > 0f) groundYOverride else screenHeight * 0.82f
        private set

    val isGrounded: Boolean
        get() = state == PlayerState.RUNNING ||
            state == PlayerState.LANDING ||
            state == PlayerState.DUCKING ||
            y >= groundY - BASE_HEIGHT - 0.5f

    @Volatile
    var state: PlayerState = PlayerState.RUNNING
        private set

    private var stateTimer = 0f
    private var apexTimer = 0f
    private var presentationElapsed = 0f

    /** Authoritative Bloom timing lives in GameStateManager. */
    var isInvincible: Boolean = false
        private set

    val hitbox = RectF()

    private val animRun = spriteManager.playerRun.copy()
    private val animJumpStart = spriteManager.playerJumpStart.copy()
    private val animJumping = spriteManager.playerJumping.copy()
    private val animApex = spriteManager.playerApex.copy()
    private val animFalling = spriteManager.playerFalling.copy()
    private val animLanding = spriteManager.playerLanding.copy()
    private val animDuck = spriteManager.playerDuck.copy()
    private val animHit = spriteManager.playerHit.copy()
    private val animDeath = spriteManager.playerDeath.copy()

    private var currentAnimation: SpriteSheet = animRun
    private val drawRect = RectF()
    private val faceManager = FaceManager()
    private val costumeOverlay = CostumeOverlay()

    var costumeStyle: CostumeStyle = CostumeStyle.NONE
        private set

    private var bloomAuraEmitter: ParticleEmitter? = null
    private var bloomTrailEmitter: ParticleEmitter? = null
    private var bloomPowerScaleBoost = 0f
    private var bloomPowerAuraAlpha = 0
    private val bloomPowerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(0, 255, 242, 188)
        style = Paint.Style.FILL
    }

    val scaleX: Float
        get() = when (state) {
            PlayerState.JUMP_START -> 1.25f
            PlayerState.JUMPING -> 0.85f
            PlayerState.FALLING -> 0.90f
            PlayerState.LANDING -> 1.30f
            PlayerState.DUCKING -> 1.15f
            else -> 1.00f
        }

    val scaleY: Float
        get() = when (state) {
            PlayerState.JUMP_START -> 0.80f
            PlayerState.JUMPING -> 1.20f
            PlayerState.FALLING -> 1.15f
            PlayerState.LANDING -> 0.75f
            PlayerState.DUCKING -> DUCK_HEIGHT_FACTOR
            else -> 1.00f
        }

    val currentWidth: Float get() = BASE_WIDTH * scaleX
    val currentHeight: Float get() = BASE_HEIGHT * scaleY

    init {
        y = groundY - BASE_HEIGHT
        updateHitbox()
    }

    /** Starts the jump immediately; release trims it to the requested height. */
    fun onJumpPressed() {
        if (state != PlayerState.RUNNING && state != PlayerState.LANDING) return

        y = groundY - BASE_HEIGHT
        velocityY = MAX_JUMP_FORCE
        transitionTo(PlayerState.JUMP_START)
        SfxManager.playJump()
        HapticManager.shortPulse()
    }

    fun onJumpHeld(@Suppress("UNUSED_PARAMETER") holdSec: Float) {
        // The jump launches at full force for responsiveness. Release applies
        // the variable-height cap, which is the standard platformer model.
    }

    fun onJumpReleased(holdSec: Float) {
        if ((state == PlayerState.JUMP_START || state == PlayerState.JUMPING) && velocityY < 0f) {
            // Never add energy on release. A tap caps the ascent near
            // MIN_JUMP_FORCE; a long hold leaves the current ascent unchanged.
            velocityY = maxOf(velocityY, jumpVelocityForHold(holdSec))
        }
    }

    fun onDuckPressed() {
        when (state) {
            PlayerState.RUNNING,
            PlayerState.LANDING -> transitionTo(PlayerState.DUCKING)

            // Defensive cancellation for gesture arbitration. InputHandler now
            // classifies a swipe before starting a jump, but this prevents a
            // future callback-order regression from making duck impossible.
            PlayerState.JUMP_START -> {
                velocityY = 0f
                y = groundY - BASE_HEIGHT
                transitionTo(PlayerState.DUCKING)
            }

            else -> Unit
        }
    }

    fun onDuckReleased() {
        if (state == PlayerState.DUCKING) {
            y = groundY - BASE_HEIGHT
            transitionTo(PlayerState.RUNNING)
        }
    }

    fun activateBloom() {
        if (isInvincible) return
        isInvincible = true

        val centerX = x + BASE_WIDTH / 2f
        val centerY = y + BASE_HEIGHT / 2f
        bloomAuraEmitter = ParticleManager.addContinuous(FxPreset.BLOOM_AURA.build(centerX, centerY))
        bloomTrailEmitter = ParticleManager.addContinuous(
            FxPreset.BLOOM_TRAIL.build(centerX - BASE_WIDTH * 0.18f, y + BASE_HEIGHT * 0.72f)
        )
        ParticleManager.emit(FxPreset.BLOOM_ACTIVATE, centerX, centerY)
    }

    fun deactivateBloom() {
        isInvincible = false
        bloomPowerScaleBoost = 0f
        bloomPowerAuraAlpha = 0
        stopBloomEmitters()

        // Compatibility with old debug/save code that may still force the
        // deprecated BLOOM locomotion state.
        if (state == PlayerState.BLOOM) transitionTo(PlayerState.RUNNING)
    }

    fun triggerRest() {
        velocityY = 0f
        y = groundY - BASE_HEIGHT
        transitionTo(PlayerState.REST)
        isInvincible = false
        bloomPowerScaleBoost = 0f
        bloomPowerAuraAlpha = 0
        stopBloomEmitters()
        ParticleManager.emit(
            FxPreset.DEATH_EXPLOSION,
            x + BASE_WIDTH / 2f,
            y + BASE_HEIGHT / 2f
        )
    }

    fun triggerStumble() {
        if (state != PlayerState.REST && !isInvincible) {
            transitionTo(PlayerState.STUMBLE)
        }
    }

    fun reset() {
        y = groundY - BASE_HEIGHT
        velocityY = 0f
        isInvincible = false
        presentationElapsed = 0f
        bloomPowerScaleBoost = 0f
        bloomPowerAuraAlpha = 0
        stopBloomEmitters()
        transitionTo(PlayerState.RUNNING)
    }

    fun update(deltaTime: Float, scrollSpeed: Float = 400f) {
        presentationElapsed += deltaTime
        stateTimer += deltaTime

        when (state) {
            PlayerState.REST -> Unit
            PlayerState.DUCKING -> updateDucking()
            PlayerState.STUMBLE -> updateStumble(deltaTime)
            PlayerState.BLOOM -> {
                // Migrate any legacy forced BLOOM state back to locomotion.
                transitionTo(if (isGrounded) PlayerState.RUNNING else PlayerState.FALLING)
                updatePhysics(deltaTime)
            }
            else -> updatePhysics(deltaTime)
        }

        if (isInvincible) syncBloomEffects()
        updateHitbox()

        if (state == PlayerState.RUNNING) {
            animRun.framesPerSec = MathUtils.map(
                scrollSpeed,
                GameConstants.BASE_SCROLL_SPEED,
                GameConstants.MAX_SCROLL_SPEED,
                24f,
                32f
            )
        }

        currentAnimation.update(deltaTime)
        faceManager.update(deltaTime)
        costumeOverlay.update(deltaTime)
    }

    private fun updateStumble(deltaTime: Float) {
        if (currentAnimation.isFinished || stateTimer >= 0.8f) {
            transitionTo(PlayerState.RUNNING)
        }

        if (y < groundY - BASE_HEIGHT) {
            velocityY += GRAVITY * deltaTime
            y += velocityY * deltaTime
        } else {
            y = groundY - BASE_HEIGHT
            velocityY = 0f
        }
    }

    private fun updatePhysics(deltaTime: Float) {
        when (state) {
            PlayerState.JUMP_START,
            PlayerState.JUMPING -> {
                velocityY += GRAVITY * deltaTime
                y += velocityY * deltaTime

                if (velocityY >= 0f) {
                    apexTimer = 0f
                    transitionTo(PlayerState.APEX)
                } else if (state == PlayerState.JUMP_START && stateTimer >= JUMP_START_DURATION_S) {
                    transitionTo(PlayerState.JUMPING)
                }
            }

            PlayerState.APEX -> {
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
                if (stateTimer >= LANDING_DURATION_S) transitionTo(PlayerState.RUNNING)
            }

            PlayerState.RUNNING,
            PlayerState.DUCKING,
            PlayerState.STUMBLE,
            PlayerState.BLOOM,
            PlayerState.REST -> Unit
        }

        if (y > groundY - BASE_HEIGHT) {
            y = groundY - BASE_HEIGHT
            velocityY = 0f
            if (
                state == PlayerState.FALLING ||
                state == PlayerState.APEX ||
                state == PlayerState.JUMPING ||
                state == PlayerState.JUMP_START
            ) {
                SfxManager.playLand()
                transitionTo(PlayerState.LANDING)
            }
        }
    }

    private fun updateDucking() {
        y = groundY - currentHeight
    }

    private fun checkLanding() {
        if (y >= groundY - BASE_HEIGHT) {
            y = groundY - BASE_HEIGHT
            velocityY = 0f
            SfxManager.playLand()
            transitionTo(PlayerState.LANDING)
        }
    }

    private fun syncBloomEffects() {
        val centerX = x + BASE_WIDTH / 2f
        val centerY = y + BASE_HEIGHT / 2f
        bloomAuraEmitter?.let {
            it.x = centerX
            it.y = centerY
        }
        bloomTrailEmitter?.let {
            it.x = centerX - BASE_WIDTH * 0.18f
            it.y = y + BASE_HEIGHT * 0.72f
        }
    }

    private fun stopBloomEmitters() {
        bloomAuraEmitter?.let { ParticleManager.removeContinuous(it) }
        bloomTrailEmitter?.let { ParticleManager.removeContinuous(it) }
        bloomAuraEmitter = null
        bloomTrailEmitter = null
    }

    fun setBloomPowerPresentation(scaleBoost: Float, auraAlpha: Int) {
        bloomPowerScaleBoost = scaleBoost.coerceIn(0f, 0.12f)
        bloomPowerAuraAlpha = auraAlpha.coerceIn(0, 255)
    }

    private fun transitionTo(newState: PlayerState) {
        val footX = x + BASE_WIDTH / 2f
        val footY = y + BASE_HEIGHT
        when (newState) {
            PlayerState.JUMP_START -> ParticleManager.emit(FxPreset.JUMP_DUST, footX, footY)
            PlayerState.LANDING -> ParticleManager.emit(FxPreset.LAND_THUD, footX, footY)
            PlayerState.DUCKING -> ParticleManager.emit(FxPreset.SLIDE_GRASS, footX, footY)
            else -> Unit
        }

        state = newState
        stateTimer = 0f
        currentAnimation = when (newState) {
            PlayerState.RUNNING -> animRun
            PlayerState.JUMP_START -> animJumpStart
            PlayerState.JUMPING -> animJumping
            PlayerState.APEX -> animApex
            PlayerState.FALLING -> animFalling
            PlayerState.LANDING -> animLanding
            PlayerState.DUCKING -> animDuck
            PlayerState.STUMBLE -> animHit
            PlayerState.BLOOM -> animRun
            PlayerState.REST -> animDeath
        }
        currentAnimation.reset()

        if (newState == PlayerState.REST) {
            currentAnimation.setFrame(currentAnimation.frameCount - 1)
        }
    }

    private fun updateHitbox() {
        val left = x + (BASE_WIDTH - currentWidth) / 2f + HITBOX_INSET
        val top = y + (BASE_HEIGHT - currentHeight) / 2f + HITBOX_INSET
        val right = left + currentWidth - HITBOX_INSET * 2f
        val bottom = top + currentHeight - HITBOX_INSET * 2f
        hitbox.set(left, top, right, bottom)
    }

    fun draw(canvas: Canvas) {
        val cx = x + BASE_WIDTH / 2f
        val feetY = y + BASE_HEIGHT
        val bloomScale = if (isInvincible || bloomPowerScaleBoost > 0f) {
            1f + bloomPowerScaleBoost
        } else {
            1f
        }
        val motion = PlayerSecondaryMotion.resolve(
            state = state,
            velocityY = velocityY,
            bodyHeight = BASE_HEIGHT,
            elapsed = presentationElapsed,
            isInvincible = isInvincible
        )
        val yOffset = motion.bodyLiftPx

        if (bloomPowerAuraAlpha > 0 && isInvincible) {
            bloomPowerPaint.alpha = bloomPowerAuraAlpha
            canvas.drawCircle(
                cx,
                y + BASE_HEIGHT * 0.48f,
                BASE_WIDTH * (0.56f + bloomPowerScaleBoost * 1.9f),
                bloomPowerPaint
            )
        }

        canvas.save()
        canvas.rotate(motion.bodyTiltDegrees, cx, feetY)
        canvas.scale(scaleX * bloomScale, scaleY * bloomScale, cx, feetY)
        drawRect.set(x, y + yOffset, x + BASE_WIDTH, y + BASE_HEIGHT + yOffset)
        currentAnimation.draw(canvas, drawRect)
        costumeOverlay.draw(canvas, drawRect, costumeStyle, state, isInvincible, motion)
        faceManager.draw(canvas, drawRect, state, velocityY, isInvincible, motion)
        canvas.restore()
    }

    fun setCostume(style: CostumeStyle) {
        costumeStyle = style
    }
}
