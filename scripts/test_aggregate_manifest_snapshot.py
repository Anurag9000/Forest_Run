from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import aggregate_device_acceptance as aggregate
from acceptance_test_support import materialize_traced_bundle
from test_validate_device_acceptance import valid_bundle


class AggregateManifestSnapshotTest(unittest.TestCase):
    def test_manifest_mutation_between_acceptance_and_trace_validation_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            bundle = valid_bundle()
            materialize_traced_bundle(root, bundle)
            manifest = root / "manifest.json"
            manifest.write_text(
                json.dumps(bundle, indent=2, sort_keys=True) + "\n",
                encoding="utf-8",
            )
            original = aggregate.manifest_traces.validate_manifest_traces

            def validate_then_mutate(path: Path, **kwargs):
                summary = original(path, **kwargs)
                resolved = Path(path)
                resolved.write_text(
                    resolved.read_text(encoding="utf-8") + " ",
                    encoding="utf-8",
                )
                return summary

            with patch.object(
                aggregate.manifest_traces,
                "validate_manifest_traces",
                side_effect=validate_then_mutate,
            ):
                with self.assertRaisesRegex(
                    aggregate.AggregationError,
                    "changed while trace contracts were validated",
                ):
                    aggregate.aggregate(manifest)

    def test_stable_manifest_read_rejects_missing_directory_and_oversize(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaisesRegex(aggregate.AggregationError, "is missing"):
                aggregate._stable_manifest_read(root / "missing.json")
            with self.assertRaisesRegex(aggregate.AggregationError, "not a regular file"):
                aggregate._stable_manifest_read(root)

            oversized = root / "oversized.json"
            with oversized.open("wb") as handle:
                handle.truncate(aggregate.acceptance.MAX_MANIFEST_BYTES + 1)
            with self.assertRaisesRegex(aggregate.AggregationError, "between"):
                aggregate._stable_manifest_read(oversized)


if __name__ == "__main__":
    unittest.main()
