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

    def test_frame_index_cleanup_removes_only_detached_atlas_annotations(self) -> None:
        character = Image.new("RGBA", (80, 106), (0, 0, 0, 0))
        for x in range(30, 55):
            for y in range(30, 90):
                character.putpixel((x, y), (18, 52, 86, 255))
        for x in range(4, 10):
            for y in range(3, 13):
                character.putpixel((x, y), (18, 35, 90, 255))
        # Colored, detached atlas text must be removed as well as grayscale text.
        character.putpixel((72, 3), (28, 44, 100, 255))
        for x in range(62, 68):
            character.putpixel((x, 105), (34, 46, 90, 255))
        for x in range(4, 18):
            character.putpixel((x, 52), (38, 46, 105, 255))
        for y in range(60, 67):
            character.putpixel((79, y), (38, 46, 105, 255))
        character.putpixel((72, 72), (150, 150, 150, 255))

        cleaned_character = importer.remove_frame_index_annotations(character, "top_index")

        self.assertEqual(character.size, cleaned_character.size)
        self.assertEqual((0, 0, 0, 0), cleaned_character.getpixel((6, 8)))
        self.assertEqual((0, 0, 0, 0), cleaned_character.getpixel((72, 3)))
        self.assertEqual((0, 0, 0, 0), cleaned_character.getpixel((64, 105)))
        self.assertEqual((0, 0, 0, 0), cleaned_character.getpixel((10, 52)))
        self.assertEqual((0, 0, 0, 0), cleaned_character.getpixel((79, 63)))
        self.assertEqual((18, 52, 86, 255), cleaned_character.getpixel((40, 50)))
        self.assertEqual((150, 150, 150, 255), cleaned_character.getpixel((72, 72)))

        plant = Image.new("RGBA", (160, 640), (0, 0, 0, 0))
        for x in range(65, 96):
            for y in range(370, 420):
                plant.putpixel((x, y), (24, 96, 48, 255))
        for x in range(74, 82):
            for y in range(440, 450):
                plant.putpixel((x, y), (0, 0, 0, 255))

        cleaned_plant = importer.remove_frame_index_annotations(plant, "bottom_index")

        self.assertEqual(plant.size, cleaned_plant.size)
        self.assertEqual((0, 0, 0, 0), cleaned_plant.getpixel((76, 444)))
        self.assertEqual((24, 96, 48, 255), cleaned_plant.getpixel((80, 400)))

        with self.assertRaisesRegex(ValueError, "Unsupported frame-index annotation policy"):
            importer.remove_frame_index_annotations(plant, "unknown")

    def test_current_numbered_atlases_import_without_index_glyph_components(self) -> None:
        indexed_sources = {
            "char/runner_girl_jump_48frame.png",
            "char/runner_girl_duck_48frame.png",
            "plants/hyacinth/hyacinth_4frames.png",
            "plants/vanilla/vanilla_4frames.png",
        }
        mappings = tuple(
            mapping
            for mapping in importer.asset_mappings()
            if mapping.src_rel in indexed_sources
        )
        self.assertEqual(indexed_sources, {mapping.src_rel for mapping in mappings})
        for mapping in mappings:
            with self.subTest(source=mapping.src_rel):
                frames = importer.slice_grid(mapping)
                self.assertEqual(mapping.count, len(frames))
                for frame in frames:
                    components = importer.connected_components(frame)
                    for component, bbox in components:
                        if mapping.annotation_policy == "top_index":
                            annotation = importer._is_top_index_annotation(frame, component, bbox)
                        else:
                            annotation = importer._is_bottom_index_annotation(frame, component, bbox)
                        self.assertFalse(annotation, (mapping.src_rel, bbox))

    def test_player_atlas_grid_matches_authored_source_layout(self) -> None:
        mappings = {mapping.src_rel: mapping for mapping in importer.asset_mappings()}
        expected = {
            "char/runner_girl_technical_48frame.png": (8, 6, 48),
            "char/runner_girl_jump_48frame.png": (8, 6, 42),
            # The legacy-named source is visibly and structurally a 6x6 sheet.
            "char/runner_girl_duck_48frame.png": (6, 6, 36),
        }
        for source, (cols, rows, count) in expected.items():
            with self.subTest(source=source):
                mapping = mappings[source]
                self.assertEqual((cols, rows, count), (mapping.cols, mapping.rows, mapping.count))
                with Image.open(importer.SRC / source) as atlas:
                    column_widths = [
                        ((column + 1) * atlas.width // mapping.cols)
                        - (column * atlas.width // mapping.cols)
                        for column in range(mapping.cols)
                    ]
                    row_heights = [
                        ((row + 1) * atlas.height // mapping.rows)
                        - (row * atlas.height // mapping.rows)
                        for row in range(mapping.rows)
                    ]
                    self.assertLessEqual(max(column_widths) - min(column_widths), 1)
                    self.assertLessEqual(max(row_heights) - min(row_heights), 1)

        jump = mappings["char/runner_girl_jump_48frame.png"]
        self.assertEqual((6, 6, 6, 8, 8, 8), jump.row_columns)
        self.assertEqual("char/runner_girl_jump_42frame.png", jump.dst_rel)
        jump_frames = importer.slice_grid(jump)
        self.assertEqual(42, len(jump_frames))
        self.assertTrue(all(frame.getbbox() is not None for frame in jump_frames))
        self.assertTrue(all(sum(pixel[3] > 0 for pixel in importer._rgba_pixels(frame)) > 500
                            for frame in jump_frames))

        duck = mappings["char/runner_girl_duck_48frame.png"]
        self.assertEqual("char/runner_girl_duck_36frame.png", duck.dst_rel)
        duck_frames = importer.slice_grid(duck)
        self.assertEqual(36, len(duck_frames))
        self.assertTrue(all(frame.getbbox() is not None for frame in duck_frames))
        self.assertTrue(all(sum(pixel[3] > 0 for pixel in importer._rgba_pixels(frame)) > 500
                            for frame in duck_frames))

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
                self.assertEqual(
                    3,
                    len(importer.slice_grid(
                        importer.Mapping("sheet.png", "out.png", 2, 2, 3, row_columns=(2, 1))
                    )),
                )
                with self.assertRaisesRegex(ValueError, "one column count per row"):
                    importer.slice_grid(
                        importer.Mapping("sheet.png", "out.png", 2, 2, 2, row_columns=(2,))
                    )
                with self.assertRaisesRegex(ValueError, "sum to the declared frame count"):
                    importer.slice_grid(
                        importer.Mapping("sheet.png", "out.png", 2, 2, 3, row_columns=(1, 1))
                    )
                with self.assertRaisesRegex(ValueError, "maximum columns"):
                    importer.slice_grid(
                        importer.Mapping("sheet.png", "out.png", 1, 2, 3, row_columns=(2, 1))
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
