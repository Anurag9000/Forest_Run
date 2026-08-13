from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from PIL import Image

import compare_visual_regression as visual


class VisualRegressionTrustedRootTest(unittest.TestCase):
    def test_regular_file_inside_regular_root_is_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "root"
            root.mkdir()
            image = root / "frame.png"
            Image.new("RGB", (4, 4), (1, 2, 3)).save(image, format="PNG")
            self.assertEqual(
                image,
                visual._trusted_regular_file(root, image, "candidate screenshot"),
            )

    def test_lexical_escape_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            root = base / "root"
            root.mkdir()
            outside = base / "outside.png"
            Image.new("RGB", (4, 4), (1, 2, 3)).save(outside, format="PNG")
            with self.assertRaisesRegex(
                visual.VisualRegressionError,
                "escapes its trusted screenshot root",
            ):
                visual._trusted_regular_file(
                    root,
                    root / ".." / "outside.png",
                    "candidate screenshot",
                )


if __name__ == "__main__":
    unittest.main()
