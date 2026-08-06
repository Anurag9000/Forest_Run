from __future__ import annotations

import hashlib
import json
import os
import tempfile
import unittest
from pathlib import Path

import build_release_evidence_index as indexer


class ReleaseEvidenceIndexTest(unittest.TestCase):
    candidate = "a" * 40
    timestamp = "2026-08-06T06:00:00Z"

    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        (self.root / "evidence").mkdir()
        self.manifest = self.root / "evidence/device.json"
        self.manifest.write_text(
            json.dumps(
                {
                    "candidate": {"commit_sha": self.candidate},
                    "sessions": [{"build": {"commit_sha": self.candidate}}],
                }
            ),
            encoding="utf-8",
        )
        self.screenshot = self.root / "evidence/opening.png"
        self.screenshot.write_bytes(b"not-a-real-png-but-valid-index-evidence")

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def build(self, specs=None, **kwargs):
        return indexer.build_index(
            self.root,
            self.candidate,
            specs or [
                "device_acceptance=evidence/device.json",
                "opening_screenshot=evidence/opening.png",
            ],
            generated_at_utc=self.timestamp,
            **kwargs,
        )

    def test_index_is_sorted_hashed_and_candidate_bound(self) -> None:
        payload = self.build(require_bound_kinds=["device_acceptance"])
        self.assertEqual(1, payload["schemaVersion"])
        self.assertEqual(self.candidate, payload["candidateSha"])
        self.assertEqual(2, payload["entryCount"])
        self.assertEqual(1, payload["candidateBoundEntryCount"])
        self.assertEqual(
            ["device_acceptance", "opening_screenshot"],
            [entry["kind"] for entry in payload["entries"]],
        )
        manifest = payload["entries"][0]
        self.assertTrue(manifest["candidateBound"])
        self.assertEqual([self.candidate], manifest["candidateBindings"])
        self.assertEqual(
            hashlib.sha256(self.manifest.read_bytes()).hexdigest(),
            manifest["sha256"],
        )
        expected_set = hashlib.sha256(
            indexer._canonical_bytes(payload["entries"])
        ).hexdigest()
        self.assertEqual(expected_set, payload["evidenceSetSha256"])

    def test_baseline_identity_does_not_invalidate_candidate_binding(self) -> None:
        self.manifest.write_text(
            json.dumps(
                {
                    "candidate": {"commit_sha": self.candidate},
                    "baseline": {"commit_sha": "b" * 40},
                }
            ),
            encoding="utf-8",
        )
        payload = self.build(
            ["device_acceptance=evidence/device.json"],
            require_bound_kinds=["device_acceptance"],
        )
        self.assertEqual([self.candidate], payload["entries"][0]["candidateBindings"])

    def test_explicit_candidate_mismatch_and_missing_binding_fail_closed(self) -> None:
        self.manifest.write_text(
            json.dumps({"candidateSha": "b" * 40}), encoding="utf-8"
        )
        with self.assertRaisesRegex(indexer.EvidenceIndexError, "does not match"):
            self.build(["device_acceptance=evidence/device.json"])

        self.manifest.write_text(json.dumps({"status": "valid"}), encoding="utf-8")
        with self.assertRaisesRegex(indexer.EvidenceIndexError, "explicit candidate SHA"):
            self.build(
                ["device_acceptance=evidence/device.json"],
                require_bound_kinds=["device_acceptance"],
            )

    def test_duplicate_kind_path_hard_link_and_output_alias_are_rejected(self) -> None:
        with self.assertRaisesRegex(indexer.EvidenceIndexError, "duplicate evidence kind"):
            self.build(
                [
                    "same=evidence/device.json",
                    "same=evidence/opening.png",
                ]
            )
        with self.assertRaisesRegex(indexer.EvidenceIndexError, "duplicate evidence path"):
            self.build(
                [
                    "first=evidence/device.json",
                    "second=evidence/device.json",
                ]
            )

        alias = self.root / "evidence/device-copy.json"
        try:
            os.link(self.manifest, alias)
        except OSError as exc:
            self.skipTest(f"hard links unavailable: {exc}")
        with self.assertRaisesRegex(indexer.EvidenceIndexError, "hard link"):
            self.build(
                [
                    "first=evidence/device.json",
                    "second=evidence/device-copy.json",
                ]
            )

        with self.assertRaisesRegex(indexer.EvidenceIndexError, "cannot also be an evidence"):
            self.build(
                ["device_acceptance=evidence/device.json"],
                output=self.manifest,
            )

    def test_unsafe_missing_empty_symlink_and_unsupported_paths_are_rejected(self) -> None:
        for spec in (
            "bad=../escape.json",
            "bad=/absolute.json",
            r"bad=evidence\windows.json",
            "bad=evidence/file.exe",
        ):
            with self.subTest(spec=spec):
                with self.assertRaises(indexer.EvidenceIndexError):
                    self.build([spec])
        with self.assertRaisesRegex(indexer.EvidenceIndexError, "missing"):
            self.build(["missing=evidence/missing.json"])

        empty = self.root / "evidence/empty.txt"
        empty.touch()
        with self.assertRaisesRegex(indexer.EvidenceIndexError, "file size"):
            self.build(["empty=evidence/empty.txt"])

        symlink = self.root / "evidence/link.json"
        try:
            symlink.symlink_to(self.manifest)
        except OSError as exc:
            self.skipTest(f"symlinks unavailable: {exc}")
        with self.assertRaisesRegex(indexer.EvidenceIndexError, "symbolic link"):
            self.build(["link=evidence/link.json"])

    def test_candidate_timestamp_required_kind_and_json_are_strict(self) -> None:
        with self.assertRaisesRegex(indexer.EvidenceIndexError, "40 lowercase"):
            indexer.build_index(
                self.root,
                "A" * 40,
                ["device=evidence/device.json"],
                generated_at_utc=self.timestamp,
            )
        with self.assertRaisesRegex(indexer.EvidenceIndexError, "UTC Z"):
            indexer.build_index(
                self.root,
                self.candidate,
                ["device=evidence/device.json"],
                generated_at_utc="2026-08-06T06:00:00+00:00",
            )
        with self.assertRaisesRegex(indexer.EvidenceIndexError, "are absent"):
            self.build(
                ["device_acceptance=evidence/device.json"],
                require_bound_kinds=["screenshot_manifest"],
            )
        self.manifest.write_bytes(b"{not json}")
        with self.assertRaisesRegex(indexer.EvidenceIndexError, "invalid UTF-8 JSON"):
            self.build(["device=evidence/device.json"])

    def test_publish_is_canonical_atomic_and_refuses_symlink_output(self) -> None:
        payload = self.build()
        output = self.root / "published/index.json"
        indexer.publish_index(output, payload)
        self.assertEqual(payload, json.loads(output.read_text(encoding="utf-8")))
        self.assertTrue(output.read_bytes().endswith(b"\n"))
        self.assertFalse(any(output.parent.glob(".index.json.*.tmp")))

        real = self.root / "published/real.json"
        real.write_text("old", encoding="utf-8")
        link = self.root / "published/link.json"
        try:
            link.symlink_to(real)
        except OSError as exc:
            self.skipTest(f"symlinks unavailable: {exc}")
        with self.assertRaisesRegex(indexer.EvidenceIndexError, "symbolic link"):
            indexer.publish_index(link, payload)
        self.assertEqual("old", real.read_text(encoding="utf-8"))

    def test_cli_publishes_and_failure_preserves_no_partial_output(self) -> None:
        output = self.root / "index.json"
        result = indexer.main(
            [
                "--root",
                str(self.root),
                "--candidate-sha",
                self.candidate,
                "--entry",
                "device_acceptance=evidence/device.json",
                "--require-bound-kind",
                "device_acceptance",
                "--generated-at-utc",
                self.timestamp,
                "--output",
                "index.json",
            ]
        )
        self.assertEqual(0, result)
        self.assertTrue(output.is_file())

        failure_output = self.root / "failure.json"
        result = indexer.main(
            [
                "--root",
                str(self.root),
                "--candidate-sha",
                self.candidate,
                "--entry",
                "missing=evidence/missing.json",
                "--generated-at-utc",
                self.timestamp,
                "--output",
                "failure.json",
            ]
        )
        self.assertEqual(1, result)
        self.assertFalse(failure_output.exists())


if __name__ == "__main__":
    unittest.main()
