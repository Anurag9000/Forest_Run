package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.EntityType
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
    fun `invalid dimensions resolve to balanced defaults`() {
        val balanced = ReadabilityProfile.entity(EntityType.OWL, screenHeight = 1080f)

        listOf(
            0f,
            -1f,
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY
        ).forEach { invalid ->
            assertEquals(DeviceDensityBucket.BALANCED, ReadabilityProfile.densityBucket(invalid))
            assertEquals(balanced, ReadabilityProfile.entity(EntityType.OWL, invalid))
            assertEquals(balanced, ReadabilityProfile.entityForGround(EntityType.OWL, invalid))
            assertEquals(1080f, ReadabilityProfile.estimateScreenHeightFromGround(invalid), 0f)
        }
    }

    @Test
    fun `spawn gap remains within canonical readability bounds`() {
        assertEquals(
            GameConstants.SPAWN_GAP_MAX_PX,
            ReadabilityProfile.spawnGapPx(0f),
            0.0001f
        )
        assertEquals(
            GameConstants.SPAWN_GAP_MIN_PX,
            ReadabilityProfile.spawnGapPx(5_000f),
            0.0001f
        )
    }

    @Test
    fun `invalid spawn distances use the opening gap`() {
        listOf(
            -1f,
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY
        ).forEach { invalid ->
            assertEquals(
                GameConstants.SPAWN_GAP_MAX_PX,
                ReadabilityProfile.spawnGapPx(invalid),
                0f
            )
        }
        assertEquals(
            GameConstants.SPAWN_GAP_MIN_PX,
            ReadabilityProfile.spawnGapPx(Float.MAX_VALUE),
            0f
        )
    }

    @Test
    fun `flora and trees now have explicit readability coverage`() {
        val lily = ReadabilityProfile.entity(EntityType.LILY_OF_VALLEY, screenHeight = 1080f)
        val hyacinth = ReadabilityProfile.entity(EntityType.HYACINTH, screenHeight = 1080f)
        val orchid = ReadabilityProfile.entity(EntityType.VANILLA_ORCHID, screenHeight = 1080f)
        val willow = ReadabilityProfile.entity(EntityType.WEEPING_WILLOW, screenHeight = 1080f)
        val jacaranda = ReadabilityProfile.entity(EntityType.JACARANDA, screenHeight = 1080f)
        val bamboo = ReadabilityProfile.entity(EntityType.BAMBOO, screenHeight = 1080f)
        val cherry = ReadabilityProfile.entity(EntityType.CHERRY_BLOSSOM, screenHeight = 1080f)
        val hedgehog = ReadabilityProfile.entity(EntityType.HEDGEHOG, screenHeight = 1080f)

        assertTrue(lily.heightPx > 0f)
        assertTrue(lily.heightPx >= 110f)
        assertTrue(lily.mercyPaddingPx >= 15f)
        assertTrue(hyacinth.heightPx >= 132f)
        assertTrue(hyacinth.hitInsetYRatio >= 0.34f)
        assertTrue(orchid.minWidthPx >= 112f)
        assertTrue(orchid.stagingPaddingPx >= 16f)
        assertTrue(willow.heightPx > 0f)
        assertTrue(willow.minWidthPx >= 304f)
        assertTrue(willow.stagingPaddingPx >= 18f)
        assertTrue(jacaranda.minWidthPx >= 258f)
        assertTrue(jacaranda.stagingPaddingPx >= 18f)
        assertTrue(bamboo.secondaryWidthPx > 0f)
        assertTrue(bamboo.secondarySpacingPx > 0f)
        assertTrue(bamboo.secondaryWidthPx >= 26f)
        assertTrue(bamboo.stagingPaddingPx >= 18f)
        assertTrue(cherry.minWidthPx >= 254f)
        assertTrue(cherry.stagingPaddingPx >= 20f)
        assertTrue(hedgehog.mercyPaddingPx >= 14f)
        assertTrue(hedgehog.stagingPaddingPx >= 10f)
        assertTrue(hedgehog.telegraphDurationSec >= 0.18f)
    }
}
