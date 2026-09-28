package com.anurag9000.forestrun.entities.trees

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.anurag9000.forestrun.engine.FrameInputAdmission
import com.anurag9000.forestrun.engine.GameStateManager
import com.anurag9000.forestrun.engine.PersistentMemoryManager
import com.anurag9000.forestrun.engine.ReadabilityProfile
import com.anurag9000.forestrun.engine.SpriteSheet
import com.anurag9000.forestrun.engine.SweptCoreOverlap
import com.anurag9000.forestrun.engine.SwayComponent
import com.anurag9000.forestrun.engine.TreeEncounterFlavor
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.Entity
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.systems.FxPreset
import com.anurag9000.forestrun.systems.ParticleManager
import com.anurag9000.forestrun.ui.DialogueBubbleManager
import kotlin.random.Random

/**
 * Bamboo — Phase 27: sprite strips rendered for each stalk; gap logic preserved.
 * 5 narrow stalks, each rendered as a narrow slice of the sprite.
 */
class Bamboo(
    context: Context,
    startX: Float,
    private val screenHeight: Float,
    private val groundY: Float,
    private val sprite: SpriteSheet,
    private val random: Random = Random.Default
) : Entity(context) {

    private val readability = ReadabilityProfile.entity(EntityType.BAMBOO, screenHeight)
    private val stalkCount        = 5
    private val gapCount          = stalkCount - 1
    private val featuredGapIndex  = 1
    private val stalkWidth        = readability.secondaryWidthPx
    private val baseGapBetweenStalks = readability.secondarySpacingPx
    private val gapSizes          = FloatArray(gapCount)
    private var totalWidth        = 0f

    private val topHitboxes       = Array(stalkCount) { RectF() }
    private val bottomHitboxes    = Array(stalkCount) { RectF() }
    private val previousTopHitboxes = Array(stalkCount) { RectF() }
    private val previousBottomHitboxes = Array(stalkCount) { RectF() }
    private val gapRects          = Array(gapCount) { RectF() }
    private val topDrawRects      = Array(stalkCount) { RectF() }
    private val bottomDrawRects   = Array(stalkCount) { RectF() }
    private val gapGuidePaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(44, 212, 255, 210)
        style = Paint.Style.FILL
    }
    private val gapGuideStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(126, 178, 236, 184)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val featuredGapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(66, 238, 248, 202)
        style = Paint.Style.FILL
    }
    private val featuredGapBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(204, 248, 252, 228)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private var guidePulse = 0f

    init {
        x = startX
        y = 0f
        swayComponent = SwayComponent(speed = 3.0f, intensity = 4f)
        for (i in 0 until gapCount) {
            gapSizes[i] = if (i == featuredGapIndex) {
                baseGapBetweenStalks * 1.55f
            } else {
                baseGapBetweenStalks * 0.78f
            }
        }
        totalWidth = stalkCount * stalkWidth + gapSizes.sum()
        val gapHeight = BambooGapPlacement.GAP_HEIGHT_PX
        val reachableCentres = BambooGapPlacement.reachableCentreRange(groundY, gapHeight)
        val gapYCenter = reachableCentres.start +
            random.nextFloat() * (reachableCentres.endInclusive - reachableCentres.start)

        updateGeometry(x, gapYCenter, gapHeight)
        updateAggregateHitbox(x)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        // Sample the ten independent physical stalks before scroll/sway.
        // Never sweep the enclosing rectangle through its genuine gap.
        for (i in 0 until stalkCount) {
            previousTopHitboxes[i].set(topHitboxes[i])
            previousBottomHitboxes[i].set(bottomHitboxes[i])
        }
        x -= scrollSpeed * deltaTime
        guidePulse += deltaTime * 3f
        val sway = swayComponent?.getOffset(deltaTime) ?: 0f
        val gapHeight = bottomHitboxes[0].top - topHitboxes[0].bottom
        val gapYCenter = topHitboxes[0].bottom + gapHeight / 2f
        val geometryX = x + sway
        updateGeometry(geometryX, gapYCenter, gapHeight)
        updateAggregateHitbox(geometryX)
        sprite.update(deltaTime)
        if (hitbox.right < -50f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val pulse = 0.6f + 0.4f * kotlin.math.sin(guidePulse)
        gapGuidePaint.alpha = (28f + 26f * pulse).toInt().coerceIn(0, 255)
        gapGuideStrokePaint.alpha = (88f + 36f * pulse).toInt().coerceIn(0, 255)
        featuredGapPaint.alpha = (46f + 34f * pulse).toInt().coerceIn(0, 255)
        featuredGapBorderPaint.alpha = (156f + 42f * pulse).toInt().coerceIn(0, 255)
        for (i in 0 until gapCount) {
            val gapRect = gapRects[i]
            if (gapRect.width() <= 0f) continue
            val radius = readability.stagingPaddingPx
            val fill = if (i == featuredGapIndex) featuredGapPaint else gapGuidePaint
            val border = if (i == featuredGapIndex) featuredGapBorderPaint else gapGuideStrokePaint
            canvas.drawRoundRect(gapRect, radius, radius, fill)
            canvas.drawRoundRect(gapRect, radius, radius, border)
            if (i == featuredGapIndex) {
                val centerX = gapRect.centerX()
                canvas.drawLine(centerX, gapRect.top + 8f, centerX, gapRect.bottom - 8f, featuredGapBorderPaint)
                repeat(3) { marker ->
                    val markerY = gapRect.top + gapRect.height() * ((marker + 1f) / 4f)
                    canvas.drawCircle(centerX, markerY, stalkWidth * 0.18f, featuredGapBorderPaint)
                }
            }
        }
        for (i in 0 until stalkCount) {
            // Top stalk
            topDrawRects[i].set(topHitboxes[i].left, 0f, topHitboxes[i].right, topHitboxes[i].bottom)
            sprite.draw(canvas, topDrawRects[i])
            // Bottom stalk
            bottomDrawRects[i].set(bottomHitboxes[i].left, bottomHitboxes[i].top, bottomHitboxes[i].right, groundY)
            sprite.draw(canvas, bottomDrawRects[i])
        }
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        val encounters = PersistentMemoryManager.getEncounterCount(context, EntityType.BAMBOO)
        val repeatHits = PersistentMemoryManager.getHitCount(context, EntityType.BAMBOO)
        gameState.addBonus(points = 145, seeds = 1)
        ParticleManager.emit(FxPreset.SEED_COLLECT, x + totalWidth * 0.5f, groundY * 0.28f)
        DialogueBubbleManager.spawn(
            TreeEncounterFlavor.bambooPass(encounters, repeatHits),
            x + totalWidth * 0.5f,
            groundY * 0.16f,
            Color.rgb(232, 255, 236),
            Color.rgb(88, 148, 92)
        )
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        var nearMiss = false
        for (i in 0 until stalkCount) {
            if (RectF.intersects(player.hitbox, topHitboxes[i]) ||
                RectF.intersects(player.hitbox, bottomHitboxes[i]) ||
                (hasMotionSample && player.hasMotionSample &&
                    (SweptCoreOverlap.intersects(
                        player.previousHitbox, player.hitbox,
                        previousTopHitboxes[i], topHitboxes[i]
                    ) || SweptCoreOverlap.intersects(
                        player.previousHitbox, player.hitbox,
                        previousBottomHitboxes[i], bottomHitboxes[i]
                    ))
                )
            ) return CollisionResult.HIT
            val mercyPad = readability.mercyPaddingPx * 0.5f
            if (
                intersectsExpanded(
                    player.hitbox,
                    topHitboxes[i],
                    leftPadding = mercyPad,
                    topPadding = 0f,
                    rightPadding = mercyPad,
                    bottomPadding = mercyPad
                ) || intersectsExpanded(
                    player.hitbox,
                    bottomHitboxes[i],
                    leftPadding = mercyPad,
                    topPadding = mercyPad,
                    rightPadding = mercyPad,
                    bottomPadding = 0f
                )
            ) nearMiss = true
        }
        return if (nearMiss) CollisionResult.MERCY_MISS else CollisionResult.NONE
    }

    private fun updateGeometry(offsetX: Float, gapYCenter: Float, gapHeight: Float) {
        var currentX = offsetX
        val gapTop = gapYCenter - gapHeight / 2f
        val gapBottom = gapYCenter + gapHeight / 2f
        for (i in 0 until stalkCount) {
            topHitboxes[i].set(currentX, 0f, currentX + stalkWidth, gapTop)
            bottomHitboxes[i].set(currentX, gapBottom, currentX + stalkWidth, groundY)
            if (i < gapCount) {
                gapRects[i].set(
                    currentX + stalkWidth,
                    gapTop,
                    currentX + stalkWidth + gapSizes[i],
                    gapBottom
                )
                currentX += stalkWidth + gapSizes[i]
            }
        }
    }

    /**
     * Base Entity pass/despawn logic consumes [hitbox]. It must enclose the
     * current swayed stalk geometry rather than the unswayed world anchor, or a
     * clean pass can become terminal while a stalk still extends past Player.
     */
    private fun updateAggregateHitbox(geometryX: Float) {
        hitbox.set(geometryX, 0f, geometryX + totalWidth, groundY)
    }
}

