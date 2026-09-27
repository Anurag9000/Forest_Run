package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncounterActionFeasibilityTest {
    private val gestureDecisionSeconds = 0.075f
    private val safetyMarginSeconds = 0.08f

    @Test
    fun `full jump ballistic envelope matches player constants conservatively`() {
        val observation = observe(
            leadDistancePx = 2_000f,
            speedPxPerSec = GameConstants.BASE_SCROLL_SPEED,
            clearancePx = 0f
        )

        val expectedRise =
            Player.MAX_JUMP_FORCE * Player.MAX_JUMP_FORCE / (2f * Player.GRAVITY)
        assertEquals(expectedRise, observation.maximumBallisticRisePx, 0.0001f)
        assertEquals(540f, observation.maximumBallisticRisePx, 0.0001f)
        assertTrue(observation.jumpFeasible)
        assertTrue(observation.duckFeasible)
    }

    @Test
    fun `production origin-gap sweep retains single-action jump and duck budget`() {
        val runTimes = floatArrayOf(0f, 6.75f, 10f, 15f, 20f, 27.99f, 28f, 60f, 600f)
        var caseIndex = 0
        var distance = 0f
        while (distance <= 20_000f) {
            for (runTime in runTimes) {
                val pacing = SpawnFairnessEnvelope.observe(distance, runTime)
                val observation = observe(
                    leadDistancePx = pacing.requiredGapPx,
                    speedPxPerSec = pacing.scrollSpeedPxPerSec,
                    clearancePx = 220f
                )

                // Numeric gap bounds plus a single isolated action do not prove
                // safe transition between consecutive different encounters.
                assertTrue("case=$caseIndex pacing", pacing.isFiniteAndWithinDeclaredBounds)
                assertTrue("case=$caseIndex finite", observation.isFinite)
                assertTrue(
                    "case=$caseIndex distance=$distance time=$runTime jump",
                    observation.jumpFeasible
                )
                assertTrue(
                    "case=$caseIndex distance=$distance time=$runTime duck",
                    observation.duckFeasible
                )
                caseIndex++
            }
            distance += 5f
        }
    }

    @Test
    fun `clearance above physical apex is rejected regardless of generous lead`() {
        val observation = observe(
            leadDistancePx = 100_000f,
            speedPxPerSec = 1f,
            clearancePx = 541f
        )

        assertFalse(observation.jumpFeasible)
        assertTrue(observation.duckFeasible)
        assertEquals(Float.MAX_VALUE, observation.timeToRequiredRiseSeconds, 0f)
    }

    @Test
    fun `more lead cannot make a feasible action become infeasible`() {
        val clearances = floatArrayOf(0f, 40f, 120f, 220f, 360f, 500f)
        for (clearance in clearances) {
            var previousJump = false
            var previousDuck = false
            for (lead in 0..4_000 step 10) {
                val observation = observe(
                    leadDistancePx = lead.toFloat(),
                    speedPxPerSec = GameConstants.MAX_SCROLL_SPEED,
                    clearancePx = clearance
                )
                if (previousJump) {
                    assertTrue("clearance=$clearance lead=$lead jump regressed", observation.jumpFeasible)
                }
                if (previousDuck) {
                    assertTrue("clearance=$clearance lead=$lead duck regressed", observation.duckFeasible)
                }
                previousJump = observation.jumpFeasible
                previousDuck = observation.duckFeasible
            }
        }
    }

    @Test
    fun `more required rise cannot turn an infeasible jump back into feasible`() {
        val leads = floatArrayOf(120f, 300f, 600f, 1_000f, 2_000f)
        for (lead in leads) {
            var infeasibleSeen = false
            for (clearance in 0..700 step 5) {
                val observation = observe(
                    leadDistancePx = lead,
                    speedPxPerSec = GameConstants.MAX_SCROLL_SPEED,
                    clearancePx = clearance.toFloat()
                )
                if (infeasibleSeen) {
                    assertFalse("lead=$lead clearance=$clearance", observation.jumpFeasible)
                }
                if (!observation.jumpFeasible) infeasibleSeen = true
            }
        }
    }

    @Test
    fun `invalid numeric inputs fail closed without nonfinite report fields`() {
        val badValues = floatArrayOf(
            Float.NaN,
            Float.NEGATIVE_INFINITY,
            Float.POSITIVE_INFINITY,
            -Float.MAX_VALUE,
            -1f,
            0f
        )
        var caseIndex = 0
        for (bad in badValues) {
            val observation = EncounterActionFeasibility.observe(
                leadDistancePx = bad,
                approachSpeedPxPerSec = bad,
                requiredVerticalClearancePx = bad,
                jumpUpwardSpeedPxPerSec = bad,
                gravityPxPerSecSquared = bad,
                gestureDecisionSeconds = bad,
                safetyMarginSeconds = bad
            )
            assertTrue("case=$caseIndex finite", observation.isFinite)
            assertFalse("case=$caseIndex jump", observation.jumpFeasible)
            assertFalse("case=$caseIndex duck", observation.duckFeasible)
            caseIndex++
        }
    }

    @Test
    fun `every independent malformed field fails closed while report remains finite`() {
        val malformed = floatArrayOf(
            Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
            -1f, -Float.MAX_VALUE
        )
        val valid = floatArrayOf(
            2_000f, GameConstants.BASE_SCROLL_SPEED, 220f,
            -Player.MAX_JUMP_FORCE, Player.GRAVITY,
            gestureDecisionSeconds, safetyMarginSeconds
        )

        for (field in valid.indices) {
            for (bad in malformed) {
                val modified = valid.copyOf()
                modified[field] = bad
                val observation = EncounterActionFeasibility.observe(
                    leadDistancePx = modified[0],
                    approachSpeedPxPerSec = modified[1],
                    requiredVerticalClearancePx = modified[2],
                    jumpUpwardSpeedPxPerSec = modified[3],
                    gravityPxPerSecSquared = modified[4],
                    gestureDecisionSeconds = modified[5],
                    safetyMarginSeconds = modified[6]
                )
                assertTrue("field=$field value=$bad finite", observation.isFinite)
                assertFalse("field=$field value=$bad jump", observation.jumpFeasible)
                assertFalse("field=$field value=$bad duck", observation.duckFeasible)
            }
        }

        for (field in intArrayOf(1, 3, 4)) {
            val modified = valid.copyOf()
            modified[field] = 0f
            val observation = EncounterActionFeasibility.observe(
                leadDistancePx = modified[0],
                approachSpeedPxPerSec = modified[1],
                requiredVerticalClearancePx = modified[2],
                jumpUpwardSpeedPxPerSec = modified[3],
                gravityPxPerSecSquared = modified[4],
                gestureDecisionSeconds = modified[5],
                safetyMarginSeconds = modified[6]
            )
            assertTrue("zero field=$field finite", observation.isFinite)
            assertFalse("zero field=$field jump", observation.jumpFeasible)
            assertFalse("zero field=$field duck", observation.duckFeasible)
        }
    }

    private fun observe(
        leadDistancePx: Float,
        speedPxPerSec: Float,
        clearancePx: Float
    ): EncounterActionFeasibilityObservation = EncounterActionFeasibility.observe(
        leadDistancePx = leadDistancePx,
        approachSpeedPxPerSec = speedPxPerSec,
        requiredVerticalClearancePx = clearancePx,
        jumpUpwardSpeedPxPerSec = -Player.MAX_JUMP_FORCE,
        gravityPxPerSecSquared = Player.GRAVITY,
        gestureDecisionSeconds = gestureDecisionSeconds,
        safetyMarginSeconds = safetyMarginSeconds
    )
}
