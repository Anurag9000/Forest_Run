package com.anurag9000.forestrun.engine

import android.content.Context
import com.anurag9000.forestrun.entities.EntityType

enum class StoryFragmentType {
    REST,
    GARDEN_REFLECTION,
    MEMORY_PAGE
}

data class StoryFragment(
    val id: String,
    val type: StoryFragmentType,
    val text: String,
    val unlocksPageId: String? = null
)

object StoryFragmentSystem {

    fun restQuote(context: Context, summary: RunSummary, biome: Biome, killer: EntityType?): String {
        val appContext = context.applicationContext
        val fragment = selectRestFragment(appContext, summary, biome, killer)
        fragment.unlocksPageId?.let { unlockMemoryPage(appContext, it) }
        unlockRestContextPages(appContext, summary, biome, killer)
        return fragment.text
    }

    fun gardenReflection(context: Context, summary: RunSummary?): String? {
        val appContext = context.applicationContext
        val fragment = selectGardenFragment(appContext, summary) ?: return null
        fragment.unlocksPageId?.let { unlockMemoryPage(appContext, it) }
        unlockGardenContextPages(appContext, summary)
        return fragment.text
    }

    fun creatureThought(context: Context, type: EntityType?): String? {
        val tracked = type ?: return null
        val appContext = context.applicationContext
        val text = RelationshipArcSystem.creatureThought(appContext, tracked)
            ?: fallbackCreatureThought(appContext, tracked)
            ?: return null
        val pageId = "page_thought_${tracked.name.lowercase()}"
        unlockMemoryPage(appContext, pageId)
        thoughtFamilyPage(tracked)?.let { unlockMemoryPage(appContext, it) }
        if (PersistentMemoryManager.getPassCount(appContext, tracked) >= 3) {
            unlockMemoryPage(appContext, "page_thought_learned_${tracked.name.lowercase()}")
        }
        if (PersistentMemoryManager.getHitCount(appContext, tracked) >= 2) {
            unlockMemoryPage(appContext, "page_thought_caution_${tracked.name.lowercase()}")
        }
        return text
    }

    fun weatherThought(context: Context, summary: RunSummary?): String {
        val appContext = context.applicationContext
        val mood = summary?.forestMood ?: ForestMoodSystem.currentState(appContext).currentMood
        val peacefulBiome = PersistentMemoryManager.featuredPeaceBiome(appContext)
        val strongest = RelationshipArcSystem.preferredGardenVisitor(appContext, RelationshipStage.RECOGNITION)
        val repeatFriend = RelationshipArcSystem.featuredRepeatFriend(appContext)
        val strainedBond = RelationshipArcSystem.featuredStrainedBond(appContext, RelationshipStage.TRUST)
        val milestoneReward = RelationshipArcSystem.featuredMilestoneReward(appContext)
        val repeatedHarmCreature = PersistentMemoryManager.featuredTenderCreature(appContext)
        val repeatedKiller = PersistentMemoryManager.featuredRepeatKiller(appContext)
        val repeatedKindnessCreature = PersistentMemoryManager.featuredWarmCreature(appContext)
        val text = when (mood) {
            ForestMood.GENTLE -> if (summary?.pacifistRouteTier == PacifistRouteTier.PEACEFUL && peacefulBiome != null && (summary.bloomConversions >= 2 || summary.cleanPasses >= 10)) {
                "The evening wind sounds like ${peacefulBiome.biome.displayName} never stopped resting after Bloom passed through it."
            } else if (summary?.pacifistRouteTier == PacifistRouteTier.PEACEFUL && peacefulBiome != null) {
                "The evening wind sounds like ${peacefulBiome.biome.displayName} is still trying to keep the peace you left there."
            } else if (summary?.pacifistRouteTier == PacifistRouteTier.PEACEFUL && (summary.bloomConversions >= 2 || summary.cleanPasses >= 10)) {
                "The evening wind sounds like it is carrying both Bloom and peace carefully around the garden."
            } else if (summary?.pacifistRouteTier == PacifistRouteTier.PEACEFUL) {
                "The evening wind sounds like it is trying not to disturb the peace you carried home."
            } else if (summary?.pacifistRouteTier == PacifistRouteTier.MERCIFUL && peacefulBiome != null) {
                "The evening wind sounds like ${peacefulBiome.biome.displayName} learned to answer mercy more softly."
            } else if (summary?.pacifistRouteTier == PacifistRouteTier.MERCIFUL && repeatFriend != null) {
                "The evening wind sounds like mercy taught something familiar to wait for you more softly."
            } else if (summary?.pacifistRouteTier == PacifistRouteTier.MERCIFUL && milestoneReward != null) {
                "The evening wind moves like ${milestoneReward.label} learned to keep mercy from fading on the way home."
            } else if (repeatFriend != null) {
                "The evening wind sounds like it has started expecting the same familiar kindness to return."
            } else if (summary?.pacifistRouteTier == PacifistRouteTier.KIND) {
                "The evening wind keeps the kinder edges of the run from disappearing too quickly."
            } else if (milestoneReward != null) {
                "The evening wind moves like it has learned the shape of your better returns."
            } else if (strongest != null) {
                "The evening wind moves like it knows who has been welcomed here."
            } else {
                "The evening wind has nothing urgent left to say."
            }
            ForestMood.RECKLESS -> if (repeatedKiller != null && summary?.lastKiller == repeatedKiller) {
                "Even the restless branches seem to lean away from ${formatEntityName(repeatedKiller)}, like the same old mistake reached the weather before it reached you."
            } else if (strainedBond != null) {
                "Even the restless branches sound careful around ${formatEntityName(strainedBond)}, like trust there has started holding itself back."
            } else if (summary?.lastKiller != null && repeatedHarmCreature == summary.lastKiller) {
                "Even the restless branches seem to know you are still carrying the exact same sharp mistake."
            } else if (repeatedHarmCreature != null) {
                "Even the restless branches seem to know which fear keeps coming back with you."
            } else if (milestoneReward != null) {
                "Even the restless branches seem unwilling to break what trust has grown here."
            } else {
                "The branches still rustle like they are catching up to your hurry."
            }
            ForestMood.FEARFUL -> if (strainedBond != null) {
                "The air stays careful around ${formatEntityName(strainedBond)}, as if even familiarity learned to leave you more room."
            } else if (milestoneReward != null && summary?.hitsTaken ?: 0 > 0) {
                "The air stays careful even around ${milestoneReward.label}, like something that knows you well is trying not to press the bruise any further."
            } else if (repeatFriend != null) {
                "The air stays careful, but not empty, as if something familiar decided to keep you company through it."
            } else if (repeatedHarmCreature != null) {
                "The air stays careful, as if it knows which shadow still follows you home."
            } else {
                "The air stays soft, as if the weather decided not to press its luck."
            }
            ForestMood.STEADY -> if ((summary?.cleanPasses ?: 0) >= 10 && (summary?.bloomConversions ?: 0) >= 2 && peacefulBiome != null) {
                "The wind keeps ${peacefulBiome.biome.displayName} in a patient glow, like calm and Bloom learned how to share the same weather."
            } else if ((summary?.cleanPasses ?: 0) >= 12 && repeatFriend != null) {
                "The wind keeps a patient pace, like it recognized both your calm and the familiar company inside it."
            } else if ((summary?.bloomConversions ?: 0) >= 2 && milestoneReward != null) {
                "The wind keeps Bloom from sounding wild, as if ${milestoneReward.label} taught even the weather how to hold a line."
            } else if (repeatFriend != null) {
                "The wind keeps a steady pace, like it recognizes a familiar bond walking back in."
            } else if (repeatedKindnessCreature != null && summary?.pacifistRouteTier == PacifistRouteTier.KIND) {
                "The wind keeps a patient pace, like it noticed kindness becoming a habit."
            } else if (milestoneReward != null) {
                "The wind keeps a patient pace, like it recognizes this version of you."
            } else {
                "The wind keeps a patient pace through the garden."
            }
        }
        unlockMemoryPage(appContext, "page_weather_${mood.name.lowercase()}")
        summary?.pacifistRouteTier?.takeIf { it != PacifistRouteTier.NONE }?.let {
            unlockMemoryPage(appContext, "page_weather_route_${it.name.lowercase()}")
        }
        peacefulBiome?.let { unlockMemoryPage(appContext, "page_weather_biome_${it.biome.name.lowercase()}") }
        if ((summary?.bloomConversions ?: 0) >= 2) {
            unlockMemoryPage(appContext, "page_weather_bloom")
        }
        repeatedKiller?.let { unlockMemoryPage(appContext, "page_weather_repeat_${it.name.lowercase()}") }
        return text
    }

