package com.yourname.forest_run.entities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPhysicsTest {
    @Test
    fun `tap maps to minimum jump force`() {
        assertEquals(Player.MIN_JUMP_FORCE, Player.jumpVelocityForHold(0f), 0.0001f)
    }

    @Test
    fun `full hold maps to maximum jump force`() {
        assertEquals(
            Player.MAX_JUMP_FORCE,
            Player.jumpVelocityForHold(Player.MAX_HOLD_DURATION_S),
            0.0001f
        )
    }

    @Test
    fun `jump force scales monotonically with hold duration`() {
        val tap = Player.jumpVelocityForHold(0f)
        val half = Player.jumpVelocityForHold(Player.MAX_HOLD_DURATION_S / 2f)
        val full = Player.jumpVelocityForHold(Player.MAX_HOLD_DURATION_S)

        assertTrue(tap > half)
        assertTrue(half > full)
    }

    @Test
    fun `hold duration is clamped to valid range`() {
        assertEquals(Player.MIN_JUMP_FORCE, Player.jumpVelocityForHold(-1f), 0.0001f)
        assertEquals(Player.MAX_JUMP_FORCE, Player.jumpVelocityForHold(99f), 0.0001f)
    }
}
