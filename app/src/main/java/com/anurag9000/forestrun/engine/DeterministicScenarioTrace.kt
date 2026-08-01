package com.anurag9000.forestrun.engine

/** One input action actually dispatched by a deterministic encounter scenario. */
internal data class DeterministicScenarioTraceEvent(
    val scenario: EncounterScenario,
    val sequence: Int,
    val scheduledAtSeconds: Float,
    val dispatchedAtSeconds: Float,
    val action: DebugScenarioAction
) {
    init {
        require(sequence >= 0) { "Trace sequence must be non-negative." }
        require(scheduledAtSeconds.isFinite() && scheduledAtSeconds >= 0f) {
            "Trace schedule time must be finite and non-negative."
        }
        require(dispatchedAtSeconds.isFinite() && dispatchedAtSeconds >= scheduledAtSeconds) {
            "Trace dispatch time must be finite and cannot precede its schedule."
        }
    }

    val latenessSeconds: Float
        get() = (dispatchedAtSeconds - scheduledAtSeconds).coerceAtLeast(0f)
}

internal data class DeterministicScenarioTraceSnapshot(
    val scenario: EncounterScenario?,
    val events: List<DeterministicScenarioTraceEvent>,
    val overflowed: Boolean
) {
    val isReplayable: Boolean
        get() = scenario != null && !overflowed && events.withIndex().all { (index, event) ->
            event.scenario == scenario && event.sequence == index
        }
}

/**
 * Bounded recorder for deterministic scenario input dispatches.
 *
 * It records only actions that were successfully handed to the runtime. The
 * recorder never owns gameplay state, performs disk I/O, or changes dispatch
 * ordering. Snapshots are detached immutable copies suitable for instrumentation
 * evidence or exact replay diagnostics.
 */
internal class DeterministicScenarioTraceRecorder(
    private val maxEvents: Int = DEFAULT_MAX_EVENTS
) {
    init {
        require(maxEvents in 1..ABSOLUTE_MAX_EVENTS) {
            "Trace event capacity must be between 1 and $ABSOLUTE_MAX_EVENTS."
        }
    }

    private val lock = Any()
    private var activeScenario: EncounterScenario? = null
    private val events = ArrayList<DeterministicScenarioTraceEvent>(maxEvents)
    private var overflowed = false

    fun begin(scenario: EncounterScenario) {
        synchronized(lock) {
            activeScenario = scenario
            events.clear()
            overflowed = false
        }
    }

    fun record(
        scenario: EncounterScenario,
        sequence: Int,
        scheduledAtSeconds: Float,
        dispatchedAtSeconds: Float,
        action: DebugScenarioAction
    ): Boolean = synchronized(lock) {
        if (activeScenario != scenario || overflowed || sequence != events.size) return@synchronized false
        if (events.size >= maxEvents) {
            overflowed = true
            return@synchronized false
        }
        val event = runCatching {
            DeterministicScenarioTraceEvent(
                scenario = scenario,
                sequence = sequence,
                scheduledAtSeconds = scheduledAtSeconds,
                dispatchedAtSeconds = dispatchedAtSeconds,
                action = action
            )
        }.getOrNull() ?: return@synchronized false
        events += event
        true
    }

    fun snapshot(): DeterministicScenarioTraceSnapshot = synchronized(lock) {
        DeterministicScenarioTraceSnapshot(
            scenario = activeScenario,
            events = events.toList(),
            overflowed = overflowed
        )
    }

    companion object {
        const val DEFAULT_MAX_EVENTS = 256
        const val ABSOLUTE_MAX_EVENTS = 4_096
    }
}

internal data class DebugScenarioInputValidation(
    val violations: List<String>
) {
    val isValid: Boolean
        get() = violations.isEmpty()
}

/** Pure state-machine validation for authored deterministic input scripts. */
internal object DebugScenarioInputContract {
    private const val MAX_SCRIPT_SECONDS = 60f

    fun validate(steps: List<DebugScenarioStep>): DebugScenarioInputValidation {
        val violations = ArrayList<String>()
        var previousTime = -1f
        var jumpHeld = false
        var duckHeld = false

        steps.forEachIndexed { index, step ->
            val label = "step[$index]"
            if (!step.atSeconds.isFinite() || step.atSeconds < 0f) {
                violations += "$label time must be finite and non-negative"
                return@forEachIndexed
            }
            if (step.atSeconds > MAX_SCRIPT_SECONDS) {
                violations += "$label exceeds the maximum script duration"
            }
            if (step.atSeconds < previousTime) {
                violations += "$label is earlier than the preceding action"
            }
            previousTime = maxOf(previousTime, step.atSeconds)

            when (step.action) {
                DebugScenarioAction.TAP_JUMP -> {
                    if (jumpHeld) violations += "$label tap jump overlaps an active held jump"
                    if (duckHeld) violations += "$label tap jump overlaps an active duck"
                }
                DebugScenarioAction.HOLD_JUMP_START -> {
                    if (jumpHeld) violations += "$label starts an already active held jump"
                    if (duckHeld) violations += "$label held jump starts during an active duck"
                    jumpHeld = true
                }
                DebugScenarioAction.HOLD_JUMP_END -> {
                    if (!jumpHeld) violations += "$label ends a held jump that was not active"
                    jumpHeld = false
                }
                DebugScenarioAction.DUCK_START -> {
                    if (duckHeld) violations += "$label starts an already active duck"
                    if (jumpHeld) violations += "$label duck starts during an active held jump"
                    duckHeld = true
                }
                DebugScenarioAction.DUCK_END -> {
                    if (!duckHeld) violations += "$label ends a duck that was not active"
                    duckHeld = false
                }
            }
        }

        if (jumpHeld) violations += "script ends with a held jump still active"
        if (duckHeld) violations += "script ends with a duck still active"
        return DebugScenarioInputValidation(violations.toList())
    }
}
