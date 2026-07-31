package com.anurag9000.forestrun.systems

import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.entities.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `invalid recordings fail closed and replace prior playback`() {
        val ghost = GhostPlayer()
        ghost.load(sampleFrames())
        assertTrue(ghost.hasGhost)

        val malformedRuns = listOf(
            emptyList(),
            listOf(frame(t = Float.NaN)),
            listOf(frame(t = 1f), frame(t = 0.5f)),
            listOf(frame(t = 0f, x = Float.POSITIVE_INFINITY)),
            listOf(frame(t = 0f, scaleX = 0f))
        )

        malformedRuns.forEach { malformed ->
            ghost.load(malformed)
            assertFalse(ghost.hasGhost)
            assertEquals(0, ghost.frameIndexForTest)
            assertEquals(0f, ghost.elapsedForTest, 0f)
        }
    }

    @Test
    fun `non finite and negative deltas are ignored without poisoning recovery`() {
        val ghost = GhostPlayer()
        ghost.load(sampleFrames(), revealImmediately = true)

        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f).forEach { malformed ->
            ghost.update(malformed, clearContext())
        }

        assertEquals(0f, ghost.elapsedForTest, 0f)
        assertEquals(0f, ghost.visibilityAlphaForTest, 0f)
        assertTrue(ghost.elapsedForTest.isFinite())
        assertTrue(ghost.visibilityAlphaForTest.isFinite())

        ghost.update(0.25f, clearContext())

        assertEquals(0.25f, ghost.elapsedForTest, 0.0001f)
        assertTrue(ghost.visibilityAlphaForTest > 0f)
        assertTrue(ghost.visibilityAlphaForTest.isFinite())
    }

    @Test
    fun `huge finite delta jumps to the final frame and completes the wave`() {
        val frames = List(1_000) { index ->
            frame(t = index * GhostRecorder.SAMPLE_INTERVAL_S, x = 200f + index)
        }
        val ghost = GhostPlayer()
        ghost.load(frames, revealImmediately = true)

        ghost.update(Float.MAX_VALUE, clearContext())

        assertEquals(frames.lastIndex, ghost.frameIndexForTest)
        assertTrue(ghost.isWavingForTest)
        assertTrue(ghost.hasGhost)
        assertTrue(ghost.elapsedForTest.isFinite())

        ghost.update(GhostPlayer.WAVE_DURATION, clearContext())

        assertFalse(ghost.hasGhost)
    }

    @Test
    fun `malformed visibility context hides ghost without poisoning alpha`() {
        val ghost = GhostPlayer()
        ghost.load(sampleFrames(), revealImmediately = true)
        repeat(4) { ghost.update(0.1f, clearContext()) }
        val visibleAlpha = ghost.visibilityAlphaForTest
        assertTrue(visibleAlpha > 0.8f)

        val malformed = GhostPlayer.VisibilityContext(
            livePlayerX = Float.NaN,
            livePlayerY = 320f,
            livePlayerWidth = 0f,
            livePlayerHeight = Float.POSITIVE_INFINITY,
            nearbyHazardCount = -1,
            nearestHazardDistancePx = Float.NaN
        )
        ghost.update(0.1f, malformed)
        val hiddenAlpha = ghost.visibilityAlphaForTest

        assertTrue(hiddenAlpha.isFinite())
        assertTrue(hiddenAlpha < visibleAlpha)
        assertEquals(0f, ghost.denseSuppressionRemainingForTest, 0f)

        ghost.update(0.1f, clearContext())
        assertTrue(ghost.visibilityAlphaForTest.isFinite())
        assertTrue(ghost.visibilityAlphaForTest > hiddenAlpha)
    }

    @Test
    fun `invalid explicit suppression cannot poison playback timer`() {
        val ghost = GhostPlayer()
        ghost.load(sampleFrames(), revealImmediately = true)

        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f, -1f).forEach {
            ghost.suppress(it)
        }
        assertEquals(0f, ghost.suppressionRemainingForTest, 0f)

        ghost.suppress(0.4f)
        ghost.update(0.1f, clearContext())

        assertEquals(0.3f, ghost.suppressionRemainingForTest, 0.0001f)
        assertTrue(ghost.visibilityAlphaForTest.isFinite())
    }

    private fun sampleFrames(): List<GhostFrame> = listOf(
        frame(t = 0f),
        frame(t = 5f)
    )

    private fun frame(
        t: Float,
        x: Float = 200f,
        y: Float = 320f,
        stateOrdinal: Int = PlayerState.RUNNING.ordinal,
        scaleX: Float = 1f,
        scaleY: Float = 1f
    ): GhostFrame = GhostFrame(
        t = t,
        x = x,
        y = y,
        stateOrdinal = stateOrdinal,
        scaleX = scaleX,
        scaleY = scaleY
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