    fun memoryPageCount(context: Context): Int =
        SaveManager.loadUnlockedMemoryPages(context.applicationContext).size

    fun unlockedMemoryPages(context: Context): Set<String> =
        SaveManager.loadUnlockedMemoryPages(context.applicationContext)

    private fun fallbackCreatureThought(context: Context, type: EntityType): String? {
        val passCount = PersistentMemoryManager.getPassCount(context, type)
        val hitCount = PersistentMemoryManager.getHitCount(context, type)
        return when (type) {
            EntityType.CACTUS -> when {
                passCount >= 3 -> "The cactus has started trusting your patience around what it keeps sharp."
                hitCount >= 2 -> "The cactus thinks you still reach too quickly for what only needed room."
                else -> "The cactus keeps its bloom where only patience can find it."
            }
            EntityType.LILY_OF_VALLEY -> when {
                passCount >= 3 -> "The lily lowers its light like it expects you to read both glow and warning together."
                hitCount >= 2 -> "The lily thinks you still trust beauty faster than you trust spacing."
                else -> "The lily keeps a bright hush over the small danger it hides."
            }
            EntityType.HYACINTH -> when {
                passCount >= 3 -> "The hyacinth has started treating your timing like part of its clustered rhythm."
                hitCount >= 2 -> "The hyacinth thinks you still arrive off-beat and call it bad luck."
                else -> "The hyacinth keeps counting in little pulses near the path."
            }
            EntityType.EUCALYPTUS -> when {
                passCount >= 3 -> "The eucalyptus keeps its harder line honest when your calm stays honest with it."
                hitCount >= 2 -> "The eucalyptus thinks your body still panics before the bend is even real."
                else -> "The eucalyptus leans like it already knows the wind will test you."
            }
            EntityType.VANILLA_ORCHID -> when {
                passCount >= 3 -> "The orchid leaves a narrow kindness open now that you have started honoring it."
                hitCount >= 2 -> "The orchid thinks you still rush past the one quiet thread it gives you."
                else -> "The orchid keeps one careful lane hidden inside all that softness."
            }
            EntityType.WEEPING_WILLOW -> when {
                passCount >= 3 -> "The willow parts its curtain just enough, like it has started trusting your patience."
                hitCount >= 2 -> "The willow thinks you still push into the curtain instead of listening for the lane."
                else -> "The willow keeps lowering the same patient green warning."
            }
            EntityType.JACARANDA -> when {
                passCount >= 3 -> "The jacaranda lets the falling canopy feel almost celebratory when you hold your line."
                hitCount >= 2 -> "The jacaranda thinks you still get lost in the spectacle before you read the space under it."
                else -> "The jacaranda lets beauty arrive thick enough to test whether you can still see through it."
            }
            EntityType.BAMBOO -> when {
                passCount >= 3 -> "The bamboo keeps a cleaner seam now that you have started respecting how exact it wants to be."
                hitCount >= 2 -> "The bamboo thinks you still trust width where only precision will do."
                else -> "The bamboo stands like it expects you to choose one exact answer."
            }
            EntityType.CHERRY_BLOSSOM -> when {
                passCount >= 3 -> "The cherry blossom lets the gust feel playful when you stop fighting every shift in it."
                hitCount >= 2 -> "The cherry blossom thinks you still argue with the wind too late."
                else -> "The cherry blossom keeps the air sweet enough to hide how much it wants to move you."
            }
            EntityType.DUCK -> when {
                passCount >= 3 -> "The duck sounds less like a warning now and more like a lesson you finally answer in time."
                hitCount >= 2 -> "The duck thinks you still forget how low the lane can become."
                else -> "The duck keeps one low answer ready under the quack."
            }
            EntityType.TIT -> when {
                passCount >= 3 -> "The tit flock thinks you have finally started hearing the beat inside the rush."
                hitCount >= 2 -> "The tit flock thinks you still mistake hurry for rhythm."
                else -> "The tit flock keeps a fast little count the air expects you to learn."
            }
            EntityType.CHICKADEE -> when {
                passCount >= 3 -> "The chickadees keep a softer pocket for you when your timing stays kind."
                hitCount >= 2 -> "The chickadees think you still flinch at charm before it turns into motion."
                else -> "The chickadees make sweetness move faster than nerves expect."
            }
            EntityType.HEDGEHOG -> when {
                passCount >= 3 -> "The hedgehog thinks you have finally learned how much room a small fear needs."
                hitCount >= 2 -> "The hedgehog thinks you still meet every thorn with more speed than care."
                else -> "The hedgehog keeps its little caution curled close to the ground."
            }
            else -> null
        }
    }

