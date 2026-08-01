from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

import compile_device_acceptance as compiler
import validate_device_acceptance as acceptance
from test_compile_device_acceptance import draft_bundle, materialize


class CompileDeviceAcceptanceBoundsTest(unittest.TestCase):
    def test_oversized_draft_is_rejected_before_json_parsing(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory, "draft.json")
            with path.open("wb") as stream:
                stream.truncate(acceptance.MAX_MANIFEST_BYTES + 1)

            with self.assertRaisesRegex(compiler.CompilationError, "draft must be between"):
                compiler._read_object(path)

    def test_oversized_artifact_is_rejected_before_streaming_hash(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            draft = draft_bundle()
            materialize(root, draft)
            artifact = root / draft["candidate"]["artifact_path"]
            with artifact.open("r+b") as stream:
                stream.truncate(acceptance.MAX_ARTIFACT_BYTES + 1)

            with self.assertRaisesRegex(compiler.CompilationError, "safety limit"):
                compiler.compile_bundle(draft, base_dir=root)

    def test_oversized_evidence_is_rejected_before_streaming_hash(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            draft = draft_bundle()
            materialize(root, draft)
            relative = next(
                iter(draft["sessions"][0]["scenarios"].values())
            )["evidence_files"][0]
            evidence = root / relative
            with evidence.open("r+b") as stream:
                stream.truncate(acceptance.MAX_EVIDENCE_FILE_BYTES + 1)

            with self.assertRaisesRegex(compiler.CompilationError, "safety limit"):
                compiler.compile_bundle(draft, base_dir=root)

    def test_bounded_draft_reader_still_accepts_normal_json_object(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory, "draft.json")
            path.write_text(json.dumps({"candidate": {}}), encoding="utf-8")
            self.assertEqual({"candidate": {}}, compiler._read_object(path))


if __name__ == "__main__":
    unittest.main()
