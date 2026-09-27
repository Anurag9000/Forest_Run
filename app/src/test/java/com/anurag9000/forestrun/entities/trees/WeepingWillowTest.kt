package com.anurag9000.forestrun.entities.trees

import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.EntityManager
import com.anurag9000.forestrun.engine.GameConstants
import com.anurag9000.forestrun.engine.GameStateManager
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
class WeepingWillowTest {

    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        spriteManager = SpriteManager(context)
    }

    @Test
    fun `willow keeps an explicit duck lane below the curtain`() {
        val willow = willow()
        val player = Player(1920, 1080, spriteManager)
        val gameState = GameStateManager(context)

        val curtainHitbox = rectField(willow, "curtainHitbox")
        val duckLaneRect = rectField(willow, "duckLaneRect")

        player.hitbox.set(
            duckLaneRect.left + 8f,
            duckLaneRect.top + 4f,
            duckLaneRect.right - 8f,
            duckLaneRect.bottom - 4f
        )
        assertEquals(CollisionResult.NONE, willow.onCollision(player, gameState))

        player.hitbox.set(
            curtainHitbox.left + 16f,
            curtainHitbox.top + 12f,
            curtainHitbox.right - 16f,
            curtainHitbox.bottom - 12f
        )
        assertEquals(CollisionResult.HIT, willow.onCollision(player, gameState))

        // The padded curtain edge is a near miss only outside the centre
        // trunk; a wide rectangle crossing the trunk is a genuine HIT.
        player.hitbox.set(
            curtainHitbox.right - 4f,
            curtainHitbox.bottom + 1f,
            curtainHitbox.right + 4f,
            curtainHitbox.bottom + 3f
        )
        val trunk = rectField(willow, "trunkHitbox")
        assertTrue(player.hitbox.top < player.hitbox.bottom)
        assertFalse(RectF.intersects(player.hitbox, curtainHitbox))
        assertFalse(RectF.intersects(player.hitbox, trunk))
        assertFalse(duckLaneRect.contains(player.hitbox))
        assertEquals(CollisionResult.MERCY_MISS, willow.onCollision(player, gameState))

        player.hitbox.set(
            trunk.centerX() - 4f, curtainHitbox.bottom + 1f,
            trunk.centerX() + 4f, curtainHitbox.bottom + 3f
        )
        assertEquals(CollisionResult.HIT, willow.onCollision(player, gameState))
    }

    @Test
    fun `willow encounter bounds enclose trunk and curtain without making duck lane solid`() {
        val willow = willow()
        val player = Player(1920, 1080, spriteManager)
        val gameState = GameStateManager(context)
        val trunk = rectField(willow, "trunkHitbox")
        val curtain = rectField(willow, "curtainHitbox")
        val lane = rectField(willow, "duckLaneRect")

        assertEncloses(willow.hitbox, trunk)
        assertEncloses(willow.hitbox, curtain)
        assertTrue(willow.hitbox.right > trunk.right)

        player.hitbox.set(
            lane.left + 8f,
            lane.top + 4f,
            lane.right - 8f,
            lane.bottom - 4f
        )
        assertTrue(RectF.intersects(player.hitbox, willow.hitbox))
        assertEquals(CollisionResult.NONE, willow.onCollision(player, gameState))
    }

    @Test
    fun `real standing Player meets curtain and real grounded duck fits lane`() {
        for (screenHeight in intArrayOf(720, 1080, 1440)) {
            val groundY = screenHeight * 0.82f
            val willow = WeepingWillow(
                context = context,
                startX = 680f,
                screenHeight = screenHeight.toFloat(),
                groundY = groundY,
                sprite = spriteManager.willowSprite.copy()
            )
            val player = Player(1920, screenHeight, spriteManager, groundYOverride = groundY)
            val state = GameStateManager(context)
            val lane = rectField(willow, "duckLaneRect")
            player.x = lane.centerX() - Player.BASE_WIDTH * 0.5f
            player.update(1f / 60f)
            assertEquals(
                "height=$screenHeight standing must meet the real curtain",
                CollisionResult.HIT, willow.onCollision(player, state)
            )
            player.onDuckPressed()
            player.update(1f / 60f)
            assertEquals(PlayerState.DUCKING, player.state)
            assertTrue(
                "height=$screenHeight real duck does not fit displayed lane",
                lane.contains(
                    player.hitbox.left, player.hitbox.top,
                    player.hitbox.right, player.hitbox.bottom
                )
            )
            assertEquals(
                "height=$screenHeight duck must avoid curtain and trunk",
                CollisionResult.NONE, willow.onCollision(player, state)
            )
        }
    }

    @Test
    fun `real grounded duck traverses the entire moving tree including its trunk`() {
        val dt = 0.016f
        for (screenHeight in intArrayOf(720, 1080, 1440)) {
            val groundY = screenHeight * 0.82f
            for (speed in floatArrayOf(
                GameConstants.BASE_SCROLL_SPEED,
                GameConstants.MAX_SCROLL_SPEED
            )) {
                val tree = WeepingWillow(
                    context = context,
                    startX = 680f,
                    screenHeight = screenHeight.toFloat(),
                    groundY = groundY,
                    sprite = spriteManager.willowSprite.copy()
                )
                val player = Player(
                    1920, screenHeight, spriteManager, groundYOverride = groundY
                )
                val state = GameStateManager(context)
                val manager = EntityManager(
                    context, 1_920f, screenHeight.toFloat(), spriteManager
                )
                manager.activeEntities += tree
                player.onDuckPressed()
                var enteredTrunk = false
                var clearedTree = false
                repeat(180) { frame ->
                    player.update(dt, speed)
                    tree.update(dt, speed)
                    val trunk = rectField(tree, "trunkHitbox")
                    if (RectF.intersects(player.hitbox, trunk)) {
                        enteredTrunk = true
                        assertTrue(
                            "height=$screenHeight speed=$speed frame=$frame " +
                                "the real crouch must fit the highlighted full-width underpass",
                            rectField(tree, "duckLaneRect").contains(
                                player.hitbox.left, player.hitbox.top,
                                player.hitbox.right, player.hitbox.bottom
                            )
                        )
                    }
                    val resolved = manager.checkCollisions(player, state)
                    assertTrue(
                        "height=$screenHeight speed=$speed frame=$frame hit in a taught underpass",
                        resolved?.result != CollisionResult.HIT
                    )
                    if (tree.encounterOutcome != EncounterOutcome.PENDING) {
                        clearedTree = true
                    }
                }
                assertTrue("height=$screenHeight speed=$speed never crossed trunk", enteredTrunk)
                assertTrue("height=$screenHeight speed=$speed did not finish encounter", clearedTree)
                assertTrue(
                    tree.encounterOutcome == EncounterOutcome.CLEAN_PASS ||
                        tree.encounterOutcome == EncounterOutcome.MERCY
                )
                assertEquals(
                    "completion must be rewarded once",
                    1, state.cleanPassesThisRun + state.mercyMissesThisRun
                )
            }
        }
    }

    private fun willow() = WeepingWillow(
        context = context,
        startX = 680f,
        screenHeight = 1080f,
        groundY = 885.6f,
        sprite = spriteManager.willowSprite.copy()
    )

    private fun assertEncloses(outer: RectF, inner: RectF) {
        assertTrue(outer.left <= inner.left)
        assertTrue(outer.top <= inner.top)
        assertTrue(outer.right >= inner.right)
        assertTrue(outer.bottom >= inner.bottom)
    }

    private fun rectField(willow: WeepingWillow, name: String): RectF {
        val field = WeepingWillow::class.java.getDeclaredField(name)
        field.isAccessible = true
        return RectF(field.get(willow) as RectF)
    }
}
