import hashlib
import json
import os
import shutil
import struct
import tempfile
import unittest
import zlib
from pathlib import Path
from unittest import mock
from PIL import Image

import verify_curated_screenshot_set as verifier
from verify_curated_screenshot_set import (
    CuratedScreenshotError,
    verify_curated_set,
)


class CuratedScreenshotSetVerifierTest(unittest.TestCase):
    candidate_sha = "a" * 40
    apk_sha256 = "b" * 64

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

    def create_png(
        self,
        path: Path,
        width=1920,
        height=1080,
        marker=b"one",
        *,
        filter_type=0,
        interlace=0,
    ):
        colour = hashlib.sha256(marker).digest()[:3] + b"\xff"
        if interlace == 0:
            decoded = bytes((filter_type,)) + colour * width
            decoded *= height
        else:
            decoded_parts = []
            for start_x, start_y, step_x, step_y in (
                (0, 0, 8, 8),
                (4, 0, 8, 8),
                (0, 4, 4, 8),
                (2, 0, 4, 4),
                (0, 2, 2, 4),
                (1, 0, 2, 2),
                (0, 1, 1, 2),
            ):
                pass_width = max(0, (width - start_x + step_x - 1) // step_x)
                pass_height = max(0, (height - start_y + step_y - 1) // step_y)
                if pass_width and pass_height:
                    row = bytes((filter_type,)) + colour * pass_width
                    decoded_parts.append(row * pass_height)
            decoded = b"".join(decoded_parts)
        image_data = zlib.compress(decoded, level=9)
        ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, interlace)
        content = (
            b"\x89PNG\r\n\x1a\n"
            + self.png_chunk(b"IHDR", ihdr)
            + self.png_chunk(b"IDAT", image_data)
            + self.png_chunk(b"IEND", b"")
        )
        path.write_bytes(content)
        return hashlib.sha256(content).hexdigest()

    def create_indexed_png(
        self,
        path: Path,
        *,
        width=800,
        height=480,
        bit_depth=1,
        palette_entries=3,
        first_sample=0,
    ):
        row_bytes = (width * bit_depth + 7) // 8
        first_row = bytearray(row_bytes)
        first_row[0] = first_sample << (8 - bit_depth)
        decoded = (b"\x00" + bytes(first_row)) * height
        ihdr = struct.pack(">IIBBBBB", width, height, bit_depth, 3, 0, 0, 0)
        content = (
            b"\x89PNG\r\n\x1a\n"
            + self.png_chunk(b"IHDR", ihdr)
            + self.png_chunk(b"PLTE", bytes(range(palette_entries * 3)))
            + self.png_chunk(b"IDAT", zlib.compress(decoded))
            + self.png_chunk(b"IEND", b"")
        )
        path.write_bytes(content)
        return hashlib.sha256(content).hexdigest()

    def create_filtered_indexed_png(self, path: Path, filter_type: int):
        width, height, bit_depth = 800, 480, 1
        row_bytes = width // 8
        decoded_rows = []
        previous_row = bytes(row_bytes)
        for row_index in range(height):
            original_row = bytes((0xAA if row_index % 2 == 0 else 0x55,)) * row_bytes
            filtered_row = bytearray(row_bytes)
            for index, original_byte in enumerate(original_row):
                left = original_row[index - 1] if index else 0
                above = previous_row[index]
                upper_left = previous_row[index - 1] if index else 0
                if filter_type == 0:
                    predictor = 0
                elif filter_type == 1:
                    predictor = left
                elif filter_type == 2:
                    predictor = above
                elif filter_type == 3:
                    predictor = (left + above) // 2
                else:
                    base = left + above - upper_left
                    distances = (abs(base - left), abs(base - above), abs(base - upper_left))
                    predictor = (left, above, upper_left)[distances.index(min(distances))]
                filtered_row[index] = (original_byte - predictor) & 0xFF
            decoded_rows.append(bytes((filter_type,)) + bytes(filtered_row))
            previous_row = original_row

        ihdr = struct.pack(">IIBBBBB", width, height, bit_depth, 3, 0, 0, 0)
        content = (
            b"\x89PNG\r\n\x1a\n"
            + self.png_chunk(b"IHDR", ihdr)
            + self.png_chunk(b"PLTE", b"\x00\x00\x00\xff\xff\xff")
            + self.png_chunk(b"IDAT", zlib.compress(b"".join(decoded_rows)))
            + self.png_chunk(b"IEND", b"")
        )
        path.write_bytes(content)
        return hashlib.sha256(content).hexdigest()

    def evidence(
        self,
        raw_file,
        scenario,
        image_sha256,
        width=1920,
        height=1080,
        **overrides,
    ):
        values = {
            "schemaVersion": 1,
            "rawFile": raw_file,
            "scenario": scenario,
            "runMode": "SCREENSHOT_CAPTURE",
            "readinessMarker": (
                f"FOREST_RUN_SCENARIO_READY scenario={scenario} mode=SCREENSHOT_CAPTURE"
            ),
            "candidateSha": self.candidate_sha,
            "apkSha256": self.apk_sha256,
            "deviceSerial": "device-1",
            "packageName": "com.anurag9000.forestrun.debug",
            "activityName": "com.anurag9000.forestrun.MainActivity",
            "settleSeconds": 2.0,
            "capturedAtUtc": "2026-07-30T12:00:00+00:00",
            "imageSha256": image_sha256,
            "width": width,
            "height": height,
        }
        values.update(overrides)
        return values

    def session(self, **overrides):
        values = {
            "schemaVersion": 1,
            "candidateSha": self.candidate_sha,
            "originMainSha": self.candidate_sha,
            "apkSha256": self.apk_sha256,
            "deviceSerial": "device-1",
            "packageName": "com.anurag9000.forestrun.debug",
            "activityName": "com.anurag9000.forestrun.MainActivity",
            "capturedAtUtc": "2026-07-30T12:10:00+00:00",
            "screenshotCount": 2,
        }
        values.update(overrides)
        return values

    def write_session(self, root: Path, **overrides) -> Path:
        raw_dir = root / "raw"
        raw_dir.mkdir(parents=True, exist_ok=True)
        path = raw_dir / "capture-session.json"
        path.write_text(json.dumps(self.session(**overrides)), encoding="utf-8")
        return path

    def create_set(self, root: Path):
        final_dir = root / "final"
        final_dir.mkdir(parents=True)
        items = [
            {
                "order": 1,
                "raw_file": "01-opening.png",
                "final_file": "01-opening.png",
                "scenario": "OPENING_READABILITY",
                "title": "Opening Readability",
                "purpose": "Readable first-run scene.",
            },
            {
                "order": 2,
                "raw_file": "02-bloom.png",
                "final_file": "02-bloom.png",
                "scenario": "BLOOM_SHOWCASE",
                "title": "Bloom",
                "purpose": "Bloom transformation and HUD state.",
            },
        ]
        (root / "curation_manifest.json").write_text(
            json.dumps({"screenshots": items}), encoding="utf-8"
        )
        self.write_session(root)
        for index, item in enumerate(items, start=1):
            png_path = final_dir / item["final_file"]
            image_hash = self.create_png(
                png_path, marker=f"image-{index}".encode()
            )
            png_path.with_suffix(".capture.json").write_text(
                json.dumps(
                    self.evidence(
                        item["raw_file"],
                        item["scenario"],
                        image_hash,
                    )
                ),
                encoding="utf-8",
            )
        return items

    def write_manifest(self, root: Path, items) -> None:
        (root / "curation_manifest.json").write_text(
            json.dumps({"screenshots": items}), encoding="utf-8"
        )

    def test_capture_session_schema_version_rejects_json_boolean(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            self.create_set(root)
            for invalid in (True, False, 1.0, "1", None):
                with self.subTest(value=invalid):
                    self.write_session(root, schemaVersion=invalid)
                    with self.assertRaisesRegex(CuratedScreenshotError, "schemaVersion"):
                        verify_curated_set(root, self.candidate_sha)

    def test_capture_session_hash_fields_require_lowercase_hex(self):
        for field, value, pattern in (
            ("candidateSha", self.candidate_sha, "candidate/origin SHA"),
            ("originMainSha", self.candidate_sha, "candidate/origin SHA"),
            ("apkSha256", self.apk_sha256, "apkSha256"),
        ):
            with tempfile.TemporaryDirectory() as temporary_directory:
                root = Path(temporary_directory)
                self.create_set(root)
                self.write_session(root, **{field: value.upper()})
                with self.subTest(field=field), self.assertRaisesRegex(CuratedScreenshotError, pattern):
                    verify_curated_set(root, self.candidate_sha)

    def test_expected_candidate_sha_requires_lowercase_hex(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            self.create_set(root)
            with self.assertRaisesRegex(CuratedScreenshotError, "expected candidate SHA"):
                verify_curated_set(root, self.candidate_sha.upper())

    def test_valid_curated_set_is_bound_to_candidate(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            self.create_set(root)

            result = verify_curated_set(root, self.candidate_sha)

            self.assertEqual(2, result.count)
            self.assertEqual(self.candidate_sha, result.candidate_sha)
            self.assertEqual(self.apk_sha256, result.apk_sha256)
            self.assertEqual("device-1", result.device_serial)

    def test_missing_and_extra_pngs_are_rejected(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            self.create_set(root)
            (root / "final" / "01-opening.png").unlink()

            with self.assertRaisesRegex(CuratedScreenshotError, "missing"):
                verify_curated_set(root, self.candidate_sha)

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            self.create_set(root)
            self.create_png(root / "final" / "extra.png", marker=b"extra")

            with self.assertRaisesRegex(CuratedScreenshotError, "extra"):
                verify_curated_set(root, self.candidate_sha)

    def test_symlinked_curated_png_is_rejected_even_when_bytes_match(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            items = self.create_set(root)
            png_path = root / "final" / items[0]["final_file"]
            target = root / "outside-capture.png"
            target.write_bytes(png_path.read_bytes())
            png_path.unlink()
            try:
                png_path.symlink_to(target)
            except OSError as exc:
                self.skipTest(f"symbolic links unavailable: {exc}")

            with self.assertRaisesRegex(CuratedScreenshotError, "symbolic link"):
                verify_curated_set(root, self.candidate_sha)

    def test_final_directory_parent_swap_to_symlink_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            items = self.create_set(root)
            final_dir = root / "final"
            external = root.parent / f"{root.name}-external-final"
            first_png = final_dir / items[0]["final_file"]
            original_open = os.open
            swapped = False

            def swap_then_open(path, flags, *args, **kwargs):
                nonlocal swapped
                is_path_open = isinstance(path, (str, Path)) and Path(path) == first_png
                is_component_open = (
                    path == "final" and kwargs.get("dir_fd") is not None
                )
                if not swapped and (is_path_open or is_component_open):
                    final_dir.rename(external)
                    final_dir.symlink_to(external, target_is_directory=True)
                    swapped = True
                return original_open(path, flags, *args, **kwargs)

            with mock.patch.object(
                verifier.os, "open", side_effect=swap_then_open
            ):
                with self.assertRaisesRegex(CuratedScreenshotError, "symbolic link"):
                    verify_curated_set(root, self.candidate_sha)

            self.assertTrue(swapped)
            shutil.rmtree(external)

    def test_missing_and_extra_sidecars_are_rejected(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            self.create_set(root)
            (root / "final" / "02-bloom.capture.json").unlink()

            with self.assertRaisesRegex(CuratedScreenshotError, "missing_sidecars"):
                verify_curated_set(root, self.candidate_sha)

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            self.create_set(root)
            (root / "final" / "stale.capture.json").write_text(
                "{}", encoding="utf-8"
            )

            with self.assertRaisesRegex(CuratedScreenshotError, "extra_sidecars"):
                verify_curated_set(root, self.candidate_sha)

    def test_duplicate_raw_scenario_and_title_coverage_is_rejected(self):
        for key in ("raw_file", "scenario", "title"):
            with self.subTest(key=key):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    root = Path(temporary_directory)
                    items = self.create_set(root)
                    items[1][key] = items[0][key]
                    self.write_manifest(root, items)

                    with self.assertRaisesRegex(
                        CuratedScreenshotError, f"duplicate {key}"
                    ):
                        verify_curated_set(root, self.candidate_sha)

    def test_order_and_marketing_copy_are_required(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            items = self.create_set(root)
            items[1]["order"] = 3
            self.write_manifest(root, items)

            with self.assertRaisesRegex(CuratedScreenshotError, "order must equal 2"):
                verify_curated_set(root, self.candidate_sha)

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            items = self.create_set(root)
            items[0]["purpose"] = " "
            self.write_manifest(root, items)

            with self.assertRaisesRegex(CuratedScreenshotError, "purpose must be non-blank"):
                verify_curated_set(root, self.candidate_sha)

    def test_stale_candidate_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            self.create_set(root)

            with self.assertRaisesRegex(CuratedScreenshotError, "does not match|mismatch"):
                verify_curated_set(root, "c" * 40)

    def test_mixed_device_identity_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            items = self.create_set(root)
            second_path = root / "final" / items[1]["final_file"]
            sidecar = second_path.with_suffix(".capture.json")
            payload = json.loads(sidecar.read_text(encoding="utf-8"))
            payload["deviceSerial"] = "device-2"
            sidecar.write_text(json.dumps(payload), encoding="utf-8")

            with self.assertRaisesRegex(CuratedScreenshotError, "deviceSerial"):
                verify_curated_set(root, self.candidate_sha)

    def test_capture_session_is_required_and_candidate_bound(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            self.create_set(root)
            (root / "raw" / "capture-session.json").unlink()

            with self.assertRaisesRegex(CuratedScreenshotError, "Missing capture session"):
                verify_curated_set(root, self.candidate_sha)

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            self.create_set(root)
            self.write_session(root, originMainSha="c" * 40)

            with self.assertRaisesRegex(CuratedScreenshotError, "originMainSha mismatch"):
                verify_curated_set(root, self.candidate_sha)

    def test_capture_session_count_identity_and_timestamp_must_match_sidecars(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            self.create_set(root)
            self.write_session(root, screenshotCount=3)

            with self.assertRaisesRegex(CuratedScreenshotError, "screenshotCount mismatch"):
                verify_curated_set(root, self.candidate_sha)

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            self.create_set(root)
            self.write_session(root, apkSha256="d" * 64)

            with self.assertRaisesRegex(CuratedScreenshotError, "session identity"):
                verify_curated_set(root, self.candidate_sha)

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            self.create_set(root)
            self.write_session(root, capturedAtUtc="2026-07-30T11:59:59+00:00")

            with self.assertRaisesRegex(CuratedScreenshotError, "timestamp predates"):
                verify_curated_set(root, self.candidate_sha)

    def test_corrupt_png_crc_is_rejected_before_sidecar_trust(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            items = self.create_set(root)
            png_path = root / "final" / items[0]["final_file"]
            content = bytearray(png_path.read_bytes())
            idat_offset = content.index(b"IDAT")
            content[idat_offset + 5] ^= 0x01
            png_path.write_bytes(content)

            with self.assertRaisesRegex(CuratedScreenshotError, "CRC mismatch"):
                verify_curated_set(root, self.candidate_sha)

    def test_indexed_png_palette_must_fit_declared_bit_depth(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            items = self.create_set(root)
            png_path = root / "final" / items[0]["final_file"]
            image_hash = self.create_indexed_png(png_path, bit_depth=1, palette_entries=3)
            sidecar = png_path.with_suffix(".capture.json")
            evidence = json.loads(sidecar.read_text(encoding="utf-8"))
            evidence["imageSha256"] = image_hash
            evidence["width"] = 800
            evidence["height"] = 480
            sidecar.write_text(json.dumps(evidence), encoding="utf-8")

            with self.assertRaisesRegex(CuratedScreenshotError, "palette"):
                verify_curated_set(root, self.candidate_sha)

    def test_indexed_png_pixels_must_reference_existing_palette_entries(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            items = self.create_set(root)
            png_path = root / "final" / items[0]["final_file"]
            image_hash = self.create_indexed_png(
                png_path,
                bit_depth=1,
                palette_entries=1,
                first_sample=1,
            )
            sidecar = png_path.with_suffix(".capture.json")
            evidence = json.loads(sidecar.read_text(encoding="utf-8"))
            evidence["imageSha256"] = image_hash
            evidence["width"] = 800
            evidence["height"] = 480
            sidecar.write_text(json.dumps(evidence), encoding="utf-8")

            with self.assertRaisesRegex(CuratedScreenshotError, "missing palette entry"):
                verify_curated_set(root, self.candidate_sha)

    def test_indexed_png_filter_reconstruction_accepts_all_five_filter_types(self):
        for filter_type in range(5):
            with self.subTest(filter_type=filter_type), tempfile.TemporaryDirectory() as temporary_directory:
                root = Path(temporary_directory)
                items = self.create_set(root)
                png_path = root / "final" / items[0]["final_file"]
                image_hash = self.create_filtered_indexed_png(png_path, filter_type)
                with Image.open(png_path) as image:
                    image.load()
                    self.assertEqual((800, 480), image.size)
                    self.assertEqual("P", image.mode)

                sidecar = png_path.with_suffix(".capture.json")
                evidence = json.loads(sidecar.read_text(encoding="utf-8"))
                evidence["imageSha256"] = image_hash
                evidence["width"] = 800
                evidence["height"] = 480
                sidecar.write_text(json.dumps(evidence), encoding="utf-8")

                verified = verify_curated_set(root, self.candidate_sha)

                self.assertEqual(len(items), verified.count)

    def test_png_chunk_reserved_type_bit_must_be_uppercase(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            items = self.create_set(root)
            png_path = root / "final" / items[0]["final_file"]
            width, height = 800, 480
            pixel = hashlib.sha256(b"reserved-bit").digest()[:3] + b"\xff"
            decoded = (b"\x00" + pixel * width) * height
            ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
            content = (
                b"\x89PNG\r\n\x1a\n"
                + self.png_chunk(b"IHDR", ihdr)
                + self.png_chunk(b"abca", b"")
                + self.png_chunk(b"IDAT", zlib.compress(decoded))
                + self.png_chunk(b"IEND", b"")
            )
            png_path.write_bytes(content)
            sidecar = png_path.with_suffix(".capture.json")
            evidence = json.loads(sidecar.read_text(encoding="utf-8"))
            evidence["imageSha256"] = hashlib.sha256(content).hexdigest()
            evidence["width"] = width
            evidence["height"] = height
            sidecar.write_text(json.dumps(evidence), encoding="utf-8")

            with self.assertRaisesRegex(CuratedScreenshotError, "reserved PNG chunk type"):
                verify_curated_set(root, self.candidate_sha)

    def test_truecolor_alpha_png_must_not_contain_trns_chunk(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            items = self.create_set(root)
            png_path = root / "final" / items[0]["final_file"]
            width, height = 800, 480
            pixel = hashlib.sha256(b"transparency").digest()[:3] + b"\xff"
            decoded = (b"\x00" + pixel * width) * height
            ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
            content = (
                b"\x89PNG\r\n\x1a\n"
                + self.png_chunk(b"IHDR", ihdr)
                + self.png_chunk(b"tRNS", b"\x00" * 6)
                + self.png_chunk(b"IDAT", zlib.compress(decoded))
                + self.png_chunk(b"IEND", b"")
            )
            png_path.write_bytes(content)
            sidecar = png_path.with_suffix(".capture.json")
            evidence = json.loads(sidecar.read_text(encoding="utf-8"))
            evidence["imageSha256"] = hashlib.sha256(content).hexdigest()
            evidence["width"] = width
            evidence["height"] = height
            sidecar.write_text(json.dumps(evidence), encoding="utf-8")

            with self.assertRaisesRegex(CuratedScreenshotError, "tRNS"):
                verify_curated_set(root, self.candidate_sha)

    def test_truncated_png_and_trailing_payload_are_rejected(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            items = self.create_set(root)
            png_path = root / "final" / items[0]["final_file"]
            png_path.write_bytes(png_path.read_bytes()[:-12])

            with self.assertRaisesRegex(
                CuratedScreenshotError,
                "missing required PNG chunks|truncated PNG",
            ):
                verify_curated_set(root, self.candidate_sha)

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            items = self.create_set(root)
            png_path = root / "final" / items[0]["final_file"]
            png_path.write_bytes(png_path.read_bytes() + b"stale-payload")

            with self.assertRaisesRegex(CuratedScreenshotError, "trailing bytes"):
                verify_curated_set(root, self.candidate_sha)

    def test_png_scanline_filter_values_are_validated(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            items = self.create_set(root)
            png_path = root / "final" / items[0]["final_file"]
            image_hash = self.create_png(
                png_path,
                width=800,
                height=480,
                marker=b"invalid-filter",
                filter_type=5,
            )
            sidecar = png_path.with_suffix(".capture.json")
            evidence = json.loads(sidecar.read_text(encoding="utf-8"))
            evidence["imageSha256"] = image_hash
            evidence["width"] = 800
            evidence["height"] = 480
            sidecar.write_text(json.dumps(evidence), encoding="utf-8")

            with self.assertRaisesRegex(CuratedScreenshotError, "scanline filter"):
                verify_curated_set(root, self.candidate_sha)

    def test_valid_adam7_interlaced_png_passes_scanline_validation(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            items = self.create_set(root)
            png_path = root / "final" / items[0]["final_file"]
            image_hash = self.create_png(
                png_path,
                width=800,
                height=480,
                marker=b"valid-adam7",
                interlace=1,
            )
            sidecar = png_path.with_suffix(".capture.json")
            evidence = json.loads(sidecar.read_text(encoding="utf-8"))
            evidence["imageSha256"] = image_hash
            evidence["width"] = 800
            evidence["height"] = 480
            sidecar.write_text(json.dumps(evidence), encoding="utf-8")

            verified = verify_curated_set(root, self.candidate_sha)

            self.assertEqual(len(items), verified.count)

    def test_ambiguous_curation_manifest_and_capture_session_are_rejected(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            self.create_set(root)
            manifest = root / "curation_manifest.json"
            original_manifest = manifest.read_text(encoding="utf-8")
            manifest.write_text(
                original_manifest.replace(
                    '"screenshots":',
                    '"extra": 1, "extra": 2, "screenshots":',
                    1,
                ),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(CuratedScreenshotError, "duplicate JSON object key"):
                verify_curated_set(root, self.candidate_sha)

            manifest.write_text(original_manifest, encoding="utf-8")
            session = root / "raw" / "capture-session.json"
            original_session = session.read_text(encoding="utf-8")
            session.write_text(
                original_session.replace(
                    '"candidateSha":',
                    '"candidateSha": "' + "c" * 40 + '", "candidateSha":',
                    1,
                ),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(CuratedScreenshotError, "duplicate JSON object key"):
                verify_curated_set(root, self.candidate_sha)

if __name__ == "__main__":
    unittest.main()
