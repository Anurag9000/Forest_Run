package com.yourname.forest_run.entities

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
}
