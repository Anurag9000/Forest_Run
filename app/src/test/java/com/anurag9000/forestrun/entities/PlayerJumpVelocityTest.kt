package com.anurag9000.forestrun.entities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerJumpVelocityTest {
    @Test
    fun `malformed and nonpositive hold durations resolve to minimum jump`() {
        listOf(
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
            -1f,
            0f
        ).forEach { hold ->
            assertEquals(
                Player.MIN_JUMP_FORCE,
                Player.jumpVelocityForHold(hold),
                0f
            )
        }
    }

    @Test
    fun `maximum and longer holds resolve to maximum jump`() {
        listOf(
            Player.MAX_HOLD_DURATION_S,
            Player.MAX_HOLD_DURATION_S * 2f,
            Float.MAX_VALUE
        ).forEach { hold ->
            assertEquals(
                Player.MAX_JUMP_FORCE,
                Player.jumpVelocityForHold(hold),
                0f
            )
        }
    }

    @Test
    fun `finite hold interpolation remains bounded and monotonic`() {
        val holds = listOf(0f, 0.1f, 0.2f, 0.4f, Player.MAX_HOLD_DURATION_S)
        val velocities = holds.map(Player::jumpVelocityForHold)

        assertTrue(velocities.all(Float::isFinite))
        assertTrue(
            velocities.all { velocity ->
                velocity in Player.MAX_JUMP_FORCE..Player.MIN_JUMP_FORCE
            }
        )
        assertTrue(
            velocities.zipWithNext().all { (shorter, longer) ->
                longer <= shorter
            }
        )
    }
}
