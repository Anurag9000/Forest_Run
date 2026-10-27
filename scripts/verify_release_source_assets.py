#!/usr/bin/env python3
from __future__ import annotations

import argparse
import ast
import json
import math
import os
import re
import stat
import struct
import zlib
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import NoReturn

from stable_file_reader import (
    StableFileError,
    checked_directory_path,
    consume_regular_file,
)

ROOT = Path(__file__).resolve().parent.parent
ASSET_PATHS_SOURCE = Path(
    "app/src/main/java/com/anurag9000/forestrun/engine/AssetPaths.kt"
)
RELEASE_PREPARER_SOURCE = Path("scripts/prepare_play_release.py")
ASSETS_ROOT = Path("app/src/main/assets")
RAW_ROOT = Path("app/src/main/res/raw")

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
FONT_SIGNATURES = {b"\x00\x01\x00\x00", b"OTTO", b"true", b"typ1"}
SUPPORTED_AUDIO_SUFFIXES = {".ogg", ".wav", ".mp3", ".m4a"}
MAX_RUNTIME_ASSET_BYTES = 128 * 1024 * 1024
MAX_AUDIO_BYTES = 128 * 1024 * 1024
MAX_PNG_PIXELS = 32_000_000
MAX_PNG_DECODED_BYTES = 256 * 1024 * 1024
MAX_FONT_TABLES = 4_096
MAX_MP3_PREFIX_SCAN_BYTES = 64 * 1024
PNG_VALID_BIT_DEPTHS = {
    0: {1, 2, 4, 8, 16},
    2: {8, 16},
    3: {1, 2, 4, 8},
    4: {8, 16},
    6: {8, 16},
}
PNG_CHANNELS_BY_COLOR_TYPE = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}
REQUIRED_FONT_TABLES = {
    b"OS/2", b"cmap", b"head", b"hhea", b"hmtx", b"maxp", b"name", b"post"
}
SPRITE_FRAME_PATTERN = re.compile(r"_(?P<count>[1-9][0-9]*)frames?(?:\.[^.]+)$", re.IGNORECASE)


class SourceAssetVerificationError(RuntimeError):
    """Raised when checked-in release inputs are missing, empty, or malformed."""


@dataclass(frozen=True)
class SourceAssetEvidence:
    asset_count: int
    png_count: int
    font_count: int
    required_audio_count: int
    checked_audio_count: int


def _fail(message: str) -> NoReturn:
    raise SourceAssetVerificationError(message)


def _read_text(path: Path, label: str) -> str:
    try:
        raw, _, _ = consume_regular_file(
            path, label, 16 * 1024 * 1024, retain_bytes=True
        )
    except StableFileError as error:
        _fail(str(error))
    assert raw is not None
    try:
        return raw.decode("utf-8")
    except UnicodeError as error:
        _fail(f"Could not read {label} {path}: {error}")


def parse_asset_paths(source: str) -> tuple[str, ...]:
    uncommented = re.sub(r"/\*.*?\*/|//[^\n]*", "", source, flags=re.DOTALL)
    declaration_pattern = re.compile(
        r'^\s*const\s+val\s+\w+\s*(?::\s*(?:kotlin\.)?String)?\s*='
        r'\s*"([^"\n]*)"\s*$'
    )
    declarations = [
        line
        for line in uncommented.splitlines()
        if re.search(r"\bconst\s+val\b", line)
    ]
    paths_list: list[str] = []
    for declaration in declarations:
        match = declaration_pattern.fullmatch(declaration)
        if match is None:
            _fail("AssetPaths.kt contains an unsupported const val declaration")
        paths_list.append(match.group(1))
    paths = tuple(paths_list)
    if not paths:
        _fail("AssetPaths.kt does not declare any asset paths")
    if len(paths) != len(set(paths)):
        _fail("AssetPaths.kt contains duplicate asset paths")
    for path in paths:
        candidate = Path(path)
        if (
            candidate.is_absolute()
            or ".." in candidate.parts
            or not candidate.parts
            or any(part in {"", "."} for part in candidate.parts)
            or "\\" in path
            or ":" in path
            or any(ord(character) < 32 or ord(character) == 127 for character in path)
        ):
            _fail(f"AssetPaths.kt contains an unsafe relative path: {path!r}")
    return paths


def parse_required_audio(source: str) -> tuple[str, ...]:
    try:
        module = ast.parse(source)
    except SyntaxError as error:
        _fail(f"Could not parse release preparer: {error}")

    def target_binds_name(target: ast.AST) -> bool:
        if isinstance(target, ast.Name):
            return target.id == "REQUIRED_AUDIO"
        if isinstance(target, (ast.Tuple, ast.List)):
            return any(target_binds_name(element) for element in target.elts)
        return False

    top_level_ids = {id(node) for node in module.body}
    writes: list[ast.AST] = []
    type_alias_node = getattr(ast, "TypeAlias", None)
    for node in ast.walk(module):
        targets: list[ast.AST] = []
        if isinstance(node, (ast.Assign, ast.Delete)):
            targets.extend(node.targets)
        elif isinstance(node, (ast.AnnAssign, ast.AugAssign, ast.NamedExpr)):
            targets.append(node.target)
        elif isinstance(node, (ast.For, ast.AsyncFor)):
            targets.append(node.target)
        elif isinstance(node, (ast.With, ast.AsyncWith)):
            targets.extend(
                item.optional_vars
                for item in node.items
                if item.optional_vars is not None
            )
        elif type_alias_node is not None and isinstance(node, type_alias_node):
            targets.append(node.name)
        elif isinstance(node, (ast.MatchAs, ast.MatchStar)):
            if node.name == "REQUIRED_AUDIO":
                writes.append(node)
        elif isinstance(node, ast.MatchMapping) and node.rest == "REQUIRED_AUDIO":
            writes.append(node)
        elif isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
            if node.name == "REQUIRED_AUDIO":
                writes.append(node)
        elif isinstance(node, ast.ExceptHandler) and node.name == "REQUIRED_AUDIO":
            writes.append(node)
        elif isinstance(node, (ast.Import, ast.ImportFrom)):
            if any(
                (alias.asname or alias.name.split(".")[0]) == "REQUIRED_AUDIO"
                for alias in node.names
            ):
                writes.append(node)
        if any(target_binds_name(target) for target in targets):
            writes.append(node)

    if (
        len(writes) != 1
        or id(writes[0]) not in top_level_ids
        or not isinstance(writes[0], ast.Assign)
        or len(writes[0].targets) != 1
        or not isinstance(writes[0].targets[0], ast.Name)
        or writes[0].targets[0].id != "REQUIRED_AUDIO"
    ):
        _fail("prepare_play_release.py must define REQUIRED_AUDIO once as a top-level literal assignment")
    try:
        value: object | None = ast.literal_eval(writes[0].value)
    except (ValueError, TypeError, SyntaxError) as error:
        _fail(f"REQUIRED_AUDIO must be a literal sequence: {error}")

    if not isinstance(value, (tuple, list)) or not value:
        _fail("prepare_play_release.py does not declare a non-empty REQUIRED_AUDIO sequence")
    if any(not isinstance(item, str) or not item for item in value):
        _fail("REQUIRED_AUDIO entries must be non-empty strings")

    names = tuple(value)
    if len(names) != len(set(names)):
        _fail("REQUIRED_AUDIO contains duplicate resource names")
    resource_pattern = re.compile(r"[a-z][a-z0-9_]*")
    for name in names:
        if not resource_pattern.fullmatch(name):
            _fail(f"Invalid Android raw-resource name in REQUIRED_AUDIO: {name!r}")
    return names


def _read_bounded_file(path: Path, label: str, maximum_bytes: int) -> bytes:
    try:
        raw, _, _ = consume_regular_file(
            path, label, maximum_bytes, retain_bytes=True
        )
    except StableFileError as error:
        _fail(str(error))
    assert raw is not None
    return raw


