from __future__ import annotations

import os
import tempfile
import unittest
import os
from pathlib import Path
from unittest.mock import patch

from PIL import Image

import generate_store_assets as generator
import stable_file_reader as stable_files


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

    def test_real_asset_pipeline_renders_and_verifies_candidate_graphics(self) -> None:
        generator.validate_inputs = self.original_validate
        generator.source_evidence = self.original_sources
        generator.draw_feature_graphic = self.original_feature
        generator.draw_promo_square = self.original_promo

        manifest = generator.generate_store_assets(self.output, "a" * 40)

        self.assertEqual("a" * 40, manifest["candidateSha"])
        self.assertEqual(
            {"feature-graphic.png", "promo-square.png", "graphics_manifest.json"},
            {path.name for path in self.output.iterdir()},
        )
        with Image.open(self.output / "feature-graphic.png") as feature:
            self.assertEqual((1024, 500), feature.size)
            self.assertEqual("RGB", feature.mode)
        with Image.open(self.output / "promo-square.png") as promo:
            self.assertEqual((512, 512), promo.size)
            self.assertEqual("RGB", promo.mode)

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
        with self.assertRaisesRegex(ValueError, "40-character"):
            generator.generate_store_assets(self.output, ("a" * 40).upper())
        self.assertFalse(self.output.exists())

    def test_interrupted_publication_backup_is_recovered_before_retry(self) -> None:
        staging = self.root / ".graphics.staging"
        backup = self.root / ".graphics.backup"
        staging.mkdir()
        backup.mkdir()
        (staging / "candidate.png").write_bytes(b"candidate")
        (backup / "previously-accepted.png").write_bytes(b"accepted")
        original_rename = stable_files._rename_noreplace

        def fail_candidate_publish(source, destination, *args, **kwargs):
            if source == staging.name and destination == self.output.name:
                raise OSError("injected graphics publication failure")
            return original_rename(source, destination, **kwargs)

        with patch.object(stable_files, "_rename_noreplace", fail_candidate_publish):
            with self.assertRaisesRegex(RuntimeError, "injected graphics publication failure"):
                generator.publish_directory(staging, self.output, backup)

        self.assertEqual(
            b"accepted",
            (self.output / "previously-accepted.png").read_bytes(),
        )
        self.assertFalse(backup.exists())

    def test_existing_output_and_backup_fail_closed_without_deleting_either(self) -> None:
        backup = self.root / ".graphics.backup"
        self.output.mkdir()
        backup.mkdir()
        (self.output / "current.png").write_bytes(b"current")
        (backup / "previous.png").write_bytes(b"previous")
        staging = self.root / ".graphics.staging"
        staging.mkdir()

        with self.assertRaisesRegex(ValueError, "backup already exists"):
            generator.publish_directory(staging, self.output, backup)

        self.assertEqual(b"current", (self.output / "current.png").read_bytes())
        self.assertEqual(b"previous", (backup / "previous.png").read_bytes())

    def test_output_symlink_is_rejected_before_resolution(self) -> None:
        target = self.root / "actual-output"
        target.mkdir()
        output_link = self.root / "graphics-link"
        output_link.symlink_to(target, target_is_directory=True)
        self.install_successful_drawers()

        with self.assertRaisesRegex(ValueError, "must not be a symlink"):
            generator.generate_store_assets(output_link, "a" * 40)

        self.assertEqual([], list(target.iterdir()))

    def test_non_directory_backup_is_preserved_and_rejected(self) -> None:
        staging = self.root / ".graphics.staging"
        backup = self.root / ".graphics.backup"
        staging.mkdir()
        backup.write_bytes(b"preserve malformed backup")

        with self.assertRaisesRegex(ValueError, "publication paths must be directories"):
            generator.publish_directory(staging, self.output, backup)

        self.assertEqual(b"preserve malformed backup", backup.read_bytes())
        self.assertTrue(staging.is_dir())

    def test_directory_publisher_rolls_back_if_parent_swaps_during_publish(self) -> None:
        parent = self.root / "parent"
        parent.mkdir()
        output = parent / "graphics"
        staging = parent / ".graphics.staging"
        backup = parent / ".graphics.backup"
        staging.mkdir()
        output.mkdir()
        (staging / "candidate.png").write_bytes(b"candidate")
        (output / "accepted.png").write_bytes(b"accepted")
        moved = self.root / "parent-moved"
        external = self.root / "external"
        external.mkdir()
        real_rename = os.rename
        real_noreplace = stable_files._rename_noreplace
        swapped = False

        def swap_parent_before_stage_rename(source, destination, *args, **kwargs):
            nonlocal swapped
            if source == staging.name and not swapped:
                swapped = True
                real_rename(parent, moved)
                parent.symlink_to(external, target_is_directory=True)
            return real_noreplace(source, destination, **kwargs)

        with patch.object(stable_files, "_rename_noreplace", swap_parent_before_stage_rename):
            with self.assertRaisesRegex(RuntimeError, "parent changed"):
                generator.publish_directory(staging, output, backup)

        self.assertTrue(swapped)
        self.assertTrue(parent.is_symlink())
        self.assertEqual(b"accepted", (moved / "graphics" / "accepted.png").read_bytes())
        self.assertEqual(b"candidate", (moved / ".graphics.staging" / "candidate.png").read_bytes())
        self.assertEqual([], list(external.iterdir()))

    def test_backup_cleanup_failure_reports_published_graphics_and_backup_path(self) -> None:
        staging = self.root / ".graphics.staging"
        backup = self.root / ".graphics.backup"
        staging.mkdir()
        self.output.mkdir()
        (staging / "candidate.png").write_bytes(b"candidate")
        (self.output / "accepted.png").write_bytes(b"accepted")

        with patch.object(
            stable_files,
            "remove_directory_at",
            side_effect=OSError("injected graphics backup cleanup failure"),
        ):
            with self.assertRaisesRegex(
                RuntimeError,
                "was published but backup cleanup failed",
            ) as raised:
                generator.publish_directory(staging, self.output, backup)

        self.assertEqual(b"candidate", (self.output / "candidate.png").read_bytes())
        recovery = Path(str(raised.exception).split("partial backup may remain at ", 1)[1])
        self.assertEqual(b"accepted", (recovery / "accepted.png").read_bytes())

    def test_generation_staging_remains_anchored_if_parent_swaps_during_render(self) -> None:
        parent = self.root / "parent"
        parent.mkdir()
        output = parent / "graphics"
        output.mkdir()
        (output / "accepted.txt").write_text("accepted", encoding="utf-8")
        moved = self.root / "parent-moved"
        external = self.root / "external"
        external.mkdir()
        swapped = False

        def draw_feature(path: Path) -> Path:
            nonlocal swapped
            if not swapped:
                parent.rename(moved)
                parent.symlink_to(external, target_is_directory=True)
                swapped = True
            return self.draw(path, "feature-graphic.png", (1024, 500), (20, 80, 40))

        generator.draw_feature_graphic = draw_feature
        generator.draw_promo_square = lambda path: self.draw(
            path, "promo-square.png", (512, 512), (80, 30, 90)
        )
        with self.assertRaisesRegex(RuntimeError, "symbolic link or non-directory"):
            generator.generate_store_assets(output, "a" * 40)

        self.assertTrue(swapped)
        self.assertTrue(parent.is_symlink())
        self.assertEqual("accepted", (moved / "graphics" / "accepted.txt").read_text())
        self.assertFalse((moved / ".graphics.staging").exists())
        self.assertEqual([], list(external.iterdir()))

    def test_manifest_writer_rejects_parent_swap_without_external_write(self) -> None:
        parent = self.root / "assets"
        parent.mkdir()
        output = parent / "graphics_manifest.json"
        moved = self.root / "assets-moved"
        external = self.root / "external"
        external.mkdir()
        real_open = os.open
        swapped = False

        def swap_parent_before_open(value, flags, *args, **kwargs):
            nonlocal swapped
            if value == "assets" and kwargs.get("dir_fd") is not None and not swapped:
                parent.rename(moved)
                parent.symlink_to(external, target_is_directory=True)
                swapped = True
            return real_open(value, flags, *args, **kwargs)

        with patch.object(stable_files.os, "open", side_effect=swap_parent_before_open):
            with self.assertRaises(stable_files.StableFileError):
                generator.atomic_write_json(output, {"status": "valid"})

        self.assertTrue(swapped)
        self.assertTrue(parent.is_symlink())
        self.assertFalse((moved / output.name).exists())
        self.assertFalse((external / output.name).exists())


if __name__ == "__main__":
    unittest.main()
