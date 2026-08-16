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

        self.graphics = [
            self.root / "release/google-play/graphics/feature-graphic.png",
            self.root / "release/google-play/graphics/promo-square.png",
        ]
        for index, path in enumerate(self.graphics):
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(f"graphic-{index}".encode("utf-8"))

        self.metadata = [
            self.root / "release/google-play/metadata/en-US/title.txt",
            self.root / "release/google-play/metadata/en-US/short-description.txt",
            self.root / "release/google-play/metadata/en-US/full-description.txt",
        ]
        self.metadata_text = [
            "Forest Run",
            "Run gently through a living forest that remembers your choices.",
            (
                "Forest Run is a handcrafted endless runner where mercy changes the path. "
                "Collect Seeds, meet the forest, and return to a persistent Garden that "
                "remembers your choices across journeys."
            ),
        ]
        for path, text in zip(self.metadata, self.metadata_text, strict=True):
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")

        self.screenshots = []
        for index in range(4):
            path = self.root / f"release/google-play/screenshots/final/shot-{index}.png"
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(f"screenshot-{index}".encode("utf-8"))
            self.screenshots.append(path)

    def tearDown(self) -> None:
        self.temp.cleanup()

    @staticmethod
    def digest(path: Path) -> str:
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def file_fact(self, path: Path) -> dict:
        return {
            "path": str(path.relative_to(self.root)).replace("\\", "/"),
            "bytes": path.stat().st_size,
            "sha256": self.digest(path),
        }

    def metadata_fact(self, path: Path) -> dict:
        text = path.read_text(encoding="utf-8")
        return {
            "path": str(path.relative_to(self.root)).replace("\\", "/"),
            "characters": len(text),
            "sha256": self.digest(path),
        }

    def payload(self, *, skip_build=False, allow_unsigned=False, signature=True):
        bundle = None
        mapping = None
        if not skip_build:
            bundle = {
                **self.file_fact(self.bundle),
                "application_id": "com.anurag9000.forestrun",
                "version_code": 7,
                "version_name": "1.2.3",
                "signature_verified": signature,
                "signer_sha256": "c" * 64 if signature else None,
                "entries": 3,
                "dex_files": ["base/dex/classes.dex"],
            }
            mapping = {
                **self.file_fact(self.mapping),
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
            "graphics": [self.file_fact(path) for path in self.graphics],
            "metadata": [self.metadata_fact(path) for path in self.metadata],
            "screenshots": {
                "images": [self.file_fact(path) for path in self.screenshots],
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

    def test_graphics_metadata_and_screenshot_bytes_are_rehashed(self) -> None:
        for path, expected in (
            (self.graphics[0], "graphics.*byte count|graphics.*SHA-256"),
            (self.metadata[1], "metadata.*SHA-256|metadata.*character count"),
            (self.screenshots[2], "screenshots.*byte count|screenshots.*SHA-256"),
        ):
            payload = self.payload()
            self.write(payload)
            path.write_bytes(path.read_bytes() + b"-changed")
            with self.assertRaisesRegex(ReleaseSummaryError, expected):
                verify_release_summary(self.root, self.release, self.candidate)
            if path in self.graphics:
                index = self.graphics.index(path)
                path.write_bytes(f"graphic-{index}".encode("utf-8"))
            elif path in self.metadata:
                index = self.metadata.index(path)
                path.write_text(self.metadata_text[index], encoding="utf-8")
            else:
                index = self.screenshots.index(path)
                path.write_bytes(f"screenshot-{index}".encode("utf-8"))

    def test_summary_file_facts_reject_symbolic_links(self) -> None:
        payload = self.payload()
        self.write(payload)
        original = self.graphics[0]
        target = self.root / "safe-graphic.png"
        target.write_bytes(original.read_bytes())
        original.unlink()
        try:
            original.symlink_to(target)
        except OSError as exc:
            self.skipTest(f"symbolic links unavailable: {exc}")
        with self.assertRaisesRegex(ReleaseSummaryError, "symbolic link"):
            verify_release_summary(self.root, self.release, self.candidate)

    def test_canonical_graphics_metadata_and_screenshot_paths_are_required(self) -> None:
        payload = self.payload()
        payload["graphics"][0] = dict(payload["graphics"][1])
        self.write(payload)
        with self.assertRaisesRegex(ReleaseSummaryError, "duplicates graphics path|canonical set"):
            verify_release_summary(self.root, self.release, self.candidate)

        other_metadata = self.root / "release/google-play/metadata/en-US/other.txt"
        other_metadata.write_text("Other metadata", encoding="utf-8")
        payload = self.payload()
        payload["metadata"][0] = self.metadata_fact(other_metadata)
        self.write(payload)
        with self.assertRaisesRegex(ReleaseSummaryError, "canonical set"):
            verify_release_summary(self.root, self.release, self.candidate)

        raw = self.root / "release/google-play/screenshots/raw/shot.png"
        raw.parent.mkdir(parents=True, exist_ok=True)
        raw.write_bytes(b"raw-shot")
        payload = self.payload()
        payload["screenshots"]["images"][0] = self.file_fact(raw)
        self.write(payload)
        with self.assertRaisesRegex(ReleaseSummaryError, "curated final screenshot"):
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
