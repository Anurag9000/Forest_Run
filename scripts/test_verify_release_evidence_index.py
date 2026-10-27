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

    def test_schema_version_must_be_integer(self) -> None:
        for invalid in (True, False, 1.0, "1", None):
            with self.subTest(value=invalid):
                payload = json.loads(self.index.read_text(encoding="utf-8"))
                payload["schemaVersion"] = invalid
                self.index.write_text(json.dumps(payload), encoding="utf-8")
                with self.assertRaisesRegex(
                    verifier.EvidenceIndexVerificationError, "schemaVersion"
                ):
                    self.verify()

    def test_valid_builder_output_is_independently_reconstructed(self) -> None:
        summary = self.verify()
        self.assertEqual("valid", summary["status"])
        self.assertEqual(2, summary["entryCount"])
        self.assertEqual(1, summary["candidateBoundEntryCount"])

    def test_evidence_root_symlink_alias_is_rejected_by_builder_and_verifier(self) -> None:
        alias = self.root.parent / (self.root.name + "-alias")
        alias.symlink_to(self.root, target_is_directory=True)
        try:
            with self.assertRaisesRegex(builder.EvidenceIndexError, "symbolic link"):
                builder.collect_entries(alias, self.candidate, self.specs)
            with self.assertRaisesRegex(
                verifier.EvidenceIndexVerificationError, "symbolic link"
            ):
                verifier.verify_index(
                    self.index,
                    root=alias,
                    expected_candidate_sha=self.candidate,
                    require_bound_kinds=["device_acceptance"],
                )
        finally:
            alias.unlink()

    def test_tampered_evidence_is_rejected(self) -> None:
        self.note.write_text("changed\n", encoding="utf-8")
        with self.assertRaisesRegex(
            verifier.EvidenceIndexVerificationError,
            "does not match",
        ):
            self.verify()

    def test_index_mutation_during_evidence_verification_is_rejected(self) -> None:
        original_read = verifier._read_stable_file
        changed = False

        def mutate_index_after_evidence_read(path, **kwargs):
            nonlocal changed
            result = original_read(path, **kwargs)
            if path == self.note and not changed:
                self.index.write_bytes(self.index.read_bytes() + b" ")
                changed = True
            return result

        with mock.patch.object(
            verifier, "_read_stable_file", side_effect=mutate_index_after_evidence_read
        ):
            with self.assertRaisesRegex(
                verifier.EvidenceIndexVerificationError,
                "index changed during verification",
            ):
                self.verify()
        self.assertTrue(changed)

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

    def test_padded_candidate_identity_in_indexed_json_is_rejected(self) -> None:
        raw = json.dumps(
            {"candidate": {"commit_sha": self.candidate + " "}},
            separators=(",", ":"),
        ).encode("utf-8")
        self.bound.write_bytes(raw)
        payload = json.loads(self.index.read_text(encoding="utf-8"))
        entry = next(
            entry for entry in payload["entries"]
            if entry["path"] == "evidence/device.json"
        )
        entry["bytes"] = len(raw)
        entry["sha256"] = hashlib.sha256(raw).hexdigest()
        payload["evidenceSetSha256"] = hashlib.sha256(
            verifier._canonical_bytes(payload["entries"])
        ).hexdigest()
        self.index.write_text(json.dumps(payload), encoding="utf-8")

        with self.assertRaisesRegex(
            verifier.EvidenceIndexVerificationError,
            "malformed candidate SHA",
        ):
            self.verify()

    def test_non_string_candidate_identity_fields_are_rejected(self) -> None:
        for payload in (
            {"candidateSha": None},
            {"candidate_sha": 7},
            {"candidate": {"commit_sha": []}},
        ):
            with self.subTest(payload=payload):
                with self.assertRaisesRegex(
                    verifier.EvidenceIndexVerificationError,
                    "candidate identity field|candidate commit field",
                ):
                    verifier._load_evidence_bindings(
                        self.bound,
                        json.dumps(payload).encode("utf-8"),
                    )

    def test_required_bound_kind_inputs_must_be_strings(self) -> None:
        for required in ([None], [7], [object()]):
            with self.subTest(required=required):
                with self.assertRaisesRegex(
                    verifier.EvidenceIndexVerificationError,
                    "required candidate-bound kinds",
                ):
                    verifier.verify_index(
                        self.index,
                        root=self.root,
                        require_bound_kinds=required,
                    )

    def test_evidence_paths_reject_control_characters_and_unpaired_surrogates(self) -> None:
        for path in ("evidence/line\x01break.txt", "evidence/\ud800.txt"):
            with self.subTest(path=path):
                with self.assertRaisesRegex(
                    verifier.EvidenceIndexVerificationError,
                    "path|Unicode",
                ):
                    verifier._safe_relative_path(path, label="entry.path")

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
                any(Path(call.args[0]).name == self.note.name for call in opened.call_args_list)
            )

    @unittest.skipUnless(
        hasattr(os, "mkfifo") and hasattr(os, "O_NONBLOCK"),
        "nonblocking FIFO race protection required",
    )
    def test_regular_evidence_replaced_by_fifo_before_open_does_not_block(self) -> None:
        real_open = os.open
        swapped = False

        def replace_before_open(value, flags, *args, **kwargs):
            nonlocal swapped
            if (
                not swapped
                and value == self.note.name
                and kwargs.get("dir_fd") is not None
                and flags & os.O_ACCMODE == os.O_RDONLY
                and flags & os.O_NOFOLLOW
            ):
                self.note.unlink()
                os.mkfifo(self.note)
                swapped = True
                self.assertTrue(flags & os.O_NONBLOCK)
            return real_open(value, flags, *args, **kwargs)

        try:
            with mock.patch.object(verifier.os, "open", side_effect=replace_before_open):
                with self.assertRaisesRegex(
                    verifier.EvidenceIndexVerificationError, "regular file"
                ):
                    self.verify()
            self.assertTrue(swapped)
        finally:
            if self.note.exists():
                self.note.unlink()

    @unittest.skipUnless(hasattr(os, "O_NONBLOCK"), "nonblocking open required")
    def test_verifier_opens_index_and_evidence_nonblocking_and_nofollow(self) -> None:
        with mock.patch.object(verifier.os, "open", wraps=verifier.os.open) as opened:
            self.verify()
        protected = {self.index.name, self.note.name, self.bound.name}
        opened_evidence = [
            call for call in opened.call_args_list
            if Path(call.args[0]).name in protected and call.kwargs.get("dir_fd") is not None
        ]
        self.assertEqual(protected, {Path(call.args[0]).name for call in opened_evidence})
        for call in opened_evidence:
            self.assertTrue(call.args[1] & os.O_NONBLOCK)
            if hasattr(os, "O_NOFOLLOW"):
                self.assertTrue(call.args[1] & os.O_NOFOLLOW)

    @unittest.skipUnless(hasattr(os, "O_DIRECTORY"), "directory descriptors required")
    def test_parent_swap_to_symlink_after_validation_cannot_escape_evidence_root(self) -> None:
        with tempfile.TemporaryDirectory() as external_directory:
            external = Path(external_directory)
            (external / "device.json").write_bytes(self.bound.read_bytes())
            (external / "review.txt").write_bytes(self.note.read_bytes())
            evidence_directory = self.root / "evidence"
            preserved_directory = self.root / "evidence-original"
            real_open = os.open
            swapped = False

            def swap_before_open(path, flags, *args, **kwargs):
                nonlocal swapped
                if path == "evidence" and kwargs.get("dir_fd") is not None and not swapped:
                    evidence_directory.rename(preserved_directory)
                    evidence_directory.symlink_to(external, target_is_directory=True)
                    swapped = True
                return real_open(path, flags, *args, **kwargs)

            try:
                with mock.patch.object(verifier.os, "open", side_effect=swap_before_open):
                    with self.assertRaisesRegex(
                        verifier.EvidenceIndexVerificationError,
                        "safely open or read",
                    ):
                        self.verify()
            finally:
                if evidence_directory.is_symlink():
                    evidence_directory.unlink()
                if preserved_directory.exists():
                    preserved_directory.rename(evidence_directory)
            self.assertTrue(swapped)

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

    def test_noncanonical_indexed_paths_are_rejected(self) -> None:
        payload = json.loads(self.index.read_text(encoding="utf-8"))
        payload["entries"][1]["path"] = "evidence//review.txt"
        payload["evidenceSetSha256"] = hashlib.sha256(
            verifier._canonical_bytes(payload["entries"])
        ).hexdigest()
        self.index.write_text(json.dumps(payload), encoding="utf-8")
        with self.assertRaisesRegex(
            verifier.EvidenceIndexVerificationError, "canonical POSIX"
        ):
            self.verify()

    def test_windows_drive_path_is_not_accepted_as_a_relative_index_path(self) -> None:
        payload = json.loads(self.index.read_text(encoding="utf-8"))
        payload["entries"][1]["path"] = "C:/outside/review.txt"
        payload["evidenceSetSha256"] = hashlib.sha256(
            verifier._canonical_bytes(payload["entries"])
        ).hexdigest()
        self.index.write_text(json.dumps(payload), encoding="utf-8")
        with self.assertRaisesRegex(
            verifier.EvidenceIndexVerificationError,
            "POSIX relative path",
        ):
            self.verify()

    def test_evidence_cannot_be_reached_through_symlinked_parent(self) -> None:
        real_directory = self.root / "evidence/real"
        real_directory.mkdir()
        real_file = real_directory / "review.txt"
        real_file.write_text("approved\n", encoding="utf-8")
        alias_directory = self.root / "evidence/alias"
        try:
            alias_directory.symlink_to(real_directory, target_is_directory=True)
        except OSError as exc:
            self.skipTest(f"symlinks unavailable: {exc}")
        payload = json.loads(self.index.read_text(encoding="utf-8"))
        entry = payload["entries"][1]
        entry["path"] = "evidence/alias/review.txt"
        content = real_file.read_bytes()
        entry["bytes"] = len(content)
        entry["sha256"] = hashlib.sha256(content).hexdigest()
        payload["evidenceSetSha256"] = hashlib.sha256(
            verifier._canonical_bytes(payload["entries"])
        ).hexdigest()
        self.index.write_text(json.dumps(payload), encoding="utf-8")
        with self.assertRaisesRegex(
            verifier.EvidenceIndexVerificationError, "must not traverse a symbolic link"
        ):
            self.verify()

    def test_index_cannot_be_reached_through_symlinked_parent(self) -> None:
        alias_directory = self.root / "alias"
        alias_directory.symlink_to(self.root, target_is_directory=True)
        with self.assertRaisesRegex(
            verifier.EvidenceIndexVerificationError, "must not traverse a symbolic link"
        ):
            verifier.verify_index(alias_directory / self.index.name, root=self.root)


if __name__ == "__main__":
    unittest.main()
