import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from PIL import Image

from verify_store_graphics import StoreGraphicsError, verify_store_graphics

ROOT = Path(__file__).resolve().parent.parent


class StoreGraphicsVerifierTest(unittest.TestCase):
    candidate = "a" * 40

    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.graphics = self.root / "release/google-play/graphics"
        self.graphics.mkdir(parents=True)
        self.source_paths = [
            "scripts/generate_store_assets.py",
            "app/src/main/assets/fonts/PressStart2P-Regular.ttf",
            "app/src/main/assets/sprites/char/runner_girl_technical_48frame.png",
            "app/src/main/assets/sprites/animals/fox_4frames.png",
            "app/src/main/assets/sprites/birds/owl_4frames.png",
            "app/src/main/assets/sprites/plants/lily_of_valley_4frames.png",
        ]
        for index, relative in enumerate(self.source_paths):
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(f"source-{index}".encode())
        self.write_graphic("feature-graphic.png", (1024, 500), (20, 80, 40))
        self.write_graphic("promo-square.png", (512, 512), (80, 30, 90))
        self.write_manifest()

    def tearDown(self) -> None:
        self.temp.cleanup()

    @staticmethod
    def digest(path: Path) -> str:
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def write_graphic(self, name, size, colour) -> None:
        Image.new("RGB", size, colour).save(self.graphics / name, format="PNG")

    def write_manifest(self, **overrides) -> None:
        payload = {
            "schemaVersion": 1,
            "generatedBy": "scripts/generate_store_assets.py",
            "candidateSha": self.candidate,
            "sourceAssets": [
                {
                    "path": relative,
                    "bytes": (self.root / relative).stat().st_size,
                    "sha256": self.digest(self.root / relative),
                }
                for relative in self.source_paths
            ],
            "outputs": [
                {
                    "file": name,
                    "width": size[0],
                    "height": size[1],
                    "mode": "RGB",
                    "bytes": (self.graphics / name).stat().st_size,
                    "sha256": self.digest(self.graphics / name),
                }
                for name, size in (
                    ("feature-graphic.png", (1024, 500)),
                    ("promo-square.png", (512, 512)),
                )
            ],
        }
        payload.update(overrides)
        (self.graphics / "graphics_manifest.json").write_text(
            json.dumps(payload), encoding="utf-8"
        )

    def test_valid_graphics_are_accepted(self) -> None:
        result = verify_store_graphics(self.root, self.graphics, self.candidate)
        self.assertEqual(2, len(result["outputs"]))

    def test_candidate_and_source_staleness_are_rejected(self) -> None:
        with self.assertRaisesRegex(StoreGraphicsError, "candidate"):
            verify_store_graphics(self.root, self.graphics, "b" * 40)

        (self.root / self.source_paths[-1]).write_bytes(b"changed")
        with self.assertRaisesRegex(StoreGraphicsError, "stale"):
            verify_store_graphics(self.root, self.graphics, self.candidate)

    def test_tampered_output_and_unmanifested_file_are_rejected(self) -> None:
        self.write_graphic("promo-square.png", (512, 512), (1, 2, 3))
        with self.assertRaisesRegex(StoreGraphicsError, "hash/size"):
            verify_store_graphics(self.root, self.graphics, self.candidate)

        self.write_manifest()
        (self.graphics / "stale.png").write_bytes(b"stale")
        with self.assertRaisesRegex(StoreGraphicsError, "unmanifested"):
            verify_store_graphics(self.root, self.graphics, self.candidate)

    def test_duplicate_or_incomplete_manifest_entries_are_rejected(self) -> None:
        payload = json.loads((self.graphics / "graphics_manifest.json").read_text())
        payload["outputs"].append(dict(payload["outputs"][0]))
        self.write_manifest(outputs=payload["outputs"])
        with self.assertRaisesRegex(StoreGraphicsError, "duplicate"):
            verify_store_graphics(self.root, self.graphics, self.candidate)

        payload = json.loads((self.graphics / "graphics_manifest.json").read_text())
        self.write_manifest(outputs=payload["outputs"][:1])
        with self.assertRaisesRegex(StoreGraphicsError, "incomplete"):
            verify_store_graphics(self.root, self.graphics, self.candidate)

    def test_wrong_dimensions_and_mode_evidence_are_rejected(self) -> None:
        payload = json.loads((self.graphics / "graphics_manifest.json").read_text())
        payload["outputs"][0]["width"] = 999
        self.write_manifest(outputs=payload["outputs"])
        with self.assertRaisesRegex(StoreGraphicsError, "dimensions"):
            verify_store_graphics(self.root, self.graphics, self.candidate)

        payload = json.loads((self.graphics / "graphics_manifest.json").read_text())
        payload["outputs"][0]["mode"] = "RGBA"
        self.write_manifest(outputs=payload["outputs"])
        with self.assertRaisesRegex(StoreGraphicsError, "mode"):
            verify_store_graphics(self.root, self.graphics, self.candidate)


class StoreGraphicsReleaseContractTest(unittest.TestCase):
    def test_canonical_wrapper_verifies_graphics_before_play_preparer(self) -> None:
        source = (ROOT / "scripts/prepare_main_release.sh").read_text(encoding="utf-8")
        graphics_index = source.index("verify_store_graphics.py")
        preparer_index = source.index("prepare_play_release.py")
        self.assertLess(graphics_index, preparer_index)
        self.assertIn('--candidate-sha "${candidate_sha}"', source[graphics_index:preparer_index])
        self.assertIn('--root "${ROOT}"', source[graphics_index:preparer_index])


if __name__ == "__main__":
    unittest.main()
