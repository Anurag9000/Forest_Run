package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.EntityType

/**
 * Stateless utility that maps [distanceMetres] to difficulty parameters.
 *
 * All values are tuned so the game is comfortably learnable in the first
 * 500m, challenging around 1000m, and demanding past 2000m.
 */
object DifficultyScaler {

    /**
     * Returns the required world-space distance between random spawn origins.
     * Unlike a time interval, this does not change unpredictably when the
     * runner accelerates or receives a temporary speed debuff.
     */
    fun getSpawnGapPx(distanceMetres: Float): Float =
        ReadabilityProfile.spawnGapPx(distanceMetres)

    // ── Biome-based spawn pools ───────────────────────────────────────────

    /** Early game pool: ground flora and simple birds only. */
    private val POOL_EARLY = listOf(
        EntityType.CACTUS,
        EntityType.LILY_OF_VALLEY,
        EntityType.HYACINTH,
        EntityType.DUCK,
        EntityType.TIT,
        EntityType.CAT,
        EntityType.HEDGEHOG
    )

    /** Mid game pool: adds trees and more complex birds. */
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

    /** Late game pool: everything including Fox, Eagle, Bamboo, Jacaranda. */
    private val POOL_LATE = EntityType.values().toList()

    /**
     * Returns the spawn pool to use at the given distance.
     * If a [biomeManager] is supplied, uses its biome-specific pool with
     * crossfade mixing. Falls back to distance tiers in isolated tests.
     */
    fun getSpawnPool(distanceMetres: Float, biomeManager: BiomeManager? = null): List<EntityType> {
        if (biomeManager != null) return biomeManager.entityPool
        return when {
            distanceMetres < 500f  -> POOL_EARLY
            distanceMetres < 1500f -> POOL_MID
            else                   -> POOL_LATE
        }
    }
}
