package com.anurag9000.forestrun.engine

/** Narrow live port for executing one planned session effect. */
internal fun interface RunSessionEffectSink {
    /** Returns false when the effect could not be completed safely. */
    fun apply(effect: RunSessionEffect): Boolean
}

internal enum class RunSessionExecutionDisposition {
    APPLIED,
    NO_OP,
    EFFECT_FAILED
}

internal data class RunSessionExecutionResult(
    val transition: RunSessionTransition,
    val disposition: RunSessionExecutionDisposition,
    val failedEffect: RunSessionEffect? = null
) {
    /** Caller may publish [RunSessionTransition.after] only when this is true. */
    val mayAdoptAfterState: Boolean
        get() = disposition == RunSessionExecutionDisposition.APPLIED
}

/**
 * Applies planned effects in declared order and leaves state publication to the
 * live owner. If an effect fails, later effects are skipped and the caller must
 * retain the original snapshot.
 */
internal class RunSessionTransitionCoordinator(
    private val effects: RunSessionEffectSink
) {
    fun execute(
        current: RunSessionSnapshot,
        event: RunSessionEvent
    ): RunSessionExecutionResult {
        val transition = RunSessionTransitionPlanner.plan(current, event)
        if (!transition.changed) {
            return RunSessionExecutionResult(
                transition = transition,
                disposition = RunSessionExecutionDisposition.NO_OP
            )
        }

        transition.effects.forEach { effect ->
            val applied = try {
                effects.apply(effect)
            } catch (_: RuntimeException) {
                false
            }
            if (!applied) {
                return RunSessionExecutionResult(
                    transition = transition,
                    disposition = RunSessionExecutionDisposition.EFFECT_FAILED,
                    failedEffect = effect
                )
            }
        }

        return RunSessionExecutionResult(
            transition = transition,
            disposition = RunSessionExecutionDisposition.APPLIED
        )
    }
}
