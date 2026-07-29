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

    /**
     * Allocation-free equivalent of intersecting [target] with a symmetrically
     * expanded copy of [source]. Invalid padding never creates an encounter.
     */
    protected fun intersectsExpanded(target: RectF, source: RectF, padding: Float): Boolean =
        intersectsExpanded(target, source, padding, padding)

    /** Allocation-free expanded-rectangle probe with independent axis padding. */
    protected fun intersectsExpanded(
        target: RectF,
        source: RectF,
        horizontalPadding: Float,
        verticalPadding: Float
    ): Boolean = intersectsExpanded(
        target = target,
        source = source,
        leftPadding = horizontalPadding,
        topPadding = verticalPadding,
        rightPadding = horizontalPadding,
        bottomPadding = verticalPadding
    )

    /** Allocation-free expanded-rectangle probe with independent per-edge padding. */
    protected fun intersectsExpanded(
        target: RectF,
        source: RectF,
        leftPadding: Float,
        topPadding: Float,
        rightPadding: Float,
        bottomPadding: Float
    ): Boolean {
        if (!leftPadding.isFinite() || !topPadding.isFinite() ||
            !rightPadding.isFinite() || !bottomPadding.isFinite()
        ) return false

        val left = leftPadding.coerceAtLeast(0f)
        val top = topPadding.coerceAtLeast(0f)
        val right = rightPadding.coerceAtLeast(0f)
        val bottom = bottomPadding.coerceAtLeast(0f)
        return target.left < source.right + right &&
            source.left - left < target.right &&
            target.top < source.bottom + bottom &&
            source.top - top < target.bottom
    }

    /** Return the current overlap result without mutating state or emitting presentation. */
    abstract fun onCollision(player: Player, gameState: GameStateManager): CollisionResult
}
