package com.yourname.forest_run.engine

import com.yourname.forest_run.entities.EntityType

data class OpeningInputState(
    val jumpSeen: Boolean = false,
    val holdSeen: Boolean = false,
    val duckSeen: Boolean = false
) {
    val allInputsSeen: Boolean
        get() = jumpSeen && holdSeen && duckSeen
}

data class OpeningGuidanceChip(
    val label: String,
    val isComplete: Boolean
)

data class OpeningGuidanceCue(
    val title: String,
    val line: String,
    val accentColor: Int,
    val chips: List<OpeningGuidanceChip>
)

object OpeningReadabilityGuide {
    const val HOLD_DISCOVERY_THRESHOLD_SEC = 0.22f
    private const val RANDOM_SPAWN_LOCKOUT_SEC = 6.75f
    private const val GUIDED_WINDOW_SEC = 28f

    private const val ACCENT_SOFT = 0xFFEACB74.toInt()
    private const val ACCENT_KIND = 0xFF8EDB8A.toInt()
    private const val ACCENT_ROUTE = 0xFF9CC8FF.toInt()

    private val guidedOpeningPool = listOf(
        EntityType.DUCK,
        EntityType.LILY_OF_VALLEY,
        EntityType.CAT,
        EntityType.TIT,
        EntityType.CACTUS
    )

    fun isRandomSpawnLocked(runTimeSeconds: Float): Boolean =
        runTimeSeconds < RANDOM_SPAWN_LOCKOUT_SEC

    fun adjustedSpawnInterval(runTimeSeconds: Float, defaultInterval: Float): Float = when {
        runTimeSeconds < RANDOM_SPAWN_LOCKOUT_SEC -> defaultInterval
        runTimeSeconds < 12f -> maxOf(defaultInterval, 1.95f)
        runTimeSeconds < 20f -> maxOf(defaultInterval, 1.78f)
        runTimeSeconds < GUIDED_WINDOW_SEC -> maxOf(defaultInterval, 1.58f)
        else -> defaultInterval
    }

    fun spawnPoolFor(runTimeSeconds: Float, defaultPool: List<EntityType>): List<EntityType> =
        if (runTimeSeconds < GUIDED_WINDOW_SEC) guidedOpeningPool else defaultPool

    fun cueFor(
        runTimeSeconds: Float,
        inputState: OpeningInputState,
        routeTier: PacifistRouteTier,
        mercyHearts: Int,
        kindnessChain: Int
    ): OpeningGuidanceCue? {
        if (runTimeSeconds >= GUIDED_WINDOW_SEC) return null

        val chips = listOf(
            OpeningGuidanceChip("Tap", inputState.jumpSeen),
            OpeningGuidanceChip("Hold", inputState.holdSeen),
            OpeningGuidanceChip("Duck", inputState.duckSeen)
        )

        val cue = when {
            runTimeSeconds < 3.2f && !inputState.jumpSeen -> OpeningGuidanceCue(
                title = "Find The Stride",
                line = "Tap to clear the first low lane.",
                accentColor = ACCENT_SOFT,
                chips = chips
            )
            !inputState.jumpSeen -> OpeningGuidanceCue(
                title = "Tap To Hop",
                line = "Short jumps keep the opening gentle.",
                accentColor = ACCENT_SOFT,
                chips = chips
            )
            !inputState.holdSeen && runTimeSeconds >= 4.5f -> OpeningGuidanceCue(
                title = "Hold For Height",
                line = "Longer presses carry you over taller shapes.",
                accentColor = ACCENT_SOFT,
                chips = chips
            )
            !inputState.duckSeen && runTimeSeconds >= 10.5f -> OpeningGuidanceCue(
                title = "Duck The Low Lane",
                line = "Swipe down when wings or petals stay low.",
                accentColor = ACCENT_ROUTE,
                chips = chips
            )
            routeTier == PacifistRouteTier.PEACEFUL || kindnessChain >= 3 -> OpeningGuidanceCue(
                title = "Keep It Peaceful",
                line = "Clean passes soften what the forest sends next.",
                accentColor = ACCENT_KIND,
                chips = chips
            )
            mercyHearts > 0 || routeTier == PacifistRouteTier.MERCIFUL -> OpeningGuidanceCue(
                title = "Stay Light",
                line = "Near-misses count when you leave room to spare.",
                accentColor = ACCENT_ROUTE,
                chips = chips
            )
            else -> OpeningGuidanceCue(
                title = "Read Early",
                line = "The opening stays sparse so the lane teaches itself.",
                accentColor = ACCENT_SOFT,
                chips = chips
            )
        }
        return cue
    }
}
