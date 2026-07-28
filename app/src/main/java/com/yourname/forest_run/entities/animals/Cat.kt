package com.yourname.forest_run.entities.animals

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.PersistentMemoryManager
import com.yourname.forest_run.engine.ReadabilityProfile
import com.yourname.forest_run.engine.RelationshipArcSystem
import com.yourname.forest_run.engine.RelationshipEncounterTuning
import com.yourname.forest_run.engine.SpriteSheet
import com.yourname.forest_run.engine.SpriteSizing
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.EntityType
import com.yourname.forest_run.entities.Player
import com.yourname.forest_run.systems.FxPreset
import com.yourname.forest_run.systems.ParticleManager
import com.yourname.forest_run.ui.DialogueBubbleManager

class Cat(
    context: Context,
    startX: Float,
    groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {
    private val readability = ReadabilityProfile.entityForGround(EntityType.CAT, groundY)
    private val relationshipTuning: RelationshipEncounterTuning =
        RelationshipArcSystem.encounterTuning(context, EntityType.CAT)
    private val warmBond = RelationshipArcSystem.isWarmBond(context, EntityType.CAT)
    private val repeatFriendHistory =
        RelationshipArcSystem.featuredRepeatFriend(context) == EntityType.CAT ||
            PersistentMemoryManager.getPassCount(context, EntityType.CAT) >= 4
    private val catH = readability.heightPx
    private val catW = SpriteSizing.widthForHeight(sprite, catH, minWidth = readability.minWidthPx)
    private val insetX = catW * readability.hitInsetXRatio
    private val insetY = catH * readability.hitInsetYRatio

    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(54, 255, 214, 236)
        style = Paint.Style.FILL
    }
    private val auraStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 196, 226)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val sharedQuietPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(44, 242, 214, 255)
        style = Paint.Style.FILL
    }
    private val sharedQuietStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(102, 224, 198, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private var playerHasPassed = false
    private var waving = false
    private var waveTimer = 0f
    private var waveStartX = 0f

    init {
        x = startX
        y = groundY - catH
        hitbox.set(x + insetX, y + insetY, x + catW - insetX, y + catH)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        if (!waving) {
            x -= scrollSpeed * deltaTime
        } else {
            x += scrollSpeed * 0.4f * deltaTime
            waveTimer -= deltaTime
            if (waveTimer <= 0f || x > waveStartX + 200f) isActive = false
        }
        hitbox.offsetTo(x + insetX, y + insetY)
        sprite.update(deltaTime)
        if (x < -catW - 20f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        if (!waving) {
            canvas.drawOval(x - 10f, y + catH * 0.18f, x + catW + 10f, y + catH + 4f, auraPaint)
            canvas.drawOval(x - 10f, y + catH * 0.18f, x + catW + 10f, y + catH + 4f, auraStrokePaint)
            if (repeatFriendHistory) {
                canvas.drawOval(x - 18f, y + catH * 0.08f, x + catW + 18f, y + catH + 10f, sharedQuietPaint)
                canvas.drawOval(x - 18f, y + catH * 0.08f, x + catW + 18f, y + catH + 10f, sharedQuietStrokePaint)
            }
        }
        sprite.draw(canvas, RectF(x, y, x + catW, y + catH))
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        if (playerHasPassed) return
        playerHasPassed = true

        gameState.addBonus(
            points = 500 + relationshipTuning.passBonusPoints + if (repeatFriendHistory) 22 else 0,
            seeds = 2 + relationshipTuning.passBonusSeeds + if (repeatFriendHistory) 1 else 0
        )
        if (warmBond || repeatFriendHistory) {
            ParticleManager.emit(FxPreset.SEED_COLLECT, x + catW * 0.5f, y + catH * 0.32f)
        }
        DialogueBubbleManager.spawn(
            text = RelationshipArcSystem.lineFor(context, EntityType.CAT, RelationshipArcSystem.Event.PASS),
            anchorX = x + catW * 0.5f,
            anchorY = y - 12f,
            fillColor = Color.rgb(255, 235, 248),
            borderColor = Color.rgb(150, 80, 130)
        )

        if (gameState.mercyHearts >= 5 && !waving) {
            triggerSpare()
            gameState.recordSpare()
        }
    }

    private fun triggerSpare() {
        waving = true
        waveTimer = 2.5f
        waveStartX = x
        PersistentMemoryManager.recordSpare(context, EntityType.CAT)
        ParticleManager.emit(FxPreset.MERCY_STARS, x + catW * 0.5f, y + catH * 0.38f)
        DialogueBubbleManager.spawn(
            RelationshipArcSystem.lineFor(context, EntityType.CAT, RelationshipArcSystem.Event.SPARE),
            x + catW * 0.5f,
            y - 18f,
            Color.rgb(255, 240, 252),
            Color.rgb(150, 80, 130)
        )
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox)) return CollisionResult.HIT

        val mercyPad = readability.mercyPaddingPx + relationshipTuning.mercyPaddingBonusPx
        val mercy = RectF(
            hitbox.left - mercyPad,
            hitbox.top - mercyPad,
            hitbox.right + mercyPad,
            hitbox.bottom + mercyPad
        )
        return if (RectF.intersects(player.hitbox, mercy)) {
            CollisionResult.MERCY_MISS
        } else {
            CollisionResult.NONE
        }
    }
}
