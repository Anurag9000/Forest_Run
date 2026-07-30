package com.anurag9000.forestrun.engine

import android.graphics.Color
import com.anurag9000.forestrun.entities.EntityType

/**
 * All game biomes.
 *
 * Each biome defines its visual palette, preferred encounter pool, and ambient
 * light. The ordered cycle repeats after NIGHT_FOREST.
 */
enum class Biome(
    val displayName: String,
    val skyTopColour: Int,
    val skyBottomColour: Int,
    val groundColour: Int,
    val midFoliageColour: Int,
    val ambientLightFactor: Float,
    val preferredPool: List<EntityType>
) {

    MEADOW(
        displayName = "Flowering Meadow",
        skyTopColour = Color.rgb(144, 210, 255),
        skyBottomColour = Color.rgb(220, 240, 255),
        groundColour = Color.rgb(80, 160, 80),
        midFoliageColour = Color.rgb(60, 140, 60),
        ambientLightFactor = 1.0f,
        preferredPool = listOf(
            EntityType.LILY_OF_VALLEY, EntityType.HYACINTH, EntityType.CACTUS,
            EntityType.DUCK, EntityType.TIT, EntityType.CAT, EntityType.HEDGEHOG,
            EntityType.CHERRY_BLOSSOM
        )
    ),

    ORCHARD(
        displayName = "Spring Orchard",
        skyTopColour = Color.rgb(255, 200, 220),
        skyBottomColour = Color.rgb(255, 230, 200),
        groundColour = Color.rgb(100, 180, 80),
        midFoliageColour = Color.rgb(180, 100, 120),
        ambientLightFactor = 0.95f,
        preferredPool = listOf(
            EntityType.HYACINTH, EntityType.VANILLA_ORCHID, EntityType.CHERRY_BLOSSOM,
            EntityType.JACARANDA, EntityType.TIT, EntityType.CHICKADEE,
            EntityType.CAT, EntityType.FOX, EntityType.DOG
        )
    ),

    ANCIENT_GROVE(
        displayName = "Ancient Grove",
        skyTopColour = Color.rgb(60, 120, 80),
        skyBottomColour = Color.rgb(30, 80, 50),
        groundColour = Color.rgb(50, 100, 50),
        midFoliageColour = Color.rgb(30, 80, 40),
        ambientLightFactor = 0.7f,
        preferredPool = listOf(
            EntityType.EUCALYPTUS, EntityType.WEEPING_WILLOW, EntityType.BAMBOO,
            EntityType.VANILLA_ORCHID, EntityType.WOLF, EntityType.FOX,
            EntityType.EAGLE, EntityType.OWL, EntityType.HEDGEHOG
        )
    ),

    DUSK_CANYON(
        displayName = "Dusk Canyon",
        skyTopColour = Color.rgb(200, 80, 40),
        skyBottomColour = Color.rgb(255, 150, 80),
        groundColour = Color.rgb(160, 100, 60),
        midFoliageColour = Color.rgb(180, 80, 40),
        ambientLightFactor = 0.6f,
        preferredPool = listOf(
            EntityType.CACTUS, EntityType.EUCALYPTUS, EntityType.EAGLE,
            EntityType.WOLF, EntityType.FOX, EntityType.DOG,
            EntityType.BAMBOO, EntityType.JACARANDA
        )
    ),

    NIGHT_FOREST(
        displayName = "Night Forest",
        skyTopColour = Color.rgb(10, 10, 40),
        skyBottomColour = Color.rgb(20, 20, 60),
        groundColour = Color.rgb(20, 40, 30),
        midFoliageColour = Color.rgb(10, 30, 20),
        ambientLightFactor = 0.35f,
        preferredPool = listOf(
            EntityType.LILY_OF_VALLEY, EntityType.WEEPING_WILLOW, EntityType.OWL,
            EntityType.WOLF, EntityType.CAT, EntityType.CHICKADEE,
            EntityType.VANILLA_ORCHID, EntityType.BAMBOO
        )
    );

    companion object {
        private val CYCLE = listOf(MEADOW, ORCHARD, ANCIENT_GROVE, DUSK_CANYON, NIGHT_FOREST)

        /** Invalid or negative distances resolve to the safe opening biome. */
        fun at(distanceMetres: Float): Biome {
            val safeDistance = distanceMetres.takeIf { it.isFinite() && it >= 0f } ?: 0f
            val segment = (safeDistance / GameConstants.BIOME_LENGTH_METRES)
                .toLong()
                .coerceAtLeast(0L)
            return CYCLE[(segment % CYCLE.size.toLong()).toInt()]
        }

        fun next(current: Biome): Biome {
            val idx = CYCLE.indexOf(current)
            return CYCLE[(idx + 1) % CYCLE.size]
        }
    }
}
