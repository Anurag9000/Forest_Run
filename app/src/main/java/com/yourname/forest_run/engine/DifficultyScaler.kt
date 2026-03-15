package com.yourname.forest_run.engine

import com.yourname.forest_run.entities.EntityType

/**
 * Stateless utility that maps [distanceMetres] to difficulty parameters.
 *
 * All values tuned so the game is comfortably learnable in the first 500m,
 * challenging around 1000m, and sweat-inducing past 2000m.
 */
object DifficultyScaler {

    // ── Spawn interval ────────────────────────────────────────────────────
    /** Max gap in seconds between entity spawns (very easy — early game). */
    private const val SPAWN_INTERVAL_MAX = 1.7f
    /** Min gap (hard cap to avoid entity congestion). */
    private const val SPAWN_INTERVAL_MIN = 0.62f
    /** Metres over which we fully ramp from max to min interval. */
    private const val INTERVAL_RAMP_METRES = 2000f

    /**
     * Returns the number of seconds to wait before the next entity spawns.
     * Shrinks linearly with distance, clamped at [SPAWN_INTERVAL_MIN].
     */
    fun getSpawnInterval(distanceMetres: Float): Float {
        val t = (distanceMetres / INTERVAL_RAMP_METRES).coerceIn(0f, 1f)
        return SPAWN_INTERVAL_MAX - t * (SPAWN_INTERVAL_MAX - SPAWN_INTERVAL_MIN)
    }

    // ── Biome-based spawn pools ───────────────────────────────────────────

    /**
     * Early game pool: ground flora and simple birds only.
     * No wolf, fox, or bamboo yet.
     */
    private val POOL_EARLY = listOf(
        EntityType.CACTUS,
        EntityType.LILY_OF_VALLEY,
        EntityType.HYACINTH,
        EntityType.DUCK,
        EntityType.TIT,
        EntityType.CAT,
        EntityType.HEDGEHOG
    )

    /**
     * Mid game pool: adds trees and more complex birds.
     */
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

    /**
     * Late game pool: everything including Fox, Eagle, Bamboo, Jacaranda.
     */
    private val POOL_LATE = EntityType.values().toList()

    /**
     * Returns the spawn pool to use at the given distance.
     * If a [biomeManager] is supplied, uses its biome-specific pool (with crossfade mixing).
     * Falls back to the distance-tiered pools for test contexts without a BiomeManager.
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
