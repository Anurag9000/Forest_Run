from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class ContinuationSourceContractTest(unittest.TestCase):
    def test_deterministic_trace_runtime_and_evidence_contracts_exist(self) -> None:
        script = (
            ROOT
            / "app/src/main/java/com/anurag9000/forestrun/engine/DebugScenarioScript.kt"
        ).read_text(encoding="utf-8")
        trace = (
            ROOT
            / "app/src/main/java/com/anurag9000/forestrun/engine/DeterministicScenarioTrace.kt"
        )
        evidence = (
            ROOT
            / "app/src/main/java/com/anurag9000/forestrun/engine/DeterministicScenarioTraceEvidence.kt"
        ).read_text(encoding="utf-8")
        fingerprint = (
            ROOT
            / "app/src/main/java/com/anurag9000/forestrun/engine/EncounterScenarioFingerprint.kt"
        ).read_text(encoding="utf-8")
        replay = (
            ROOT
            / "app/src/main/java/com/anurag9000/forestrun/engine/DeterministicScenarioReplayContract.kt"
        ).read_text(encoding="utf-8")
        validator = (ROOT / "scripts/validate_scenario_trace.py").read_text(
            encoding="utf-8"
        )
        source_parser = (ROOT / "scripts/scenario_source_contract.py").read_text(
            encoding="utf-8"
        )

        self.assertTrue(trace.is_file())
        self.assertIn("private val defaultTraceRecorder", script)
        self.assertIn("recorder: DeterministicScenarioTraceRecorder = defaultTraceRecorder", script)
        self.assertIn("fun traceSnapshot()", script)
        self.assertIn("private const val SCHEMA_VERSION = 2", evidence)
        self.assertIn("scenario_definition_sha256", evidence)
        self.assertIn("trace_contract_sha256", evidence)
        self.assertIn("scheduled_at_micros", evidence)
        self.assertIn("dispatched_at_micros", evidence)
        self.assertIn("lateness_micros", evidence)
        self.assertNotIn("scheduled_at_seconds", evidence)
        self.assertIn("DeterministicScenarioReplayContract.matches(snapshot)", evidence)
        self.assertIn("scenario.forcedBiome?.name.orEmpty()", fingerprint)
        self.assertIn("scenario.allowGhostPlayback", fingerprint)
        self.assertIn("scenario.startsWithBloom", fingerprint)
        self.assertIn("traceContractSha256", fingerprint)
        self.assertIn("DebugScenarioScript.stepsFor(scenario)", fingerprint)
        self.assertIn("expected.isEmpty()", replay)
        self.assertIn("event.scheduledAtSeconds == step.atSeconds", replay)
        self.assertIn("SCHEMA_VERSION = 2", validator)
        self.assertIn("scenario_definition_sha256", validator)
        self.assertIn("trace_contract_sha256", validator)
        self.assertIn("no authored deterministic input script", validator)
        self.assertIn("scheduled_at_micros does not match", validator)
        self.assertIn("action does not match", validator)
        self.assertIn("parse_scenario_definition", source_parser)
        self.assertIn("parse_input_steps", source_parser)
        self.assertIn("struct.pack", source_parser)
        self.assertIn("trace_contract_canonical_bytes", source_parser)
        self.assertIn("LONG_MIN = -(1 << 63)", source_parser)
        self.assertIn("LONG_MAX = (1 << 63) - 1", source_parser)
        self.assertIn("def _kotlin_round_to_long", source_parser)
        self.assertIn("ties toward positive infinity", source_parser)
        self.assertTrue(
            (
                ROOT
                / "app/src/test/java/com/anurag9000/forestrun/engine/DeterministicScenarioTraceTest.kt"
            ).is_file()
        )
        self.assertTrue(
            (
                ROOT
                / "app/src/test/java/com/anurag9000/forestrun/engine/DeterministicScenarioTraceEvidenceTest.kt"
            ).is_file()
        )
        self.assertTrue(
            (
                ROOT
                / "app/src/test/java/com/anurag9000/forestrun/engine/DeterministicScenarioReplayContractTest.kt"
            ).is_file()
        )
        self.assertTrue(
            (
                ROOT
                / "app/src/test/java/com/anurag9000/forestrun/engine/EncounterScenarioFingerprintTest.kt"
            ).is_file()
        )
        self.assertTrue(
            (
                ROOT
                / "app/src/main/java/com/anurag9000/forestrun/engine/DeterministicScenarioTraceEvidenceStore.kt"
            ).is_file()
        )
        self.assertTrue((ROOT / "scripts/test_scenario_source_contract.py").is_file())
        self.assertTrue((ROOT / "scripts/test_scenario_trace_zero_action.py").is_file())
        self.assertTrue((ROOT / "scripts/validate_manifest_scenario_traces.py").is_file())
        self.assertTrue((ROOT / "docs/DETERMINISTIC_SCENARIO_EVIDENCE.md").is_file())

    def test_physical_acceptance_aggregation_surface_is_complete(self) -> None:
        expected = (
            "scripts/aggregate_device_acceptance.py",
            "scripts/test_aggregate_device_acceptance.py",
            "scripts/test_aggregate_device_acceptance_aliases.py",
            "scripts/aggregate_device_acceptance_bundle.sh",
            "scripts/test_aggregate_device_acceptance_bundle_contract.py",
            "scripts/compile_device_acceptance_bundle.sh",
            "scripts/test_compile_device_acceptance_bundle_contract.py",
            "docs/DEVICE_ACCEPTANCE_AGGREGATION.md",
        )
        for relative in expected:
            with self.subTest(path=relative):
                self.assertTrue((ROOT / relative).is_file())

        wrapper = (ROOT / "scripts/aggregate_device_acceptance_bundle.sh").read_text(
            encoding="utf-8"
        )
        compiler_wrapper = (ROOT / "scripts/compile_device_acceptance_bundle.sh").read_text(
            encoding="utf-8"
        )
        aggregator = (ROOT / "scripts/aggregate_device_acceptance.py").read_text(
            encoding="utf-8"
        )
        self.assertLess(
            wrapper.index("verify_strict_json_evidence.py"),
            wrapper.index("validate_manifest_scenario_traces.py"),
        )
        self.assertLess(
            wrapper.index("validate_manifest_scenario_traces.py"),
            wrapper.index("aggregate_device_acceptance.py"),
        )
        self.assertGreaterEqual(wrapper.count("--require-at-least-one"), 2)
        self.assertIn("--require-at-least-one", compiler_wrapper)
        self.assertIn("must not overwrite the candidate", wrapper)
        self.assertIn("must not overwrite the baseline", wrapper)
        self.assertIn("os.path.samefile", aggregator)
        self.assertIn("_manifest_protected_paths", aggregator)
        self.assertIn("aggregate output must not overwrite protected source", aggregator)
        self.assertGreaterEqual(
            aggregator.count("_assert_output_is_separate(destination, protected_paths)"),
            2,
        )

    def test_spawn_fairness_envelope_uses_production_pacing_contracts(self) -> None:
        envelope_path = (
            ROOT
            / "app/src/main/java/com/anurag9000/forestrun/engine/SpawnFairnessEnvelope.kt"
        )
        test_path = (
            ROOT
            / "app/src/test/java/com/anurag9000/forestrun/engine/SpawnFairnessEnvelopeTest.kt"
        )
        envelope = envelope_path.read_text(encoding="utf-8")
        tests = test_path.read_text(encoding="utf-8")

        self.assertIn("DifficultyScaler.getSpawnGapPx", envelope)
        self.assertIn("SpawnPacing.requiredGapPx", envelope)
        self.assertIn("GameConstants.BASE_SCROLL_SPEED", envelope)
        self.assertIn("GameConstants.MAX_SCROLL_SPEED", envelope)
        self.assertIn("SPAWN_GAP_MIN_PX / GameConstants.MAX_SCROLL_SPEED", envelope)
        self.assertIn("0.39f", tests)
        self.assertIn("distance <= 20_000f", tests)
        self.assertIn("runTimes", tests)
        self.assertIn("malformed inputs fail closed", tests)

    def test_strict_json_numeric_and_depth_boundaries_remain_present(self) -> None:
        parser = (ROOT / "scripts/strict_json.py").read_text(encoding="utf-8")
        self.assertIn("math.isfinite(value)", parser)
        self.assertIn("_preflight_nesting", parser)
        self.assertIn("DEFAULT_MAX_INTEGER_DIGITS = 256", parser)
        self.assertIn("parse_int=lambda literal", parser)
        self.assertIn("maximum_integer_digits=maximum_integer_digits", parser)
        self.assertIn("except (ValueError, RecursionError)", parser)
        self.assertTrue((ROOT / "scripts/test_strict_json_overflow.py").is_file())

    def test_ghost_and_leitmotif_admission_fixes_remain_present(self) -> None:
        ghost = (
            ROOT
            / "app/src/main/java/com/anurag9000/forestrun/systems/GhostPlayer.kt"
        ).read_text(encoding="utf-8")
        leitmotif = (
            ROOT
            / "app/src/main/java/com/anurag9000/forestrun/engine/LeitmotifManager.kt"
        ).read_text(encoding="utf-8")

        self.assertIn(
            "if (!isActive || !deltaTime.isFinite() || deltaTime <= 0f) return",
            ghost,
        )
        self.assertNotIn("lastParameterUpdateNs", leitmotif)
        self.assertIn("tempoEvaluationThrottle.tryAcquire(nowNs)", leitmotif)
        self.assertIn("bloomEvaluationThrottle.tryAcquire(nowNs, force = conversionChanged)", leitmotif)

    def test_progression_arithmetic_replacements_and_tests_exist(self) -> None:
        arithmetic = (
            ROOT
            / "app/src/main/java/com/anurag9000/forestrun/engine/SafeProgressionArithmetic.kt"
        ).read_text(encoding="utf-8")
        warmth = (
            ROOT
            / "app/src/main/java/com/anurag9000/forestrun/engine/FamiliarityWarmthScoring.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("fun saturatingIncrement", arithmetic)
        self.assertIn("fun elapsedAtLeast", arithmetic)
        self.assertIn("fun elapsedOrZero", arithmetic)
        self.assertIn("bonus(safePasses >= 3)", warmth)
        self.assertIn("bonus(safePasses >= 5)", warmth)
        self.assertIn("bonus(safeSpares >= 2)", warmth)
        self.assertIn("bonus(safeKindness >= 3)", warmth)
        self.assertIn("bonus(safeEncounters >= 5)", warmth)


if __name__ == "__main__":
    unittest.main()