/**
 * Vertical sampling range for the actual Player collision body, not merely
 * for a decorative gap rectangle. The capped 50ms semi-implicit Euler jump
 * loses half an integration step of ideal ballistic rise. Keep an additional
 * safety margin so the lowest sampled opening has a real, reachable passage.
 *
 * A momentary vertical fit is insufficient across all five stalks. Restrict
 * the gap to a measured partial-jump flight band with a full-span regression.
 * Mixed encounter recovery remains a distinct, unproven fairness requirement.
 */
internal object BambooGapPlacement {
    private const val VERTICAL_MARGIN_PX = 20f
    const val GAP_HEIGHT_PX = Player.BASE_HEIGHT * 3f
    private const val LOWEST_FLIGHT_CENTRE_ABOVE_GROUND_PX = 355f
    private const val HIGHEST_FLIGHT_CENTRE_ABOVE_GROUND_PX = 335f

    /** True only when this surface can host the full authored traversal. */
    fun canStageAtGround(groundY: Float): Boolean =
        viableCentreRangeOrNull(groundY, GAP_HEIGHT_PX) != null

    fun reachableCentreRange(
        groundY: Float,
        gapHeight: Float
    ): ClosedFloatingPointRange<Float> =
        viableCentreRangeOrNull(groundY, gapHeight)
            ?: throw IllegalArgumentException(
                "Bamboo requires sufficient usable height for a reachable opening"
            )

