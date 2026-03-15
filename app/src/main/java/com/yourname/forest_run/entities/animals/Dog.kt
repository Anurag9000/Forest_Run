package com.yourname.forest_run.entities.animals

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.SfxManager
import com.yourname.forest_run.engine.SpriteSizing
import com.yourname.forest_run.engine.SpriteSheet
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.Player
import com.yourname.forest_run.ui.FlavorTextManager
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

    private val dogH = 68f
    private val dogW = SpriteSizing.widthForHeight(sprite, dogH, minWidth = 56f)
    private val insetX = dogW * 0.13f
    private val insetY = dogH * 0.07f

    // ── Bark Projectile nested class ─────────────────────────────────────
    /**
     * A horizontal shockwave emitted by the Dog on each bark.
     * Travels forward at [PROJECTILE_SPEED] px/s and despawns off-screen left.
     */
    private inner class BarkProjectile(spawnX: Float, spawnY: Float) {
        private val PROJECTILE_SPEED = 500f
        val rect = RectF(spawnX, spawnY - 20f, spawnX + 60f, spawnY)
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
            val m = RectF(rect.left - 12f, rect.top - 12f, rect.right + 12f, rect.bottom + 12f)
            return RectF.intersects(player.hitbox, m)
        }
    }

    private val barkPaint = Paint().apply {
        color  = Color.argb(180, 255, 220, 80)
        style  = Paint.Style.FILL
    }

    // ── State machine ──────────────────────────────────────────────────────
    private enum class DogMode { HAZARD, BUDDY, BUDDY_DASH }
    private var mode = if (isBuddy) DogMode.BUDDY else DogMode.HAZARD

    // Hazard
    private val barkInterval = 1.8f
    private var barkTimer    = barkInterval * 0.5f  // First bark sooner
    private val projectiles  = mutableListOf<BarkProjectile>()

    // Buddy
    private var buddyTimer   = 3f + Random.nextFloat() * 2f
    private var buddyDialogueStep = 0
    private var buddyDialogueTimer = 0f

    private val buddyDialogue = listOf("BORF!", "Hi!!", "See ya!")

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
        if (barkTimer <= 0f) {
            barkTimer = barkInterval
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

    private fun updateBuddy(deltaTime: Float, scrollSpeed: Float) {
        // Buddy mode: drift gently alongside player (doesn't scroll left)
        buddyTimer -= deltaTime

        // Periodic bark dialogue
        buddyDialogueTimer -= deltaTime
        if (buddyDialogueTimer <= 0f && buddyDialogueStep < buddyDialogue.size - 1) {
            FlavorTextManager.spawn(buddyDialogue[buddyDialogueStep], x, y - 35f, Color.rgb(255, 240, 100))
            SfxManager.playBark()
            buddyDialogueStep++
            buddyDialogueTimer = (buddyTimer / buddyDialogue.size).coerceAtLeast(0.8f)
        }

        if (buddyTimer <= 0f) {
            // Final "See ya!" then dash ahead
            FlavorTextManager.spawn("See ya!", x, y - 45f, Color.rgb(255, 240, 100))
            mode = DogMode.BUDDY_DASH
        }
    }

    private fun updateBuddyDash(deltaTime: Float, scrollSpeed: Float) {
        // Dog dashes forward (to the left) and quickly disappears
        x -= (scrollSpeed * 5f) * deltaTime
        if (x < -dogW - 100f) isActive = false
    }

    private fun bark() {
        // Spawn projectile from dog's mouth position (left edge of dog = toward player)
        FlavorTextManager.spawn("BORF!", x, y - 30f, Color.rgb(255, 220, 80))
        projectiles.add(BarkProjectile(x, y + dogH * 0.4f))
        SfxManager.playBark()
    }

    override fun draw(canvas: Canvas) {
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
        val mercy = RectF(hitbox.left - 12f, hitbox.top - 12f, hitbox.right + 12f, hitbox.bottom + 12f)
        if (RectF.intersects(player.hitbox, mercy)) {
            return CollisionResult.MERCY_MISS
        }
        return CollisionResult.NONE
    }
}
