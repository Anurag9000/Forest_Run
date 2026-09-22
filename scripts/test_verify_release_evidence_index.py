from __future__ import annotations

import hashlib
import json
import os
import tempfile
import unittest
from unittest import mock
from pathlib import Path

import build_release_evidence_index as builder
import verify_release_evidence_index as verifier


class ReleaseEvidenceIndexVerifierTest(unittest.TestCase):
    candidate = "a" * 40
    timestamp = "2026-08-06T06:00:00Z"

    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        (self.root / "evidence").mkdir()
        self.bound = self.root / "evidence/device.json"
        self.bound.write_text(
            json.dumps({"candidate": {"commit_sha": self.candidate}}),
            encoding="utf-8",
        )
        self.note = self.root / "evidence/review.txt"
        self.note.write_text("approved\n", encoding="utf-8")
        self.index = self.root / "release-evidence-index.json"
        self.specs = [
            "device_acceptance=evidence/device.json",
            "review=evidence/review.txt",
        ]
        payload = builder.build_index(
            self.root,
            self.candidate,
            self.specs,
            generated_at_utc=self.timestamp,
            require_bound_kinds=["device_acceptance"],
            output=self.index,
        )
        builder.publish_index(self.index, payload, root=self.root)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def verify(self):
        return verifier.verify_index(
            self.index,
            root=self.root,
            expected_candidate_sha=self.candidate,
            require_bound_kinds=["device_acceptance"],
        )

    def test_valid_builder_output_is_independently_reconstructed(self) -> None:
        summary = self.verify()
        self.assertEqual("valid", summary["status"])
        self.assertEqual(2, summary["entryCount"])
        self.assertEqual(1, summary["candidateBoundEntryCount"])

    def test_tampered_evidence_is_rejected(self) -> None:
        self.note.write_text("changed\n", encoding="utf-8")
        with self.assertRaisesRegex(
            verifier.EvidenceIndexVerificationError,
            "does not match",
        ):
            self.verify()

    def test_forged_entry_digest_and_set_digest_are_rejected_against_disk(self) -> None:
        payload = json.loads(self.index.read_text(encoding="utf-8"))
        payload["entries"][1]["sha256"] = "b" * 64
        payload["evidenceSetSha256"] = hashlib.sha256(
            verifier._canonical_bytes(payload["entries"])
        ).hexdigest()
        self.index.write_text(json.dumps(payload), encoding="utf-8")
        with self.assertRaisesRegex(
            verifier.EvidenceIndexVerificationError,
            "does not match",
        ):
            self.verify()

    def test_duplicate_index_key_is_rejected_by_strict_json(self) -> None:
        text = self.index.read_text(encoding="utf-8")
        self.index.write_text(
            text.replace(
                '{"candidateBoundEntryCount"',
                '{"schemaVersion":1,"candidateBoundEntryCount"',
                1,
            ),
            encoding="utf-8",
        )
        with self.assertRaisesRegex(
            verifier.EvidenceIndexVerificationError,
            "duplicate JSON object key",
        ):
            self.verify()

    def test_expected_candidate_mismatch_is_rejected(self) -> None:
        with self.assertRaisesRegex(
            verifier.EvidenceIndexVerificationError,
            "expected candidate",
        ):
            verifier.verify_index(
                self.index,
                root=self.root,
                expected_candidate_sha="b" * 40,
            )

    def test_duplicate_physical_evidence_via_hard_link_is_rejected(self) -> None:
        alias = self.root / "evidence/alias.txt"
        try:
            os.link(self.note, alias)
        except OSError as exc:
            self.skipTest(f"hard links unavailable: {exc}")
        payload = json.loads(self.index.read_text(encoding="utf-8"))
        alias_entry = dict(payload["entries"][1])
        alias_entry["kind"] = "review_alias"
        alias_entry["path"] = "evidence/alias.txt"
        payload["entries"].append(alias_entry)
        payload["entries"].sort(key=lambda entry: (entry["kind"], entry["path"]))
        payload["entryCount"] = 3
        payload["evidenceSetSha256"] = hashlib.sha256(
            verifier._canonical_bytes(payload["entries"])
        ).hexdigest()
        self.index.write_text(json.dumps(payload), encoding="utf-8")
        with self.assertRaisesRegex(
            verifier.EvidenceIndexVerificationError,
            "hard link",
        ):
            self.verify()

    def test_index_hard_link_alias_of_indexed_file_is_rejected_before_digest(self) -> None:
        self.index.unlink()
        self.bound.unlink()
        self.bound.write_text(
            json.dumps(
                {
                    "schemaVersion": 1,
                    "candidateSha": self.candidate,
                    "generatedAtUtc": self.timestamp,
                    "entryCount": 1,
                    "candidateBoundEntryCount": 0,
                    "evidenceSetSha256": "0" * 64,
                    "entries": [
                        {
                            "kind": "self",
                            "path": "evidence/device.json",
                            "bytes": 1,
                            "sha256": "0" * 64,
                            "candidateBound": False,
                            "candidateBindings": [],
                        }
                    ],
                }
            ),
            encoding="utf-8",
        )
        try:
            os.link(self.bound, self.index)
        except OSError as exc:
            self.skipTest(f"hard links unavailable: {exc}")
        payload = json.loads(self.bound.read_text(encoding="utf-8"))
        payload["entries"][0]["bytes"] = self.bound.stat().st_size
        payload["evidenceSetSha256"] = hashlib.sha256(
            verifier._canonical_bytes(payload["entries"])
        ).hexdigest()
        self.bound.write_text(json.dumps(payload), encoding="utf-8")
        with self.assertRaisesRegex(
            verifier.EvidenceIndexVerificationError,
            "aliases",
        ):
            verifier.verify_index(self.index, root=self.root)

    def test_symlinked_evidence_is_rejected(self) -> None:
        target = self.root / "evidence/target.txt"
        target.write_text("approved\n", encoding="utf-8")
        self.note.unlink()
        try:
            self.note.symlink_to(target)
        except OSError as exc:
            self.skipTest(f"symlinks unavailable: {exc}")
        with self.assertRaisesRegex(
            verifier.EvidenceIndexVerificationError,
            "symbolic link",
        ):
            self.verify()

    @unittest.skipUnless(hasattr(os, "mkfifo"), "FIFO support required")
    def test_fifo_evidence_is_rejected_before_open(self) -> None:
        self.note.unlink()
        os.mkfifo(self.note)
        with mock.patch.object(verifier.os, "open", wraps=verifier.os.open) as opened:
            with self.assertRaisesRegex(
                verifier.EvidenceIndexVerificationError,
                "regular file",
            ):
                self.verify()
            # The index is opened normally; the FIFO evidence is never opened.
            self.assertFalse(
                any(Path(call.args[0]) == self.note for call in opened.call_args_list)
            )

    @unittest.skipUnless(hasattr(os, "O_NONBLOCK"), "nonblocking open required")
    def test_verifier_opens_index_and_evidence_nonblocking_and_nofollow(self) -> None:
        with mock.patch.object(verifier.os, "open", wraps=verifier.os.open) as opened:
            self.verify()
        protected = {self.index, self.note, self.bound}
        opened_evidence = [
            call for call in opened.call_args_list
            if Path(call.args[0]) in protected
        ]
        self.assertEqual(protected, {Path(call.args[0]) for call in opened_evidence})
        for call in opened_evidence:
            self.assertTrue(call.args[1] & os.O_NONBLOCK)
            if hasattr(os, "O_NOFOLLOW"):
                self.assertTrue(call.args[1] & os.O_NOFOLLOW)

    def test_required_kind_must_be_candidate_bound(self) -> None:
        with self.assertRaisesRegex(
            verifier.EvidenceIndexVerificationError,
            "not candidate-bound",
        ):
            verifier.verify_index(
                self.index,
                root=self.root,
                require_bound_kinds=["review"],
            )


if __name__ == "__main__":
    unittest.main()
