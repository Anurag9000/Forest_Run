#!/usr/bin/env python3
"""Apply Garden, return-moment, menu, sanctuary, and rest-screen fixes."""

from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: {label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def main() -> None:
    return_moments = Path("app/src/main/java/com/yourname/forest_run/engine/ReturnMomentsSystem.kt")
    replace_once(
        return_moments,
        "    private const val DAY_MS = 24L * 60L * 60L * 1_000L\n",
        "",
        "remove UTC day bucket constant",
    )
    replace_once(
        return_moments,
        "        val dayId = nowMs / DAY_MS\n",
        "        val dayId = localCalendarDayId(nowMs)\n",
        "use local calendar day",
    )

    garden = Path("app/src/main/java/com/yourname/forest_run/ui/GardenScreen.kt")
    replace_once(
        garden,
        """    fun load() {\n        unlockedCount = SaveManager.loadGardenProgress(context).coerceAtLeast(1)\n        lifeSeeds     = SaveManager.loadLifetimeSeeds(context)\n        syncWardrobe()\n        refreshStats()\n    }\n\n    /** Called after a run to refresh the seed count. */\n    fun refresh() {\n        lifeSeeds = SaveManager.loadLifetimeSeeds(context)\n        syncWardrobe()\n        refreshStats()\n    }\n""",
        """    fun load() {\n        unlockedCount = SaveManager.loadGardenProgress(context).coerceAtLeast(1)\n        lifeSeeds     = SaveManager.loadLifetimeSeeds(context)\n        syncWardrobe()\n        // Surface creation may happen while the menu is still visible. Preview\n        // the greeting here; only an actual Garden entry may consume it.\n        refreshStats(consumeReturnMoment = false)\n    }\n\n    /** Called when the Garden is actually entered or revisited. */\n    fun refresh() {\n        lifeSeeds = SaveManager.loadLifetimeSeeds(context)\n        syncWardrobe()\n        refreshStats(consumeReturnMoment = true)\n    }\n""",
        "separate preview from greeting consumption",
    )
    replace_once(
        garden,
        """        catalogueSprites.forEach { it.update(deltaTime) }\n        returnVisitorSprite?.update(deltaTime)\n        if (unlockAnim >= 0f) {\n""",
        """        catalogueSprites.forEach { it.update(deltaTime) }\n        returnVisitorSprite?.update(deltaTime)\n        ParticleManager.update(deltaTime)\n        if (unlockAnim >= 0f) {\n""",
        "advance Garden particles",
    )
    replace_once(
        garden,
        """                    wardrobeMessageTimer = 2.5f\n                    refreshStats()\n""",
        """                    wardrobeMessageTimer = 2.5f\n                    refreshStats(consumeReturnMoment = false)\n""",
        "wardrobe refresh must not consume greeting",
    )
    replace_once(
        garden,
        '        canvas.drawText("tap anywhere to go back", cw / 2f, ch * 0.93f, backPaint)\n',
        '        canvas.drawText("tap the bottom edge to go back", cw / 2f, ch * 0.93f, backPaint)\n',
        "truthful Garden back hint",
    )
    replace_once(
        garden,
        """    private fun refreshStats() {\n        bestDistance = SaveManager.loadBestDistance(context)\n""",
        """    private fun refreshStats(consumeReturnMoment: Boolean) {\n        bestDistance = SaveManager.loadBestDistance(context)\n""",
        "parameterize Garden refresh",
    )
    replace_once(
        garden,
        "        returnMoment = ReturnMomentsSystem.resolveGardenMoment(context, lastRunSummary)\n",
        """        returnMoment = if (consumeReturnMoment) {\n            ReturnMomentsSystem.resolveGardenMoment(context, lastRunSummary)\n        } else {\n            ReturnMomentsSystem.previewGardenMoment(context, lastRunSummary)\n        }\n""",
        "preview or consume return moment",
    )

    menu = Path("app/src/main/java/com/yourname/forest_run/ui/MainMenuScreen.kt")
    replace_once(
        menu,
        """    fun refreshCopy() {\n        sceneCopy = SessionArcComposer.menuCopy(context)\n        val summary = SaveManager.loadLastRunSummary(context.applicationContext)\n        sanctuaryState = GardenSanctuaryPlanner.build(context, summary)\n    }\n\n    /** Consume a pending run-start request so it only fires once. */\n""",
        """    fun refreshCopy() {\n        sceneCopy = SessionArcComposer.menuCopy(context)\n        val summary = SaveManager.loadLastRunSummary(context.applicationContext)\n        sanctuaryState = GardenSanctuaryPlanner.build(context, summary)\n    }\n\n    /** Restore the willow sit-rise ritual whenever the player returns home. */\n    fun resetRitual() {\n        phase = Phase.IDLE\n        standTimer = 0f\n        startRunRequested = false\n        standPlayerSprite.reset()\n        readyPlayerSprite.reset()\n        idlePlayerSprite.setFrame(3)\n    }\n\n    /** Consume a pending run-start request so it only fires once. */\n""",
        "add menu ritual reset",
    )

    game_view = Path("app/src/main/java/com/yourname/forest_run/engine/GameView.kt")
    replace_once(
        game_view,
        """            gardenScreen.onBack = {\n                if (::mainMenuScreen.isInitialized) mainMenuScreen.refreshCopy()\n                appState = AppGameState.MENU\n            }\n""",
        """            gardenScreen.onBack = {\n                if (::mainMenuScreen.isInitialized) {\n                    mainMenuScreen.resetRitual()\n                    mainMenuScreen.refreshCopy()\n                }\n                appState = AppGameState.MENU\n            }\n""",
        "reset menu on Garden exit",
    )

    sanctuary = Path("app/src/main/java/com/yourname/forest_run/engine/GardenSanctuaryPlanner.kt")
    replace_once(
        sanctuary,
        """            fireflyCount = fireflies,\n            petalCount = petals,\n            bloomPatchCount = bloomPatches,\n            mistBandCount = mistBands,\n            lanternGlowCount = lanternGlows,\n            groundGlowAlpha = groundGlowAlpha.coerceAtMost(180),\n            canopyShadeAlpha = canopyShadeAlpha,\n""",
        """            fireflyCount = fireflies.coerceAtLeast(0),\n            petalCount = petals.coerceAtLeast(0),\n            bloomPatchCount = bloomPatches.coerceAtLeast(0),\n            mistBandCount = mistBands.coerceAtLeast(0),\n            lanternGlowCount = lanternGlows.coerceAtLeast(0),\n            groundGlowAlpha = groundGlowAlpha.coerceIn(0, 180),\n            canopyShadeAlpha = canopyShadeAlpha.coerceIn(0, 255),\n""",
        "bound sanctuary atmosphere",
    )

    game_over = Path("app/src/main/java/com/yourname/forest_run/ui/GameOverScreen.kt")
    replace_once(
        game_over,
        "import com.yourname.forest_run.engine.PostRunReflectionPlanner\n",
        """import com.yourname.forest_run.engine.PostRunReflectionEntry\nimport com.yourname.forest_run.engine.PostRunReflectionPlanner\n""",
        "import reflection entry",
    )
    replace_once(
        game_over,
        "import com.yourname.forest_run.engine.RunSummary\n",
        """import com.yourname.forest_run.engine.RestSceneCopy\nimport com.yourname.forest_run.engine.RunSummary\n""",
        "import rest scene copy",
    )
    replace_once(
        game_over,
        """    private val cx         = screenWidth / 2f\n    private val cinematicOverlay = CinematicOverlayRenderer()\n\n    // ── Update ────────────────────────────────────────────────────────────\n""",
        """    private val cx         = screenWidth / 2f\n    private val cinematicOverlay = CinematicOverlayRenderer()\n\n    private data class RestComposition(\n        val summary: RunSummary,\n        val sceneCopy: RestSceneCopy,\n        val sanctuaryState: GardenSanctuaryState,\n        val reflectionEntry: PostRunReflectionEntry?\n    )\n\n    private var cachedComposition: RestComposition? = null\n\n    private fun compositionFor(summary: RunSummary): RestComposition {\n        cachedComposition?.takeIf { it.summary == summary }?.let { return it }\n\n        val sceneCopy = SessionArcComposer.restCopy(appContext, summary)\n        val sanctuaryState = GardenSanctuaryPlanner.build(appContext, summary)\n        return RestComposition(\n            summary = summary,\n            sceneCopy = sceneCopy,\n            sanctuaryState = sanctuaryState,\n            reflectionEntry = PostRunReflectionPlanner.restEntry(\n                summary = summary,\n                sanctuaryState = sanctuaryState,\n                recoveryLine = sceneCopy.recoveryLine,\n                carryHomeLine = sceneCopy.carryHomeLine\n            )\n        ).also { cachedComposition = it }\n    }\n\n    // ── Update ────────────────────────────────────────────────────────────\n""",
        "cache rest composition",
    )
    replace_once(
        game_over,
        """        val sceneCopy = SessionArcComposer.restCopy(appContext, summary)\n        val sanctuaryState = GardenSanctuaryPlanner.build(appContext, summary)\n        val reflectionEntry = PostRunReflectionPlanner.restEntry(\n            summary = summary,\n            sanctuaryState = sanctuaryState,\n            recoveryLine = sceneCopy.recoveryLine,\n            carryHomeLine = sceneCopy.carryHomeLine\n        )\n""",
        """        val composition = compositionFor(summary)\n        val sceneCopy = composition.sceneCopy\n        val sanctuaryState = composition.sanctuaryState\n        val reflectionEntry = composition.reflectionEntry\n""",
        "use cached rest composition",
    )


if __name__ == "__main__":
    main()
