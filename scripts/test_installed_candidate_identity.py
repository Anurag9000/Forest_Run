from __future__ import annotations

import copy
import hashlib
import json
import os
import tempfile
import unittest
from pathlib import Path

import collect_installed_candidate_identity as collector
import validate_installed_candidate_identity as validator

SHA = "1" * 40
CERT = "2" * 64


def sha_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def materialize(root: Path) -> dict:
    files = {
        "raw/adb-devices.txt": b"List of devices attached\nserial-1\tdevice\n",
        "raw/pm-path.txt": b"package:/data/app/example/base.apk\npackage:/data/app/example/split_config.en.apk\n",
        "raw/pm-installer.txt": b"package:com.anurag9000.forestrun  installer=com.android.vending\n",
        "raw/dumpsys-package.txt": b"Package [com.anurag9000.forestrun]\n",
        "raw/device.properties": b"manufacturer=Example\nmodel=Phone\n",
        "raw/apkanalyzer-summary.txt": b"com.anurag9000.forestrun 7 1.0\n",
        "raw/apksigner-base.apk.txt": b"Signer #1 certificate SHA-256 digest: " + CERT.encode() + b"\n",
        "raw/apksigner-split_config.en.apk.txt": b"Signer #1 certificate SHA-256 digest: " + CERT.encode() + b"\n",
        "raw/adb-pull-base.apk.txt": b"pulled base\n",
        "raw/adb-pull-split_config.en.apk.txt": b"pulled split\n",
        "apks/base.apk": b"base-apk-bytes\n",
        "apks/split_config.en.apk": b"split-apk-bytes\n",
    }
    for relative, payload in files.items():
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(payload)

    return {
        "schema_version": validator.SCHEMA_VERSION,
        "captured_at_utc": "2026-08-09T18:00:00Z",
        "candidate": {
            "repository": validator.CANONICAL_REPOSITORY,
            "branch": validator.CANONICAL_BRANCH,
            "commit_sha": SHA,
            "application_id": validator.CANONICAL_APPLICATION_ID,
            "version_code": 7,
            "app_signing_certificate_sha256": CERT,
            "expected_installer_package": validator.PLAY_STORE_INSTALLER,
        },
        "device": {
            "serial_sha256": sha_bytes(b"serial-1"),
            "manufacturer": "Example",
            "model": "Phone",
            "device": "example_device",
            "sdk": 35,
            "build_fingerprint": "example/device/build:fingerprint",
        },
        "installed_package": {
            "application_id": validator.CANONICAL_APPLICATION_ID,
            "version_code": 7,
            "version_name": "1.0",
            "installer_package": validator.PLAY_STORE_INSTALLER,
            "app_signing_certificate_sha256": CERT,
            "base_apk_sha256": sha_bytes(files["apks/base.apk"]),
            "apk_set": [
                {
                    "name": "base.apk",
                    "sha256": sha_bytes(files["apks/base.apk"]),
                    "size_bytes": len(files["apks/base.apk"]),
                    "signing_certificate_sha256": CERT,
                },
                {
                    "name": "split_config.en.apk",
                    "sha256": sha_bytes(files["apks/split_config.en.apk"]),
                    "size_bytes": len(files["apks/split_config.en.apk"]),
                    "signing_certificate_sha256": CERT,
                },
            ],
        },
        "claims": {
            "play_store_installer_observed": True,
            "specific_play_track_verified": False,
        },
        "evidence_files": [
            {"path": relative, "sha256": sha_bytes(payload)}
            for relative, payload in sorted(files.items())
        ],
    }


