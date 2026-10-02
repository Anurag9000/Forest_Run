package com.anurag9000.forestrun.entities.trees

import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.EntityManager
import com.anurag9000.forestrun.engine.FrameInputAdmission
import com.anurag9000.forestrun.engine.GameConstants
import com.anurag9000.forestrun.engine.GameStateManager
import com.anurag9000.forestrun.engine.RunMode
import com.anurag9000.forestrun.engine.SpriteManager
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.EncounterOutcome
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.entities.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CherryBlossomTest {

    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        spriteManager = SpriteManager(context)
    }

    @Test
    fun `cherry blossom keeps the gust band narrower than the storm veil`() {
        val cherry = cherry()
        val player = Player(1920, 1080, spriteManager)
        val gameState = GameStateManager(context)

        val branchHitbox = rectField(cherry, "branchHitbox")
        val stormVeilRect = rectField(cherry, "stormVeilRect")
        assertTrue(stormVeilRect.width() > branchHitbox.width())

        player.hitbox.set(
            branchHitbox.left + 8f,
            branchHitbox.top + 8f,
            branchHitbox.right - 8f,
            branchHitbox.bottom - 8f
        )
        assertEquals(CollisionResult.HIT, cherry.onCollision(player, gameState))

        player.hitbox.set(
            stormVeilRect.left + 6f,
            stormVeilRect.top + 6f,
            stormVeilRect.right - 6f,
            branchHitbox.top - 2f
        )
        assertEquals(CollisionResult.MERCY_MISS, cherry.onCollision(player, gameState))
    }

    @Test
    fun `legal recovery step cannot tunnel completely through the solid trunk`() {
        val player = Player(1920, 1080, spriteManager)
        val state = GameStateManager(context) { false }
        repeat(3) { state.update(5_000f) }
        assertEquals(GameConstants.MAX_SCROLL_SPEED, state.scrollSpeed, 0f)

        // Obtain the real narrow FALLING collision dimensions instead of
        // assuming the wider grounded RUNNING body can be fully tunneled.
        player.onJumpPressed()
        var guard = 0
        while (player.state != PlayerState.FALLING && guard++ < 40) {
            player.update(FrameInputAdmission.MAX_DELTA_SECONDS, state.scrollSpeed)
        }
        assertEquals(PlayerState.FALLING, player.state)
        val fallingWidth = player.hitbox.width()
        val fallingHeight = player.hitbox.height()

        val probe = cherry()
        val probeTrunk = rectField(probe, "trunkHitbox")
        val probeBranch = rectField(probe, "branchHitbox")
        val movementPx =
            GameConstants.MAX_SCROLL_SPEED * FrameInputAdmission.MAX_DELTA_SECONDS
        assertTrue(
            "fixture requires a physically possible endpoint tunnel",
            fallingWidth + probeTrunk.width() < movementPx
        )

        // Place that actual Player-sized falling body below the branch while
        // remaining inside the trunk's vertical span. This isolates the narrow
        // trunk; the production sweep receives a valid stationary Player sample.
        val bodyLeft = player.hitbox.left
        val bodyTop = probeBranch.bottom + 4f
        assertTrue(bodyTop + fallingHeight < probeTrunk.bottom)
        player.previousHitbox.set(
            bodyLeft, bodyTop, bodyLeft + fallingWidth, bodyTop + fallingHeight
        )
        player.hitbox.set(player.previousHitbox)
        player.hasMotionSample = true

        val trunkOffset = probeTrunk.left - probe.x
        val startX = player.hitbox.right + 3f - trunkOffset
        val cherry = CherryBlossom(
            context = context,
            startX = startX,
            screenHeight = 1080f,
            groundY = 885.6f,
            sprite = spriteManager.cherryBlossomSprite.copy()
        )
        val manager = EntityManager(context, 1_920f, 1_080f, spriteManager)
        manager.activeEntities += cherry

        val before = rectField(cherry, "trunkHitbox")
        assertTrue(before.left > player.hitbox.right)
        assertFalse(RectF.intersects(player.hitbox, before))
        assertFalse(RectF.intersects(player.hitbox, rectField(cherry, "branchHitbox")))

        manager.update(
            deltaTime = FrameInputAdmission.MAX_DELTA_SECONDS,
            gameState = state,
            player = player,
            runMode = RunMode.DEBUG_SCENARIO
        )

        val after = rectField(cherry, "trunkHitbox")
        assertTrue(after.right < player.hitbox.left)
        assertFalse(RectF.intersects(player.hitbox, after))
        assertFalse(
            "branch endpoint must not be the reason this fixture hits",
            RectF.intersects(player.hitbox, rectField(cherry, "branchHitbox"))
        )

        val frame = requireNotNull(manager.checkCollisions(player, state))
        assertEquals(CollisionResult.HIT, frame.result)
        assertEquals(EncounterOutcome.HIT, cherry.encounterOutcome)
        assertEquals(0, state.mercyMissesThisRun)
        assertEquals(0, state.cleanPassesThisRun)
    }

    @Test
    fun `cherry encounter bounds enclose trunk and branch without filling empty lower side`() {
        val cherry = cherry()
        val player = Player(1920, 1080, spriteManager)
        val gameState = GameStateManager(context)
        val trunk = rectField(cherry, "trunkHitbox")
        val branch = rectField(cherry, "branchHitbox")

        assertEncloses(cherry.hitbox, trunk)
        assertEncloses(cherry.hitbox, branch)
        assertTrue(cherry.hitbox.right > trunk.right)

        player.hitbox.set(
            cherry.hitbox.left + 2f,
            cherry.hitbox.bottom - 12f,
            cherry.hitbox.left + 12f,
            cherry.hitbox.bottom - 2f
        )
        assertTrue(RectF.intersects(player.hitbox, cherry.hitbox))
        assertEquals(CollisionResult.NONE, cherry.onCollision(player, gameState))
    }

    private fun cherry() = CherryBlossom(
        context = context,
        startX = 640f,
        screenHeight = 1080f,
        groundY = 885.6f,
        sprite = spriteManager.cherryBlossomSprite.copy()
    )

    private fun assertEncloses(outer: RectF, inner: RectF) {
        assertTrue(outer.left <= inner.left)
        assertTrue(outer.top <= inner.top)
        assertTrue(outer.right >= inner.right)
        assertTrue(outer.bottom >= inner.bottom)
    }

    private fun rectField(cherry: CherryBlossom, name: String): RectF {
        val field = CherryBlossom::class.java.getDeclaredField(name)
        field.isAccessible = true
        return RectF(field.get(cherry) as RectF)
    }
}
