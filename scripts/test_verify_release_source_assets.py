from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from verify_release_source_assets import (
    SourceAssetVerificationError,
    parse_asset_paths,
    verify_release_source_assets,
)

ROOT = Path(__file__).resolve().parent.parent


class ReleaseSourceAssetVerifierTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        self.asset_source = (
            self.root
            / "app/src/main/java/com/anurag9000/forestrun/engine/AssetPaths.kt"
        )
        self.assets_root = self.root / "app/src/main/assets"
        self.raw_root = self.root / "app/src/main/res/raw"
        self.preparer = self.root / "scripts/prepare_play_release.py"
        self.asset_source.parent.mkdir(parents=True)
        self.assets_root.mkdir(parents=True)
        self.raw_root.mkdir(parents=True)
        self.preparer.parent.mkdir(parents=True)

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def write_valid_fixture(self) -> None:
        self.asset_source.write_text(
            'object AssetPaths {\n'
            '  const val IMAGE = "sprites/test.png"\n'
            '  const val FONT = "fonts/test.ttf"\n'
            '}\n',
            encoding="utf-8",
        )
        self.preparer.write_text(
            'REQUIRED_AUDIO = ("sfx_jump",)\n',
            encoding="utf-8",
        )
        image = self.assets_root / "sprites/test.png"
        font = self.assets_root / "fonts/test.ttf"
        image.parent.mkdir(parents=True)
        font.parent.mkdir(parents=True)
        image.write_bytes(b"\x89PNG\r\n\x1a\nfixture")
        font.write_bytes(b"\x00\x01\x00\x00fixture")
        (self.raw_root / "sfx_jump.ogg").write_bytes(b"OggSfixture")

    def test_valid_fixture_is_accepted(self) -> None:
        self.write_valid_fixture()

        evidence = verify_release_source_assets(self.root)

        self.assertEqual(2, evidence.asset_count)
        self.assertEqual(1, evidence.png_count)
        self.assertEqual(1, evidence.font_count)
        self.assertEqual(1, evidence.required_audio_count)
        self.assertEqual(1, evidence.checked_audio_count)

    def test_missing_empty_and_wrong_signature_files_are_rejected(self) -> None:
        self.write_valid_fixture()
        (self.raw_root / "sfx_jump.ogg").write_bytes(b"")
        with self.assertRaisesRegex(SourceAssetVerificationError, "Empty raw audio"):
            verify_release_source_assets(self.root)

        (self.raw_root / "sfx_jump.ogg").write_bytes(b"not-an-ogg")
        with self.assertRaisesRegex(SourceAssetVerificationError, "invalid signature"):
            verify_release_source_assets(self.root)

        (self.raw_root / "sfx_jump.ogg").write_bytes(b"OggSfixture")
        (self.assets_root / "sprites/test.png").unlink()
        with self.assertRaisesRegex(SourceAssetVerificationError, "Missing runtime asset"):
            verify_release_source_assets(self.root)

    def test_duplicate_required_audio_stems_are_rejected(self) -> None:
        self.write_valid_fixture()
        (self.raw_root / "sfx_jump.wav").write_bytes(
            b"RIFF\x04\x00\x00\x00WAVEfixture"
        )

        with self.assertRaisesRegex(SourceAssetVerificationError, "Duplicate raw audio"):
            verify_release_source_assets(self.root)

    def test_unsafe_and_duplicate_asset_paths_are_rejected(self) -> None:
        with self.assertRaisesRegex(SourceAssetVerificationError, "unsafe"):
            parse_asset_paths('const val BAD = "../secret.png"')
        with self.assertRaisesRegex(SourceAssetVerificationError, "duplicate"):
            parse_asset_paths(
                'const val FIRST = "sprites/a.png"\n'
                'const val SECOND = "sprites/a.png"\n'
            )

    def test_checked_in_repository_assets_pass_source_contract(self) -> None:
        evidence = verify_release_source_assets(ROOT)

        self.assertGreaterEqual(evidence.asset_count, 30)
        self.assertGreaterEqual(evidence.png_count, 29)
        self.assertGreaterEqual(evidence.font_count, 1)
        self.assertGreaterEqual(evidence.required_audio_count, 15)
        self.assertGreaterEqual(
            evidence.checked_audio_count,
            evidence.required_audio_count,
        )


if __name__ == "__main__":
    unittest.main()
