from __future__ import annotations

import copy
import hashlib
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

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

    def test_source_bytes_must_match_identity_object(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bundle = materialize(root)
            with self.assertRaisesRegex(validator.InstalledIdentityError, "source bytes do not encode"):
                validator.validate_bundle(bundle, source_bytes=b"{}", evidence_base=root)

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

class InstalledCandidateIdentityReaderHardeningTest(unittest.TestCase):
    def test_parent_swap_to_symlink_during_evidence_open_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bundle = materialize(root)
            evidence_parent = root / "raw"
            moved_parent = root / "raw.saved"
            outside = root / "outside"
            outside.mkdir()
            original_open = os.open
            swapped = False

            def swap_parent_then_open(path, flags, *args, **kwargs):
                nonlocal swapped
                if path == "raw" and kwargs.get("dir_fd") is not None and not swapped:
                    evidence_parent.rename(moved_parent)
                    evidence_parent.symlink_to(outside, target_is_directory=True)
                    swapped = True
                return original_open(path, flags, *args, **kwargs)

            with patch.object(validator.os, "open", side_effect=swap_parent_then_open):
                with self.assertRaises(validator.InstalledIdentityError):
                    InstalledCandidateIdentityTest().validate(root, bundle)
            self.assertTrue(swapped)
            self.assertTrue(evidence_parent.is_symlink())

    def test_hash_fields_reject_uppercase_hex_instead_of_normalizing_it(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bundle = materialize(root)
            bundle["candidate"]["commit_sha"] = ("ab" * 20).upper()
            with self.assertRaisesRegex(validator.InstalledIdentityError, "lowercase 40-hex"):
                InstalledCandidateIdentityTest().validate(root, bundle)

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bundle = materialize(root)
            bundle["candidate"]["app_signing_certificate_sha256"] = ("ab" * 32).upper()
            with self.assertRaisesRegex(validator.InstalledIdentityError, "lowercase 64-hex"):
                InstalledCandidateIdentityTest().validate(root, bundle)

    def test_hash_fields_reject_surrounding_whitespace(self):
        mutators = {
            "candidate.commit_sha": lambda item: item["candidate"].__setitem__(
                "commit_sha", item["candidate"]["commit_sha"] + " "
            ),
            "candidate.certificate": lambda item: item["candidate"].__setitem__(
                "app_signing_certificate_sha256",
                item["candidate"]["app_signing_certificate_sha256"] + " ",
            ),
            "device.serial": lambda item: item["device"].__setitem__(
                "serial_sha256", item["device"]["serial_sha256"] + " "
            ),
            "installed certificate": lambda item: item["installed_package"].__setitem__(
                "app_signing_certificate_sha256",
                item["installed_package"]["app_signing_certificate_sha256"] + " ",
            ),
            "base APK digest": lambda item: item["installed_package"].__setitem__(
                "base_apk_sha256", item["installed_package"]["base_apk_sha256"] + " "
            ),
            "split APK digest": lambda item: item["installed_package"]["apk_set"][1].__setitem__(
                "sha256", item["installed_package"]["apk_set"][1]["sha256"] + " "
            ),
            "APK certificate": lambda item: item["installed_package"]["apk_set"][0].__setitem__(
                "signing_certificate_sha256",
                item["installed_package"]["apk_set"][0]["signing_certificate_sha256"] + " ",
            ),
            "evidence digest": lambda item: item["evidence_files"][0].__setitem__(
                "sha256", item["evidence_files"][0]["sha256"] + " "
            ),
        }
        for label, mutate in mutators.items():
            with self.subTest(field=label), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                bundle = materialize(root)
                mutate(bundle)
                with self.assertRaisesRegex(validator.InstalledIdentityError, "lowercase .*hex"):
                    InstalledCandidateIdentityTest().validate(root, bundle)

    def test_manifest_reader_rejects_fifo_symlink_and_oversize_before_read(self):
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            oversized = root / "oversized.json"
            with oversized.open("wb") as handle:
                handle.truncate(validator.MAX_MANIFEST_BYTES + 1)
            with patch.object(Path, "read_bytes", side_effect=AssertionError("unbounded read attempted")):
                with self.assertRaisesRegex(validator.InstalledIdentityError, "between 1 and"):
                    validator.load_and_validate(oversized)

            target = root / "target.json"
            target.write_text("{}")
            alias = root / "alias.json"
            alias.symlink_to(target)
            with patch.object(Path, "read_bytes", side_effect=AssertionError("symlink target read")):
                with self.assertRaisesRegex(validator.InstalledIdentityError, "regular non-symlink"):
                    validator.load_and_validate(alias)

            fifo = root / "identity.fifo"
            os.mkfifo(fifo)
            with self.assertRaisesRegex(validator.InstalledIdentityError, "regular non-symlink"):
                validator.load_and_validate(fifo)

    def test_manifest_reader_detects_replacement_during_read(self):
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bundle = materialize(root)
            manifest = root / "identity.json"
            manifest.write_text(json.dumps(bundle), encoding="utf-8")
            real_read = os.read
            replaced = False
            def replace_after_read(descriptor, amount):
                nonlocal replaced
                result = real_read(descriptor, amount)
                if result and not replaced:
                    replaced = True
                    replacement = root / "replacement.json"
                    replacement.write_text("{}", encoding="utf-8")
                    os.replace(replacement, manifest)
                return result
            with patch.object(validator.os, "read", side_effect=replace_after_read):
                with self.assertRaisesRegex(validator.InstalledIdentityError, "changed while being read"):
                    validator.load_and_validate(manifest)

    def test_evidence_paths_require_canonical_normalized_posix_form(self):
        for path in ("raw//file.txt", "raw/./file.txt", "./raw/file.txt", "raw/file.txt/"):
            with self.subTest(path=path), self.assertRaisesRegex(validator.InstalledIdentityError, "normalized POSIX"):
                validator._safe_relative_path(path, "evidence.path")
        self.assertEqual("raw/file.txt", validator._safe_relative_path("raw/file.txt", "evidence.path"))

    def test_evidence_hasher_uses_nonblocking_nofollow_descriptor(self):
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "payload.bin"
            path.write_bytes(b"payload")
            flags_seen = []
            real_open = os.open
            def record_open(value, flags, *args, **kwargs):
                flags_seen.append(flags)
                return real_open(value, flags, *args, **kwargs)
            with patch.object(validator.os, "open", side_effect=record_open):
                digest, _ = validator._hash_regular_file(path, "payload", 100)
            self.assertEqual(sha_bytes(b"payload"), digest)
            self.assertGreaterEqual(len(flags_seen), 2)
            file_flags = flags_seen[-1]
            if hasattr(os, "O_NOFOLLOW"):
                self.assertTrue(file_flags & os.O_NOFOLLOW)
            if hasattr(os, "O_NONBLOCK"):
                self.assertTrue(file_flags & os.O_NONBLOCK)

    def test_evidence_hasher_rejects_fifo_swap_without_blocking(self):
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "payload.bin"
            path.write_bytes(b"payload")
            real_open = os.open
            changed = False
            def swap_to_fifo(value, flags, *args, **kwargs):
                nonlocal changed
                if value == path.name and kwargs.get("dir_fd") is not None and not changed:
                    changed = True
                    path.unlink()
                    os.mkfifo(path)
                return real_open(value, flags, *args, **kwargs)
            with patch.object(validator.os, "open", side_effect=swap_to_fifo):
                with self.assertRaisesRegex(validator.InstalledIdentityError, "changed while being opened"):
                    validator._hash_regular_file(path, "payload", 100)
