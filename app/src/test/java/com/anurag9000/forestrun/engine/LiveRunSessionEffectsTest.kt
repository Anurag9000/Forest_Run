package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRunSessionEffectsTest {
    @Test
    fun delegatesEveryEffectWithoutOwningTransitionOrder() {
        val calls = mutableListOf<String>()
        val effects = effects(calls)

        RunSessionEffect.entries.forEach { effect ->
            assertTrue(effects.apply(effect))
        }

        assertEquals(
            listOf(
                "prepare",
                "ritual",
                "copy",
                "death",
                "begin-restart",
                "run-reset",
                "ghost-reset",
                "ghost-reload",
                "garden",
                "menu-music"
            ),
            calls
        )
    }

    @Test
    fun coordinatorStopsAtThrowingLiveEffectAndForbidsStateAdoption() {
        val calls = mutableListOf<String>()
        val effects = LiveRunSessionEffects(
            prepareFreshRunAction = { calls += "prepare" },
            resetMenuRitualAction = { calls += "ritual" },
            refreshMenuCopyAction = { calls += "copy" },
            triggerDeathAction = { calls += "death" },
            beginRestartAction = { calls += "begin-restart" },
            executeRunResetAction = { calls += "run-reset" },
            resetGhostRecorderAction = { calls += "ghost-reset" },
            reloadGhostAction = {
                calls += "ghost-reload"
                throw IllegalStateException("ghost unavailable")
            },
            refreshGardenAction = { calls += "garden" },
            playMenuMusicAction = { calls += "menu-music" }
        )
        val coordinator = RunSessionTransitionCoordinator(effects)

        val result = coordinator.execute(
            current = RunSessionSnapshot(
                AppGameState.PLAYING,
                RunState.RESTARTING
            ),
            event = RunSessionEvent.RESTART_FADE_COMPLETED
        )

        assertEquals(
            listOf("run-reset", "ghost-reset", "ghost-reload"),
            calls
        )
        assertEquals(
            RunSessionExecutionDisposition.EFFECT_FAILED,
            result.disposition
        )
        assertEquals(RunSessionEffect.RELOAD_GHOST, result.failedEffect)
        assertFalse(result.mayAdoptAfterState)
    }

    @Test
    fun plannerRemainsTheOnlyOwnerOfEffectOrder() {
        val calls = mutableListOf<String>()
        val coordinator = RunSessionTransitionCoordinator(effects(calls))

        val result = coordinator.execute(
            current = RunSessionSnapshot(
                AppGameState.GARDEN,
                RunState.PLAYING
            ),
            event = RunSessionEvent.GARDEN_BACK_REQUESTED
        )

        assertTrue(result.mayAdoptAfterState)
        assertEquals(listOf("ritual", "copy", "menu-music"), calls)
        assertEquals(
            result.transition.effects,
            listOf(
                RunSessionEffect.RESET_MENU_RITUAL,
                RunSessionEffect.REFRESH_MENU_COPY,
                RunSessionEffect.PLAY_MENU_MUSIC
            )
        )
    }

    private fun effects(calls: MutableList<String>): LiveRunSessionEffects =
        LiveRunSessionEffects(
            prepareFreshRunAction = { calls += "prepare" },
            resetMenuRitualAction = { calls += "ritual" },
            refreshMenuCopyAction = { calls += "copy" },
            triggerDeathAction = { calls += "death" },
            beginRestartAction = { calls += "begin-restart" },
            executeRunResetAction = { calls += "run-reset" },
            resetGhostRecorderAction = { calls += "ghost-reset" },
            reloadGhostAction = { calls += "ghost-reload" },
            refreshGardenAction = { calls += "garden" },
            playMenuMusicAction = { calls += "menu-music" }
        )
}
