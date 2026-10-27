from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
import unittest
import zipfile
from unittest.mock import patch
from pathlib import Path

import verify_android_page_size_package as verifier


CANDIDATE = "a" * 40


class AndroidPageSizePackageTest(unittest.TestCase):
    def _write_zip(self, path: Path, entries: dict[str, bytes]) -> None:
        with zipfile.ZipFile(path, "w") as archive:
            for name, content in entries.items():
                archive.writestr(name, content)

    def _run_cli(self, *arguments: str, candidate: str | None = CANDIDATE):
        environment = os.environ.copy()
        if candidate is None:
            environment.pop("GITHUB_SHA", None)
        else:
            environment["GITHUB_SHA"] = candidate
        return subprocess.run(
            [sys.executable, str(Path(verifier.__file__).resolve()), *arguments],
            check=False,
            capture_output=True,
            text=True,
            env=environment,
        )

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

    def test_elf_content_is_detected_even_without_so_filename(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "misnamed-native.apk"
            self._write_zip(
                artifact,
                {"assets/plugins/runtime.bin": b"\x7fELF" + b"\x00" * 128},
            )

            result = verifier.inspect_artifact(artifact, CANDIDATE)

            self.assertEqual("native-verification-required", result["assessment"])
            self.assertEqual(
                ["assets/plugins/runtime.bin"],
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

    def test_legacy_ci_cli_is_candidate_bound_and_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            artifact = root / "app.apk"
            self._write_zip(artifact, {"classes.dex": b"dex"})

            result = self._run_cli(
                "--artifact",
                str(artifact),
                "--build-tools-dir",
                str(root / "unused-build-tools"),
            )
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            payload = json.loads(result.stdout)
            self.assertEqual("valid", payload["status"])
            self.assertEqual(CANDIDATE, payload["candidateSha"])
            self.assertEqual("no-native-code", payload["assessment"])

            native_artifact = root / "native.apk"
            self._write_zip(
                native_artifact,
                {"lib/arm64-v8a/libexample.so": b"not-an-elf"},
            )
            native_result = self._run_cli(
                "--artifact",
                str(native_artifact),
                "--build-tools-dir",
                str(root / "unused-build-tools"),
            )
            self.assertEqual(1, native_result.returncode)
            self.assertIn("native libraries require ELF", native_result.stdout)

    def test_legacy_ci_cli_rejects_missing_candidate_binding(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "app.apk"
            self._write_zip(artifact, {"classes.dex": b"dex"})
            result = self._run_cli(
                "--artifact",
                str(artifact),
                "--build-tools-dir",
                str(Path(directory) / "unused-build-tools"),
                candidate=None,
            )
            self.assertEqual(1, result.returncode)
            self.assertIn("candidate SHA must be provided", result.stdout)

    def test_rejects_ambiguous_artifact_arguments(self) -> None:
        namespace = type(
            "Arguments",
            (),
            {
                "artifact": Path("positional.apk"),
                "legacy_artifact": Path("legacy.apk"),
                "candidate_sha": CANDIDATE,
                "legacy_build_tools_dir": None,
                "require_no_native_code": False,
            },
        )()
        with self.assertRaisesRegex(verifier.PageSizeInspectionError, "exactly once"):
            verifier._resolve_cli_inputs(namespace)

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

    def test_rejects_corrupt_member_payload_crc(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "corrupt.apk"
            with zipfile.ZipFile(artifact, "w", compression=zipfile.ZIP_STORED) as archive:
                archive.writestr("classes.dex", b"payload")
            raw = bytearray(artifact.read_bytes())
            offset = raw.index(b"payload")
            raw[offset] ^= 1
            artifact.write_bytes(raw)

            with self.assertRaisesRegex(
                verifier.PageSizeInspectionError,
                "entry is corrupt or unsupported",
            ):
                verifier.inspect_artifact(artifact, CANDIDATE)

    @unittest.skipUnless(hasattr(zipfile, "ZIP_LZMA"), "ZIP LZMA support required")
    def test_normalizes_lzma_decompressor_errors(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "corrupt-lzma.apk"
            with zipfile.ZipFile(artifact, "w", compression=zipfile.ZIP_LZMA) as archive:
                archive.writestr("classes.dex", b"A" * 1000)
            raw = bytearray(artifact.read_bytes())
            local_header = raw.index(b"PK\x03\x04")
            name_length = int.from_bytes(raw[local_header + 26 : local_header + 28], "little")
            extra_length = int.from_bytes(raw[local_header + 28 : local_header + 30], "little")
            payload_offset = local_header + 30 + name_length + extra_length
            raw[payload_offset + 2] ^= 1
            artifact.write_bytes(raw)

            with self.assertRaisesRegex(
                verifier.PageSizeInspectionError,
                "entry is corrupt or unsupported",
            ):
                verifier.inspect_artifact(artifact, CANDIDATE)

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

class AndroidPageSizePackageBoundaryTest(AndroidPageSizePackageTest):
    def test_artifact_open_is_nonblocking_and_rejects_fifo_and_invalid_sizes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fifo = root / "artifact.fifo"
            os.mkfifo(fifo)
            with self.assertRaisesRegex(verifier.PageSizeInspectionError, "regular file"):
                verifier._open_regular_file(fifo)

            empty = root / "empty.apk"
            empty.touch()
            with self.assertRaisesRegex(verifier.PageSizeInspectionError, "size"):
                verifier._open_regular_file(empty)
            oversized = root / "large.apk"
            oversized.write_bytes(b"0123456789")
            with patch.object(verifier, "MAX_ARTIFACT_BYTES", 5):
                with self.assertRaisesRegex(verifier.PageSizeInspectionError, "size"):
                    verifier._open_regular_file(oversized)

    def test_bad_zip_entry_count_and_uncompressed_limits_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            artifact = root / "entries.apk"
            self._write_zip(artifact, {"one": b"1", "two": b"2"})
            with patch.object(verifier, "MAX_ENTRY_COUNT", 1):
                with self.assertRaisesRegex(verifier.PageSizeInspectionError, "too many entries"):
                    verifier.inspect_artifact(artifact, CANDIDATE)
            with patch.object(verifier, "MAX_ENTRY_BYTES", 0):
                with self.assertRaisesRegex(verifier.PageSizeInspectionError, "entry exceeds size bound"):
                    verifier.inspect_artifact(artifact, CANDIDATE)
            corrupt = root / "corrupt.apk"
            corrupt.write_bytes(b"not a zip")
            with self.assertRaisesRegex(verifier.PageSizeInspectionError, "valid ZIP"):
                verifier.inspect_artifact(corrupt, CANDIDATE)

    def test_safe_archive_name_and_descriptor_flags(self) -> None:
        for name in ("", "../escape", "a\\b", "a\x00b", "/absolute"):
            with self.subTest(name=name), self.assertRaises(verifier.PageSizeInspectionError):
                verifier._safe_archive_name(name)
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "safe.apk"
            self._write_zip(artifact, {"classes.dex": b"dex"})
            observed_flags = []
            real_open = os.open
            def record_open(path, flags, *args, **kwargs):
                observed_flags.append(flags)
                return real_open(path, flags, *args, **kwargs)
            with patch.object(verifier.os, "open", side_effect=record_open):
                stream, _ = verifier._open_regular_file(artifact)
                stream.close()
            if hasattr(os, "O_CLOEXEC"):
                self.assertTrue(observed_flags[0] & os.O_CLOEXEC)
            if hasattr(os, "O_NOFOLLOW"):
                self.assertTrue(observed_flags[0] & os.O_NOFOLLOW)
            if hasattr(os, "O_NONBLOCK"):
                self.assertTrue(observed_flags[0] & os.O_NONBLOCK)

    def test_cli_positional_path_and_explicit_candidate_output(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            artifact = root / "app.apk"
            output = root / "result.json"
            self._write_zip(artifact, {"classes.dex": b"dex"})
            result = self._run_cli(str(artifact), "--candidate-sha", CANDIDATE, "--output", str(output))
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertEqual("valid", json.loads(result.stdout)["status"])
            self.assertEqual("no-native-code", json.loads(output.read_text())["assessment"])

    def test_direct_api_normalizes_invalid_candidate_types_and_missing_artifacts(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for candidate in (None, 123, "A" * 40):
                with self.subTest(candidate=candidate), self.assertRaisesRegex(
                    verifier.PageSizeInspectionError, "candidate SHA"
                ):
                    verifier.inspect_artifact(root / "missing.apk", candidate)
            with self.assertRaisesRegex(verifier.PageSizeInspectionError, "unable to open artifact"):
                verifier.inspect_artifact(root / "missing.apk", CANDIDATE)

    def test_same_size_artifact_mutation_during_snapshot_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "app.apk"
            self._write_zip(artifact, {"classes.dex": b"dex"})
            original_open = verifier._open_regular_file
            changed = False

            class MutatingStream:
                def __init__(self, stream):
                    self.stream = stream

                def __enter__(self):
                    return self

                def __exit__(self, *args):
                    self.stream.close()

                def fileno(self):
                    return self.stream.fileno()

                def read(self, size=-1):
                    nonlocal changed
                    chunk = self.stream.read(size)
                    if chunk and not changed:
                        changed = True
                        content = artifact.read_bytes()
                        artifact.write_bytes(b"X" + content[1:])
                    return chunk

            def open_and_wrap(path):
                stream, size = original_open(path)
                return MutatingStream(stream), size

            with patch.object(verifier, "_open_regular_file", side_effect=open_and_wrap):
                with self.assertRaisesRegex(
                    verifier.PageSizeInspectionError,
                    "changed while being inspected",
                ):
                    verifier.inspect_artifact(artifact, CANDIDATE)
            self.assertTrue(changed)
