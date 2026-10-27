from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from PIL import Image

import import_final_assets as importer


class ImportFinalAssetsTest(unittest.TestCase):
    def test_quantization_background_matching_and_alpha_crop(self) -> None:
        self.assertEqual((15, 0, 8), importer.quantize((255, 15, 128, 255)))
        palette = {(1, 2, 3)}
        self.assertTrue(importer.near_any((32, 48, 64, 255), palette))
        self.assertFalse(importer.near_any((32, 48, 64, 100), palette))

        image = Image.new("RGBA", (20, 20), (0, 0, 0, 0))
        image.putpixel((10, 9), (20, 80, 160, 255))
        cropped = importer.crop_alpha(image, pad=2)
        self.assertEqual((5, 5), cropped.size)
        self.assertEqual("RGBA", cropped.mode)
        self.assertEqual((20, 80, 160, 255), cropped.getpixel((2, 2)))

    def test_flood_fill_removes_only_connected_boundary_background(self) -> None:
        image = Image.new("RGBA", (7, 7), (0, 0, 0, 255))
        image.putpixel((3, 3), (255, 255, 255, 255))

        cleaned = importer.flood_fill_background(image)

        self.assertEqual(0, cleaned.getpixel((0, 0))[3])
        self.assertEqual((255, 255, 255, 255), cleaned.getpixel((3, 3)))

    def test_components_are_four_connected_and_subject_filter_keeps_nearby_subject_parts(self) -> None:
        image = Image.new("RGBA", (50, 20), (0, 0, 0, 0))
        image.putpixel((1, 1), (255, 0, 0, 255))
        image.putpixel((2, 2), (0, 255, 0, 255))
        components = importer.connected_components(image)
        self.assertEqual(2, len(components))
        self.assertEqual({(1, 1, 2, 2), (2, 2, 3, 3)}, {bbox for _, bbox in components})

        subject = Image.new("RGBA", (50, 20), (0, 0, 0, 0))
        for x in range(2, 7):
            for y in range(6, 11):
                subject.putpixel((x, y), (20, 40, 80, 255))
        for x in range(12, 14):
            for y in range(7, 9):
                subject.putpixel((x, y), (80, 40, 20, 255))
        subject.putpixel((40, 1), (255, 255, 0, 255))

        filtered = importer.keep_subject_components(subject, component_floor_ratio=0.2)
        self.assertNotEqual(0, filtered.getpixel((3, 5))[3])
        self.assertNotEqual(0, filtered.getpixel((12, 6))[3])
        self.assertLess(filtered.width, 30)

    def test_bottom_band_and_character_background_policies(self) -> None:
        image = Image.new("RGBA", (8, 10), (210, 210, 210, 255))
        image.putpixel((3, 3), (140, 20, 30, 255))
        self.assertIs(image, importer.clear_bottom_band(image, 0.0))
        cleared = importer.clear_bottom_band(image, 0.3)
        self.assertEqual(0, cleared.getpixel((3, 8))[3])
        self.assertEqual(255, cleared.getpixel((3, 3))[3])

        character = importer.strip_light_neutral_background(image)
        self.assertEqual(0, character.getpixel((0, 0))[3])
        self.assertNotEqual(0, character.getpixel((2, 2))[3])

    def test_grid_slicing_validates_geometry_and_indices(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            sheet = Image.new("RGBA", (4, 2), (0, 0, 0, 0))
            colors = ((255, 0, 0, 255), (0, 255, 0, 255), (0, 0, 255, 255), (255, 255, 0, 255))
            for index, color in enumerate(colors):
                x = (index % 2) * 2
                y = (index // 2)
                for px in range(x, x + 2):
                    sheet.putpixel((px, y), color)
            sheet.save(root / "sheet.png")

            mapping = importer.Mapping("sheet.png", "out.png", 2, 2, 2, explicit_indices=(3, 0))
            with patch.object(importer, "SRC", root), patch.object(
                importer, "process_frame", side_effect=lambda frame, **_kwargs: frame.copy()
            ):
                frames = importer.slice_grid(mapping)
                self.assertEqual((255, 255, 0, 255), frames[0].getpixel((0, 0)))
                self.assertEqual((255, 0, 0, 255), frames[1].getpixel((0, 0)))

                with self.assertRaisesRegex(ValueError, "outside the source grid"):
                    importer.slice_grid(
                        importer.Mapping("sheet.png", "out.png", 2, 2, 1, explicit_indices=(4,))
                    )
                with self.assertRaisesRegex(ValueError, "indices must be unique"):
                    importer.slice_grid(
                        importer.Mapping("sheet.png", "out.png", 2, 2, 2, explicit_indices=(1, 1))
                    )
                with self.assertRaisesRegex(ValueError, "positive"):
                    importer.slice_grid(importer.Mapping("sheet.png", "out.png", 0, 2, 1))
                with self.assertRaisesRegex(ValueError, "Expected 2 frame indices"):
                    importer.slice_grid(
                        importer.Mapping("sheet.png", "out.png", 2, 2, 2, explicit_indices=(1,))
                    )
                with self.assertRaisesRegex(ValueError, "Unsupported sprite cleanup strategy"):
                    importer.slice_grid(
                        importer.Mapping("sheet.png", "out.png", 2, 2, 1, strategy="unknown")
                    )
                with self.assertRaisesRegex(ValueError, "remove_support_ratio"):
                    importer.slice_grid(
                        importer.Mapping("sheet.png", "out.png", 2, 2, 1, remove_support_ratio=1.0)
                    )
                with self.assertRaisesRegex(ValueError, "component_floor_ratio"):
                    importer.slice_grid(
                        importer.Mapping("sheet.png", "out.png", 2, 2, 1, component_floor_ratio=-0.1)
                    )

                Image.new("RGBA", (5, 2), (1, 2, 3, 255)).save(root / "uneven-grid.png")
                uneven = importer.slice_grid(
                    importer.Mapping("uneven-grid.png", "out.png", 2, 1, 2)
                )
                self.assertEqual([2, 3], [frame.width for frame in uneven])
                self.assertEqual(5, sum(frame.width for frame in uneven))
                with self.assertRaisesRegex(ValueError, "exceeds sheet dimensions"):
                    importer.slice_grid(
                        importer.Mapping("uneven-grid.png", "out.png", 6, 1, 2)
                    )

    def test_strip_packing_and_import_one_publish_frames(self) -> None:
        frames = [Image.new("RGBA", (2, 2), (255, 0, 0, 255)), Image.new("RGBA", (4, 1), (0, 255, 0, 255))]
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / "strip.png"
            importer.pack_strip(frames, output)
            with Image.open(output) as strip:
                self.assertEqual((8, 2), strip.size)
                self.assertEqual((255, 0, 0, 255), strip.getpixel((1, 0)))
                self.assertEqual((0, 255, 0, 255), strip.getpixel((5, 1)))

            sheet = Image.new("RGBA", (4, 1), (255, 255, 255, 255))
            sheet.putpixel((0, 0), (40, 80, 120, 255))
            sheet.putpixel((2, 0), (120, 80, 40, 255))
            sheet.save(root / "input.png")
            with patch.object(importer, "SRC", root), patch.object(importer, "DST", root / "output"), patch.object(
                importer, "process_frame", side_effect=lambda frame, **_kwargs: frame.copy()
            ):
                importer.import_one(importer.Mapping("input.png", "nested/out.png", 2, 1, 2))
                self.assertTrue((root / "output/nested/out.png").is_file())


if __name__ == "__main__":
    unittest.main()