    private fun selectRestFragment(
        context: Context,
        summary: RunSummary,
        biome: Biome,
        killer: EntityType?
    ): StoryFragment {
        val hitCount = killer?.let { PersistentMemoryManager.getHitCount(context, it) } ?: 0
        val repeatedKiller = killer != null && hitCount >= 2
        val relationshipStage = killer?.let { RelationshipArcSystem.stageFor(context, it) }
        val strainedKiller = killer?.takeIf { RelationshipArcSystem.isStrainedBond(context, it) }
        val peacefulBiome = PersistentMemoryManager.featuredPeaceBiome(context)
        val repeatFriend = RelationshipArcSystem.featuredRepeatFriend(context)
        val warmCreature = PersistentMemoryManager.featuredWarmCreature(context)
        val milestoneReward = RelationshipArcSystem.featuredMilestoneReward(context)

        if (summary.pacifistRouteTier == PacifistRouteTier.PEACEFUL && summary.hitsTaken == 0) {
            val text = when {
                peacefulBiome != null && summary.bloomConversions >= 2 ->
                    "${peacefulBiome.biome.displayName} stayed so peaceful that even Bloom felt careful there."
                peacefulBiome != null ->
                    "${peacefulBiome.biome.displayName} still feels like it agreed with the way you crossed it."
                summary.bloomConversions >= 2 ->
                    "Even Bloom came down quietly, like the run never wanted to stop being kind."
                repeatFriend != null ->
                    "Peace followed you home so completely that even something familiar seemed calmer around it."
                else ->
                    "The run was quiet enough that rest feels like a continuation, not an interruption."
            }
            return StoryFragment(
                id = "rest_route_peaceful",
                type = StoryFragmentType.REST,
                text = text,
                unlocksPageId = "page_rest_route_peaceful"
            )
        }

        if (summary.pacifistRouteTier == PacifistRouteTier.MERCIFUL && summary.sparedCount > 0) {
            val text = when {
                peacefulBiome != null ->
                    "${peacefulBiome.biome.displayName} sounds less guarded now that mercy keeps reaching it."
                repeatFriend != null ->
                    "${formatEntityName(repeatFriend)} is starting to feel the difference between mercy and hesitation."
                milestoneReward != null ->
                    "${milestoneReward.label} feels a little closer after a run that kept choosing mercy."
                else ->
                    "Mercy stayed with the run long enough to change the shape of rest."
            }
            return StoryFragment(
                id = "rest_route_merciful",
                type = StoryFragmentType.REST,
                text = text,
                unlocksPageId = "page_rest_route_merciful"
            )
        }

        if (summary.cleanPasses >= 12 && summary.hitsTaken == 0) {
            val text = when {
                repeatFriend != null ->
                    "The whole run stayed so steady it felt like ${formatEntityName(repeatFriend)} was part of the rhythm."
                milestoneReward != null ->
                    "A clean run leaves ${milestoneReward.label} feeling less like a reward and more like home."
                else ->
                    "The run stayed calm long enough to make rest feel earned instead of borrowed."
            }
            return StoryFragment(
                id = "rest_clean_return",
                type = StoryFragmentType.REST,
                text = text,
                unlocksPageId = "page_rest_clean_return"
            )
        }

        if (killer != null && repeatedKiller) {
            val text = when (killer) {
                EntityType.CAT -> "The cat already knew your rhythm."
                EntityType.FOX -> "The fox keeps learning you faster than you learn it."
                EntityType.WOLF -> if (relationshipStage == RelationshipStage.TRUST || relationshipStage == RelationshipStage.MILESTONE) {
                    "Even the familiar howl found the same opening."
                } else {
                    "The howl keeps finding the same weak moment."
                }
                EntityType.DOG -> "You heard the bark and still stayed in its line."
                EntityType.HEDGEHOG -> "The tiny ones still punish impatience."
                EntityType.OWL -> "The owl waits for that same jump every time."
                EntityType.EAGLE -> "The sky keeps remembering where you panic."
                else -> "The forest noticed the pattern before you did."
            }
            return StoryFragment(
                id = "rest_repeat_${killer.name.lowercase()}",
                type = StoryFragmentType.REST,
                text = text,
                unlocksPageId = "page_repeat_${killer.name.lowercase()}"
            )
        }

        if (strainedKiller != null && relationshipStage != null && relationshipStage.ordinal >= RelationshipStage.TRUST.ordinal) {
            return StoryFragment(
                id = "rest_strained_${strainedKiller.name.lowercase()}",
                type = StoryFragmentType.REST,
                text = RelationshipArcSystem.strainedBondLine(context, strainedKiller),
                unlocksPageId = "page_rest_strained_${strainedKiller.name.lowercase()}"
            )
        }

        if (killer != null) {
            val text = when (killer) {
                EntityType.CAT -> if (relationshipStage == RelationshipStage.TRUST || relationshipStage == RelationshipStage.MILESTONE) {
                    "Even familiar paws need you to slow down."
                } else {
                    "You rushed the cat instead of reading it."
                }
                EntityType.FOX -> "The fox wanted a conversation in motion."
                EntityType.WOLF -> "The wolf announced the charge. You stayed anyway."
                EntityType.DOG -> "The bark wave came first. The hit came second."
                EntityType.HEDGEHOG -> "You clipped the thorns and lost your tempo."
                EntityType.DUCK -> "The duck owned the lane you forgot to lower for."
                EntityType.TIT, EntityType.CHICKADEE -> "The flock looked small until it was not."
                EntityType.OWL -> if (relationshipStage == RelationshipStage.TRUST || relationshipStage == RelationshipStage.MILESTONE) {
                    "The owl only turns when you ask the night to notice."
                } else {
                    "The owl only dives when you tell it to."
                }
                EntityType.EAGLE -> "The eagle marked you before it arrived."
                EntityType.CACTUS,
                EntityType.LILY_OF_VALLEY,
                EntityType.HYACINTH,
                EntityType.EUCALYPTUS,
                EntityType.VANILLA_ORCHID,
                EntityType.WEEPING_WILLOW,
                EntityType.JACARANDA,
                EntityType.BAMBOO,
                EntityType.CHERRY_BLOSSOM -> "The plants were speaking with spacing, not words."
            }
            return StoryFragment(
                id = "rest_hit_${killer.name.lowercase()}",
                type = StoryFragmentType.REST,
                text = text,
                unlocksPageId = when (killer) {
                    EntityType.OWL, EntityType.EAGLE, EntityType.WOLF -> "page_mark_${killer.name.lowercase()}"
                    else -> null
                }
            )
        }

        if (repeatFriend != null) {
            return StoryFragment(
                id = "rest_repeat_friend_${repeatFriend.name.lowercase()}",
                type = StoryFragmentType.REST,
                text = when (repeatFriend) {
                    EntityType.CAT -> "The cat is beginning to treat your return like part of the evening."
                    EntityType.FOX -> "The fox no longer makes your gentler timing feel accidental."
                    EntityType.WOLF -> "The grove has started trusting the calmer version of your courage."
                    EntityType.DOG -> "The dog seems certain this happiness belongs to your return now."
                    EntityType.OWL -> "The owl has started leaving more familiarity than warning in the dark."
                    EntityType.EAGLE -> "Even the eagle's shadow feels less severe when it keeps meeting the same calm."
                    else -> "Something in the forest has started expecting your gentler return."
                },
                unlocksPageId = "page_repeat_friend_${repeatFriend.name.lowercase()}"
            )
        }

        if (warmCreature != null) {
            val text = when (warmCreature) {
                EntityType.CAT -> "The cat is beginning to expect your softer timing."
                EntityType.FOX -> "The fox seems to remember when you answer with calm."
                EntityType.WOLF -> "The grove keeps the version of you that did not flinch."
                EntityType.DOG -> "The dog seems to think your gentler runs are worth celebrating."
                EntityType.OWL -> "Even the owl leaves the night feeling less severe after mercy."
                EntityType.EAGLE -> "The sky feels less punishing when you keep choosing restraint."
                else -> "The forest noticed that your gentleness lasted longer this time."
            }
            return StoryFragment(
                id = "rest_warm_${warmCreature.name.lowercase()}",
                type = StoryFragmentType.REST,
                text = text,
                unlocksPageId = "page_warm_${warmCreature.name.lowercase()}"
            )
        }

        val biomeText = when (biome) {
            Biome.MEADOW -> if (summary.pacifistRouteTier == PacifistRouteTier.KIND) {
                "The meadow keeps the kinder edges of a run longer than the score does."
            } else {
                "The meadow is gentle only if you stay gentle with it."
            }
            Biome.ORCHARD -> if (summary.cleanPasses >= 8) {
                "The orchard rewards rhythm enough that a clean run still sounds musical here."
            } else {
                "The orchard rewards rhythm more than speed."
            }
            Biome.ANCIENT_GROVE -> if (summary.lastKiller == EntityType.WOLF) {
                "The grove asks for patience before bravery, especially when the howl already knows you."
            } else {
                "The grove asks for patience before bravery."
            }
            Biome.DUSK_CANYON -> if (summary.pacifistRouteTier == PacifistRouteTier.MERCIFUL) {
                "Dusk shortened every decision, and you still left room for mercy."
            } else {
                "Dusk shortens every decision."
            }
            Biome.NIGHT_FOREST -> if (summary.bloomConversions >= 2) {
                "Night keeps the memory of Bloom longer than it admits."
            } else {
                "Night keeps the score, but it also keeps the memory."
            }
        }
        return StoryFragment(
            id = "rest_biome_${biome.name.lowercase()}",
            type = StoryFragmentType.REST,
            text = biomeText
        )
    }

