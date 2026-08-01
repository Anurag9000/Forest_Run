package com.anurag9000.forestrun.engine

/** Input action emitted by a deterministic encounter script. */
internal enum class DebugScenarioAction {
    TAP_JUMP,
    HOLD_JUMP_START,
    HOLD_JUMP_END,
    DUCK_START,
    DUCK_END
}

internal data class DebugScenarioStep(
    val atSeconds: Float,
    val action: DebugScenarioAction
)

/**
 * Owns deterministic input sequencing independently from [GameView].
 *
 * The completed/empty fast path is lock-free. Synchronization is used only
 * while a short active script is being prepared or dispatching due actions.
 */
internal class DebugScenarioScript {
    @Volatile
    private var steps: List<DebugScenarioStep> = emptyList()

    @Volatile
    private var nextIndex = 0

    private var activeScenario: EncounterScenario? = null
    private var traceRecorder: DeterministicScenarioTraceRecorder? = null

    fun prepare(
        scenario: EncounterScenario,
        recorder: DeterministicScenarioTraceRecorder? = null
    ) {
        val preparedSteps = stepsFor(scenario)
        val validation = DebugScenarioInputContract.validate(preparedSteps)
        require(validation.isValid) {
            "Invalid deterministic input script for ${scenario.name}: " +
                validation.violations.joinToString("; ")
        }

        synchronized(this) {
            steps = preparedSteps
            nextIndex = 0
            activeScenario = scenario
            traceRecorder = recorder
            recorder?.begin(scenario)
        }
    }

    fun clear() {
        synchronized(this) {
            steps = emptyList()
            nextIndex = 0
            activeScenario = null
            traceRecorder = null
        }
    }

    fun advance(elapsedSeconds: Float, dispatch: (DebugScenarioAction) -> Unit) {
        val observedSteps = steps
        if (!elapsedSeconds.isFinite() || nextIndex >= observedSteps.size) return

        synchronized(this) {
            val activeSteps = steps
            val scenario = activeScenario
            while (nextIndex < activeSteps.size &&
                activeSteps[nextIndex].atSeconds <= elapsedSeconds
            ) {
                val sequence = nextIndex
                val step = activeSteps[sequence]
                dispatch(step.action)
                if (scenario != null) {
                    traceRecorder?.record(
                        scenario = scenario,
                        sequence = sequence,
                        scheduledAtSeconds = step.atSeconds,
                        dispatchedAtSeconds = elapsedSeconds,
                        action = step.action
                    )
                }
                nextIndex++
            }
        }
    }

    internal fun pendingCountForTest(): Int =
        (steps.size - nextIndex).coerceAtLeast(0)

    companion object {
        internal fun stepsFor(scenario: EncounterScenario): List<DebugScenarioStep> = when (scenario) {
            EncounterScenario.CACTUS_READ -> listOf(
                DebugScenarioStep(3.18f, DebugScenarioAction.HOLD_JUMP_START),
                DebugScenarioStep(3.48f, DebugScenarioAction.HOLD_JUMP_END),
                DebugScenarioStep(5.06f, DebugScenarioAction.HOLD_JUMP_START),
                DebugScenarioStep(5.36f, DebugScenarioAction.HOLD_JUMP_END)
            )
            EncounterScenario.CAT_KINDNESS -> listOf(
                DebugScenarioStep(0.95f, DebugScenarioAction.HOLD_JUMP_START),
                DebugScenarioStep(1.22f, DebugScenarioAction.HOLD_JUMP_END),
                DebugScenarioStep(3.25f, DebugScenarioAction.HOLD_JUMP_START),
                DebugScenarioStep(3.52f, DebugScenarioAction.HOLD_JUMP_END)
            )
            EncounterScenario.FOX_MIRROR -> listOf(
                DebugScenarioStep(2.10f, DebugScenarioAction.HOLD_JUMP_START),
                DebugScenarioStep(2.40f, DebugScenarioAction.HOLD_JUMP_END),
                DebugScenarioStep(4.35f, DebugScenarioAction.HOLD_JUMP_START),
                DebugScenarioStep(4.64f, DebugScenarioAction.HOLD_JUMP_END)
            )
            EncounterScenario.EAGLE_MARK -> listOf(
                DebugScenarioStep(1.35f, DebugScenarioAction.HOLD_JUMP_START),
                DebugScenarioStep(1.66f, DebugScenarioAction.HOLD_JUMP_END),
                DebugScenarioStep(4.30f, DebugScenarioAction.HOLD_JUMP_START),
                DebugScenarioStep(4.62f, DebugScenarioAction.HOLD_JUMP_END)
            )
            else -> emptyList()
        }
    }
}
