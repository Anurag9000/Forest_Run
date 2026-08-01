#!/usr/bin/env python3
from __future__ import annotations

import argparse
import ast
import json
import math
import re
import struct
import zlib
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import NoReturn

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
REQUIRED_FONT_TABLES = {b"cmap", b"head", b"hhea", b"hmtx", b"maxp", b"name"}
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
    if not path.is_file():
        _fail(f"Missing {label}: {path}")
    try:
        return path.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as error:
        _fail(f"Could not read {label} {path}: {error}")


def parse_asset_paths(source: str) -> tuple[str, ...]:
    paths = tuple(
        match.group(1)
        for match in re.finditer(r'\bconst\s+val\s+\w+\s*=\s*"([^"]+)"', source)
    )
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

    value: object | None = None
    for node in module.body:
        if not isinstance(node, ast.Assign):
            continue
        if any(isinstance(target, ast.Name) and target.id == "REQUIRED_AUDIO" for target in node.targets):
            try:
                value = ast.literal_eval(node.value)
            except (ValueError, TypeError, SyntaxError) as error:
                _fail(f"REQUIRED_AUDIO must be a literal sequence: {error}")
            break

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
    if not path.is_file():
        _fail(f"Missing {label}: {path}")
    try:
        size = path.stat().st_size
    except OSError as error:
        _fail(f"Could not stat {label} {path}: {error}")
    if size <= 0:
        _fail(f"Empty {label}: {path}")
    if size > maximum_bytes:
        _fail(
            f"{label.capitalize()} exceeds the {maximum_bytes}-byte safety limit: "
            f"{path} is {size} bytes"
        )
    try:
        content = path.read_bytes()
    except OSError as error:
        _fail(f"Could not read {label} {path}: {error}")
    if len(content) != size:
        _fail(f"{label.capitalize()} changed while being read: {path}")
    return content


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
    if interlace == 0:
        channels = PNG_CHANNELS_BY_COLOR_TYPE[color_type]
        row_bytes = math.ceil(width * channels * bit_depth / 8)
        if len(decoded) != height * (row_bytes + 1):
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
    directory_end = 12 + num_tables * 16
    if directory_end > len(content):
        _fail(f"Runtime font has a truncated table directory: {path}")

    tables: dict[bytes, tuple[int, int]] = {}
    for index in range(num_tables):
        entry = content[12 + index * 16 : 28 + index * 16]
        tag, _checksum, offset, length = struct.unpack(">4sIII", entry)
        if any(value < 32 or value > 126 for value in tag):
            _fail(f"Runtime font contains an invalid table tag: {path}")
        if tag in tables:
            _fail(f"Runtime font contains a duplicate {tag!r} table: {path}")
        if offset < directory_end or length <= 0 or offset > len(content) - length:
            _fail(f"Runtime font table {tag!r} is outside the file: {path}")
        tables[tag] = (offset, length)

    missing = sorted(REQUIRED_FONT_TABLES.difference(tables))
    if missing:
        rendered = ", ".join(tag.decode("ascii") for tag in missing)
        _fail(f"Runtime font is missing required tables ({rendered}): {path}")

    head_offset, head_length = tables[b"head"]
    if head_length < 54 or struct.unpack(">I", content[head_offset + 12 : head_offset + 16])[0] != 0x5F0F3CF5:
        _fail(f"Runtime font has an invalid head table: {path}")
    maxp_offset, maxp_length = tables[b"maxp"]
    if maxp_length < 6 or struct.unpack(">H", content[maxp_offset + 4 : maxp_offset + 6])[0] <= 0:
        _fail(f"Runtime font has no glyphs in its maxp table: {path}")
    cmap_offset, cmap_length = tables[b"cmap"]
    if cmap_length < 4 or struct.unpack(">H", content[cmap_offset + 2 : cmap_offset + 4])[0] <= 0:
        _fail(f"Runtime font has no cmap encoding records: {path}")
    name_offset, name_length = tables[b"name"]
    if name_length < 6 or struct.unpack(">H", content[name_offset + 2 : name_offset + 4])[0] <= 0:
        _fail(f"Runtime font has no name records: {path}")


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


