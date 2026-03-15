package com.yourname.forest_run.engine

import android.content.Context
import com.yourname.forest_run.entities.EntityType

enum class RelationshipStage(val displayName: String) {
    FIRST_IMPRESSION("First"),
    RECOGNITION("Known"),
    TRUST("Trust"),
    MILESTONE("Bond")
}

private data class RelationshipThresholds(
    val recognitionScore: Int,
    val trustScore: Int,
    val milestoneScore: Int
)

private enum class RelationshipTone {
    WARM,
    NEUTRAL,
    CAUTIOUS
}

object RelationshipArcSystem {

    private val trackedTypes = setOf(
        EntityType.CAT,
        EntityType.FOX,
        EntityType.WOLF,
        EntityType.DOG,
        EntityType.OWL,
        EntityType.EAGLE
    )

    private val thresholds = mapOf(
        EntityType.CAT to RelationshipThresholds(2, 5, 9),
        EntityType.FOX to RelationshipThresholds(2, 6, 10),
        EntityType.WOLF to RelationshipThresholds(3, 7, 11),
        EntityType.DOG to RelationshipThresholds(2, 5, 8),
        EntityType.OWL to RelationshipThresholds(2, 5, 9),
        EntityType.EAGLE to RelationshipThresholds(2, 6, 10)
    )

    enum class Event {
        PASS,
        THREAT,
        SPARE,
        RETURN
    }

    fun isTracked(type: EntityType): Boolean = type in trackedTypes

    fun refreshStage(context: Context, type: EntityType): RelationshipStage {
        if (!isTracked(type)) return RelationshipStage.FIRST_IMPRESSION
        val stage = computeStage(
            type = type,
            encounters = SaveManager.loadEncounterCount(context.applicationContext, type),
            spared = SaveManager.loadSparedCount(context.applicationContext, type),
            hits = SaveManager.loadHitCount(context.applicationContext, type)
        )
        SaveManager.saveRelationshipStage(context.applicationContext, type, stage)
        return stage
    }

    fun stageFor(context: Context, type: EntityType): RelationshipStage {
        if (!isTracked(type)) return RelationshipStage.FIRST_IMPRESSION
        val saved = SaveManager.loadRelationshipStage(context.applicationContext, type)
        return saved ?: refreshStage(context, type)
    }

    fun strongestRelationshipLabel(context: Context): String? {
        val strongest = strongestRelationship(context) ?: return null
        return "${formatName(strongest.first)} ${strongest.second.displayName}"
    }

    fun strongestRelationship(context: Context): Pair<EntityType, RelationshipStage>? {
        val strongest = trackedTypes.maxWithOrNull(
            compareBy<EntityType> { stageFor(context, it).ordinal }
                .thenBy { affinityScore(context, it) }
        ) ?: return null
        val stage = stageFor(context, strongest)
        if (stage == RelationshipStage.FIRST_IMPRESSION &&
            SaveManager.loadEncounterCount(context.applicationContext, strongest) == 0
        ) {
            return null
        }
        return strongest to stage
    }

    fun lineFor(context: Context, type: EntityType, event: Event): String {
        val stage = stageFor(context, type)
        val tone = toneFor(context, type)
        return when (type) {
            EntityType.CAT -> catLine(stage, tone, event)
            EntityType.FOX -> foxLine(stage, tone, event)
            EntityType.WOLF -> wolfLine(stage, tone, event)
            EntityType.DOG -> dogLine(stage, tone, event)
            EntityType.OWL -> owlLine(stage, tone, event)
            EntityType.EAGLE -> eagleLine(stage, tone, event)
            else -> ""
        }
    }

    private fun computeStage(type: EntityType, encounters: Int, spared: Int, hits: Int): RelationshipStage {
        val config = thresholds.getValue(type)
        val score = encounters + spared * 2 + maxOf(0, spared - hits)
        return when {
            score >= config.milestoneScore -> RelationshipStage.MILESTONE
            score >= config.trustScore -> RelationshipStage.TRUST
            score >= config.recognitionScore -> RelationshipStage.RECOGNITION
            else -> RelationshipStage.FIRST_IMPRESSION
        }
    }

    private fun affinityScore(context: Context, type: EntityType): Int {
        val appContext = context.applicationContext
        val encounters = SaveManager.loadEncounterCount(appContext, type)
        val spared = SaveManager.loadSparedCount(appContext, type)
        val hits = SaveManager.loadHitCount(appContext, type)
        return encounters + spared * 3 - hits
    }

    private fun toneFor(context: Context, type: EntityType): RelationshipTone {
        val appContext = context.applicationContext
        val spared = SaveManager.loadSparedCount(appContext, type)
        val hits = SaveManager.loadHitCount(appContext, type)
        return when {
            spared > hits && spared >= 1 -> RelationshipTone.WARM
            hits > spared && hits >= 2 -> RelationshipTone.CAUTIOUS
            else -> RelationshipTone.NEUTRAL
        }
    }

    private fun formatName(type: EntityType): String =
        type.name.lowercase().split("_").joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

