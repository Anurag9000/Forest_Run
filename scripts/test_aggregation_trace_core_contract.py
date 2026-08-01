from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
AGGREGATOR = ROOT / "scripts/aggregate_device_acceptance.py"


class AggregationTraceCoreContractTest(unittest.TestCase):
    def test_python_core_requires_and_summarizes_exact_traces(self) -> None:
        source = AGGREGATOR.read_text(encoding="utf-8")

        self.assertIn(
            "import validate_manifest_scenario_traces as manifest_traces",
            source,
        )
        self.assertIn("require_at_least_one=True", source)
        self.assertIn('"trace_count": trace_validation["trace_count"]', source)
        self.assertIn('"trace_contracts": _trace_contracts(trace_validation)', source)
        self.assertIn("scenario_definition_sha256", source)
        self.assertIn("trace_contract_sha256", source)

    def test_baseline_comparison_requires_identical_trace_contract_sets(self) -> None:
        source = AGGREGATOR.read_text(encoding="utf-8")

        self.assertIn("candidate_contracts", source)
        self.assertIn("baseline_contracts", source)
        self.assertIn("trace-contract sets differ", source)
        self.assertIn('"trace_contracts": candidate["trace_contracts"]', source)

    def test_manifest_is_stable_across_acceptance_and_trace_validation(self) -> None:
        source = AGGREGATOR.read_text(encoding="utf-8")
        mutation_tests = (ROOT / "scripts/test_aggregate_manifest_snapshot.py").read_text(
            encoding="utf-8"
        )

        self.assertIn("def _stable_manifest_read", source)
        self.assertIn("before.st_mtime_ns", source)
        self.assertIn("before.st_ino", source)
        self.assertIn("confirmed_raw != raw", source)
        self.assertIn("changed while trace contracts were validated", source)
        self.assertIn("def _aggregate_with_sources", source)
        self.assertIn("_manifest_protected_paths(candidate_path, candidate_data)", source)
        self.assertIn("payload, protected_paths = _aggregate_with_sources", source)
        self.assertIn("mutation_between_acceptance_and_trace_validation", mutation_tests)

    def test_reusable_traced_test_fixture_and_adversarial_tests_exist(self) -> None:
        support = (ROOT / "scripts/acceptance_test_support.py").read_text(
            encoding="utf-8"
        )
        tests = (ROOT / "scripts/test_aggregate_device_acceptance.py").read_text(
            encoding="utf-8"
        )

        self.assertIn("materialize_traced_bundle", support)
        self.assertIn('"schema_version": 2', support)
        self.assertIn("scenario_definition_sha256", support)
        self.assertIn("trace_contract_sha256", support)
        self.assertIn("trace_free_manifest_is_rejected", tests)
        self.assertIn("trace_contract_sets_must_match", tests)
        self.assertIn("digest_matched_but_unauthored_trace_is_rejected", tests)


if __name__ == "__main__":
    unittest.main()
