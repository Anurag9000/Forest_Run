package com.anurag9000.forestrun.entities.animals

import android.content.Context
import android.graphics.RectF
import com.anurag9000.forestrun.engine.EntityManager
import com.anurag9000.forestrun.entities.EncounterOutcome
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.GameStateManager
import com.anurag9000.forestrun.engine.SpriteManager
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HedgehogTest {

    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        spriteManager = SpriteManager(context)
    }

    @Test
    fun `body contact during warning remains nonlethal stumble rather than mercy`() {
        val hedgehog = newHedgehog()
        val gameState = GameStateManager(context)
        val player = Player(1920, 1080, spriteManager)
        player.hitbox.set(hedgehog.hitbox)
        hedgehog.updatePlayerInteraction(player, gameState)

        assertTrue(booleanField(hedgehog, "warned"))
        assertFalse(booleanField(hedgehog, "armed"))
        assertEquals(CollisionResult.STUMBLE, hedgehog.onCollision(player, gameState))

        // The warning expiry does not change the physical contact contract.
        hedgehog.update(0.25f, 0f)
        assertTrue(booleanField(hedgehog, "armed"))
        assertEquals(CollisionResult.STUMBLE, hedgehog.onCollision(player, gameState))
    }

    @Test
    fun `warning body contact resolves once and cannot later earn mercy or clean pass`() {
        val hedgehog = newHedgehog()
        hedgehog.shouldRecordPersistence = false
        val gameState = GameStateManager(context)
        val player = Player(1920, 1080, spriteManager)
        val manager = EntityManager(context, 1920f, 1080f, spriteManager)
        manager.activeEntities.add(hedgehog)

        player.hitbox.set(hedgehog.hitbox)
        hedgehog.updatePlayerInteraction(player, gameState)
        val frame = requireNotNull(manager.checkCollisions(player, gameState))
        assertEquals(CollisionResult.STUMBLE, frame.result)
        assertEquals(EncounterOutcome.STUMBLE, hedgehog.encounterOutcome)
        assertEquals(0.5f, gameState.speedDebuffMultiplier, 0f)
        assertEquals(0, gameState.mercyHearts)

        player.hitbox.set(hedgehog.hitbox.right + 1f, 0f, hedgehog.hitbox.right + 21f, 20f)
        assertEquals(null, manager.checkCollisions(player, gameState))
        assertEquals(EncounterOutcome.STUMBLE, hedgehog.encounterOutcome)
        assertEquals(0, gameState.mercyHearts)
        assertEquals(0, gameState.cleanPassesThisRun)
    }

    @Test
    fun `untouched outer band stays provisional until safe passage`() {
        val hedgehog = newHedgehog()
        hedgehog.shouldRecordPersistence = false
        val gameState = GameStateManager(context)
        val player = Player(1920, 1080, spriteManager)
        val manager = EntityManager(context, 1920f, 1080f, spriteManager)
        manager.activeEntities.add(hedgehog)

        val body = RectF(hedgehog.hitbox)
        player.hitbox.set(body.left, body.top - 8f, body.right, body.top - 2f)
        hedgehog.updatePlayerInteraction(player, gameState)
        assertFalse(RectF.intersects(player.hitbox, body))
        assertEquals(CollisionResult.MERCY_MISS, hedgehog.onCollision(player, gameState))
        assertEquals(null, manager.checkCollisions(player, gameState))
        assertEquals(EncounterOutcome.PENDING, hedgehog.encounterOutcome)
        assertEquals(0, gameState.mercyHearts)

        player.hitbox.set(body.right + 1f, 0f, body.right + 21f, 20f)
        assertEquals(CollisionResult.MERCY_MISS, manager.checkCollisions(player, gameState)?.result)
        assertEquals(EncounterOutcome.MERCY, hedgehog.encounterOutcome)
        assertEquals(1, gameState.mercyHearts)
        assertEquals(null, manager.checkCollisions(player, gameState))
        assertEquals(1, gameState.mercyHearts)
    }

    @Test
    fun `provisional outer band never masks later real body contact`() {
        val hedgehog = newHedgehog()
        hedgehog.shouldRecordPersistence = false
        val gameState = GameStateManager(context)
        val player = Player(1920, 1080, spriteManager)
        val manager = EntityManager(context, 1920f, 1080f, spriteManager)
        manager.activeEntities.add(hedgehog)

        val body = RectF(hedgehog.hitbox)
        player.hitbox.set(body.left, body.top - 8f, body.right, body.top - 2f)
        assertEquals(null, manager.checkCollisions(player, gameState))
        player.hitbox.set(body)
        assertEquals(CollisionResult.STUMBLE, manager.checkCollisions(player, gameState)?.result)
        assertEquals(EncounterOutcome.STUMBLE, hedgehog.encounterOutcome)
        assertEquals(0, gameState.mercyHearts)
    }

    @Test
    fun `hedgehog clear read after warning gives stronger pass reward`() {
        val baselineHedgehog = Hedgehog(
            context = context,
            startX = 520f,
            groundY = 885.6f,
            sprite = spriteManager.hedgehogSprite.copy()
        )
        val clearedHedgehog = Hedgehog(
            context = context,
            startX = 520f,
            groundY = 885.6f,
            sprite = spriteManager.hedgehogSprite.copy()
        )
        val baselineState = GameStateManager(context)
        val clearedState = GameStateManager(context)
        val player = Player(1920, 1080, spriteManager)

        setBooleanField(clearedHedgehog, "warned", true)

        baselineHedgehog.performUniqueAction(player, baselineState)
        clearedHedgehog.performUniqueAction(player, clearedState)

        assertTrue(clearedState.score > baselineState.score)
        assertTrue(clearedState.seedsThisRun > baselineState.seedsThisRun)
    }

    private fun newHedgehog() = Hedgehog(
        context = context,
        startX = 520f,
        groundY = 885.6f,
        sprite = spriteManager.hedgehogSprite.copy()
    )

    private fun booleanField(hedgehog: Hedgehog, name: String): Boolean {
        val field = Hedgehog::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.getBoolean(hedgehog)
    }

    private fun setBooleanField(hedgehog: Hedgehog, name: String, value: Boolean) {
        val field = Hedgehog::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.setBoolean(hedgehog, value)
    }
}
