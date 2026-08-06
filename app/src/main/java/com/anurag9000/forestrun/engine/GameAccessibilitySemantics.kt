package com.anurag9000.forestrun.engine

/** High-level Canvas surfaces that need deterministic screen-reader semantics. */
internal enum class AccessibilitySurface {
    MENU,
    SETTINGS,
    PLAYING,
    GARDEN,
    REST
}

/** Semantic actions supported by a virtual Canvas accessibility node. */
internal enum class AccessibilitySemanticAction {
    ACTIVATE,
    INCREMENT,
    DECREMENT,
    JUMP,
    LONG_JUMP,
    DUCK,
    DISMISS
}

/** Stable virtual node IDs. Never derive these from list indices or draw order. */
internal object AccessibilityNodeIds {
    const val MENU_CONTINUE = 100
    const val MENU_GARDEN = 110
    const val MENU_SETTINGS = 120
    const val SETTINGS_REDUCED_MOTION = 200
    const val SETTINGS_AUDIO = 210
    const val SETTINGS_HAPTICS = 220
    const val SETTINGS_CLOSE = 230
    const val RUN_STATUS = 300
    const val RUN_JUMP = 310
    const val RUN_LONG_JUMP = 320
    const val RUN_DUCK = 330
    const val GARDEN_SUMMARY = 400
    const val GARDEN_FIRST_PLANT = 410
    const val GARDEN_WARDROBE = 500
    const val GARDEN_RUN = 510
    const val GARDEN_HOME = 520
    const val REST_SUMMARY = 600
    const val REST_CONTINUE = 610
}

internal data class AccessibilitySemanticNode(
    val id: Int,
    val focusOrder: Int,
    val label: String,
    val stateDescription: String? = null,
    val actions: Set<AccessibilitySemanticAction> = emptySet(),
    val enabled: Boolean = true,
    val liveRegion: Boolean = false
)

/** Immutable presentation facts needed to build one semantic tree. */
internal data class AccessibilitySemanticSnapshot(
    val surface: AccessibilitySurface,
    val reducedMotion: Boolean = false,
    val audioEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val distanceM: Int = 0,
    val score: Int = 0,
    val seeds: Int = 0,
    val bloomReady: Boolean = false,
    val bloomActive: Boolean = false,
    val gardenUnlockedPlants: Int = 0,
    val gardenTotalPlants: Int = 9,
    val nextPlantCost: Int? = null,
    val wardrobeUnlocked: Boolean = false,
    val restQuote: String = "",
    val restSummary: String = ""
)

/**
 * Pure, allocation-bounded semantic planner for the custom Canvas UI.
 *
 * The result is sorted by explicit focus order and validates stable IDs. It is
 * intentionally independent from draw order, animation, touch geometry, and
 * translated safe-content coordinates. A future AccessibilityNodeProvider can
 * expose this tree without re-encoding product wording or state rules.
 */
internal object GameAccessibilitySemantics {
    fun build(snapshot: AccessibilitySemanticSnapshot): List<AccessibilitySemanticNode> {
        require(snapshot.distanceM >= 0) { "distance must be non-negative" }
        require(snapshot.score >= 0) { "score must be non-negative" }
        require(snapshot.seeds >= 0) { "seeds must be non-negative" }
        require(snapshot.gardenTotalPlants in 1..MAX_GARDEN_PLANTS) {
            "garden total must be bounded"
        }
        require(snapshot.gardenUnlockedPlants in 0..snapshot.gardenTotalPlants) {
            "unlocked plant count must be within the Garden"
        }
        require(snapshot.nextPlantCost == null || snapshot.nextPlantCost >= 0) {
            "next plant cost must be non-negative"
        }

        val nodes = when (snapshot.surface) {
            AccessibilitySurface.MENU -> menuNodes()
            AccessibilitySurface.SETTINGS -> settingsNodes(snapshot)
            AccessibilitySurface.PLAYING -> playingNodes(snapshot)
            AccessibilitySurface.GARDEN -> gardenNodes(snapshot)
            AccessibilitySurface.REST -> restNodes(snapshot)
        }
        require(nodes.map(AccessibilitySemanticNode::id).distinct().size == nodes.size) {
            "semantic node IDs must be unique"
        }
        require(nodes.map(AccessibilitySemanticNode::focusOrder).distinct().size == nodes.size) {
            "semantic focus order must be unique"
        }
        return nodes.sortedBy(AccessibilitySemanticNode::focusOrder)
    }

    private fun menuNodes(): List<AccessibilitySemanticNode> = listOf(
        node(
            id = AccessibilityNodeIds.MENU_CONTINUE,
            order = 10,
            label = "Begin forest run",
            action = AccessibilitySemanticAction.ACTIVATE
        ),
        node(
            id = AccessibilityNodeIds.MENU_GARDEN,
            order = 20,
            label = "Open Garden",
            action = AccessibilitySemanticAction.ACTIVATE
        ),
        node(
            id = AccessibilityNodeIds.MENU_SETTINGS,
            order = 30,
            label = "Open feedback settings",
            action = AccessibilitySemanticAction.ACTIVATE
        )
    )

    private fun settingsNodes(
        snapshot: AccessibilitySemanticSnapshot
    ): List<AccessibilitySemanticNode> = listOf(
        toggleNode(
            id = AccessibilityNodeIds.SETTINGS_REDUCED_MOTION,
            order = 10,
            label = "Reduced motion",
            checked = snapshot.reducedMotion
        ),
        toggleNode(
            id = AccessibilityNodeIds.SETTINGS_AUDIO,
            order = 20,
            label = "Audio",
            checked = snapshot.audioEnabled
        ),
        toggleNode(
            id = AccessibilityNodeIds.SETTINGS_HAPTICS,
            order = 30,
            label = "Haptics",
            checked = snapshot.hapticsEnabled
        ),
        node(
            id = AccessibilityNodeIds.SETTINGS_CLOSE,
            order = 40,
            label = "Close settings",
            action = AccessibilitySemanticAction.DISMISS
        )
    )