    private fun selectGardenFragment(context: Context, summary: RunSummary?): StoryFragment? {
        val strongest = RelationshipArcSystem.strongestRelationship(context)
        val milestoneReward = RelationshipArcSystem.featuredMilestoneReward(context)
        val repeatFriend = RelationshipArcSystem.featuredRepeatFriend(context)
        val strainedBond = RelationshipArcSystem.featuredStrainedBond(context, RelationshipStage.TRUST)
        val peacefulBiome = PersistentMemoryManager.featuredPeaceBiome(context)
        val mood = ForestMoodSystem.currentState(context).currentMood
        val repeatedKiller = PersistentMemoryManager.featuredRepeatKiller(context)
        val repeatedHarmCreature = PersistentMemoryManager.featuredTenderCreature(context)
            ?: (summary?.lastKiller ?: PersistentMemoryManager.getLastKiller(context))?.takeIf {
                PersistentMemoryManager.getHitCount(context, it) >= 2
            }
        val repeatedKindnessCreature = PersistentMemoryManager.featuredWarmCreature(context)
        val cactusBloom = PersistentMemoryManager.featuredCleanPass(context, setOf(EntityType.CACTUS))

        if (cactusBloom != null && summary?.hitsTaken == 0) {
            return StoryFragment(
                id = "garden_cactus_bloom",
                type = StoryFragmentType.GARDEN_REFLECTION,
                text = "Even the cactus patch has started flowering where you keep reading the rigid line cleanly.",
                unlocksPageId = "page_garden_cactus_bloom"
            )
        }

        if (milestoneReward != null && summary?.forestMood == ForestMood.GENTLE && (summary.sparedCount > 0 || summary.kindnessChain >= 5)) {
            return StoryFragment(
                id = "garden_milestone_gentle_${milestoneReward.type.name.lowercase()}",
                type = StoryFragmentType.GARDEN_REFLECTION,
                text = when (milestoneReward.type) {
                    EntityType.CAT -> "The cat's quiet patch feels especially close after a gentle return."
                    EntityType.FOX -> "The brighter trail looks almost proud of how gently you came back."
                    EntityType.WOLF -> "The watch stone feels calmer when your courage stays soft."
                    EntityType.DOG -> "Even the welcome bell sounds softer after a kind run."
                    EntityType.OWL -> "The lantern branch keeps a warmer light after a gentler night."
                    EntityType.EAGLE -> "The sky thread looks less severe when you come home with mercy."
                    else -> "Something trusted your gentler return."
                },
                unlocksPageId = "page_milestone_gentle_${milestoneReward.type.name.lowercase()}"
            )
        }

        if (summary != null &&
            summary.pacifistRouteTier == PacifistRouteTier.MERCIFUL &&
            repeatFriend != null &&
            summary.sparedCount > 0
        ) {
            return StoryFragment(
                id = "garden_route_merciful_friend_${repeatFriend.name.lowercase()}",
                type = StoryFragmentType.GARDEN_REFLECTION,
                text = "${formatEntityName(repeatFriend)} feels closer to home when mercy is the thing you keep bringing back.",
                unlocksPageId = "page_route_merciful_friend_${repeatFriend.name.lowercase()}"
            )
        }

        if (summary != null && repeatFriend != null && summary.hitsTaken == 0 && summary.cleanPasses >= 8) {
            return StoryFragment(
                id = "garden_repeat_friend_${repeatFriend.name.lowercase()}",
                type = StoryFragmentType.GARDEN_REFLECTION,
                text = when (repeatFriend) {
                    EntityType.CAT -> "The garden has started treating the cat's quiet return as part of yours."
                    EntityType.FOX -> "The brighter part of the path behaves like it remembers both of you now."
                    EntityType.WOLF -> "The grove keeps the wolf's respect near the same places your calm returns to."
                    EntityType.DOG -> "The garden sounds more welcoming when the dog's joy feels expected instead of sudden."
                    EntityType.OWL -> "The dark edge feels like a familiar witness instead of a warning now."
                    EntityType.EAGLE -> "Even the sky seems to expect recognition instead of fear from that shadow now."
                    else -> "The garden has started keeping a familiar shape around your better returns."
                },
                unlocksPageId = "page_repeat_friend_garden_${repeatFriend.name.lowercase()}"
            )
        }

        if (summary != null && strainedBond != null && (summary.hitsTaken > 0 || summary.forestMood == ForestMood.FEARFUL)) {
            return StoryFragment(
                id = "garden_strained_${strainedBond.name.lowercase()}",
                type = StoryFragmentType.GARDEN_REFLECTION,
                text = RelationshipArcSystem.strainedBondLine(context, strainedBond),
                unlocksPageId = "page_garden_strained_${strainedBond.name.lowercase()}"
            )
        }

        if (summary != null && repeatedKindnessCreature != null && summary.cleanPasses >= 10 && summary.hitsTaken == 0) {
            return StoryFragment(
                id = "garden_repeated_kindness_clean_${repeatedKindnessCreature.name.lowercase()}",
                type = StoryFragmentType.GARDEN_REFLECTION,
                text = "${formatEntityName(repeatedKindnessCreature)} is starting to feel less like a lucky exception and more like part of the calmer path you keep bringing home.",
                unlocksPageId = "page_garden_warm_clean_${repeatedKindnessCreature.name.lowercase()}"
            )
        }

        if (summary != null && repeatedKindnessCreature != null && (summary.sparedCount > 0 || summary.kindnessChain >= 4)) {
            return StoryFragment(
                id = "garden_repeated_kindness_${repeatedKindnessCreature.name.lowercase()}",
                type = StoryFragmentType.GARDEN_REFLECTION,
                text = "The garden has started trusting your gentler habits as something dependable.",
                unlocksPageId = "page_garden_warm_${repeatedKindnessCreature.name.lowercase()}"
            )
        }

        if (summary != null && summary.pacifistRouteTier == PacifistRouteTier.KIND && (summary.sparedCount > 0 || summary.kindnessChain >= 4)) {
            if (peacefulBiome != null) {
                return StoryFragment(
                    id = "garden_route_kind_${peacefulBiome.biome.name.lowercase()}",
                    type = StoryFragmentType.GARDEN_REFLECTION,
                    text = "${peacefulBiome.biome.displayName} kept the kindness of that run close instead of letting it disappear on the way home.",
                    unlocksPageId = "page_route_kind_${peacefulBiome.biome.name.lowercase()}"
                )
            }
            return StoryFragment(
                id = "garden_route_kind",
                type = StoryFragmentType.GARDEN_REFLECTION,
                text = "The garden kept the kindness of that run close instead of treating it like a small accident.",
                unlocksPageId = "page_route_kind"
            )
        }

        if (summary != null && summary.hitsTaken == 0 && summary.pacifistRouteTier == PacifistRouteTier.PEACEFUL) {
            if (peacefulBiome != null && summary.bloomConversions >= 2) {
                return StoryFragment(
                    id = "garden_route_peaceful_bloom_${peacefulBiome.biome.name.lowercase()}",
                    type = StoryFragmentType.GARDEN_REFLECTION,
                    text = "${peacefulBiome.biome.displayName} seems to be holding both the peace of the run and the last of Bloom in the same hush.",
                    unlocksPageId = "page_route_peaceful_bloom_${peacefulBiome.biome.name.lowercase()}"
                )
            }
            if (peacefulBiome != null) {
                return StoryFragment(
                    id = "garden_route_peaceful_${peacefulBiome.biome.name.lowercase()}",
                    type = StoryFragmentType.GARDEN_REFLECTION,
                    text = "${peacefulBiome.biome.displayName} still feels at peace with the way you came through it.",
                    unlocksPageId = "page_route_peaceful_${peacefulBiome.biome.name.lowercase()}"
                )
            }
            if (summary.bloomConversions >= 2 && milestoneReward != null) {
                return StoryFragment(
                    id = "garden_route_peaceful_bloom_${milestoneReward.type.name.lowercase()}",
                    type = StoryFragmentType.GARDEN_REFLECTION,
                    text = "${milestoneReward.label} seems to be holding both the peace of the run and the last of Bloom in the same light.",
                    unlocksPageId = "page_route_peaceful_bloom_${milestoneReward.type.name.lowercase()}"
                )
            }
            return StoryFragment(
                id = "garden_route_peaceful",
                type = StoryFragmentType.GARDEN_REFLECTION,
                text = "The whole garden seems to be listening to the peace you brought back with you.",
                unlocksPageId = "page_route_peaceful"
            )
        }

        if (summary != null && summary.hitsTaken == 0 && summary.pacifistRouteTier == PacifistRouteTier.MERCIFUL) {
            if (peacefulBiome != null) {
                return StoryFragment(
                    id = "garden_route_merciful_${peacefulBiome.biome.name.lowercase()}",
                    type = StoryFragmentType.GARDEN_REFLECTION,
                    text = "${peacefulBiome.biome.displayName} feels less guarded now that mercy keeps finding it.",
                    unlocksPageId = "page_route_merciful_${peacefulBiome.biome.name.lowercase()}"
                )
            }
            return StoryFragment(
                id = "garden_route_merciful",
                type = StoryFragmentType.GARDEN_REFLECTION,
                text = "Mercy left the path feeling less guarded than it did before the run began.",
                unlocksPageId = "page_route_merciful"
            )
        }

        if (strongest != null) {
            val (type, stage) = strongest
            if (stage == RelationshipStage.MILESTONE) {
                val text = when (type) {
                    EntityType.CAT -> "A quiet patch of grass feels already occupied."
                    EntityType.FOX -> "Something clever left the path slightly brighter."
                    EntityType.WOLF -> "The grove feels guarded instead of watched."
                    EntityType.DOG -> "The air still carries a happy kind of noise."
                    EntityType.OWL -> "The dark edge of the garden no longer feels empty."
                    EntityType.EAGLE -> "Even the sky above home feels like part of the bond now."
                    else -> "The garden keeps a trace of someone you know."
                }
                return StoryFragment(
                    id = "garden_bond_${type.name.lowercase()}",
                    type = StoryFragmentType.GARDEN_REFLECTION,
                    text = text,
                    unlocksPageId = "page_bond_${type.name.lowercase()}"
                )
            } else if (stage == RelationshipStage.TRUST) {
                val text = when (type) {
                    EntityType.CAT -> "A familiar pause lingers near the flowers."
                    EntityType.FOX -> "The path looks like it expects an answer from you."
                    EntityType.WOLF -> "The grove sounds less empty than it used to."
                    EntityType.DOG -> "Something about the air still feels eager."
                    EntityType.OWL -> "The dark edge feels watched over instead of watched."
                    EntityType.EAGLE -> "The sky feels stern, but not unfriendly."
                    else -> "Something familiar has stayed behind."
                }
                return StoryFragment(
                    id = "garden_trust_${type.name.lowercase()}",
                    type = StoryFragmentType.GARDEN_REFLECTION,
                    text = text
                )
            }
        }

        if (summary != null && peacefulBiome != null) {
            biomeGardenFragment(
                summary = summary,
                peacefulBiome = peacefulBiome,
                repeatFriend = repeatFriend,
                milestoneReward = milestoneReward,
                strainedBond = strainedBond
            )?.let { return it }
        }

        summary?.let {
            if (repeatedKiller != null && repeatedKiller == repeatedHarmCreature && it.hitsTaken > 0) {
                return StoryFragment(
                    id = "garden_same_shadow_${repeatedKiller.name.lowercase()}",
                    type = StoryFragmentType.GARDEN_REFLECTION,
                    text = "The garden has started recognizing the same shadow before you even name it.",
                    unlocksPageId = "page_same_shadow_${repeatedKiller.name.lowercase()}"
                )
            }
            if (repeatedHarmCreature != null && (it.hitsTaken > 0 || it.forestMood == ForestMood.FEARFUL)) {
                return StoryFragment(
                    id = "garden_repeated_harm_${repeatedHarmCreature.name.lowercase()}",
                    type = StoryFragmentType.GARDEN_REFLECTION,
                    text = "The garden is gentle about the places your nerves still remember.",
                    unlocksPageId = "page_garden_caution_${repeatedHarmCreature.name.lowercase()}"
                )
            }
            if (it.forestMood == ForestMood.GENTLE && it.sparedCount > 0) {
                return StoryFragment(
                    id = "garden_gentle_aftercare",
                    type = StoryFragmentType.GARDEN_REFLECTION,
                    text = "The leaves settle as if they trusted the way you came home.",
                    unlocksPageId = "page_gentle_aftercare"
                )
            }
            if (it.cleanPasses >= 12 && it.hitsTaken == 0) {
                return StoryFragment(
                    id = "garden_clean_return",
                    type = StoryFragmentType.GARDEN_REFLECTION,
                    text = "The whole garden feels as if it noticed how calmly you made it through.",
                    unlocksPageId = "page_clean_return"
                )
            }
            if (it.bloomConversions >= 4) {
                return StoryFragment(
                    id = "garden_bloom_afterglow",
                    type = StoryFragmentType.GARDEN_REFLECTION,
                    text = "A little of Bloom is still hanging in the leaves around home.",
                    unlocksPageId = "page_bloom_afterglow"
                )
            }
            if (it.isNewHighScore) {
                return StoryFragment(
                    id = "garden_after_best",
                    type = StoryFragmentType.GARDEN_REFLECTION,
                    text = "The path still hums with the distance you carried back.",
                    unlocksPageId = "page_after_best"
                )
            }
        }

        val text = when (mood) {
            ForestMood.GENTLE -> if (repeatFriend != null) {
                "The garden breathes like it has started expecting the same familiar kindness to come back."
            } else if (repeatedKindnessCreature != null) {
                "The garden breathes like it has started trusting your softer returns."
            } else {
                "The garden breathes a little easier tonight."
            }
            ForestMood.RECKLESS -> "Even the flowers look like they heard you arrive too fast."
            ForestMood.FEARFUL -> "The garden keeps its voice low until you are ready again."
            ForestMood.STEADY -> "Home feels ordinary in the best possible way."
        }
        return StoryFragment(
            id = "garden_mood_${mood.name.lowercase()}",
            type = StoryFragmentType.GARDEN_REFLECTION,
            text = text
        )
    }

