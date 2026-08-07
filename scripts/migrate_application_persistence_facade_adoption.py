#!/usr/bin/env python3
"""One-shot exact migration to make ApplicationPersistenceFacade the live write boundary."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ENGINE = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine"
UI = ROOT / "app/src/main/java/com/anurag9000/forestrun/ui"
TEST = ROOT / "app/src/test/java/com/anurag9000/forestrun/engine/ApplicationPersistenceFacadeTest.kt"
WORKFLOW = ROOT / ".github/workflows/application-persistence-facade-migration.yml"
SELF = Path(__file__)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    return text.replace(old, new, 1)


def migrate_entity_manager() -> None:
    path = ENGINE / "EntityManager.kt"
    source = path.read_text(encoding="utf-8")
    source = replace_once(
        source,
        '''    private val spriteManager: SpriteManager,
    val biomeManager: BiomeManager = BiomeManager()
) {
''',
        '''    private val spriteManager: SpriteManager,
    val biomeManager: BiomeManager = BiomeManager(),
    private val encounterPersistence: ApplicationEncounterPersistence =
        AndroidApplicationEncounterPersistence(context)
) {
''',
        "EntityManager persistence injection",
    )
    source = replace_once(
        source,
        "PersistentMemoryManager.recordPass(context, type)",
        "encounterPersistence.recordPass(type)",
        "EntityManager pass memory",
    )
    source = replace_once(
        source,
        "PersistentMemoryManager.recordEncounter(context, type)",
        "encounterPersistence.recordEncounter(type)",
        "EntityManager encounter memory",
    )
    path.write_text(source, encoding="utf-8")


def migrate_feedback_panel() -> None:
    path = UI / "FeedbackSettingsPanel.kt"
    source = path.read_text(encoding="utf-8")
    source = replace_once(
        source,
        "import com.anurag9000.forestrun.engine.AssetPaths\n",
        "import com.anurag9000.forestrun.engine.ApplicationPersistenceFacade\n"
        "import com.anurag9000.forestrun.engine.AssetPaths\n",
        "feedback facade import",
    )
    source = replace_once(
        source,
        '''internal class FeedbackSettingsPanel(
    private val context: Context,
    screenWidth: Int,
    screenHeight: Int
) {
''',
        '''internal class FeedbackSettingsPanel(
    private val context: Context,
    screenWidth: Int,
    screenHeight: Int,
    private val persistenceFacade: ApplicationPersistenceFacade =
        ApplicationPersistenceFacade.android(context)
) {
''',
        "feedback facade constructor",
    )
    source = replace_once(
        source,
        '''            FeedbackToggle.REDUCED_MOTION -> FeedbackSettings.setReducedMotion(
                context,
                !FeedbackSettings.reducedMotion
            )
            FeedbackToggle.AUDIO -> FeedbackSettings.setAudioEnabled(
                context,
                !FeedbackSettings.audioEnabled
            )
            FeedbackToggle.HAPTICS -> FeedbackSettings.setHapticsEnabled(
                context,
                !FeedbackSettings.hapticsEnabled
            )
''',
        '''            FeedbackToggle.REDUCED_MOTION -> persistenceFacade.saveFeedbackPreferences(
                FeedbackSettings.snapshot().copy(
                    reducedMotion = !FeedbackSettings.reducedMotion
                )
            )
            FeedbackToggle.AUDIO -> persistenceFacade.saveFeedbackPreferences(
                FeedbackSettings.snapshot().copy(
                    audioEnabled = !FeedbackSettings.audioEnabled
                )
            )
            FeedbackToggle.HAPTICS -> persistenceFacade.saveFeedbackPreferences(
                FeedbackSettings.snapshot().copy(
                    hapticsEnabled = !FeedbackSettings.hapticsEnabled
                )
            )
''',
        "feedback mutations",
    )
    path.write_text(source, encoding="utf-8")


def migrate_menu() -> None:
    path = UI / "MainMenuScreen.kt"
    source = path.read_text(encoding="utf-8")
    source = replace_once(
        source,
        "import com.anurag9000.forestrun.engine.AssetPaths\n",
        "import com.anurag9000.forestrun.engine.ApplicationPersistenceFacade\n"
        "import com.anurag9000.forestrun.engine.AssetPaths\n",
        "menu facade import",
    )
    source = replace_once(
        source,
        '''    private val spriteManager: SpriteManager,
    private val screenW: Int,
    private val screenH: Int
) {
''',
        '''    private val spriteManager: SpriteManager,
    private val screenW: Int,
    private val screenH: Int,
    private val persistenceFacade: ApplicationPersistenceFacade =
        ApplicationPersistenceFacade.android(context)
) {
''',
        "menu facade constructor",
    )
    source = replace_once(
        source,
        "private val feedbackSettingsPanel = FeedbackSettingsPanel(context, screenW, screenH)",
        "private val feedbackSettingsPanel = FeedbackSettingsPanel(\n"
        "        context, screenW, screenH, persistenceFacade\n"
        "    )",
        "menu feedback injection",
    )
    path.write_text(source, encoding="utf-8")


def migrate_garden() -> None:
    path = UI / "GardenScreen.kt"
    source = path.read_text(encoding="utf-8")
    source = replace_once(
        source,
        "import com.anurag9000.forestrun.engine.AssetPaths\n",
        "import com.anurag9000.forestrun.engine.ApplicationPersistenceFacade\n"
        "import com.anurag9000.forestrun.engine.AssetPaths\n",
        "garden facade import",
    )
    source = replace_once(
        source,
        '''    private val spriteManager: SpriteManager,
    private val screenW: Int,
    private val screenH: Int
) {
''',
        '''    private val spriteManager: SpriteManager,
    private val screenW: Int,
    private val screenH: Int,
    private val persistenceFacade: ApplicationPersistenceFacade =
        ApplicationPersistenceFacade.android(context)
) {
''',
        "garden facade constructor",
    )
    source = replace_once(
        source,
        "GardenPurchaseManager.purchaseNext(\n                        context = context,\n                        requestedIndex = i\n                    )",
        "persistenceFacade.purchaseNextGardenPlant(i)",
        "garden purchase mutation",
    )
    source = replace_once(
        source,
        "CostumeManager.equip(context, style)",
        "persistenceFacade.equipCostume(style)",
        "garden wardrobe mutation",
    )
    path.write_text(source, encoding="utf-8")


def migrate_game_view() -> None:
    path = ENGINE / "GameView.kt"
    source = path.read_text(encoding="utf-8")
    source = replace_once(
        source,
        '''    private val ghostRecorder = GhostRecorder()
    private val runOutcomePersistence =
        RunOutcomePersistenceCoordinator(AndroidRunOutcomePersistenceSink(context))
    private val ghostPlayer = GhostPlayer()
''',
        '''    private val ghostRecorder = GhostRecorder()
    private val applicationPersistence = ApplicationPersistenceFacade.android(context)
    private val runOutcomePersistence: ApplicationRunOutcomePort = applicationPersistence
    private val ghostPlayer = GhostPlayer()
''',
        "GameView facade field",
    )
    source = replace_once(
        source,
        "relationshipRecorder = AndroidTerminalHitRelationshipRecorder(context),",
        "relationshipRecorder = applicationPersistence,",
        "terminal relationship facade",
    )
    source = replace_once(
        source,
        "relationshipRecorder = AndroidNonTerminalCollisionRelationshipRecorder(context),",
        "relationshipRecorder = applicationPersistence,",
        "nonterminal relationship facade",
    )
    source = replace_once(
        source,
        "entityManager = EntityManager(context, screenWidth.toFloat(), screenHeight.toFloat(), spriteManager)",
        "entityManager = EntityManager(\n"
        "                context,\n"
        "                screenWidth.toFloat(),\n"
        "                screenHeight.toFloat(),\n"
        "                spriteManager,\n"
        "                encounterPersistence = applicationPersistence\n"
        "            )",
        "EntityManager shared facade",
    )
    source = replace_once(
        source,
        "mainMenuScreen = MainMenuScreen(context, spriteManager, screenWidth, screenHeight)",
        "mainMenuScreen = MainMenuScreen(\n"
        "                context, spriteManager, screenWidth, screenHeight, applicationPersistence\n"
        "            )",
        "menu shared facade",
    )
    source = replace_once(
        source,
        "gardenScreen = GardenScreen(context, spriteManager, screenWidth, screenHeight)",
        "gardenScreen = GardenScreen(\n"
        "                context, spriteManager, screenWidth, screenHeight, applicationPersistence\n"
        "            )",
        "garden shared facade",
    )
    source = replace_once(
        source,
        '''        FeedbackSettings.setReducedMotion(context, !FeedbackSettings.reducedMotion)
        notifyAccessibilityTreeChanged()
''',
        '''        applicationPersistence.saveFeedbackPreferences(
            FeedbackSettings.snapshot().copy(
                reducedMotion = !FeedbackSettings.reducedMotion
            )
        )
        notifyAccessibilityTreeChanged()
''',
        "accessibility reduced motion facade",
    )
    source = replace_once(
        source,
        '''        FeedbackSettings.setAudioEnabled(context, !FeedbackSettings.audioEnabled)
        notifyAccessibilityTreeChanged()
''',
        '''        applicationPersistence.saveFeedbackPreferences(
            FeedbackSettings.snapshot().copy(
                audioEnabled = !FeedbackSettings.audioEnabled
            )
        )
        notifyAccessibilityTreeChanged()
''',
        "accessibility audio facade",
    )
    source = replace_once(
        source,
        '''        FeedbackSettings.setHapticsEnabled(context, !FeedbackSettings.hapticsEnabled)
        notifyAccessibilityTreeChanged()
''',
        '''        applicationPersistence.saveFeedbackPreferences(
            FeedbackSettings.snapshot().copy(
                hapticsEnabled = !FeedbackSettings.hapticsEnabled
            )
        )
        notifyAccessibilityTreeChanged()
''',
        "accessibility haptics facade",
    )
    source = replace_once(
        source,
        "val result = GardenPurchaseManager.purchaseNext(context, index)",
        "val result = applicationPersistence.purchaseNextGardenPlant(index)",
        "accessibility garden facade",
    )
    source = replace_once(
        source,
        "val equipped = CostumeManager.equip(context, style)",
        "val equipped = applicationPersistence.equipCostume(style)",
        "accessibility wardrobe facade",
    )
    path.write_text(source, encoding="utf-8")


def migrate_tests() -> None:
    source = TEST.read_text(encoding="utf-8")
    marker = "            recoveryMaintenance = ThrowingRecoveryMaintenance(calls)"
    count = source.count(marker)
    if count != 3:
        raise SystemExit(f"facade tests: expected three recovery anchors, found {count}")
    source = source.replace(
        marker,
        "            encounterPersistence = RecordingEncounterPersistence(calls),\n" + marker,
    )
    class_anchor = '''    private class ThrowingRecoveryMaintenance(
        private val calls: MutableList<String>
    ) : ApplicationRecoveryMaintenance {
'''
    recorder = '''    private class RecordingEncounterPersistence(
        private val calls: MutableList<String>
    ) : ApplicationEncounterPersistence {
        override fun recordEncounter(type: com.anurag9000.forestrun.entities.EntityType) {
            calls += "encounter:${type.name}"
        }

        override fun recordPass(type: com.anurag9000.forestrun.entities.EntityType) {
            calls += "pass:${type.name}"
        }

        override fun recordHit(type: com.anurag9000.forestrun.entities.EntityType) {
            calls += "hit:${type.name}"
        }
    }

'''
    source = replace_once(source, class_anchor, recorder + class_anchor, "facade encounter recorder")
    TEST.write_text(source, encoding="utf-8")


def verify() -> None:
    game = (ENGINE / "GameView.kt").read_text(encoding="utf-8")
    entity = (ENGINE / "EntityManager.kt").read_text(encoding="utf-8")
    garden = (UI / "GardenScreen.kt").read_text(encoding="utf-8")
    feedback = (UI / "FeedbackSettingsPanel.kt").read_text(encoding="utf-8")
    required = (
        (game, "ApplicationPersistenceFacade.android(context)"),
        (game, "relationshipRecorder = applicationPersistence"),
        (game, "encounterPersistence = applicationPersistence"),
        (game, "applicationPersistence.purchaseNextGardenPlant(index)"),
        (game, "applicationPersistence.equipCostume(style)"),
        (entity, "encounterPersistence.recordPass(type)"),
        (entity, "encounterPersistence.recordEncounter(type)"),
        (garden, "persistenceFacade.purchaseNextGardenPlant(i)"),
        (garden, "persistenceFacade.equipCostume(style)"),
        (feedback, "persistenceFacade.saveFeedbackPreferences("),
    )
    for text, token in required:
        if token not in text:
            raise SystemExit(f"missing migrated token: {token}")
    forbidden = (
        (game, "AndroidTerminalHitRelationshipRecorder(context)"),
        (game, "AndroidNonTerminalCollisionRelationshipRecorder(context)"),
        (game, "GardenPurchaseManager.purchaseNext(context, index)"),
        (game, "CostumeManager.equip(context, style)"),
        (entity, "PersistentMemoryManager.recordPass(context, type)"),
        (entity, "PersistentMemoryManager.recordEncounter(context, type)"),
    )
    for text, token in forbidden:
        if token in text:
            raise SystemExit(f"direct live persistence path survived: {token}")


def cleanup() -> None:
    for path in (WORKFLOW, SELF):
        if path.exists():
            path.unlink()


def main() -> None:
    migrate_entity_manager()
    migrate_feedback_panel()
    migrate_menu()
    migrate_garden()
    migrate_game_view()
    migrate_tests()
    verify()
    cleanup()


if __name__ == "__main__":
    main()
