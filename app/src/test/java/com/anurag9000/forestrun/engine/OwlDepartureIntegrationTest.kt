package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.EncounterOutcome
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.entities.birds.Owl
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
class OwlDepartureIntegrationTest {
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
    fun `visible warned Owl dive exiting vertically resolves one clean pass`() {
        val owl = warnedDivingOwl()
        advanceUntilDiveExits(owl)

        assertTrue(owl.hasCompletedDiveEscape)
        assertEquals(EncounterOutcome.PENDING, owl.encounterOutcome)
        assertEquals(0, state.cleanPassesThisRun)
        assertTrue(manager.activeEntities.isEmpty())

        assertNull(manager.checkCollisions(player, state))
        assertEquals(EncounterOutcome.CLEAN_PASS, owl.encounterOutcome)
        assertEquals(1, state.cleanPassesThisRun)
        assertTrue(state.score > 0)
        assertNull(manager.checkCollisions(player, state))
        assertEquals(1, state.cleanPassesThisRun)
    }

    @Test
    fun `final vertical Owl escape segment cannot tunnel through player into clean pass`() {
        val owl = newOwl()
        player.update(0.05f, state.scrollSpeed)

        // Enter the genuine dive state, then stage one final high-speed segment
        // whose endpoints straddle the Player and whose end leaves the surface.
        owl.triggerDive(player.hitbox.centerX(), player.hitbox.centerY())
        Owl::class.java.getDeclaredField("hasWarned").apply {
            isAccessible = true
            setBoolean(owl, true)
        }
        val insetX = owl.hitbox.left - owl.x
        val insetY = owl.hitbox.top - owl.y
        val coreHeight = owl.hitbox.height()
        owl.x = player.hitbox.left - insetX + 4f
        owl.y = player.hitbox.top - coreHeight - insetY - 12f
        owl.hitbox.offsetTo(owl.x + insetX, owl.y + insetY)
        Owl::class.java.getDeclaredField("velX").apply {
            isAccessible = true
            setFloat(owl, 0f)
        }
        Owl::class.java.getDeclaredField("velY").apply {
            isAccessible = true
            setFloat(owl, 10_000f)
        }
        manager.activeEntities.add(owl)

        manager.update(0.05f, state, player, runMode = RunMode.DEBUG_SCENARIO)
        assertFalse(owl.isActive)
        assertTrue(owl.hasCompletedDiveEscape)
        assertTrue(manager.activeEntities.isEmpty())

        val frame = requireNotNull(manager.checkCollisions(player, state))
        assertEquals(com.anurag9000.forestrun.entities.CollisionResult.HIT, frame.result)
        assertTrue(frame.entity === owl)
        assertEquals(EncounterOutcome.HIT, owl.encounterOutcome)
        assertEquals(0, state.cleanPassesThisRun)
        assertNull(manager.checkCollisions(player, state))
    }

    @Test
    fun `completed Owl escape under Bloom is exclusive conversion`() {
        val owl = warnedDivingOwl()
        state.debugActivateBloom()
        advanceUntilDiveExits(owl)

        assertNull(manager.checkCollisions(player, state))
        assertEquals(EncounterOutcome.BLOOM_CONVERTED, owl.encounterOutcome)
        assertEquals(1, state.bloomConversionsThisRun)
        assertEquals(0, state.cleanPassesThisRun)
        assertEquals(0, state.mercyHearts)
    }

    @Test
    fun `sleeping nonattacking Owl still resolves through normal x plane passage`() {
        val owl = newOwl()
        manager.activeEntities.add(owl)
        var ticks = 0
        while (owl.encounterOutcome == EncounterOutcome.PENDING && ticks++ < 80) {
            manager.update(0.05f, state, player, runMode = RunMode.DEBUG_SCENARIO)
            assertNull(manager.checkCollisions(player, state))
        }
        assertFalse(owl.hasCompletedDiveEscape)
        assertEquals(EncounterOutcome.CLEAN_PASS, owl.encounterOutcome)
        assertEquals(1, state.cleanPassesThisRun)
        assertNull(manager.checkCollisions(player, state))
        assertEquals(1, state.cleanPassesThisRun)
    }

    private fun warnedDivingOwl(): Owl {
        val owl = newOwl()
        manager.activeEntities.add(owl)
        player.onJumpPressed()
        val stateField = Owl::class.java.getDeclaredField("owlState").apply {
            isAccessible = true
        }

        var ticks = 0
        while (stateField.get(owl).toString() != "DIVING" && ticks++ < 60) {
            manager.update(0.05f, state, player, runMode = RunMode.DEBUG_SCENARIO)
            assertNull(manager.checkCollisions(player, state))
            assertTrue(owl.isActive)
        }
        assertEquals("DIVING", stateField.get(owl).toString())
        // Move out of the now fixed dive corridor without granting an x-plane
        // pass early. The warned bird must finish its full vertical attack.
        player.hitbox.set(0f, 0f, 32f, 32f)
        return owl
    }

    private fun advanceUntilDiveExits(owl: Owl) {
        var ticks = 0
        while (owl.isActive && ticks++ < 160) {
            manager.update(0.05f, state, player, runMode = RunMode.DEBUG_SCENARIO)
            if (owl.isActive) {
                assertNull(manager.checkCollisions(player, state))
                assertEquals(EncounterOutcome.PENDING, owl.encounterOutcome)
            }
        }
        assertFalse("Owl never completed its dive", owl.isActive)
        assertTrue("Only a genuinely warned dive may earn an escape",
            owl.hasCompletedDiveEscape)
    }

    private fun newOwl() = Owl(
        context = context,
        startX = 560f,
        groundY = 885.6f,
        screenWidth = 1_920f,
        idleSprite = sprites.owlSprite.copy(),
        actionSprite = sprites.owlFlying.copy()
    ).apply { shouldRecordPersistence = false }
}
