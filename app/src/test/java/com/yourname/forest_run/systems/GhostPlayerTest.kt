package com.yourname.forest_run.systems

import com.yourname.forest_run.entities.Player
import com.yourname.forest_run.entities.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GhostPlayerTest {

    @Test
    fun `ghost stays hidden before reveal delay and fades in afterward`() {
        val ghost = GhostPlayer()
        ghost.load(sampleFrames())

        ghost.update(0.9f)
        assertEquals(0f, ghost.visibilityAlphaForTest, 0.0001f)

        ghost.update(0.7f)
        assertTrue(ghost.visibilityAlphaForTest > 0f)
    }

    @Test
    fun `ghost fades much lower when overlapping the live player`() {
        val overlappingGhost = GhostPlayer()
        overlappingGhost.load(sampleFrames(), revealImmediately = true)

        repeat(6) {
            overlappingGhost.update(0.1f, overlapContext())
        }

        val farGhost = GhostPlayer()
        farGhost.load(sampleFrames(), revealImmediately = true)

        repeat(6) {
            farGhost.update(0.1f, clearContext())
        }

        assertTrue(overlappingGhost.visibilityAlphaForTest < 0.08f)
        assertTrue(farGhost.visibilityAlphaForTest > 0.70f)
    }

    @Test
    fun `dense hazard window suppresses ghost and reentry is smooth`() {
        val ghost = GhostPlayer()
        ghost.load(sampleFrames(), revealImmediately = true)

        repeat(8) {
            ghost.update(0.1f, clearContext())
        }
        val settledAlpha = ghost.visibilityAlphaForTest
        assertTrue(settledAlpha > 0.80f)

        ghost.update(0.05f, denseContext())
        assertTrue(ghost.denseSuppressionRemainingForTest > 0f)

        repeat(5) {
            ghost.update(0.1f, denseContext())
        }
        assertTrue(ghost.visibilityAlphaForTest < 0.05f)

        repeat(4) {
            ghost.update(0.1f, clearContext())
        }
        val earlyReturnAlpha = ghost.visibilityAlphaForTest
        assertTrue(earlyReturnAlpha > 0f)
        assertTrue(earlyReturnAlpha < 0.55f)

        repeat(5) {
            ghost.update(0.1f, clearContext())
        }
        assertTrue(ghost.visibilityAlphaForTest > earlyReturnAlpha)
    }

    private fun sampleFrames(): List<GhostFrame> = listOf(
        GhostFrame(
            t = 0f,
            x = 200f,
            y = 320f,
            stateOrdinal = PlayerState.RUNNING.ordinal,
            scaleX = 1f,
            scaleY = 1f
        ),
        GhostFrame(
            t = 5f,
            x = 200f,
            y = 320f,
            stateOrdinal = PlayerState.RUNNING.ordinal,
            scaleX = 1f,
            scaleY = 1f
        )
    )

    private fun clearContext(): GhostPlayer.VisibilityContext =
        GhostPlayer.VisibilityContext(
            livePlayerX = 340f,
            livePlayerY = 320f,
            livePlayerWidth = Player.BASE_WIDTH,
            livePlayerHeight = Player.BASE_HEIGHT,
            nearbyHazardCount = 0,
            nearestHazardDistancePx = Float.POSITIVE_INFINITY
        )

    private fun overlapContext(): GhostPlayer.VisibilityContext =
        GhostPlayer.VisibilityContext(
            livePlayerX = 206f,
            livePlayerY = 324f,
            livePlayerWidth = Player.BASE_WIDTH,
            livePlayerHeight = Player.BASE_HEIGHT,
            nearbyHazardCount = 0,
            nearestHazardDistancePx = Float.POSITIVE_INFINITY
        )

    private fun denseContext(): GhostPlayer.VisibilityContext =
        GhostPlayer.VisibilityContext(
            livePlayerX = 260f,
            livePlayerY = 320f,
            livePlayerWidth = Player.BASE_WIDTH,
            livePlayerHeight = Player.BASE_HEIGHT,
            nearbyHazardCount = 3,
            nearestHazardDistancePx = Player.BASE_WIDTH * 1.1f
        )
}
