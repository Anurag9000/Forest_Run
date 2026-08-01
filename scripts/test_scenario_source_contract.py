from __future__ import annotations

import shutil
import tempfile
import unittest
from pathlib import Path

import scenario_source_contract as contract


ROOT = Path(__file__).resolve().parents[1]
CACTUS_SCENARIO_SHA = "3246dd15f7e694d387d06430537bf1805e8d57a53a9bcd1bdc5dd13e929b524c"
CACTUS_TRACE_SHA = "edb682a29079ceaebf9c3e56c2f24362ce3335a0c1432e3803305a5dc2b58430"


class ScenarioSourceContractTest(unittest.TestCase):
    def test_cactus_contract_matches_cross_language_fixed_hashes(self) -> None:
        result = contract.load_trace_contract(ROOT, "CACTUS_READ")

        self.assertEqual("Cactus Read", result.scenario.title)
        self.assertEqual(
            "Baseline silhouette and fair jump timing",
            result.scenario.summary,
        )
        self.assertEqual("DUSK_CANYON", result.scenario.forced_biome)
        self.assertFalse(result.scenario.starts_with_bloom)
        self.assertFalse(result.scenario.allow_ghost_playback)
        self.assertEqual(2, len(result.scenario.steps))
        self.assertEqual(200_000, result.scenario.steps[0].at_micros)
        self.assertEqual(420_000_000, result.scenario.steps[0].x_offset_micro_pixels)
        self.assertEqual(4, len(result.input_steps))
        self.assertEqual(3_180_000, result.input_steps[0].at_micros)
        self.assertEqual("HOLD_JUMP_START", result.input_steps[0].action)
        self.assertEqual(CACTUS_SCENARIO_SHA, result.scenario_definition_sha256)
        self.assertEqual(CACTUS_TRACE_SHA, result.trace_contract_sha256)

    def test_optional_behavior_fields_and_variants_are_reconstructed(self) -> None:
        bloom = contract.load_trace_contract(ROOT, "BLOOM_SHOWCASE")
        ghost = contract.load_trace_contract(ROOT, "GHOST_READABILITY")
        dog = contract.load_trace_contract(ROOT, "DOG_HAZARD")

        self.assertTrue(bloom.scenario.starts_with_bloom)
        self.assertTrue(ghost.scenario.allow_ghost_playback)
        self.assertEqual((), ghost.input_steps)
        self.assertEqual("DOG_HAZARD", dog.scenario.steps[0].variant)

    def test_signed_exponent_offsets_match_kotlin_float_and_rounding_semantics(self) -> None:
        source = self.encounter_source(ROOT).read_text(encoding="utf-8")
        modified = source.replace(
            "EncounterStep(0.20f, EntityType.CACTUS, 420f)",
            "EncounterStep(0.20f, EntityType.CACTUS, -1.25e1f)",
            1,
        )

        definition = contract.parse_scenario_definition(modified, "CACTUS_READ")

        self.assertEqual(-12_500_000, definition.steps[0].x_offset_micro_pixels)
        self.assertEqual(2, contract._kotlin_round_to_long(1.5, "positive tie"))
        self.assertEqual(-1, contract._kotlin_round_to_long(-1.5, "negative tie"))
        self.assertEqual(contract.LONG_MAX, contract._kotlin_round_to_long(1e40, "large"))
        self.assertEqual(contract.LONG_MIN, contract._kotlin_round_to_long(-1e40, "small"))

    def test_negative_time_and_nonrepresentable_float_literals_fail_closed(self) -> None:
        source = self.encounter_source(ROOT).read_text(encoding="utf-8")
        negative_time = source.replace(
            "EncounterStep(0.20f, EntityType.CACTUS, 420f)",
            "EncounterStep(-0.20f, EntityType.CACTUS, 420f)",
            1,
        )
        with self.assertRaisesRegex(
            contract.ScenarioSourceContractError,
            "time must be non-negative",
        ):
            contract.parse_scenario_definition(negative_time, "CACTUS_READ")

        overflow = source.replace(
            "EncounterStep(0.20f, EntityType.CACTUS, 420f)",
            "EncounterStep(0.20f, EntityType.CACTUS, 1e100f)",
            1,
        )
        with self.assertRaisesRegex(
            contract.ScenarioSourceContractError,
            "representable Kotlin Float",
        ):
            contract.parse_scenario_definition(overflow, "CACTUS_READ")

    def test_scenario_and_input_changes_affect_only_the_expected_hashes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.copy_sources(root)
            original = contract.load_trace_contract(root, "CACTUS_READ")

            encounter = self.encounter_source(root)
            text = encounter.read_text(encoding="utf-8")
            encounter.write_text(
                text.replace(
                    "Baseline silhouette and fair jump timing",
                    "Changed silhouette and jump timing",
                    1,
                ),
                encoding="utf-8",
            )
            changed_scenario = contract.load_trace_contract(root, "CACTUS_READ")
            self.assertNotEqual(
                original.scenario_definition_sha256,
                changed_scenario.scenario_definition_sha256,
            )
            self.assertNotEqual(
                original.trace_contract_sha256,
                changed_scenario.trace_contract_sha256,
            )

            self.copy_sources(root)
            script = self.script_source(root)
            script_text = script.read_text(encoding="utf-8")
            script.write_text(
                script_text.replace(
                    "DebugScenarioStep(3.18f, DebugScenarioAction.HOLD_JUMP_START)",
                    "DebugScenarioStep(3.19f, DebugScenarioAction.HOLD_JUMP_START)",
                    1,
                ),
                encoding="utf-8",
            )
            changed_input = contract.load_trace_contract(root, "CACTUS_READ")
            self.assertEqual(
                original.scenario_definition_sha256,
                changed_input.scenario_definition_sha256,
            )
            self.assertNotEqual(
                original.trace_contract_sha256,
                changed_input.trace_contract_sha256,
            )

    def test_unknown_or_unparseable_scenarios_fail_closed(self) -> None:
        with self.assertRaisesRegex(
            contract.ScenarioSourceContractError,
            "exactly once",
        ):
            contract.load_trace_contract(ROOT, "NOT_A_SCENARIO")

        source = self.encounter_source(ROOT).read_text(encoding="utf-8")
        broken = source.replace("steps = listOf(", "steps = sequenceOf(", 1)
        with self.assertRaisesRegex(
            contract.ScenarioSourceContractError,
            "steps=listOf",
        ):
            contract.parse_scenario_definition(broken, "OPENING_READABILITY")

    @staticmethod
    def encounter_source(root: Path) -> Path:
        return (
            root
            / "app/src/main/java/com/anurag9000/forestrun/engine/EncounterDirector.kt"
        )

    @staticmethod
    def script_source(root: Path) -> Path:
        return (
            root
            / "app/src/main/java/com/anurag9000/forestrun/engine/DebugScenarioScript.kt"
        )

    @classmethod
    def copy_sources(cls, root: Path) -> None:
        encounter = cls.encounter_source(root)
        script = cls.script_source(root)
        encounter.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(cls.encounter_source(ROOT), encounter)
        shutil.copyfile(cls.script_source(ROOT), script)


if __name__ == "__main__":
    unittest.main()
