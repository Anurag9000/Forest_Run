package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunResetPartitionInvariantTest {
    @Test
    fun `dying transition is invariant to finite elapsed time partitioning`() {
        val totals = listOf(0.1f, 0.4f, 0.9f, 1.1f, 1.3f, 2.5f, 8f)
        val partitionCounts = listOf(1, 2, 3, 7, 16, 60, 257)

        totals.forEach { total ->
            val expected = advanceDying(total, 1)
            partitionCounts.forEach { partitions ->
                assertEquals(
                    "total=$total partitions=$partitions",
                    expected,
                    advanceDying(total, partitions)
                )
            }
        }
    }

    @Test
    fun `restart transition and fade are invariant to finite elapsed time partitioning`() {
        val totals = listOf(0.05f, 0.1f, 0.25f, 0.4f, 0.6f, 1f, 4f)
        val partitionCounts = listOf(1, 2, 3, 7, 16, 60, 257)

        totals.forEach { total ->
            val expected = advanceRestart(total, 1)
            partitionCounts.forEach { partitions ->
                val actual = advanceRestart(total, partitions)
                assertEquals(
                    "state total=$total partitions=$partitions",
                    expected.first,
                    actual.first
                )
                if (expected.first == RunState.PLAYING) {
                    assertEquals(
                        "terminal fade total=$total partitions=$partitions",
                        255,
                        actual.second
                    )
                } else {
                    assertTrue(
                        "fade total=$total partitions=$partitions expected=${expected.second} actual=${actual.second}",
                        kotlin.math.abs(expected.second - actual.second) <= 1
                    )
                }
            }
        }
    }

    private fun advanceDying(totalSeconds: Float, partitions: Int): RunState {
        val manager = RunResetManager()
        var state = RunState.DYING
        partition(totalSeconds, partitions).forEach { delta ->
            state = manager.update(delta, state)
        }
        return state
    }

    private fun advanceRestart(totalSeconds: Float, partitions: Int): Pair<RunState, Int> {
        val manager = RunResetManager()
        var state = manager.beginRestart()
        partition(totalSeconds, partitions).forEach { delta ->
            state = manager.update(delta, state)
        }
        return state to manager.restartFadeAlpha
    }

    private fun partition(totalSeconds: Float, count: Int): List<Float> {
        require(totalSeconds > 0f && totalSeconds.isFinite())
        require(count > 0)
        if (count == 1) return listOf(totalSeconds)

        val step = totalSeconds / count
        val deltas = MutableList(count - 1) { step }
        val consumed = step * (count - 1)
        val residual = totalSeconds - consumed
        deltas += if (residual > 0f) residual else step
        return deltas
    }
}
