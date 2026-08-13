from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from PIL import Image

import visual_baseline_provenance as provenance


class VisualBaselineProvenanceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.baseline = self.root / "baseline"
        self.baseline.mkdir()
        self.manifest = self.root / "manifest.json"
        self.manifest.write_text(
            json.dumps(
                {
                    "screenshots": [
                        {
                            "order": 1,
                            "raw_file": "01_FOREST.png",
                            "final_file": "01_forest.png",
                            "scenario": "FOREST",
                            "title": "Forest",
                            "purpose": "Provenance test",
                        },
                        {
                            "order": 2,
                            "raw_file": "02_BLOOM.png",
                            "final_file": "02_bloom.png",
                            "scenario": "BLOOM",
                            "title": "Bloom",
                            "purpose": "Provenance test",
                        },
                    ]
                }
            ),
            encoding="utf-8",
        )
        for index, name in enumerate(("01_forest.png", "02_bloom.png"), start=1):
            Image.new("RGB", (5, 4), (index, index + 1, index + 2)).save(
                self.baseline / name,
                format="PNG",
            )
        self.candidate_sha = "a" * 40

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def test_build_and_verify_round_trip(self) -> None:
        payload = provenance.build_provenance(
            manifest_path=self.manifest,
            baseline_dir=self.baseline,
            filename_field="final_file",
            baseline_candidate_sha=self.candidate_sha,
        )
        self.assertEqual("visual_baseline_identity", payload["kind"])
        self.assertEqual(2, payload["screenshotCount"])
        self.assertEqual(self.candidate_sha, payload["baselineCandidateSha"])
        output = self.root / "baseline-provenance.json"
        provenance.publish(output, payload)
        self.assertEqual(
            payload,
            provenance.verify_provenance(
                output,
                manifest_path=self.manifest,
                baseline_dir=self.baseline,
                filename_field="final_file",
            ),
        )

    def test_modified_baseline_is_rejected(self) -> None:
        payload = provenance.build_provenance(
            manifest_path=self.manifest,
            baseline_dir=self.baseline,
            filename_field="final_file",
            baseline_candidate_sha=self.candidate_sha,
        )
        output = self.root / "baseline-provenance.json"
        provenance.publish(output, payload)
        Image.new("RGB", (5, 4), (99, 98, 97)).save(
            self.baseline / "01_forest.png",
            format="PNG",
        )
        with self.assertRaisesRegex(
            provenance.VisualBaselineProvenanceError,
            "does not match",
        ):
            provenance.verify_provenance(
                output,
                manifest_path=self.manifest,
                baseline_dir=self.baseline,
                filename_field="final_file",
            )

    def test_modified_manifest_is_rejected(self) -> None:
        payload = provenance.build_provenance(
            manifest_path=self.manifest,
            baseline_dir=self.baseline,
            filename_field="final_file",
            baseline_candidate_sha=self.candidate_sha,
        )
        output = self.root / "baseline-provenance.json"
        provenance.publish(output, payload)
        source = json.loads(self.manifest.read_text(encoding="utf-8"))
        source["screenshots"][0]["purpose"] = "Changed manifest identity"
        self.manifest.write_text(json.dumps(source), encoding="utf-8")
        with self.assertRaisesRegex(
            provenance.VisualBaselineProvenanceError,
            "does not match",
        ):
            provenance.verify_provenance(
                output,
                manifest_path=self.manifest,
                baseline_dir=self.baseline,
                filename_field="final_file",
            )

    def test_tampered_descriptor_and_duplicate_keys_fail_closed(self) -> None:
        payload = provenance.build_provenance(
            manifest_path=self.manifest,
            baseline_dir=self.baseline,
            filename_field="final_file",
            baseline_candidate_sha=self.candidate_sha,
        )
        output = self.root / "baseline-provenance.json"
        payload["screenshotSetSha256"] = "0" * 64
        provenance.publish(output, payload)
        with self.assertRaises(provenance.VisualBaselineProvenanceError):
            provenance.verify_provenance(
                output,
                manifest_path=self.manifest,
                baseline_dir=self.baseline,
                filename_field="final_file",
            )

        output.write_text(
            '{"schemaVersion":1,"schemaVersion":1}',
            encoding="utf-8",
        )
        with self.assertRaisesRegex(
            provenance.VisualBaselineProvenanceError,
            "duplicate JSON object key",
        ):
            provenance.verify_provenance(
                output,
                manifest_path=self.manifest,
                baseline_dir=self.baseline,
                filename_field="final_file",
            )

    def test_candidate_sha_is_exact_lowercase_git_sha(self) -> None:
        for candidate in ("A" * 40, "a" * 39, "main"):
            with self.subTest(candidate=candidate):
                with self.assertRaisesRegex(
                    provenance.VisualBaselineProvenanceError,
                    "40 lowercase",
                ):
                    provenance.build_provenance(
                        manifest_path=self.manifest,
                        baseline_dir=self.baseline,
                        filename_field="final_file",
                        baseline_candidate_sha=candidate,
                    )


if __name__ == "__main__":
    unittest.main()
