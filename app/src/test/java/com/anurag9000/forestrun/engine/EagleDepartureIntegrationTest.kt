package com.anurag9000.forestrun.engine

import android.content.Context
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.EncounterOutcome
import com.anurag9000.forestrun.entities.Entity
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.entities.birds.Eagle
import com.anurag9000.forestrun.systems.ParticleManager
import com.anurag9000.forestrun.ui.DialogueBubbleManager
import com.anurag9000.forestrun.ui.FlavorTextManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EagleDepartureIntegrationTest {
    private lateinit var context: Context
    private lateinit var sprites: SpriteManager
    private lateinit var player: Player
    private lateinit var manager: EntityManager
    private lateinit var state: GameStateManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        ParticleManager.clear()
        DialogueBubbleManager.clear()
        FlavorTextManager.clear()
        sprites = SpriteManager(context)
        player = Player(1_920, 1_080, sprites)
        manager = EntityManager(context, 1_920f, 1_080f, sprites)
        state = GameStateManager(context) { false }
    }

    @After
    fun tearDown() {
        ParticleManager.clear()
        DialogueBubbleManager.clear()
        FlavorTextManager.clear()
    }

    @Test
    fun `completed vertical Eagle escape receives one clean pass not a silent removal`() {
        val eagle = lockedEagle()
        advanceUntilEagleDeparts(eagle)

        assertTrue(eagle.hasCompletedAttackEscape)
        assertEquals(EncounterOutcome.PENDING, eagle.encounterOutcome)
        assertEquals(0, state.cleanPassesThisRun)
        assertTrue(manager.activeEntities.isEmpty())

        assertNull(manager.checkCollisions(player, state))
        assertEquals(EncounterOutcome.CLEAN_PASS, eagle.encounterOutcome)
        assertTrue(eagle.hasBeenPassed)
        assertEquals(1, state.cleanPassesThisRun)
        assertTrue(state.score > 0)
        assertNull(manager.checkCollisions(player, state))
        assertEquals(1, state.cleanPassesThisRun)
    }

    @Test
    fun `final vertical Eagle escape segment cannot tunnel through player into clean pass`() {
        val eagle = Eagle(
            context = context,
            startX = player.hitbox.left,
            screenWidth = 1_920f,
            groundY = 885.6f,
            sprite = sprites.eagleSprite.copy()
        ).apply { shouldRecordPersistence = false }

        // Give the stationary Player a real previous/current motion sample.
        player.update(0.05f, state.scrollSpeed)

        val insetX = eagle.hitbox.left - eagle.x
        val insetY = eagle.hitbox.top - eagle.y
        val coreHeight = eagle.hitbox.height()
        eagle.x = player.hitbox.left - insetX + 4f
        eagle.y = player.hitbox.top - coreHeight - insetY - 12f
        eagle.hitbox.offsetTo(eagle.x + insetX, eagle.y + insetY)

        Eagle::class.java.getDeclaredField("isLocked").apply {
            isAccessible = true
            setBoolean(eagle, true)
        }
        Eagle::class.java.getDeclaredField("hasEnteredHorizontalViewport").apply {
            isAccessible = true
            setBoolean(eagle, true)
        }
        Eagle::class.java.getDeclaredField("velX").apply {
            isAccessible = true
            setFloat(eagle, 0f)
        }
        Eagle::class.java.getDeclaredField("velY").apply {
            isAccessible = true
            setFloat(eagle, 10_000f)
        }
        manager.activeEntities.add(eagle)

        manager.update(0.05f, state, player, runMode = RunMode.DEBUG_SCENARIO)
        assertFalse(eagle.isActive)
        assertTrue(eagle.hasCompletedAttackEscape)
        assertTrue(manager.activeEntities.isEmpty())

        val frame = requireNotNull(manager.checkCollisions(player, state))
        assertEquals(CollisionResult.HIT, frame.result)
        assertTrue(frame.entity === eagle)
        assertEquals(EncounterOutcome.HIT, eagle.encounterOutcome)
        assertEquals(0, state.cleanPassesThisRun)
        assertNull(manager.checkCollisions(player, state))
    }

    @Test
    fun `completed Eagle escape preserves Bloom conversion exclusivity`() {
        val eagle = lockedEagle()
        state.debugActivateBloom()
        advanceUntilEagleDeparts(eagle)

        assertEquals(EncounterOutcome.PENDING, eagle.encounterOutcome)
        assertNull(manager.checkCollisions(player, state))
        assertEquals(EncounterOutcome.BLOOM_CONVERTED, eagle.encounterOutcome)
        assertEquals(1, state.bloomConversionsThisRun)
        assertEquals(0, state.cleanPassesThisRun)
        assertEquals(0, state.mercyHearts)
    }

    @Test
    fun `same frame direct hit outranks deferred Eagle escape and reset drops it`() {
        val eagle = lockedEagle()
        advanceUntilEagleDeparts(eagle)
        manager.activeEntities.add(ForcedHit(context))

        assertEquals(CollisionResult.HIT, manager.checkCollisions(player, state)?.result)
        assertEquals(EncounterOutcome.PENDING, eagle.encounterOutcome)
        assertEquals(0, state.cleanPassesThisRun)

        // A terminal death does not retroactively credit the abandoned escape.
        manager.reset()
        assertNull(manager.checkCollisions(player, state))
        assertEquals(0, state.cleanPassesThisRun)
    }

    private fun lockedEagle(): Eagle {
        val eagle = Eagle(
            context = context,
            startX = 600f,
            screenWidth = 1_920f,
            groundY = 885.6f,
            sprite = sprites.eagleSprite.copy()
        ).apply { shouldRecordPersistence = false }
        manager.activeEntities.add(eagle)
        val lockField = Eagle::class.java.getDeclaredField("isLocked").apply {
            isAccessible = true
        }
        var ticks = 0
        while (!lockField.getBoolean(eagle) && ticks++ < 60) {
            manager.update(0.05f, state, player, runMode = RunMode.DEBUG_SCENARIO)
            assertNull(manager.checkCollisions(player, state))
            assertTrue(eagle.isActive)
        }
        assertTrue("Eagle must lock onto the live initial player", lockField.getBoolean(eagle))
        // Escape the known locked mark without touching the diving body.
        // A left-side low hitbox also prevents an ordinary x-plane pass from
        // masking the specific vertical-departure path under test.
        player.hitbox.set(0f, 0f, 32f, 32f)
        return eagle
    }

    private fun advanceUntilEagleDeparts(eagle: Eagle) {
        var ticks = 0
        while (eagle.isActive && ticks++ < 160) {
            manager.update(0.05f, state, player, runMode = RunMode.DEBUG_SCENARIO)
            if (eagle.isActive) {
                assertNull(manager.checkCollisions(player, state))
                assertEquals(EncounterOutcome.PENDING, eagle.encounterOutcome)
            }
        }
        assertFalse("Eagle never completed its dive", eagle.isActive)
        assertTrue("Expected a completed visible dive, not pre-entry culling",
            eagle.hasCompletedAttackEscape)
    }

    private class ForcedHit(context: Context) : Entity(context) {
        init { hitbox.set(0f, 0f, 32f, 32f) }
        override fun update(deltaTime: Float, scrollSpeed: Float) = Unit
        override fun draw(canvas: Canvas) = Unit
        override fun onCollision(player: Player, gameState: GameStateManager) =
            CollisionResult.HIT
    }
}
