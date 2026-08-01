import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from verify_release_summary import ReleaseSummaryError, verify_release_summary


class ReleaseSummaryPathTest(unittest.TestCase):
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
            candidate = "a" * 40
            bundle_hash = hashlib.sha256(bundle.read_bytes()).hexdigest()
            payload = {
                "candidate": {"sha": candidate, "branch": "main"},
                "identity": {
                    "application_id": "com.anurag9000.forestrun",
                    "version_name": "1.2.3",
                    "version_code": 7,
                },
                "graphics": [{}, {}],
                "metadata": [{}, {}, {}],
                "screenshots": {
                    "images": [{}, {}, {}, {}],
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
                    "sha256": hashlib.sha256(mapping.read_bytes()).hexdigest(),
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
        # The path parser is exercised through a deliberately incomplete summary.
        for unsafe in ("C:\\outside.aab", "../outside.aab", "/outside.aab"):
            with self.subTest(unsafe=unsafe):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    root = Path(temporary_directory)
                    release = root / "release/google-play"
                    release.mkdir(parents=True)
                    candidate = "a" * 40
                    payload = {
                        "candidate": {"sha": candidate, "branch": "main"},
                        "identity": {"application_id": "com.anurag9000.forestrun", "version_code": 1},
                        "graphics": [{}, {}],
                        "metadata": [{}, {}, {}],
                        "screenshots": {"images": [{}, {}, {}, {}], "candidate_sha": candidate, "package_name": "com.anurag9000.forestrun.debug"},
                        "audio": [f"audio_{index}" for index in range(15)],
                        "bundle": {"path": unsafe, "bytes": 1, "sha256": "b" * 64, "application_id": "com.anurag9000.forestrun", "version_code": 1, "version_name": None, "signature_verified": True, "signer_sha256": "c" * 64},
                        "r8_mapping": {"path": unsafe, "bytes": 1, "sha256": "d" * 64, "application_classes": 1, "renamed_classes": 1},
                        "dry_run_overrides": {"allow_placeholder_id": False, "allow_unsigned": False, "skip_build": False},
                    }
                    (release / "build_summary.json").write_text(json.dumps(payload), encoding="utf-8")
                    (release / "BUILD_SUMMARY.md").write_text(f"{candidate}\ncom.anurag9000.forestrun", encoding="utf-8")
                    with self.assertRaisesRegex(ReleaseSummaryError, "path is unsafe"):
                        verify_release_summary(root, release, candidate)


if __name__ == "__main__":
    unittest.main()
