import os
import stat
import tempfile
import unittest
import warnings
import zipfile
from unittest.mock import patch
from pathlib import Path

from release_artifact_verifier import (
    ArtifactVerificationError,
    CommandResult,
    _extract_sha256_fingerprint,
    _last_non_blank_line,
    _run_checked,
    inspect_bundle_identity,
    resolve_tool,
    verify_bundle_signature,
    verify_bundle_structure,
)


class FakeRunner:
    def __init__(self, responses):
        self.responses = responses
        self.calls = []

    def __call__(self, command, environment=None):
        key = tuple(command)
        self.calls.append((key, dict(environment or {})))
        response = self.responses.get(key)
        if response is None:
            raise AssertionError(f"Unexpected command: {key}")
        return response


class ReleaseArtifactVerifierTest(unittest.TestCase):
    def test_resolve_tool_prefers_latest_then_highest_numeric_sdk_version(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            sdk = Path(temporary_directory)
            for version in ("9.0", "10.0", "latest"):
                tool = sdk / "cmdline-tools" / version / "bin" / "apkanalyzer"
                tool.parent.mkdir(parents=True, exist_ok=True)
                tool.write_text("tool")
                tool.chmod(0o755)

            selected = resolve_tool("apkanalyzer", {"ANDROID_HOME": str(sdk)}, which=lambda _: None)

            self.assertEqual(str(sdk / "cmdline-tools/latest/bin/apkanalyzer"), selected)

    def test_resolve_tool_uses_numeric_version_order_and_skips_non_executable(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            sdk = Path(temporary_directory)
            for version, mode in (("9.0", 0o755), ("10.0", 0o644)):
                tool = sdk / "cmdline-tools" / version / "bin" / "apkanalyzer"
                tool.parent.mkdir(parents=True, exist_ok=True)
                tool.write_text("tool")
                tool.chmod(mode)

            selected = resolve_tool("apkanalyzer", {"ANDROID_HOME": str(sdk)}, which=lambda _: None)

            self.assertEqual(str(sdk / "cmdline-tools/9.0/bin/apkanalyzer"), selected)

    def test_resolve_tool_respects_explicit_empty_environment(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            sdk = Path(temporary_directory)
            tool = sdk / "bin" / "apkanalyzer"
            tool.parent.mkdir()
            tool.write_text("tool")
            tool.chmod(0o755)

            with patch.dict(os.environ, {"ANDROID_HOME": str(sdk)}):
                with self.assertRaisesRegex(ArtifactVerificationError, "not found"):
                    resolve_tool("apkanalyzer", {}, which=lambda _: None)

    def test_resolve_tool_uses_the_supplied_path(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            tool_dir = Path(temporary_directory)
            tool = tool_dir / "apkanalyzer"
            tool.write_text("tool")
            tool.chmod(0o755)
            with patch("release_artifact_verifier.shutil.which", wraps=__import__("shutil").which) as which:
                selected = resolve_tool("apkanalyzer", {"PATH": str(tool_dir)})
            self.assertEqual(str(tool), selected)
            self.assertEqual(str(tool_dir), which.call_args.kwargs["path"])

    def test_command_output_helpers_reject_missing_or_failed_values(self):
        with self.assertRaisesRegex(ArtifactVerificationError, "no output"):
            _run_checked(["tool"], lambda *_: CommandResult(1, "", ""), label="probe")
        self.assertEqual("value", _run_checked(
            ["tool"], lambda *_: CommandResult(0, "", " value \n"), label="probe"
        ))
        with self.assertRaisesRegex(ArtifactVerificationError, "produced no value"):
            _last_non_blank_line(" \n", "probe")
        self.assertEqual("last", _last_non_blank_line("first\n last \n", "probe"))

    def test_fingerprint_parser_accepts_supported_formats_and_rejects_invalid(self):
        expected = "".join(f"{value:02X}" for value in range(32))
        self.assertEqual(expected, _extract_sha256_fingerprint(
            "SHA-256: " + ":".join(f"{value:02x}" for value in range(32)), "probe"
        ))
        with self.assertRaisesRegex(ArtifactVerificationError, "Could not find"):
            _extract_sha256_fingerprint("SHA256: AA:BB", "probe")

    @staticmethod
    def write_required_entries(archive: zipfile.ZipFile, include_dex: bool = True) -> None:
        archive.writestr("BundleConfig.pb", b"config")
        archive.writestr("base/manifest/AndroidManifest.xml", b"manifest")
        if include_dex:
            archive.writestr("base/dex/classes.dex", b"dex")

    def create_bundle(self, root: Path, include_dex=True):
        bundle = root / "app-release.aab"
        with zipfile.ZipFile(bundle, "w") as archive:
            self.write_required_entries(archive, include_dex)
        return bundle

    def test_bundle_structure_requires_manifest_config_and_dex(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            bundle = self.create_bundle(root)

            facts = verify_bundle_structure(bundle)

            self.assertEqual(3, facts["entries"])
            self.assertEqual(["base/dex/classes.dex"], facts["dex_files"])
            self.assertEqual(17, facts["total_uncompressed_bytes"])

    def test_bundle_structure_rejects_missing_dex(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            bundle = self.create_bundle(Path(temporary_directory), include_dex=False)

            with self.assertRaisesRegex(ArtifactVerificationError, "DEX"):
                verify_bundle_structure(bundle)

    def test_bundle_structure_rejects_duplicate_names(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            bundle = Path(temporary_directory, "duplicate.aab")
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                with zipfile.ZipFile(bundle, "w") as archive:
                    archive.writestr("BundleConfig.pb", b"config")
                    archive.writestr("BundleConfig.pb", b"second")
                    archive.writestr(
                        "base/manifest/AndroidManifest.xml", b"manifest"
                    )
                    archive.writestr("base/dex/classes.dex", b"dex")

            with self.assertRaisesRegex(ArtifactVerificationError, "duplicate"):
                verify_bundle_structure(bundle)

    def test_bundle_structure_rejects_unsafe_entry_names(self):
        unsafe_names = (
            "../outside.txt",
            "C:/outside.txt",
            "base/assets/file:stream",
            "base/assets/bad\x01name",
        )
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            for index, unsafe_name in enumerate(unsafe_names):
                bundle = root / f"unsafe-{index}.aab"
                with zipfile.ZipFile(bundle, "w") as archive:
                    self.write_required_entries(archive)
                    archive.writestr(unsafe_name, b"unsafe")

                with self.assertRaisesRegex(
                    ArtifactVerificationError,
                    "unsafe ZIP|control character",
                ):
                    verify_bundle_structure(bundle)

    def test_bundle_structure_rejects_symbolic_link_entries(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            bundle = Path(temporary_directory, "symlink.aab")
            with zipfile.ZipFile(bundle, "w") as archive:
                self.write_required_entries(archive)
                link = zipfile.ZipInfo("base/assets/link")
                link.create_system = 3
                link.external_attr = (stat.S_IFLNK | 0o777) << 16
                archive.writestr(link, "../../outside")

            with self.assertRaisesRegex(ArtifactVerificationError, "symbolic-link"):
                verify_bundle_structure(bundle)

    def test_bundle_structure_rejects_empty_required_and_dex_entries(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            empty_manifest = root / "empty-manifest.aab"
            with zipfile.ZipFile(empty_manifest, "w") as archive:
                archive.writestr("BundleConfig.pb", b"config")
                archive.writestr("base/manifest/AndroidManifest.xml", b"")
                archive.writestr("base/dex/classes.dex", b"dex")
            with self.assertRaisesRegex(ArtifactVerificationError, "empty required"):
                verify_bundle_structure(empty_manifest)

            empty_dex = root / "empty-dex.aab"
            with zipfile.ZipFile(empty_dex, "w") as archive:
                archive.writestr("BundleConfig.pb", b"config")
                archive.writestr(
                    "base/manifest/AndroidManifest.xml", b"manifest"
                )
                archive.writestr("base/dex/classes.dex", b"")
            with self.assertRaisesRegex(ArtifactVerificationError, "non-empty.*DEX"):
                verify_bundle_structure(empty_dex)

    def test_apkanalyzer_identity_must_match_gradle_configuration(self):
        bundle = Path("app-release.aab")
        analyzer = "/sdk/apkanalyzer"
        runner = FakeRunner(
            {
                (analyzer, "manifest", "application-id", str(bundle)): CommandResult(
                    0, "com.anurag9000.forestrun\n", ""
                ),
                (analyzer, "manifest", "version-code", str(bundle)): CommandResult(
                    0, "7\n", ""
                ),
                (analyzer, "manifest", "version-name", str(bundle)): CommandResult(
                    0, "1.2.3\n", ""
                ),
            }
        )

        identity = inspect_bundle_identity(
            bundle,
            "com.anurag9000.forestrun",
            7,
            "1.2.3",
            runner=runner,
            apkanalyzer=analyzer,
        )

        self.assertEqual("com.anurag9000.forestrun", identity.application_id)
        self.assertEqual(7, identity.version_code)
        self.assertEqual("1.2.3", identity.version_name)

    def test_mismatched_artifact_identity_is_rejected(self):
        bundle = Path("app-release.aab")
        analyzer = "/sdk/apkanalyzer"
        runner = FakeRunner(
            {
                (analyzer, "manifest", "application-id", str(bundle)): CommandResult(
                    0, "com.wrong.application\n", ""
                ),
                (analyzer, "manifest", "version-code", str(bundle)): CommandResult(
                    0, "7\n", ""
                ),
                (analyzer, "manifest", "version-name", str(bundle)): CommandResult(
                    0, "1.2.3\n", ""
                ),
            }
        )

        with self.assertRaisesRegex(ArtifactVerificationError, "does not match"):
            inspect_bundle_identity(
                bundle,
                "com.anurag9000.forestrun",
                7,
                "1.2.3",
                runner=runner,
                apkanalyzer=analyzer,
            )

    def test_signed_bundle_must_match_configured_upload_certificate(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            bundle = self.create_bundle(root)
            keystore = root / "upload.jks"
            keystore.write_bytes(b"keystore")
            fingerprint = ":".join(f"{value:02X}" for value in range(32))
            jarsigner = "/jdk/jarsigner"
            keytool = "/jdk/keytool"
            runner = FakeRunner(
                {
                    (jarsigner, "-verify", "-verbose", "-certs", str(bundle)): CommandResult(
                        0,
                        "jar verified.\nWarning: signer certificate is self-signed.\n",
                        "",
                    ),
                    (keytool, "-printcert", "-jarfile", str(bundle)): CommandResult(
                        0, f"Certificate fingerprints:\n SHA256: {fingerprint}\n", ""
                    ),
                    (
                        keytool,
                        "-list",
                        "-v",
                        "-keystore",
                        str(keystore),
                        "-alias",
                        "upload",
                        "-storepass:env",
                        "FOREST_RUN_RELEASE_VERIFY_STORE_PASSWORD",
                    ): CommandResult(
                        0, f"Certificate fingerprints:\n SHA-256: {fingerprint}\n", ""
                    ),
                }
            )

            signature = verify_bundle_signature(
                bundle,
                keystore=keystore,
                alias="upload",
                store_password="secret-value",
                allow_unsigned=False,
                runner=runner,
                jarsigner=jarsigner,
                keytool=keytool,
            )

            self.assertTrue(signature.verified)
            self.assertEqual(signature.signer_sha256, signature.expected_sha256)
            for _, environment in runner.calls:
                self.assertEqual(
                    "secret-value",
                    environment["FOREST_RUN_RELEASE_VERIFY_STORE_PASSWORD"],
                )

    def test_unsigned_entry_warning_is_rejected_even_when_jarsigner_says_verified(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            bundle = self.create_bundle(root)
            keystore = root / "upload.jks"
            keystore.write_bytes(b"keystore")
            jarsigner = "/jdk/jarsigner"
            runner = FakeRunner(
                {
                    (jarsigner, "-verify", "-verbose", "-certs", str(bundle)): CommandResult(
                        0,
                        "jar verified.\nWarning: This jar contains unsigned entries.\n",
                        "",
                    )
                }
            )

            with self.assertRaisesRegex(ArtifactVerificationError, "completely"):
                verify_bundle_signature(
                    bundle,
                    keystore=keystore,
                    alias="upload",
                    store_password="secret-value",
                    allow_unsigned=False,
                    runner=runner,
                    jarsigner=jarsigner,
                    keytool="/jdk/keytool",
                )

            self.assertEqual(1, len(runner.calls))

    def test_unsigned_override_is_explicitly_reported(self):
        signature = verify_bundle_signature(
            Path("unsigned.aab"),
            keystore=None,
            alias=None,
            store_password=None,
            allow_unsigned=True,
        )

        self.assertFalse(signature.verified)
        self.assertIsNone(signature.signer_sha256)

    def test_wrong_upload_certificate_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            bundle = self.create_bundle(root)
            keystore = root / "upload.jks"
            keystore.write_bytes(b"keystore")
            bundle_fingerprint = ":".join("AA" for _ in range(32))
            configured_fingerprint = ":".join("BB" for _ in range(32))
            jarsigner = "/jdk/jarsigner"
            keytool = "/jdk/keytool"
            runner = FakeRunner(
                {
                    (jarsigner, "-verify", "-verbose", "-certs", str(bundle)): CommandResult(
                        0, "jar verified.\n", ""
                    ),
                    (keytool, "-printcert", "-jarfile", str(bundle)): CommandResult(
                        0, f"SHA256: {bundle_fingerprint}\n", ""
                    ),
                    (
                        keytool,
                        "-list",
                        "-v",
                        "-keystore",
                        str(keystore),
                        "-alias",
                        "upload",
                        "-storepass:env",
                        "FOREST_RUN_RELEASE_VERIFY_STORE_PASSWORD",
                    ): CommandResult(
                        0, f"SHA256: {configured_fingerprint}\n", ""
                    ),
                }
            )

            with self.assertRaisesRegex(ArtifactVerificationError, "does not match"):
                verify_bundle_signature(
                    bundle,
                    keystore=keystore,
                    alias="upload",
                    store_password="secret-value",
                    allow_unsigned=False,
                    runner=runner,
                    jarsigner=jarsigner,
                    keytool=keytool,
                )


if __name__ == "__main__":
    unittest.main()

class ReleaseArtifactVerifierNamespaceTest(ReleaseArtifactVerifierTest):
    def test_bundle_structure_rejects_file_directory_namespace_collisions(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            cases = (
                ("same-name.aab", [("base/assets/", b""), ("base/assets", b"file")]),
                ("file-parent.aab", [("base/assets", b"file"), ("base/assets/item", b"child")]),
                ("late-file-parent.aab", [("base/assets/item", b"child"), ("base/assets", b"file")]),
            )
            for filename, extra in cases:
                bundle = root / filename
                with zipfile.ZipFile(bundle, "w") as archive:
                    self.write_required_entries(archive)
                    for name, payload in extra:
                        archive.writestr(name, payload)
                with self.subTest(filename=filename), self.assertRaisesRegex(
                    ArtifactVerificationError, "conflicting|nested beneath a file|shadows an existing directory"
                ):
                    verify_bundle_structure(bundle)

    def test_bundle_structure_enforces_entry_count_and_expanded_size_limits(self):
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bundle = self.create_bundle(root)
            with patch("release_artifact_verifier.MAX_BUNDLE_ENTRIES", 2):
                with self.assertRaisesRegex(ArtifactVerificationError, "too many entries"):
                    verify_bundle_structure(bundle)
            with patch("release_artifact_verifier.MAX_ENTRY_UNCOMPRESSED_BYTES", 2):
                with self.assertRaisesRegex(ArtifactVerificationError, "excessive expanded size"):
                    verify_bundle_structure(bundle)

    def test_bundle_structure_rejects_corrupt_zip_payload(self):
        with tempfile.TemporaryDirectory() as temporary:
            bundle = self.create_bundle(Path(temporary))
            content = bytearray(bundle.read_bytes())
            marker = content.find(b"manifest")
            self.assertGreaterEqual(marker, 0)
            content[marker] ^= 1
            bundle.write_bytes(content)
            with self.assertRaisesRegex(ArtifactVerificationError, "corrupt entry|valid ZIP"):
                verify_bundle_structure(bundle)

    def test_bundle_reader_rejects_symlinks_and_fifos_without_blocking(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bundle = self.create_bundle(root)
            alias = root / "alias.aab"
            alias.symlink_to(bundle)
            with self.assertRaisesRegex(ArtifactVerificationError, "regular non-symlink"):
                verify_bundle_structure(alias)
            fifo = root / "bundle.fifo"
            os.mkfifo(fifo)
            with self.assertRaisesRegex(ArtifactVerificationError, "regular non-symlink"):
                verify_bundle_structure(fifo)

    def test_bundle_reader_uses_nonblocking_nofollow_descriptor(self):
        with tempfile.TemporaryDirectory() as temporary:
            bundle = self.create_bundle(Path(temporary))
            flags_seen = []
            real_open = os.open
            def capture_open(path, flags, *args, **kwargs):
                flags_seen.append(flags)
                return real_open(path, flags, *args, **kwargs)
            with patch("release_artifact_verifier.os.open", side_effect=capture_open):
                verify_bundle_structure(bundle)
            if hasattr(os, "O_NOFOLLOW"):
                self.assertTrue(flags_seen[0] & os.O_NOFOLLOW)
            if hasattr(os, "O_NONBLOCK"):
                self.assertTrue(flags_seen[0] & os.O_NONBLOCK)
