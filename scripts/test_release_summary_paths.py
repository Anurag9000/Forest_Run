import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from verify_release_summary import ReleaseSummaryError, verify_release_summary


class ReleaseSummaryPathTest(unittest.TestCase):
    @staticmethod
    def _digest(path: Path) -> str:
        return hashlib.sha256(path.read_bytes()).hexdigest()

    @classmethod
    def _file_fact(cls, root: Path, path: Path) -> dict:
        return {
            "path": str(path.relative_to(root)).replace("\\", "/"),
            "bytes": path.stat().st_size,
            "sha256": cls._digest(path),
        }

    @classmethod
    def _metadata_fact(cls, root: Path, path: Path) -> dict:
        text = path.read_text(encoding="utf-8")
        return {
            "path": str(path.relative_to(root)).replace("\\", "/"),
            "characters": len(text),
            "sha256": cls._digest(path),
        }

    @classmethod
    def _public_release_facts(cls, root: Path) -> tuple[list[dict], list[dict], list[dict]]:
        graphics_paths = [
            root / "release/google-play/graphics/feature-graphic.png",
            root / "release/google-play/graphics/promo-square.png",
        ]
        for index, path in enumerate(graphics_paths):
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(f"graphic-{index}".encode("utf-8"))

        metadata_paths = [
            root / "release/google-play/metadata/en-US/title.txt",
            root / "release/google-play/metadata/en-US/short-description.txt",
            root / "release/google-play/metadata/en-US/full-description.txt",
        ]
        metadata_text = [
            "Forest Run",
            "Run gently through a living forest that remembers your choices.",
            (
                "Forest Run is a handcrafted endless runner where mercy changes the path. "
                "Collect Seeds, meet the forest, and return to a persistent Garden that "
                "remembers your choices across journeys."
            ),
        ]
        for path, text in zip(metadata_paths, metadata_text, strict=True):
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")

        screenshot_paths: list[Path] = []
        for index in range(4):
            path = root / f"release/google-play/screenshots/final/shot-{index}.png"
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(f"screenshot-{index}".encode("utf-8"))
            screenshot_paths.append(path)

        return (
            [cls._file_fact(root, path) for path in graphics_paths],
            [cls._metadata_fact(root, path) for path in metadata_paths],
            [cls._file_fact(root, path) for path in screenshot_paths],
        )

    def test_safe_windows_style_relative_artifact_paths_are_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            release = root / "release/google-play"
            release.mkdir(parents=True)
            bundle = root / "app/build/outputs/bundle/release/app-release.aab"
            mapping = root / "app/build/outputs/mapping/release/mapping.txt"
            bundle.parent.mkdir(parents=True)
            mapping.parent.mkdir(parents=True)
            bundle.write_bytes(b"bundle")
            mapping.write_bytes(b"mapping")
            graphics, metadata, screenshots = self._public_release_facts(root)
            candidate = "a" * 40
            bundle_hash = self._digest(bundle)
            payload = {
                "candidate": {"sha": candidate, "branch": "main"},
                "identity": {
                    "application_id": "com.anurag9000.forestrun",
                    "version_name": "1.2.3",
                    "version_code": 7,
                },
                "graphics": graphics,
                "metadata": metadata,
                "screenshots": {
                    "images": screenshots,
                    "candidate_sha": candidate,
                    "package_name": "com.anurag9000.forestrun.debug",
                },
                "audio": [f"audio_{index}" for index in range(15)],
                "bundle": {
                    "path": "app\\build\\outputs\\bundle\\release\\app-release.aab",
                    "bytes": bundle.stat().st_size,
                    "sha256": bundle_hash,
                    "application_id": "com.anurag9000.forestrun",
                    "version_code": 7,
                    "version_name": "1.2.3",
                    "signature_verified": True,
                    "signer_sha256": "c" * 64,
                },
                "r8_mapping": {
                    "path": "app\\build\\outputs\\mapping\\release\\mapping.txt",
                    "bytes": mapping.stat().st_size,
                    "sha256": self._digest(mapping),
                    "application_classes": 10,
                    "renamed_classes": 9,
                },
                "dry_run_overrides": {
                    "allow_placeholder_id": False,
                    "allow_unsigned": False,
                    "skip_build": False,
                },
            }
            (release / "build_summary.json").write_text(json.dumps(payload), encoding="utf-8")
            (release / "BUILD_SUMMARY.md").write_text(
                f"{candidate}\ncom.anurag9000.forestrun\n{bundle_hash}\nTrue\n",
                encoding="utf-8",
            )

            verify_release_summary(root, release, candidate)

    def test_absolute_drive_and_parent_paths_are_rejected(self) -> None:
        for unsafe in ("C:\\outside.aab", "../outside.aab", "/outside.aab"):
            with self.subTest(unsafe=unsafe):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    root = Path(temporary_directory)
                    release = root / "release/google-play"
                    release.mkdir(parents=True)
                    graphics, metadata, screenshots = self._public_release_facts(root)
                    candidate = "a" * 40
                    payload = {
                        "candidate": {"sha": candidate, "branch": "main"},
                        "identity": {
                            "application_id": "com.anurag9000.forestrun",
                            "version_name": "1.2.3",
                            "version_code": 1,
                        },
                        "graphics": graphics,
                        "metadata": metadata,
                        "screenshots": {
                            "images": screenshots,
                            "candidate_sha": candidate,
                            "package_name": "com.anurag9000.forestrun.debug",
                        },
                        "audio": [f"audio_{index}" for index in range(15)],
                        "bundle": {
                            "path": unsafe,
                            "bytes": 1,
                            "sha256": "b" * 64,
                            "application_id": "com.anurag9000.forestrun",
                            "version_code": 1,
                            "version_name": "1.2.3",
                            "signature_verified": True,
                            "signer_sha256": "c" * 64,
                        },
                        "r8_mapping": {
                            "path": unsafe,
                            "bytes": 1,
                            "sha256": "d" * 64,
                            "application_classes": 1,
                            "renamed_classes": 1,
                        },
                        "dry_run_overrides": {
                            "allow_placeholder_id": False,
                            "allow_unsigned": False,
                            "skip_build": False,
                        },
                    }
                    (release / "build_summary.json").write_text(
                        json.dumps(payload), encoding="utf-8"
                    )
                    (release / "BUILD_SUMMARY.md").write_text(
                        f"{candidate}\ncom.anurag9000.forestrun",
                        encoding="utf-8",
                    )
                    with self.assertRaisesRegex(ReleaseSummaryError, "path is unsafe"):
                        verify_release_summary(root, release, candidate)


if __name__ == "__main__":
    unittest.main()