    private fun viableCentreRangeOrNull(
        groundY: Float,
        gapHeight: Float
    ): ClosedFloatingPointRange<Float>? {
        if (!groundY.isFinite() || !gapHeight.isFinite() || gapHeight <= 0f) return null
        val maximumAllowed = groundY - gapHeight
        if (maximumAllowed <= gapHeight * 0.5f + VERTICAL_MARGIN_PX) return null

        val upwardSpeed = -Player.MAX_JUMP_FORCE
        val conservativeJumpRise = (
            upwardSpeed * upwardSpeed / (2f * Player.GRAVITY) -
                upwardSpeed * FrameInputAdmission.MAX_DELTA_SECONDS * 0.5f
        ).coerceAtLeast(0f)
        val standingHitboxTop = groundY - Player.BASE_HEIGHT + Player.HITBOX_INSET
        val hitboxHeight = Player.BASE_HEIGHT - Player.HITBOX_INSET * 2f
        val minimumCentre = maxOf(
            gapHeight * 0.5f + VERTICAL_MARGIN_PX,
            standingHitboxTop - conservativeJumpRise +
                hitboxHeight - gapHeight * 0.5f + VERTICAL_MARGIN_PX,
            groundY - LOWEST_FLIGHT_CENTRE_ABOVE_GROUND_PX
        )
        val maximumCentre = minOf(
            maximumAllowed,
            groundY - HIGHEST_FLIGHT_CENTRE_ABOVE_GROUND_PX
        )
        if (minimumCentre > maximumCentre) return null
        return minimumCentre..maximumCentre
    }
}
