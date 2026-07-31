package com.anurag9000.forestrun.engine

import android.graphics.Canvas
import kotlin.math.sin
import kotlin.random.Random

/** Trauma-based screen shake with reduced-motion and Canvas fail-safety. */
object CameraSystem {
    private const val TRAUMA_DECAY = 2.2f
    private const val MAX_OFFSET_X = 22f
    private const val MAX_OFFSET_Y = 14f
    private const val SHAKE_FREQ = 18f
    private const val FULL_CYCLE_RADIANS = 6.28f

    private var trauma = 0f

    var offsetX: Float = 0f
        private set
    var offsetY: Float = 0f
        private set

    private var shakeTime = 0f
    private var phaseX = 0f
    private var phaseY = 0.5f

    fun addTrauma(amount: Float) {
        if (FeedbackSettings.reducedMotion || !amount.isFinite() || amount <= 0f) return
        val current = trauma.takeIf { it.isFinite() && it >= 0f } ?: 0f
        trauma = (current + amount).coerceIn(0f, 1f)
        phaseX = Random.nextFloat() * FULL_CYCLE_RADIANS
        phaseY = Random.nextFloat() * FULL_CYCLE_RADIANS
    }

    internal val traumaForTest: Float
        get() = trauma

    fun reset() {
        trauma = 0f
        offsetX = 0f
        offsetY = 0f
        shakeTime = 0f
        phaseX = 0f
        phaseY = 0.5f
    }

    fun update(deltaTime: Float) {
        if (FeedbackSettings.reducedMotion) {
            reset()
            return
        }
        if (!deltaTime.isFinite() || deltaTime <= 0f) return
        if (!trauma.isFinite() || trauma <= 0f) {
            reset()
            return
        }

        trauma = (trauma - TRAUMA_DECAY * deltaTime).coerceIn(0f, 1f)
        if (trauma <= 0f) {
            offsetX = 0f
            offsetY = 0f
            return
        }

        shakeTime = ((shakeTime.toDouble() + deltaTime.toDouble()) % 1_000_000.0).toFloat()
        val shake = trauma * trauma
        val phase = shakeTime * SHAKE_FREQ * FULL_CYCLE_RADIANS
        offsetX = shake * MAX_OFFSET_X * sin(phase + phaseX)
        offsetY = shake * MAX_OFFSET_Y * sin(phase + phaseY)
        if (!offsetX.isFinite() || !offsetY.isFinite()) {
            reset()
        }
    }

    inline fun applyTo(canvas: Canvas, block: () -> Unit) {
        if (!offsetX.isFinite() || !offsetY.isFinite() ||
            (offsetX == 0f && offsetY == 0f)
        ) {
            block()
            return
        }
        val saveCount = canvas.save()
        try {
            canvas.translate(offsetX, offsetY)
            block()
        } finally {
            canvas.restoreToCount(saveCount)
        }
    }

    fun shakeHit() = addTrauma(0.90f)
    fun shakeHeavyLand() = addTrauma(0.50f)
    fun shakeWolfHowl() = addTrauma(0.35f)
    fun shakeEagle() = addTrauma(0.25f)
    fun shakeMercyMiss() = addTrauma(0.15f)
    fun shakeBloom() = addTrauma(0.32f)
    fun shakeBloomChain(tier: Int) =
        addTrauma((0.12f + tier.coerceIn(0, 3) * 0.05f).coerceAtMost(0.32f))
}