class InstalledCandidateIdentityTest(unittest.TestCase):
    def validate(self, root: Path, bundle: dict):
        return validator.validate_bundle(
            bundle,
            source_bytes=(json.dumps(bundle, sort_keys=True) + "\n").encode(),
            evidence_base=root,
        )

    def test_valid_record_binds_package_version_installer_and_all_apk_signers(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bundle = materialize(root)
            summary = self.validate(root, bundle)
            self.assertEqual(SHA, summary.candidate_sha)
            self.assertEqual(7, summary.version_code)
            self.assertEqual(CERT, summary.app_signing_certificate_sha256)
            self.assertEqual(validator.PLAY_STORE_INSTALLER, summary.installer_package)
            self.assertEqual(2, summary.apk_count)

    def test_installer_does_not_authorize_specific_track_claim(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bundle = materialize(root)
            bundle["claims"]["specific_play_track_verified"] = True
            with self.assertRaises(validator.InstalledIdentityError) as raised:
                self.validate(root, bundle)
            self.assertIn("cannot prove a Play track", str(raised.exception))

    def test_wrong_version_installer_or_signer_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            base = materialize(root)
            mutations = (
                ("version", lambda item: item["installed_package"].__setitem__("version_code", 8), "version_code"),
                ("installer", lambda item: item["installed_package"].__setitem__("installer_package", "adb"), "installer"),
                ("signer", lambda item: item["installed_package"].__setitem__("app_signing_certificate_sha256", "3" * 64), "certificate"),
            )
            for label, mutate, fragment in mutations:
                with self.subTest(label=label):
                    bundle = copy.deepcopy(base)
                    mutate(bundle)
                    with self.assertRaises(validator.InstalledIdentityError) as raised:
                        self.validate(root, bundle)
                    self.assertIn(fragment, str(raised.exception))

    def test_split_with_different_signer_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bundle = materialize(root)
            bundle["installed_package"]["apk_set"][1]["signing_certificate_sha256"] = "4" * 64
            with self.assertRaises(validator.InstalledIdentityError) as raised:
                self.validate(root, bundle)
            self.assertIn("signer does not match", str(raised.exception))

    def test_raw_evidence_tampering_and_symlink_aliasing_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bundle = materialize(root)
            (root / "raw/pm-path.txt").write_text("tampered\n", encoding="utf-8")
            with self.assertRaises(validator.InstalledIdentityError) as raised:
                self.validate(root, bundle)
            self.assertIn("evidence digest mismatch", str(raised.exception))

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bundle = materialize(root)
            real = root / "raw"
            alias = root / "raw-alias"
            try:
                alias.symlink_to(real, target_is_directory=True)
            except OSError as exc:
                self.skipTest(f"symbolic links unavailable: {exc}")
            reference = next(item for item in bundle["evidence_files"] if item["path"] == "raw/pm-path.txt")
            reference["path"] = "raw-alias/pm-path.txt"
            with self.assertRaises(validator.InstalledIdentityError) as raised:
                self.validate(root, bundle)
            self.assertIn("must not traverse a symbolic link", str(raised.exception))

    def test_apk_set_digest_and_size_must_match_the_pulled_apk_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bundle = materialize(root)
            bundle["installed_package"]["apk_set"][1]["sha256"] = "5" * 64
            with self.assertRaises(validator.InstalledIdentityError) as raised:
                self.validate(root, bundle)
            self.assertIn("does not match pulled APK evidence", str(raised.exception))

    def test_manifest_loader_rejects_duplicate_keys_and_symlink_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            duplicate = root / "duplicate.json"
            duplicate.write_text('{"schema_version":1,"schema_version":1}\n', encoding="utf-8")
            with self.assertRaises(validator.InstalledIdentityError) as raised:
                validator.load_and_validate(duplicate)
            self.assertIn("duplicate JSON object key", str(raised.exception))

            bundle = materialize(root)
            real = root / "real.json"
            real.write_text(json.dumps(bundle) + "\n", encoding="utf-8")
            alias = root / "alias.json"
            try:
                alias.symlink_to(real)
            except OSError as exc:
                self.skipTest(f"symbolic links unavailable: {exc}")
            with self.assertRaises(validator.InstalledIdentityError) as raised:
                validator.load_and_validate(alias)
            self.assertIn("regular non-symlink", str(raised.exception))

    def test_collector_parsers_accept_expected_android_tool_outputs(self) -> None:
        devices = collector.parse_adb_devices(
            "List of devices attached\nSERIAL1\tdevice product:x\nSERIAL2\toffline\n"
        )
        self.assertEqual(["SERIAL1"], devices)
        self.assertEqual(
            ["/data/app/pkg/base.apk", "/data/app/pkg/split_config.en.apk"],
            collector.parse_pm_paths(
                "package:/data/app/pkg/base.apk\npackage:/data/app/pkg/split_config.en.apk\n"
            ),
        )
        self.assertEqual(
            (validator.CANONICAL_APPLICATION_ID, 7, "1.0"),
            collector.parse_apkanalyzer_summary(
                f"{validator.CANONICAL_APPLICATION_ID} 7 1.0\n"
            ),
        )
        self.assertEqual(
            validator.PLAY_STORE_INSTALLER,
            collector.parse_installer_package(
                f"package:{validator.CANONICAL_APPLICATION_ID}  installer={validator.PLAY_STORE_INSTALLER}\n",
                validator.CANONICAL_APPLICATION_ID,
            ),
        )
        self.assertEqual(
            CERT,
            collector.parse_apksigner_certificate(
                f"Signer #1 certificate SHA-256 digest: {CERT}\n"
            ),
        )

    def test_collector_parsers_reject_ambiguous_or_malformed_outputs(self) -> None:
        with self.assertRaises(collector.CollectionError):
            collector.parse_pm_paths("package:/data/app/pkg/split.apk\n")
        with self.assertRaises(collector.CollectionError):
            collector.parse_apkanalyzer_summary("package-only\n")
        with self.assertRaises(collector.CollectionError):
            collector.parse_installer_package("package:other installer=com.android.vending\n", validator.CANONICAL_APPLICATION_ID)
        with self.assertRaises(collector.CollectionError):
            collector.parse_apksigner_certificate("Signer #1 certificate DN: CN=Example\n")


if __name__ == "__main__":
    unittest.main()
