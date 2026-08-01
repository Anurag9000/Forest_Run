package com.anurag9000.forestrun.systems

import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.entities.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GhostPlayerMalformedDeltaAdmissionTest {

    @Test
    fun `malformed delta cannot change visible playback suppression or position`() {
        val ghost = GhostPlayer()
        ghost.load(
            listOf(
                frame(t = 0f, x = 200f),
                frame(t = 5f, x = 260f)
            ),
            revealImmediately = true
        )
        repeat(8) { ghost.update(0.1f, clearContext()) }

        val elapsedBefore = ghost.elapsedForTest
        val frameBefore = ghost.frameIndexForTest
        val alphaBefore = ghost.visibilityAlphaForTest
        val denseBefore = ghost.denseSuppressionRemainingForTest
        assertTrue(alphaBefore > 0.8f)

        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f, -0.1f).forEach {
            ghost.update(it, denseContext())
            assertEquals(elapsedBefore, ghost.elapsedForTest, 0f)
            assertEquals(frameBefore, ghost.frameIndexForTest)
            assertEquals(alphaBefore, ghost.visibilityAlphaForTest, 0f)
            assertEquals(denseBefore, ghost.denseSuppressionRemainingForTest, 0f)
        }
    }

    private fun frame(t: Float, x: Float): GhostFrame = GhostFrame(
        t = t,
        x = x,
        y = 320f,
        stateOrdinal = PlayerState.RUNNING.ordinal,
        scaleX = 1f,
        scaleY = 1f
    )

    private fun clearContext(): GhostPlayer.VisibilityContext =
        GhostPlayer.VisibilityContext(
            livePlayerX = 520f,
            livePlayerY = 320f,
            livePlayerWidth = Player.BASE_WIDTH,
            livePlayerHeight = Player.BASE_HEIGHT,
            nearbyHazardCount = 0,
            nearestHazardDistancePx = Float.POSITIVE_INFINITY
        )

    private fun denseContext(): GhostPlayer.VisibilityContext =
        GhostPlayer.VisibilityContext(
            livePlayerX = 250f,
            livePlayerY = 320f,
            livePlayerWidth = Player.BASE_WIDTH,
            livePlayerHeight = Player.BASE_HEIGHT,
            nearbyHazardCount = 3,
            nearestHazardDistancePx = Player.BASE_WIDTH
        )
}
