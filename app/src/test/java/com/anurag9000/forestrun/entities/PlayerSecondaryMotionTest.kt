package com.anurag9000.forestrun.entities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSecondaryMotionTest {

    @Test
    fun `bloom motion is more expressive than calm running`() {
        val running = PlayerSecondaryMotion.resolve(
            state = PlayerState.RUNNING,
            velocityY = 0f,
            bodyHeight = Player.BASE_HEIGHT,
            elapsed = 0.12f,
            isInvincible = false
        )
        val bloom = PlayerSecondaryMotion.resolve(
            state = PlayerState.BLOOM,
            velocityY = 0f,
            bodyHeight = Player.BASE_HEIGHT,
            elapsed = 0.12f,
            isInvincible = true
        )

        assertTrue(kotlin.math.abs(bloom.costumeSwingPx) > kotlin.math.abs(running.costumeSwingPx))
        assertTrue(bloom.costumeTrailLiftPx > running.costumeTrailLiftPx)
    }

    @Test
    fun `jumping and falling shift body tilt in opposite directions`() {
        val jumping = PlayerSecondaryMotion.resolve(
            state = PlayerState.JUMPING,
            velocityY = -950f,
            bodyHeight = Player.BASE_HEIGHT,
            elapsed = 0.15f,
            isInvincible = false
        )
        val falling = PlayerSecondaryMotion.resolve(
            state = PlayerState.FALLING,
            velocityY = 900f,
            bodyHeight = Player.BASE_HEIGHT,
            elapsed = 0.15f,
            isInvincible = false
        )

        assertTrue(jumping.bodyTiltDegrees < 0f)
        assertTrue(falling.bodyTiltDegrees > 0f)
        assertTrue(jumping.costumeTrailLiftPx > falling.costumeTrailLiftPx)
    }

    @Test
    fun `every Player state returns finite transforms for malformed physics`() {
        val invalidValues = listOf(
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
            -Float.MAX_VALUE,
            Float.MAX_VALUE
        )

        PlayerState.entries.forEach { state ->
            invalidValues.forEach { invalid ->
                val motion = PlayerSecondaryMotion.resolve(
                    state = state,
                    velocityY = invalid,
                    bodyHeight = invalid,
                    elapsed = invalid,
                    isInvincible = true
                )
                assertFinite(motion)
            }
        }
    }

    @Test
    fun `negative body height and elapsed time normalize to stable zero geometry`() {
        val normalized = PlayerSecondaryMotion.resolve(
            state = PlayerState.RUNNING,
            velocityY = 0f,
            bodyHeight = -100f,
            elapsed = -5f,
            isInvincible = false
        )
        val zero = PlayerSecondaryMotion.resolve(
            state = PlayerState.RUNNING,
            velocityY = 0f,
            bodyHeight = 0f,
            elapsed = 0f,
            isInvincible = false
        )

        assertEquals(zero, normalized)
    }

    @Test
    fun `extreme finite values are bounded and deterministic`() {
        val first = PlayerSecondaryMotion.resolve(
            state = PlayerState.STUMBLE,
            velocityY = Float.MAX_VALUE,
            bodyHeight = Float.MAX_VALUE,
            elapsed = Float.MAX_VALUE,
            isInvincible = true
        )
        val second = PlayerSecondaryMotion.resolve(
            state = PlayerState.STUMBLE,
            velocityY = Float.MAX_VALUE,
            bodyHeight = Float.MAX_VALUE,
            elapsed = Float.MAX_VALUE,
            isInvincible = true
        )

        assertEquals(first, second)
        assertFinite(first)
        assertTrue(kotlin.math.abs(first.bodyLiftPx) <= 10_000f)
        assertTrue(kotlin.math.abs(first.costumeSwingPx) <= 10_000f)
    }

    @Test
    fun `rest state has no secondary transform even when invincible`() {
        val rest = PlayerSecondaryMotion.resolve(
            state = PlayerState.REST,
            velocityY = 500f,
            bodyHeight = Player.BASE_HEIGHT,
            elapsed = 10f,
            isInvincible = true
        )

        assertEquals(0f, rest.bodyTiltDegrees, 0f)
        assertTrue(rest.bodyLiftPx <= 0f)
        assertEquals(0f, rest.costumeSwingPx, 0f)
        assertTrue(rest.costumeTrailLiftPx >= 0f)
    }

    private fun assertFinite(motion: PlayerSecondaryMotionState) {
        assertTrue(motion.bodyTiltDegrees.isFinite())
        assertTrue(motion.bodyLiftPx.isFinite())
        assertTrue(motion.costumeSwingPx.isFinite())
        assertTrue(motion.costumeTrailLiftPx.isFinite())
        assertTrue(motion.headOffsetPx.isFinite())
    }
}
