import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from verify_release_summary import ReleaseSummaryError, verify_release_summary


class ReleaseSummaryVerifierTest(unittest.TestCase):
    candidate = "a" * 40

    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.release = self.root / "release/google-play"
        self.release.mkdir(parents=True)
        self.bundle = self.root / "app/build/outputs/bundle/release/app-release.aab"
        self.mapping = self.root / "app/build/outputs/mapping/release/mapping.txt"
        self.bundle.parent.mkdir(parents=True)
        self.mapping.parent.mkdir(parents=True)
        self.bundle.write_bytes(b"bundle")
        self.mapping.write_text("mapping", encoding="utf-8")

    def tearDown(self) -> None:
        self.temp.cleanup()

    @staticmethod
    def digest(path: Path) -> str:
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def payload(self, *, skip_build=False, allow_unsigned=False, signature=True):
        bundle = None
        mapping = None
        if not skip_build:
            bundle = {
                "path": str(self.bundle.relative_to(self.root)),
                "bytes": self.bundle.stat().st_size,
                "sha256": self.digest(self.bundle),
                "application_id": "com.anurag9000.forestrun",
                "version_code": 7,
                "version_name": "1.2.3",
                "signature_verified": signature,
                "signer_sha256": "c" * 64 if signature else None,
                "entries": 3,
                "dex_files": ["base/dex/classes.dex"],
            }
            mapping = {
                "path": str(self.mapping.relative_to(self.root)),
                "bytes": self.mapping.stat().st_size,
                "sha256": self.digest(self.mapping),
                "application_classes": 100,
                "renamed_classes": 90,
            }
        return {
            "candidate": {"sha": self.candidate, "branch": "main"},
            "identity": {
                "application_id": "com.anurag9000.forestrun",
                "version_name": "1.2.3",
                "version_code": 7,
            },
            "graphics": [{}, {}],
            "metadata": [{}, {}, {}],
            "screenshots": {
                "images": [{}, {}, {}, {}],
                "candidate_sha": self.candidate,
                "package_name": "com.anurag9000.forestrun.debug",
            },
            "audio": [f"audio_{index}" for index in range(15)],
            "bundle": bundle,
            "r8_mapping": mapping,
            "dry_run_overrides": {
                "allow_placeholder_id": False,
                "allow_unsigned": allow_unsigned,
                "skip_build": skip_build,
            },
        }

    def write(self, payload) -> None:
        (self.release / "build_summary.json").write_text(
            json.dumps(payload), encoding="utf-8"
        )
        lines = [
            "# Play Release Build Summary",
            self.candidate,
            "com.anurag9000.forestrun",
        ]
        if payload["dry_run_overrides"]["skip_build"]:
            lines.append("Release bundle: build skipped")
        else:
            lines.append(payload["bundle"]["sha256"])
            lines.append(str(payload["bundle"]["signature_verified"]))
        (self.release / "BUILD_SUMMARY.md").write_text(
            "\n".join(lines), encoding="utf-8"
        )

    def test_signed_built_summary_is_accepted(self) -> None:
        payload = self.payload()
        self.write(payload)
        self.assertEqual(
            self.candidate,
            verify_release_summary(self.root, self.release, self.candidate)["candidate"]["sha"],
        )

    def test_explicit_unsigned_and_skip_build_dry_runs_are_distinct(self) -> None:
        unsigned = self.payload(allow_unsigned=True, signature=False)
        self.write(unsigned)
        verify_release_summary(self.root, self.release, self.candidate)

        skipped = self.payload(skip_build=True, allow_unsigned=True)
        self.write(skipped)
        verify_release_summary(self.root, self.release, self.candidate)

    def test_unapproved_unsigned_bundle_is_rejected(self) -> None:
        payload = self.payload(signature=False, allow_unsigned=False)
        self.write(payload)
        with self.assertRaisesRegex(ReleaseSummaryError, "Unsigned bundle"):
            verify_release_summary(self.root, self.release, self.candidate)

    def test_candidate_screenshot_and_artifact_staleness_are_rejected(self) -> None:
        payload = self.payload()
        payload["candidate"]["sha"] = "b" * 40
        self.write(payload)
        with self.assertRaisesRegex(ReleaseSummaryError, "candidate"):
            verify_release_summary(self.root, self.release, self.candidate)

        payload = self.payload()
        payload["screenshots"]["candidate_sha"] = "b" * 40
        self.write(payload)
        with self.assertRaisesRegex(ReleaseSummaryError, "Screenshot"):
            verify_release_summary(self.root, self.release, self.candidate)

        payload = self.payload()
        self.write(payload)
        self.bundle.write_bytes(b"changed")
        with self.assertRaisesRegex(ReleaseSummaryError, "byte count|SHA-256"):
            verify_release_summary(self.root, self.release, self.candidate)

    def test_human_summary_must_disclose_exact_bundle_and_status(self) -> None:
        payload = self.payload()
        self.write(payload)
        (self.release / "BUILD_SUMMARY.md").write_text(
            f"{self.candidate}\ncom.anurag9000.forestrun\n",
            encoding="utf-8",
        )
        with self.assertRaisesRegex(ReleaseSummaryError, "bundle hash"):
            verify_release_summary(self.root, self.release, self.candidate)

    def test_audio_graphics_metadata_and_r8_counts_are_consistent(self) -> None:
        payload = self.payload()
        payload["audio"] = payload["audio"][:14]
        self.write(payload)
        with self.assertRaisesRegex(ReleaseSummaryError, "audio"):
            verify_release_summary(self.root, self.release, self.candidate)

        payload = self.payload()
        payload["r8_mapping"]["renamed_classes"] = 101
        self.write(payload)
        with self.assertRaisesRegex(ReleaseSummaryError, "R8 class counts"):
            verify_release_summary(self.root, self.release, self.candidate)


if __name__ == "__main__":
    unittest.main()
