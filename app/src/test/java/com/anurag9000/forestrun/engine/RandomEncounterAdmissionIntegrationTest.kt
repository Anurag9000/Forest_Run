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
    fun `compact max-speed random spawn keeps full sampled action lead after creation tick`() {
        val compactPlayer = Player(640, 360, sprites)
        val manager = EntityManager(context, 640f, 360f, sprites)
        val state = GameStateManager(context) { false }

        // Reach the supported speed ceiling while keeping deterministic local
        // persistence isolated, then move beyond opening random-spawn lock.
        repeat(3) { state.update(5_000f) }
        assertEquals(GameConstants.MAX_SCROLL_SPEED, state.scrollSpeed, 0f)
        assertTrue(state.runTimeSeconds >= 28f)

        // Accumulate enough random origin spacing. The admitted entity is
        // created and updated in this same final frame.
        var guard = 0
        while (manager.activeEntities.isEmpty() && guard++ < 40) {
            manager.update(
                deltaTime = FrameInputAdmission.MAX_DELTA_SECONDS,
                gameState = state,
                player = compactPlayer,
                runMode = RunMode.NORMAL
            )
        }
        assertEquals(1, manager.activeEntities.size)
        val staged = manager.activeEntities.single()
        val remainingLead = staged.encounterBounds.left - compactPlayer.hitbox.right

        val apex = EncounterActionFeasibility.observe(
            leadDistancePx = Float.MAX_VALUE,
            approachSpeedPxPerSec = state.scrollSpeed,
            requiredVerticalClearancePx = 0f,
            jumpUpwardSpeedPxPerSec = -Player.MAX_JUMP_FORCE,
            gravityPxPerSecSquared = Player.GRAVITY,
            gestureDecisionSeconds = 0f
        ).maximumBallisticRisePx
        val reaction = EncounterActionFeasibility.observe(
            leadDistancePx = remainingLead,
            approachSpeedPxPerSec = state.scrollSpeed,
            requiredVerticalClearancePx = apex,
            jumpUpwardSpeedPxPerSec = -Player.MAX_JUMP_FORCE,
            gravityPxPerSecSquared = Player.GRAVITY,
            gestureDecisionSeconds = 0.075f,
            safetyMarginSeconds = 0.08f
        )
        assertTrue(
            "new random encounter lost its sampled action lead on creation frame",
            reaction.jumpFeasible
        )
    }


    @Test
    fun `max-speed Hedgehog retains full sampled jump window after its creation update`() {
        val compactPlayer = Player(640, 360, sprites)
        val manager = EntityManager(context, 640f, 360f, sprites)
        val state = GameStateManager(context) { false }
        repeat(3) { state.update(5_000f) }
        assertEquals(GameConstants.MAX_SCROLL_SPEED, state.scrollSpeed, 0f)

        val lead = SpawnPacing.minimumRandomEncounterLeadPx(state.scrollSpeed)
        manager.spawn(
            type = EntityType.HEDGEHOG,
            startX = compactPlayer.hitbox.right + lead,
            recordPersistence = false
        )
        manager.update(
            deltaTime = FrameInputAdmission.MAX_DELTA_SECONDS,
            gameState = state,
            player = compactPlayer,
            runMode = RunMode.DEBUG_SCENARIO
        )

        val staged = manager.activeEntities.single()
        val remainingLead = staged.encounterBounds.left - compactPlayer.hitbox.right
        val fastestApproach = state.scrollSpeed * 1.15f
        val apex = EncounterActionFeasibility.observe(
            leadDistancePx = Float.MAX_VALUE,
            approachSpeedPxPerSec = fastestApproach,
            requiredVerticalClearancePx = 0f,
            jumpUpwardSpeedPxPerSec = -Player.MAX_JUMP_FORCE,
            gravityPxPerSecSquared = Player.GRAVITY,
            gestureDecisionSeconds = 0f
        ).maximumBallisticRisePx
        val reaction = EncounterActionFeasibility.observe(
            leadDistancePx = remainingLead,
            approachSpeedPxPerSec = fastestApproach,
            requiredVerticalClearancePx = apex,
            jumpUpwardSpeedPxPerSec = -Player.MAX_JUMP_FORCE,
            gravityPxPerSecSquared = Player.GRAVITY,
            gestureDecisionSeconds = 0.075f,
            safetyMarginSeconds = 0.08f
        )

        assertEquals(495f, apex, 0.002f)
        assertTrue(
            "fastest immediate family consumed the full-window reaction lead",
            reaction.jumpFeasible
        )
    }

    @Test
    fun `random staging ignores temporary slow when computing future reaction lead`() {
        val compactPlayer = Player(640, 360, sprites)
        val manager = EntityManager(context, 640f, 360f, sprites)
        val state = GameStateManager(context) { false }

        // Reach the production speed ceiling, then leave only one frame of a
        // 50% slow. The encounter will spend most of its approach after the
        // world returns to full speed.
        repeat(3) { state.update(5_000f) }
        assertEquals(GameConstants.MAX_SCROLL_SPEED, state.undebuffedScrollSpeed, 0f)
        state.applySpeedDebuff(0.5f, 100)
        state.update(FrameInputAdmission.MAX_DELTA_SECONDS)
        assertEquals(GameConstants.MAX_SCROLL_SPEED * 0.5f, state.scrollSpeed, 0f)
        assertEquals(GameConstants.MAX_SCROLL_SPEED, state.undebuffedScrollSpeed, 0f)

        // Blocked time retains the production spacing counter. Seed it beyond
        // the existing gap so this frame exercises only live action admission.
        val gapField = EntityManager::class.java.getDeclaredField(
            "distanceSinceRandomSpawnPx"
        )
        gapField.isAccessible = true
        gapField.setFloat(manager, Float.MAX_VALUE)

        manager.update(
            deltaTime = FrameInputAdmission.MAX_DELTA_SECONDS,
            gameState = state,
            player = compactPlayer,
            runMode = RunMode.NORMAL
        )
        assertEquals(1, manager.activeEntities.size)

        val staged = manager.activeEntities.single()
        val requiredRecoveryLead = SpawnPacing.minimumRandomEncounterLeadPx(
            state.undebuffedScrollSpeed
        )
        val initialCoreLead =
            staged.previousHitbox.left - compactPlayer.hitbox.right
        assertTrue(
            "temporary slow incorrectly shortened random encounter staging",
            initialCoreLead + 0.001f >= requiredRecoveryLead
        )

        // The temporary slow expires; the next published frame speed is the
        // same undebuffed speed that admission budgeted.
        state.update(FrameInputAdmission.MAX_DELTA_SECONDS)
        state.update(FrameInputAdmission.MAX_DELTA_SECONDS)
        assertEquals(GameConstants.MAX_SCROLL_SPEED, state.scrollSpeed, 0f)
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
