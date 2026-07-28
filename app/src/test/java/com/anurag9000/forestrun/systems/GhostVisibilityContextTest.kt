package com.anurag9000.forestrun.systems

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GhostVisibilityContextTest {

    @Test
    fun `set updates every visibility field without replacing the context`() {
        val context = GhostPlayer.VisibilityContext(
            livePlayerX = 0f,
            livePlayerY = 0f,
            livePlayerWidth = 1f,
            livePlayerHeight = 1f,
            nearbyHazardCount = 0,
            nearestHazardDistancePx = Float.POSITIVE_INFINITY
        )

        val returned = context.set(
            livePlayerX = 120f,
            livePlayerY = 340f,
            livePlayerWidth = 72f,
            livePlayerHeight = 108f,
            nearbyHazardCount = 3,
            nearestHazardDistancePx = 48f
        )

        assertSame(context, returned)
        assertEquals(120f, context.livePlayerX, 0f)
        assertEquals(340f, context.livePlayerY, 0f)
        assertEquals(72f, context.livePlayerWidth, 0f)
        assertEquals(108f, context.livePlayerHeight, 0f)
        assertEquals(3, context.nearbyHazardCount)
        assertEquals(48f, context.nearestHazardDistancePx, 0f)
    }
}
