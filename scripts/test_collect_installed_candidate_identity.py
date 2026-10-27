from __future__ import annotations

import contextlib
import io
import json
import os
import runpy
import subprocess
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
import sys

import collect_installed_candidate_identity as collector
import stable_file_reader as stable_files


class CandidateIdentityCollectionPublicationTest(unittest.TestCase):
    CANDIDATE_SHA = "a" * 40
    CERTIFICATE_SHA = "b" * 64

    def call_collect(self, output: Path, *, serial: str | None = None):
        return collector.collect(
            root=Path("/repo"),
            output_dir=output,
            expected_candidate_sha=self.CANDIDATE_SHA,
            expected_version_code=7,
            expected_app_signing_certificate_sha256=self.CERTIFICATE_SHA,
            serial=serial,
        )

    def collect_with_mocked_device(
        self,
        output: Path,
        *,
        installer: str = "com.android.vending",
        summary: str = "com.anurag9000.forestrun 7 1.2.3\n",
        signer_digest: str | None = None,
        sdk: str = "35",
        create_pulled_apk: bool = True,
        origin_checks: list[object] | None = None,
        serial: str | None = None,
    ):
        expected_digest = signer_digest or self.CERTIFICATE_SHA

        def run(command, **_kwargs):
            if command == ["adb-test", "devices"]:
                return subprocess.CompletedProcess(
                    command, 0, "List of devices attached\nSERIAL-1\tdevice\n", ""
                )
            if command[:2] == ["adb-test", "-s"] and command[3] == "pull":
                if create_pulled_apk:
                    Path(command[5]).write_bytes(("apk:" + command[4]).encode())
                return subprocess.CompletedProcess(command, 0, "1 file pulled\n", "")
            if command[0] == "apksigner-test":
                return subprocess.CompletedProcess(
                    command, 0,
                    f"Signer #1 certificate SHA-256 digest: {expected_digest}\n", ""
                )
            if command[0] == "apkanalyzer-test":
                return subprocess.CompletedProcess(command, 0, summary, "")
            self.fail(f"unexpected command: {command!r}")

        def device_shell(adb, serial, *arguments):
            self.assertEqual("adb-test", adb)
            self.assertEqual("SERIAL-1", serial)
            if arguments[:2] == ("pm", "path"):
                return (
                    "package:/data/app/example/base.apk\n"
                    "package:/data/app/example/split_config.arm64_v8a.apk\n"
                )
            if arguments[:3] == ("pm", "list", "packages"):
                return (
                    "package:com.anurag9000.forestrun.debug installer=com.android.packageinstaller\n"
                    f"package:com.anurag9000.forestrun installer={installer}\n"
                )
            if arguments[:2] == ("dumpsys", "package"):
                return "Package [com.anurag9000.forestrun]\n"
            self.fail(f"unexpected device shell arguments: {arguments!r}")

        with (
            patch.object(
                collector.verify_main_candidate,
                "verify_main_candidate",
                return_value=SimpleNamespace(sha=self.CANDIDATE_SHA),
            ),
            patch.object(collector, "_verify_origin_main", side_effect=origin_checks),
            patch.object(collector, "_resolve_adb", return_value="adb-test"),
            patch.object(collector, "_resolve_apkanalyzer", return_value="apkanalyzer-test"),
            patch.object(collector, "_resolve_apksigner", return_value="apksigner-test"),
            patch.object(collector, "_run", side_effect=run),
            patch.object(collector, "_device_shell", side_effect=device_shell),
            patch.object(
                collector,
                "_getprop",
                side_effect=["Forest", "Run Phone", "forest_device", sdk, "fingerprint"],
            ),
        ):
            return self.call_collect(output, serial=serial)

    def test_failed_collection_removes_partial_stage_and_allows_retry(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            parent = Path(temporary)
            output = parent / "identity"
            with (
                patch.object(
                    collector.verify_main_candidate,
                    "verify_main_candidate",
                    return_value=SimpleNamespace(sha=self.CANDIDATE_SHA),
                ),
                patch.object(collector, "_verify_origin_main"),
                patch.object(collector, "_resolve_adb", return_value="adb-test"),
                patch.object(collector, "_resolve_apkanalyzer", return_value="apkanalyzer-test"),
                patch.object(collector, "_resolve_apksigner", return_value="apksigner-test"),
                patch.object(
                    collector,
                    "_run",
                    return_value=subprocess.CompletedProcess(
                        ["adb-test", "devices"], 0, "List of devices attached\n", ""
                    ),
                ) as run,
            ):
                for _ in range(2):
                    with self.assertRaisesRegex(collector.CollectionError, "exactly one authorized device"):
                        self.call_collect(output)
                    self.assertFalse(output.exists())
                    self.assertEqual([], list(parent.glob(".identity.*.staging")))
            self.assertEqual(2, run.call_count)

    def test_validated_stage_is_published_and_replaces_empty_destination(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            parent = Path(temporary)
            output = parent / "identity"
            output.mkdir()
            result = object()

            def write_validated_stage(**kwargs):
                stage = kwargs["output_dir"]
                (stage / "installed-candidate-identity.json").write_text(
                    '{"status":"valid"}\n', encoding="utf-8"
                )
                return result

            with patch.object(
                collector, "_collect_in_directory", side_effect=write_validated_stage
            ):
                actual = self.call_collect(output)

            self.assertIs(result, actual)
            self.assertEqual(
                '{"status":"valid"}\n',
                (output / "installed-candidate-identity.json").read_text(encoding="utf-8"),
            )
            self.assertEqual([], list(parent.glob(".identity.*.staging")))

    def test_nonempty_destination_is_preserved_without_collection(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "identity"
            output.mkdir()
            protected = output / "operator-note.txt"
            protected.write_text("keep", encoding="utf-8")
            with patch.object(collector, "_collect_in_directory") as collect_stage:
                with self.assertRaisesRegex(collector.CollectionError, "absent or empty"):
                    self.call_collect(output)
            collect_stage.assert_not_called()
            self.assertEqual("keep", protected.read_text(encoding="utf-8"))

    def test_output_type_and_concurrent_destination_change_fail_without_clobbering(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            parent = Path(temporary)
            file_destination = parent / "identity-file"
            file_destination.write_text("protected", encoding="utf-8")
            with patch.object(collector, "_collect_in_directory") as collect_stage:
                with self.assertRaisesRegex(collector.CollectionError, "not a directory"):
                    self.call_collect(file_destination)
            collect_stage.assert_not_called()
            self.assertEqual("protected", file_destination.read_text(encoding="utf-8"))

            protected_directory = parent / "protected-directory"
            protected_directory.mkdir()
            output_link = parent / "identity-link"
            try:
                output_link.symlink_to(protected_directory, target_is_directory=True)
            except (OSError, NotImplementedError) as exc:
                self.skipTest(f"symlinks unavailable: {exc}")
            with self.assertRaisesRegex(collector.CollectionError, "must not be a symbolic link"):
                self.call_collect(output_link)
            self.assertTrue(protected_directory.is_dir())
            self.assertTrue(output_link.is_symlink())

            output = parent / "identity"
            result = object()

            def concurrent_writer(**_kwargs):
                output.mkdir()
                (output / "concurrent.txt").write_text("keep", encoding="utf-8")
                return result

            with patch.object(collector, "_collect_in_directory", side_effect=concurrent_writer):
                with self.assertRaisesRegex(collector.CollectionError, "changed during collection"):
                    self.call_collect(output)
            self.assertEqual("keep", (output / "concurrent.txt").read_text(encoding="utf-8"))
            self.assertEqual([], list(parent.glob(".identity.*.staging")))

    def test_output_parent_swap_during_collection_cannot_redirect_publication(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            parent = root / "parent"
            parent.mkdir()
            output = parent / "identity"
            moved = root / "parent-moved"
            external = root / "external"
            external.mkdir()
            result = object()

            def swap_parent_and_write_scratch(**kwargs):
                scratch = kwargs["output_dir"]
                (scratch / "validated.json").write_text('{"status":"valid"}')
                parent.rename(moved)
                parent.symlink_to(external, target_is_directory=True)
                return result

            with patch.object(
                collector,
                "_collect_in_directory",
                side_effect=swap_parent_and_write_scratch,
            ):
                with self.assertRaisesRegex(collector.CollectionError, "output parent changed"):
                    self.call_collect(output)

            self.assertTrue(parent.is_symlink())
            self.assertFalse((moved / "identity").exists())
            self.assertEqual([], list(external.iterdir()))

    def test_failed_publish_restores_preexisting_empty_destination(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            parent = Path(temporary)
            output = parent / "identity"
            output.mkdir()

            def write_validated_stage(**kwargs):
                (kwargs["output_dir"] / "validated.json").write_text(
                    '{"status":"valid"}', encoding="utf-8"
                )
                return object()

            real_rename = stable_files._rename_noreplace
            calls = 0

            def fail_stage_publish(source, destination, *args, **kwargs):
                nonlocal calls
                calls += 1
                if calls == 2:
                    raise OSError("injected directory publication failure")
                return real_rename(source, destination, **kwargs)

            with (
                patch.object(collector, "_collect_in_directory", side_effect=write_validated_stage),
                patch.object(stable_files, "_rename_noreplace", fail_stage_publish),
            ):
                with self.assertRaisesRegex(collector.CollectionError, "directory publication failure"):
                    self.call_collect(output)

            self.assertTrue(output.is_dir())
            self.assertEqual([], list(output.iterdir()))
            self.assertEqual([], list(parent.glob(".identity.*.staging")))

    def test_mocked_device_collection_validates_and_publishes_complete_split_set(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            parent = Path(temporary)
            root = parent / "repo"
            root.mkdir()
            output = parent / "identity"
            expected_digest = self.CERTIFICATE_SHA

            def run(command, **kwargs):
                if command == ["adb-test", "devices"]:
                    return subprocess.CompletedProcess(
                        command, 0, "List of devices attached\nSERIAL-1\tdevice\n", ""
                    )
                if command[:2] == ["adb-test", "-s"] and command[3] == "pull":
                    Path(command[5]).write_bytes(("apk:" + command[4]).encode())
                    return subprocess.CompletedProcess(command, 0, "1 file pulled\n", "")
                if command[0] == "apksigner-test":
                    return subprocess.CompletedProcess(
                        command, 0,
                        f"Signer #1 certificate SHA-256 digest: {expected_digest}\n", ""
                    )
                if command[0] == "apkanalyzer-test":
                    return subprocess.CompletedProcess(
                        command, 0,
                        "com.anurag9000.forestrun 7 1.2.3\n", ""
                    )
                self.fail(f"unexpected command: {command!r}")

            def device_shell(adb, serial, *arguments):
                self.assertEqual("adb-test", adb)
                self.assertEqual("SERIAL-1", serial)
                if arguments[:2] == ("pm", "path"):
                    return (
                        "package:/data/app/example/base.apk\n"
                        "package:/data/app/example/split_config.arm64_v8a.apk\n"
                    )
                if arguments[:3] == ("pm", "list", "packages"):
                    return (
                        "package:com.anurag9000.forestrun.debug installer=com.android.packageinstaller\n"
                        "package:com.anurag9000.forestrun installer=com.android.vending\n"
                    )
                if arguments[:2] == ("dumpsys", "package"):
                    return "Package [com.anurag9000.forestrun]\n"
                self.fail(f"unexpected device shell arguments: {arguments!r}")

            with (
                patch.object(
                    collector.verify_main_candidate,
                    "verify_main_candidate",
                    return_value=SimpleNamespace(sha=self.CANDIDATE_SHA),
                ),
                patch.object(collector, "_verify_origin_main"),
                patch.object(collector, "_resolve_adb", return_value="adb-test"),
                patch.object(collector, "_resolve_apkanalyzer", return_value="apkanalyzer-test"),
                patch.object(collector, "_resolve_apksigner", return_value="apksigner-test"),
                patch.object(collector, "_run", side_effect=run),
                patch.object(collector, "_device_shell", side_effect=device_shell),
                patch.object(
                    collector,
                    "_getprop",
                    side_effect=["Forest", "Run Phone", "forest_device", "35", "fingerprint"],
                ),
            ):
                result = self.call_collect(output)

            self.assertEqual("valid", result.to_json()["status"])
            self.assertEqual(2, result.apk_count)
            self.assertEqual(
                {"base.apk", "split_config.arm64_v8a.apk"},
                {path.name for path in (output / "apks").glob("*.apk")},
            )
            self.assertTrue((output / "installed-candidate-identity.json").is_file())
            self.assertEqual([], list(parent.glob(".identity.*.staging")))

    def test_collector_rejects_mismatched_device_package_and_apk_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            parent = Path(temporary)
            cases = (
                ({"installer": "com.android.packageinstaller"}, "attributed to"),
                ({"serial": "OTHER-SERIAL"}, "requested device is not connected"),
                ({"create_pulled_apk": False}, "missing or empty"),
                ({"summary": "com.example.other 7 1.2.3\n"}, "application ID is"),
                ({"summary": "com.anurag9000.forestrun 8 1.2.3\n"}, "versionCode is 8"),
                ({"signer_digest": "c" * 64}, "signer set does not equal"),
                ({"sdk": "not-a-number"}, "SDK property is not an integer"),
            )
            for index, (overrides, message) in enumerate(cases):
                with self.subTest(message=message):
                    output = parent / f"identity-{index}"
                    with self.assertRaisesRegex(collector.CollectionError, message):
                        self.collect_with_mocked_device(output, **overrides)
                    self.assertFalse(output.exists())
                    self.assertEqual([], list(parent.glob(f".identity-{index}.*.staging")))

    def test_second_candidate_check_can_abort_publication_after_collection(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "identity"
            with self.assertRaisesRegex(collector.CollectionError, "candidate changed during collection"):
                self.collect_with_mocked_device(
                    output,
                    origin_checks=[
                        None,
                        collector.CollectionError("candidate changed during collection"),
                    ],
                )
            self.assertFalse(output.exists())
            self.assertEqual([], list(Path(temporary).glob(".identity.*.staging")))

    def test_missing_base_summary_and_independent_validation_failure_abort_publication(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            parent = Path(temporary)
            missing_summary = parent / "identity-no-summary"
            with patch.object(collector, "parse_apkanalyzer_summary", return_value=None):
                with self.assertRaisesRegex(collector.CollectionError, "base.apk summary was not captured"):
                    self.collect_with_mocked_device(missing_summary)
            self.assertFalse(missing_summary.exists())

            invalid_manifest = parent / "identity-invalid-manifest"
            with patch.object(
                collector.identity,
                "load_and_validate",
                side_effect=collector.identity.InstalledIdentityError("injected validation rejection"),
            ):
                with self.assertRaisesRegex(collector.CollectionError, "injected validation rejection"):
                    self.collect_with_mocked_device(invalid_manifest)
            self.assertFalse(invalid_manifest.exists())
            self.assertEqual([], list(parent.glob(".identity-*.*.staging")))


class CandidateIdentityCollectorParsingTest(unittest.TestCase):
    def test_command_runner_checks_errors_and_can_return_unchecked_status(self) -> None:
        failed = subprocess.CompletedProcess(["tool"], 7, "fallback", "diagnostic")
        with patch.object(collector.subprocess, "run", return_value=failed):
            with self.assertRaisesRegex(collector.CollectionError, "diagnostic"):
                collector._run(["tool"])
            self.assertEqual(failed, collector._run(["tool"], check=False))
        with patch.object(collector.subprocess, "run", side_effect=FileNotFoundError("missing")):
            with self.assertRaisesRegex(collector.CollectionError, "could not run 'tool'"):
                collector._run(["tool"])

    def test_origin_normalization_and_candidate_ref_binding(self) -> None:
        for value in (
            "git@github.com:Anurag9000/Forest_Run.git",
            "ssh://git@github.com/Anurag9000/Forest_Run.git",
            "https://github.com/Anurag9000/Forest_Run.git",
            "https://github.com/Anurag9000/Forest_Run/",
        ):
            self.assertEqual(collector.CANONICAL_ORIGIN, collector._normalize_origin(value))
        with patch.object(
            collector,
            "_run",
            side_effect=[
                subprocess.CompletedProcess([], 0, "https://github.com/Anurag9000/Forest_Run.git", ""),
                subprocess.CompletedProcess([], 0, "", ""),
                *[
                    subprocess.CompletedProcess([], 0, "a" * 40, "")
                    for _ in range(3)
                ],
            ],
        ) as run:
            collector._verify_origin_main(Path("/repo"), "a" * 40)
        self.assertEqual(5, run.call_count)
        with patch.object(
            collector,
            "_run",
            side_effect=[
                subprocess.CompletedProcess([], 0, "git@github.com:other/repo.git", "")
            ],
        ):
            with self.assertRaisesRegex(collector.CollectionError, "not the canonical"):
                collector._verify_origin_main(Path("/repo"), "a" * 40)
        for mismatch_at in range(3):
            outputs = [
                subprocess.CompletedProcess([], 0, "git@github.com:Anurag9000/Forest_Run.git", ""),
                subprocess.CompletedProcess([], 0, "", ""),
                *[
                    subprocess.CompletedProcess(
                        [], 0, "b" * 40 if index == mismatch_at else "a" * 40, ""
                    )
                    for index in range(3)
                ],
            ]
            with patch.object(collector, "_run", side_effect=outputs):
                with self.assertRaisesRegex(collector.CollectionError, "changed or diverged"):
                    collector._verify_origin_main(Path("/repo"), "a" * 40)

    def test_sdk_tool_resolution_uses_path_then_sdk_fallbacks(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            sdk = Path(temporary) / "sdk"
            adb = sdk / "platform-tools/adb"
            analyzer_versions = (
                sdk / "cmdline-tools/9.0/bin/apkanalyzer",
                sdk / "cmdline-tools/12.0/bin/apkanalyzer",
                sdk / "cmdline-tools/latest/bin/apkanalyzer",
            )
            older_signer = sdk / "build-tools/34.0.0/apksigner"
            newer_signer = sdk / "build-tools/35.0.0/apksigner"
            for path in (adb, *analyzer_versions, older_signer, newer_signer):
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("", encoding="utf-8")
            with patch.dict("os.environ", {"ANDROID_HOME": str(sdk), "ANDROID_SDK_ROOT": str(sdk)}, clear=True), patch.object(
                collector.shutil, "which", return_value=None
            ):
                self.assertEqual(str(adb), collector._resolve_adb())
                self.assertEqual(str(analyzer_versions[2]), collector._resolve_apkanalyzer())
                self.assertEqual(str(newer_signer), collector._resolve_apksigner())
                self.assertEqual([sdk], collector._sdk_roots())
                analyzer_versions[2].unlink()
                self.assertEqual(str(analyzer_versions[1]), collector._resolve_apkanalyzer())
            with patch.object(collector.shutil, "which", return_value="/path/adb"):
                self.assertEqual("/path/adb", collector._resolve_adb())
            with patch.object(collector.shutil, "which", side_effect=["/path/apkanalyzer", "/path/apksigner"]):
                self.assertEqual("/path/apkanalyzer", collector._resolve_apkanalyzer())
                self.assertEqual("/path/apksigner", collector._resolve_apksigner())
        with patch.dict("os.environ", {}, clear=True), patch.object(
            collector.shutil, "which", return_value=None
        ):
            for resolver, message in (
                (collector._resolve_adb, "adb was not found"),
                (collector._resolve_apkanalyzer, "apkanalyzer was not found"),
                (collector._resolve_apksigner, "apksigner was not found"),
            ):
                with self.subTest(resolver=resolver.__name__), self.assertRaisesRegex(
                    collector.CollectionError, message
                ):
                    resolver()
        with tempfile.TemporaryDirectory() as temporary:
            sdk_without_tools = Path(temporary) / "sdk-without-tools"
            sdk_with_empty_tools = Path(temporary) / "sdk-with-empty-tools"
            (sdk_with_empty_tools / "platform-tools").mkdir(parents=True)
            (sdk_with_empty_tools / "cmdline-tools").mkdir()
            build_tools = sdk_with_empty_tools / "build-tools"
            build_tools.mkdir()
            (build_tools / "not-a-directory").write_text("ignored", encoding="utf-8")
            (build_tools / "35.0.0").mkdir()
            with patch.dict(
                "os.environ",
                {
                    "ANDROID_HOME": str(sdk_without_tools),
                    "ANDROID_SDK_ROOT": str(sdk_with_empty_tools),
                },
                clear=True,
            ), patch.object(collector.shutil, "which", return_value=None):
                for resolver, message in (
                    (collector._resolve_adb, "adb was not found"),
                    (collector._resolve_apkanalyzer, "apkanalyzer was not found"),
                    (collector._resolve_apksigner, "apksigner was not found"),
                ):
                    with self.subTest(resolver=resolver.__name__), self.assertRaisesRegex(
                        collector.CollectionError, message
                    ):
                        resolver()
        with patch.object(collector.os, "name", "nt"):
            self.assertEqual(
                ("adb.exe", "adb.bat", "adb.cmd", "adb"),
                collector._executable_variants("adb"),
            )
        self.assertEqual("unrecognized://remote/repository", collector._normalize_origin("unrecognized://remote/repository"))

    def test_apksigner_resolution_skips_missing_build_tools_and_non_directories(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            first = Path(temporary) / "sdk-one"
            second = Path(temporary) / "sdk-two"
            build_tools = second / "build-tools"
            build_tools.mkdir(parents=True)
            (build_tools / "not-a-version-directory").write_text("ignore", encoding="utf-8")
            signer = build_tools / "35.0.0/apksigner"
            signer.parent.mkdir()
            signer.write_text("", encoding="utf-8")
            with patch.dict(
                "os.environ",
                {"ANDROID_HOME": str(first), "ANDROID_SDK_ROOT": str(second)},
                clear=True,
            ), patch.object(collector.shutil, "which", return_value=None):
                self.assertEqual(str(signer), collector._resolve_apksigner())

    def test_device_and_installed_apk_output_parsers_reject_ambiguous_data(self) -> None:
        self.assertEqual(
            ["serial-1"],
            collector.parse_adb_devices(
                "List of devices attached\nserial-1 device product:x\n"
                "serial-2 unauthorized usb:1\nserial-3 offline\n"
            ),
        )
        self.assertEqual(
            ["/data/app/split.apk", "/data/app/base.apk"],
            collector.parse_pm_paths(
                "package:/data/app/split.apk\n\npackage:/data/app/base.apk\n"
            ),
        )
        invalid_paths = (
            ("", "no installed APK paths"),
            ("noise\npackage:/data/base.apk", "unexpected pm path"),
            ("package:relative/base.apk", "invalid installed APK path"),
            ("package:/data/base.zip", "invalid installed APK path"),
            ("package:/data/base.apk\npackage:/data/base.apk", "duplicate installed APK path"),
            ("package:/data/split.apk", "exactly one base.apk"),
            ("package:/one/base.apk\npackage:/two/base.apk", "exactly one base.apk"),
            ("package:/one/base.apk\npackage:/two/split.apk\npackage:/three/split.apk", "basenames are not unique"),
        )
        for output, message in invalid_paths:
            with self.subTest(output=output), self.assertRaisesRegex(collector.CollectionError, message):
                collector.parse_pm_paths(output)

    def test_summary_installer_and_certificate_parsers_enforce_exact_identity(self) -> None:
        self.assertEqual(
            ("com.example.app", 12, "release candidate"),
            collector.parse_apkanalyzer_summary("\ncom.example.app 12 release candidate\n"),
        )
        self.assertEqual(
            "com.android.vending",
            collector.parse_installer_package(
                "package:com.example.app.debug installer=com.other\n"
                "package:com.example.app installer=com.android.vending\n",
                "com.example.app",
            ),
        )
        with self.assertRaisesRegex(collector.CollectionError, "installer package is blank"):
            collector.parse_installer_package(
                "package:com.example.app installer=\n", "com.example.app"
            )
        self.assertEqual(
            self_digest := "ab" * 32,
            collector.parse_apksigner_certificate(
                "Signer #1 certificate SHA-256 digest: " + ":".join(self_digest[i:i + 2] for i in range(0, 64, 2))
            ),
        )
        for output, message in (
            ("", "contain applicationId"),
            ("app nope 1.2", "non-integer versionCode"),
            ("app 0 name", "invalid package version"),
            ("app 1", "contain applicationId"),
        ):
            with self.subTest(output=output), self.assertRaisesRegex(collector.CollectionError, message):
                collector.parse_apkanalyzer_summary(output)
        with self.assertRaisesRegex(collector.CollectionError, "exactly one"):
            collector.parse_installer_package("package:com.example.app.debug installer=x\n", "com.example.app")
        with self.assertRaisesRegex(collector.CollectionError, "exactly one"):
            collector.parse_installer_package(
                "package:com.example.app installer=x\npackage:com.example.app installer=y\n",
                "com.example.app",
            )
        with self.assertRaisesRegex(collector.CollectionError, "did not report"):
            collector.parse_installer_package("package:com.example.app\n", "com.example.app")
        for output, message in (
            ("", "did not report Signer"),
            ("Signer #1 certificate SHA-256 digest: abc", "invalid certificate"),
        ):
            with self.subTest(output=output), self.assertRaisesRegex(collector.CollectionError, message):
                collector.parse_apksigner_certificate(output)

    def test_getprop_normalizes_line_endings_and_rejects_blank_values(self) -> None:
        with patch.object(collector, "_run", return_value=subprocess.CompletedProcess([], 0, "value\r\n", "")) as run:
            self.assertEqual("value", collector._getprop("adb", "serial", "ro.test"))
            self.assertEqual("value\n", collector._device_shell("adb", "serial", "getprop", "ro.test"))
        run.assert_called()
        with patch.object(collector, "_device_shell", return_value="  \n"):
            with self.assertRaisesRegex(collector.CollectionError, "property ro.test is blank"):
                collector._getprop("adb", "serial", "ro.test")

    def test_atomic_manifest_write_cleans_failed_temporary_and_rejects_nan(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            manifest = root / "identity.json"
            manifest.write_text('{"old":true}\n', encoding="utf-8")
            with patch.object(stable_files, "_rename_exchange", side_effect=OSError("publish failed")):
                with self.assertRaisesRegex(collector.CollectionError, "publish failed"):
                    collector._atomic_json(manifest, {"new": True})
            self.assertEqual('{"old":true}\n', manifest.read_text(encoding="utf-8"))
            self.assertEqual([], list(root.glob(".identity.json.*.tmp")))
            with self.assertRaises(ValueError):
                collector._atomic_json(manifest, {"value": float("nan")})
            self.assertEqual([], list(root.glob(".identity.json.*.tmp")))

    def test_evidence_records_hash_relative_files_and_reject_duplicates(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            evidence = root / "raw/device.txt"
            evidence.parent.mkdir()
            evidence.write_text("device evidence", encoding="utf-8")
            rows = collector._evidence_file_records(root, [evidence])
            self.assertEqual("raw/device.txt", rows[0]["path"])
            self.assertEqual(collector._sha256(evidence), rows[0]["sha256"])
            with self.assertRaisesRegex(collector.CollectionError, "duplicate evidence path"):
                collector._evidence_file_records(root, [evidence, evidence])

    def test_collection_preflight_rejects_bad_expected_identity_before_git_or_device(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "staging"
            output.mkdir()
            cases = (
                ({"expected_candidate_sha": "g" * 40}, "candidate SHA"),
                ({"expected_candidate_sha": "A" * 40}, "candidate SHA"),
                ({"expected_version_code": 0}, "version code must be positive"),
                ({"expected_app_signing_certificate_sha256": "bad"}, "certificate"),
                ({"package_name": "com.example.other"}, "canonical Forest Run"),
                ({"expected_installer_package": "com.example.installer"}, "installer must be Google Play"),
            )
            defaults = {
                "root": Path("/repo"),
                "output_dir": output,
                "expected_candidate_sha": "a" * 40,
                "expected_version_code": 7,
                "expected_app_signing_certificate_sha256": "b" * 64,
            }
            with patch.object(collector.verify_main_candidate, "verify_main_candidate") as verify:
                for overrides, message in cases:
                    with self.subTest(message=message), self.assertRaisesRegex(collector.CollectionError, message):
                        collector._collect_in_directory(**(defaults | overrides))
            verify.assert_not_called()

    def test_candidate_verification_failure_is_wrapped_without_starting_origin_check(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "stage"
            output.mkdir()
            with patch.object(
                collector.verify_main_candidate,
                "verify_main_candidate",
                side_effect=collector.verify_main_candidate.CandidateVerificationError("dirty tree"),
            ), patch.object(collector, "_verify_origin_main") as verify_origin:
                with self.assertRaisesRegex(collector.CollectionError, "dirty tree"):
                    collector._collect_in_directory(
                        root=Path("/repo"),
                        output_dir=output,
                        expected_candidate_sha="a" * 40,
                        expected_version_code=7,
                        expected_app_signing_certificate_sha256="b" * 64,
                    )
            verify_origin.assert_not_called()

    def test_private_collection_refuses_nonempty_existing_staging_directory(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "stage"
            output.mkdir()
            protected = output / "already-there"
            protected.write_text("keep", encoding="utf-8")
            with patch.object(
                collector.verify_main_candidate,
                "verify_main_candidate",
                return_value=SimpleNamespace(sha="a" * 40),
            ), patch.object(collector, "_verify_origin_main"):
                with self.assertRaisesRegex(collector.CollectionError, "absent or empty"):
                    collector._collect_in_directory(
                        root=Path("/repo"),
                        output_dir=output,
                        expected_candidate_sha="a" * 40,
                        expected_version_code=7,
                        expected_app_signing_certificate_sha256="b" * 64,
                    )
            self.assertEqual("keep", protected.read_text(encoding="utf-8"))

    def test_cli_emits_json_for_collection_success_and_expected_failure(self) -> None:
        arguments = [
            "--root", "/repo",
            "--output-dir", "/tmp/identity",
            "--expected-candidate-sha", "a" * 40,
            "--expected-version-code", "7",
            "--expected-app-signing-certificate-sha256", "b" * 64,
        ]
        summary = SimpleNamespace(to_json=lambda: {"status": "valid", "apk_count": 2})
        with patch.object(collector, "collect", return_value=summary), contextlib.redirect_stdout(io.StringIO()) as stdout:
            self.assertEqual(0, collector.main(arguments))
        self.assertEqual({"status": "valid", "apk_count": 2}, json.loads(stdout.getvalue()))

        with patch.object(
            collector, "collect", side_effect=collector.CollectionError("no device")
        ), contextlib.redirect_stdout(io.StringIO()) as stdout:
            self.assertEqual(1, collector.main(arguments))
        self.assertEqual({"status": "invalid", "error": "no device"}, json.loads(stdout.getvalue()))

    def test_script_entry_guard_runs_cli_and_exits_with_failure_status(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "not-a-repository"
            root.mkdir()
            output = Path(temporary) / "identity"
            argv = [
                str(collector.__file__),
                "--root", str(root),
                "--output-dir", str(output),
                "--expected-candidate-sha", "a" * 40,
                "--expected-version-code", "7",
                "--expected-app-signing-certificate-sha256", "b" * 64,
            ]
            with patch.object(sys, "argv", argv), contextlib.redirect_stdout(io.StringIO()) as stdout:
                with self.assertRaises(SystemExit) as exit_result:
                    runpy.run_path(str(collector.__file__), run_name="__main__")
            self.assertEqual(1, exit_result.exception.code)
            self.assertEqual("invalid", json.loads(stdout.getvalue())["status"])
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
