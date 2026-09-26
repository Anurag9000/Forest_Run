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

/** Runtime interpretation of one authored action at its actual scheduled hold duration. */
internal data class DebugScenarioTimedAction(
    val action: DebugScenarioAction,
    val scheduledAtSeconds: Float,
    val holdDurationSeconds: Float
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

    private val defaultTraceRecorder = DeterministicScenarioTraceRecorder()
    private var activeScenario: EncounterScenario? = null
    private var traceRecorder: DeterministicScenarioTraceRecorder = defaultTraceRecorder
    private var completedTrace = DeterministicScenarioTraceSnapshot(
        scenario = null,
        events = emptyList(),
        overflowed = false
    )

    fun prepare(
        scenario: EncounterScenario,
        recorder: DeterministicScenarioTraceRecorder = defaultTraceRecorder
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
            completedTrace = DeterministicScenarioTraceSnapshot(
                scenario = null,
                events = emptyList(),
                overflowed = false
            )
            recorder.begin(scenario)
        }
    }

    fun clear() {
        synchronized(this) {
            completedTrace = traceRecorder.snapshot()
            steps = emptyList()
            nextIndex = 0
            activeScenario = null
            traceRecorder = defaultTraceRecorder
        }
    }

    /** Keep the action-only API without duplicating dispatch/trace semantics. */
    fun advance(elapsedSeconds: Float, dispatch: (DebugScenarioAction) -> Unit) {
        advanceTimed(elapsedSeconds) { timed -> dispatch(timed.action) }
    }

    /**
     * Use the authored press/end timestamps for release height. Frame batching
     * must not turn a scheduled hold into a zero-duration tap.
     */
    fun advanceTimed(elapsedSeconds: Float, dispatch: (DebugScenarioTimedAction) -> Unit) {
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
                val heldSeconds = if (step.action == DebugScenarioAction.HOLD_JUMP_END) {
                    // prepare() requires balanced, nonoverlapping authored hold pairs.
                    val start = activeSteps.subList(0, sequence).lastOrNull {
                        it.action == DebugScenarioAction.HOLD_JUMP_START
                    } ?: error("Validated deterministic jump end has no start")
                    (step.atSeconds - start.atSeconds).coerceAtLeast(0f)
                } else {
                    0f
                }
                dispatch(
                    DebugScenarioTimedAction(
                        action = step.action,
                        scheduledAtSeconds = step.atSeconds,
                        holdDurationSeconds = heldSeconds
                    )
                )
                if (scenario != null) {
                    traceRecorder.record(
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

    fun traceSnapshot(): DeterministicScenarioTraceSnapshot = synchronized(this) {
        if (activeScenario != null) traceRecorder.snapshot() else completedTrace
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
