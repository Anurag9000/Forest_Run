package com.yourname.forest_run.engine

import com.yourname.forest_run.entities.EntityType

enum class DeviceDensityBucket {
    COMPACT,
    BALANCED,
    ROOMY
}

data class EntityReadability(
    val heightPx: Float,
    val minWidthPx: Float,
    val hitInsetXRatio: Float,
    val hitInsetYRatio: Float,
    val mercyPaddingPx: Float = 12f,
    val stagingPaddingPx: Float = 10f,
    val secondaryWidthPx: Float = 0f,
    val secondarySpacingPx: Float = 0f,
    val detectionRangeBodies: Float = 3f,
    val telegraphDurationSec: Float = 0.3f,
    val movementSpeedPxPerSec: Float = 0f
)

private data class EntityReadabilityTemplate(
    val baseHeightPx: Float,
    val baseMinWidthPx: Float,
    val hitInsetXRatio: Float,
    val hitInsetYRatio: Float,
    val mercyPaddingPx: Float = 12f,
    val stagingPaddingPx: Float = 10f,
    val secondaryWidthPx: Float = 0f,
    val secondarySpacingPx: Float = 0f,
    val detectionRangeBodies: Float = 3f,
    val telegraphDurationSec: Float = 0.3f,
    val movementSpeedPxPerSec: Float = 0f
)

object ReadabilityProfile {
    private const val GROUND_RATIO = 0.82f
    private const val SPAWN_INTERVAL_MAX = 1.7f
    private const val SPAWN_INTERVAL_MIN = 0.62f
    private const val INTERVAL_RAMP_METRES = 2000f

    private val templates = mapOf(
        EntityType.CACTUS to EntityReadabilityTemplate(134f, 68f, 0.16f, 0.10f, mercyPaddingPx = 13f, stagingPaddingPx = 12f),
        EntityType.LILY_OF_VALLEY to EntityReadabilityTemplate(94f, 58f, 0.22f, 0.16f, mercyPaddingPx = 13f, stagingPaddingPx = 10f),
        EntityType.HYACINTH to EntityReadabilityTemplate(120f, 50f, 0.22f, 0.28f, mercyPaddingPx = 13f, stagingPaddingPx = 10f),
        EntityType.EUCALYPTUS to EntityReadabilityTemplate(136f, 64f, 0.18f, 0.14f, mercyPaddingPx = 13f, stagingPaddingPx = 12f),
        EntityType.VANILLA_ORCHID to EntityReadabilityTemplate(232f, 100f, 0.18f, 0.10f, mercyPaddingPx = 14f, stagingPaddingPx = 12f),
        EntityType.WEEPING_WILLOW to EntityReadabilityTemplate(692f, 282f, 0.18f, 0.10f, mercyPaddingPx = 14f, stagingPaddingPx = 12f),
        EntityType.JACARANDA to EntityReadabilityTemplate(626f, 238f, 0.16f, 0.10f, mercyPaddingPx = 14f, stagingPaddingPx = 12f),
        EntityType.BAMBOO to EntityReadabilityTemplate(0f, 0f, 0f, 0f, mercyPaddingPx = 10f, stagingPaddingPx = 14f, secondaryWidthPx = 22f, secondarySpacingPx = 40f),
        EntityType.CHERRY_BLOSSOM to EntityReadabilityTemplate(605f, 238f, 0.16f, 0.10f, mercyPaddingPx = 14f, stagingPaddingPx = 16f),
        EntityType.CAT to EntityReadabilityTemplate(84f, 70f, 0.14f, 0.10f, mercyPaddingPx = 14f, stagingPaddingPx = 12f),
        EntityType.FOX to EntityReadabilityTemplate(102f, 84f, 0.12f, 0.09f, mercyPaddingPx = 14f, stagingPaddingPx = 12f, detectionRangeBodies = 3.2f),
        EntityType.WOLF to EntityReadabilityTemplate(118f, 94f, 0.11f, 0.07f, mercyPaddingPx = 14f, stagingPaddingPx = 14f, telegraphDurationSec = 1.05f),
        EntityType.DOG to EntityReadabilityTemplate(104f, 82f, 0.13f, 0.07f, mercyPaddingPx = 14f, stagingPaddingPx = 12f),
        EntityType.HEDGEHOG to EntityReadabilityTemplate(66f, 50f, 0.08f, 0.08f, mercyPaddingPx = 12f, stagingPaddingPx = 8f),
        EntityType.DUCK to EntityReadabilityTemplate(96f, 68f, 0.10f, 0.10f, mercyPaddingPx = 13f, stagingPaddingPx = 12f),
        EntityType.TIT to EntityReadabilityTemplate(62f, 50f, 0.06f, 0.06f, mercyPaddingPx = 12f, stagingPaddingPx = 10f),
        EntityType.CHICKADEE to EntityReadabilityTemplate(54f, 44f, 0.06f, 0.06f, mercyPaddingPx = 12f, stagingPaddingPx = 10f),
        EntityType.OWL to EntityReadabilityTemplate(92f, 68f, 0.10f, 0.10f, mercyPaddingPx = 14f, stagingPaddingPx = 14f, telegraphDurationSec = 0.24f, movementSpeedPxPerSec = 620f),
        EntityType.EAGLE to EntityReadabilityTemplate(98f, 72f, 0.10f, 0.10f, mercyPaddingPx = 14f, stagingPaddingPx = 14f, telegraphDurationSec = 0.38f, movementSpeedPxPerSec = 720f)
    )

    fun estimateScreenHeightFromGround(groundY: Float): Float = groundY / GROUND_RATIO

    fun densityBucket(screenHeight: Float): DeviceDensityBucket = when {
        screenHeight < 760f -> DeviceDensityBucket.COMPACT
        screenHeight > 1320f -> DeviceDensityBucket.ROOMY
        else -> DeviceDensityBucket.BALANCED
    }

    fun entity(type: EntityType, screenHeight: Float): EntityReadability {
        val template = templates[type] ?: return EntityReadability(
            heightPx = 88f,
            minWidthPx = 64f,
            hitInsetXRatio = 0.1f,
            hitInsetYRatio = 0.1f
        )
        val scale = when (densityBucket(screenHeight)) {
            DeviceDensityBucket.COMPACT -> 0.94f
            DeviceDensityBucket.BALANCED -> 1f
            DeviceDensityBucket.ROOMY -> 1.08f
        }
        return EntityReadability(
            heightPx = template.baseHeightPx * scale,
            minWidthPx = template.baseMinWidthPx * scale,
            hitInsetXRatio = template.hitInsetXRatio,
            hitInsetYRatio = template.hitInsetYRatio,
            mercyPaddingPx = template.mercyPaddingPx * scale,
            stagingPaddingPx = template.stagingPaddingPx * scale,
            secondaryWidthPx = template.secondaryWidthPx * scale,
            secondarySpacingPx = template.secondarySpacingPx * scale,
            detectionRangeBodies = template.detectionRangeBodies,
            telegraphDurationSec = template.telegraphDurationSec,
            movementSpeedPxPerSec = template.movementSpeedPxPerSec * scale
        )
    }

    fun entityForGround(type: EntityType, groundY: Float): EntityReadability =
        entity(type, estimateScreenHeightFromGround(groundY))

    fun spawnInterval(distanceMetres: Float): Float {
        val t = (distanceMetres / INTERVAL_RAMP_METRES).coerceIn(0f, 1f)
        return SPAWN_INTERVAL_MAX - t * (SPAWN_INTERVAL_MAX - SPAWN_INTERVAL_MIN)
    }
}
