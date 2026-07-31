import tempfile
import unittest
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
    def create_bundle(self, root: Path, include_dex=True):
        bundle = root / "app-release.aab"
        with zipfile.ZipFile(bundle, "w") as archive:
            archive.writestr("BundleConfig.pb", b"config")
            archive.writestr("base/manifest/AndroidManifest.xml", b"manifest")
            if include_dex:
                archive.writestr("base/dex/classes.dex", b"dex")
        return bundle

    def test_bundle_structure_requires_manifest_config_and_dex(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            bundle = self.create_bundle(root)

            facts = verify_bundle_structure(bundle)

            self.assertEqual(3, facts["entries"])
            self.assertEqual(["base/dex/classes.dex"], facts["dex_files"])

    def test_bundle_structure_rejects_missing_dex(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            bundle = self.create_bundle(Path(temporary_directory), include_dex=False)

            with self.assertRaisesRegex(ArtifactVerificationError, "DEX"):
                verify_bundle_structure(bundle)

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
                        0, "jar verified.\n", ""
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
