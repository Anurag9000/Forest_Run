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
        steps = listOf(
            EncounterStep(0.20f, EntityType.CACTUS, 420f),
            EncounterStep(1.10f, EntityType.LILY_OF_VALLEY, 640f),
            EncounterStep(2.00f, EntityType.HYACINTH, 860f),
            EncounterStep(2.90f, EntityType.EUCALYPTUS, 1_050f),
            EncounterStep(3.85f, EntityType.VANILLA_ORCHID, 1_260f)
        )
    ),
    TREE_SHOWCASE(
        title = "Tree Showcase",
        summary = "All four tree hazards",
        steps = listOf(
            EncounterStep(0.25f, EntityType.WEEPING_WILLOW, 560f),
            EncounterStep(1.50f, EntityType.JACARANDA, 860f),
            EncounterStep(2.80f, EntityType.BAMBOO, 1_120f),
            EncounterStep(4.00f, EntityType.CHERRY_BLOSSOM, 1_380f)
        )
    ),
    BIRD_SHOWCASE(
        title = "Bird Showcase",
        summary = "Duck -> Tit -> Chickadee -> Owl -> Eagle",
        steps = listOf(
            EncounterStep(0.20f, EntityType.DUCK, 420f),
            EncounterStep(1.20f, EntityType.TIT, 720f),
            EncounterStep(2.25f, EntityType.CHICKADEE, 980f),
            EncounterStep(3.30f, EntityType.OWL, 1_180f),
            EncounterStep(4.50f, EntityType.EAGLE, 1_280f)
        )
    ),
    ANIMAL_SHOWCASE(
        title = "Animal Showcase",
        summary = "Cat -> Fox -> Wolf -> Hedgehog -> Dog",
        steps = listOf(
            EncounterStep(0.20f, EntityType.CAT, 420f),
            EncounterStep(1.35f, EntityType.FOX, 760f),
            EncounterStep(2.70f, EntityType.WOLF, 1_040f),
            EncounterStep(4.05f, EntityType.HEDGEHOG, 1_220f),
            EncounterStep(5.20f, EntityType.DOG, 1_420f, EncounterVariant.DOG_HAZARD)
        )
    ),
    DOG_BUDDY(
        title = "Dog Buddy",
        summary = "Forced harmless buddy run",
        steps = listOf(
            EncounterStep(0.20f, EntityType.DOG, 480f, EncounterVariant.DOG_BUDDY)
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
