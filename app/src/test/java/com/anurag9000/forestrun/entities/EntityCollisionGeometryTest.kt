package com.anurag9000.forestrun.entities

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.GameStateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EntityCollisionGeometryTest {
    private class ProbeEntity(context: Context) : Entity(context) {
        override fun update(deltaTime: Float, scrollSpeed: Float) = Unit
        override fun draw(canvas: Canvas) = Unit
        override fun onCollision(player: Player, gameState: GameStateManager) = CollisionResult.NONE

        fun intersects(target: RectF, source: RectF, padding: Float): Boolean =
            intersectsExpanded(target, source, padding)

        fun intersects(
            target: RectF,
            source: RectF,
            horizontalPadding: Float,
            verticalPadding: Float
        ): Boolean = intersectsExpanded(target, source, horizontalPadding, verticalPadding)

        fun intersects(
            target: RectF,
            source: RectF,
            leftPadding: Float,
            topPadding: Float,
            rightPadding: Float,
            bottomPadding: Float
        ): Boolean = intersectsExpanded(
            target,
            source,
            leftPadding,
            topPadding,
            rightPadding,
            bottomPadding
        )
    }

    private val probe = ProbeEntity(ApplicationProvider.getApplicationContext())
    private val source = RectF(20f, 20f, 40f, 40f)

    @Test
    fun `zero padding preserves strict RectF overlap semantics`() {
        assertTrue(probe.intersects(RectF(39f, 25f, 45f, 35f), source, 0f))
        assertFalse(probe.intersects(RectF(40f, 25f, 45f, 35f), source, 0f))
    }

    @Test
    fun `symmetric mercy padding admits a near miss without allocation`() {
        val nearMiss = RectF(44f, 25f, 50f, 35f)
        assertFalse(probe.intersects(nearMiss, source, 0f))
        assertTrue(probe.intersects(nearMiss, source, 5f))
    }

    @Test
    fun `expanded edge contact remains non intersecting`() {
        assertFalse(probe.intersects(RectF(45f, 25f, 50f, 35f), source, 5f))
    }

    @Test
    fun `negative padding is clamped and non finite padding is rejected`() {
        val nearMiss = RectF(41f, 25f, 45f, 35f)
        assertFalse(probe.intersects(nearMiss, source, -10f))
        assertFalse(probe.intersects(nearMiss, source, Float.NaN))
        assertFalse(probe.intersects(nearMiss, source, Float.POSITIVE_INFINITY))
    }

    @Test
    fun `axis expansion preserves Cherry gust geometry`() {
        val verticalNearMiss = RectF(25f, 12f, 35f, 18f)
        assertFalse(probe.intersects(verticalNearMiss, source, 3f, 1f))
        assertTrue(probe.intersects(verticalNearMiss, source, 3f, 3f))
    }

    @Test
    fun `per edge helper is equivalent to an explicitly expanded RectF`() {
        val targets = listOf(
            RectF(10f, 10f, 18f, 18f),
            RectF(15f, 24f, 21f, 32f),
            RectF(39f, 25f, 48f, 35f),
            RectF(24f, 40f, 36f, 48f),
            RectF(45f, 45f, 50f, 50f)
        )
        val paddings = listOf(
            floatArrayOf(0f, 0f, 0f, 0f),
            floatArrayOf(5f, 0f, 2f, 8f),
            floatArrayOf(1.5f, 7f, 9f, 0.5f)
        )

        for (target in targets) {
            for (padding in paddings) {
                val expanded = RectF(
                    source.left - padding[0],
                    source.top - padding[1],
                    source.right + padding[2],
                    source.bottom + padding[3]
                )
                assertEquals(
                    RectF.intersects(target, expanded),
                    probe.intersects(
                        target,
                        source,
                        leftPadding = padding[0],
                        topPadding = padding[1],
                        rightPadding = padding[2],
                        bottomPadding = padding[3]
                    )
                )
            }
        }
    }
}
