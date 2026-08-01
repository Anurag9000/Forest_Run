from __future__ import annotations

import struct
import tempfile
import unittest
import zlib
from pathlib import Path

from verify_release_source_assets import (
    SourceAssetVerificationError,
    _ogg_crc,
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

    @staticmethod
    def png_chunk(chunk_type: bytes, data: bytes) -> bytes:
        checksum = zlib.crc32(chunk_type)
        checksum = zlib.crc32(data, checksum) & 0xFFFFFFFF
        return (
            struct.pack(">I", len(data))
            + chunk_type
            + data
            + struct.pack(">I", checksum)
        )

    def valid_png(self, width: int = 8, height: int = 4) -> bytes:
        raw = (b"\x00" + b"\x20\x80\xd0\xff" * width) * height
        return (
            b"\x89PNG\r\n\x1a\n"
            + self.png_chunk(
                b"IHDR",
                struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0),
            )
            + self.png_chunk(b"IDAT", zlib.compress(raw, level=9))
            + self.png_chunk(b"IEND", b"")
        )

    @staticmethod
    def valid_font() -> bytes:
        table_data = {
            b"cmap": b"\x00\x00\x00\x01",
            b"head": b"\x00" * 12 + struct.pack(">I", 0x5F0F3CF5) + b"\x00" * 38,
            b"hhea": b"\x00" * 36,
            b"hmtx": b"\x00\x01\x00\x00",
            b"maxp": b"\x00\x01\x00\x00\x00\x01",
            b"name": b"\x00\x00\x00\x01\x00\x12",
        }
        num_tables = len(table_data)
        header = b"\x00\x01\x00\x00" + struct.pack(">HHHH", num_tables, 0, 0, 0)
        directory = bytearray()
        payload = bytearray()
        offset = 12 + num_tables * 16
        for tag, data in table_data.items():
            directory.extend(struct.pack(">4sIII", tag, 0, offset, len(data)))
            payload.extend(data)
            offset += len(data)
        return header + bytes(directory) + bytes(payload)

    @staticmethod
    def valid_ogg(packet: bytes | None = None) -> bytes:
        packet = packet or (b"\x01vorbis" + b"\x00" * 23)
        if len(packet) > 254:
            raise AssertionError("test packet must fit one Ogg segment")
        page = bytearray(
            b"OggS"
            + b"\x00"
            + b"\x06"
            + struct.pack("<Q", 0)
            + struct.pack("<I", 7)
            + struct.pack("<I", 0)
            + b"\x00\x00\x00\x00"
            + b"\x01"
            + bytes([len(packet)])
            + packet
        )
        page[22:26] = struct.pack("<I", _ogg_crc(page))
        return bytes(page)

    @staticmethod
    def valid_wav() -> bytes:
        fmt = struct.pack("<HHIIHH", 1, 1, 8_000, 8_000, 1, 8)
        data = b"\x80" * 8
        body = (
            b"WAVE"
            + b"fmt "
            + struct.pack("<I", len(fmt))
            + fmt
            + b"data"
            + struct.pack("<I", len(data))
            + data
        )
        return b"RIFF" + struct.pack("<I", len(body)) + body

    @staticmethod
    def valid_mp3() -> bytes:
        # MPEG-1 Layer III, 128 kbps, 44.1 kHz, stereo frame header.
        return b"\xff\xfb\x90\x64" + b"\x00" * 64

    @staticmethod
    def box(box_type: bytes, payload: bytes) -> bytes:
        return struct.pack(">I", 8 + len(payload)) + box_type + payload

    def valid_m4a(self) -> bytes:
        return (
            self.box(b"ftyp", b"M4A \x00\x00\x02\x00M4A isom")
            + self.box(b"moov", b"metadata")
            + self.box(b"mdat", b"audio")
        )

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
        image.write_bytes(self.valid_png())
        font.write_bytes(self.valid_font())
        (self.raw_root / "sfx_jump.ogg").write_bytes(self.valid_ogg())

    def test_valid_fixture_is_accepted(self) -> None:
        self.write_valid_fixture()

        evidence = verify_release_source_assets(self.root)

        self.assertEqual(2, evidence.asset_count)
        self.assertEqual(1, evidence.png_count)
        self.assertEqual(1, evidence.font_count)
        self.assertEqual(1, evidence.required_audio_count)
        self.assertEqual(1, evidence.checked_audio_count)

    def test_header_only_and_wrong_signature_files_are_rejected(self) -> None:
        self.write_valid_fixture()
        (self.raw_root / "sfx_jump.ogg").write_bytes(b"OggSfixture")
        with self.assertRaisesRegex(SourceAssetVerificationError, "truncated first page"):
            verify_release_source_assets(self.root)

        (self.raw_root / "sfx_jump.ogg").write_bytes(b"not-an-ogg")
        with self.assertRaisesRegex(SourceAssetVerificationError, "invalid or truncated"):
            verify_release_source_assets(self.root)

        (self.raw_root / "sfx_jump.ogg").write_bytes(self.valid_ogg())
        (self.assets_root / "sprites/test.png").write_bytes(
            b"\x89PNG\r\n\x1a\nfixture"
        )
        with self.assertRaisesRegex(SourceAssetVerificationError, "truncated chunk"):
            verify_release_source_assets(self.root)

        (self.assets_root / "sprites/test.png").write_bytes(self.valid_png())
        (self.assets_root / "fonts/test.ttf").write_bytes(
            b"\x00\x01\x00\x00fixture"
        )
        with self.assertRaisesRegex(SourceAssetVerificationError, "table count|offset table"):
            verify_release_source_assets(self.root)

    def test_png_crc_zlib_geometry_and_frame_count_are_validated(self) -> None:
        self.write_valid_fixture()
        image = self.assets_root / "sprites/test.png"
        corrupt = bytearray(image.read_bytes())
        corrupt[corrupt.index(b"IDAT") + 5] ^= 0x01
        image.write_bytes(corrupt)
        with self.assertRaisesRegex(SourceAssetVerificationError, "CRC mismatch"):
            verify_release_source_assets(self.root)

        image.write_bytes(self.valid_png(width=10, height=4))
        self.asset_source.write_text(
            'object AssetPaths {\n'
            '  const val IMAGE = "sprites/test_4frames.png"\n'
            '  const val FONT = "fonts/test.ttf"\n'
            '}\n',
            encoding="utf-8",
        )
        image.rename(self.assets_root / "sprites/test_4frames.png")
        with self.assertRaisesRegex(SourceAssetVerificationError, "not divisible"):
            verify_release_source_assets(self.root)

    def test_font_table_bounds_and_required_tables_are_validated(self) -> None:
        self.write_valid_fixture()
        font = self.assets_root / "fonts/test.ttf"
        content = bytearray(font.read_bytes())
        # Move the first table outside the file.
        content[20:24] = struct.pack(">I", len(content) + 100)
        font.write_bytes(content)
        with self.assertRaisesRegex(SourceAssetVerificationError, "outside the file"):
            verify_release_source_assets(self.root)

        font.write_bytes(self.valid_font().replace(b"cmap", b"post", 1))
        with self.assertRaisesRegex(SourceAssetVerificationError, "missing required tables"):
            verify_release_source_assets(self.root)

    def test_ogg_checksum_codec_sequence_and_eos_are_validated(self) -> None:
        self.write_valid_fixture()
        audio = self.raw_root / "sfx_jump.ogg"
        corrupt = bytearray(audio.read_bytes())
        corrupt[-1] ^= 0x01
        audio.write_bytes(corrupt)
        with self.assertRaisesRegex(SourceAssetVerificationError, "checksum mismatch"):
            verify_release_source_assets(self.root)

        audio.write_bytes(self.valid_ogg(packet=b"not-a-codec"))
        with self.assertRaisesRegex(SourceAssetVerificationError, "Vorbis or Opus"):
            verify_release_source_assets(self.root)

        no_eos = bytearray(self.valid_ogg())
        no_eos[5] = 0x02
        no_eos[22:26] = b"\x00\x00\x00\x00"
        no_eos[22:26] = struct.pack("<I", _ogg_crc(no_eos))
        audio.write_bytes(no_eos)
        with self.assertRaisesRegex(SourceAssetVerificationError, "missing an EOS"):
            verify_release_source_assets(self.root)

    def test_supported_wav_mp3_and_m4a_containers_are_structurally_checked(self) -> None:
        fixtures = {
            ".wav": self.valid_wav(),
            ".mp3": self.valid_mp3(),
            ".m4a": self.valid_m4a(),
        }
        for suffix, content in fixtures.items():
            with self.subTest(suffix=suffix):
                self.write_valid_fixture()
                ogg = self.raw_root / "sfx_jump.ogg"
                ogg.unlink()
                (self.raw_root / f"sfx_jump{suffix}").write_bytes(content)
                evidence = verify_release_source_assets(self.root)
                self.assertEqual(1, evidence.checked_audio_count)
                for path in self.raw_root.iterdir():
                    path.unlink()

    def test_duplicate_required_audio_stems_are_rejected(self) -> None:
        self.write_valid_fixture()
        (self.raw_root / "sfx_jump.wav").write_bytes(self.valid_wav())

        with self.assertRaisesRegex(SourceAssetVerificationError, "Duplicate raw audio"):
            verify_release_source_assets(self.root)

    def test_unsafe_and_duplicate_asset_paths_are_rejected(self) -> None:
        for path in ("../secret.png", "C:/secret.png", "sprites\\bad.png", "bad\x01.png"):
            with self.subTest(path=path):
                with self.assertRaisesRegex(SourceAssetVerificationError, "unsafe"):
                    parse_asset_paths(f'const val BAD = "{path}"')
        with self.assertRaisesRegex(SourceAssetVerificationError, "duplicate"):
            parse_asset_paths(
                'const val FIRST = "sprites/a.png"\n'
                'const val SECOND = "sprites/a.png"\n'
            )

    def test_checked_in_repository_assets_pass_source_contract(self) -> None:
        evidence = verify_release_source_assets(ROOT)

        self.assertEqual(25, evidence.asset_count)
        self.assertEqual(24, evidence.png_count)
        self.assertEqual(1, evidence.font_count)
        self.assertEqual(18, evidence.required_audio_count)
        self.assertEqual(18, evidence.checked_audio_count)

    def test_main_release_wrapper_runs_asset_preflight_before_preparer(self) -> None:
        wrapper = (ROOT / "scripts/prepare_main_release.sh").read_text(
            encoding="utf-8"
        )

        verifier_index = wrapper.index("verify_release_source_assets.py")
        preparer_index = wrapper.index("prepare_play_release.py")
        self.assertLess(verifier_index, preparer_index)
        self.assertIn('--root "${ROOT}"', wrapper[verifier_index:preparer_index])


if __name__ == "__main__":
    unittest.main()
