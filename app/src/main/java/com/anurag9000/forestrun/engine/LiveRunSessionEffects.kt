package com.anurag9000.forestrun.engine

/**
 * Callback adapter for the live Android/gameplay owners required by
 * [RunSessionTransitionCoordinator].
 *
 * It contains no transition table and never publishes app/run state. Exceptions
 * deliberately propagate to the coordinator, which fails closed and forbids
 * adoption of the planned after-state.
 */
internal class LiveRunSessionEffects(
    private val prepareFreshRunAction: () -> Unit,
    private val resetMenuRitualAction: () -> Unit,
    private val refreshMenuCopyAction: () -> Unit,
    private val triggerDeathAction: () -> Unit,
    private val beginRestartAction: () -> Unit,
    private val executeRunResetAction: () -> Unit,
    private val resetGhostRecorderAction: () -> Unit,
    private val reloadGhostAction: () -> Unit,
    private val refreshGardenAction: () -> Unit,
    private val playMenuMusicAction: () -> Unit
) : RunSessionEffectSink {
    override fun apply(effect: RunSessionEffect): Boolean {
        when (effect) {
            RunSessionEffect.PREPARE_FRESH_RUN -> prepareFreshRunAction()
            RunSessionEffect.RESET_MENU_RITUAL -> resetMenuRitualAction()
            RunSessionEffect.REFRESH_MENU_COPY -> refreshMenuCopyAction()
            RunSessionEffect.TRIGGER_DEATH -> triggerDeathAction()
            RunSessionEffect.BEGIN_RESTART -> beginRestartAction()
            RunSessionEffect.EXECUTE_RUN_RESET -> executeRunResetAction()
            RunSessionEffect.RESET_GHOST_RECORDER -> resetGhostRecorderAction()
            RunSessionEffect.RELOAD_GHOST -> reloadGhostAction()
            RunSessionEffect.REFRESH_GARDEN -> refreshGardenAction()
            RunSessionEffect.PLAY_MENU_MUSIC -> playMenuMusicAction()
        }
        return true
    }
}
