import stat
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path

from release_artifact_verifier import (
    ArtifactVerificationError,
    CommandResult,
    inspect_bundle_identity,
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
