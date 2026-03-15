package com.yourname.forest_run.entities.animals

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.PersistentMemoryManager
import com.yourname.forest_run.engine.RelationshipArcSystem
import com.yourname.forest_run.engine.SpriteSizing
import com.yourname.forest_run.engine.SpriteSheet
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.EntityType
import com.yourname.forest_run.entities.Player
import com.yourname.forest_run.ui.DialogueBubbleManager

/**
 * Cat (Phase 11)
 *
 * Behaviour:
 * - Sits stationary on the ground. Tail-flick animation via SpriteSheet.
 * - Player PASSES it cleanly → awardKindnessBonus() (double seeds + 2× multiplier + "Meow?").
 * - MERCY_MISS overlap → flavour text but smaller reward.
 * - Direct HIT → game over (cat hisses).
 * - After 5 passed (spared): cat waves and exits. [Spare event — Phase 17 full handling]
 *
 * Spares / costumes tracked via Phase 18 PersistentMemoryManager.
 * For now the spare counter is per-instance and fires once.
 */
class Cat(
    context: Context,
    startX: Float,
    groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val catH = 82f
    private val catW = SpriteSizing.widthForHeight(sprite, catH, minWidth = 68f)
    private val insetX = catW * 0.14f
    private val insetY = catH * 0.10f
    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(54, 255, 214, 236)
        style = Paint.Style.FILL
    }
    private val auraStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 196, 226)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    // Tracks whether the player has already passed this specific cat instance
    private var playerHasPassed = false
    private var waving = false
    private var waveTimer = 0f

    init {
        x = startX
        y = groundY - catH
        hitbox.set(x + insetX, y + insetY, x + catW - insetX, y + catH)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        if (!waving) {
            x -= scrollSpeed * deltaTime
        } else {
            // Cat trots off screen to the right during Spare event
            x += scrollSpeed * 0.4f * deltaTime
            waveTimer -= deltaTime
            if (waveTimer <= 0f || x > x + 200f) isActive = false
        }
        hitbox.offsetTo(x + insetX, y + insetY)
        sprite.update(deltaTime)
        if (x < -catW - 20f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        if (!waving) {
            canvas.drawOval(x - 10f, y + catH * 0.18f, x + catW + 10f, y + catH + 4f, auraPaint)
            canvas.drawOval(x - 10f, y + catH * 0.18f, x + catW + 10f, y + catH + 4f, auraStrokePaint)
        }
        val drawRect = RectF(x, y, x + catW, y + catH)
        sprite.draw(canvas, drawRect)
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        // Called by EntityManager when player has fully passed (player.hitbox.left > hitbox.right)
        if (!playerHasPassed) {
            playerHasPassed = true
            // Kindness bonus: flat 500 pts + double seeds (removed broken permanent 2x multiplier)
            gameState.addBonus(points = 500, seeds = 2)
            DialogueBubbleManager.spawn(
                text = RelationshipArcSystem.lineFor(context, EntityType.CAT, RelationshipArcSystem.Event.PASS),
                anchorX = x + catW * 0.5f,
                anchorY = y - 12f,
                fillColor = Color.rgb(255, 235, 248),
                borderColor = Color.rgb(150, 80, 130)
            )

            // Check for Spare threshold (5 clean passes for this cat type)
            // Phase 18: PersistentMemoryManager.getSpared(CAT) >= 5
            // For now, use a simple run-level mercy heart check
            if (gameState.mercyHearts >= 5 && !waving) {
                triggerSpare()
                gameState.recordSpare()
            }
        }
    }

    private fun triggerSpare() {
        waving = true
        waveTimer = 2.5f
        PersistentMemoryManager.recordSpare(context, EntityType.CAT)
        DialogueBubbleManager.spawn(
            RelationshipArcSystem.lineFor(context, EntityType.CAT, RelationshipArcSystem.Event.SPARE),
            x + catW * 0.5f,
            y - 18f,
            Color.rgb(255, 240, 252),
            Color.rgb(150, 80, 130)
        )
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox)) {
            DialogueBubbleManager.spawn(
                RelationshipArcSystem.lineFor(context, EntityType.CAT, RelationshipArcSystem.Event.THREAT),
                x + catW * 0.5f,
                y - 14f,
                Color.rgb(255, 226, 226),
                Color.rgb(180, 60, 60)
            )
            return CollisionResult.HIT
        }

        val mercy = RectF(hitbox.left - 12f, hitbox.top - 12f, hitbox.right + 12f, hitbox.bottom + 12f)
        if (RectF.intersects(player.hitbox, mercy)) {
            if (!playerHasPassed) {
                DialogueBubbleManager.spawn("Phew...", player.x + Player.BASE_WIDTH * 0.5f, player.y - 24f, Color.rgb(255, 245, 220), Color.rgb(180, 140, 70))
            }
            return CollisionResult.MERCY_MISS
        }

        return CollisionResult.NONE
    }
}
