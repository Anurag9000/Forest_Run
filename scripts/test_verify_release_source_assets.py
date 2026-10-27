from __future__ import annotations

import re
import os
import struct
import tempfile
import unittest
import zlib
from pathlib import Path

from verify_release_source_assets import (
    SourceAssetVerificationError,
    _ogg_crc,
    _read_bounded_file,
    _validate_m4a,
    _validate_mp3,
    _validate_ogg,
    _validate_png,
    _validate_wav,
    _validate_font,
    _validate_cmap_variable_arrays,
    _validate_cmap_format4,
    _validate_cmap_table,
    parse_asset_paths,
    parse_required_audio,
    verify_release_source_assets,
)

ROOT = Path(__file__).resolve().parent.parent


class ReleaseSourceAssetVerifierTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        self.asset_source = self.root / "app/src/main/java/com/anurag9000/forestrun/engine/AssetPaths.kt"
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
        crc = zlib.crc32(chunk_type)
        crc = zlib.crc32(data, crc) & 0xFFFFFFFF
        return struct.pack(">I", len(data)) + chunk_type + data + struct.pack(">I", crc)

    def valid_png(self, width: int = 8, height: int = 4) -> bytes:
        raw = (b"\x00" + b"\x20\x80\xd0\xff" * width) * height
        return (
            b"\x89PNG\r\n\x1a\n"
            + self.png_chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + self.png_chunk(b"IDAT", zlib.compress(raw, level=9))
            + self.png_chunk(b"IEND", b"")
        )

    def indexed_png_with_too_many_palette_entries(self) -> bytes:
        scanline = b"\x00\x00"
        return (
            b"\x89PNG\r\n\x1a\n"
            + self.png_chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 1, 3, 0, 0, 0))
            + self.png_chunk(b"PLTE", b"\x00\x00\x00" * 3)
            + self.png_chunk(b"IDAT", zlib.compress(scanline))
            + self.png_chunk(b"IEND", b"")
        )

    @staticmethod
    def valid_font(
        *, missing_outlines: bool = False, glyph_count: int = 1,
        composite_self_cycle: bool = False, composite_chain_depth: int = 0,
        composite_instruction_flag_early: bool = False,
        loca_format: int = 0,
    ) -> bytes:
        head_table = bytearray(
            b"\x00" * 12 + struct.pack(">I", 0x5F0F3CF5) + b"\x00" * 38
        )
        struct.pack_into(">h", head_table, 50, loca_format)
        table_data = {
            b"cmap": (
                struct.pack(">HHHHI", 0, 1, 3, 1, 12)
                + struct.pack(">HHH", 0, 262, 0)
                + bytes(256)
            ),
            b"head": head_table,
            b"hhea": b"\x00" * 34 + struct.pack(">H", 1),
            b"hmtx": bytes(4 + max(0, glyph_count - 1) * 2),
            b"maxp": struct.pack(">IH", 0x00010000, glyph_count) + bytes(26),
            b"name": (
                struct.pack(">HHH", 0, 1, 18)
                + struct.pack(">HHHHHH", 3, 1, 0, 1, 2, 0)
                + b"\x00A"
            ),
            b"OS/2": bytes(78),
            b"post": struct.pack(">I", 0x00030000) + bytes(28),
        }
        if not missing_outlines:
            if composite_chain_depth:
                if glyph_count != composite_chain_depth + 1:
                    raise ValueError("composite chain requires one terminal simple glyph")
                glyph_records = [
                    struct.pack(">hhhhh", -1, 0, 0, 0, 0)
                    + struct.pack(">HHBB", 0, component + 1, 0, 0)
                    for component in range(composite_chain_depth)
                ]
                glyph_records.append(
                    struct.pack(">hhhhh", 0, 0, 0, 0, 0) + b"\x00\x00"
                )
                table_data[b"glyf"] = b"".join(glyph_records)
                byte_offsets = [0]
                for record in glyph_records:
                    byte_offsets.append(byte_offsets[-1] + len(record))
                loca_offsets = (
                    [offset // 2 for offset in byte_offsets]
                    if loca_format == 0
                    else byte_offsets
                )
            elif composite_instruction_flag_early:
                if glyph_count != 3:
                    raise ValueError("early-instruction composite needs three glyphs")
                composite = (
                    struct.pack(">hhhhh", -1, 0, 0, 0, 0)
                    + struct.pack(">HHBB", 0x0120, 1, 0, 0)
                    + struct.pack(">HHBB", 0, 2, 0, 0)
                    + b"\x00\x00"
                )
                simple = struct.pack(">hhhhh", 0, 0, 0, 0, 0) + b"\x00\x00"
                table_data[b"glyf"] = composite + simple + simple
                byte_offsets = [
                    0,
                    len(composite),
                    len(composite) + len(simple),
                    len(composite) + 2 * len(simple),
                ]
                loca_offsets = (
                    [offset // 2 for offset in byte_offsets]
                    if loca_format == 0
                    else byte_offsets
                )
            elif composite_self_cycle:
                table_data[b"glyf"] = (
                    struct.pack(">hhhhh", -1, 0, 0, 0, 0)
                    + struct.pack(">HHBB", 0, 0, 0, 0)
                )
                loca_offsets = [0, 8]
            else:
                table_data[b"glyf"] = struct.pack(">hhhhh", 0, 0, 0, 0, 0) + b"\x00\x00"
                glyph_data_units = 6
                loca_end = glyph_data_units * (2 if loca_format == 1 else 1)
                loca_offsets = [0, loca_end] + ([loca_end] if glyph_count > 1 else [])
            loca_code = "H" if loca_format == 0 else "I"
            table_data[b"loca"] = struct.pack(f">{len(loca_offsets)}{loca_code}", *loca_offsets)
        table_count = len(table_data)
        power = 1 << (table_count.bit_length() - 1)
        header = b"\x00\x01\x00\x00" + struct.pack(
            ">HHHH", table_count, power * 16, power.bit_length() - 1,
            table_count * 16 - power * 16,
        )
        directory = bytearray()
        payload = bytearray()
        offset = 12 + table_count * 16
        for tag, data in sorted(table_data.items()):
            padding = (-offset) % 4
            payload.extend(b"\x00" * padding)
            offset += padding
            directory.extend(struct.pack(">4sIII", tag, 0, offset, len(data)))
            payload.extend(data)
            offset += len(data)
        font = bytearray(header + directory + payload)
        for index in range(table_count):
            tag = bytes(font[12 + index * 16:16 + index * 16])
            ReleaseSourceAssetVerifierTest.update_font_table_checksum(font, tag)
        return bytes(font)

    @staticmethod
    def update_font_table_checksum(font: bytearray, target_tag: bytes) -> None:
        table_count = struct.unpack(">H", font[4:6])[0]
        for index in range(table_count):
            record_offset = 12 + index * 16
            tag, _, table_offset, table_length = struct.unpack(
                ">4sIII", font[record_offset:record_offset + 16]
            )
            if tag != target_tag:
                continue
            table = bytearray(font[table_offset:table_offset + table_length])
            if tag == b"head":
                table[8:12] = b"\x00\x00\x00\x00"
            table.extend(b"\x00" * ((-len(table)) % 4))
            checksum = sum(word[0] for word in struct.iter_unpack(">I", table)) & 0xFFFFFFFF
            struct.pack_into(">I", font, record_offset + 4, checksum)
            head_offset = next(
                struct.unpack_from(">I", font, 12 + head_index * 16 + 8)[0]
                for head_index in range(table_count)
                if font[12 + head_index * 16 : 16 + head_index * 16] == b"head"
            )
            font[head_offset + 8:head_offset + 12] = b"\x00\x00\x00\x00"
            whole = bytearray(font)
            whole.extend(b"\x00" * ((-len(whole)) % 4))
            whole_checksum = sum(
                word[0] for word in struct.iter_unpack(">I", whole)
            ) & 0xFFFFFFFF
            struct.pack_into(">I", font, head_offset + 8, (0xB1B0AFBA - whole_checksum) & 0xFFFFFFFF)
            return

    @staticmethod
    def valid_ogg(packet: bytes | None = None) -> bytes:
        if packet is None:
            packet = (
                b"\x01vorbis"
                + struct.pack("<I", 0)
                + b"\x01"
                + struct.pack("<I", 8_000)
                + b"\x00" * 12
                + b"\x86\x01"
            )
        page = bytearray(
            b"OggS" + b"\x00\x06" + struct.pack("<QII", 0, 7, 0)
            + b"\x00\x00\x00\x00" + b"\x01" + bytes([len(packet)]) + packet
        )
        page[22:26] = struct.pack("<I", _ogg_crc(page))
        return bytes(page)

    @staticmethod
    def valid_wav(bits: int = 8) -> bytes:
        block_align = (bits + 7) // 8
        fmt = struct.pack(
            "<HHIIHH", 1, 1, 8_000, 8_000 * block_align, block_align, bits
        )
        data = b"\x80" * 8
        body = b"WAVEfmt " + struct.pack("<I", len(fmt)) + fmt + b"data" + struct.pack("<I", len(data)) + data
        return b"RIFF" + struct.pack("<I", len(body)) + body

    @staticmethod
    def valid_mp3() -> bytes:
        return b"\xff\xfb\x90\x64" + b"\x00" * 413

    @staticmethod
    def box(box_type: bytes, payload: bytes) -> bytes:
        return struct.pack(">I", 8 + len(payload)) + box_type + payload

    def valid_m4a(self) -> bytes:
        return self.box(b"ftyp", b"M4A \x00\x00\x02\x00M4A isom") + self.box(b"moov", b"metadata") + self.box(b"mdat", b"audio")

    def write_valid_fixture(self) -> None:
        self.asset_source.write_text(
            'object AssetPaths {\n  const val IMAGE = "sprites/test.png"\n  const val FONT = "fonts/test.ttf"\n}\n',
            encoding="utf-8",
        )
        self.preparer.write_text('REQUIRED_AUDIO = ("sfx_jump",)\n', encoding="utf-8")
        image = self.assets_root / "sprites/test.png"
        font = self.assets_root / "fonts/test.ttf"
        image.parent.mkdir(parents=True, exist_ok=True)
        font.parent.mkdir(parents=True, exist_ok=True)
        image.write_bytes(self.valid_png())
        font.write_bytes(self.valid_font())
        (self.raw_root / "sfx_jump.ogg").write_bytes(self.valid_ogg())

    def test_valid_fixture_is_accepted(self) -> None:
        self.write_valid_fixture()
        evidence = verify_release_source_assets(self.root)
        self.assertEqual((2, 1, 1, 1, 1), (
            evidence.asset_count,
            evidence.png_count,
            evidence.font_count,
            evidence.required_audio_count,
            evidence.checked_audio_count,
        ))

    def test_required_audio_parser_rejects_overridden_or_augmented_assignments(self) -> None:
        for source in (
            'REQUIRED_AUDIO = ("sfx_jump",)\nREQUIRED_AUDIO = ("sfx_other",)\n',
            'REQUIRED_AUDIO = ("sfx_jump",)\nREQUIRED_AUDIO += ("sfx_other",)\n',
            'REQUIRED_AUDIO: tuple[str, ...] = ("sfx_jump",)\n',
            'REQUIRED_AUDIO = ("sfx_jump",)\nmatch candidate:\n    case REQUIRED_AUDIO:\n        pass\n',
            'REQUIRED_AUDIO = ("sfx_jump",)\ntype REQUIRED_AUDIO = tuple[str, ...]\n',
        ):
            with self.subTest(source=source):
                with self.assertRaisesRegex(SourceAssetVerificationError, "define REQUIRED_AUDIO once"):
                    parse_required_audio(source)

    def test_font_offset_table_search_parameters_are_validated(self) -> None:
        font = bytearray(self.valid_font())
        struct.pack_into(">H", font, 6, 0)
        with self.assertRaisesRegex(SourceAssetVerificationError, "search parameters"):
            _validate_font(Path("invalid-offset-table.ttf"), bytes(font))

    def test_cff_font_signature_requires_outline_table(self) -> None:
        font = self.valid_font(missing_outlines=True)
        with self.assertRaisesRegex(SourceAssetVerificationError, "outline tables"):
            _validate_font(Path("missing-outline-tables.ttf"), font)

        font = bytearray(self.valid_font())
        font[:4] = b"OTTO"
        with self.assertRaisesRegex(SourceAssetVerificationError, "CFF runtime font"):
            _validate_font(Path("missing-cff-table.otf"), bytes(font))

    def test_font_os2_and_post_versions_have_required_table_shapes(self) -> None:
        font = bytearray(self.valid_font())
        table_count = struct.unpack_from(">H", font, 4)[0]
        for target, value, message in (
            (b"OS/2", 6, "OS/2 table has an unsupported version or length"),
            (b"post", 0, "post table has an unsupported version or length"),
        ):
            mutated = bytearray(font)
            record = next(
                12 + index * 16
                for index in range(table_count)
                if mutated[12 + index * 16 : 16 + index * 16] == target
            )
            offset = struct.unpack_from(">I", mutated, record + 8)[0]
            if target == b"OS/2":
                struct.pack_into(">H", mutated, offset, value)
            else:
                struct.pack_into(">I", mutated, offset, value)
            self.update_font_table_checksum(mutated, target)
            with self.subTest(table=target):
                with self.assertRaisesRegex(SourceAssetVerificationError, message):
                    _validate_font(Path("invalid-required-table.ttf"), bytes(mutated))

    def test_truetype_maxp_loca_and_glyph_headers_are_validated(self) -> None:
        valid = bytearray(self.valid_font())
        table_count = struct.unpack_from(">H", valid, 4)[0]
        records = {
            bytes(valid[12 + index * 16 : 16 + index * 16]): 12 + index * 16
            for index in range(table_count)
        }

        invalid_maxp = bytearray(valid)
        maxp_offset = struct.unpack_from(">I", valid, records[b"maxp"] + 8)[0]
        struct.pack_into(">I", invalid_maxp, maxp_offset, 0x00005000)
        self.update_font_table_checksum(invalid_maxp, b"maxp")
        with self.assertRaisesRegex(SourceAssetVerificationError, "invalid maxp"):
            _validate_font(Path("invalid-maxp.ttf"), bytes(invalid_maxp))

        invalid_loca = bytearray(valid)
        loca_offset = struct.unpack_from(">I", valid, records[b"loca"] + 8)[0]
        struct.pack_into(">HH", invalid_loca, loca_offset, 5, 0)
        self.update_font_table_checksum(invalid_loca, b"loca")
        with self.assertRaisesRegex(SourceAssetVerificationError, "loca table does not begin"):
            _validate_font(Path("invalid-loca.ttf"), bytes(invalid_loca))

        invalid_glyph = bytearray(valid)
        glyf_offset = struct.unpack_from(">I", valid, records[b"glyf"] + 8)[0]
        struct.pack_into(">hh", invalid_glyph, glyf_offset + 2, 5, 0)
        self.update_font_table_checksum(invalid_glyph, b"glyf")
        with self.assertRaisesRegex(SourceAssetVerificationError, "invalid header"):
            _validate_font(Path("invalid-glyph-header.ttf"), bytes(invalid_glyph))

        with self.assertRaisesRegex(SourceAssetVerificationError, "graph contains a cycle"):
            _validate_font(
                Path("composite-cycle.ttf"),
                self.valid_font(composite_self_cycle=True),
            )
        _validate_font(Path("long-loca.ttf"), self.valid_font(loca_format=1))
        _validate_font(
            Path("deep-composite-chain.ttf"),
            self.valid_font(glyph_count=1201, composite_chain_depth=1200),
        )
        _validate_font(
            Path("early-composite-instructions.ttf"),
            self.valid_font(glyph_count=3, composite_instruction_flag_early=True),
        )

    @unittest.skipUnless(hasattr(os, "mkfifo"), "FIFO support required")
    def test_unrelated_fifo_in_raw_resource_directory_is_rejected(self) -> None:
        self.write_valid_fixture()
        os.mkfifo(self.raw_root / "unexpected.ogg")
        with self.assertRaisesRegex(SourceAssetVerificationError, "regular file"):
            verify_release_source_assets(self.root)

    def test_repository_root_symlink_is_rejected(self) -> None:
        self.write_valid_fixture()
        alias = self.root.parent / (self.root.name + "-source-alias")
        alias.symlink_to(self.root, target_is_directory=True)
        try:
            with self.assertRaisesRegex(SourceAssetVerificationError, "symbolic link"):
                verify_release_source_assets(alias)
        finally:
            alias.unlink()

    def test_header_only_assets_are_rejected(self) -> None:
        self.write_valid_fixture()
        (self.raw_root / "sfx_jump.ogg").write_bytes(b"OggSfixture")
        with self.assertRaisesRegex(SourceAssetVerificationError, "truncated first page"):
            verify_release_source_assets(self.root)
        (self.raw_root / "sfx_jump.ogg").write_bytes(self.valid_ogg())
        (self.assets_root / "sprites/test.png").write_bytes(b"\x89PNG\r\n\x1a\nfixture")
        with self.assertRaisesRegex(SourceAssetVerificationError, "truncated chunk"):
            verify_release_source_assets(self.root)
        (self.assets_root / "sprites/test.png").write_bytes(self.valid_png())
        (self.assets_root / "fonts/test.ttf").write_bytes(b"\x00\x01\x00\x00fixture")
        with self.assertRaisesRegex(SourceAssetVerificationError, "table count|offset table"):
            verify_release_source_assets(self.root)

    def test_png_crc_geometry_and_frame_count_are_validated(self) -> None:
        self.write_valid_fixture()
        image = self.assets_root / "sprites/test.png"
        corrupt = bytearray(image.read_bytes())
        corrupt[corrupt.index(b"IDAT") + 5] ^= 1
        image.write_bytes(corrupt)
        with self.assertRaisesRegex(SourceAssetVerificationError, "CRC mismatch"):
            verify_release_source_assets(self.root)
        image.write_bytes(self.valid_png(width=10))
        self.asset_source.write_text(
            'object AssetPaths {\n  const val IMAGE = "sprites/test_4frames.png"\n  const val FONT = "fonts/test.ttf"\n}\n',
            encoding="utf-8",
        )
        image.rename(self.assets_root / "sprites/test_4frames.png")
        with self.assertRaisesRegex(SourceAssetVerificationError, "not divisible"):
            verify_release_source_assets(self.root)

    def test_png_scanline_filters_and_adam7_geometry_are_validated(self) -> None:
        width, height = 8, 4
        ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
        bad_filter_data = (b"\x05" + b"\x20\x80\xd0\xff" * width) * height
        bad_filter = (
            b"\x89PNG\r\n\x1a\n"
            + self.png_chunk(b"IHDR", ihdr)
            + self.png_chunk(b"IDAT", zlib.compress(bad_filter_data))
            + self.png_chunk(b"IEND", b"")
        )
        with self.assertRaisesRegex(SourceAssetVerificationError, "invalid scanline filter"):
            _validate_png(Path("bad-filter.png"), bad_filter)

        interlaced_ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 1)
        invalid_adam7 = (
            b"\x89PNG\r\n\x1a\n"
            + self.png_chunk(b"IHDR", interlaced_ihdr)
            + self.png_chunk(b"IDAT", zlib.compress((b"\x00" + b"\x00" * 32) * height))
            + self.png_chunk(b"IEND", b"")
        )
        with self.assertRaisesRegex(SourceAssetVerificationError, "IHDR geometry"):
            _validate_png(Path("bad-adam7.png"), invalid_adam7)

        passes = (
            (0, 0, 8, 8), (4, 0, 8, 8), (0, 4, 4, 8), (2, 0, 4, 4),
            (0, 2, 2, 4), (1, 0, 2, 2), (0, 1, 1, 2),
        )
        decoded = bytearray()
        for start_x, start_y, step_x, step_y in passes:
            pass_width = (width - start_x + step_x - 1) // step_x
            pass_height = (height - start_y + step_y - 1) // step_y
            if width <= start_x or height <= start_y:
                continue
            row_bytes = pass_width * 4
            decoded.extend((b"\x00" + b"\x00" * row_bytes) * pass_height)
        valid_adam7 = (
            b"\x89PNG\r\n\x1a\n"
            + self.png_chunk(b"IHDR", interlaced_ihdr)
            + self.png_chunk(b"IDAT", zlib.compress(decoded))
            + self.png_chunk(b"IEND", b"")
        )
        self.assertEqual((width, height), _validate_png(Path("valid-adam7.png"), valid_adam7))

        invalid_reserved_bit = (
            b"\x89PNG\r\n\x1a\n"
            + self.png_chunk(b"IHDR", ihdr)
            + self.png_chunk(b"tesT", b"")
            + self.png_chunk(b"IDAT", zlib.compress((b"\x00" + b"\x00" * 32) * height))
            + self.png_chunk(b"IEND", b"")
        )
        with self.assertRaisesRegex(SourceAssetVerificationError, "reserved chunk-type bit"):
            _validate_png(Path("bad-chunk-type.png"), invalid_reserved_bit)

    def test_indexed_png_palette_cannot_exceed_bit_depth_capacity(self) -> None:
        self.write_valid_fixture()
        (self.assets_root / "sprites/test.png").write_bytes(
            self.indexed_png_with_too_many_palette_entries()
        )

        with self.assertRaisesRegex(SourceAssetVerificationError, "invalid palette"):
            verify_release_source_assets(self.root)

    def test_font_table_bounds_and_required_tables_are_validated(self) -> None:
        self.write_valid_fixture()
        font = self.assets_root / "fonts/test.ttf"
        content = bytearray(font.read_bytes())
        content[20:24] = struct.pack(">I", len(content) + 100)
        font.write_bytes(content)
        with self.assertRaisesRegex(SourceAssetVerificationError, "outside the file"):
            verify_release_source_assets(self.root)
        font.write_bytes(self.valid_font().replace(b"cmap", b"caaa", 1))
        with self.assertRaisesRegex(SourceAssetVerificationError, "missing required tables"):
            verify_release_source_assets(self.root)

    def test_font_table_ranges_must_not_overlap(self) -> None:
        font = bytearray(self.valid_font())
        table_count = struct.unpack_from(">H", font, 4)[0]
        head_record = next(
            12 + index * 16
            for index in range(table_count)
            if font[12 + index * 16:16 + index * 16] == b"head"
        )
        hhea_record = next(
            12 + index * 16
            for index in range(table_count)
            if font[12 + index * 16:16 + index * 16] == b"hhea"
        )
        hhea_offset = struct.unpack_from(">I", font, hhea_record + 8)[0]
        hhea_data = bytes(font[hhea_offset : hhea_offset + 36])
        overlapping_offset = struct.unpack_from(">I", font, head_record + 8)[0] + 52
        font[overlapping_offset : overlapping_offset + len(hhea_data)] = hhea_data
        struct.pack_into(">I", font, hhea_record + 8, overlapping_offset)
        self.update_font_table_checksum(font, b"hhea")

        with self.assertRaisesRegex(SourceAssetVerificationError, "overlap"):
            _validate_font(Path("overlapping-tables.ttf"), bytes(font))

    def test_font_directory_order_and_table_alignment_are_validated(self) -> None:
        self.write_valid_fixture()
        font = self.assets_root / "fonts/test.ttf"
        content = bytearray(self.valid_font())
        first_offset = struct.unpack(">I", content[20:24])[0]
        content[20:24] = struct.pack(">I", first_offset + 1)
        font.write_bytes(content)
        with self.assertRaisesRegex(SourceAssetVerificationError, "four-byte aligned"):
            verify_release_source_assets(self.root)

        content = bytearray(self.valid_font())
        first_tag = bytes(content[12:16])
        second_tag = bytes(content[28:32])
        content[12:16] = second_tag
        content[28:32] = first_tag
        font.write_bytes(content)
        with self.assertRaisesRegex(SourceAssetVerificationError, "not sorted by tag"):
            verify_release_source_assets(self.root)

    def test_font_horizontal_metrics_match_glyph_count(self) -> None:
        self.write_valid_fixture()
        font_path = self.assets_root / "fonts/test.ttf"
        valid = bytearray(self.valid_font())

        table_count = struct.unpack_from(">H", valid, 4)[0]
        hhea_record = next(
            12 + index * 16
            for index in range(table_count)
            if valid[12 + index * 16 : 16 + index * 16] == b"hhea"
        )
        hhea_offset = struct.unpack(">I", valid[hhea_record + 8:hhea_record + 12])[0]
        invalid_count = bytearray(valid)
        invalid_count[hhea_offset + 34:hhea_offset + 36] = b"\x00\x00"
        self.update_font_table_checksum(invalid_count, b"hhea")
        font_path.write_bytes(invalid_count)
        with self.assertRaisesRegex(SourceAssetVerificationError, "numberOfHMetrics"):
            verify_release_source_assets(self.root)

        # Two glyphs need one additional left-side-bearing entry in hmtx.
        insufficient_metrics = bytearray(self.valid_font(glyph_count=2))
        hmtx_record = next(
            12 + index * 16
            for index in range(table_count)
            if insufficient_metrics[12 + index * 16 : 16 + index * 16] == b"hmtx"
        )
        struct.pack_into(">I", insufficient_metrics, hmtx_record + 12, 4)
        self.update_font_table_checksum(insufficient_metrics, b"hmtx")
        font_path.write_bytes(insufficient_metrics)
        with self.assertRaisesRegex(SourceAssetVerificationError, "hmtx table is too short"):
            verify_release_source_assets(self.root)

    def test_font_table_checksum_detects_corrupted_table_data(self) -> None:
        font = bytearray(self.valid_font())
        table_count = struct.unpack(">H", font[4:6])[0]
        cmap_offset = None
        for index in range(table_count):
            record_offset = 12 + index * 16
            tag, _, offset, _ = struct.unpack(">4sIII", font[record_offset:record_offset + 16])
            if tag == b"cmap":
                cmap_offset = offset
                break
        self.assertIsNotNone(cmap_offset)
        font[cmap_offset] ^= 1

        with self.assertRaisesRegex(SourceAssetVerificationError, "checksum mismatch"):
            _validate_font(Path("corrupt.ttf"), bytes(font))

    def test_font_checksum_adjustment_is_verified(self) -> None:
        font = bytearray(self.valid_font())
        table_count = struct.unpack(">H", font[4:6])[0]
        head_offset = next(
            struct.unpack_from(">I", font, 12 + index * 16 + 8)[0]
            for index in range(table_count)
            if font[12 + index * 16:16 + index * 16] == b"head"
        )
        font[head_offset + 8] ^= 1

        with self.assertRaisesRegex(SourceAssetVerificationError, "checksum adjustment"):
            _validate_font(Path("corrupt-adjustment.ttf"), bytes(font))

    def test_font_cmap_and_name_record_arrays_must_fit_their_tables(self) -> None:
        for tag, length, message in (
            (b"cmap", 4, "cmap encoding record array is truncated"),
            (b"name", 6, "name record array is truncated"),
        ):
            with self.subTest(tag=tag):
                font = bytearray(self.valid_font())
                table_count = struct.unpack(">H", font[4:6])[0]
                record_offset = next(
                    12 + index * 16
                    for index in range(table_count)
                    if font[12 + index * 16:16 + index * 16] == tag
                )
                struct.pack_into(">I", font, record_offset + 12, length)
                self.update_font_table_checksum(font, tag)
                with self.assertRaisesRegex(SourceAssetVerificationError, message):
                    _validate_font(Path("truncated-records.ttf"), bytes(font))

    def test_font_format4_cmap_requires_a_complete_segment(self) -> None:
        font = bytearray(self.valid_font())
        table_count = struct.unpack_from(">H", font, 4)[0]
        record_offset = next(
            12 + index * 16
            for index in range(table_count)
            if font[12 + index * 16:16 + index * 16] == b"cmap"
        )
        cmap_offset = struct.unpack_from(">I", font, record_offset + 8)[0]
        struct.pack_into(">I", font, record_offset + 12, 28)
        font[cmap_offset:cmap_offset + 28] = (
            struct.pack(">HHHHI", 0, 1, 3, 1, 12)
            + struct.pack(">HHHHHHH", 4, 16, 0, 0, 0, 0, 0)
            + b"\x00\x00"
        )
        self.update_font_table_checksum(font, b"cmap")

        with self.assertRaisesRegex(SourceAssetVerificationError, "cmap"):
            _validate_font(Path("invalid-format4.ttf"), bytes(font))

    def test_font_format6_cmap_glyph_array_must_match_entry_count(self) -> None:
        font = bytearray(self.valid_font())
        table_count = struct.unpack_from(">H", font, 4)[0]
        record_offset = next(
            12 + index * 16
            for index in range(table_count)
            if font[12 + index * 16:16 + index * 16] == b"cmap"
        )
        cmap_offset = struct.unpack_from(">I", font, record_offset + 8)[0]
        struct.pack_into(">I", font, record_offset + 12, 22)
        font[cmap_offset:cmap_offset + 22] = (
            struct.pack(">HHHHI", 0, 1, 3, 1, 12)
            + struct.pack(">HHHHH", 6, 10, 0, 0, 1)
        )
        self.update_font_table_checksum(font, b"cmap")

        with self.assertRaisesRegex(SourceAssetVerificationError, "format 6"):
            _validate_font(Path("invalid-format6.ttf"), bytes(font))

    def test_font_format14_cmap_offsets_must_reference_bounded_tables(self) -> None:
        font = bytearray(self.valid_font())
        table_count = struct.unpack_from(">H", font, 4)[0]
        record_offset = next(
            12 + index * 16
            for index in range(table_count)
            if font[12 + index * 16:16 + index * 16] == b"cmap"
        )
        cmap_offset = struct.unpack_from(">I", font, record_offset + 8)[0]
        struct.pack_into(">I", font, record_offset + 12, 33)
        font[cmap_offset:cmap_offset + 33] = (
            struct.pack(">HHHHI", 0, 1, 3, 1, 12)
            + struct.pack(">HI", 14, 21)
            + struct.pack(">I", 1)
            + b"\x00\xFE\x0F"
            + struct.pack(">II", 0, 0xFFFFFFFF)
        )
        self.update_font_table_checksum(font, b"cmap")

        with self.assertRaisesRegex(SourceAssetVerificationError, "format 14"):
            _validate_font(Path("invalid-format14.ttf"), bytes(font))

    def test_font_format12_cmap_groups_must_be_sorted_and_nonempty(self) -> None:
        font = bytearray(self.valid_font())
        table_count = struct.unpack_from(">H", font, 4)[0]
        record_offset = next(
            12 + index * 16
            for index in range(table_count)
            if font[12 + index * 16:16 + index * 16] == b"cmap"
        )
        cmap_offset = struct.unpack_from(">I", font, record_offset + 8)[0]
        struct.pack_into(">I", font, record_offset + 12, 40)
        font[cmap_offset:cmap_offset + 40] = (
            struct.pack(">HHHHI", 0, 1, 3, 1, 12)
            + struct.pack(">HHIII", 12, 0, 28, 0, 1)
            + struct.pack(">III", 2, 1, 0)
        )
        self.update_font_table_checksum(font, b"cmap")

        with self.assertRaisesRegex(SourceAssetVerificationError, "format 12 groups"):
            _validate_font(Path("invalid-format12.ttf"), bytes(font))

    def test_format14_default_and_non_default_arrays_are_bounded_and_ordered(self) -> None:
        default_table = struct.pack(">I", 1) + b"\x00\x00\x41\x00"
        non_default_table = struct.pack(">I", 1) + b"\x00\x00\x42\x00\x01"
        records_end = 21
        non_default_offset = records_end + len(default_table)
        subtable_length = non_default_offset + len(non_default_table)
        subtable = (
            struct.pack(">HI", 14, subtable_length)
            + struct.pack(">I", 1)
            + b"\x00\xFE\x0F"
            + struct.pack(">II", records_end, non_default_offset)
            + default_table
            + non_default_table
        )
        _validate_cmap_variable_arrays(Path("valid-format14.ttf"), 14, subtable, 2)

        truncated_default = bytearray(subtable)
        struct.pack_into(">I", truncated_default, records_end, 2)
        with self.assertRaisesRegex(SourceAssetVerificationError, "format 14 default"):
            _validate_cmap_variable_arrays(
                Path("truncated-format14.ttf"), 14, bytes(truncated_default), 2
            )

    def test_font_cmap_rejects_glyph_ids_outside_maxp_glyph_count(self) -> None:
        for format_value in (12, 13):
            with self.subTest(format=format_value):
                font = bytearray(self.valid_font(glyph_count=1))
                table_count = struct.unpack_from(">H", font, 4)[0]
                record_offset = next(
                    12 + index * 16
                    for index in range(table_count)
                    if font[12 + index * 16:16 + index * 16] == b"cmap"
                )
                cmap_offset = struct.unpack_from(">I", font, record_offset + 8)[0]
                cmap = (
                    struct.pack(">HHHHI", 0, 1, 3, 10, 12)
                    + struct.pack(">HHIII", format_value, 0, 28, 0, 1)
                    + struct.pack(">III", 0x20, 0x20, 1)
                )
                struct.pack_into(">I", font, record_offset + 12, len(cmap))
                font[cmap_offset:cmap_offset + len(cmap)] = cmap
                self.update_font_table_checksum(font, b"cmap")

                with self.assertRaisesRegex(
                    SourceAssetVerificationError,
                    f"format {format_value} maps outside maxp glyph count",
                ):
                    _validate_font(Path(f"invalid-format{format_value}.ttf"), bytes(font))

    def test_font_format4_cmap_rejects_glyph_ids_outside_maxp_count(self) -> None:
        subtable = (
            struct.pack(">7H", 4, 32, 0, 4, 4, 1, 0)
            + struct.pack(">2H", 0x20, 0xFFFF)
            + struct.pack(">H", 0)
            + struct.pack(">2H", 0x20, 0xFFFF)
            + struct.pack(">2h", -31, 1)
            + struct.pack(">2H", 0, 0)
        )
        with self.assertRaisesRegex(
            SourceAssetVerificationError,
            "format 4 maps outside maxp glyph count",
        ):
            _validate_cmap_format4(Path("invalid-glyph-id-format4.ttf"), subtable, 1)

    def test_cmap_glyph_bounds_cover_every_supported_subtable_format(self) -> None:
        format4 = (
            struct.pack(">7H", 4, 32, 0, 4, 4, 1, 0)
            + struct.pack(">2H", 0x20, 0xFFFF)
            + struct.pack(">H", 0)
            + struct.pack(">2H", 0x20, 0xFFFF)
            + struct.pack(">2h", -31, 1)
            + struct.pack(">2H", 0, 0)
        )
        format2 = (
            struct.pack(">HHH", 2, 528, 0)
            + bytes(512)
            + struct.pack(">HHhH", 0, 1, 0, 2)
            + struct.pack(">H", 1)
        )
        format6 = struct.pack(">5H", 6, 12, 0, 0x20, 1) + struct.pack(">H", 1)
        format8 = (
            struct.pack(">HHII", 8, 0, 8220, 0)
            + bytes(8192)
            + struct.pack(">I", 1)
            + struct.pack(">III", 0x20, 0x20, 1)
        )
        format10 = struct.pack(">HHIIII", 10, 0, 22, 0, 0x20, 1) + struct.pack(">H", 1)
        format12 = struct.pack(">HHIII", 12, 0, 28, 0, 1) + struct.pack(">III", 0x20, 0x20, 1)
        format13 = struct.pack(">HHIII", 13, 0, 28, 0, 1) + struct.pack(">III", 0x20, 0x20, 1)
        format14 = (
            struct.pack(">HI", 14, 30)
            + struct.pack(">I", 1)
            + b"\x00\xfe\x0f"
            + struct.pack(">II", 0, 21)
            + struct.pack(">I", 1)
            + b"\x00\x00\x20\x00\x01"
        )
        format0 = struct.pack(">HHH", 0, 262, 0) + bytes(255) + b"\x01"
        format_subtables = {
            0: format0,
            2: format2,
            4: format4,
            6: format6,
            8: format8,
            10: format10,
            12: format12,
            13: format13,
            14: format14,
        }

        for format_value, subtable in format_subtables.items():
            with self.subTest(format=format_value):
                platform_id, encoding_id = (0, 5) if format_value == 14 else (3, 10)
                cmap = (
                    struct.pack(">HHHHI", 0, 1, platform_id, encoding_id, 12)
                    + subtable
                )
                with self.assertRaisesRegex(
                    SourceAssetVerificationError,
                    f"format {format_value} maps outside maxp glyph count",
                ):
                    _validate_cmap_table(Path(f"format{format_value}.ttf"), cmap, 1)

    def test_ogg_checksum_codec_and_eos_are_validated(self) -> None:
        self.write_valid_fixture()
        audio = self.raw_root / "sfx_jump.ogg"
        corrupt = bytearray(audio.read_bytes())
        corrupt[-1] ^= 1
        audio.write_bytes(corrupt)
        with self.assertRaisesRegex(SourceAssetVerificationError, "checksum mismatch"):
            verify_release_source_assets(self.root)
        audio.write_bytes(self.valid_ogg(packet=b"not-a-codec"))
        with self.assertRaisesRegex(SourceAssetVerificationError, "Vorbis or Opus"):
            verify_release_source_assets(self.root)
        no_eos = bytearray(self.valid_ogg())
        no_eos[5] = 0x02
        no_eos[22:26] = b"\x00" * 4
        no_eos[22:26] = struct.pack("<I", _ogg_crc(no_eos))
        audio.write_bytes(no_eos)
        with self.assertRaisesRegex(SourceAssetVerificationError, "missing an EOS"):
            verify_release_source_assets(self.root)

    def test_ogg_page_flags_must_match_stream_and_packet_state(self) -> None:
        self.write_valid_fixture()
        audio = self.raw_root / "sfx_jump.ogg"
        for flag, message in (
            (0x80, "reserved page-header flags"),
            (0x07, "does not begin with a BOS page"),
        ):
            with self.subTest(flag=flag):
                page = bytearray(self.valid_ogg())
                page[5] = flag
                page[22:26] = b"\x00" * 4
                page[22:26] = struct.pack("<I", _ogg_crc(page))
                audio.write_bytes(page)

                with self.assertRaisesRegex(SourceAssetVerificationError, message):
                    verify_release_source_assets(self.root)

    def test_ogg_codec_identification_headers_are_complete_and_well_formed(self) -> None:
        path = Path("audio.ogg")
        valid_vorbis = self.valid_ogg()
        _validate_ogg(path, valid_vorbis)
        valid_opus_packet = (
            b"OpusHead"
            + bytes([1, 1])
            + struct.pack("<H", 0)
            + struct.pack("<I", 48_000)
            + struct.pack("<h", 0)
            + bytes([0])
        )
        _validate_ogg(path, self.valid_ogg(packet=valid_opus_packet))

        invalid_packets = (
            (b"\x01vorbis", "Vorbis identification header"),
            (
                b"\x01vorbis" + struct.pack("<I", 0) + b"\x00"
                + struct.pack("<I", 8_000) + b"\x00" * 14,
                "Vorbis identification header",
            ),
            (b"OpusHead", "OpusHead identification header"),
            (
                b"OpusHead" + bytes([1, 0]) + struct.pack("<H", 0)
                + struct.pack("<I", 48_000) + struct.pack("<h", 0) + bytes([0]),
                "OpusHead identification header",
            ),
        )
        for packet, message in invalid_packets:
            with self.subTest(packet=packet[:8]):
                with self.assertRaisesRegex(SourceAssetVerificationError, message):
                    _validate_ogg(path, self.valid_ogg(packet=packet))

    def test_opus_identification_packet_can_continue_across_ogg_pages(self) -> None:
        path = Path("multi-page-opus.ogg")
        packet = (
            b"OpusHead"
            + bytes((1, 255))
            + struct.pack("<HIhB", 0, 48_000, 0, 255)
            + bytes((255, 0))
            + bytes(range(255))
        )
        self.assertEqual(276, len(packet))

        def page(
            flags: int, sequence: int, granule: int, body: bytes, serial: int = 7
        ) -> bytes:
            lacing = bytes((255,)) if len(body) == 255 else bytes((len(body),))
            raw = bytearray(
                b"OggS"
                + bytes((0, flags))
                + struct.pack("<QII", granule, serial, sequence)
                + b"\x00\x00\x00\x00"
                + bytes((len(lacing),))
                + lacing
                + body
            )
            raw[22:26] = struct.pack("<I", _ogg_crc(raw))
            return bytes(raw)

        stream = page(0x02, 0, 0xFFFFFFFFFFFFFFFF, packet[:255]) + page(
            0x05, 1, 0, packet[255:]
        )
        _validate_ogg(path, stream)

    def test_opus_packet_reassembly_ignores_interleaved_logical_streams(self) -> None:
        path = Path("interleaved-opus.ogg")
        packet = (
            b"OpusHead"
            + bytes((1, 255))
            + struct.pack("<HIhB", 0, 48_000, 0, 255)
            + bytes((255, 0))
            + bytes(range(255))
        )

        def page(flags: int, sequence: int, granule: int, body: bytes, serial: int) -> bytes:
            lacing = bytes((len(body),))
            raw = bytearray(
                b"OggS"
                + bytes((0, flags))
                + struct.pack("<QII", granule, serial, sequence)
                + b"\x00\x00\x00\x00"
                + bytes((len(lacing),))
                + lacing
                + body
            )
            raw[22:26] = struct.pack("<I", _ogg_crc(raw))
            return bytes(raw)

        stream = (
            page(0x02, 0, 0xFFFFFFFFFFFFFFFF, packet[:255], 7)
            + page(0x06, 0, 0, b"secondary", 8)
            + page(0x05, 1, 0, packet[255:], 7)
        )
        _validate_ogg(path, stream)

    def test_supported_audio_containers_are_structurally_checked(self) -> None:
        for suffix, content in {
            ".wav": self.valid_wav(),
            ".mp3": self.valid_mp3(),
            ".m4a": self.valid_m4a(),
        }.items():
            with self.subTest(suffix=suffix):
                self.write_valid_fixture()
                (self.raw_root / "sfx_jump.ogg").unlink()
                (self.raw_root / f"sfx_jump{suffix}").write_bytes(content)
                self.assertEqual(1, verify_release_source_assets(self.root).checked_audio_count)
                for path in self.raw_root.iterdir():
                    path.unlink()

    def test_m4a_requires_a_complete_unique_file_type_and_nonempty_media_box(self) -> None:
        path = Path("invalid.m4a")
        invalid_inputs = (
            (self.box(b"ftyp", b"") + self.box(b"moov", b"meta") + self.box(b"mdat", b"audio"), "ftyp"),
            (
                self.box(b"ftyp", b"M4A \x00\x00\x00\x00X")
                + self.box(b"moov", b"meta")
                + self.box(b"mdat", b"audio"),
                "invalid or duplicate ftyp",
            ),
            (self.box(b"ftyp", b"M4A \x00\x00\x00\x00") + self.box(b"moov", b"meta") + self.box(b"mdat", b""), "non-empty mdat"),
            (self.box(b"ftyp", b"M4A \x00\x00\x00\x00") * 2 + self.box(b"moov", b"meta") + self.box(b"mdat", b"audio"), "duplicate ftyp"),
        )
        for content, message in invalid_inputs:
            with self.subTest(message=message):
                with self.assertRaisesRegex(SourceAssetVerificationError, message):
                    _validate_m4a(path, content)

    def test_wav_pcm_rates_alignment_and_extensible_format_bounds_are_validated(self) -> None:
        path = Path("invalid.wav")
        valid = self.valid_wav()
        invalid_rate = bytearray(valid)
        invalid_rate[28:32] = struct.pack("<I", 7_999)
        with self.assertRaisesRegex(SourceAssetVerificationError, "inconsistent PCM"):
            _validate_wav(path, bytes(invalid_rate))

        invalid_alignment = bytearray(valid)
        invalid_alignment[32:34] = struct.pack("<H", 2)
        with self.assertRaisesRegex(SourceAssetVerificationError, "inconsistent PCM"):
            _validate_wav(path, bytes(invalid_alignment))

        extensible_fmt = struct.pack("<HHIIHHH", 0xFFFE, 1, 8_000, 8_000, 1, 8, 22)
        # A declared 22-byte extension requires the full 40-byte extensible fmt chunk.
        extensible_body = b"WAVEfmt " + struct.pack("<I", 18) + extensible_fmt
        extensible_body += b"data" + struct.pack("<I", 8) + b"\x80" * 8
        extensible = b"RIFF" + struct.pack("<I", len(extensible_body)) + extensible_body
        with self.assertRaisesRegex(SourceAssetVerificationError, "extensible fmt chunk is truncated"):
            _validate_wav(path, extensible)

    def test_wav_pcm_data_must_contain_complete_sample_frames(self) -> None:
        path = Path("truncated-frame.wav")
        fmt = struct.pack("<HHIIHH", 1, 1, 8_000, 16_000, 2, 16)
        data = b"\x00"
        body = (
            b"WAVEfmt "
            + struct.pack("<I", len(fmt))
            + fmt
            + b"data"
            + struct.pack("<I", len(data))
            + data
            + b"\x00"  # RIFF pad byte; the audio payload is still a partial sample.
        )
        truncated_frame = b"RIFF" + struct.pack("<I", len(body)) + body

        with self.assertRaisesRegex(SourceAssetVerificationError, "sample frame"):
            _validate_wav(path, truncated_frame)

    def test_wav_pcm_container_width_must_be_byte_aligned(self) -> None:
        with self.assertRaisesRegex(SourceAssetVerificationError, "whole number of bytes"):
            _validate_wav(Path("partial-byte-samples.wav"), self.valid_wav(bits=7))

    def test_wav_extensible_validates_subformat_and_valid_bits(self) -> None:
        path = Path("extensible.wav")

        def make_wav(subformat: bytes, valid_bits: int = 16) -> bytes:
            guid = struct.pack("<IHH8s", 1, 0, 0x0010, b"\x80\x00\x00\xaa\x00\x38\x9b\x71")
            if subformat != b"pcm":
                guid = subformat
            fmt = struct.pack(
                "<HHIIHHHHI", 0xFFFE, 1, 8_000, 16_000, 2, 16, 22, valid_bits, 0
            ) + guid
            data = b"\x00\x00"
            body = b"WAVEfmt " + struct.pack("<I", len(fmt)) + fmt
            body += b"data" + struct.pack("<I", len(data)) + data
            return b"RIFF" + struct.pack("<I", len(body)) + body

        valid = make_wav(b"pcm")
        _validate_wav(path, valid)

        with self.assertRaisesRegex(SourceAssetVerificationError, "subformat"):
            _validate_wav(path, make_wav(b"\x01" * 16))
        with self.assertRaisesRegex(SourceAssetVerificationError, "valid bits"):
            _validate_wav(path, make_wav(b"pcm", valid_bits=17))

    def test_mp3_requires_a_complete_frame_and_rejects_reserved_emphasis(self) -> None:
        path = Path("invalid.mp3")
        with self.assertRaisesRegex(SourceAssetVerificationError, "no complete valid audio frame"):
            _validate_mp3(path, b"\xff\xfb\x90\x64" + b"\x00" * 64)
        reserved_emphasis = b"\xff\xfb\x90\x66" + b"\x00" * 413
        with self.assertRaisesRegex(SourceAssetVerificationError, "no complete valid audio frame"):
            _validate_mp3(path, reserved_emphasis)

    def test_mp3_id3v24_footer_must_match_its_header(self) -> None:
        path = Path("footer.mp3")
        frame = self.valid_mp3()
        tag_body = b"TIT2\x00\x00\x00\x02\x00\x00\x00A"
        tag_size = len(tag_body)
        size = bytes((
            (tag_size >> 21) & 0x7F,
            (tag_size >> 14) & 0x7F,
            (tag_size >> 7) & 0x7F,
            tag_size & 0x7F,
        ))
        header = b"ID3\x04\x00\x10" + size
        footer = b"3DI" + header[3:]
        _validate_mp3(path, header + tag_body + footer + frame)

        invalid_footers = (
            b"BAD" + footer[3:],
            footer[:4] + b"\x01" + footer[5:],
            footer[:5] + b"\x00" + footer[6:],
            footer[:6] + b"\x00\x00\x00\x00",
        )
        for invalid_footer in invalid_footers:
            with self.subTest(footer=invalid_footer):
                with self.assertRaisesRegex(SourceAssetVerificationError, "invalid ID3v2.4 footer"):
                    _validate_mp3(path, header + tag_body + invalid_footer + frame)

        unsupported_footer_header = b"ID3\x03\x00\x10" + size
        with self.assertRaisesRegex(SourceAssetVerificationError, "invalid ID3 version or flags"):
            _validate_mp3(path, unsupported_footer_header + tag_body + footer + frame)

    def test_mp3_standard_header_matrix_accepts_complete_frames(self) -> None:
        mpeg1_bitrates = {
            3: (0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448, 0),
            2: (0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384, 0),
            1: (0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0),
        }
        mpeg2_bitrates = {
            3: (0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256, 0),
            2: (0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0),
            1: (0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0),
        }
        sample_rates = {
            3: (44_100, 48_000, 32_000),
            2: (22_050, 24_000, 16_000),
            0: (11_025, 12_000, 8_000),
        }
        path = Path("header-matrix.mp3")
        for version in (3, 2, 0):
            for layer in (3, 2, 1):
                bitrates = mpeg1_bitrates if version == 3 else mpeg2_bitrates
                for bitrate_index in range(1, 15):
                    bitrate = bitrates[layer][bitrate_index]
                    for sample_rate_index, sample_rate in enumerate(sample_rates[version]):
                        for padding in (0, 1):
                            if layer == 3:
                                frame_length = (12 * bitrate * 1000 // sample_rate + padding) * 4
                            else:
                                coefficient = 144 if version == 3 or layer == 2 else 72
                                frame_length = coefficient * bitrate * 1000 // sample_rate + padding
                            word = (
                                0xFFE00000
                                | version << 19
                                | layer << 17
                                | 1 << 16
                                | bitrate_index << 12
                                | sample_rate_index << 10
                                | padding << 9
                            )
                            frame = word.to_bytes(4, "big") + bytes(frame_length - 4)
                            with self.subTest(
                                version=version,
                                layer=layer,
                                bitrate=bitrate_index,
                                sample_rate=sample_rate_index,
                                padding=padding,
                            ):
                                _validate_mp3(path, frame)

    def test_duplicate_required_audio_stems_are_rejected(self) -> None:
        self.write_valid_fixture()
        (self.raw_root / "sfx_jump.wav").write_bytes(self.valid_wav())
        with self.assertRaisesRegex(SourceAssetVerificationError, "Duplicate raw audio"):
            verify_release_source_assets(self.root)

    def test_duplicate_unrequired_audio_stems_are_rejected(self) -> None:
        self.write_valid_fixture()
        (self.raw_root / "extra.ogg").write_bytes(self.valid_ogg())
        (self.raw_root / "extra.wav").write_bytes(self.valid_wav())
        with self.assertRaisesRegex(SourceAssetVerificationError, "Duplicate raw audio"):
            verify_release_source_assets(self.root)

    def test_raw_resource_names_follow_android_filename_rules(self) -> None:
        self.write_valid_fixture()
        for name in ("Bad.ogg", "bad-name.ogg", "bad.OGG"):
            with self.subTest(name=name):
                path = self.raw_root / name
                path.write_bytes(self.valid_ogg())
                with self.assertRaisesRegex(
                    SourceAssetVerificationError,
                    "Invalid Android raw-resource filename",
                ):
                    verify_release_source_assets(self.root)
                path.unlink()

    def test_unsafe_and_duplicate_asset_paths_are_rejected(self) -> None:
        for path in ("../secret.png", "C:/secret.png", "sprites\\bad.png", "bad\x01.png"):
            with self.subTest(path=path):
                with self.assertRaisesRegex(SourceAssetVerificationError, "unsafe"):
                    parse_asset_paths(f'const val BAD = "{path}"')
        with self.assertRaisesRegex(SourceAssetVerificationError, "duplicate"):
            parse_asset_paths('const val FIRST = "sprites/a.png"\nconst val SECOND = "sprites/a.png"\n')

    def test_asset_path_parser_includes_typed_constants_and_rejects_unparsed_ones(self) -> None:
        source = (
            'const val FIRST = "sprites/first.png"\n'
            'const val SECOND: String = "sprites/second.png"\n'
            'const val THIRD: kotlin.String = "sprites/third.png"\n'
        )
        self.assertEqual(
            ("sprites/first.png", "sprites/second.png", "sprites/third.png"),
            parse_asset_paths(source),
        )
        with self.assertRaisesRegex(SourceAssetVerificationError, "unsupported const val"):
            parse_asset_paths(
                'const val FIRST = "sprites/first.png"\n'
                'const val OMITTED: String = "sprites/missing.png" + "extra"\n'
            )

    def test_checked_in_repository_assets_pass_source_contract(self) -> None:
        evidence = verify_release_source_assets(ROOT)
        self.assertEqual((30, 29, 1, 15, 15), (
            evidence.asset_count,
            evidence.png_count,
            evidence.font_count,
            evidence.required_audio_count,
            evidence.checked_audio_count,
        ))

    def test_runtime_and_release_catalogues_match_asset_paths(self) -> None:
        asset_source = (ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/AssetPaths.kt").read_text(encoding="utf-8")
        runtime_source = (ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/RuntimeAssetValidator.kt").read_text(encoding="utf-8")
        preparer_source = (ROOT / "scripts/prepare_play_release.py").read_text(encoding="utf-8")

        expected_references = {"AssetPaths.PIXEL_FONT"}
        current_object = None
        for line in asset_source.splitlines():
            object_match = re.match(r"\s*object\s+(\w+)\s*\{", line)
            if object_match and object_match.group(1) != "AssetPaths":
                current_object = object_match.group(1)
                continue
            constant_match = re.match(
                r"\s*const\s+val\s+(\w+)\s*(?::\s*(?:kotlin\.)?String)?\s*=",
                line,
            )
            if constant_match:
                name = constant_match.group(1)
                if name != "PIXEL_FONT":
                    expected_references.add(f"AssetPaths.{current_object}.{name}")
        runtime_references = set(
            re.findall(
                r"(?<![A-Za-z0-9_])AssetPaths(?:\.\w+){1,2}",
                runtime_source,
            )
        )
        self.assertEqual(expected_references, runtime_references)

        runtime_audio_block = re.search(
            r"requiredRawResources\s*=\s*listOf\((?P<body>.*?)\n\s*\)",
            runtime_source,
            re.DOTALL,
        )
        self.assertIsNotNone(runtime_audio_block)
        runtime_audio = set(re.findall(r'"([a-z][a-z0-9_]*)"', runtime_audio_block.group("body")))
        self.assertEqual(set(parse_required_audio(preparer_source)), runtime_audio)

    def test_main_release_wrapper_runs_asset_preflight_before_preparer(self) -> None:
        wrapper = (ROOT / "scripts/prepare_main_release.sh").read_text(encoding="utf-8")
        verifier_index = wrapper.index("verify_release_source_assets.py")
        preparer_index = wrapper.index("prepare_play_release.py")
        self.assertLess(verifier_index, preparer_index)
        self.assertIn('--root "${ROOT}"', wrapper[verifier_index:preparer_index])

    def test_bounded_asset_reader_rejects_symlink_fifo_and_oversize_inputs(self) -> None:
        asset = self.root / "bounded.bin"
        asset.write_bytes(b"payload")
        alias = self.root / "alias.bin"
        alias.symlink_to(asset)
        with self.assertRaisesRegex(SourceAssetVerificationError, "non-symlink"):
            _read_bounded_file(alias, "runtime asset", 32)
        with self.assertRaisesRegex(SourceAssetVerificationError, "safety limit"):
            _read_bounded_file(asset, "runtime asset", 3)
        fifo = self.root / "asset.fifo"
        os.mkfifo(fifo)
        with self.assertRaisesRegex(SourceAssetVerificationError, "non-symlink"):
            _read_bounded_file(fifo, "runtime asset", 32)

    def test_wolf_runtime_frame_count_matches_authored_four_frame_contract(self) -> None:
        source = (
            ROOT
            / "app/src/main/java/com/anurag9000/forestrun/engine/SpriteManager.kt"
        ).read_text(encoding="utf-8")
        self.assertIn(
            "wolfSprite = loadEntity(AssetPaths.Animals.WOLF, Color.rgb(100, 100, 120), 4)",
            source,
        )
        self.assertNotIn(
            "wolfSprite = loadEntity(AssetPaths.Animals.WOLF, Color.rgb(100, 100, 120), 8)",
            source,
        )


if __name__ == "__main__":
    unittest.main()
