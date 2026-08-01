import json
import tempfile
import unittest
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
