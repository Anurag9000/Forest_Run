package com.yourname.forest_run.engine

import com.yourname.forest_run.entities.EntityType

data class EncounterStep(
    val atSeconds: Float,
    val type: EntityType,
    val xOffset: Float,
    val variant: EncounterVariant = EncounterVariant.DEFAULT
)

enum class EncounterScenario(
    val title: String,
    val summary: String,
    val forcedBiome: Biome? = null,
    val startsWithBloom: Boolean = false,
    val allowGhostPlayback: Boolean = false,
    val steps: List<EncounterStep>
) {
    OPENING_READABILITY(
        title = "Opening Readability",
        summary = "Duck -> Lily -> Cat -> Tit",
        steps = listOf(
            EncounterStep(0.15f, EntityType.DUCK, 380f),
            EncounterStep(1.00f, EntityType.LILY_OF_VALLEY, 640f),
            EncounterStep(1.90f, EntityType.CAT, 920f),
            EncounterStep(2.85f, EntityType.TIT, 1_180f)
        )
    ),
    FLORA_SHOWCASE(
        title = "Flora Showcase",
        summary = "All five flora in sequence",
        forcedBiome = Biome.MEADOW,
        steps = listOf(
            EncounterStep(0.20f, EntityType.CACTUS, 420f),
            EncounterStep(1.10f, EntityType.LILY_OF_VALLEY, 640f),
            EncounterStep(2.00f, EntityType.HYACINTH, 860f),
            EncounterStep(2.90f, EntityType.EUCALYPTUS, 1_050f),
            EncounterStep(3.85f, EntityType.VANILLA_ORCHID, 1_260f)
        )
    ),
    CACTUS_READ(
        title = "Cactus Read",
        summary = "Baseline silhouette and fair jump timing",
        forcedBiome = Biome.DUSK_CANYON,
        steps = listOf(
            EncounterStep(0.20f, EntityType.CACTUS, 420f),
            EncounterStep(1.45f, EntityType.CACTUS, 860f)
        )
    ),
    LILY_GLOW(
        title = "Lily Glow",
        summary = "Night glow and seed-lure readability",
        forcedBiome = Biome.NIGHT_FOREST,
        steps = listOf(
            EncounterStep(0.20f, EntityType.LILY_OF_VALLEY, 430f),
            EncounterStep(1.65f, EntityType.LILY_OF_VALLEY, 890f)
        )
    ),
    HYACINTH_BRUSH(
        title = "Hyacinth Brush",
        summary = "Brush-vs-hit lane read",
        forcedBiome = Biome.MEADOW,
        steps = listOf(
            EncounterStep(0.20f, EntityType.HYACINTH, 430f),
            EncounterStep(1.55f, EntityType.HYACINTH, 870f)
        )
    ),
    EUCALYPTUS_WHIP(
        title = "Eucalyptus Whip",
        summary = "Lean and whip timing pass",
        forcedBiome = Biome.ANCIENT_GROVE,
        steps = listOf(
            EncounterStep(0.20f, EntityType.EUCALYPTUS, 460f),
            EncounterStep(1.75f, EntityType.EUCALYPTUS, 930f)
        )
    ),
    ORCHID_WINDOW(
        title = "Orchid Window",
        summary = "Low/high window readability",
        forcedBiome = Biome.ORCHARD,
        steps = listOf(
            EncounterStep(0.20f, EntityType.VANILLA_ORCHID, 450f),
            EncounterStep(1.85f, EntityType.VANILLA_ORCHID, 940f)
        )
    ),
    TREE_SHOWCASE(
        title = "Tree Showcase",
        summary = "All four tree hazards",
        forcedBiome = Biome.ANCIENT_GROVE,
        steps = listOf(
            EncounterStep(0.25f, EntityType.WEEPING_WILLOW, 560f),
            EncounterStep(1.50f, EntityType.JACARANDA, 860f),
            EncounterStep(2.80f, EntityType.BAMBOO, 1_120f),
            EncounterStep(4.00f, EntityType.CHERRY_BLOSSOM, 1_380f)
        )
    ),
    WILLOW_CURTAIN(
        title = "Willow Curtain",
        summary = "Curtain-read pressure and silhouette",
        forcedBiome = Biome.ANCIENT_GROVE,
        steps = listOf(
            EncounterStep(0.25f, EntityType.WEEPING_WILLOW, 560f),
            EncounterStep(1.90f, EntityType.WEEPING_WILLOW, 980f)
        )
    ),
    JACARANDA_PETALS(
        title = "Jacaranda Petals",
        summary = "Petal curtain readability",
        forcedBiome = Biome.ORCHARD,
        steps = listOf(
            EncounterStep(0.25f, EntityType.JACARANDA, 560f),
            EncounterStep(1.90f, EntityType.JACARANDA, 980f)
        )
    ),
    BAMBOO_GAP(
        title = "Bamboo Gap",
        summary = "Precision threading through trunks",
        forcedBiome = Biome.ANCIENT_GROVE,
        steps = listOf(
            EncounterStep(0.25f, EntityType.BAMBOO, 560f),
            EncounterStep(2.05f, EntityType.BAMBOO, 1_040f)
        )
    ),
    CHERRY_GUST(
        title = "Cherry Gust",
        summary = "Windy petal pressure pass",
        forcedBiome = Biome.ORCHARD,
        steps = listOf(
            EncounterStep(0.25f, EntityType.CHERRY_BLOSSOM, 560f),
            EncounterStep(2.00f, EntityType.CHERRY_BLOSSOM, 1_020f)
        )
    ),
    BIRD_SHOWCASE(
        title = "Bird Showcase",
        summary = "Duck -> Tit -> Chickadee -> Owl -> Eagle",
        forcedBiome = Biome.NIGHT_FOREST,
        steps = listOf(
            EncounterStep(0.20f, EntityType.DUCK, 420f),
            EncounterStep(1.20f, EntityType.TIT, 720f),
            EncounterStep(2.25f, EntityType.CHICKADEE, 980f),
            EncounterStep(3.30f, EntityType.OWL, 1_180f),
            EncounterStep(4.50f, EntityType.EAGLE, 1_280f)
        )
    ),
    DUCK_TEACH(
        title = "Duck Teach",
        summary = "Readable low-fly duck cue",
        forcedBiome = Biome.MEADOW,
        steps = listOf(
            EncounterStep(0.20f, EntityType.DUCK, 420f),
            EncounterStep(1.25f, EntityType.DUCK, 780f)
        )
    ),
    TIT_WAVE(
        title = "Tit Wave",
        summary = "Sine-wave flock rhythm",
        forcedBiome = Biome.ORCHARD,
        steps = listOf(
            EncounterStep(0.20f, EntityType.TIT, 420f),
            EncounterStep(1.30f, EntityType.TIT, 820f)
        )
    ),
    CHICKADEE_SWERVE(
        title = "Chickadee Swerve",
        summary = "Erratic but readable altitude changes",
        forcedBiome = Biome.ORCHARD,
        steps = listOf(
            EncounterStep(0.20f, EntityType.CHICKADEE, 430f),
            EncounterStep(1.35f, EntityType.CHICKADEE, 840f)
        )
    ),
    OWL_DIVE(
        title = "Owl Dive",
        summary = "Night alert and dive telegraph",
        forcedBiome = Biome.NIGHT_FOREST,
        steps = listOf(
            EncounterStep(0.20f, EntityType.OWL, 460f),
            EncounterStep(2.05f, EntityType.OWL, 1_020f)
        )
    ),
    EAGLE_MARK(
        title = "Eagle Mark",
        summary = "Lock-on reticle and fear beat",
        forcedBiome = Biome.DUSK_CANYON,
        steps = listOf(
            EncounterStep(0.20f, EntityType.EAGLE, 520f),
            EncounterStep(2.40f, EntityType.EAGLE, 1_060f)
        )
    ),
    ANIMAL_SHOWCASE(
        title = "Animal Showcase",
        summary = "Cat -> Fox -> Wolf -> Hedgehog -> Dog",
        forcedBiome = Biome.ANCIENT_GROVE,
        steps = listOf(
            EncounterStep(0.20f, EntityType.CAT, 420f),
            EncounterStep(1.35f, EntityType.FOX, 760f),
            EncounterStep(2.70f, EntityType.WOLF, 1_040f),
            EncounterStep(4.05f, EntityType.HEDGEHOG, 1_220f),
            EncounterStep(5.20f, EntityType.DOG, 1_420f, EncounterVariant.DOG_HAZARD)
        )
    ),
    CAT_KINDNESS(
        title = "Cat Kindness",
        summary = "Kindness bonus and spare warmth",
        forcedBiome = Biome.MEADOW,
        steps = listOf(
            EncounterStep(0.20f, EntityType.CAT, 420f),
            EncounterStep(1.65f, EntityType.CAT, 920f)
        )
    ),
    FOX_MIRROR(
        title = "Fox Mirror",
        summary = "Mirror-jump personality read",
        forcedBiome = Biome.ORCHARD,
        steps = listOf(
            EncounterStep(0.20f, EntityType.FOX, 460f),
            EncounterStep(1.85f, EntityType.FOX, 980f)
        )
    ),
    WOLF_CHARGE(
        title = "Wolf Charge",
        summary = "Howl, charge, and spare tension",
        forcedBiome = Biome.ANCIENT_GROVE,
        steps = listOf(
            EncounterStep(0.20f, EntityType.WOLF, 500f),
            EncounterStep(2.30f, EntityType.WOLF, 1_080f)
        )
    ),
    HEDGEHOG_DEBUFF(
        title = "Hedgehog Debuff",
        summary = "Non-lethal debuff readability",
        forcedBiome = Biome.MEADOW,
        steps = listOf(
            EncounterStep(0.20f, EntityType.HEDGEHOG, 420f),
            EncounterStep(1.70f, EntityType.HEDGEHOG, 920f)
        )
    ),
    DOG_HAZARD(
        title = "Dog Hazard",
        summary = "Bark projectile timing",
        forcedBiome = Biome.ORCHARD,
        steps = listOf(
            EncounterStep(0.20f, EntityType.DOG, 480f, EncounterVariant.DOG_HAZARD),
            EncounterStep(2.10f, EntityType.DOG, 1_020f, EncounterVariant.DOG_HAZARD)
        )
    ),
    DOG_BUDDY(
        title = "Dog Buddy",
        summary = "Forced harmless buddy run",
        forcedBiome = Biome.ORCHARD,
        steps = listOf(
            EncounterStep(0.20f, EntityType.DOG, 480f, EncounterVariant.DOG_BUDDY)
        )
    ),
    BLOOM_SHOWCASE(
        title = "Bloom Showcase",
        summary = "Start in Bloom and convert the full lane",
        forcedBiome = Biome.MEADOW,
        startsWithBloom = true,
        steps = listOf(
            EncounterStep(0.20f, EntityType.CACTUS, 420f),
            EncounterStep(0.75f, EntityType.LILY_OF_VALLEY, 620f),
            EncounterStep(1.35f, EntityType.CAT, 860f),
            EncounterStep(2.00f, EntityType.DUCK, 1_080f),
            EncounterStep(2.70f, EntityType.WEEPING_WILLOW, 1_320f)
        )
    ),
    GHOST_READABILITY(
        title = "Ghost Readability",
        summary = "Keep ghost visible while the live lane stays readable",
        forcedBiome = Biome.MEADOW,
        allowGhostPlayback = true,
        steps = listOf(
            EncounterStep(0.25f, EntityType.DUCK, 420f),
            EncounterStep(1.25f, EntityType.CAT, 820f),
            EncounterStep(2.35f, EntityType.HYACINTH, 1_100f)
        )
    ),
    REST_RETURN_LOOP(
        title = "Rest Return Loop",
        summary = "Take a clear hit and verify the fade back to Garden",
        forcedBiome = Biome.DUSK_CANYON,
        steps = listOf(
            EncounterStep(0.40f, EntityType.CACTUS, 360f),
            EncounterStep(1.90f, EntityType.WOLF, 900f)
        )
    );
}

