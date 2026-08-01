from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from PIL import Image

import generate_store_assets as generator


class StoreGraphicsGeneratorTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.output = self.root / "graphics"
        self.original_validate = generator.validate_inputs
        self.original_feature = generator.draw_feature_graphic
        self.original_promo = generator.draw_promo_square
        self.original_sources = generator.source_evidence
        generator.validate_inputs = lambda: None
        generator.source_evidence = lambda: [
            {"path": "source", "bytes": 1, "sha256": "b" * 64}
        ]

    def tearDown(self) -> None:
        generator.validate_inputs = self.original_validate
        generator.draw_feature_graphic = self.original_feature
        generator.draw_promo_square = self.original_promo
        generator.source_evidence = self.original_sources
        self.temp.cleanup()

    @staticmethod
    def draw(path: Path, name: str, size: tuple[int, int], colour) -> Path:
        destination = path / name
        Image.new("RGB", size, colour).save(destination, format="PNG")
        return destination

    def install_successful_drawers(self) -> None:
        generator.draw_feature_graphic = lambda path: self.draw(
            path, "feature-graphic.png", (1024, 500), (20, 80, 40)
        )
        generator.draw_promo_square = lambda path: self.draw(
            path, "promo-square.png", (512, 512), (80, 30, 90)
        )

    def test_generation_publishes_exact_candidate_bound_set(self) -> None:
        self.install_successful_drawers()

        manifest = generator.generate_store_assets(self.output, "a" * 40)

        self.assertEqual("a" * 40, manifest["candidateSha"])
        self.assertEqual(1, manifest["schemaVersion"])
        self.assertEqual(
            {"feature-graphic.png", "promo-square.png", "graphics_manifest.json"},
            {path.name for path in self.output.iterdir()},
        )
        self.assertFalse((self.root / ".graphics.staging").exists())
        self.assertFalse((self.root / ".graphics.backup").exists())

    def test_failed_generation_preserves_previous_accepted_directory(self) -> None:
        self.output.mkdir()
        (self.output / "accepted.txt").write_text("accepted", encoding="utf-8")
        generator.draw_feature_graphic = lambda path: self.draw(
            path, "feature-graphic.png", (1024, 500), (20, 80, 40)
        )

        def fail(_path):
            raise RuntimeError("forced generation failure")

        generator.draw_promo_square = fail

        with self.assertRaisesRegex(RuntimeError, "forced generation failure"):
            generator.generate_store_assets(self.output, "a" * 40)

        self.assertEqual(
            "accepted",
            (self.output / "accepted.txt").read_text(encoding="utf-8"),
        )
        self.assertFalse((self.root / ".graphics.staging").exists())
        self.assertFalse((self.root / ".graphics.backup").exists())

    def test_invalid_candidate_is_rejected_before_publication(self) -> None:
        self.install_successful_drawers()
        with self.assertRaisesRegex(ValueError, "40-character"):
            generator.generate_store_assets(self.output, "short")
        self.assertFalse(self.output.exists())


if __name__ == "__main__":
    unittest.main()
