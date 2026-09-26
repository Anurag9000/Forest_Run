from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import prepare_play_release as preparer


class PlayPreparerGraphicsManifestTest(unittest.TestCase):
    def test_strict_manifest_admission_and_canonical_output_names(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            directory = Path(temporary_directory)
            manifest = directory / "graphics_manifest.json"
            expected = {
                "feature-graphic.png": "a" * 64,
                "promo-square.png": "b" * 64,
            }
            facts = [
                {"path": name, "sha256": digest}
                for name, digest in expected.items()
            ]
            original = json.dumps({"outputs": [
                {"file": name, "sha256": digest}
                for name, digest in expected.items()
            ]})

            def inspect(path, _dimensions):
                return next(fact for fact in facts if fact["path"] == path.name)

            with patch.object(preparer, "GRAPHICS_DIR", directory), patch.object(
                preparer, "inspect_png", side_effect=inspect
            ):
                manifest.write_text(original, encoding="utf-8")
                self.assertEqual(facts, preparer.verify_graphics())

                for malformed, error in (
                    (
                        original.replace(
                            '"outputs":', '"extra": 1, "extra": 2, "outputs":', 1
                        ),
                        "duplicate JSON object key",
                    ),
                    (
                        original.replace(
                            '"outputs":', '"extra": Infinity, "outputs":', 1
                        ),
                        "non-finite",
                    ),
                    (" " * (preparer.MAX_GRAPHICS_MANIFEST_BYTES + 1), "between"),
                ):
                    with self.subTest(error=error):
                        manifest.write_text(malformed, encoding="utf-8")
                        with self.assertRaisesRegex(SystemExit, error):
                            preparer.verify_graphics()

                manifest.write_text(json.dumps({"outputs": [
                    {"file": "feature-graphic.png", "sha256": "a" * 64},
                    {"file": "feature-graphic.png", "sha256": "b" * 64},
                ]}), encoding="utf-8")
                with self.assertRaisesRegex(SystemExit, "duplicate output"):
                    preparer.verify_graphics()

                manifest.write_text(json.dumps({"outputs": [
                    {"file": "feature-graphic.png", "sha256": "a" * 64},
                    {"file": "other.png", "sha256": "b" * 64},
                ]}), encoding="utf-8")
                with self.assertRaisesRegex(SystemExit, "required pair"):
                    preparer.verify_graphics()


if __name__ == "__main__":
    unittest.main()