def _validate_ogg(path: Path, content: bytes) -> None:
    if len(content) < 28 or not content.startswith(b"OggS"):
        _fail(f"Ogg resource has an invalid or truncated first page: {path}")
    offset = 0
    streams: dict[int, int] = {}
    ended_streams: set[int] = set()
    first_packet: bytes | None = None
    page_count = 0

    while offset < len(content):
        if len(content) - offset < 27 or content[offset : offset + 4] != b"OggS":
            _fail(f"Ogg resource has a truncated or invalid page header: {path}")
        if content[offset + 4] != 0:
            _fail(f"Ogg resource uses an unsupported stream version: {path}")
        header_type = content[offset + 5]
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
            if header_type & 0x02 == 0 or sequence != 0:
                _fail(f"Ogg logical stream does not begin with a BOS page: {path}")
            streams[serial] = sequence
        else:
            if serial in ended_streams or sequence != previous_sequence + 1:
                _fail(f"Ogg logical stream page sequence is invalid: {path}")
            streams[serial] = sequence

        if header_type & 0x04:
            ended_streams.add(serial)
        if first_packet is None:
            body = content[table_end:page_end]
            packet_length = 0
            for segment_length in lacing:
                packet_length += segment_length
                if segment_length < 255:
                    first_packet = body[:packet_length]
                    break
        page_count += 1
        offset = page_end

    if offset != len(content) or page_count <= 0 or not streams:
        _fail(f"Ogg resource does not contain a complete logical stream: {path}")
    if set(streams) != ended_streams:
        _fail(f"Ogg resource is missing an EOS page: {path}")
    if first_packet is None or not (
        first_packet.startswith(b"\x01vorbis")
        or first_packet.startswith(b"OpusHead")
    ):
        _fail(f"Ogg resource does not begin with Vorbis or Opus audio: {path}")


def _validate_wav(path: Path, content: bytes) -> None:
    if len(content) < 44 or content[:4] != b"RIFF" or content[8:12] != b"WAVE":
        _fail(f"WAV resource has an invalid or truncated RIFF header: {path}")
    riff_size = struct.unpack("<I", content[4:8])[0]
    if riff_size + 8 != len(content):
        _fail(f"WAV RIFF size does not match the file length: {path}")
    offset = 12
    found_format = False
    found_data = False
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
            found_format = True
        elif chunk_id == b"data":
            if found_data or chunk_size <= 0:
                _fail(f"WAV resource has an invalid data chunk: {path}")
            found_data = True
        offset = data_end + (chunk_size & 1)
    if offset != len(content) or not found_format or not found_data:
        _fail(f"WAV resource is missing complete fmt/data chunks: {path}")


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
    )


def _validate_mp3(path: Path, content: bytes) -> None:
    if len(content) < 8:
        _fail(f"MP3 resource is too short to contain an audio frame: {path}")
    offset = 0
    if content.startswith(b"ID3"):
        if len(content) < 10:
            _fail(f"MP3 resource has a truncated ID3 header: {path}")
        tag_size = _synchsafe_int(content[6:10])
        if tag_size < 0:
            _fail(f"MP3 resource has an invalid ID3 size: {path}")
        offset = 10 + tag_size
        if content[5] & 0x10:
            offset += 10
        if offset > len(content) - 4:
            _fail(f"MP3 ID3 tag consumes the entire file: {path}")
    scan_end = min(len(content) - 3, offset + MAX_MP3_PREFIX_SCAN_BYTES)
    if not any(
        _is_mp3_frame_header(content[index : index + 4])
        for index in range(offset, scan_end)
    ):
        _fail(f"MP3 resource contains no valid audio frame header: {path}")


def _validate_m4a(path: Path, content: bytes) -> None:
    if len(content) < 24:
        _fail(f"M4A resource is too short to contain required boxes: {path}")
    offset = 0
    boxes: list[bytes] = []
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
        boxes.append(box_type)
        offset += box_size
        if size32 == 0:
            break
    if offset != len(content) or not boxes or boxes[0] != b"ftyp":
        _fail(f"M4A resource does not begin with a complete ftyp box: {path}")
    if b"moov" not in boxes or b"mdat" not in boxes:
        _fail(f"M4A resource is missing moov or mdat audio boxes: {path}")


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
    resolved_root = root.expanduser().resolve()
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
    if not raw_root.is_dir():
        _fail(f"Missing Android raw-resource directory: {raw_root}")
    audio_paths = sorted(path for path in raw_root.iterdir() if path.is_file())
    if not audio_paths:
        _fail(f"Android raw-resource directory is empty: {raw_root}")

    by_stem: dict[str, list[Path]] = {}
    for path in audio_paths:
        by_stem.setdefault(path.stem, []).append(path)
        _verify_audio_file(path)

    for name in required_audio:
        matches = by_stem.get(name, [])
        if not matches:
            _fail(f"Missing required raw audio resource: {name}")
        if len(matches) != 1:
            rendered = ", ".join(str(path) for path in matches)
            _fail(f"Duplicate raw audio resources for {name}: {rendered}")

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
