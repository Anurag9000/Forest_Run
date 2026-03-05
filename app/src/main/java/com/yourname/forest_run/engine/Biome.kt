package com.yourname.forest_run.engine

import android.graphics.Color
import com.yourname.forest_run.entities.EntityType

/**
 * All game biomes.
 *
 * Each biome defines:
 * - Sky gradient (top and bottom colour for background layer 0)
 * - Ground tint (floor colour on layer 3)
 * - Midground foliage tint (layer 1 and layer 2 base colours)
 * - Preferred entity pool (overrides DifficultyScaler.getSpawnPool())
 * - Ambient light multiplier (1.0 = day, 0.35 = night)
 *
 * Biomes cycle in order: MEADOW → ORCHARD → ANCIENT_GROVE → DUSK_CANYON → NIGHT_FOREST → repeat.
 * Each biome lasts [BIOME_LENGTH_METRES] metres (defined in GameConstants).
 */
enum class Biome(
    val displayName: String,
    val skyTopColour: Int,
    val skyBottomColour: Int,
    val groundColour: Int,
    val midFoliageColour: Int,
    val ambientLightFactor: Float,      // 0..1 applied as alpha overlay
    val preferredPool: List<EntityType>
) {

    MEADOW(
        displayName          = "Flowering Meadow",
        skyTopColour         = Color.rgb(144, 210, 255),
        skyBottomColour      = Color.rgb(220, 240, 255),
        groundColour         = Color.rgb(80, 160, 80),
        midFoliageColour     = Color.rgb(60, 140, 60),
        ambientLightFactor   = 1.0f,
        preferredPool        = listOf(
            EntityType.LILY_OF_VALLEY, EntityType.HYACINTH, EntityType.CACTUS,
            EntityType.DUCK, EntityType.TIT, EntityType.CAT, EntityType.HEDGEHOG,
            EntityType.CHERRY_BLOSSOM
        )
    ),

    ORCHARD(
        displayName          = "Spring Orchard",
        skyTopColour         = Color.rgb(255, 200, 220),
        skyBottomColour      = Color.rgb(255, 230, 200),
        groundColour         = Color.rgb(100, 180, 80),
        midFoliageColour     = Color.rgb(180, 100, 120),
        ambientLightFactor   = 0.95f,
        preferredPool        = listOf(
            EntityType.HYACINTH, EntityType.VANILLA_ORCHID, EntityType.CHERRY_BLOSSOM,
            EntityType.JACARANDA, EntityType.TIT, EntityType.CHICKADEE,
            EntityType.CAT, EntityType.FOX, EntityType.DOG
        )
    ),

    ANCIENT_GROVE(
        displayName          = "Ancient Grove",
        skyTopColour         = Color.rgb(60, 120, 80),
        skyBottomColour      = Color.rgb(30, 80, 50),
        groundColour         = Color.rgb(50, 100, 50),
        midFoliageColour     = Color.rgb(30, 80, 40),
        ambientLightFactor   = 0.7f,
        preferredPool        = listOf(
            EntityType.EUCALYPTUS, EntityType.WEEPING_WILLOW, EntityType.BAMBOO,
            EntityType.VANILLA_ORCHID, EntityType.WOLF, EntityType.FOX,
            EntityType.EAGLE, EntityType.OWL, EntityType.HEDGEHOG
        )
    ),

    DUSK_CANYON(
        displayName          = "Dusk Canyon",
        skyTopColour         = Color.rgb(200, 80, 40),
        skyBottomColour      = Color.rgb(255, 150, 80),
        groundColour         = Color.rgb(160, 100, 60),
        midFoliageColour     = Color.rgb(180, 80, 40),
        ambientLightFactor   = 0.6f,
        preferredPool        = listOf(
            EntityType.CACTUS, EntityType.EUCALYPTUS, EntityType.EAGLE,
            EntityType.WOLF, EntityType.FOX, EntityType.DOG,
            EntityType.BAMBOO, EntityType.JACARANDA
        )
    ),

    NIGHT_FOREST(
        displayName          = "Night Forest",
        skyTopColour         = Color.rgb(10, 10, 40),
        skyBottomColour      = Color.rgb(20, 20, 60),
        groundColour         = Color.rgb(20, 40, 30),
        midFoliageColour     = Color.rgb(10, 30, 20),
        ambientLightFactor   = 0.35f,   // Dark — entities lit by their own particle glow (Phase 14)
        preferredPool        = listOf(
            EntityType.LILY_OF_VALLEY, EntityType.WEEPING_WILLOW, EntityType.OWL,
            EntityType.WOLF, EntityType.CAT, EntityType.CHICKADEE,
            EntityType.VANILLA_ORCHID, EntityType.BAMBOO
        )
    );

    companion object {
        /** Ordered biome cycle (repeats after the last biome). */
        private val CYCLE = listOf(MEADOW, ORCHARD, ANCIENT_GROVE, DUSK_CANYON, NIGHT_FOREST)

        /**
         * Returns the biome that should be playing at [distanceMetres].
         * Uses GameConstants.BIOME_LENGTH_METRES as the segment length.
         */
        fun at(distanceMetres: Float): Biome {
            val segment = (distanceMetres / GameConstants.BIOME_LENGTH_METRES).toInt()
            return CYCLE[segment % CYCLE.size]
        }

        /** Returns the NEXT biome in the cycle — used by the crossfade system. */
        fun next(current: Biome): Biome {
            val idx = CYCLE.indexOf(current)
            return CYCLE[(idx + 1) % CYCLE.size]
        }
    }
}
