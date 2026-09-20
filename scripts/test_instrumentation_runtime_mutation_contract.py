from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
TEST = ROOT / "app/src/androidTest/java/com/anurag9000/forestrun/MainActivityInstrumentedTest.kt"
PLAYER = ROOT / "app/src/main/java/com/anurag9000/forestrun/entities/Player.kt"
BIOME_MANAGER = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/BiomeManager.kt"
MAIN_MENU = ROOT / "app/src/main/java/com/anurag9000/forestrun/ui/MainMenuScreen.kt"
GHOST_PLAYER = ROOT / "app/src/main/java/com/anurag9000/forestrun/systems/GhostPlayer.kt"
GHOST_RECORDER = ROOT / "app/src/main/java/com/anurag9000/forestrun/systems/GhostRecorder.kt"
GAME_VIEW = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt"
ENCOUNTER_DIRECTOR = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/EncounterDirector.kt"

class InstrumentationRuntimeMutationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = TEST.read_text(encoding="utf-8")
        helper_start = cls.source.index("    private fun mutateStopped(")
        helper_end = cls.source.index("    private fun tapLogical(", helper_start)
        cls.helper = cls.source[helper_start:helper_end]
        cls.player = PLAYER.read_text(encoding="utf-8")
        cls.biome_manager = BIOME_MANAGER.read_text(encoding="utf-8")
        cls.main_menu = MAIN_MENU.read_text(encoding="utf-8")
        cls.ghost_player = GHOST_PLAYER.read_text(encoding="utf-8")
        cls.ghost_recorder = GHOST_RECORDER.read_text(encoding="utf-8")
        cls.game_view = GAME_VIEW.read_text(encoding="utf-8")
        cls.encounter_director = ENCOUNTER_DIRECTOR.read_text(encoding="utf-8")

    def test_cross_thread_instrumentation_observations_are_safely_published(self) -> None:
        for source, declaration in (
            (self.player, "var isInvincible: Boolean = false"),
            (self.biome_manager, "var currentBiome: Biome = Biome.MEADOW"),
            (self.main_menu, "var phase: Phase = Phase.IDLE"),
            (self.ghost_player, "private var isActive: Boolean = false"),
            (self.ghost_recorder, "internal var recordedFrameCount: Int = 0"),
            (self.game_view, "private var gameThread: GameThread = GameThread(holder, this)"),
            (self.encounter_director, "var activeScenario: EncounterScenario? = null"),
        ):
            index = source.index(declaration)
            prefix = source[max(0, index - 64):index]
            self.assertIn("@Volatile", prefix, declaration)

        self.assertIn("recorder.recordedFrameCount >= 5", self.source)
        self.assertNotIn("recorder.frames.size >= 5", self.source)
        self.assertIn("recordedFrameCount = activeFrames.size", self.ghost_recorder)
        self.assertIn("recordedFrameCount = 0", self.ghost_recorder)

    def test_quiescence_helper_requires_successful_stop_and_always_resumes(self) -> None:
        self.assertIn("gameView.pause()", self.helper)
        self.assertIn("assertTrue(", self.helper)
        self.assertIn("try {", self.helper)
        self.assertIn("finally {", self.helper)
        self.assertIn("gameView.resume()", self.helper)

    def test_live_owner_mutations_are_not_direct_activity_callbacks(self) -> None:
        self.assertNotIn(
            'scenario.onActivity {\n                val gameState = getPrivateField(gameView, "gameState")',
            self.source,
        )
        self.assertNotIn(
            'scenario.onActivity {\n                val entityManager = getPrivateField(gameView, "entityManager")',
            self.source,
        )

    def test_entity_roster_proof_updates_once_while_quiesced_before_live_resume(self) -> None:
        start = self.source.index("    fun allEntityTypesSpawnAndUpdateOnDevice()")
        end = self.source.index("    @Test\n    fun bestRunPersistsGhostAndReloadsOnNextLaunch()", start)
        region = self.source[start:end]
        block = region.index("mutateStopped(gameView) {")
        update = region.index("entityManager.update(", block)
        exact_types = region.index("entityManager.activeEntities.mapNotNull(entityManager::entityTypeOf).toSet()", update)
        type_assert = region.index("assertEquals(expectedTypes, actualTypes)", exact_types)
        exact_count = region.index("assertEquals(expectedTypes.size, entityManager.debugActiveEntityCount)", type_assert)
        live_loop = region.index('waitForCondition("live loop continues with full entity roster"', exact_count)
        self.assertLess(block, update)
        self.assertLess(update, exact_types)
        self.assertLess(exact_types, type_assert)
        self.assertLess(type_assert, exact_count)
        self.assertLess(exact_count, live_loop)
        self.assertIn("runMode = RunMode.DEBUG_SCENARIO", region)
        self.assertIn("val stagingX = gameView.width + 100f", region)
        self.assertIn("EntityType.values().forEach { type ->", region)
        self.assertIn("entityManager.debugSpawnAt(type, stagingX)", region)
        self.assertNotIn("forEachIndexed", region)
        self.assertNotIn("all entity types remain active for at least one live update", region)

    def test_each_high_risk_mutation_is_inside_a_quiesced_block(self) -> None:
        markers = (
            "gameState.collectSeed()",
            "entityManager.debugSpawnAt(",
            "entityManager.reset()",
            'setPrivateField(gameState, "distanceMetres"',
        )
        for marker in markers:
            search_from = 0
            while True:
                index = self.source.find(marker, search_from)
                if index < 0:
                    break
                block_start = self.source.rfind("mutateStopped(gameView) {", 0, index)
                next_close = self.source.find("\n            }", block_start)
                self.assertGreaterEqual(block_start, 0, marker)
                self.assertGreater(next_close, index, marker)
                search_from = index + len(marker)

if __name__ == "__main__":
    unittest.main()
