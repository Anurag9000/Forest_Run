import hashlib
import json
import struct
import tempfile
import unittest
import zlib
from pathlib import Path

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

    def create_png(self, path: Path, width=1920, height=1080, marker=b"one"):
        colour = hashlib.sha256(marker).digest()[:3] + b"\xff"
        scanline = b"\x00" + colour * width
        image_data = zlib.compress(scanline * height, level=9)
        ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
        content = (
            b"\x89PNG\r\n\x1a\n"
            + self.png_chunk(b"IHDR", ihdr)
            + self.png_chunk(b"IDAT", image_data)
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

            with self.assertRaisesRegex(CuratedScreenshotError, "does not match"):
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


if __name__ == "__main__":
    unittest.main()
