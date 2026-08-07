#!/usr/bin/env python3
"""One-shot exact migration from synthetic root actions to virtual accessibility nodes."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GAME_VIEW = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt"
MAIN_ACTIVITY = ROOT / "app/src/main/java/com/anurag9000/forestrun/MainActivity.kt"
MAIN_MENU = ROOT / "app/src/main/java/com/anurag9000/forestrun/ui/MainMenuScreen.kt"
LEGACY_DELEGATE = ROOT / "app/src/main/java/com/anurag9000/forestrun/ForestRunAccessibilityDelegate.kt"
LEGACY_TEST = ROOT / "app/src/test/java/com/anurag9000/forestrun/ForestRunAccessibilityDelegateTest.kt"
WORKFLOW = ROOT / ".github/workflows/live-accessibility-migration.yml"
SELF = Path(__file__)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


def migrate_game_view() -> None:
    source = GAME_VIEW.read_text(encoding="utf-8")
    source = replace_once(
        source,
        "import android.graphics.Paint\n",
        "import android.graphics.Paint\nimport android.graphics.Rect\n",
        "GameView Rect import",
    )
    source = replace_once(
        source,
        "import android.view.SurfaceView\n",
        "import android.view.SurfaceView\n"
        "import android.view.accessibility.AccessibilityManager\n"
        "import android.view.accessibility.AccessibilityNodeProvider\n",
        "GameView accessibility imports",
    )
    source = replace_once(
        source,
        "import kotlin.math.hypot\n",
        "import kotlin.math.ceil\nimport kotlin.math.floor\nimport kotlin.math.hypot\n",
        "GameView rounding imports",
    )

    accessibility_fields = '''    @Volatile
    private var safeContentTransform = SafeContentTransform.create(1, 1)
    private var accessibilitySettingsOpen = false
    private val accessibilityManager = context.getSystemService(AccessibilityManager::class.java)
    private val liveAccessibilityActions by lazy {
        LiveGameAccessibilityActions(
            menuPrimaryAction = ::performAccessibilityMenuPrimary,
            sessionEventAction = ::applyRunSessionEvent,
            openSettingsAction = ::openAccessibilitySettings,
            closeSettingsAction = ::closeAccessibilitySettings,
            toggleReducedMotionAction = ::toggleAccessibilityReducedMotion,
            toggleAudioAction = ::toggleAccessibilityAudio,
            toggleHapticsAction = ::toggleAccessibilityHaptics,
            jumpAction = { performAccessibilityJump(holdSeconds = 0f) },
            longJumpAction = { performAccessibilityJump(holdSeconds = 0.42f) },
            duckAction = ::performAccessibilityDuck,
            purchasePlantAction = ::performAccessibilityPlantPurchase,
            equipCostumeAction = ::performAccessibilityCostumeEquip
        )
    }
    private val accessibilityActionRouter by lazy {
        GameAccessibilityActionRouter(
            snapshotProvider = ::buildAccessibilitySnapshot,
            handler = liveAccessibilityActions
        )
    }
    private val gameAccessibilityNodeProvider by lazy {
        GameAccessibilityNodeProvider(
            hostView = this,
            router = accessibilityActionRouter,
            boundsResolver = AccessibilityNodeBoundsResolver(::accessibilityBoundsFor)
        )
    }

    override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider =
        gameAccessibilityNodeProvider

    // -----------------------------------------------------------------------
    // Lifecycle
'''
    source = replace_once(
        source,
        '''    @Volatile
    private var safeContentTransform = SafeContentTransform.create(1, 1)

    // -----------------------------------------------------------------------
    // Lifecycle
''',
        accessibility_fields,
        "GameView accessibility fields",
    )
    source = replace_once(
        source,
        '''    init {
        holder.addCallback(this)
''',
        '''    init {
        holder.addCallback(this)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isFocusable = true
''',
        "GameView accessibility host flags",
    )

    source = replace_once(
        source,
        '''    private fun applyRunSessionEvent(event: RunSessionEvent): Boolean {
        val result = runSessionTransitions.execute(
            current = RunSessionSnapshot(appState, runState),
            event = event
        )
        if (!result.mayAdoptAfterState) return false
        appState = result.transition.after.appState
        runState = result.transition.after.runState
        return true
    }
''',
        '''    private fun applyRunSessionEvent(event: RunSessionEvent): Boolean {
        val result = runSessionTransitions.execute(
            current = RunSessionSnapshot(appState, runState),
            event = event
        )
        if (!result.mayAdoptAfterState) return false
        appState = result.transition.after.appState
        runState = result.transition.after.runState
        accessibilitySettingsOpen = false
        notifyAccessibilityTreeChanged()
        return true
    }
''',
        "GameView session accessibility notification",
    )

    accessibility_methods = r'''    private fun buildAccessibilitySnapshot(): AccessibilitySemanticSnapshot {
        val surface = when {
            appState == AppGameState.MENU && accessibilitySettingsOpen ->
                AccessibilitySurface.SETTINGS
            appState == AppGameState.MENU -> AccessibilitySurface.MENU
            appState == AppGameState.GARDEN -> AccessibilitySurface.GARDEN
            runState != RunState.PLAYING -> AccessibilitySurface.REST
            else -> AccessibilitySurface.PLAYING
        }
        val feedback = FeedbackSettings.snapshot()
        val summary = currentRunSummary
        val liveDistance = if (::gameState.isInitialized) gameState.distanceMetres else 0f
        val distanceM = boundedAccessibilityDistance(summary?.distanceM ?: liveDistance)
        val score = (summary?.score ?: if (::gameState.isInitialized) gameState.score else 0)
            .coerceAtLeast(0)
        val runSeeds = (
            summary?.seedsCollected ?: if (::gameState.isInitialized) gameState.seedsThisRun else 0
        ).coerceAtLeast(0)

        val gardenActive = surface == AccessibilitySurface.GARDEN
        val gardenUnlocked = if (gardenActive) {
            SaveManager.loadGardenProgress(context).coerceIn(1, GardenEconomy.catalogueSize)
        } else {
            0
        }
        val gardenSeeds = if (gardenActive) {
            SaveManager.loadLifetimeSeeds(context).coerceAtLeast(0)
        } else {
            runSeeds
        }
        val nextPlantCost = if (gardenActive && gardenUnlocked < GardenEconomy.catalogueSize) {
            GardenEconomy.seedCostForIndex(gardenUnlocked)
        } else {
            null
        }
        val availableCostumes = if (gardenActive) {
            CostumeManager.availableCostumes(context).toSet() + CostumeStyle.NONE
        } else {
            setOf(CostumeStyle.NONE)
        }
        val activeCostume = if (gardenActive) {
            CostumeManager.activeCostume(context).takeIf { it in availableCostumes }
                ?: CostumeStyle.NONE
        } else {
            CostumeStyle.NONE
        }
        val bloomActive = ::gameState.isInitialized && gameState.isBloomActive
        val bloomReady = ::gameState.isInitialized &&
            !bloomActive &&
            gameState.bloomMeter >= gameState.bloomSeedTarget - 1
        val restSummary = summary?.let {
            "Score ${it.score.coerceAtLeast(0)}, " +
                "${boundedAccessibilityDistance(it.distanceM)} metres, " +
                "${it.seedsCollected.coerceAtLeast(0)} Seeds, " +
                "${it.sparedCount.coerceAtLeast(0)} spared"
        }.orEmpty()

        return AccessibilitySemanticSnapshot(
            surface = surface,
            reducedMotion = feedback.reducedMotion,
            audioEnabled = feedback.audioEnabled,
            hapticsEnabled = feedback.hapticsEnabled,
            distanceM = distanceM,
            score = score,
            seeds = gardenSeeds,
            bloomReady = bloomReady,
            bloomActive = bloomActive,
            gardenUnlockedPlants = gardenUnlocked,
            gardenTotalPlants = GardenEconomy.catalogueSize,
            nextPlantCost = nextPlantCost,
            wardrobeUnlocked = availableCostumes.size > 1,
            wardrobeUnlockedCostumes = availableCostumes,
            activeCostume = activeCostume,
            restQuote = summary?.restQuote?.ifBlank { currentRestQuote } ?: currentRestQuote,
            restSummary = restSummary,
            restContinueEnabled = runState == RunState.GAME_OVER
        )
    }

    private fun accessibilityBoundsFor(nodeId: Int): Rect {
        val transform = safeContentTransform
        val logical = GameAccessibilityGeometry.boundsFor(
            nodeId = nodeId,
            width = transform.logicalWidth.toFloat(),
            height = transform.logicalHeight.toFloat()
        )
        val topLeft = transform.toPhysical(logical.left, logical.top)
        val bottomRight = transform.toPhysical(logical.right, logical.bottom)
        return Rect(
            floor(topLeft.x.toDouble()).toInt(),
            floor(topLeft.y.toDouble()).toInt(),
            ceil(bottomRight.x.toDouble()).toInt(),
            ceil(bottomRight.y.toDouble()).toInt()
        )
    }

    private fun boundedAccessibilityDistance(value: Float): Int {
        val safe = value.takeIf { it.isFinite() && it >= 0f } ?: 0f
        return safe.toDouble().coerceAtMost(Int.MAX_VALUE.toDouble()).toInt()
    }

    private fun notifyAccessibilityTreeChanged() {
        if (accessibilityManager?.isEnabled == true) {
            gameAccessibilityNodeProvider.notifySemanticTreeChanged()
        }
    }

    private fun performAccessibilityMenuPrimary(): Boolean {
        if (appState != AppGameState.MENU || accessibilitySettingsOpen) return false
        if (!::mainMenuScreen.isInitialized) return false
        val accepted = mainMenuScreen.performAccessibilityPrimaryAction()
        if (accepted) notifyAccessibilityTreeChanged()
        return accepted
    }

    private fun openAccessibilitySettings(): Boolean {
        if (appState != AppGameState.MENU || accessibilitySettingsOpen) return false
        accessibilitySettingsOpen = true
        notifyAccessibilityTreeChanged()
        return true
    }

    private fun closeAccessibilitySettings(): Boolean {
        if (appState != AppGameState.MENU || !accessibilitySettingsOpen) return false
        accessibilitySettingsOpen = false
        notifyAccessibilityTreeChanged()
        return true
    }

    private fun toggleAccessibilityReducedMotion(): Boolean {
        if (appState != AppGameState.MENU || !accessibilitySettingsOpen) return false
        FeedbackSettings.setReducedMotion(context, !FeedbackSettings.reducedMotion)
        notifyAccessibilityTreeChanged()
        return true
    }

    private fun toggleAccessibilityAudio(): Boolean {
        if (appState != AppGameState.MENU || !accessibilitySettingsOpen) return false
        FeedbackSettings.setAudioEnabled(context, !FeedbackSettings.audioEnabled)
        notifyAccessibilityTreeChanged()
        return true
    }

    private fun toggleAccessibilityHaptics(): Boolean {
        if (appState != AppGameState.MENU || !accessibilitySettingsOpen) return false
        FeedbackSettings.setHapticsEnabled(context, !FeedbackSettings.hapticsEnabled)
        notifyAccessibilityTreeChanged()
        return true
    }

    private fun performAccessibilityJump(holdSeconds: Float): Boolean {
        if (!acceptsGameplayInput()) return false
        val pressed = inputHandler.onJumpPressed ?: return false
        val released = inputHandler.onJumpReleased ?: return false
        pressed.invoke()
        val safeHold = holdSeconds.coerceIn(0f, 0.6f)
        if (safeHold > 0f) inputHandler.onJumpHeld?.invoke(safeHold)
        released.invoke(safeHold)
        return true
    }

    private fun performAccessibilityDuck(): Boolean {
        if (!acceptsGameplayInput()) return false
        val pressed = inputHandler.onDuckPressed ?: return false
        val released = inputHandler.onDuckReleased ?: return false
        pressed.invoke()
        postDelayed(
            {
                if (acceptsGameplayInput()) released.invoke()
            },
            280L
        )
        return true
    }

    private fun performAccessibilityPlantPurchase(index: Int): Boolean {
        if (appState != AppGameState.GARDEN || !::gardenScreen.isInitialized) return false
        val result = GardenPurchaseManager.purchaseNext(context, index)
        gardenScreen.load()
        if (result.purchased) notifyAccessibilityTreeChanged()
        return result.purchased
    }

    private fun performAccessibilityCostumeEquip(style: CostumeStyle): Boolean {
        if (appState != AppGameState.GARDEN || !::gardenScreen.isInitialized) return false
        val equipped = CostumeManager.equip(context, style)
        if (equipped) {
            gardenScreen.load()
            notifyAccessibilityTreeChanged()
        }
        return equipped
    }

'''
    source = replace_once(
        source,
        '''    /** Called once after [player] is initialized to attach physics callbacks. */
    private fun wirePlayerToInput() {
''',
        accessibility_methods
        + '''    /** Called once after [player] is initialized to attach physics callbacks. */
    private fun wirePlayerToInput() {
''',
        "GameView accessibility methods",
    )

    GAME_VIEW.write_text(source, encoding="utf-8")


def migrate_main_activity() -> None:
    source = MAIN_ACTIVITY.read_text(encoding="utf-8")
    source = replace_once(
        source,
        '''        gameView = GameView(this)
        attachForestRunAccessibility(gameView, gameView.inputHandler)
        setContentView(gameView)
''',
        '''        gameView = GameView(this)
        setContentView(gameView)
''',
        "MainActivity legacy accessibility attach",
    )
    MAIN_ACTIVITY.write_text(source, encoding="utf-8")


def migrate_main_menu() -> None:
    source = MAIN_MENU.read_text(encoding="utf-8")
    method = '''    /** Accessibility equivalent of the primary willow ritual action. */
    fun performAccessibilityPrimaryAction(): Boolean = when (phase) {
        Phase.IDLE -> {
            phase = Phase.STANDING_UP
            standTimer = 0f
            true
        }
        Phase.STANDING_UP -> false
        Phase.READY -> {
            startRunRequested = true
            true
        }
    }

'''
    source = replace_once(
        source,
        "    fun update(deltaTime: Float) {\n",
        method + "    fun update(deltaTime: Float) {\n",
        "MainMenu accessibility ritual action",
    )
    MAIN_MENU.write_text(source, encoding="utf-8")


def remove_legacy_authority() -> None:
    for path in (LEGACY_DELEGATE, LEGACY_TEST):
        if not path.exists():
            raise SystemExit(f"legacy accessibility path missing before migration: {path}")
        path.unlink()


def verify() -> None:
    game_view = GAME_VIEW.read_text(encoding="utf-8")
    activity = MAIN_ACTIVITY.read_text(encoding="utf-8")
    menu = MAIN_MENU.read_text(encoding="utf-8")
    required = (
        "override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider",
        "GameAccessibilityNodeProvider(",
        "GameAccessibilityActionRouter(",
        "LiveGameAccessibilityActions(",
        "AccessibilitySemanticSnapshot(",
        "RunSessionEvent.REST_TAPPED",
        "GardenPurchaseManager.purchaseNext(context, index)",
        "CostumeManager.equip(context, style)",
        "performAccessibilityPrimaryAction()",
    )
    for token in required:
        if token not in game_view and token not in menu:
            raise SystemExit(f"missing migrated accessibility token: {token}")
    forbidden = (
        "attachForestRunAccessibility(",
        "MotionEvent.obtain(",
        "dispatchTouchEvent(",
    )
    combined = activity + game_view
    for token in forbidden:
        if token in combined:
            raise SystemExit(f"legacy synthetic accessibility path survived: {token}")
    if LEGACY_DELEGATE.exists() or LEGACY_TEST.exists():
        raise SystemExit("legacy accessibility authority survived migration")


def remove_temporary_migration_files() -> None:
    for path in (WORKFLOW, SELF):
        if path.exists():
            path.unlink()


def main() -> None:
    migrate_game_view()
    migrate_main_activity()
    migrate_main_menu()
    remove_legacy_authority()
    verify()
    remove_temporary_migration_files()


if __name__ == "__main__":
    main()