data class EncounterSpawnDirective(
    val type: EntityType,
    val xOffset: Float,
    val variant: EncounterVariant
)

class EncounterDirector {

    private var selectedIndex = 0
    private var elapsedSeconds = 0f
    private var nextStepIndex = 0

    var activeScenario: EncounterScenario? = null
        private set

    val selectedScenario: EncounterScenario
        get() = EncounterScenario.entries[selectedIndex]

    val scenarioCount: Int
        get() = EncounterScenario.entries.size

    val isScenarioActive: Boolean
        get() = activeScenario != null

    val remainingSteps: Int
        get() = activeScenario?.steps?.size?.minus(nextStepIndex)?.coerceAtLeast(0) ?: 0

    fun previousScenario() {
        selectedIndex = if (selectedIndex == 0) EncounterScenario.entries.lastIndex else selectedIndex - 1
    }

    fun nextScenario() {
        selectedIndex = (selectedIndex + 1) % EncounterScenario.entries.size
    }

    fun startSelectedScenario() {
        activeScenario = selectedScenario
        elapsedSeconds = 0f
        nextStepIndex = 0
    }

    fun stopScenario() {
        activeScenario = null
        elapsedSeconds = 0f
        nextStepIndex = 0
    }

    fun advance(deltaTime: Float): List<EncounterSpawnDirective> {
        val scenario = activeScenario ?: return emptyList()
        elapsedSeconds += deltaTime
        if (nextStepIndex >= scenario.steps.size) return emptyList()

        val due = mutableListOf<EncounterSpawnDirective>()
        while (nextStepIndex < scenario.steps.size && scenario.steps[nextStepIndex].atSeconds <= elapsedSeconds) {
            val step = scenario.steps[nextStepIndex++]
            due += EncounterSpawnDirective(step.type, step.xOffset, step.variant)
        }
        return due
    }
}