def _validate_png(path: Path, content: bytes) -> tuple[int, int]:
    if not content.startswith(PNG_SIGNATURE):
        _fail(f"Runtime PNG has an invalid signature: {path}")

    offset = len(PNG_SIGNATURE)
    width = 0
    height = 0
    bit_depth = 0
    color_type = -1
    interlace = -1
    saw_ihdr = False
    saw_plte = False
    saw_idat = False
    idat_closed = False
    saw_iend = False
    idat_parts: list[bytes] = []

    while offset < len(content):
        if len(content) - offset < 12:
            _fail(f"Runtime PNG has a truncated chunk header: {path}")
        length = struct.unpack(">I", content[offset : offset + 4])[0]
        chunk_type = content[offset + 4 : offset + 8]
        if any(
            not (65 <= value <= 90 or 97 <= value <= 122)
            for value in chunk_type
        ):
            _fail(f"Runtime PNG has an invalid chunk type: {path}")
        if chunk_type[2] & 0x20:
            _fail(f"Runtime PNG has a lowercase reserved chunk-type bit: {path}")
        chunk_end = offset + 12 + length
        if chunk_end > len(content):
            _fail(f"Runtime PNG has a truncated chunk: {path}")
        data_start = offset + 8
        data_end = data_start + length
        chunk_data = content[data_start:data_end]
        expected_crc = struct.unpack(">I", content[data_end : data_end + 4])[0]
        actual_crc = zlib.crc32(chunk_type)
        actual_crc = zlib.crc32(chunk_data, actual_crc) & 0xFFFFFFFF
        if actual_crc != expected_crc:
            _fail(f"Runtime PNG has a CRC mismatch: {path}")

        if not saw_ihdr and chunk_type != b"IHDR":
            _fail(f"Runtime PNG does not start with IHDR: {path}")
        if chunk_type == b"IHDR":
            if saw_ihdr or length != 13:
                _fail(f"Runtime PNG has an invalid IHDR: {path}")
            (
                width,
                height,
                bit_depth,
                color_type,
                compression,
                filtering,
                interlace,
            ) = struct.unpack(">IIBBBBB", chunk_data)
            if (
                width <= 0
                or height <= 0
                or width * height > MAX_PNG_PIXELS
                or color_type not in PNG_VALID_BIT_DEPTHS
                or bit_depth not in PNG_VALID_BIT_DEPTHS[color_type]
                or compression != 0
                or filtering != 0
                or interlace not in {0, 1}
            ):
                _fail(f"Runtime PNG has invalid IHDR values: {path}")
            saw_ihdr = True
        elif chunk_type == b"PLTE":
            if (
                saw_idat
                or saw_plte
                or color_type in {0, 4}
                or length == 0
                or length > 768
                or length % 3 != 0
                or (color_type == 3 and length // 3 > 1 << bit_depth)
            ):
                _fail(f"Runtime PNG has an invalid palette: {path}")
            saw_plte = True
        elif chunk_type == b"IDAT":
            if not saw_ihdr or saw_iend or idat_closed:
                _fail(f"Runtime PNG has out-of-order IDAT chunks: {path}")
            if color_type == 3 and not saw_plte:
                _fail(f"Indexed runtime PNG is missing its palette: {path}")
            saw_idat = True
            idat_parts.append(chunk_data)
        elif chunk_type == b"IEND":
            if length != 0 or not saw_ihdr or not saw_idat or saw_iend:
                _fail(f"Runtime PNG has an invalid IEND: {path}")
            saw_iend = True
            offset = chunk_end
            if offset != len(content):
                _fail(f"Runtime PNG has trailing bytes after IEND: {path}")
            break
        else:
            if saw_idat:
                idat_closed = True
            if chunk_type[0] & 0x20 == 0:
                _fail(f"Runtime PNG has an unknown critical chunk: {path}")
        offset = chunk_end

    if not saw_ihdr or not saw_idat or not saw_iend:
        _fail(f"Runtime PNG is missing required chunks: {path}")

    decompressor = zlib.decompressobj()
    try:
        decoded = decompressor.decompress(
            b"".join(idat_parts),
            MAX_PNG_DECODED_BYTES + 1,
        )
        if decompressor.unconsumed_tail or len(decoded) > MAX_PNG_DECODED_BYTES:
            _fail(f"Runtime PNG expands beyond the decode safety limit: {path}")
        decoded += decompressor.flush()
    except zlib.error as error:
        _fail(f"Runtime PNG has invalid compressed image data: {path}: {error}")
    if (
        not decompressor.eof
        or decompressor.unused_data
        or len(decoded) > MAX_PNG_DECODED_BYTES
    ):
        _fail(f"Runtime PNG has an incomplete or trailing zlib stream: {path}")
    channels = PNG_CHANNELS_BY_COLOR_TYPE[color_type]
    if interlace == 0:
        passes = ((0, 0, 1, 1),)
    else:
        passes = (
            (0, 0, 8, 8),
            (4, 0, 8, 8),
            (0, 4, 4, 8),
            (2, 0, 4, 4),
            (0, 2, 2, 4),
            (1, 0, 2, 2),
            (0, 1, 1, 2),
        )
    scanline_offset = 0
    for start_x, start_y, step_x, step_y in passes:
        pass_width = (width - start_x + step_x - 1) // step_x
        pass_height = (height - start_y + step_y - 1) // step_y
        if width <= start_x or height <= start_y:
            continue
        row_bytes = math.ceil(pass_width * channels * bit_depth / 8)
        encoded_row_bytes = row_bytes + 1
        pass_size = pass_height * encoded_row_bytes
        if scanline_offset + pass_size > len(decoded):
            _fail(f"Runtime PNG scanline data does not match IHDR geometry: {path}")
        for row in range(pass_height):
            filter_type = decoded[scanline_offset + row * encoded_row_bytes]
            if filter_type > 4:
                _fail(f"Runtime PNG has an invalid scanline filter: {path}")
        scanline_offset += pass_size
    if scanline_offset != len(decoded):
        _fail(f"Runtime PNG scanline data does not match IHDR geometry: {path}")

    frame_match = SPRITE_FRAME_PATTERN.search(path.name)
    if frame_match is not None:
        frame_count = int(frame_match.group("count"))
        if width % frame_count != 0 or width // frame_count <= 0:
            _fail(
                f"Sprite sheet width {width} is not divisible by its authored "
                f"{frame_count}-frame count: {path}"
            )
    return width, height


def _validate_font(path: Path, content: bytes) -> None:
    if len(content) < 12 or content[:4] not in FONT_SIGNATURES:
        _fail(f"Runtime font has an invalid signature or offset table: {path}")
    num_tables = struct.unpack(">H", content[4:6])[0]
    if num_tables <= 0 or num_tables > MAX_FONT_TABLES:
        _fail(f"Runtime font has an invalid table count: {path}")
    search_range, entry_selector, range_shift = struct.unpack(">HHH", content[6:12])
    search_power = 1 << (num_tables.bit_length() - 1)
    if (
        search_range != search_power * 16
        or entry_selector != search_power.bit_length() - 1
        or range_shift != num_tables * 16 - search_range
    ):
        _fail(f"Runtime font offset-table search parameters are invalid: {path}")
    directory_end = 12 + num_tables * 16
    if directory_end > len(content):
        _fail(f"Runtime font has a truncated table directory: {path}")

    tables: dict[bytes, tuple[int, int, int]] = {}
    table_tags: list[bytes] = []
    table_ranges: list[tuple[int, int, bytes]] = []
    for index in range(num_tables):
        entry = content[12 + index * 16 : 28 + index * 16]
        tag, checksum, offset, length = struct.unpack(">4sIII", entry)
        if any(value < 32 or value > 126 for value in tag):
            _fail(f"Runtime font contains an invalid table tag: {path}")
        if tag in tables:
            _fail(f"Runtime font contains a duplicate {tag!r} table: {path}")
        table_tags.append(tag)
        if (
            offset < directory_end
            or length <= 0
            or offset > len(content) - length
        ):
            _fail(f"Runtime font table {tag!r} is outside the file: {path}")
        if offset % 4 != 0:
            _fail(f"Runtime font table {tag!r} is not four-byte aligned: {path}")
        tables[tag] = (offset, length, checksum)
        table_ranges.append((offset, offset + length, tag))

    previous_end = directory_end
    previous_tag: bytes | None = None
    for offset, end, tag in sorted(table_ranges):
        if offset < previous_end:
            _fail(
                f"Runtime font tables {previous_tag!r} and {tag!r} overlap: {path}"
            )
        previous_end = end
        previous_tag = tag

    if table_tags != sorted(table_tags):
        _fail(f"Runtime font table directory is not sorted by tag: {path}")

    missing = sorted(REQUIRED_FONT_TABLES.difference(tables))
    if missing:
        rendered = ", ".join(tag.decode("ascii") for tag in missing)
        _fail(f"Runtime font is missing required tables ({rendered}): {path}")

    if content[:4] in {b"\x00\x01\x00\x00", b"true"}:
        missing_outlines = sorted({b"glyf", b"loca"}.difference(tables))
        if missing_outlines:
            rendered = ", ".join(tag.decode("ascii") for tag in missing_outlines)
            _fail(f"TrueType runtime font is missing outline tables ({rendered}): {path}")
    elif content[:4] == b"OTTO" and not ({b"CFF ", b"CFF2"} & tables.keys()):
        _fail(f"CFF runtime font is missing its CFF or CFF2 outline table: {path}")

    for tag, (offset, length, expected_checksum) in tables.items():
        table = bytearray(content[offset : offset + length])
        if tag == b"head":
            if len(table) < 12:
                _fail(f"Runtime font has a truncated head table: {path}")
            table[8:12] = b"\x00\x00\x00\x00"
        table.extend(b"\x00" * ((-len(table)) % 4))
        actual_checksum = sum(
            word[0]
            for word in struct.iter_unpack(">I", table)
        ) & 0xFFFFFFFF
        if actual_checksum != expected_checksum:
            _fail(f"Runtime font table {tag!r} checksum mismatch: {path}")

    head_offset, head_length, _ = tables[b"head"]
    if head_length < 12:
        _fail(f"Runtime font has a truncated head table: {path}")
    expected_adjustment = struct.unpack(">I", content[head_offset + 8 : head_offset + 12])[0]
    whole_font = bytearray(content)
    whole_font[head_offset + 8 : head_offset + 12] = b"\x00\x00\x00\x00"
    whole_font.extend(b"\x00" * ((-len(whole_font)) % 4))
    whole_font_checksum = sum(
        word[0]
        for word in struct.iter_unpack(">I", whole_font)
    ) & 0xFFFFFFFF
    if expected_adjustment != (0xB1B0AFBA - whole_font_checksum) & 0xFFFFFFFF:
        _fail(f"Runtime font has an invalid checksum adjustment: {path}")

    if head_length < 54 or struct.unpack(">I", content[head_offset + 12 : head_offset + 16])[0] != 0x5F0F3CF5:
        _fail(f"Runtime font has an invalid head table: {path}")
    maxp_offset, maxp_length, _ = tables[b"maxp"]
    if maxp_length < 6:
        _fail(f"Runtime font has no glyphs in its maxp table: {path}")
    maxp_version = struct.unpack(">I", content[maxp_offset : maxp_offset + 4])[0]
    glyph_count = struct.unpack(">H", content[maxp_offset + 4 : maxp_offset + 6])[0]
    if glyph_count <= 0:
        _fail(f"Runtime font has no glyphs in its maxp table: {path}")
    if content[:4] in {b"\x00\x01\x00\x00", b"true"}:
        if maxp_version != 0x00010000 or maxp_length < 32:
            _fail(f"TrueType runtime font has an invalid maxp table: {path}")
        loca_format = struct.unpack_from(">h", content, head_offset + 50)[0]
        glyph_data_format = struct.unpack_from(">h", content, head_offset + 52)[0]
        if loca_format not in {0, 1} or glyph_data_format != 0:
            _fail(f"TrueType runtime font has invalid head glyph formats: {path}")
        _validate_truetype_outlines(path, content, tables, glyph_count, loca_format)
    elif content[:4] == b"OTTO" and maxp_version != 0x00005000:
        _fail(f"CFF runtime font has an invalid maxp table: {path}")
    hhea_offset, hhea_length, _ = tables[b"hhea"]
    if hhea_length < 36:
        _fail(f"Runtime font has a truncated hhea table: {path}")
    horizontal_metric_count = struct.unpack(
        ">H", content[hhea_offset + 34 : hhea_offset + 36]
    )[0]
    if not 1 <= horizontal_metric_count <= glyph_count:
        _fail(f"Runtime font has an invalid numberOfHMetrics value: {path}")
    hmtx_offset, hmtx_length, _ = tables[b"hmtx"]
    required_hmtx_length = (
        horizontal_metric_count * 4
        + (glyph_count - horizontal_metric_count) * 2
    )
    if hmtx_length < required_hmtx_length:
        _fail(f"Runtime font hmtx table is too short for its glyph metrics: {path}")
    os2_offset, os2_length, _ = tables[b"OS/2"]
    _validate_os2_table(path, content[os2_offset : os2_offset + os2_length])
    post_offset, post_length, _ = tables[b"post"]
    _validate_post_table(
        path,
        content[post_offset : post_offset + post_length],
        glyph_count,
    )
    cmap_offset, cmap_length, _ = tables[b"cmap"]
    _validate_cmap_table(
        path,
        content[cmap_offset : cmap_offset + cmap_length],
        glyph_count,
    )
    name_offset, name_length, _ = tables[b"name"]
    _validate_name_table(path, content[name_offset : name_offset + name_length])


def _validate_os2_table(path: Path, table: bytes) -> None:
    if len(table) < 2:
        _fail(f"Runtime font OS/2 table is truncated: {path}")
    version = struct.unpack_from(">H", table)[0]
    minimum_lengths = {0: 78, 1: 86, 2: 96, 3: 96, 4: 96, 5: 100}
    minimum_length = minimum_lengths.get(version)
    if minimum_length is None or len(table) < minimum_length:
        _fail(f"Runtime font OS/2 table has an unsupported version or length: {path}")


def _validate_truetype_outlines(
    path: Path,
    content: bytes,
    tables: dict[bytes, tuple[int, int, int]],
    glyph_count: int,
    loca_format: int,
) -> None:
    loca_offset, loca_length, _ = tables[b"loca"]
    glyf_offset, glyf_length, _ = tables[b"glyf"]
    entry_width = 2 if loca_format == 0 else 4
    if loca_length != entry_width * (glyph_count + 1):
        _fail(f"Runtime font loca table has an invalid length: {path}")
    if loca_format == 0:
        raw_offsets = struct.unpack_from(f">{glyph_count + 1}H", content, loca_offset)
        offsets = tuple(value * 2 for value in raw_offsets)
    else:
        offsets = struct.unpack_from(f">{glyph_count + 1}I", content, loca_offset)
    if offsets[0] != 0:
        _fail(f"Runtime font loca table does not begin at glyf offset zero: {path}")
    if offsets[-1] != glyf_length:
        _fail(f"Runtime font loca table does not end at the glyf table boundary: {path}")
    previous = 0
    composite_references: dict[int, set[int]] = {}
    for glyph_index, offset in enumerate(offsets):
        if offset < previous or offset > glyf_length or offset % 2:
            _fail(f"Runtime font loca offsets are unordered, misaligned, or outside glyf: {path}")
        if glyph_index and offset > previous:
            glyph_id = glyph_index - 1
            glyph_data = content[glyf_offset + previous : glyf_offset + offset]
            references = _validate_truetype_glyph(path, glyph_data, glyph_id, glyph_count)
            if references:
                composite_references[glyph_id] = references
        previous = offset
    state: dict[int, int] = {}
    for root_glyph in composite_references:
        if state.get(root_glyph) == 2:
            continue
        state[root_glyph] = 1
        stack = [(root_glyph, iter(composite_references.get(root_glyph, set())))]
        while stack:
            glyph_id, children = stack[-1]
            try:
                component = next(children)
            except StopIteration:
                state[glyph_id] = 2
                stack.pop()
                continue
            component_state = state.get(component, 0)
            if component_state == 1:
                _fail(f"Runtime font composite glyph graph contains a cycle: {path}")
            if component_state == 2:
                continue
            state[component] = 1
            stack.append((component, iter(composite_references.get(component, set()))))


def _validate_truetype_glyph(
    path: Path,
    data: bytes,
    glyph_id: int,
    glyph_count: int,
) -> set[int]:
    if len(data) < 10:
        _fail(f"Runtime font glyph {glyph_id} has a truncated header: {path}")
    contour_count, x_min, y_min, x_max, y_max = struct.unpack_from(">hhhhh", data)
    if contour_count < -1 or x_min > x_max or y_min > y_max:
        _fail(f"Runtime font glyph {glyph_id} has an invalid header: {path}")
    cursor = 10
    if contour_count >= 0:
        if cursor + 2 * contour_count > len(data):
            _fail(f"Runtime font glyph {glyph_id} has truncated contour endpoints: {path}")
        end_points = struct.unpack_from(f">{contour_count}H", data, cursor) if contour_count else ()
        if any(left >= right for left, right in zip(end_points, end_points[1:])):
            _fail(f"Runtime font glyph {glyph_id} has unordered contour endpoints: {path}")
        point_count = end_points[-1] + 1 if end_points else 0
        cursor += 2 * contour_count
        if cursor + 2 > len(data):
            _fail(f"Runtime font glyph {glyph_id} has truncated instructions: {path}")
        instruction_length = struct.unpack_from(">H", data, cursor)[0]
        cursor += 2
        if cursor + instruction_length > len(data):
            _fail(f"Runtime font glyph {glyph_id} has truncated instructions: {path}")
        cursor += instruction_length
        flags: list[int] = []
        while len(flags) < point_count:
            if cursor >= len(data):
                _fail(f"Runtime font glyph {glyph_id} has truncated point flags: {path}")
            flag = data[cursor]
            cursor += 1
            if flag & 0xC0:
                _fail(f"Runtime font glyph {glyph_id} uses reserved point flags: {path}")
            repeat_count = 0
            if flag & 0x08:
                if cursor >= len(data):
                    _fail(f"Runtime font glyph {glyph_id} has a truncated flag repeat: {path}")
                repeat_count = data[cursor]
                cursor += 1
            if len(flags) + repeat_count + 1 > point_count:
                _fail(f"Runtime font glyph {glyph_id} point flags exceed its contour endpoints: {path}")
            flags.extend([flag] * (repeat_count + 1))
        x_bytes = sum(1 if flag & 0x02 else 0 if flag & 0x10 else 2 for flag in flags)
        y_bytes = sum(1 if flag & 0x04 else 0 if flag & 0x20 else 2 for flag in flags)
        cursor += x_bytes + y_bytes
        if cursor > len(data) or len(data) - cursor > 3:
            _fail(f"Runtime font glyph {glyph_id} coordinate arrays are truncated or overlong: {path}")
        return set()

    references: set[int] = set()
    has_instructions = False
    while True:
        if cursor + 4 > len(data):
            _fail(f"Runtime font composite glyph {glyph_id} has a truncated component: {path}")
        flags, component_glyph = struct.unpack_from(">HH", data, cursor)
        cursor += 4
        if flags & 0xE010:
            _fail(f"Runtime font composite glyph {glyph_id} uses reserved flags: {path}")
        if component_glyph >= glyph_count:
            _fail(f"Runtime font composite glyph {glyph_id} references an unknown glyph: {path}")
        transform_flags = flags & 0x00C8
        if transform_flags and transform_flags & (transform_flags - 1):
            _fail(f"Runtime font composite glyph {glyph_id} has conflicting transforms: {path}")
        if flags & 0x0800 and flags & 0x1000:
            _fail(f"Runtime font composite glyph {glyph_id} has conflicting offset flags: {path}")
        references.add(component_glyph)
        has_instructions = has_instructions or bool(flags & 0x0100)
        argument_bytes = 4 if flags & 0x0001 else 2
        transform_bytes = 2 if flags & 0x0008 else 4 if flags & 0x0040 else 8 if flags & 0x0080 else 0
        cursor += argument_bytes + transform_bytes
        if cursor > len(data):
            _fail(f"Runtime font composite glyph {glyph_id} has truncated component data: {path}")
        if not flags & 0x0020:
            break
    if has_instructions:
        if cursor + 2 > len(data):
            _fail(f"Runtime font composite glyph {glyph_id} has truncated instructions: {path}")
        instruction_length = struct.unpack_from(">H", data, cursor)[0]
        cursor += 2 + instruction_length
    if cursor > len(data) or len(data) - cursor > 3:
        _fail(f"Runtime font composite glyph {glyph_id} has truncated or overlong data: {path}")
    return references


def _validate_post_table(path: Path, table: bytes, glyph_count: int) -> None:
    if len(table) < 32:
        _fail(f"Runtime font post table is truncated: {path}")
    version = struct.unpack_from(">I", table)[0]
    if version == 0x00010000:
        if glyph_count != 258 or len(table) != 32:
            _fail(f"Runtime font post version 1.0 has an invalid glyph count or length: {path}")
        return
    if version == 0x00020000:
        if len(table) < 34:
            _fail(f"Runtime font post version 2.0 glyph count is truncated: {path}")
        post_glyph_count = struct.unpack_from(">H", table, 32)[0]
        glyph_names_end = 34 + post_glyph_count * 2
        if post_glyph_count != glyph_count or glyph_names_end > len(table):
            _fail(f"Runtime font post version 2.0 glyph-name indices are invalid: {path}")
        glyph_name_indices = struct.unpack_from(
            f">{post_glyph_count}H", table, 34
        )
        custom_name_count = max(0, max(glyph_name_indices, default=257) - 257)
        cursor = glyph_names_end
        for _ in range(custom_name_count):
            if cursor >= len(table):
                _fail(f"Runtime font post version 2.0 custom names are truncated: {path}")
            name_end = cursor + 1 + table[cursor]
            if name_end > len(table):
                _fail(f"Runtime font post version 2.0 custom names are truncated: {path}")
            cursor = name_end
        if cursor != len(table):
            _fail(f"Runtime font post version 2.0 has trailing bytes: {path}")
        return
    if version == 0x00025000:
        if len(table) < 34:
            _fail(f"Runtime font post version 2.5 glyph count is truncated: {path}")
        post_glyph_count = struct.unpack_from(">H", table, 32)[0]
        if post_glyph_count != glyph_count or len(table) != 34 + post_glyph_count:
            _fail(f"Runtime font post version 2.5 glyph offsets are invalid: {path}")
        return
    if version == 0x00030000 and len(table) == 32:
        return
    _fail(f"Runtime font post table has an unsupported version or length: {path}")


def _validate_cmap_format4(path: Path, subtable: bytes, glyph_count: int) -> None:
    if len(subtable) < 24:
        _fail(f"Runtime font cmap format 4 has no complete segment: {path}")
    (
        _format,
        declared_length,
        _language,
        segment_count_x2,
        search_range,
        entry_selector,
        range_shift,
    ) = struct.unpack(">7H", subtable[:14])
    if segment_count_x2 == 0 or segment_count_x2 % 2:
        _fail(f"Runtime font cmap format 4 has an invalid segment count: {path}")
    segment_count = segment_count_x2 // 2
    minimum_length = 16 + segment_count * 8
    if declared_length != len(subtable) or declared_length < minimum_length:
        _fail(f"Runtime font cmap format 4 segment arrays are truncated: {path}")
    power = 1 << (segment_count.bit_length() - 1)
    if (
        search_range != 2 * power
        or entry_selector != power.bit_length() - 1
        or range_shift != segment_count_x2 - search_range
    ):
        _fail(f"Runtime font cmap format 4 search parameters are invalid: {path}")

    end_codes_offset = 14
    reserved_pad_offset = end_codes_offset + 2 * segment_count
    start_codes_offset = reserved_pad_offset + 2
    deltas_offset = start_codes_offset + 2 * segment_count
    range_offsets_offset = deltas_offset + 2 * segment_count
    if struct.unpack_from(">H", subtable, reserved_pad_offset)[0] != 0:
        _fail(f"Runtime font cmap format 4 reservedPad is nonzero: {path}")
    end_codes = struct.unpack_from(f">{segment_count}H", subtable, end_codes_offset)
    start_codes = struct.unpack_from(f">{segment_count}H", subtable, start_codes_offset)
    range_offsets = struct.unpack_from(
        f">{segment_count}H", subtable, range_offsets_offset
    )
    if (
        any(left >= right for left, right in zip(end_codes, end_codes[1:]))
        or any(start > end for start, end in zip(start_codes, end_codes))
        or end_codes[-1] != 0xFFFF
        or start_codes[-1] != 0xFFFF
    ):
        _fail(f"Runtime font cmap format 4 segments are invalid: {path}")

    glyph_array_offset = range_offsets_offset + 2 * segment_count
    for index, range_offset in enumerate(range_offsets):
        if range_offset == 0:
            delta = struct.unpack_from(">h", subtable, deltas_offset + 2 * index)[0]
            for codepoint in range(start_codes[index], end_codes[index] + 1):
                if (codepoint + delta) & 0xFFFF >= glyph_count:
                    _fail(f"Runtime font cmap format 4 maps outside maxp glyph count: {path}")
            continue
        range_offset_position = range_offsets_offset + 2 * index
        last_glyph_position = (
            range_offset_position
            + range_offset
            + 2 * (end_codes[index] - start_codes[index])
        )
        if (
            range_offset % 2
            or range_offset_position + range_offset < glyph_array_offset
            or last_glyph_position + 2 > declared_length
        ):
            _fail(f"Runtime font cmap format 4 glyph array is outside the subtable: {path}")
        delta = struct.unpack_from(">h", subtable, deltas_offset + 2 * index)[0]
        first_glyph_position = range_offset_position + range_offset
        for codepoint in range(start_codes[index], end_codes[index] + 1):
            glyph_id = struct.unpack_from(
                ">H",
                subtable,
                first_glyph_position + 2 * (codepoint - start_codes[index]),
            )[0]
            if glyph_id != 0:
                glyph_id = (glyph_id + delta) & 0xFFFF
            if glyph_id >= glyph_count:
                _fail(f"Runtime font cmap format 4 maps outside maxp glyph count: {path}")


def _validate_cmap_variable_arrays(
    path: Path,
    format_value: int,
    subtable: bytes,
    glyph_count: int,
) -> None:
    length = len(subtable)
    if format_value == 2:
        if length < 518:
            _fail(f"Runtime font cmap format 2 subHeaderKeys are truncated: {path}")
        keys = struct.unpack_from(">256H", subtable, 6)
        if any(key % 8 for key in keys):
            _fail(f"Runtime font cmap format 2 has an invalid subHeaderKey: {path}")
        subheader_count = max(keys, default=0) // 8 + 1
        subheaders_end = 518 + subheader_count * 8
        if subheaders_end > length:
            _fail(f"Runtime font cmap format 2 subheaders are truncated: {path}")
        for index in range(subheader_count):
            subheader_offset = 518 + index * 8
            first_code, entry_count = struct.unpack_from(">HH", subtable, subheader_offset)
            delta = struct.unpack_from(">h", subtable, subheader_offset + 4)[0]
            range_offset = struct.unpack_from(">H", subtable, subheader_offset + 6)[0]
            if first_code > 0xFF or first_code + entry_count > 0x100:
                _fail(f"Runtime font cmap format 2 subheader range is invalid: {path}")
            if range_offset and (
                range_offset % 2
                or subheader_offset + 6 + range_offset < subheaders_end
                or subheader_offset + 6 + range_offset + 2 * entry_count > length
            ):
                _fail(f"Runtime font cmap format 2 glyph array is truncated: {path}")
            if range_offset:
                glyph_array_start = subheader_offset + 6 + range_offset
                for glyph_index in range(entry_count):
                    mapped_glyph = struct.unpack_from(
                        ">H", subtable, glyph_array_start + 2 * glyph_index
                    )[0]
                    if mapped_glyph:
                        mapped_glyph = (mapped_glyph + delta) & 0xFFFF
                    if mapped_glyph >= glyph_count:
                        _fail(f"Runtime font cmap format 2 maps outside maxp glyph count: {path}")
    elif format_value == 6:
        first_code, entry_count = struct.unpack_from(">HH", subtable, 6)
        if first_code + entry_count > 0x10000 or length != 10 + 2 * entry_count:
            _fail(f"Runtime font cmap format 6 glyph array length is invalid: {path}")
        glyph_ids = struct.unpack_from(f">{entry_count}H", subtable, 10)
        if any(glyph_id >= glyph_count for glyph_id in glyph_ids):
            _fail(f"Runtime font cmap format 6 maps outside maxp glyph count: {path}")
    elif format_value == 8:
        if struct.unpack_from(">H", subtable, 2)[0] != 0:
            _fail(f"Runtime font cmap format 8 reserved field is nonzero: {path}")
        group_count = struct.unpack_from(">I", subtable, 8204)[0]
        if length != 8208 + 12 * group_count:
            _fail(f"Runtime font cmap format 8 groups are truncated: {path}")
        previous_end = -1
        for index in range(group_count):
            start_code, end_code, start_glyph = struct.unpack_from(">III", subtable, 8208 + 12 * index)
            if (
                start_code <= previous_end
                or start_code > end_code
                or end_code > 0x10FFFF
            ):
                _fail(f"Runtime font cmap format 8 groups are invalid: {path}")
            if start_glyph + end_code - start_code >= glyph_count:
                _fail(f"Runtime font cmap format 8 maps outside maxp glyph count: {path}")
            previous_end = end_code
    elif format_value == 10:
        if struct.unpack_from(">H", subtable, 2)[0] != 0:
            _fail(f"Runtime font cmap format 10 reserved field is nonzero: {path}")
        start_code, character_count = struct.unpack_from(">II", subtable, 12)
        if start_code + character_count > 0x110000 or length != 20 + 2 * character_count:
            _fail(f"Runtime font cmap format 10 glyph array length is invalid: {path}")
        glyph_ids = struct.unpack_from(f">{character_count}H", subtable, 20)
        if any(glyph_id >= glyph_count for glyph_id in glyph_ids):
            _fail(f"Runtime font cmap format 10 maps outside maxp glyph count: {path}")
    elif format_value in {12, 13}:
        if struct.unpack_from(">H", subtable, 2)[0] != 0:
            _fail(f"Runtime font cmap format {format_value} reserved field is nonzero: {path}")
        group_count = struct.unpack_from(">I", subtable, 12)[0]
        if length != 16 + 12 * group_count:
            _fail(f"Runtime font cmap format {format_value} groups are truncated: {path}")
        previous_end = -1
        for index in range(group_count):
            start_code, end_code, glyph_id = struct.unpack_from(">III", subtable, 16 + 12 * index)
            if (
                start_code <= previous_end
                or start_code > end_code
                or end_code > 0x10FFFF
            ):
                _fail(f"Runtime font cmap format {format_value} groups are invalid: {path}")
            final_glyph = glyph_id + end_code - start_code if format_value == 12 else glyph_id
            if final_glyph >= glyph_count:
                _fail(f"Runtime font cmap format {format_value} maps outside maxp glyph count: {path}")
            previous_end = end_code
    elif format_value == 14:
        record_count = struct.unpack_from(">I", subtable, 6)[0]
        records_end = 10 + 11 * record_count
        if length < records_end:
            _fail(f"Runtime font cmap format 14 selector records are truncated: {path}")
        previous_selector = -1

        def read_u24(offset: int) -> int:
            return int.from_bytes(subtable[offset : offset + 3], "big")

        for index in range(record_count):
            record_offset = 10 + 11 * index
            selector = read_u24(record_offset)
            if selector <= previous_selector or selector > 0x10FFFF:
                _fail(f"Runtime font cmap format 14 selectors are invalid: {path}")
            previous_selector = selector
            for offset_position, record_size, label in (
                (record_offset + 3, 4, "default"),
                (record_offset + 7, 5, "non-default"),
            ):
                table_offset = struct.unpack_from(">I", subtable, offset_position)[0]
                if table_offset == 0:
                    continue
                if table_offset < records_end or table_offset > length - 4:
                    _fail(
                        f"Runtime font cmap format 14 {label} table offset is invalid: {path}"
                    )
                item_count = struct.unpack_from(">I", subtable, table_offset)[0]
                items_start = table_offset + 4
                if item_count > (length - items_start) // record_size:
                    _fail(
                        f"Runtime font cmap format 14 {label} table is truncated: {path}"
                    )
                previous_value = -1
                for item_index in range(item_count):
                    item_offset = items_start + item_index * record_size
                    value = read_u24(item_offset)
                    if label == "default":
                        end_value = value + subtable[item_offset + 3]
                        if value <= previous_value or end_value >= 0x110000:
                            _fail(
                                f"Runtime font cmap format 14 default ranges are invalid: {path}"
                            )
                        previous_value = end_value
                    else:
                        if value <= previous_value or value > 0x10FFFF:
                            _fail(
                                f"Runtime font cmap format 14 non-default mappings are invalid: {path}"
                            )
                        glyph_id = struct.unpack_from(">H", subtable, item_offset + 3)[0]
                        if glyph_id >= glyph_count:
                            _fail(f"Runtime font cmap format 14 maps outside maxp glyph count: {path}")
                        previous_value = value


def _validate_cmap_table(path: Path, table: bytes, glyph_count: int) -> None:
    if len(table) < 4:
        _fail(f"Runtime font has no cmap encoding records: {path}")
    version, count = struct.unpack(">HH", table[:4])
    records_end = 4 + count * 8
    if version != 0 or count == 0:
        _fail(f"Runtime font has no cmap encoding records: {path}")
    if records_end > len(table):
        _fail(f"Runtime font cmap encoding record array is truncated: {path}")

    minimum_lengths = {0: 262, 2: 518, 4: 24, 6: 10, 8: 8208, 10: 20, 12: 16, 13: 16, 14: 10}
    for index in range(count):
        record_offset = 4 + index * 8
        subtable_offset = struct.unpack(">I", table[record_offset + 4 : record_offset + 8])[0]
        if subtable_offset < records_end or subtable_offset > len(table) - 2:
            _fail(f"Runtime font cmap subtable offset is outside the table: {path}")
        format_value = struct.unpack(">H", table[subtable_offset : subtable_offset + 2])[0]
        minimum_length = minimum_lengths.get(format_value)
        if minimum_length is None:
            _fail(f"Runtime font cmap uses an unsupported subtable format: {path}")
        if format_value in {0, 2, 4, 6}:
            if subtable_offset > len(table) - 4:
                _fail(f"Runtime font cmap subtable header is truncated: {path}")
            subtable_length = struct.unpack(">H", table[subtable_offset + 2 : subtable_offset + 4])[0]
        elif format_value == 14:
            if subtable_offset > len(table) - 6:
                _fail(f"Runtime font cmap subtable header is truncated: {path}")
            subtable_length = struct.unpack(">I", table[subtable_offset + 2 : subtable_offset + 6])[0]
        else:
            if subtable_offset > len(table) - 8:
                _fail(f"Runtime font cmap subtable header is truncated: {path}")
            subtable_length = struct.unpack(">I", table[subtable_offset + 4 : subtable_offset + 8])[0]
        if (
            subtable_length < minimum_length
            or subtable_offset > len(table) - subtable_length
        ):
            _fail(f"Runtime font cmap subtable is truncated: {path}")
        if format_value == 4:
            _validate_cmap_format4(
                path,
                table[subtable_offset : subtable_offset + subtable_length],
                glyph_count,
            )
        elif format_value in {2, 6, 8, 10, 12, 13, 14}:
            _validate_cmap_variable_arrays(
                path,
                format_value,
                table[subtable_offset : subtable_offset + subtable_length],
                glyph_count,
            )
        elif format_value == 0:
            glyph_ids = table[subtable_offset + 6 : subtable_offset + 262]
            if any(glyph_id >= glyph_count for glyph_id in glyph_ids):
                _fail(f"Runtime font cmap format 0 maps outside maxp glyph count: {path}")


def _validate_name_table(path: Path, table: bytes) -> None:
    if len(table) < 6:
        _fail(f"Runtime font has no name records: {path}")
    format_value, count, string_offset = struct.unpack(">HHH", table[:6])
    if format_value not in {0, 1} or count == 0:
        _fail(f"Runtime font has no name records: {path}")
    records_end = 6 + count * 12
    if records_end > len(table):
        _fail(f"Runtime font name record array is truncated: {path}")
    lang_tag_count = 0
    extended_records_end = records_end
    if format_value == 1:
        if records_end > len(table) - 2:
            _fail(f"Runtime font language-tag record count is truncated: {path}")
        lang_tag_count = struct.unpack(">H", table[records_end : records_end + 2])[0]
        extended_records_end += 2 + lang_tag_count * 4
        if extended_records_end > len(table):
            _fail(f"Runtime font language-tag record array is truncated: {path}")
    if string_offset < extended_records_end or string_offset > len(table):
        _fail(f"Runtime font name string storage offset is invalid: {path}")

    for index in range(count):
        record_offset = 6 + index * 12
        length, relative_offset = struct.unpack(">HH", table[record_offset + 8 : record_offset + 12])
        if relative_offset > len(table) - string_offset or length > len(table) - string_offset - relative_offset:
            _fail(f"Runtime font name string is outside its table: {path}")
    for index in range(lang_tag_count):
        record_offset = records_end + 2 + index * 4
        length, relative_offset = struct.unpack(">HH", table[record_offset : record_offset + 4])
        if relative_offset > len(table) - string_offset or length > len(table) - string_offset - relative_offset:
            _fail(f"Runtime font language-tag string is outside its table: {path}")


def _ogg_crc(content: bytes) -> int:
    value = 0
    for byte in content:
        value ^= byte << 24
        for _ in range(8):
            value = (
                ((value << 1) ^ 0x04C11DB7)
                if value & 0x80000000
                else value << 1
            ) & 0xFFFFFFFF
    return value


def _validate_ogg_identification_packet(path: Path, packet: bytes) -> None:
    if packet.startswith(b"\x01vorbis"):
        if len(packet) != 30:
            _fail(f"Ogg Vorbis identification header has an invalid length: {path}")
        version = struct.unpack("<I", packet[7:11])[0]
        channels = packet[11]
        sample_rate = struct.unpack("<I", packet[12:16])[0]
        block_sizes = packet[28]
        small_block = block_sizes & 0x0F
        large_block = block_sizes >> 4
        if (
            version != 0
            or channels == 0
            or sample_rate == 0
            or not 6 <= small_block <= large_block <= 13
            or packet[29] != 1
        ):
            _fail(f"Ogg Vorbis identification header has invalid fields: {path}")
        return

    if packet.startswith(b"OpusHead"):
        if len(packet) < 19:
            _fail(f"Ogg OpusHead identification header is truncated: {path}")
        version = packet[8]
        channels = packet[9]
        mapping_family = packet[18]
        if version > 15 or channels == 0:
            _fail(f"Ogg OpusHead identification header has invalid fields: {path}")
        if mapping_family == 0:
            if channels > 2 or len(packet) != 19:
                _fail(f"Ogg OpusHead identification header has invalid mapping: {path}")
            return
        if len(packet) != 21 + channels:
            _fail(f"Ogg OpusHead identification header has an invalid mapping length: {path}")
        stream_count, coupled_count = packet[19:21]
        coded_channels = stream_count + coupled_count
        if stream_count == 0 or coupled_count > stream_count or coded_channels > 255:
            _fail(f"Ogg OpusHead identification header has invalid stream counts: {path}")
        if any(index != 255 and index >= coded_channels for index in packet[21:]):
            _fail(f"Ogg OpusHead identification header has an invalid channel map: {path}")
        return

    _fail(f"Ogg resource does not begin with Vorbis or Opus audio: {path}")


def _validate_ogg(path: Path, content: bytes) -> None:
    if len(content) < 28 or not content.startswith(b"OggS"):
        _fail(f"Ogg resource has an invalid or truncated first page: {path}")
    offset = 0
    streams: dict[int, int] = {}
    ended_streams: set[int] = set()
    continuation_pending: dict[int, bool] = {}
    first_packet: bytes | None = None
    first_packet_fragments = bytearray()
    first_packet_serial: int | None = None
    page_count = 0

    while offset < len(content):
        if len(content) - offset < 27 or content[offset : offset + 4] != b"OggS":
            _fail(f"Ogg resource has a truncated or invalid page header: {path}")
        if content[offset + 4] != 0:
            _fail(f"Ogg resource uses an unsupported stream version: {path}")
        header_type = content[offset + 5]
        if header_type & ~0x07:
            _fail(f"Ogg resource uses reserved page-header flags: {path}")
        continued = bool(header_type & 0x01)
        beginning = bool(header_type & 0x02)
        ending = bool(header_type & 0x04)
        serial = struct.unpack("<I", content[offset + 14 : offset + 18])[0]
        sequence = struct.unpack("<I", content[offset + 18 : offset + 22])[0]
        stored_crc = struct.unpack("<I", content[offset + 22 : offset + 26])[0]
        segment_count = content[offset + 26]
        table_end = offset + 27 + segment_count
        if table_end > len(content):
            _fail(f"Ogg resource has a truncated lacing table: {path}")
        lacing = content[offset + 27 : table_end]
        body_length = sum(lacing)
        page_end = table_end + body_length
        if page_end > len(content):
            _fail(f"Ogg resource has a truncated page body: {path}")

        page = bytearray(content[offset:page_end])
        page[22:26] = b"\x00\x00\x00\x00"
        if _ogg_crc(page) != stored_crc:
            _fail(f"Ogg resource has a page checksum mismatch: {path}")

        previous_sequence = streams.get(serial)
        if previous_sequence is None:
            if not beginning or continued or sequence != 0:
                _fail(f"Ogg logical stream does not begin with a BOS page: {path}")
            streams[serial] = sequence
            if first_packet_serial is None:
                first_packet_serial = serial
        else:
            if beginning:
                _fail(f"Ogg logical stream sets BOS after its first page: {path}")
            if serial in ended_streams or sequence != previous_sequence + 1:
                _fail(f"Ogg logical stream page sequence is invalid: {path}")
            if continued != continuation_pending[serial]:
                _fail(f"Ogg page continuation flag does not match packet lacing: {path}")
            streams[serial] = sequence

        continuation_pending[serial] = bool(lacing and lacing[-1] == 255)
        if ending and continuation_pending[serial]:
            _fail(f"Ogg EOS page ends with an incomplete packet: {path}")
        if ending:
            ended_streams.add(serial)
        if first_packet is None and serial == first_packet_serial:
            body = content[table_end:page_end]
            body_offset = 0
            for segment_length in lacing:
                first_packet_fragments.extend(
                    body[body_offset : body_offset + segment_length]
                )
                body_offset += segment_length
                if segment_length < 255:
                    first_packet = bytes(first_packet_fragments)
                    break
        page_count += 1
        offset = page_end

    if offset != len(content) or page_count <= 0 or not streams:
        _fail(f"Ogg resource does not contain a complete logical stream: {path}")
    if set(streams) != ended_streams:
        _fail(f"Ogg resource is missing an EOS page: {path}")
    if first_packet is None:
        _fail(f"Ogg resource does not begin with Vorbis or Opus audio: {path}")
    _validate_ogg_identification_packet(path, first_packet)


def _validate_wav(path: Path, content: bytes) -> None:
    if len(content) < 44 or content[:4] != b"RIFF" or content[8:12] != b"WAVE":
        _fail(f"WAV resource has an invalid or truncated RIFF header: {path}")
    riff_size = struct.unpack("<I", content[4:8])[0]
    if riff_size + 8 != len(content):
        _fail(f"WAV RIFF size does not match the file length: {path}")
    offset = 12
    found_format = False
    found_data = False
    pcm_block_align: int | None = None
    data_size: int | None = None
    while offset < len(content):
        if len(content) - offset < 8:
            _fail(f"WAV resource has a truncated chunk header: {path}")
        chunk_id = content[offset : offset + 4]
        chunk_size = struct.unpack("<I", content[offset + 4 : offset + 8])[0]
        data_start = offset + 8
        data_end = data_start + chunk_size
        if data_end > len(content):
            _fail(f"WAV resource has a truncated {chunk_id!r} chunk: {path}")
        if chunk_id == b"fmt ":
            if found_format or chunk_size < 16:
                _fail(f"WAV resource has an invalid fmt chunk: {path}")
            audio_format, channels, sample_rate, byte_rate, block_align, bits = struct.unpack(
                "<HHIIHH", content[data_start : data_start + 16]
            )
            if (
                audio_format <= 0
                or channels not in range(1, 33)
                or sample_rate <= 0
                or sample_rate > 768_000
                or byte_rate <= 0
                or block_align <= 0
                or bits <= 0
                or bits > 64
            ):
                _fail(f"WAV resource has invalid format values: {path}")
            pcm_format = audio_format
            if audio_format == 0xFFFE:
                if chunk_size < 40:
                    _fail(f"WAV extensible fmt chunk is truncated: {path}")
                extension_size = struct.unpack(
                    "<H", content[data_start + 16 : data_start + 18]
                )[0]
                if extension_size < 22 or 18 + extension_size > chunk_size:
                    _fail(f"WAV extensible fmt data is invalid: {path}")
                valid_bits = struct.unpack(
                    "<H", content[data_start + 18 : data_start + 20]
                )[0]
                subformat_guid = content[data_start + 24 : data_start + 40]
                guid_tail = b"\x00\x00\x10\x00\x80\x00\x00\xaa\x00\x38\x9b\x71"
                if valid_bits <= 0 or valid_bits > bits:
                    _fail(f"WAV extensible valid bits are outside the sample width: {path}")
                if (
                    len(subformat_guid) != 16
                    or subformat_guid[4:] != guid_tail
                ):
                    _fail(f"WAV extensible subformat GUID is invalid or unsupported: {path}")
                pcm_format = struct.unpack("<I", subformat_guid[:4])[0]
                if pcm_format not in {1, 3}:
                    _fail(f"WAV extensible subformat is unsupported: {path}")
            if pcm_format in {1, 3}:
                if bits % 8 != 0:
                    _fail(f"WAV PCM container width is not a whole number of bytes: {path}")
                expected_block_align = channels * ((bits + 7) // 8)
                if block_align != expected_block_align or byte_rate != sample_rate * block_align:
                    _fail(f"WAV resource has inconsistent PCM rate or block alignment: {path}")
                pcm_block_align = block_align
            if pcm_format == 3 and bits not in {32, 64}:
                _fail(f"WAV floating-point samples must use 32- or 64-bit precision: {path}")
            found_format = True
        elif chunk_id == b"data":
            if found_data or chunk_size <= 0:
                _fail(f"WAV resource has an invalid data chunk: {path}")
            found_data = True
            data_size = chunk_size
        offset = data_end + (chunk_size & 1)
    if offset != len(content) or not found_format or not found_data:
        _fail(f"WAV resource is missing complete fmt/data chunks: {path}")
    if pcm_block_align is not None and data_size is not None and data_size % pcm_block_align:
        _fail(f"WAV PCM data does not contain complete sample frames: {path}")


def _synchsafe_int(value: bytes) -> int:
    if len(value) != 4 or any(byte & 0x80 for byte in value):
        return -1
    return (value[0] << 21) | (value[1] << 14) | (value[2] << 7) | value[3]


def _is_mp3_frame_header(header: bytes) -> bool:
    if len(header) < 4:
        return False
    word = int.from_bytes(header[:4], "big")
    return (
        word >> 21 == 0x7FF
        and ((word >> 19) & 0x3) != 0x1
        and ((word >> 17) & 0x3) != 0x0
        and ((word >> 12) & 0xF) not in {0x0, 0xF}
        and ((word >> 10) & 0x3) != 0x3
        and (word & 0x3) != 0x2
    )


def _mp3_frame_length(header: bytes) -> int | None:
    if not _is_mp3_frame_header(header):
        return None
    word = int.from_bytes(header[:4], "big")
    version = (word >> 19) & 0x3
    layer = (word >> 17) & 0x3
    bitrate_index = (word >> 12) & 0xF
    sample_rate_index = (word >> 10) & 0x3
    padding = (word >> 9) & 0x1
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
    table = mpeg1_bitrates if version == 3 else mpeg2_bitrates
    bitrate_kbps = table[layer][bitrate_index]
    sample_rate = sample_rates[version][sample_rate_index]
    if layer == 3:
        return (12 * bitrate_kbps * 1000 // sample_rate + padding) * 4
    coefficient = 144 if version == 3 or layer == 2 else 72
    return coefficient * bitrate_kbps * 1000 // sample_rate + padding


def _validate_mp3(path: Path, content: bytes) -> None:
    if len(content) < 8:
        _fail(f"MP3 resource is too short to contain an audio frame: {path}")
    offset = 0
    if content.startswith(b"ID3"):
        if len(content) < 10:
            _fail(f"MP3 resource has a truncated ID3 header: {path}")
        header = content[:10]
        major_version = header[3]
        revision = header[4]
        flags = header[5]
        allowed_flags = {2: 0xC0, 3: 0xE0, 4: 0xF0}.get(major_version)
        if revision == 0xFF or allowed_flags is None or flags & ~allowed_flags:
            _fail(f"MP3 resource has an invalid ID3 version or flags: {path}")
        tag_size = _synchsafe_int(content[6:10])
        if tag_size < 0:
            _fail(f"MP3 resource has an invalid ID3 size: {path}")
        offset = 10 + tag_size
        if major_version == 4 and flags & 0x10:
            if offset > len(content) - 10:
                _fail(f"MP3 resource has a truncated ID3v2.4 footer: {path}")
            footer = content[offset : offset + 10]
            if footer != b"3DI" + header[3:10]:
                _fail(f"MP3 resource has an invalid ID3v2.4 footer: {path}")
            offset += 10
        if offset > len(content) - 4:
            _fail(f"MP3 ID3 tag consumes the entire file: {path}")
    scan_end = min(len(content) - 3, offset + MAX_MP3_PREFIX_SCAN_BYTES)
    for index in range(offset, scan_end):
        frame_length = _mp3_frame_length(content[index : index + 4])
        if frame_length is not None and index + frame_length <= len(content):
            return
    _fail(f"MP3 resource contains no complete valid audio frame: {path}")


def _validate_m4a(path: Path, content: bytes) -> None:
    if len(content) < 24:
        _fail(f"M4A resource is too short to contain required boxes: {path}")
    offset = 0
    boxes: list[bytes] = []
    saw_nonempty_media_data = False
    while offset < len(content):
        if len(content) - offset < 8:
            _fail(f"M4A resource has a truncated box header: {path}")
        size32 = struct.unpack(">I", content[offset : offset + 4])[0]
        box_type = content[offset + 4 : offset + 8]
        header_size = 8
        if any(value < 32 or value > 126 for value in box_type):
            _fail(f"M4A resource has an invalid box type: {path}")
        if size32 == 1:
            if len(content) - offset < 16:
                _fail(f"M4A resource has a truncated extended box header: {path}")
            box_size = struct.unpack(">Q", content[offset + 8 : offset + 16])[0]
            header_size = 16
        elif size32 == 0:
            box_size = len(content) - offset
        else:
            box_size = size32
        if box_size < header_size or offset > len(content) - box_size:
            _fail(f"M4A resource has an invalid box size: {path}")
        if box_type == b"ftyp":
            payload_size = box_size - header_size
            if boxes or payload_size < 8 or (payload_size - 8) % 4:
                _fail(f"M4A resource has an invalid or duplicate ftyp box: {path}")
        elif box_type == b"mdat" and box_size > header_size:
            saw_nonempty_media_data = True
        boxes.append(box_type)
        offset += box_size
        if size32 == 0:
            break
    if offset != len(content) or not boxes or boxes[0] != b"ftyp":
        _fail(f"M4A resource does not begin with a complete ftyp box: {path}")
    if b"moov" not in boxes or not saw_nonempty_media_data:
        _fail(f"M4A resource is missing moov or non-empty mdat audio boxes: {path}")


def _verify_asset_file(path: Path) -> str:
    content = _read_bounded_file(path, "runtime asset", MAX_RUNTIME_ASSET_BYTES)
    suffix = path.suffix.lower()
    if suffix == ".png":
        _validate_png(path, content)
        return "png"
    if suffix in {".ttf", ".otf"}:
        _validate_font(path, content)
        return "font"
    _fail(f"AssetPaths.kt references an unsupported release asset type: {path}")


def _verify_audio_file(path: Path) -> None:
    content = _read_bounded_file(path, "raw audio resource", MAX_AUDIO_BYTES)
    suffix = path.suffix.lower()
    if suffix not in SUPPORTED_AUDIO_SUFFIXES:
        _fail(f"Unsupported raw audio extension: {path}")
    if suffix == ".ogg":
        _validate_ogg(path, content)
    elif suffix == ".wav":
        _validate_wav(path, content)
    elif suffix == ".mp3":
        _validate_mp3(path, content)
    elif suffix == ".m4a":
        _validate_m4a(path, content)


def verify_release_source_assets(root: Path = ROOT) -> SourceAssetEvidence:
    try:
        resolved_root = checked_directory_path(root, "release source root")
    except StableFileError as exc:
        _fail(str(exc))
    asset_paths = parse_asset_paths(
        _read_text(resolved_root / ASSET_PATHS_SOURCE, "AssetPaths source")
    )
    required_audio = parse_required_audio(
        _read_text(resolved_root / RELEASE_PREPARER_SOURCE, "release preparer")
    )

    png_count = 0
    font_count = 0
    for relative_path in asset_paths:
        kind = _verify_asset_file(resolved_root / ASSETS_ROOT / relative_path)
        if kind == "png":
            png_count += 1
        elif kind == "font":
            font_count += 1

    raw_root = resolved_root / RAW_ROOT
    try:
        raw_root = checked_directory_path(raw_root, "Android raw-resource directory")
    except StableFileError as exc:
        _fail(str(exc))
    if not raw_root.is_dir():
        _fail(f"Missing Android raw-resource directory: {raw_root}")
    try:
        audio_paths = sorted(raw_root.iterdir())
    except OSError as exc:
        _fail(f"Could not inspect Android raw-resource directory {raw_root}: {exc}")
    if not audio_paths:
        _fail(f"Android raw-resource directory is empty: {raw_root}")

    by_stem: dict[str, list[Path]] = {}
    for path in audio_paths:
        try:
            metadata = path.lstat()
        except OSError as exc:
            _fail(f"Could not inspect Android raw-resource entry {path}: {exc}")
        if not stat.S_ISREG(metadata.st_mode):
            _fail(f"Android raw-resource entry must be a regular file: {path}")
        if (
            path.name != path.name.lower()
            or re.fullmatch(r"[a-z][a-z0-9_]*", path.stem) is None
            or path.suffix not in SUPPORTED_AUDIO_SUFFIXES
        ):
            _fail(f"Invalid Android raw-resource filename: {path}")
        by_stem.setdefault(path.stem, []).append(path)
        _verify_audio_file(path)

    for name, matches in by_stem.items():
        if len(matches) > 1:
            rendered = ", ".join(str(path) for path in matches)
            _fail(f"Duplicate raw audio resources for {name}: {rendered}")

    for name in required_audio:
        matches = by_stem.get(name, [])
        if not matches:
            _fail(f"Missing required raw audio resource: {name}")

    return SourceAssetEvidence(
        asset_count=len(asset_paths),
        png_count=png_count,
        font_count=font_count,
        required_audio_count=len(required_audio),
        checked_audio_count=len(audio_paths),
    )


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Verify checked-in Forest Run release assets before Gradle packaging."
    )
    parser.add_argument(
        "--root",
        type=Path,
        default=ROOT,
        help="Repository root to verify.",
    )
    parser.add_argument("--json", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = _parse_args()
    try:
        evidence = verify_release_source_assets(args.root)
    except SourceAssetVerificationError as error:
        raise SystemExit(str(error)) from error

    if args.json:
        print(json.dumps(asdict(evidence), sort_keys=True))
    else:
        print(
            "Verified "
            f"{evidence.asset_count} runtime asset(s) and "
            f"{evidence.checked_audio_count} raw audio resource(s)."
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
