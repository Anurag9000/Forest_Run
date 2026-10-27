import json
import os
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from io import StringIO
from pathlib import Path
from unittest import mock

import build_stable_release_evidence_index as stable


CANDIDATE = "a" * 40
GENERATED_AT = "2026-08-06T12:00:00Z"


class StableReleaseEvidenceIndexTest(unittest.TestCase):
    def test_entry_spec_requires_kind_separator_and_nonempty_kind(self) -> None:
        for spec, expected in (("evidence.json", "kind=relative/path"), ("=evidence.json", "unsafe evidence entry")):
            with self.subTest(spec=spec), tempfile.TemporaryDirectory() as directory:
                with self.assertRaisesRegex(stable.StableEvidenceIndexError, expected):
                    stable.build_stable_index(
                        root=Path(directory),
                        candidate_sha=CANDIDATE,
                        specs=[spec],
                        generated_at_utc=GENERATED_AT,
                        output=Path(directory) / "index.json",
                    )

    def test_missing_snapshot_source_fails_before_publication(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "index.json"
            with self.assertRaisesRegex(stable.StableEvidenceIndexError, "missing"):
                stable.build_stable_index(
                    root=root,
                    candidate_sha=CANDIDATE,
                    specs=["acceptance=missing.json"],
                    generated_at_utc=GENERATED_AT,
                    output=output,
                )
            self.assertFalse(output.exists())

    def test_snapshot_rejects_source_replacement_truncation_and_growth_during_read(self) -> None:
        mutations = (
            ("replace", "changed during snapshot"),
            ("truncate", "changed during snapshot"),
            ("grow", "grew during snapshot"),
        )
        for mutation, expected_error in mutations:
            with self.subTest(mutation=mutation), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                evidence = root / "acceptance.json"
                evidence.write_bytes(b'{"candidateSha":"' + CANDIDATE.encode() + b'"}')
                original = evidence.read_bytes()
                inode = evidence.stat().st_ino
                real_read = stable.os.read
                changed = False

                def mutate_after_read(descriptor, size):
                    nonlocal changed
                    chunk = real_read(descriptor, size)
                    if not changed and chunk and os.fstat(descriptor).st_ino == inode:
                        changed = True
                        if mutation == "replace":
                            evidence.rename(root / "original.json")
                            evidence.write_bytes(original)
                        elif mutation == "truncate":
                            evidence.write_bytes(b"{}")
                        else:
                            with evidence.open("ab") as stream:
                                stream.write(b"x")
                    return chunk

                with mock.patch.object(stable.os, "read", side_effect=mutate_after_read):
                    with self.assertRaisesRegex(stable.StableEvidenceIndexError, expected_error):
                        stable._read_snapshot(root, evidence.name)
                self.assertTrue(changed)
    def test_snapshot_streams_to_private_disk_file_without_retaining_file_bytes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            evidence = root / "large-evidence.jpg"
            expected = b"evidence-bytes" * 500_000
            evidence.write_bytes(expected)
            destination = root / "snapshot" / "large-evidence.jpg"

            snapshot = stable._read_snapshot(root, evidence.name, destination)

            self.assertEqual(expected, destination.read_bytes())
            self.assertEqual(len(expected), snapshot.size)
            self.assertEqual(stable.hashlib.sha256(expected).hexdigest(), snapshot.sha256)
            self.assertFalse(hasattr(snapshot, "bytes_value"))

    def test_rejects_noncanonical_and_windows_style_entry_paths(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for spec in (
                "entry=C:/outside/evidence.json",
                "entry=evidence//nested.json",
                "entry=evidence/./nested.json",
                r"entry=evidence\nested.json",
                "entry=evidence/line\x01break.json",
                "entry=evidence/\ud800.json",
            ):
                with self.subTest(spec=spec):
                    with self.assertRaisesRegex(
                        stable.StableEvidenceIndexError,
                        "unsafe evidence entry|valid Unicode",
                    ):
                        stable.build_stable_index(
                            root=root,
                            candidate_sha=CANDIDATE,
                            specs=[spec],
                            generated_at_utc=GENERATED_AT,
                            output=root / "index.json",
                        )

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

    def test_cli_reports_success_and_domain_failure(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "acceptance.json").write_text(
                json.dumps({"candidateSha": CANDIDATE}), encoding="utf-8"
            )
            arguments = [
                "--root", str(root),
                "--candidate-sha", CANDIDATE,
                "--entry", "acceptance=acceptance.json",
                "--generated-at-utc", GENERATED_AT,
                "--output", "index.json",
            ]
            stdout = StringIO()
            with redirect_stdout(stdout):
                self.assertEqual(0, stable.main(arguments))
            self.assertIn("built and verified 1 stable evidence files", stdout.getvalue())

            stderr = StringIO()
            with (
                mock.patch.object(
                    stable,
                    "build_stable_index",
                    side_effect=stable.StableEvidenceIndexError("injected failure"),
                ),
                redirect_stderr(stderr),
            ):
                self.assertEqual(1, stable.main(arguments))
            self.assertIn("stable release evidence index error: injected failure", stderr.getvalue())

    def test_required_bound_kind_iterable_reaches_independent_verifier_intact(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "acceptance.json").write_text(
                json.dumps({"candidateSha": CANDIDATE}),
                encoding="utf-8",
            )
            observed: list[tuple[str, ...]] = []
            original_verify = stable.verifier.verify_index

            def record_required_kinds(index_path, **kwargs):
                observed.append(tuple(kwargs["require_bound_kinds"]))
                return original_verify(index_path, **kwargs)

            with mock.patch.object(
                stable.verifier,
                "verify_index",
                side_effect=record_required_kinds,
            ):
                stable.build_stable_index(
                    root=root,
                    candidate_sha=CANDIDATE,
                    specs=["acceptance=acceptance.json"],
                    generated_at_utc=GENERATED_AT,
                    output=root / "release-evidence-index.json",
                    require_bound_kinds=(kind for kind in ("acceptance",)),
                )

            self.assertEqual([("acceptance",)], observed)

    def test_single_required_bound_kind_string_is_not_split_into_characters(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "acceptance.json").write_text(
                json.dumps({"candidateSha": CANDIDATE}), encoding="utf-8"
            )
            stable.build_stable_index(
                root=root,
                candidate_sha=CANDIDATE,
                specs=["acceptance=acceptance.json"],
                generated_at_utc=GENERATED_AT,
                output=root / "index.json",
                require_bound_kinds="acceptance",
            )

    def test_noniterable_required_bound_kinds_is_rejected_clearly(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaisesRegex(stable.StableEvidenceIndexError, "iterable of strings"):
                stable.build_stable_index(
                    root=root,
                    candidate_sha=CANDIDATE,
                    specs=[],
                    generated_at_utc=GENERATED_AT,
                    output=root / "index.json",
                    require_bound_kinds=None,
                )

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

    @unittest.skipUnless(hasattr(os, "symlink"), "symlink support required")
    def test_rejects_leaf_symlink_introduced_after_component_check(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            evidence = root / "acceptance.json"
            evidence.write_text("{}", encoding="utf-8")
            outside = root / "outside.json"
            outside.write_text("{}", encoding="utf-8")
            original_open_directory = stable.open_directory_descriptor
            swapped = False

            def check_then_swap(path, label="directory"):
                nonlocal swapped
                descriptor = original_open_directory(path, label)
                if Path(path) == root and not swapped:
                    swapped = True
                    evidence.unlink()
                    evidence.symlink_to(outside)
                return descriptor

            with mock.patch.object(
                stable,
                "open_directory_descriptor",
                side_effect=check_then_swap,
            ):
                with self.assertRaisesRegex(
                    stable.StableEvidenceIndexError,
                    "must not traverse a symbolic link",
                ):
                    stable._read_snapshot(root, evidence.name)
            self.assertTrue(swapped)
            self.assertEqual("{}", outside.read_text(encoding="utf-8"))

    def test_rejects_regular_leaf_replacement_between_preopen_stat_and_open(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            evidence = root / "acceptance.json"
            evidence.write_bytes(b"original")
            original_open = stable.os.open
            replaced = False

            def replace_before_open(path, flags, *args, **kwargs):
                nonlocal replaced
                if path == evidence.name and kwargs.get("dir_fd") is not None and not replaced:
                    replaced = True
                    evidence.rename(root / "original.json")
                    evidence.write_bytes(b"replacement")
                return original_open(path, flags, *args, **kwargs)

            with mock.patch.object(stable.os, "open", side_effect=replace_before_open):
                with self.assertRaisesRegex(
                    stable.StableEvidenceIndexError,
                    "changed before snapshot",
                ):
                    stable._read_snapshot(root, evidence.name)
            self.assertTrue(replaced)

    def test_rejects_empty_oversized_source_and_existing_snapshot_leaf(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            evidence = root / "acceptance.json"
            evidence.write_bytes(b"")
            with self.assertRaisesRegex(stable.StableEvidenceIndexError, "size is outside"):
                stable._read_snapshot(root, evidence.name)

            evidence.write_bytes(b"larger than limit")
            with mock.patch.object(stable.builder, "MAX_FILE_BYTES", 2):
                with self.assertRaisesRegex(stable.StableEvidenceIndexError, "size is outside"):
                    stable._read_snapshot(root, evidence.name)

            evidence.write_bytes(b"valid")
            snapshot = root / "snapshot.json"
            snapshot.write_bytes(b"preserve existing")
            with self.assertRaisesRegex(
                stable.StableEvidenceIndexError,
                "could not create private evidence snapshot",
            ):
                stable._read_snapshot(root, evidence.name, snapshot)
            self.assertEqual(b"preserve existing", snapshot.read_bytes())

    @unittest.skipUnless(hasattr(os, "symlink"), "symlink support required")
    def test_parent_replaced_by_symlink_after_path_check_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            evidence_parent = root / "evidence"
            evidence_parent.mkdir()
            (evidence_parent / "acceptance.json").write_text(
                json.dumps({"candidateSha": CANDIDATE}),
                encoding="utf-8",
            )
            outside = root / "outside"
            outside.mkdir()
            (outside / "acceptance.json").write_text(
                json.dumps({"candidateSha": CANDIDATE}),
                encoding="utf-8",
            )
            moved_parent = root / "evidence-moved"
            original_check = stable._assert_no_symlink_components

            def check_then_swap(parent, relative):
                checked = original_check(parent, relative)
                evidence_parent.rename(moved_parent)
                evidence_parent.symlink_to(outside, target_is_directory=True)
                return checked

            with mock.patch.object(
                stable,
                "_assert_no_symlink_components",
                side_effect=check_then_swap,
            ):
                with self.assertRaisesRegex(
                    stable.StableEvidenceIndexError,
                    "symbolic link",
                ):
                    stable._read_snapshot(root, "evidence/acceptance.json")

    @unittest.skipUnless(hasattr(os, "mkfifo"), "FIFO support required")
    def test_rejects_fifo_evidence_without_opening_it(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fifo = root / "acceptance.json"
            os.mkfifo(fifo)
            output = root / "release-evidence-index.json"

            with mock.patch.object(stable.os, "open", wraps=stable.os.open) as opened:
                with self.assertRaisesRegex(
                    stable.StableEvidenceIndexError,
                    "not a regular file",
                ):
                    stable.build_stable_index(
                        root=root,
                        candidate_sha=CANDIDATE,
                        specs=["acceptance=acceptance.json"],
                        generated_at_utc=GENERATED_AT,
                        output=output,
                    )
                self.assertFalse(
                    any(call.args[0] == fifo.name for call in opened.call_args_list),
                    "the FIFO itself must not be opened",
                )
            self.assertFalse(output.exists())

    @unittest.skipUnless(hasattr(os, "O_NONBLOCK"), "nonblocking open required")
    def test_regular_evidence_open_is_nonblocking_against_fifo_replacement(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            evidence = root / "acceptance.json"
            evidence.write_text(
                json.dumps({"candidateSha": CANDIDATE}),
                encoding="utf-8",
            )
            original_open = stable.os.open
            with mock.patch.object(stable.os, "open", wraps=original_open) as opened:
                stable.build_stable_index(
                    root=root,
                    candidate_sha=CANDIDATE,
                    specs=["acceptance=acceptance.json"],
                    generated_at_utc=GENERATED_AT,
                    output=root / "release-evidence-index.json",
                )
            evidence_opens = [
                call for call in opened.call_args_list
                if Path(call.args[0]).name == evidence.name
                and call.kwargs.get("dir_fd") is not None
            ]
            self.assertGreaterEqual(len(evidence_opens), 1)
            for call in evidence_opens:
                self.assertTrue(call.args[1] & os.O_NONBLOCK)

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

    def test_strict_snapshot_builder_rejects_duplicate_json_keys_before_publication(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            evidence = root / "acceptance.json"
            evidence.write_text(
                '{"candidateSha":"%s","candidateSha":"%s"}'
                % (CANDIDATE, CANDIDATE),
                encoding="utf-8",
            )
            output = root / "release-evidence-index.json"

            # Strict JSON validation now runs in the snapshot builder, before
            # publication. This is stronger than waiting for the verifier.
            with self.assertRaisesRegex(
                stable.builder.EvidenceIndexError,
                "duplicate JSON object key",
            ):
                stable.build_stable_index(
                    root=root,
                    candidate_sha=CANDIDATE,
                    specs=["acceptance=acceptance.json"],
                    generated_at_utc=GENERATED_AT,
                    output=output,
                )
            self.assertFalse(output.exists())

    def test_independent_verification_failure_removes_published_output(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            evidence = root / "acceptance.json"
            evidence.write_text(
                json.dumps({"candidateSha": CANDIDATE}),
                encoding="utf-8",
            )
            output = root / "release-evidence-index.json"

            with mock.patch.object(
                stable.verifier,
                "verify_index",
                side_effect=stable.verifier.EvidenceIndexVerificationError(
                    "independent mismatch"
                ),
            ):
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

    def test_verification_failure_reports_when_published_output_cannot_be_removed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            evidence = root / "acceptance.json"
            evidence.write_text(
                json.dumps({"candidateSha": CANDIDATE}),
                encoding="utf-8",
            )
            output = root / "release-evidence-index.json"
            original_unlink = stable.os.unlink

            def fail_output_unlink(path, *args, **kwargs):
                if isinstance(path, str) and path.endswith(".cleanup"):
                    raise PermissionError("injected cleanup denial")
                return original_unlink(path, *args, **kwargs)

            with (
                mock.patch.object(
                    stable.verifier,
                    "verify_index",
                    side_effect=stable.verifier.EvidenceIndexVerificationError(
                        "injected mismatch"
                    ),
                ),
                mock.patch.object(stable.os, "unlink", side_effect=fail_output_unlink),
            ):
                with self.assertRaisesRegex(
                    stable.StableEvidenceIndexError,
                    "could not remove published output safely: injected cleanup denial",
                ):
                    stable.build_stable_index(
                        root=root,
                        candidate_sha=CANDIDATE,
                        specs=["acceptance=acceptance.json"],
                        generated_at_utc=GENERATED_AT,
                        output=output,
                    )

            self.assertTrue(output.is_file())

    def test_verification_failure_cleanup_preserves_replacement_after_identity_check(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            evidence = root / "acceptance.json"
            evidence.write_text(json.dumps({"candidateSha": CANDIDATE}), encoding="utf-8")
            output = root / "release-evidence-index.json"
            saved = root / "published-index-saved.json"
            original_stat = stable.os.stat
            verification_failed = False
            swapped = False

            def fail_verification(*args, **kwargs):
                nonlocal verification_failed
                verification_failed = True
                raise stable.verifier.EvidenceIndexVerificationError("injected mismatch")

            def stat_then_replace(path, *args, **kwargs):
                nonlocal swapped
                result = original_stat(path, *args, **kwargs)
                if (
                    verification_failed
                    and path == output.name
                    and kwargs.get("dir_fd") is not None
                    and not swapped
                ):
                    swapped = True
                    os.rename(output, saved)
                    descriptor = os.open(
                        output.name,
                        os.O_WRONLY | os.O_CREAT | os.O_EXCL,
                        0o600,
                        dir_fd=kwargs["dir_fd"],
                    )
                    try:
                        os.write(descriptor, b"concurrent replacement")
                    finally:
                        os.close(descriptor)
                return result

            with (
                mock.patch.object(stable.verifier, "verify_index", side_effect=fail_verification),
                mock.patch.object(stable.os, "stat", side_effect=stat_then_replace),
            ):
                with self.assertRaisesRegex(
                    stable.StableEvidenceIndexError,
                    "independent verification failed.*concurrent entry restored",
                ):
                    stable.build_stable_index(
                        root=root,
                        candidate_sha=CANDIDATE,
                        specs=["acceptance=acceptance.json"],
                        generated_at_utc=GENERATED_AT,
                        output=output,
                    )

            self.assertTrue(swapped)
            self.assertTrue(saved.is_file())
            self.assertEqual("concurrent replacement", output.read_text(encoding="utf-8"))

    def test_verification_failure_cleanup_does_not_follow_swapped_output_parent(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            workspace = Path(directory)
            root = workspace / "evidence-root"
            root.mkdir()
            evidence = root / "acceptance.json"
            evidence.write_text(json.dumps({"candidateSha": CANDIDATE}), encoding="utf-8")
            output = root / "release-evidence-index.json"
            moved_root = workspace / "evidence-root-moved"
            external = workspace / "external"
            external.mkdir()
            protected = external / output.name
            protected.write_text("preserve external file", encoding="utf-8")

            def swap_parent_and_fail(*args, **kwargs):
                root.rename(moved_root)
                root.symlink_to(external, target_is_directory=True)
                raise stable.verifier.EvidenceIndexVerificationError("injected mismatch")

            with mock.patch.object(
                stable.verifier,
                "verify_index",
                side_effect=swap_parent_and_fail,
            ):
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

            self.assertEqual("preserve external file", protected.read_text(encoding="utf-8"))
            self.assertTrue(root.is_symlink())

    def test_rejects_output_replacement_during_independent_verification(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            evidence = root / "acceptance.json"
            evidence.write_text(json.dumps({"candidateSha": CANDIDATE}), encoding="utf-8")
            output = root / "release-evidence-index.json"
            original_verify = stable.verifier.verify_index

            def verify_then_replace(index_path, **kwargs):
                summary = original_verify(index_path, **kwargs)
                replacement = root / "replacement.json"
                replacement.write_text("replacement", encoding="utf-8")
                os.replace(replacement, index_path)
                return summary

            with mock.patch.object(
                stable.verifier, "verify_index", side_effect=verify_then_replace
            ):
                with self.assertRaisesRegex(
                    stable.StableEvidenceIndexError,
                    "changed during independent verification",
                ):
                    stable.build_stable_index(
                        root=root,
                        candidate_sha=CANDIDATE,
                        specs=["acceptance=acceptance.json"],
                        generated_at_utc=GENERATED_AT,
                        output=output,
                    )

            self.assertEqual("replacement", output.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
