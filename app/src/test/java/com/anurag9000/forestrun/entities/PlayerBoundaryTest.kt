package com.anurag9000.forestrun.entities

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.SpriteManager
import com.anurag9000.forestrun.engine.SweptCoreOverlap
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerBoundaryTest {
    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        spriteManager = SpriteManager(context)
    }

    @Test
    fun `jump hold mapping rejects malformed durations`() {
        listOf(
            -1f,
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY
        ).forEach { hold ->
            assertEquals(Player.MIN_JUMP_FORCE, Player.jumpVelocityForHold(hold), 0f)
        }
        assertEquals(Player.MAX_JUMP_FORCE, Player.jumpVelocityForHold(10f), 0f)
    }

    @Test
    fun `invalid frame deltas do not mutate running Player`() {
        val player = player()
        val initialY = player.y
        val initialHitbox = android.graphics.RectF(player.hitbox)

        listOf(
            0f,
            -1f,
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY
        ).forEach { delta -> player.update(delta) }

        assertEquals(PlayerState.RUNNING, player.state)
        assertEquals(initialY, player.y, 0f)
        assertEquals(0f, player.velocityY, 0f)
        assertEquals(initialHitbox, player.hitbox)
    }

    @Test
    fun `large physics delta is capped to one render-frame budget`() {
        val player = player()
        player.onJumpPressed()
        val initialY = player.y

        player.update(10f)

        assertEquals(-1_650f, player.velocityY, 0.001f)
        assertEquals(initialY - 82.5f, player.y, 0.001f)
        assertEquals(PlayerState.JUMPING, player.state)
    }

    @Test
    fun `valid update repairs poisoned kinematics and timers`() {
        val player = player()
        player.x = Float.NaN
        player.y = Float.NaN
        player.velocityY = Float.NaN
        setFloat(player, "stateTimer", Float.NaN)
        setFloat(player, "apexTimer", Float.NaN)
        setFloat(player, "presentationElapsed", Float.NaN)

        player.update(0.016f, Float.NaN)

        assertEquals(0f, player.x, 0f)
        assertEquals(player.groundY - Player.BASE_HEIGHT, player.y, 0f)
        assertEquals(0f, player.velocityY, 0f)
        assertEquals(0.016f, float(player, "stateTimer"), 0f)
        assertEquals(0f, float(player, "apexTimer"), 0f)
        assertEquals(0.016f, float(player, "presentationElapsed"), 0f)
        assertTrue(player.hitbox.left.isFinite())
        assertTrue(player.hitbox.top.isFinite())
        assertTrue(player.hitbox.right.isFinite())
        assertTrue(player.hitbox.bottom.isFinite())
    }

    @Test
    fun `Bloom presentation rejects non finite scale`() {
        val player = player()

        player.setBloomPowerPresentation(Float.NaN, Int.MAX_VALUE)

        assertEquals(0f, float(player, "bloomPowerScaleBoost"), 0f)
        assertEquals(255, int(player, "bloomPowerAuraAlpha"))
    }

    @Test
    fun `infinite ground override falls back to finite screen ground`() {
        val player = Player(1920, 1080, spriteManager, Float.POSITIVE_INFINITY)

        assertTrue(player.groundY.isFinite())
        assertEquals(1080f * 0.82f, player.groundY, 0.001f)
    }


    @Test
    fun `input stance transitions publish physical collision geometry immediately`() {
        val croucher = player()
        val standingTop = croucher.hitbox.top
        croucher.onDuckPressed()
        assertEquals(PlayerState.DUCKING, croucher.state)
        assertFootAnchoredHitbox(croucher)
        assertTrue(croucher.hitbox.top > standingTop)

        // A frame admitted after the gesture must begin in the duck stance.
        val duckCore = RectF(croucher.hitbox)
        croucher.update(0.05f)
        assertEquals(duckCore, croucher.previousHitbox)
        assertEquals(duckCore, croucher.hitbox)

        croucher.onDuckReleased()
        assertEquals(PlayerState.RUNNING, croucher.state)
        assertFootAnchoredHitbox(croucher)
        assertTrue(croucher.hitbox.top < duckCore.top)
        val releasedCore = RectF(croucher.hitbox)
        croucher.update(0.05f)
        assertEquals(releasedCore, croucher.previousHitbox)

        val jumper = player()
        jumper.onJumpPressed()
        assertEquals(PlayerState.JUMP_START, jumper.state)
        assertFootAnchoredHitbox(jumper)
        val launchCore = RectF(jumper.hitbox)
        jumper.update(0.05f)
        assertEquals(launchCore, jumper.previousHitbox)
    }

    @Test
    fun `preframe duck does not invent a same time collision with low flyer`() {
        val croucher = player()
        val fullHeight = RectF(croucher.hitbox)
        croucher.onDuckPressed()
        val duckAtInput = RectF(croucher.hitbox)
        val flyerTop = fullHeight.top + 2f
        val flyerBottom = duckAtInput.top - 2f
        assertTrue("fixture must pass the old upright body", flyerTop < flyerBottom)
        val beforeBird = RectF(
            croucher.hitbox.right + 30f, flyerTop,
            croucher.hitbox.right + 54f, flyerBottom
        )
        val afterBird = RectF(
            croucher.hitbox.left - 54f, flyerTop,
            croucher.hitbox.left - 30f, flyerBottom
        )

        croucher.update(0.05f)
        assertEquals(duckAtInput, croucher.previousHitbox)
        assertFalse(
            "a duck before physics must not sweep the old upright body into the flyer",
            SweptCoreOverlap.intersects(
                croucher.previousHitbox, croucher.hitbox, beforeBird, afterBird
            )
        )
        assertTrue(
            "the stale upright sample would have manufactured contact",
            SweptCoreOverlap.intersects(
                fullHeight, croucher.hitbox, beforeBird, afterBird
            )
        )
    }

    @Test
    fun `locomotion hitboxes follow rendered feet and crouching stays on the ground`() {
        val jumper = player()
        assertFootAnchoredHitbox(jumper)
        jumper.onJumpPressed()
        jumper.update(1f / 60f)
        assertFootAnchoredHitbox(jumper)
        jumper.onJumpReleased(0.12f)
        jumper.update(1f / 60f)
        assertFootAnchoredHitbox(jumper)

        val croucher = player()
        croucher.onDuckPressed()
        croucher.update(1f / 60f)
        assertEquals(PlayerState.DUCKING, croucher.state)
        assertEquals(croucher.groundY - Player.BASE_HEIGHT, croucher.y, 0.001f)
        assertEquals(croucher.groundY, croucher.y + Player.BASE_HEIGHT, 0.001f)
        assertFootAnchoredHitbox(croucher)
        assertEquals(
            croucher.groundY - Player.HITBOX_INSET,
            croucher.hitbox.bottom,
            0.001f
        )
        croucher.onDuckReleased()
        croucher.update(1f / 60f)
        assertEquals(PlayerState.RUNNING, croucher.state)
        assertFootAnchoredHitbox(croucher)
    }

    private fun assertFootAnchoredHitbox(player: Player) {
        assertEquals(
            player.x + (Player.BASE_WIDTH - player.currentWidth) / 2f +
                Player.HITBOX_INSET,
            player.hitbox.left,
            0.002f
        )
        assertEquals(
            player.y + Player.BASE_HEIGHT - player.currentHeight +
                Player.HITBOX_INSET,
            player.hitbox.top,
            0.002f
        )
        assertEquals(
            player.y + Player.BASE_HEIGHT - Player.HITBOX_INSET,
            player.hitbox.bottom,
            0.002f
        )
        assertTrue(player.hitbox.width() > 0f)
        assertTrue(player.hitbox.height() > 0f)
    }

    private fun player(): Player = Player(1920, 1080, spriteManager)

    private fun float(player: Player, name: String): Float {
        val field = Player::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.getFloat(player)
    }

    private fun int(player: Player, name: String): Int {
        val field = Player::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.getInt(player)
    }

    private fun setFloat(player: Player, name: String, value: Float) {
        val field = Player::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.setFloat(player, value)
    }
}