    private fun biomeGardenFragment(
        summary: RunSummary,
        peacefulBiome: PersistentMemoryManager.BiomeFriendshipMark,
        repeatFriend: EntityType?,
        milestoneReward: RelationshipMilestoneReward?,
        strainedBond: EntityType?
    ): StoryFragment? {
        val biome = peacefulBiome.biome
        val pageId = "page_garden_biome_${biome.name.lowercase()}"
        val text = when (biome) {
            Biome.MEADOW -> when {
                summary.forestMood == ForestMood.GENTLE && (summary.sparedCount > 0 || summary.kindnessChain >= 4) ->
                    "The meadow edge still feels open enough to keep the kindness of that run from closing back up."
                summary.cleanPasses >= 8 && summary.hitsTaken == 0 ->
                    "The meadow grass keeps the line you held, like calm is easier to believe there now."
                else ->
                    "The meadow side of home feels a little wider, like it recognized your softer return."
            }
            Biome.ORCHARD -> when {
                summary.cleanPasses >= 8 && summary.hitsTaken == 0 ->
                    "The orchard light still falls in rhythm, like the cleaner run taught it not to rush the next note."
                milestoneReward != null ->
                    "${milestoneReward.label} leaves the orchard sounding more like a chorus than a reward."
                else ->
                    "The orchard keeps a patient sweetness, like it heard you choose rhythm over hurry."
            }
            Biome.ANCIENT_GROVE -> when {
                strainedBond == EntityType.WOLF || summary.lastKiller == EntityType.WOLF || summary.forestMood == ForestMood.FEARFUL ->
                    "Ancient Grove keeps its older patience close, as if it knows courage still needs more quiet than noise."
                repeatFriend == EntityType.WOLF || milestoneReward?.type == EntityType.WOLF || (summary.cleanPasses >= 8 && summary.hitsTaken == 0) ->
                    "Ancient Grove feels more like a place that recognized your calm than a place that tested it."
                else ->
                    "Ancient Grove still waits the way only old places do, asking for patience before anything else."
            }
            Biome.DUSK_CANYON -> when {
                summary.pacifistRouteTier.ordinal >= PacifistRouteTier.MERCIFUL.ordinal || summary.sparedCount > 0 ->
                    "Dusk Canyon still carries the mercy of shorter choices, like even its sharp edge softened for the way you moved through it."
                summary.hitsTaken > 0 || summary.forestMood == ForestMood.FEARFUL ->
                    "Dusk Canyon keeps the bruise of rushed choices visible, but not cruel."
                else ->
                    "Dusk Canyon still glows like it remembers how carefully you crossed what closes quickly."
            }
            Biome.NIGHT_FOREST -> when {
                summary.bloomConversions >= 2 ->
                    "Night Forest is still holding a little Bloom under the dark, like it refused to let that light disappear on schedule."
                repeatFriend == EntityType.OWL || repeatFriend == EntityType.EAGLE || milestoneReward?.type == EntityType.OWL || milestoneReward?.type == EntityType.EAGLE ->
                    "Night Forest feels less lonely than severe tonight, as if the dark kept watch instead of distance."
                else ->
                    "Night Forest keeps more memory than silence tonight."
            }
        }
        return StoryFragment(
            id = "garden_biome_${biome.name.lowercase()}",
            type = StoryFragmentType.GARDEN_REFLECTION,
            text = text,
            unlocksPageId = pageId
        )
    }

