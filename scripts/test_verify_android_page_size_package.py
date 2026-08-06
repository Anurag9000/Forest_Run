from __future__ import annotations

import json
import os
import tempfile
import unittest
import zipfile
from pathlib import Path

import verify_android_page_size_package as verifier


CANDIDATE = "a" * 40


class AndroidPageSizePackageTest(unittest.TestCase):
    def _write_zip(self, path: Path, entries: dict[str, bytes]) -> None:
        with zipfile.ZipFile(path, "w") as archive:
            for name, content in entries.items():
                archive.writestr(name, content)

    def test_kotlin_only_package_is_candidate_bound_and_compatible_by_inspection(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "app.aab"
            self._write_zip(
                artifact,
                {
                    "base/manifest/AndroidManifest.xml": b"manifest",
                    "base/dex/classes.dex": b"dex",
                },
            )

            result = verifier.inspect_artifact(
                artifact,
                CANDIDATE,
                require_no_native_code=True,
            )

            self.assertEqual(CANDIDATE, result["candidateSha"])
            self.assertEqual("no-native-code", result["assessment"])
            self.assertTrue(result["compatibleByPackageInspection"])
            self.assertEqual(0, result["nativeLibraryCount"])
            self.assertEqual([], result["nativeLibraries"])
            self.assertEqual(64, len(result["artifactSha256"]))

    def test_native_library_requires_independent_alignment_verification(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "app.apk"
            self._write_zip(
                artifact,
                {
                    "AndroidManifest.xml": b"manifest",
                    "lib/arm64-v8a/libexample.so": b"not-an-elf",
                },
            )

            result = verifier.inspect_artifact(artifact, CANDIDATE)
            self.assertEqual("native-verification-required", result["assessment"])
            self.assertFalse(result["compatibleByPackageInspection"])
            self.assertEqual(
                ["lib/arm64-v8a/libexample.so"],
                result["nativeLibraries"],
            )
            with self.assertRaisesRegex(
                verifier.PageSizeInspectionError,
                "native libraries require ELF",
            ):
                verifier.inspect_artifact(
                    artifact,
                    CANDIDATE,
                    require_no_native_code=True,
                )

    def test_rejects_duplicate_and_unsafe_archive_paths(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "duplicate.apk"
            with zipfile.ZipFile(artifact, "w") as archive:
                archive.writestr("classes.dex", b"one")
                archive.writestr("classes.dex", b"two")
            with self.assertRaisesRegex(
                verifier.PageSizeInspectionError,
                "duplicate entry",
            ):
                verifier.inspect_artifact(artifact, CANDIDATE)

            unsafe = Path(directory) / "unsafe.apk"
            self._write_zip(unsafe, {"../escape": b"bad"})
            with self.assertRaisesRegex(
                verifier.PageSizeInspectionError,
                "unsafe archive path",
            ):
                verifier.inspect_artifact(unsafe, CANDIDATE)

    def test_rejects_noncanonical_candidate_sha(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "app.apk"
            self._write_zip(artifact, {"classes.dex": b"dex"})
            for invalid in ("A" * 40, "a" * 39, "not-a-sha"):
                with self.subTest(invalid=invalid):
                    with self.assertRaisesRegex(
                        verifier.PageSizeInspectionError,
                        "40 lowercase hexadecimal",
                    ):
                        verifier.inspect_artifact(artifact, invalid)

    @unittest.skipUnless(hasattr(os, "symlink"), "symlink support required")
    def test_rejects_artifact_and_output_symlinks(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            artifact = root / "real.apk"
            self._write_zip(artifact, {"classes.dex": b"dex"})
            alias = root / "alias.apk"
            alias.symlink_to(artifact)
            with self.assertRaisesRegex(
                verifier.PageSizeInspectionError,
                "symbolic link",
            ):
                verifier.inspect_artifact(alias, CANDIDATE)

            payload = verifier.inspect_artifact(artifact, CANDIDATE)
            target = root / "target.json"
            target.write_text("{}\n", encoding="utf-8")
            output = root / "result.json"
            output.symlink_to(target)
            with self.assertRaisesRegex(
                verifier.PageSizeInspectionError,
                "output must not be a symbolic link",
            ):
                verifier.publish(output, payload)
            self.assertEqual("{}\n", target.read_text(encoding="utf-8"))

    def test_publish_is_canonical_json(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            artifact = root / "app.apk"
            self._write_zip(artifact, {"classes.dex": b"dex"})
            payload = verifier.inspect_artifact(artifact, CANDIDATE)
            output = root / "result.json"

            verifier.publish(output, payload)

            expected = (
                json.dumps(
                    payload,
                    sort_keys=True,
                    separators=(",", ":"),
                    ensure_ascii=False,
                )
                + "\n"
            ).encode("utf-8")
            self.assertEqual(expected, output.read_bytes())


if __name__ == "__main__":
    unittest.main()
