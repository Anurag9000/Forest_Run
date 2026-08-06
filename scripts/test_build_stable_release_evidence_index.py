import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import build_stable_release_evidence_index as stable


CANDIDATE = "a" * 40
GENERATED_AT = "2026-08-06T12:00:00Z"


class StableReleaseEvidenceIndexTest(unittest.TestCase):
    def test_builds_from_one_snapshot_and_independently_verifies(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            evidence = root / "evidence" / "acceptance.json"
            evidence.parent.mkdir()
            evidence.write_text(
                json.dumps({"candidateSha": CANDIDATE, "status": "valid"}),
                encoding="utf-8",
            )
            output = root / "release-evidence-index.json"

            summary = stable.build_stable_index(
                root=root,
                candidate_sha=CANDIDATE,
                specs=["acceptance=evidence/acceptance.json"],
                generated_at_utc=GENERATED_AT,
                output=output,
                require_bound_kinds=["acceptance"],
            )

            self.assertEqual("valid", summary["status"])
            self.assertEqual(CANDIDATE, summary["candidateSha"])
            self.assertEqual(1, summary["entryCount"])
            self.assertTrue(output.is_file())

    @unittest.skipUnless(hasattr(os, "symlink"), "symlink support required")
    def test_rejects_symlinked_evidence_parent(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            real = root / "real"
            real.mkdir()
            (real / "acceptance.json").write_text(
                json.dumps({"candidateSha": CANDIDATE}),
                encoding="utf-8",
            )
            (root / "evidence").symlink_to(real, target_is_directory=True)

            with self.assertRaisesRegex(
                stable.StableEvidenceIndexError,
                "must not traverse a symbolic link",
            ):
                stable.build_stable_index(
                    root=root,
                    candidate_sha=CANDIDATE,
                    specs=["acceptance=evidence/acceptance.json"],
                    generated_at_utc=GENERATED_AT,
                    output=root / "release-evidence-index.json",
                )

    def test_rejects_source_mutation_after_snapshot_before_publication(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            evidence = root / "acceptance.json"
            evidence.write_text(
                json.dumps({"candidateSha": CANDIDATE}),
                encoding="utf-8",
            )
            original = stable.builder.build_index

            def mutate_after_build(*args, **kwargs):
                payload = original(*args, **kwargs)
                evidence.write_text(
                    json.dumps({"candidateSha": CANDIDATE, "changed": True}),
                    encoding="utf-8",
                )
                return payload

            with mock.patch.object(
                stable.builder,
                "build_index",
                side_effect=mutate_after_build,
            ):
                with self.assertRaisesRegex(
                    stable.StableEvidenceIndexError,
                    "changed after snapshot",
                ):
                    stable.build_stable_index(
                        root=root,
                        candidate_sha=CANDIDATE,
                        specs=["acceptance=acceptance.json"],
                        generated_at_utc=GENERATED_AT,
                        output=root / "release-evidence-index.json",
                    )
            self.assertFalse((root / "release-evidence-index.json").exists())

    @unittest.skipUnless(hasattr(os, "link"), "hard-link support required")
    def test_rejects_two_specs_reusing_one_inode(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            first = root / "first.json"
            second = root / "second.json"
            first.write_text(json.dumps({"candidateSha": CANDIDATE}), encoding="utf-8")
            os.link(first, second)

            with self.assertRaisesRegex(
                stable.StableEvidenceIndexError,
                "reused through a hard link",
            ):
                stable.build_stable_index(
                    root=root,
                    candidate_sha=CANDIDATE,
                    specs=["first=first.json", "second=second.json"],
                    generated_at_utc=GENERATED_AT,
                    output=root / "release-evidence-index.json",
                )

    def test_strict_verifier_rejects_duplicate_json_keys_and_removes_output(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            evidence = root / "acceptance.json"
            evidence.write_text(
                '{"candidateSha":"%s","candidateSha":"%s"}'
                % (CANDIDATE, CANDIDATE),
                encoding="utf-8",
            )
            output = root / "release-evidence-index.json"

            with self.assertRaisesRegex(
                stable.StableEvidenceIndexError,
                "independent verification failed",
            ):
                stable.build_stable_index(
                    root=root,
                    candidate_sha=CANDIDATE,
                    specs=["acceptance=acceptance.json"],
                    generated_at_utc=GENERATED_AT,
                    output=output,
                )
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
