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
class JacarandaTest {

    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        spriteManager = SpriteManager(context)
    }

    @Test
    fun `jacaranda keeps a readable underside lane below the petal veil`() {
        val jacaranda = jacaranda()
        val player = Player(1920, 1080, spriteManager)
        val gameState = GameStateManager(context)

        val branchHitbox = rectField(jacaranda, "branchHitbox")
        val undersideLaneRect = rectField(jacaranda, "undersideLaneRect")

        player.hitbox.set(
            undersideLaneRect.left + 8f,
            undersideLaneRect.top + 4f,
            undersideLaneRect.right - 8f,
            undersideLaneRect.bottom - 4f
        )
        assertEquals(CollisionResult.NONE, jacaranda.onCollision(player, gameState))

        player.hitbox.set(
            branchHitbox.left + 16f,
            branchHitbox.top + 12f,
            branchHitbox.right - 16f,
            branchHitbox.bottom - 12f
        )
        assertEquals(CollisionResult.HIT, jacaranda.onCollision(player, gameState))

        // A genuine near miss is just outside the branch at the far edge,
        // not a wide strip across the still-solid centre trunk.
        player.hitbox.set(
            branchHitbox.right - 4f,
            branchHitbox.bottom + 1f,
            branchHitbox.right + 4f,
            branchHitbox.bottom + 3f
        )
        val trunk = rectField(jacaranda, "trunkHitbox")
        assertFalse(RectF.intersects(player.hitbox, branchHitbox))
        assertFalse(RectF.intersects(player.hitbox, trunk))
        assertFalse(undersideLaneRect.contains(player.hitbox))
        assertEquals(CollisionResult.MERCY_MISS, jacaranda.onCollision(player, gameState))

        // Being close vertically does not exempt the actual solid trunk.
        player.hitbox.set(
            trunk.centerX() - 4f, branchHitbox.bottom + 1f,
            trunk.centerX() + 4f, branchHitbox.bottom + 3f
        )
        assertEquals(CollisionResult.HIT, jacaranda.onCollision(player, gameState))
    }

    @Test
    fun `jacaranda encounter bounds enclose trunk and branch without filling underside lane`() {
        val jacaranda = jacaranda()
        val player = Player(1920, 1080, spriteManager)
        val gameState = GameStateManager(context)
        val trunk = rectField(jacaranda, "trunkHitbox")
        val branch = rectField(jacaranda, "branchHitbox")
        val lane = rectField(jacaranda, "undersideLaneRect")

        assertEncloses(jacaranda.hitbox, trunk)
        assertEncloses(jacaranda.hitbox, branch)
        assertTrue(jacaranda.hitbox.right > trunk.right)

        player.hitbox.set(
            lane.left + 8f,
            lane.top + 4f,
            lane.right - 8f,
            lane.bottom - 4f
        )
        assertTrue(RectF.intersects(player.hitbox, jacaranda.hitbox))
        assertEquals(CollisionResult.NONE, jacaranda.onCollision(player, gameState))
    }

    @Test
    fun `real standing Player meets branch and real grounded duck fits underside`() {
        for (screenHeight in intArrayOf(720, 1080, 1440)) {
            val groundY = screenHeight * 0.82f
            val jacaranda = Jacaranda(
                context = context,
                startX = 660f,
                screenHeight = screenHeight.toFloat(),
                groundY = groundY,
                sprite = spriteManager.jacarandaSprite.copy()
            )
            val player = Player(1920, screenHeight, spriteManager, groundYOverride = groundY)
            val state = GameStateManager(context)
            val lane = rectField(jacaranda, "undersideLaneRect")
            player.x = lane.centerX() - Player.BASE_WIDTH * 0.5f
            player.update(1f / 60f)
            assertEquals(
                "height=$screenHeight standing must meet the real branch",
                CollisionResult.HIT, jacaranda.onCollision(player, state)
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
                "height=$screenHeight duck must avoid branch and trunk",
                CollisionResult.NONE, jacaranda.onCollision(player, state)
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
                val tree = Jacaranda(
                    context = context,
                    startX = 660f,
                    screenHeight = screenHeight.toFloat(),
                    groundY = groundY,
                    sprite = spriteManager.jacarandaSprite.copy()
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
                            rectField(tree, "undersideLaneRect").contains(
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

    private fun jacaranda() = Jacaranda(
        context = context,
        startX = 660f,
        screenHeight = 1080f,
        groundY = 885.6f,
        sprite = spriteManager.jacarandaSprite.copy()
    )

    private fun assertEncloses(outer: RectF, inner: RectF) {
        assertTrue(outer.left <= inner.left)
        assertTrue(outer.top <= inner.top)
        assertTrue(outer.right >= inner.right)
        assertTrue(outer.bottom >= inner.bottom)
    }

    private fun rectField(jacaranda: Jacaranda, name: String): RectF {
        val field = Jacaranda::class.java.getDeclaredField(name)
        field.isAccessible = true
        return RectF(field.get(jacaranda) as RectF)
    }
}