    private fun catLine(stage: RelationshipStage, tone: RelationshipTone, event: Event): String = when (event) {
        Event.PASS -> when (stage) {
            RelationshipStage.FIRST_IMPRESSION -> "Meow?"
            RelationshipStage.RECOGNITION -> if (tone == RelationshipTone.WARM) "You again." else "Soft steps."
            RelationshipStage.TRUST -> "Stayed a while?"
            RelationshipStage.MILESTONE -> "Home?"
        }
        Event.SPARE -> when (stage) {
            RelationshipStage.MILESTONE -> "See you soon."
            RelationshipStage.TRUST -> "I'll stay."
            else -> "Friend?"
        }
        Event.THREAT -> if (tone == RelationshipTone.CAUTIOUS) "Too sudden." else "Hiss!"
        Event.RETURN -> when (stage) {
            RelationshipStage.TRUST, RelationshipStage.MILESTONE -> "A cat has already claimed this place."
            else -> "A cat watches from the path."
        }
    }

    private fun foxLine(stage: RelationshipStage, tone: RelationshipTone, event: Event): String = when (event) {
        Event.PASS -> when (stage) {
            RelationshipStage.FIRST_IMPRESSION -> "Heh."
            RelationshipStage.RECOGNITION -> "Same jump?"
            RelationshipStage.TRUST -> if (tone == RelationshipTone.WARM) "Still with me?" else "Caught that."
            RelationshipStage.MILESTONE -> "Knew you would."
        }
        Event.SPARE -> when (stage) {
            RelationshipStage.MILESTONE -> "Till next time."
            RelationshipStage.TRUST -> "Fine. Go on."
            else -> "Fine."
        }
        Event.THREAT -> if (tone == RelationshipTone.CAUTIOUS) "Not so quick." else "Next time..."
        Event.RETURN -> when (stage) {
            RelationshipStage.TRUST, RelationshipStage.MILESTONE -> "A fox lingers like it expected you."
            else -> "Something clever moved through the garden."
        }
    }

    private fun wolfLine(stage: RelationshipStage, tone: RelationshipTone, event: Event): String = when (event) {
        Event.PASS -> when (stage) {
            RelationshipStage.FIRST_IMPRESSION -> "You made it."
            RelationshipStage.RECOGNITION -> "Again."
            RelationshipStage.TRUST -> if (tone == RelationshipTone.WARM) "You held steady." else "Still standing."
            RelationshipStage.MILESTONE -> "You know the howl now."
        }
        Event.SPARE -> when (stage) {
            RelationshipStage.MILESTONE -> "Then pass in peace."
            RelationshipStage.TRUST -> "Go on."
            else -> "Not today."
        }
        Event.THREAT -> if (tone == RelationshipTone.CAUTIOUS) "I remember." else "GRRR..."
        Event.RETURN -> when (stage) {
            RelationshipStage.TRUST, RelationshipStage.MILESTONE -> "The grove feels watched, not threatened."
            else -> "A distant howl still belongs to the path."
        }
    }

    private fun dogLine(stage: RelationshipStage, tone: RelationshipTone, event: Event): String = when (event) {
        Event.PASS -> when (stage) {
            RelationshipStage.FIRST_IMPRESSION -> "Good hop!"
            RelationshipStage.RECOGNITION -> "Hi!!"
            RelationshipStage.TRUST -> if (tone == RelationshipTone.WARM) "Still here!" else "Nice one!"
            RelationshipStage.MILESTONE -> "Best friend!"
        }
        Event.SPARE -> "Best friend!"
        Event.THREAT -> if (tone == RelationshipTone.CAUTIOUS) "BORF!" else "Hi!!"
        Event.RETURN -> when (stage) {
            RelationshipStage.TRUST, RelationshipStage.MILESTONE -> "The garden still feels wagged awake."
            else -> "A bark seems closer than before."
        }
    }

    private fun owlLine(stage: RelationshipStage, tone: RelationshipTone, event: Event): String = when (event) {
        Event.PASS -> when (stage) {
            RelationshipStage.FIRST_IMPRESSION -> "Silent pass."
            RelationshipStage.RECOGNITION -> "Still awake."
            RelationshipStage.TRUST -> if (tone == RelationshipTone.WARM) "Not prey." else "Too slow."
            RelationshipStage.MILESTONE -> "The night knows you."
        }
        Event.SPARE -> "The branch stays yours."
        Event.THREAT -> if (tone == RelationshipTone.CAUTIOUS) "...again?" else "...hoo?"
        Event.RETURN -> when (stage) {
            RelationshipStage.TRUST, RelationshipStage.MILESTONE -> "Night holds a familiar pair of eyes."
            else -> "Something patient watches the dark edge."
        }
    }

    private fun eagleLine(stage: RelationshipStage, tone: RelationshipTone, event: Event): String = when (event) {
        Event.PASS -> when (stage) {
            RelationshipStage.FIRST_IMPRESSION -> "Outran the mark."
            RelationshipStage.RECOGNITION -> "Still marked."
            RelationshipStage.TRUST -> if (tone == RelationshipTone.WARM) "You held the line." else "Missed again."
            RelationshipStage.MILESTONE -> "You know the shadow."
        }
        Event.SPARE -> "The sky lets you pass."
        Event.THREAT -> if (tone == RelationshipTone.CAUTIOUS) "Marked again." else "Marked."
        Event.RETURN -> when (stage) {
            RelationshipStage.TRUST, RelationshipStage.MILESTONE -> "The sky feels vast, but no longer empty."
            else -> "A shadow crossed the garden earlier."
        }
    }
}