    private fun playingNodes(
        snapshot: AccessibilitySemanticSnapshot
    ): List<AccessibilitySemanticNode> {
        val bloom = when {
            snapshot.bloomActive -> "Bloom active"
            snapshot.bloomReady -> "Bloom ready"
            else -> "Bloom charging"
        }
        return listOf(
            AccessibilitySemanticNode(
                id = AccessibilityNodeIds.RUN_STATUS,
                focusOrder = 10,
                label = "Run status",
                stateDescription =
                    "${snapshot.distanceM} metres, score ${snapshot.score}, " +
                        "${snapshot.seeds} Seeds, $bloom",
                liveRegion = true
            ),
            node(
                id = AccessibilityNodeIds.RUN_JUMP,
                order = 20,
                label = "Jump",
                action = AccessibilitySemanticAction.JUMP
            ),
            node(
                id = AccessibilityNodeIds.RUN_LONG_JUMP,
                order = 30,
                label = "Long jump",
                action = AccessibilitySemanticAction.LONG_JUMP
            ),
            node(
                id = AccessibilityNodeIds.RUN_DUCK,
                order = 40,
                label = "Duck",
                action = AccessibilitySemanticAction.DUCK
            )
        )
    }

    private fun gardenNodes(
        snapshot: AccessibilitySemanticSnapshot
    ): List<AccessibilitySemanticNode> = buildList {
        add(
            AccessibilitySemanticNode(
                id = AccessibilityNodeIds.GARDEN_SUMMARY,
                focusOrder = 10,
                label = "Garden progress",
                stateDescription =
                    "${snapshot.gardenUnlockedPlants} of " +
                        "${snapshot.gardenTotalPlants} plants grown, " +
                        "${snapshot.seeds} Seeds available"
            )
        )
        repeat(snapshot.gardenTotalPlants) { index ->
            val unlocked = index < snapshot.gardenUnlockedPlants
            val isNext = index == snapshot.gardenUnlockedPlants &&
                snapshot.gardenUnlockedPlants < snapshot.gardenTotalPlants
            val cost = if (isNext) snapshot.nextPlantCost else null
            val description = when {
                unlocked -> "Grown"
                isNext && cost != null -> "Next plant, costs $cost Seeds"
                isNext -> "Next plant"
                else -> "Locked"
            }
            add(
                AccessibilitySemanticNode(
                    id = AccessibilityNodeIds.GARDEN_FIRST_PLANT + index,
                    focusOrder = 20 + index,
                    label = "Garden plant ${index + 1}",
                    stateDescription = description,
                    actions = if (isNext && cost != null && snapshot.seeds >= cost) {
                        setOf(AccessibilitySemanticAction.ACTIVATE)
                    } else {
                        emptySet()
                    },
                    enabled = unlocked || isNext
                )
            )
        }
        add(
            AccessibilitySemanticNode(
                id = AccessibilityNodeIds.GARDEN_WARDROBE,
                focusOrder = 40,
                label = "Wardrobe",
                stateDescription = if (snapshot.wardrobeUnlocked) "Unlocked" else "Locked",
                actions = if (snapshot.wardrobeUnlocked) {
                    setOf(AccessibilitySemanticAction.ACTIVATE)
                } else {
                    emptySet()
                },
                enabled = snapshot.wardrobeUnlocked
            )
        )
        add(
            node(
                id = AccessibilityNodeIds.GARDEN_RUN,
                order = 50,
                label = "Begin another run",
                action = AccessibilitySemanticAction.ACTIVATE
            )
        )
        add(
            node(
                id = AccessibilityNodeIds.GARDEN_HOME,
                order = 60,
                label = "Return home",
                action = AccessibilitySemanticAction.ACTIVATE
            )
        )
    }

    private fun restNodes(
        snapshot: AccessibilitySemanticSnapshot
    ): List<AccessibilitySemanticNode> = listOf(
        AccessibilitySemanticNode(
            id = AccessibilityNodeIds.REST_SUMMARY,
            focusOrder = 10,
            label = snapshot.restQuote.ifBlank { "Rest beneath the willow" },
            stateDescription = snapshot.restSummary.ifBlank {
                "Score ${snapshot.score}, ${snapshot.distanceM} metres, " +
                    "${snapshot.seeds} Seeds"
            }
        ),
        node(
            id = AccessibilityNodeIds.REST_CONTINUE,
            order = 20,
            label = "Continue to Garden",
            action = AccessibilitySemanticAction.ACTIVATE
        )
    )

    private fun node(
        id: Int,
        order: Int,
        label: String,
        action: AccessibilitySemanticAction
    ): AccessibilitySemanticNode = AccessibilitySemanticNode(
        id = id,
        focusOrder = order,
        label = label,
        actions = setOf(action)
    )

    private fun toggleNode(
        id: Int,
        order: Int,
        label: String,
        checked: Boolean
    ): AccessibilitySemanticNode = AccessibilitySemanticNode(
        id = id,
        focusOrder = order,
        label = label,
        stateDescription = if (checked) "On" else "Off",
        actions = setOf(AccessibilitySemanticAction.ACTIVATE)
    )

    private const val MAX_GARDEN_PLANTS = 9
}
