from __future__ import annotations

import os
import tempfile
import unittest
from pathlib import Path

import aggregate_device_acceptance as aggregate
from test_validate_device_acceptance import materialize_files, valid_bundle


class AggregateDeviceAcceptanceAliasTest(unittest.TestCase):
    @staticmethod
    def write_bundle(root: Path) -> tuple[Path, dict]:
        import json

        bundle = valid_bundle()
        root.mkdir(parents=True, exist_ok=True)
        manifest = root / "manifest.json"
        manifest.write_text(
            json.dumps(bundle, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        materialize_files(root, bundle)
        return manifest, bundle

    def test_output_cannot_overwrite_manifest_artifact_or_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest, bundle = self.write_bundle(root)
            artifact = root / bundle["candidate"]["artifact_path"]
            evidence = root / next(
                item["path"]
                for session in bundle["sessions"]
                for scenario in session["scenarios"].values()
                for item in scenario["evidence_files"]
            )

            for protected in (manifest, artifact, evidence):
                with self.subTest(protected=protected):
                    before = protected.read_bytes()
                    self.assertEqual(
                        1,
                        aggregate.main(
                            [str(manifest), "--output", str(protected)]
                        ),
                    )
                    self.assertEqual(before, protected.read_bytes())
                    self.assertFalse(
                        any(protected.parent.glob(f".{protected.name}.*.tmp"))
                    )

    def test_existing_hardlink_to_evidence_cannot_be_output(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest, bundle = self.write_bundle(root)
            evidence = root / next(
                item["path"]
                for session in bundle["sessions"]
                for scenario in session["scenarios"].values()
                for item in scenario["evidence_files"]
            )
            alias = root / "aggregate.json"
            os.link(evidence, alias)
            before = evidence.read_bytes()

            self.assertEqual(
                1,
                aggregate.main([str(manifest), "--output", str(alias)]),
            )
            self.assertEqual(before, evidence.read_bytes())
            self.assertEqual(before, alias.read_bytes())
            self.assertTrue(os.path.samefile(evidence, alias))

    def test_candidate_and_baseline_must_be_distinct_in_python_core(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest, _ = self.write_bundle(root)

            with self.assertRaisesRegex(
                aggregate.AggregationError,
                "must be distinct files",
            ):
                aggregate.aggregate(manifest, baseline_path=manifest)

            hardlink = root / "baseline.json"
            os.link(manifest, hardlink)
            with self.assertRaisesRegex(
                aggregate.AggregationError,
                "must be distinct files",
            ):
                aggregate.aggregate(manifest, baseline_path=hardlink)

    def test_unrelated_output_remains_allowed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest, _ = self.write_bundle(root)
            output = root / "reports" / "aggregate.json"

            self.assertEqual(
                0,
                aggregate.main([str(manifest), "--output", str(output)]),
            )
            self.assertTrue(output.is_file())


if __name__ == "__main__":
    unittest.main()
