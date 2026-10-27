import json
import os
import tempfile
import unittest
from unittest import mock

import strict_json
from pathlib import Path

from strict_json import StrictJsonError, load_file, loads
from verify_strict_json_evidence import (
    EvidenceJsonPreflightError,
    expand_paths,
    verify_json_evidence,
)


class StrictJsonTest(unittest.TestCase):
    def test_valid_object_and_array_are_accepted(self) -> None:
        self.assertEqual({"a": [1, True, None]}, loads(b'{"a":[1,true,null]}'))
        self.assertEqual([1, 2], loads("[1,2]"))

    def test_matching_object_bytes_must_equal_the_validated_object(self) -> None:
        self.assertEqual(
            {"candidate": "abc"},
            strict_json.loads_matching_object(
                b'{"candidate":"abc"}',
                {"candidate": "abc"},
                label="manifest",
            ),
        )
        with self.assertRaisesRegex(StrictJsonError, "do not encode the supplied object"):
            strict_json.loads_matching_object(
                b"{}",
                {"candidate": "abc"},
                label="manifest",
            )
        with self.assertRaisesRegex(StrictJsonError, "do not encode the supplied object"):
            strict_json.loads_matching_object(
                b'{"value":true}',
                {"value": 1},
                label="manifest",
            )

    def test_duplicate_keys_and_non_finite_constants_are_rejected(self) -> None:
        with self.assertRaisesRegex(StrictJsonError, "duplicate JSON object key"):
            loads('{"candidate":1,"candidate":2}')
        for constant in ("NaN", "Infinity", "-Infinity"):
            with self.subTest(constant=constant):
                with self.assertRaisesRegex(StrictJsonError, "non-finite"):
                    loads(f'{{"value":{constant}}}')

    def test_bom_invalid_utf8_empty_and_oversized_inputs_are_rejected(self) -> None:
        cases = (
            (b"\xef\xbb\xbf{}", "BOM"),
            (b"\xff", "UTF-8"),
            (b"", "between"),
        )
        for raw, message in cases:
            with self.subTest(message=message):
                with self.assertRaisesRegex(StrictJsonError, message):
                    loads(raw)
        with self.assertRaisesRegex(StrictJsonError, "between"):
            loads("{}", maximum_bytes=1)
        with self.assertRaisesRegex(StrictJsonError, "UTF-8 text"):
            loads('{"value":"\ud800"}')

    def test_depth_and_required_object_are_enforced(self) -> None:
        nested = "[" * 10 + "0" + "]" * 10
        with self.assertRaisesRegex(StrictJsonError, "nesting"):
            loads(nested, maximum_depth=5)
        with self.assertRaisesRegex(StrictJsonError, "JSON object"):
            loads("[]", require_object=True)

    def test_file_reader_rejects_missing_non_regular_and_non_object_files(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaisesRegex(StrictJsonError, "missing"):
                load_file(root / "missing.json")
            with self.assertRaisesRegex(StrictJsonError, "not a regular file"):
                load_file(root)
            path = root / "array.json"
            path.write_text("[]", encoding="utf-8")
            with self.assertRaisesRegex(StrictJsonError, "JSON object"):
                load_file(path, require_object=True)


    @unittest.skipUnless(hasattr(os, "mkfifo"), "FIFO support required")
    def test_file_reader_rejects_fifo_without_opening(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory, "fifo.json")
            os.mkfifo(path)
            with mock.patch.object(strict_json.os, "open", wraps=strict_json.os.open) as opened:
                with self.assertRaisesRegex(StrictJsonError, "not a regular file"):
                    load_file(path)
            self.assertFalse(
                any(call.args[0] == path.name for call in opened.call_args_list),
                "the FIFO leaf must be rejected before opening it",
            )

    @unittest.skipUnless(hasattr(os, "O_NONBLOCK"), "nonblocking open required")
    def test_file_reader_uses_checked_nonblocking_descriptor(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory, "evidence.json")
            path.write_text('{"candidateSha":"ok"}', encoding="utf-8")
            with mock.patch.object(strict_json.os, "open", wraps=strict_json.os.open) as opened:
                self.assertEqual({"candidateSha": "ok"}, load_file(path))
            calls = [call for call in opened.call_args_list if call.args[0] == path.name]
            self.assertEqual(1, len(calls))
            self.assertTrue(calls[0].args[1] & os.O_NONBLOCK)
            if hasattr(os, "O_NOFOLLOW"):
                self.assertTrue(calls[0].args[1] & os.O_NOFOLLOW)

    def test_file_reader_rejects_parent_swap_to_external_symlink(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            workspace = Path(directory)
            root = workspace / "root"
            root.mkdir()
            path = root / "evidence.json"
            path.write_text("{}", encoding="utf-8")
            saved_root = workspace / "root.saved"
            external = workspace / "external"
            external.mkdir()
            (external / "evidence.json").write_text("{}", encoding="utf-8")
            original_open = os.open
            swapped = False

            def swap_then_open(value, flags, *args, **kwargs):
                nonlocal swapped
                if value == root.name and kwargs.get("dir_fd") is not None and not swapped:
                    root.rename(saved_root)
                    root.symlink_to(external, target_is_directory=True)
                    swapped = True
                return original_open(value, flags, *args, **kwargs)

            with mock.patch.object(strict_json.os, "open", side_effect=swap_then_open):
                with self.assertRaises(StrictJsonError):
                    load_file(path)
            self.assertTrue(swapped)
            self.assertTrue(root.is_symlink())


class StrictJsonEvidencePreflightTest(unittest.TestCase):
    def test_directory_expansion_is_recursive_deduplicated_and_sorted(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            nested = root / "nested"
            nested.mkdir()
            first = root / "a.json"
            second = nested / "b.json"
            ignored = nested / "note.txt"
            first.write_text("{}", encoding="utf-8")
            second.write_text('{"ok":true}', encoding="utf-8")
            ignored.write_text("not JSON", encoding="utf-8")

            paths = verify_json_evidence([root, first])

            self.assertEqual((first.resolve(), second.resolve()), paths)

    def test_preflight_rejects_ambiguous_file_and_non_json_argument(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            ambiguous = root / "bad.json"
            ambiguous.write_text('{"a":1,"a":2}', encoding="utf-8")
            with self.assertRaisesRegex(EvidenceJsonPreflightError, "duplicate"):
                verify_json_evidence([ambiguous])

            text = root / "note.txt"
            text.write_text("{}", encoding="utf-8")
            with self.assertRaisesRegex(EvidenceJsonPreflightError, "not a JSON"):
                expand_paths([text])

    @unittest.skipUnless(hasattr(os, "symlink"), "symbolic links required")
    def test_preflight_rejects_direct_json_symlink_instead_of_resolving_target(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / "target.json"
            link = root / "evidence.json"
            target.write_text('{"trusted":true}', encoding="utf-8")
            link.symlink_to(target)

            with self.assertRaisesRegex(EvidenceJsonPreflightError, "symbolic link"):
                verify_json_evidence([link])

    @unittest.skipUnless(hasattr(os, "symlink"), "symbolic links required")
    def test_directory_preflight_preserves_symlinked_json_child_identity(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            external = root / "external.json"
            nested = root / "nested"
            nested.mkdir()
            external.write_text('{"trusted":true}', encoding="utf-8")
            (nested / "linked.json").symlink_to(external)

            with self.assertRaisesRegex(EvidenceJsonPreflightError, "symbolic link"):
                verify_json_evidence([nested])

    @unittest.skipUnless(hasattr(os, "symlink"), "symbolic links required")
    def test_preflight_rejects_json_path_beneath_symlinked_parent(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            external = root / "external"
            external.mkdir()
            (external / "evidence.json").write_text("{}", encoding="utf-8")
            alias = root / "alias"
            alias.symlink_to(external, target_is_directory=True)

            with self.assertRaisesRegex(EvidenceJsonPreflightError, "symbolic link"):
                verify_json_evidence([alias / "evidence.json"])

    def test_directory_expansion_stops_at_file_limit_plus_one(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            files = []
            for index in range(5):
                path = root / f"{index}.json"
                path.write_text("{}", encoding="utf-8")
                files.append(path)
            yielded = []

            def counted_rglob(_path, _pattern):
                for path in files:
                    yielded.append(path)
                    yield path

            with mock.patch("verify_strict_json_evidence.MAX_FILES", 2), \
                 mock.patch.object(Path, "rglob", counted_rglob):
                with self.assertRaisesRegex(EvidenceJsonPreflightError, "2-file safety limit"):
                    expand_paths([root])
            self.assertEqual(files[:3], yielded)

    def test_empty_directory_and_missing_paths_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaisesRegex(EvidenceJsonPreflightError, "no JSON"):
                verify_json_evidence([root])
            with self.assertRaisesRegex(EvidenceJsonPreflightError, "does not exist"):
                verify_json_evidence([root / "missing"])

    def test_normal_generated_json_round_trips(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory, "manifest.json")
            payload = {"schemaVersion": 1, "files": [{"name": "a"}]}
            path.write_text(json.dumps(payload), encoding="utf-8")
            self.assertEqual((path.resolve(),), verify_json_evidence([path]))


if __name__ == "__main__":
    unittest.main()
