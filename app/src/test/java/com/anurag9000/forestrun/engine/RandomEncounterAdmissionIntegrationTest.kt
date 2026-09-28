package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.EncounterOutcome
import com.anurag9000.forestrun.entities.EntityType
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
class RandomEncounterAdmissionIntegrationTest {
    private lateinit var context: Context
    private lateinit var sprites: SpriteManager
    private lateinit var player: Player

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        sprites = SpriteManager(context)
        player = Player(1_920, 1_080, sprites)
    }

    @Test
    fun `ordinary random admission waits for prior encounter resolution and player recovery`() {
        val manager = EntityManager(context, 1_920f, 1_080f, sprites)
        val state = GameStateManager(context)
        state.update(28f) // leave the guided random-spawn lock

        assertTrue(manager.canAdmitRandomEncounter(player))

        manager.spawn(
            type = EntityType.CACTUS,
            startX = 100_000f,
            recordPersistence = true
        )
        val prior = manager.activeEntities.single()
        assertFalse(manager.canAdmitRandomEncounter(player))

        // Accumulate more than the production spawn-origin gap. The old path
        // would stage another random encounter even though this one is pending.
        repeat(40) {
            manager.update(
                deltaTime = FrameInputAdmission.MAX_DELTA_SECONDS,
                gameState = state,
                player = player,
                runMode = RunMode.NORMAL
            )
        }
        assertEquals(1, manager.activeEntities.size)

        // Even after the previous encounter is resolved, an airborne player
        // must finish the actual action before another random encounter stages.
        prior.encounterOutcome = EncounterOutcome.CLEAN_PASS
        player.onJumpPressed()
        player.update(FrameInputAdmission.MAX_DELTA_SECONDS, state.scrollSpeed)
        assertTrue(player.state != PlayerState.RUNNING && player.state != PlayerState.LANDING)
        assertFalse(manager.canAdmitRandomEncounter(player))
        repeat(8) {
            manager.update(
                deltaTime = FrameInputAdmission.MAX_DELTA_SECONDS,
                gameState = state,
                player = player,
                runMode = RunMode.NORMAL
            )
        }
        assertEquals(1, manager.activeEntities.size)

        // Drive the real Player until the landing state becomes an allowed
        // action-switch point. The accumulated gap is retained while blocked,
        // so the next frame may stage exactly one fresh ordinary encounter.
        var guard = 0
        while (player.state != PlayerState.LANDING && guard < 80) {
            player.update(FrameInputAdmission.MAX_DELTA_SECONDS, state.scrollSpeed)
            guard++
        }
        assertEquals(PlayerState.LANDING, player.state)
        assertTrue(manager.canAdmitRandomEncounter(player))

        manager.update(
            deltaTime = FrameInputAdmission.MAX_DELTA_SECONDS,
            gameState = state,
            player = player,
            runMode = RunMode.NORMAL
        )
        assertEquals(2, manager.activeEntities.size)
        assertEquals(1, manager.activeEntities.count {
            it.encounterOutcome == EncounterOutcome.PENDING
        })
    }

    @Test
    fun `duck and stumble states hold random admission until real recovery`() {
        val manager = EntityManager(context, 1_920f, 1_080f, sprites)
        assertTrue(manager.canAdmitRandomEncounter(player))

        player.onDuckPressed()
        assertEquals(PlayerState.DUCKING, player.state)
        assertFalse(manager.canAdmitRandomEncounter(player))
        player.onDuckReleased()
        assertEquals(PlayerState.RUNNING, player.state)
        assertTrue(manager.canAdmitRandomEncounter(player))

        player.triggerStumble()
        assertEquals(PlayerState.STUMBLE, player.state)
        assertFalse(manager.canAdmitRandomEncounter(player))
        repeat(20) { player.update(FrameInputAdmission.MAX_DELTA_SECONDS) }
        assertEquals(PlayerState.RUNNING, player.state)
        assertTrue(manager.canAdmitRandomEncounter(player))
    }

    @Test
    fun `resolved and nonpersistent entities do not falsely block ordinary admission`() {
        val manager = EntityManager(context, 1_920f, 1_080f, sprites)
        manager.spawn(EntityType.CACTUS, startX = 1_000f, recordPersistence = false)
        assertTrue(manager.canAdmitRandomEncounter(player))

        manager.activeEntities.single().shouldRecordPersistence = true
        manager.activeEntities.single().encounterOutcome = EncounterOutcome.MERCY
        assertTrue(manager.canAdmitRandomEncounter(player))
    }
}
