from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

import aggregate_device_acceptance as aggregate
import publish_device_acceptance_aggregate as publisher
from acceptance_test_support import materialize_traced_bundle
from test_validate_device_acceptance import valid_bundle


class PublishAggregateSourceBindingTest(unittest.TestCase):
    @staticmethod
    def materialize(root: Path, *, commit: str | None = None) -> Path:
        bundle = valid_bundle()
        if commit is not None:
            bundle["candidate"]["commit_sha"] = commit
            for session in bundle["sessions"]:
                session["build"]["commit_sha"] = commit
        materialize_traced_bundle(root, bundle)
        manifest = root / "manifest.json"
        manifest.write_text(
            json.dumps(bundle, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        return manifest

    @staticmethod
    def stage(root: Path, candidate: Path, baseline: Path | None = None) -> Path:
        staged = root / ".aggregate.source-binding.tmp"
        payload = aggregate.aggregate(candidate, baseline_path=baseline)
        staged.write_text(
            json.dumps(payload, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        return staged

    @staticmethod
    def rewrite(staged: Path, payload: dict) -> None:
        staged.write_text(
            json.dumps(payload, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

    def assert_source_unbound_report_is_rejected(
        self,
        candidate: Path,
        staged: Path,
        output: Path,
        *,
        baseline: Path | None = None,
    ) -> None:
        with self.assertRaisesRegex(
            publisher.PublicationError,
            "does not exactly match the final validated manifest aggregation",
        ):
            publisher.publish(
                candidate,
                staged,
                output,
                baseline_path=baseline,
            )
        self.assertTrue(staged.is_file())
        self.assertFalse(output.exists())

    def test_rejects_valid_schema_with_forged_candidate_version(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            candidate = self.materialize(root / "candidate")
            staged = self.stage(root, candidate)
            payload = json.loads(staged.read_text(encoding="utf-8"))
            payload["candidate_summary"]["candidate"]["version_code"] += 1
            self.rewrite(staged, payload)

            self.assert_source_unbound_report_is_rejected(
                candidate,
                staged,
                root / "aggregate.json",
            )

    def test_rejects_valid_schema_with_forged_physical_device_identity(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            candidate = self.materialize(root / "candidate")
            staged = self.stage(root, candidate)
            payload = json.loads(staged.read_text(encoding="utf-8"))
            older = payload["candidate_summary"]["by_device_class"]["older_phone"]
            older["physical_device_ids"] = ["f" * 64]
            self.rewrite(staged, payload)

            self.assert_source_unbound_report_is_rejected(
                candidate,
                staged,
                root / "aggregate.json",
            )

    def test_rejects_valid_schema_with_forged_baseline_delta(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            candidate = self.materialize(root / "candidate", commit="1" * 40)
            baseline = self.materialize(root / "baseline", commit="2" * 40)
            staged = self.stage(root, candidate, baseline)
            payload = json.loads(staged.read_text(encoding="utf-8"))
            payload["baseline_comparison"]["global_metric_deltas"]["p95_frame_ms"][
                "mean_delta"
            ] = 0.5
            self.rewrite(staged, payload)

            self.assert_source_unbound_report_is_rejected(
                candidate,
                staged,
                root / "aggregate.json",
                baseline=baseline,
            )

    def test_publishes_exactly_reconstructed_baseline_report(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            candidate = self.materialize(root / "candidate", commit="1" * 40)
            baseline = self.materialize(root / "baseline", commit="2" * 40)
            staged = self.stage(root, candidate, baseline)
            output = root / "aggregate.json"

            summary = publisher.publish(
                candidate,
                staged,
                output,
                baseline_path=baseline,
            )

            self.assertTrue(summary["baseline_comparison"])
            self.assertTrue(output.is_file())
            self.assertFalse(staged.exists())


if __name__ == "__main__":
    unittest.main()
