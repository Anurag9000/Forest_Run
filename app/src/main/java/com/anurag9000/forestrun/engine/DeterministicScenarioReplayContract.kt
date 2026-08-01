package com.anurag9000.forestrun.engine

/** Exact authored-script boundary for deterministic scenario evidence. */
internal object DeterministicScenarioReplayContract {
    fun matches(snapshot: DeterministicScenarioTraceSnapshot): Boolean {
        val scenario = snapshot.scenario ?: return false
        if (!snapshot.isReplayable || snapshot.overflowed) return false
        val expected = DebugScenarioScript.stepsFor(scenario)
        if (snapshot.events.size != expected.size) return false
        return snapshot.events.indices.all { index ->
            val event = snapshot.events[index]
            val step = expected[index]
            event.scenario == scenario &&
                event.sequence == index &&
                event.scheduledAtSeconds == step.atSeconds &&
                event.action == step.action
        }
    }
}
