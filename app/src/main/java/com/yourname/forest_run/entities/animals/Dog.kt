package com.yourname.forest_run.entities.animals

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.ReadabilityProfile
import com.yourname.forest_run.engine.RelationshipEncounterTuning
import com.yourname.forest_run.engine.RelationshipArcSystem
import com.yourname.forest_run.engine.RelationshipStage
import com.yourname.forest_run.engine.SfxManager
import com.yourname.forest_run.engine.SpriteSizing
import com.yourname.forest_run.engine.SpriteSheet
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.EntityType
import com.yourname.forest_run.entities.Player
import com.yourname.forest_run.systems.FxPreset
import com.yourname.forest_run.systems.ParticleManager
import com.yourname.forest_run.ui.DialogueBubbleManager
import kotlin.math.sin
import kotlin.random.Random

/**
 * Dog (Phase 11)
 *
 * Two modes (20% chance of buddy on spawn):
 *
 * HAZARD MODE (80%):
 * - Stands on ground. Barks every [barkInterval] seconds.
 * - Each bark spawns a BarkProjectile — a horizontal shockwave the player must jump over.
 * - Phase 20: Bark SFX cued 1s before entity enters screen.
 *
 * RUNNING BUDDY MODE (20%):
 * - Dog runs alongside the player harmlessly for 3–5s.
 * - Periodic musical bark SFX pulses (Phase 20).
 * - Dog winks (2-frame anim), then dashes forward and despawns.
 * - Dialogue sequence: "BORF!" → "Hi!!" → "See ya!"
 */