    private fun unlockRestContextPages(
        context: Context,
        summary: RunSummary,
        biome: Biome,
        killer: EntityType?
    ) {
        unlockMemoryPage(context, "page_rest_biome_${biome.name.lowercase()}")
        unlockMemoryPage(context, "page_rest_mood_${summary.forestMood.name.lowercase()}")
        summary.pacifistRouteTier.takeIf { it != PacifistRouteTier.NONE }?.let {
            unlockMemoryPage(context, "page_rest_route_${it.name.lowercase()}")
        }
        if (summary.cleanPasses >= 8 && summary.hitsTaken == 0) {
            unlockMemoryPage(context, "page_rest_clean_pattern")
        }
        if (summary.bloomConversions >= 2) {
            unlockMemoryPage(context, "page_rest_bloom_memory")
        }
        killer?.let { unlockMemoryPage(context, "page_rest_killer_${it.name.lowercase()}") }
    }

    private fun unlockGardenContextPages(context: Context, summary: RunSummary?) {
        val mood = summary?.forestMood ?: ForestMoodSystem.currentState(context).currentMood
        unlockMemoryPage(context, "page_garden_mood_${mood.name.lowercase()}")
        summary?.pacifistRouteTier?.takeIf { it != PacifistRouteTier.NONE }?.let {
            unlockMemoryPage(context, "page_garden_route_${it.name.lowercase()}")
        }
        PersistentMemoryManager.featuredPeaceBiome(context)?.let {
            unlockMemoryPage(context, "page_garden_peace_${it.biome.name.lowercase()}")
        }
        PersistentMemoryManager.featuredWarmCreature(context)?.let {
            unlockMemoryPage(context, "page_garden_warmth_${it.name.lowercase()}")
        }
        PersistentMemoryManager.featuredRepeatKiller(context)?.let {
            unlockMemoryPage(context, "page_garden_repeat_${it.name.lowercase()}")
        }
        if ((summary?.bloomConversions ?: 0) >= 2) {
            unlockMemoryPage(context, "page_garden_bloom_memory")
        }
    }

