package com.yourname.forest_run.engine

import com.yourname.forest_run.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadabilityProfileTest {

    @Test
    fun `entity sizes scale across compact and roomy devices`() {
        val compact = ReadabilityProfile.entity(EntityType.CAT, screenHeight = 720f)
        val roomy = ReadabilityProfile.entity(EntityType.CAT, screenHeight = 1440f)

        assertTrue(compact.heightPx < roomy.heightPx)
        assertTrue(compact.minWidthPx < roomy.minWidthPx)
    }

    @Test
    fun `ground helper preserves balanced defaults`() {
        val fromGround = ReadabilityProfile.entityForGround(EntityType.OWL, groundY = 885.6f)
        val direct = ReadabilityProfile.entity(EntityType.OWL, screenHeight = 1080f)

        assertEquals(direct.heightPx, fromGround.heightPx, 0.0001f)
        assertEquals(direct.telegraphDurationSec, fromGround.telegraphDurationSec, 0.0001f)
        assertEquals(direct.movementSpeedPxPerSec, fromGround.movementSpeedPxPerSec, 0.0001f)
    }

    @Test
    fun `spawn interval remains within canonical readability bounds`() {
        assertEquals(1.7f, ReadabilityProfile.spawnInterval(0f), 0.0001f)
        assertEquals(0.62f, ReadabilityProfile.spawnInterval(5_000f), 0.0001f)
    }

    @Test
    fun `flora and trees now have explicit readability coverage`() {
        val lily = ReadabilityProfile.entity(EntityType.LILY_OF_VALLEY, screenHeight = 1080f)
        val willow = ReadabilityProfile.entity(EntityType.WEEPING_WILLOW, screenHeight = 1080f)
        val bamboo = ReadabilityProfile.entity(EntityType.BAMBOO, screenHeight = 1080f)

        assertTrue(lily.heightPx > 0f)
        assertTrue(willow.heightPx > 0f)
        assertTrue(bamboo.secondaryWidthPx > 0f)
        assertTrue(bamboo.secondarySpacingPx > 0f)
    }
}