class Dog(
    context: Context,
    startX: Float,
    private val groundY: Float,
    private val screenWidth: Float,
    private val sprite: SpriteSheet,
    isBuddy: Boolean = Random.nextFloat() < 0.20f  // 20% chance at spawn
) : Entity(context) {

    private val readability = ReadabilityProfile.entityForGround(EntityType.DOG, groundY)
    private val relationshipTuning: RelationshipEncounterTuning =
        RelationshipArcSystem.encounterTuning(context, EntityType.DOG)
    private val buddyDurationBonusSec = RelationshipArcSystem.dogBuddyDurationBonusSec(context)
    private val relationshipStage = RelationshipArcSystem.stageFor(context, EntityType.DOG)
    private val warmBond = RelationshipArcSystem.isWarmBond(context, EntityType.DOG)
    private val buddyCelebration = relationshipStage.ordinal >= RelationshipStage.TRUST.ordinal && warmBond
    private val dogH = readability.heightPx
    private val dogW = SpriteSizing.widthForHeight(sprite, dogH, minWidth = readability.minWidthPx)
    private val insetX = dogW * readability.hitInsetXRatio
    private val insetY = dogH * readability.hitInsetYRatio

    // ── Bark Projectile nested class ─────────────────────────────────────
    /**
     * A horizontal shockwave emitted by the Dog on each bark.
     * Travels forward at [PROJECTILE_SPEED] px/s and despawns off-screen left.
     */
    private inner class BarkProjectile(spawnX: Float, spawnY: Float) {
        private val PROJECTILE_SPEED = 520f
        val rect = RectF(spawnX, spawnY - 28f, spawnX + 84f, spawnY + 8f)
        var active = true

        fun update(deltaTime: Float, scrollSpeed: Float) {
            // Travels in the same direction as the world scroll
            rect.offset(-(scrollSpeed + PROJECTILE_SPEED) * deltaTime, 0f)
            if (rect.right < -60f) active = false
        }

        fun draw(canvas: Canvas) {
            canvas.drawOval(rect, barkPaint)
        }

        fun collides(player: Player): Boolean = RectF.intersects(player.hitbox, rect)
        fun nearMiss(player: Player): Boolean {
            val mercyPad = readability.mercyPaddingPx + relationshipTuning.mercyPaddingBonusPx
            val m = RectF(rect.left - mercyPad, rect.top - mercyPad, rect.right + mercyPad, rect.bottom + mercyPad)
            return RectF.intersects(player.hitbox, m)
        }
    }

    private val barkPaint = Paint().apply {
        color  = Color.argb(200, 255, 220, 80)
        style  = Paint.Style.FILL
    }
    private val buddyTrailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(104, 255, 234, 152)
        style = Paint.Style.STROKE
        strokeWidth = 16f
        strokeCap = Paint.Cap.ROUND
    }
    private val buddyAuraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(64, 255, 246, 190)
        style = Paint.Style.FILL
    }

    // ── State machine ──────────────────────────────────────────────────────
    private enum class DogMode { HAZARD, BUDDY, BUDDY_DASH }
    private var mode = if (isBuddy) DogMode.BUDDY else DogMode.HAZARD

    // Hazard
    private val barkInterval = 1.8f
    private var barkTimer    = barkInterval * 0.5f  // First bark sooner
    private var barkCharge   = 0f
    private val projectiles  = mutableListOf<BarkProjectile>()

    // Buddy
    private var buddyTimer   = 3f + Random.nextFloat() * 2f + buddyDurationBonusSec
    private var buddyDialogueStep = 0
    private var buddyDialogueTimer = 0f
    private var buddyRewarded = false
    private var buddyTrailPulse = 0f
    private var buddySparkTimer = 0.45f

    private val buddyDialogue = RelationshipArcSystem.dogBuddyDialogue(context)

    init {
        x = startX
        y = groundY - dogH

        if (mode == DogMode.BUDDY) {
            // Start alongside player (run slightly ahead of the player)
            x = screenWidth * 0.25f + 160f
        }

        hitbox.set(x + insetX, y + insetY, x + dogW - insetX, y + dogH)
        // Phase 20: schedule bark SFX 1 s before dog reaches screen edge
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        sprite.update(deltaTime)

        when (mode) {
            DogMode.HAZARD -> updateHazard(deltaTime, scrollSpeed)
            DogMode.BUDDY  -> updateBuddy(deltaTime, scrollSpeed)
            DogMode.BUDDY_DASH -> updateBuddyDash(deltaTime, scrollSpeed)
        }

        hitbox.offsetTo(x + insetX, y + insetY)
        if (x < -dogW - 50f) isActive = false
    }

    private fun updateHazard(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime

        barkTimer -= deltaTime
        barkCharge = (1f - barkTimer / barkInterval).coerceIn(0f, 1f)
        if (barkTimer <= 0f) {
            barkTimer = barkInterval
            barkCharge = 0f
            bark()
        }

        // Update and cull projectiles
        val iter = projectiles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.update(deltaTime, scrollSpeed)
            if (!p.active) iter.remove()
        }
    }

    private fun updateBuddy(deltaTime: Float, @Suppress("UNUSED_PARAMETER") scrollSpeed: Float) {
        // Buddy mode: drift gently alongside player (doesn't scroll left)
        buddyTimer -= deltaTime
        buddyTrailPulse += deltaTime * 5f
        buddySparkTimer -= deltaTime

        // Periodic bark dialogue
        buddyDialogueTimer -= deltaTime
        if (buddyDialogueTimer <= 0f && buddyDialogueStep < buddyDialogue.size - 1) {
            DialogueBubbleManager.spawn(
                buddyDialogue[buddyDialogueStep],
                x + dogW * 0.5f,
                y - 18f,
                Color.rgb(255, 248, 210),
                Color.rgb(170, 120, 45)
            )
            SfxManager.playBark()
            ParticleManager.emit(FxPreset.MERCY_STARS, x + dogW * 0.5f, y + dogH * 0.45f)
            if (buddyCelebration && buddyDialogueStep >= 1) {
                ParticleManager.emit(FxPreset.SEED_COLLECT, x + dogW * 0.5f, y + dogH * 0.28f)
            }
            buddyDialogueStep++
            buddyDialogueTimer = (buddyTimer / buddyDialogue.size).coerceAtLeast(0.8f)
        }

        if (buddyCelebration && buddySparkTimer <= 0f) {
            ParticleManager.emit(FxPreset.MERCY_STARS, x + dogW * 0.18f, y + dogH * 0.36f)
            ParticleManager.emit(FxPreset.MERCY_STARS, x + dogW * 0.82f, y + dogH * 0.52f)
            buddySparkTimer = 0.65f
        }

        if (buddyTimer <= 0f) {
            // Final "See ya!" then dash ahead
            DialogueBubbleManager.spawn(
                buddyDialogue.last(),
                x + dogW * 0.5f,
                y - 20f,
                Color.rgb(255, 248, 210),
                Color.rgb(170, 120, 45)
            )
            if (relationshipStage.ordinal >= RelationshipStage.TRUST.ordinal) {
                ParticleManager.emit(FxPreset.SEED_COLLECT, x + dogW * 0.5f, y + dogH * 0.36f)
                ParticleManager.emit(FxPreset.MERCY_STARS, x + dogW * 0.5f, y + dogH * 0.52f)
            }
            if (buddyCelebration) {
                ParticleManager.emit(FxPreset.SEED_COLLECT, x + dogW * 0.22f, y + dogH * 0.30f)
                ParticleManager.emit(FxPreset.SEED_COLLECT, x + dogW * 0.78f, y + dogH * 0.30f)
                ParticleManager.emit(FxPreset.MERCY_STARS, x + dogW * 0.5f, y + dogH * 0.20f)
            }
            mode = DogMode.BUDDY_DASH
        }
    }

    private fun updateBuddyDash(deltaTime: Float, scrollSpeed: Float) {
        // Dog dashes forward (to the left) and quickly disappears
        buddyTrailPulse += deltaTime * 7f
        x -= (scrollSpeed * 5f) * deltaTime
        if (x < -dogW - 100f) isActive = false
    }

    private fun bark() {
        // Spawn projectile from dog's mouth position (left edge of dog = toward player)
        DialogueBubbleManager.spawn("BORF!", x + dogW * 0.5f, y - 18f, Color.rgb(255, 246, 214), Color.rgb(170, 120, 45))
        projectiles.add(BarkProjectile(x, y + dogH * 0.4f))
        SfxManager.playBark()
    }

    override fun draw(canvas: Canvas) {
        if (mode == DogMode.HAZARD) {
            barkPaint.alpha = (120f + 100f * barkCharge).toInt().coerceIn(0, 255)
            val pad = readability.stagingPaddingPx
            canvas.drawOval(x - pad, y + dogH * 0.18f, x + dogW + pad, y + dogH * 0.82f, barkPaint)
        } else {
            val pulseValue = 0.55f + 0.45f * sin(buddyTrailPulse)
            buddyTrailPaint.alpha = if (buddyCelebration) {
                (128f + 80f * pulseValue).toInt().coerceIn(0, 255)
            } else {
                (82f + 46f * pulseValue).toInt().coerceIn(0, 255)
            }
            buddyAuraPaint.alpha = if (buddyCelebration) {
                (68f + 28f * pulseValue).toInt().coerceIn(0, 255)
            } else {
                (42f + 20f * pulseValue).toInt().coerceIn(0, 255)
            }
            val trailStartX = x - dogW * 0.52f
            val trailY = y + dogH * 0.72f
            canvas.drawLine(trailStartX, trailY, x + dogW * 0.20f, trailY, buddyTrailPaint)
            canvas.drawRoundRect(
                x - 12f,
                y - 6f,
                x + dogW + 12f,
                y + dogH + 6f,
                24f,
                24f,
                buddyAuraPaint
            )
        }
        val drawRect = RectF(x, y, x + dogW, y + dogH)
        sprite.draw(canvas, drawRect)

        // Draw all active bark projectiles
        for (proj in projectiles) {
            proj.draw(canvas)
        }
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        // Buddy mode: dog is harmless
        if (mode != DogMode.HAZARD) return CollisionResult.NONE

        // Check bark projectiles first
        for (proj in projectiles) {
            if (proj.collides(player)) return CollisionResult.HIT
            if (proj.nearMiss(player)) return CollisionResult.MERCY_MISS
        }

        // Check dog body
        if (RectF.intersects(player.hitbox, hitbox)) {
            return CollisionResult.HIT
        }
        val mercyPad = readability.mercyPaddingPx + relationshipTuning.mercyPaddingBonusPx
        val mercy = RectF(hitbox.left - mercyPad, hitbox.top - mercyPad, hitbox.right + mercyPad, hitbox.bottom + mercyPad)
        if (RectF.intersects(player.hitbox, mercy)) {
            return CollisionResult.MERCY_MISS
        }
        return CollisionResult.NONE
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        if (mode == DogMode.HAZARD) {
            gameState.addBonus(
                points = 145 + relationshipTuning.passBonusPoints,
                seeds = 1 + relationshipTuning.passBonusSeeds
            )
            DialogueBubbleManager.spawn(
                RelationshipArcSystem.lineFor(context, EntityType.DOG, RelationshipArcSystem.Event.PASS),
                x + dogW * 0.5f,
                y - 18f,
                Color.rgb(255, 246, 214),
                Color.rgb(170, 120, 45)
            )
            return
        }

        if (!buddyRewarded) {
            buddyRewarded = true
            gameState.addBonus(
                points = 180 + relationshipTuning.passBonusPoints + if (buddyCelebration) 90 else 0,
                seeds = 2 + relationshipTuning.passBonusSeeds + (if (warmBond) 1 else 0) + (if (buddyCelebration) 1 else 0)
            )
            ParticleManager.emit(FxPreset.SEED_COLLECT, x + dogW * 0.5f, y + dogH * 0.3f)
            ParticleManager.emit(FxPreset.MERCY_STARS, x + dogW * 0.5f, y + dogH * 0.48f)
            if (buddyCelebration) {
                ParticleManager.emit(FxPreset.SEED_COLLECT, x + dogW * 0.24f, y + dogH * 0.24f)
                ParticleManager.emit(FxPreset.SEED_COLLECT, x + dogW * 0.76f, y + dogH * 0.24f)
                ParticleManager.emit(FxPreset.MERCY_STARS, x + dogW * 0.5f, y + dogH * 0.16f)
            }
            DialogueBubbleManager.spawn(
                RelationshipArcSystem.lineFor(context, EntityType.DOG, RelationshipArcSystem.Event.SPARE),
                x + dogW * 0.5f,
                y - 20f,
                Color.rgb(255, 248, 210),
                Color.rgb(170, 120, 45)
            )
        }
    }
}
