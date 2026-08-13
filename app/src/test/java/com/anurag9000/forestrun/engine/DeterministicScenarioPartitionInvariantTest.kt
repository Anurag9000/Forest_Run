package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicScenarioPartitionInvariantTest {

    @Test
    fun `every authored scenario preserves canonical actions across frame partitions`() {
        val authored = EncounterScenario.entries.filter { DebugScenarioScript.stepsFor(it).isNotEmpty() }
        assertTrue(authored.isNotEmpty())

        authored.forEach { scenario ->
            val expected = DebugScenarioScript.stepsFor(scenario)
                .map { it.atSeconds to it.action }

            val partitions = listOf(
                expected.map { it.first },
                listOf(expected.last().first + 10f),
                fineGrainedTimesThrough(expected.last().first + 0.5f),
                irregularTimesThrough(expected.last().first + 0.5f)
            )

            partitions.forEachIndexed { partitionIndex, elapsedTimes ->
                val script = DebugScenarioScript()
                val recorder = DeterministicScenarioTraceRecorder()
                val dispatched = mutableListOf<DebugScenarioAction>()
                script.prepare(scenario, recorder)

                elapsedTimes.forEach { elapsed ->
                    script.advance(elapsed) { action -> dispatched += action }
                }

                val snapshot = script.traceSnapshot()
                assertEquals(
                    "${scenario.name} partition $partitionIndex must dispatch every authored action",
                    expected.map { it.second },
                    dispatched
                )
                assertEquals(
                    "${scenario.name} partition $partitionIndex must preserve authored schedule and actions",
                    expected,
                    snapshot.events.map { it.scheduledAtSeconds to it.action }
                )
                assertTrue(
                    "${scenario.name} partition $partitionIndex must remain exact-replay evidence",
                    DeterministicScenarioReplayContract.matches(snapshot)
                )
            }
        }
    }

    @Test
    fun `backward and non finite frame observations never duplicate dispatched actions`() {
        val scenario = EncounterScenario.CACTUS_READ
        val expected = DebugScenarioScript.stepsFor(scenario)
        val script = DebugScenarioScript()
        val dispatched = mutableListOf<DebugScenarioAction>()
        script.prepare(scenario)

        script.advance(expected[1].atSeconds) { dispatched += it }
        script.advance(expected[0].atSeconds) { dispatched += it }
        script.advance(Float.NaN) { dispatched += it }
        script.advance(Float.POSITIVE_INFINITY) { dispatched += it }
        script.advance(expected.last().atSeconds + 1f) { dispatched += it }
        script.advance(expected.last().atSeconds + 2f) { dispatched += it }

        assertEquals(expected.map { it.action }, dispatched)
        assertTrue(DeterministicScenarioReplayContract.matches(script.traceSnapshot()))
    }

    @Test
    fun `rejected trace writes do not consume the next valid sequence slot`() {
        val recorder = DeterministicScenarioTraceRecorder(maxEvents = 4)
        val scenario = EncounterScenario.CACTUS_READ
        val first = DebugScenarioScript.stepsFor(scenario).first()
        recorder.begin(scenario)

        assertFalse(
            recorder.record(
                scenario = scenario,
                sequence = 1,
                scheduledAtSeconds = first.atSeconds,
                dispatchedAtSeconds = first.atSeconds,
                action = first.action
            )
        )
        assertFalse(
            recorder.record(
                scenario = EncounterScenario.EAGLE_MARK,
                sequence = 0,
                scheduledAtSeconds = first.atSeconds,
                dispatchedAtSeconds = first.atSeconds,
                action = first.action
            )
        )
        assertTrue(
            recorder.record(
                scenario = scenario,
                sequence = 0,
                scheduledAtSeconds = first.atSeconds,
                dispatchedAtSeconds = first.atSeconds,
                action = first.action
            )
        )

        val snapshot = recorder.snapshot()
        assertEquals(1, snapshot.events.size)
        assertEquals(0, snapshot.events.single().sequence)
        assertFalse(snapshot.overflowed)
    }

    private fun fineGrainedTimesThrough(end: Float): List<Float> {
        val result = mutableListOf<Float>()
        var time = 0f
        while (time < end) {
            result += time
            time += 0.137f
        }
        result += end
        return result
    }

    private fun irregularTimesThrough(end: Float): List<Float> {
        val increments = floatArrayOf(0.011f, 0.73f, 0.041f, 1.19f, 0.22f, 0.003f, 0.91f)
        val result = mutableListOf<Float>()
        var time = 0f
        var index = 0
        while (time < end) {
            time += increments[index % increments.size]
            result += minOf(time, end)
            index++
        }
        return result
    }
}