    private fun thoughtFamilyPage(type: EntityType): String? = when (type) {
        EntityType.CACTUS,
        EntityType.LILY_OF_VALLEY,
        EntityType.HYACINTH,
        EntityType.EUCALYPTUS,
        EntityType.VANILLA_ORCHID -> "page_thought_family_flora"
        EntityType.WEEPING_WILLOW,
        EntityType.JACARANDA,
        EntityType.BAMBOO,
        EntityType.CHERRY_BLOSSOM -> "page_thought_family_trees"
        EntityType.DUCK,
        EntityType.TIT,
        EntityType.CHICKADEE,
        EntityType.OWL,
        EntityType.EAGLE -> "page_thought_family_birds"
        EntityType.CAT,
        EntityType.FOX,
        EntityType.WOLF,
        EntityType.DOG,
        EntityType.HEDGEHOG -> "page_thought_family_animals"
    }

    private fun formatEntityName(type: EntityType): String =
        type.name.lowercase().split("_").joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

    private fun unlockMemoryPage(context: Context, pageId: String) {
        val unlocked = SaveManager.loadUnlockedMemoryPages(context).toMutableSet()
        if (unlocked.add(pageId)) {
            SaveManager.saveUnlockedMemoryPages(context, unlocked)
        }
    }
}
