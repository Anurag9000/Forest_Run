package com.anurag9000.forestrun.entities

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import com.anurag9000.forestrun.engine.GameStateManager
import com.anurag9000.forestrun.engine.SwayComponent

/** Exactly one terminal outcome may be assigned to an entity instance. */
enum class EncounterOutcome {
    PENDING,
    HIT,
    STUMBLE,
    MERCY,
    CLEAN_PASS,
    BLOOM_CONVERTED
}

/** Base class for every obstacle, flora, bird, and animal encounter. */
abstract class Entity(val context: Context) {
    var x: Float = 0f
    var y: Float = 0f
    var velocityX: Float = 0f
    var velocityY: Float = 0f

    var hitbox = RectF()
    var isActive: Boolean = true
    var hasBeenPassed: Boolean = false
    var encounterOutcome: EncounterOutcome = EncounterOutcome.PENDING

    /** Debug/showcase entities must never mutate real relationship history. */
    var shouldRecordPersistence: Boolean = true

    var swayComponent: SwayComponent? = null

    abstract fun update(deltaTime: Float, scrollSpeed: Float)
    abstract fun draw(canvas: Canvas)

    open fun performUniqueAction(player: Player, gameState: GameStateManager) = Unit

    /**
     * Advance telegraphs and player-reactive mechanics once per frame. This is
     * intentionally separate from [onCollision], which must be a pure query.
     */
    open fun updatePlayerInteraction(player: Player, gameState: GameStateManager) = Unit

    /** Apply mechanical or presentation effects only after this entity wins arbitration. */
    open fun onOutcomeSelected(
        result: CollisionResult,
        player: Player,
        gameState: GameStateManager
    ) = Unit

    /** Return the current overlap result without mutating state or emitting presentation. */
    abstract fun onCollision(player: Player, gameState: GameStateManager): CollisionResult
}
