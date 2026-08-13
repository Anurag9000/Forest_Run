from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from PIL import Image

import compare_visual_regression as visual


class VisualRegressionSymlinkRootTest(unittest.TestCase):
    def test_symlink_root_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            real_root = base / "real"
            real_root.mkdir()
            image = real_root / "frame.png"
            Image.new("RGB", (4, 4), (1, 2, 3)).save(image, format="PNG")
            alias = base / "alias"
            try:
                alias.symlink_to("real", target_is_directory=True)
            except (OSError, NotImplementedError) as exc:
                self.skipTest(f"symbolic links are unavailable: {exc}")
            with self.assertRaisesRegex(
                visual.VisualRegressionError,
                "trusted screenshot root",
            ):
                visual._trusted_regular_file(
                    alias,
                    alias / "frame.png",
                    "baseline screenshot",
                )


if __name__ == "__main__":
    unittest.main()
