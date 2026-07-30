package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.EntityType

/** Stateless mapping from safe run distance to spawn pacing and encounter pools. */
object DifficultyScaler {

    private val POOL_EARLY = listOf(
        EntityType.CACTUS,
        EntityType.LILY_OF_VALLEY,
        EntityType.HYACINTH,
        EntityType.DUCK,
        EntityType.TIT,
        EntityType.CAT,
        EntityType.HEDGEHOG
    )

    private val POOL_MID = listOf(
        EntityType.CACTUS,
        EntityType.EUCALYPTUS,
        EntityType.HYACINTH,
        EntityType.VANILLA_ORCHID,
        EntityType.WEEPING_WILLOW,
        EntityType.CHERRY_BLOSSOM,
        EntityType.DUCK,
        EntityType.TIT,
        EntityType.CHICKADEE,
        EntityType.OWL,
        EntityType.CAT,
        EntityType.WOLF,
        EntityType.HEDGEHOG,
        EntityType.DOG
    )

    private val POOL_LATE = EntityType.entries.toList()

    fun getSpawnGapPx(distanceMetres: Float): Float =
        ReadabilityProfile.spawnGapPx(safeDistance(distanceMetres))

    fun getSpawnPool(
        distanceMetres: Float,
        biomeManager: BiomeManager? = null
    ): List<EntityType> {
        if (biomeManager != null) return biomeManager.entityPool
        return when (safeDistance(distanceMetres)) {
            in 0f..<500f -> POOL_EARLY
            in 500f..<1_500f -> POOL_MID
            else -> POOL_LATE
        }
    }

    private fun safeDistance(distanceMetres: Float): Float =
        distanceMetres.takeIf { it.isFinite() && it >= 0f } ?: 0f
}
