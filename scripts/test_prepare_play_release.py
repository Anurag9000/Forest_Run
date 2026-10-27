from __future__ import annotations

import json
import hashlib
import sys
import builtins
import runpy
import subprocess
import tempfile
import unittest
from types import SimpleNamespace
from contextlib import ExitStack, redirect_stdout
import io
from pathlib import Path
from unittest.mock import patch

from PIL import Image

import prepare_play_release as preparer
import stable_file_reader


class PlayReleasePreparationTest(unittest.TestCase):
    @staticmethod
    def build_text(
        *,
        application_id: str = "com.anurag9000.forestrun",
        version_name: str = "1.2.3",
        version_code: int = 9,
        compile_sdk: int = 36,
        target_sdk: int = 35,
        min_sdk: int = 24,
    ) -> str:
        return "\n".join(
            (
                f'applicationId = "{application_id}"',
                f'versionName = "{version_name}"',
                f"versionCode = {version_code}",
                f"compileSdk = {compile_sdk}",
                f"targetSdk = {target_sdk}",
                f"minSdk = {min_sdk}",
            )
        )

    def test_parse_assignment_accepts_supported_value_forms_and_rejects_missing(self) -> None:
        self.assertEqual("com.anurag9000.forestrun", preparer.parse_assignment(
            'applicationId = "com.anurag9000.forestrun"', "applicationId"
        ))
        self.assertEqual("36", preparer.parse_assignment("compileSdk=36", "compileSdk"))
        with self.assertRaisesRegex(SystemExit, "Unable to parse missingSdk"):
            preparer.parse_assignment('targetSdk = "latest"', "missingSdk")

    def test_parse_assignment_ignores_line_and_nested_block_comments(self) -> None:
        build = """// targetSdk = 35
/* old values
   targetSdk = 34 /* nested targetSdk = 33 */
*/
targetSdk = 37
"""
        self.assertEqual("37", preparer.parse_assignment(build, "targetSdk"))

    def test_kotlin_comment_scanner_preserves_comment_markers_inside_strings(self) -> None:
        source = r'''url = "https://host/*literal*/" // remove this
raw = """// keep this /* too */"""
escaped = "a quote: \" // still string" // remove this too
character = '/'
'''
        stripped = preparer._strip_kotlin_comments(source)
        self.assertIn('url = "https://host/*literal*/"', stripped)
        self.assertIn('raw = """// keep this /* too */"""', stripped)
        self.assertIn(r'escaped = "a quote: \" // still string"', stripped)
        self.assertIn("character = '/'", stripped)
        self.assertNotIn("remove this", stripped)

    def test_project_identity_uses_active_sdk_assignment_after_comments(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            manifest = Path(temporary) / "AndroidManifest.xml"
            manifest.write_text("<manifest />", encoding="utf-8")
            build = self.build_text().replace(
                "targetSdk = 35", "// targetSdk = 35\ntargetSdk = 37"
            )
            with patch.object(preparer, "MANIFEST_FILE", manifest):
                with self.assertRaisesRegex(SystemExit, "SDK ordering"):
                    preparer.verify_project_identity(build, False)

    def test_project_identity_validates_package_version_sdk_order_and_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            manifest = Path(temporary) / "AndroidManifest.xml"
            manifest.write_text("<manifest />", encoding="utf-8")
            with patch.object(preparer, "MANIFEST_FILE", manifest):
                identity = preparer.verify_project_identity(self.build_text(), False)
                self.assertEqual("com.anurag9000.forestrun", identity["application_id"])
                self.assertEqual(9, identity["version_code"])
                for build, message in (
                    (self.build_text(application_id="com.example.forestrun"), "placeholder"),
                    (self.build_text(version_code=0), "must be positive"),
                    (self.build_text(version_name="one"), "versionName"),
                    (self.build_text(min_sdk=36), "SDK ordering"),
                    (self.build_text(target_sdk=37), "SDK ordering"),
                ):
                    with self.subTest(message=message), self.assertRaisesRegex(SystemExit, message):
                        preparer.verify_project_identity(build, False)
                dry_run_identity = preparer.verify_project_identity(
                    self.build_text(application_id="com.example.forestrun"), True
                )
                self.assertEqual("com.example.forestrun", dry_run_identity["application_id"])

    def test_release_signing_missing_config_and_credentials_fail_closed(self) -> None:
        with patch.dict("os.environ", {}, clear=True), patch.object(
            preparer, "read_external_gradle_properties", return_value={}
        ):
            self.assertIsNone(preparer.verify_release_signing("android {}", True))
            with self.assertRaisesRegex(SystemExit, "No explicit release signing"):
                preparer.verify_release_signing("android {}", False)

            build = 'signingConfigs { create("release") {} }\nbuildTypes { release {\n signingConfig = signingConfigs.getByName("release")\n} }'
            self.assertIsNone(preparer.verify_release_signing(build, True))
            with self.assertRaisesRegex(SystemExit, "credentials are missing"):
                preparer.verify_release_signing(build, False)

    def test_release_signing_resolves_external_credentials_and_keystore(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            keystore = root / "release.keystore"
            keystore.write_bytes(b"opaque signing material")
            build = 'signingConfigs { create("release") {} }\nbuildTypes { release {\n signingConfig = signingConfigs.getByName("release")\n} }'
            environment = {
                "FOREST_RUN_KEYSTORE": str(keystore),
                "FOREST_RUN_STORE_PASSWORD": "store-secret",
                "FOREST_RUN_KEY_ALIAS": "upload",
                "FOREST_RUN_KEY_PASSWORD": "key-secret",
            }
            with patch.dict("os.environ", environment, clear=True), patch.object(
                preparer, "read_external_gradle_properties", return_value={}
            ):
                signing = preparer.verify_release_signing(build, False)
            self.assertIsNotNone(signing)
            self.assertEqual(keystore, signing.keystore)
            self.assertEqual("upload", signing.alias)
            self.assertEqual("store-secret", signing.store_password)

    def test_candidate_tree_rejects_dirty_invalid_changed_candidate_and_handles_detached_head(self) -> None:
        with patch.object(preparer.shutil, "which", return_value="git"):
            with patch.object(preparer, "run_text", return_value=" M app/file.kt"):
                with self.assertRaisesRegex(SystemExit, "clean candidate tree"):
                    preparer.verify_candidate_tree()

            with patch.object(preparer, "run_text", side_effect=["", "not-a-sha"]):
                with self.assertRaisesRegex(SystemExit, "invalid candidate SHA"):
                    preparer.verify_candidate_tree()

            with patch.object(preparer, "run_text", side_effect=["", "a" * 40]), patch.object(
                preparer.subprocess, "run", return_value=subprocess.CompletedProcess([], 1, "", "")
            ):
                with self.assertRaisesRegex(SystemExit, "changed during"):
                    preparer.verify_candidate_tree("b" * 40)

            with patch.object(preparer, "run_text", side_effect=["", "a" * 40]), patch.object(
                preparer.subprocess, "run", return_value=subprocess.CompletedProcess([], 1, "", "")
            ):
                candidate = preparer.verify_candidate_tree("a" * 40)
            self.assertEqual("(detached HEAD)", candidate["branch"])

    def test_png_inspection_checks_signature_dimensions_and_hash(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            png = root / "graphic.png"
            Image.new("RGB", (32, 16), (30, 90, 120)).save(png)
            with patch.object(preparer, "ROOT", root):
                result = preparer.inspect_png(png, (32, 16))
                self.assertEqual((32, 16), (result["width"], result["height"]))
                with self.assertRaisesRegex(SystemExit, "expected 16x32"):
                    preparer.inspect_png(png, (16, 32))
                png.write_bytes(b"not png")
                with self.assertRaisesRegex(SystemExit, "Invalid PNG"):
                    preparer.inspect_png(png)

    def test_require_file_rejects_symlinked_file_and_repo_parent(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "repo"
            outside = Path(temporary) / "outside.txt"
            root.mkdir()
            outside.write_text("release input", encoding="utf-8")
            direct_link = root / "input.txt"
            try:
                direct_link.symlink_to(outside)
            except (OSError, NotImplementedError) as exc:
                self.skipTest(f"symlinks unavailable: {exc}")
            with patch.object(preparer, "ROOT", root):
                with self.assertRaisesRegex(SystemExit, "Symbolic link is not allowed"):
                    preparer.require_file(direct_link, "release input")

                external_link = root.parent / "external-input-link"
                external_link.symlink_to(outside)
                with self.assertRaisesRegex(SystemExit, "Symbolic link is not allowed"):
                    preparer.require_file(external_link, "external release input")

                link_parent = root / "linked-directory"
                link_parent.symlink_to(outside.parent, target_is_directory=True)
                nested_link = link_parent / outside.name
                with self.assertRaisesRegex(SystemExit, "Symbolic link is not allowed"):
                    preparer.require_file(nested_link, "release input")
            self.assertEqual("release input", outside.read_text(encoding="utf-8"))

    def test_require_file_rejects_missing_and_empty_files_with_clear_context(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            with patch.object(preparer, "ROOT", root):
                with self.assertRaisesRegex(SystemExit, "Missing required asset"):
                    preparer.require_file(root / "missing.bin", "required asset")
                empty = root / "empty.bin"
                empty.touch()
                with self.assertRaisesRegex(SystemExit, "Empty required asset"):
                    preparer.require_file(empty, "required asset")
                populated = root / "valid.bin"
                populated.write_bytes(b"x")
                self.assertEqual(populated, preparer.require_file(populated))

    def test_png_diagnostics_support_paths_outside_the_repository(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            outside = Path(temporary) / "outside.png"
            outside.write_bytes(b"not a png")
            with self.assertRaisesRegex(SystemExit, "Invalid PNG .*/outside.png"):
                preparer.inspect_png(outside)
            Image.new("RGB", (16, 8), (1, 2, 3)).save(outside)
            with self.assertRaisesRegex(SystemExit, "outside.png has size 16x8"):
                preparer.inspect_png(outside, (8, 16))
            self.assertEqual(str(outside), preparer.inspect_png(outside)["path"])

    def test_run_text_and_candidate_tree_fail_closed_and_report_branch(self) -> None:
        with patch.object(
            preparer.subprocess,
            "run",
            return_value=subprocess.CompletedProcess([], 0, "  result\n", "warning"),
        ):
            self.assertEqual("result", preparer.run_text(["tool"], "probe"))
        with patch.object(
            preparer.subprocess,
            "run",
            return_value=subprocess.CompletedProcess([], 0, "", "  warning\n"),
        ):
            self.assertEqual("warning", preparer.run_text(["tool"], "probe"))
        with patch.object(
            preparer.subprocess,
            "run",
            return_value=subprocess.CompletedProcess([], 4, "", "  denied\n"),
        ):
            with self.assertRaisesRegex(SystemExit, "probe failed with exit code 4: denied"):
                preparer.run_text(["tool"], "probe")
        with patch.object(preparer.shutil, "which", return_value=None):
            with self.assertRaisesRegex(SystemExit, "git is required"):
                preparer.verify_candidate_tree()
        with patch.object(preparer.shutil, "which", return_value="git"), patch.object(
            preparer, "run_text", side_effect=["", "A" * 40]
        ), patch.object(
            preparer.subprocess,
            "run",
            return_value=subprocess.CompletedProcess([], 0, "main\n", ""),
        ):
            candidate = preparer.verify_candidate_tree("a" * 40)
        self.assertEqual({"sha": "a" * 40, "branch": "main"}, candidate)

    def test_external_gradle_properties_precedence_and_keystore_resolution(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "repo"
            home = Path(temporary) / "home"
            (root / "app").mkdir(parents=True)
            (home / ".gradle").mkdir(parents=True)
            (root / "gradle.properties").write_text(
                "# comment\nIGNORED\nFOREST_RUN_KEY_ALIAS=root\nEMPTY=\n",
                encoding="utf-8",
            )
            (home / ".gradle/gradle.properties").write_text(
                "FOREST_RUN_KEY_ALIAS=home\nOTHER=value=with=equals\n",
                encoding="utf-8",
            )
            with patch.object(preparer, "ROOT", root), patch.object(
                preparer.Path, "home", return_value=home
            ):
                values = preparer.read_external_gradle_properties()
                self.assertEqual("home", values["FOREST_RUN_KEY_ALIAS"])
                self.assertEqual("value=with=equals", values["OTHER"])
                self.assertEqual("", values["EMPTY"])
                self.assertEqual(root / "app/release.keystore", preparer.resolve_keystore_path("release.keystore"))
                self.assertEqual(Path(temporary) / "absolute.keystore", preparer.resolve_keystore_path(str(Path(temporary) / "absolute.keystore")))
            self.assertEqual("from-properties", preparer.resolve_external_value("ABSENT", {"ABSENT": "from-properties"}))
            with patch.dict("os.environ", {"ABSENT": "from-environment"}, clear=True):
                self.assertEqual("from-environment", preparer.resolve_external_value("ABSENT", {"ABSENT": "from-properties"}))
            with patch.dict("os.environ", {"ABSENT": "   "}, clear=True):
                self.assertIsNone(preparer.resolve_external_value("ABSENT", {}))
            with patch.object(preparer, "ROOT", root), patch.object(
                preparer.Path, "home", return_value=home
            ):
                (home / ".gradle/gradle.properties").unlink()
                self.assertEqual(
                    {"FOREST_RUN_KEY_ALIAS": "root", "EMPTY": ""},
                    preparer.read_external_gradle_properties(),
                )

    def test_release_signing_accepts_external_property_credentials(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "app").mkdir()
            keystore = root / "app/upload.jks"
            keystore.write_bytes(b"key bytes")
            properties = {
                "FOREST_RUN_KEYSTORE": "upload.jks",
                "FOREST_RUN_STORE_PASSWORD": "store-secret",
                "FOREST_RUN_KEY_ALIAS": "upload",
                "FOREST_RUN_KEY_PASSWORD": "key-secret",
            }
            build = 'signingConfigs { create("release") {} }\nbuildTypes { release {\n signingConfig = signingConfigs.getByName("release")\n} }'
            with patch.object(preparer, "ROOT", root), patch.dict(
                "os.environ", {}, clear=True
            ), patch.object(preparer, "read_external_gradle_properties", return_value=properties):
                result = preparer.verify_release_signing(build, False)
            self.assertEqual(keystore, result.keystore)
            self.assertEqual("upload", result.alias)

    def test_graphics_verifier_binds_the_exact_png_pair_and_hashes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            graphics = root / "graphics"
            graphics.mkdir()
            feature = graphics / "feature-graphic.png"
            promo = graphics / "promo-square.png"
            Image.new("RGB", (1024, 500), (30, 90, 120)).save(feature)
            Image.new("RGB", (512, 512), (50, 100, 150)).save(promo)

            def write_manifest(outputs):
                (graphics / "graphics_manifest.json").write_text(
                    json.dumps({"outputs": outputs}), encoding="utf-8"
                )

            outputs = [
                {"file": path.name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
                for path in (feature, promo)
            ]
            write_manifest(outputs)
            with patch.object(preparer, "ROOT", root), patch.object(
                preparer, "GRAPHICS_DIR", graphics
            ):
                facts = preparer.verify_graphics()
                self.assertEqual(2, len(facts))
                for bad_outputs, message in (
                    (outputs[:1], "exactly two outputs"),
                    ([outputs[0], outputs[0]], "duplicate output"),
                    ([{"file": "wrong.png", "sha256": "0" * 64}, outputs[1]], "names differ"),
                    ([{"file": outputs[0]["file"], "sha256": 7}, outputs[1]], "malformed"),
                    ([{**outputs[0], "sha256": "0" * 64}, outputs[1]], "hash mismatch"),
                ):
                    write_manifest(bad_outputs)
                    with self.subTest(message=message), self.assertRaisesRegex(SystemExit, message):
                        preparer.verify_graphics()
                (graphics / "graphics_manifest.json").write_text(
                    '{"outputs":[],"outputs":[]}', encoding="utf-8"
                )
                with self.assertRaisesRegex(SystemExit, "Invalid graphics manifest"):
                    preparer.verify_graphics()

    def test_metadata_verification_checks_limits_and_placeholders(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            metadata = root / "metadata"
            metadata.mkdir()
            files = {
                "title.txt": "Forest Run",
                "short-description.txt": "A tiny forest adventure.",
                "full-description.txt": "Run, grow, and meet forest friends.",
            }

            def write_files(values):
                for filename, content in values.items():
                    (metadata / filename).write_text(content, encoding="utf-8")

            write_files(files)
            with patch.object(preparer, "ROOT", root), patch.object(
                preparer, "METADATA_DIR", metadata
            ):
                self.assertEqual(3, len(preparer.verify_metadata()))
                invalid_values = (
                    ({**files, "short-description.txt": " "}, "Metadata file is empty"),
                    ({**files, "title.txt": "T" * 81}, "unexpectedly long"),
                    ({**files, "short-description.txt": "S" * 161}, "unexpectedly long"),
                    ({**files, "full-description.txt": "F" * 10_001}, "unexpectedly long"),
                    ({**files, "full-description.txt": "YOURNAME is here"}, "Placeholder/TODO"),
                    ({**files, "short-description.txt": "Please TODO this"}, "Placeholder/TODO"),
                )
                for values, message in invalid_values:
                    write_files(values)
                    with self.subTest(message=message), self.assertRaisesRegex(SystemExit, message):
                        preparer.verify_metadata()

    def test_audio_verifier_reports_missing_directory_and_names(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            raw = root / "app/src/main/res/raw"
            with patch.object(preparer, "ROOT", root):
                with self.assertRaisesRegex(SystemExit, "Missing Android raw-resource directory"):
                    preparer.verify_audio_assets()
                raw.mkdir(parents=True)
                for name in preparer.REQUIRED_AUDIO:
                    (raw / f"{name}.ogg").write_bytes(b"OggSencoded-audio")
                (raw / "ignored-subdirectory").mkdir()
                self.assertEqual(sorted(preparer.REQUIRED_AUDIO), preparer.verify_audio_assets())
                empty = raw / f"{preparer.REQUIRED_AUDIO[0]}.ogg"
                empty.unlink()
                with self.assertRaisesRegex(SystemExit, "Missing required audio resources"):
                    preparer.verify_audio_assets()
                empty.write_bytes(b"")
                with self.assertRaisesRegex(SystemExit, "empty or not an OGG file"):
                    preparer.verify_audio_assets()
                empty.write_bytes(b"bad!encoded-audio")
                with self.assertRaisesRegex(SystemExit, "invalid OGG signature"):
                    preparer.verify_audio_assets()
                empty.unlink()
                outside = root / "outside.ogg"
                outside.write_bytes(b"OggSencoded-audio")
                try:
                    empty.symlink_to(outside)
                except (OSError, NotImplementedError) as exc:
                    self.skipTest(f"symlinks unavailable: {exc}")
                with self.assertRaisesRegex(SystemExit, "must not be a symbolic link"):
                    preparer.verify_audio_assets()
                empty.unlink()
                empty.write_bytes(b"OggSencoded-audio")
                duplicate = raw / f"{preparer.REQUIRED_AUDIO[0]}.mp3"
                duplicate.write_bytes(b"OggSduplicate")
                with self.assertRaisesRegex(SystemExit, "multiple files with the same Android name"):
                    preparer.verify_audio_assets()

    def test_r8_mapping_requires_application_classes_and_actual_renaming(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            mapping = root / "mapping.txt"
            with patch.object(preparer, "ROOT", root), patch.object(
                preparer, "MAPPING_PATH", mapping
            ):
                mapping.write_text("other.Class -> a:\n", encoding="utf-8")
                with self.assertRaisesRegex(SystemExit, "no Forest Run application classes"):
                    preparer.verify_r8_mapping()
                mapping.write_text(
                    "com.anurag9000.forestrun.MainActivity -> com.anurag9000.forestrun.MainActivity:\n",
                    encoding="utf-8",
                )
                with self.assertRaisesRegex(SystemExit, "does not show any renamed"):
                    preparer.verify_r8_mapping()
                mapping.write_text(
                    "com.anurag9000.forestrun.MainActivity -> a.a:\n",
                    encoding="utf-8",
                )
                report = preparer.verify_r8_mapping()
                self.assertEqual(1, report["renamed_classes"])

    def test_gradle_runner_checks_java_and_task_status(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            wrapper = root / "gradlew"
            wrapper.write_text("#!/bin/sh\n", encoding="utf-8")
            with patch.object(preparer, "ROOT", root), patch.object(
                preparer.shutil, "which", return_value=None
            ):
                with self.assertRaisesRegex(SystemExit, "Java 21 is unavailable"):
                    preparer.run_gradle(["test"])
            with patch.object(preparer, "ROOT", root), patch.object(
                preparer.shutil, "which", return_value="java"
            ), patch.object(
                preparer.subprocess,
                "run",
                return_value=subprocess.CompletedProcess([], 0, "", 'openjdk version "17.0.12"'),
            ):
                with self.assertRaisesRegex(SystemExit, "Java 21 or newer"):
                    preparer.run_gradle(["test"])
            with patch.object(preparer, "ROOT", root), patch.object(
                preparer.shutil, "which", return_value="java"
            ), patch.object(
                preparer.subprocess,
                "run",
                return_value=subprocess.CompletedProcess([], 0, "", "not a Java version"),
            ):
                with self.assertRaisesRegex(SystemExit, "found: not a Java version"):
                    preparer.run_gradle(["test"])
            with patch.object(preparer, "ROOT", root), patch.object(
                preparer.shutil, "which", return_value="java"
            ), patch.object(
                preparer.subprocess,
                "run",
                side_effect=[
                    subprocess.CompletedProcess([], 0, "", 'openjdk version "21.0.1"'),
                    subprocess.CompletedProcess([], 7, "", ""),
                ],
            ):
                with self.assertRaisesRegex(SystemExit, "Gradle release gate failed with exit code 7"):
                    preparer.run_gradle(["test"])
            with patch.object(preparer, "ROOT", root), patch.object(
                preparer.shutil, "which", return_value="java"
            ), patch.object(
                preparer.subprocess,
                "run",
                side_effect=[
                    subprocess.CompletedProcess([], 0, "", 'openjdk version "21.0.1"'),
                    subprocess.CompletedProcess([], 0, "", ""),
                ],
            ):
                self.assertIsNone(preparer.run_gradle(["test"]))

    def test_missing_pillow_dependency_has_actionable_import_error(self) -> None:
        original_import = builtins.__import__

        def without_pillow(name, *args, **kwargs):
            if name == "PIL":
                raise ImportError("injected missing Pillow")
            return original_import(name, *args, **kwargs)

        with patch.object(builtins, "__import__", side_effect=without_pillow):
            with self.assertRaisesRegex(SystemExit, "Pillow is required"):
                runpy.run_path(str(preparer.__file__), run_name="prepare_play_release_probe")

    def test_script_entrypoint_stops_at_clean_candidate_gate(self) -> None:
        dirty = subprocess.CompletedProcess([], 0, " M injected-candidate-change\n", "")
        with patch.object(preparer.shutil, "which", return_value="git"), patch.object(
            preparer.subprocess, "run", return_value=dirty
        ), patch.object(sys, "argv", [str(preparer.__file__), "--skip-build"]):
            with self.assertRaisesRegex(SystemExit, "clean candidate tree"):
                runpy.run_path(str(preparer.__file__), run_name="__main__")

    def test_summary_writer_publishes_both_formats_and_refuses_symlinks(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "repo"
            release = root / "release"
            release.mkdir(parents=True)
            machine = release / "build_summary.json"
            human = release / "BUILD_SUMMARY.md"
            payload = {
                "candidate": {"sha": "a" * 40, "branch": "main"},
                "identity": {
                    "application_id": "com.anurag9000.forestrun",
                    "version_name": "1.2.3",
                    "version_code": 7,
                    "min_sdk": 24,
                    "target_sdk": 35,
                    "compile_sdk": 36,
                },
                "graphics": [{}, {}],
                "screenshots": {
                    "images": [{}, {}, {}, {}],
                    "candidate_sha": "a" * 40,
                    "apk_sha256": "b" * 64,
                    "device_serial": "serial-hash",
                },
                "audio": list(preparer.REQUIRED_AUDIO),
                "bundle": None,
                "r8_mapping": None,
            }
            with patch.object(preparer, "ROOT", root), patch.object(
                preparer, "MACHINE_SUMMARY_PATH", machine
            ), patch.object(preparer, "SUMMARY_PATH", human):
                preparer.write_summaries(payload)
                self.assertEqual(payload, json.loads(machine.read_text(encoding="utf-8")))
                self.assertIn("build skipped (asset-validation dry run)", human.read_text(encoding="utf-8"))

                payload["bundle"] = {
                    "path": "app-release.aab",
                    "bytes": 12,
                    "sha256": "c" * 64,
                    "application_id": "com.anurag9000.forestrun",
                    "version_name": "1.2.3",
                    "version_code": 7,
                    "signature_verified": True,
                    "signer_sha256": "d" * 64,
                    "dex_files": ["classes.dex"],
                }
                payload["r8_mapping"] = {"sha256": "e" * 64, "renamed_classes": 1, "application_classes": 2}
                preparer.write_summaries(payload)
                self.assertIn("Bundle signature verified: `True`", human.read_text(encoding="utf-8"))
                self.assertIn("Renamed application classes: `1` / `2`", human.read_text(encoding="utf-8"))
                payload["r8_mapping"] = None
                preparer.write_summaries(payload)
                self.assertNotIn("Renamed application classes", human.read_text(encoding="utf-8"))
                payload["r8_mapping"] = {
                    "sha256": "e" * 64,
                    "renamed_classes": 1,
                    "application_classes": 2,
                }

                previous_machine = machine.read_bytes()
                protected = Path(temporary) / "outside.md"
                protected.write_text("preserve", encoding="utf-8")
                human.unlink()
                try:
                    human.symlink_to(protected)
                except (OSError, NotImplementedError) as exc:
                    self.skipTest(f"symlinks unavailable: {exc}")
                with self.assertRaisesRegex(SystemExit, "Symbolic link is not allowed"):
                    preparer.write_summaries(payload)
                self.assertEqual(previous_machine, machine.read_bytes())
                self.assertEqual("preserve", protected.read_text(encoding="utf-8"))

    def test_atomic_summary_writer_cleans_temp_and_rejects_unsafe_paths(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "repo"
            root.mkdir()
            output = root / "release/nested/report.txt"
            with patch.object(preparer, "ROOT", root):
                preparer._atomic_write_text(output, "first\n", "test report")
                self.assertEqual("first\n", output.read_text(encoding="utf-8"))
                with patch.object(stable_file_reader, "_rename_exchange", side_effect=OSError("replace failed")):
                    with self.assertRaisesRegex(OSError, "replace failed"):
                        preparer._atomic_write_text(output, "second\n", "test report")
                self.assertEqual("first\n", output.read_text(encoding="utf-8"))
                self.assertEqual([], list(output.parent.glob(".report.txt.*.tmp")))
                with self.assertRaisesRegex(SystemExit, "inside the repository"):
                    preparer._ensure_safe_output_path(Path(temporary) / "outside.txt", "test report")

                outside = Path(temporary) / "outside"
                outside.mkdir()
                parent_link = root / "linked-release"
                try:
                    parent_link.symlink_to(outside, target_is_directory=True)
                except (OSError, NotImplementedError) as exc:
                    self.skipTest(f"symlinks unavailable: {exc}")
                with self.assertRaisesRegex(SystemExit, "Symbolic link is not allowed"):
                    preparer._ensure_safe_output_path(parent_link / "report.txt", "test report")

                blocked_parent = root / "not-a-directory"
                blocked_parent.write_text("x", encoding="utf-8")
                with self.assertRaisesRegex(SystemExit, "not a directory"):
                    preparer._ensure_safe_output_path(blocked_parent / "report.txt", "test report")
                output_directory = root / "report-directory"
                output_directory.mkdir()
                with self.assertRaisesRegex(SystemExit, "not a regular file"):
                    preparer._ensure_safe_output_path(output_directory, "test report")

    def test_atomic_summary_writer_rejects_parent_swap_during_publication(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "repo"
            root.mkdir()
            output = root / "release/nested/report.txt"
            output.parent.mkdir(parents=True)
            moved = root / "moved"
            external = Path(temporary) / "external"
            external.mkdir()
            real_open = preparer.os.open
            swapped = False

            def swap_before_temp_open(path, flags, *args, **kwargs):
                nonlocal swapped
                if (
                    not swapped
                    and kwargs.get("dir_fd") is not None
                    and isinstance(path, str)
                    and path.startswith(".report.txt.")
                ):
                    swapped = True
                    output.parent.rename(moved)
                    output.parent.symlink_to(external, target_is_directory=True)
                return real_open(path, flags, *args, **kwargs)

            with patch.object(preparer, "ROOT", root), patch.object(
                stable_file_reader.os,
                "open",
                side_effect=swap_before_temp_open,
            ):
                with self.assertRaisesRegex(OSError, "symbolic link or non-directory"):
                    preparer._atomic_write_text(output, "unsafe\n", "test report")

            self.assertTrue(swapped)
            self.assertFalse((external / output.name).exists())
            self.assertFalse((moved / output.name).exists())
            self.assertEqual([], list(moved.glob(".report.txt.*.tmp")))

    def test_screenshot_verifier_binds_count_order_dimensions_and_hashes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            screenshots_root = root / "screenshots"
            final = screenshots_root / "final"
            final.mkdir(parents=True)
            report = screenshots_root / "CURATED_SET.md"
            report.write_text("verified set", encoding="utf-8")

            def write_images(dimensions=((64, 32),) * 4, colors=None):
                for index in range(4):
                    size = dimensions[index]
                    color = colors[index] if colors else (index * 40, 80, 120)
                    Image.new("RGB", size, color).save(final / f"{index + 1:02}.png")

            def evidence_for(count=4, hashes=None):
                facts = [preparer.inspect_png(path) for path in sorted(final.glob("*.png"))]
                return SimpleNamespace(
                    count=count,
                    candidate_sha="a" * 40,
                    apk_sha256="b" * 64,
                    device_serial="serial-hash",
                    package_name="com.anurag9000.forestrun",
                    activity_name="com.anurag9000.forestrun.MainActivity",
                    image_sha256=tuple(hashes if hashes is not None else [item["sha256"] for item in facts]),
                )

            write_images()
            with patch.object(preparer, "ROOT", root), patch.object(
                preparer, "SCREENSHOTS_ROOT", screenshots_root
            ), patch.object(preparer, "FINAL_SCREENSHOTS_DIR", final):
                evidence = evidence_for()
                with patch.object(preparer, "verify_curated_set", return_value=evidence):
                    result = preparer.verify_screenshots("a" * 40)
                self.assertEqual(4, len(result["images"]))
                self.assertEqual("b" * 64, result["apk_sha256"])

                with patch.object(
                    preparer,
                    "verify_curated_set",
                    side_effect=preparer.CuratedScreenshotError("sidecar rejected"),
                ):
                    with self.assertRaisesRegex(SystemExit, "sidecar rejected"):
                        preparer.verify_screenshots("a" * 40)
                with patch.object(preparer, "verify_curated_set", return_value=evidence_for(count=3)):
                    with self.assertRaisesRegex(SystemExit, "evidence count mismatch"):
                        preparer.verify_screenshots("a" * 40)
                (final / "04.png").unlink()
                with patch.object(preparer, "verify_curated_set", return_value=evidence_for(count=3)):
                    with self.assertRaisesRegex(SystemExit, "At least 4 curated screenshots"):
                        preparer.verify_screenshots("a" * 40)
                write_images()

                write_images(dimensions=((64, 32), (32, 64), (64, 32), (64, 32)))
                with patch.object(preparer, "verify_curated_set", return_value=evidence_for()):
                    with self.assertRaisesRegex(SystemExit, "mixed dimensions"):
                        preparer.verify_screenshots("a" * 40)
                write_images(dimensions=((32, 64),) * 4)
                with patch.object(preparer, "verify_curated_set", return_value=evidence_for()):
                    with self.assertRaisesRegex(SystemExit, "not landscape"):
                        preparer.verify_screenshots("a" * 40)
                write_images(colors=[(1, 2, 3)] * 4)
                with patch.object(preparer, "verify_curated_set", return_value=evidence_for()):
                    with self.assertRaisesRegex(SystemExit, "exact duplicates"):
                        preparer.verify_screenshots("a" * 40)
                write_images()
                hashes = list(evidence_for().image_sha256)
                with patch.object(
                    preparer,
                    "verify_curated_set",
                    return_value=evidence_for(hashes=reversed(hashes)),
                ):
                    with self.assertRaisesRegex(SystemExit, "ordering or image hashes"):
                        preparer.verify_screenshots("a" * 40)
                report.unlink()
                with patch.object(preparer, "verify_curated_set", return_value=evidence_for()):
                    with self.assertRaisesRegex(SystemExit, "Missing curated screenshot report"):
                        preparer.verify_screenshots("a" * 40)

    def test_screenshot_verifier_rejects_missing_final_directory(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            screenshots_root = root / "screenshots"
            with patch.object(preparer, "ROOT", root), patch.object(
                preparer, "SCREENSHOTS_ROOT", screenshots_root
            ), patch.object(preparer, "FINAL_SCREENSHOTS_DIR", screenshots_root / "final"):
                with self.assertRaisesRegex(SystemExit, "Curated screenshot directory is missing"):
                    preparer.verify_screenshots("a" * 40)

    def test_bundle_verifier_checks_structure_identity_signature_and_unsigned_path(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bundle_path = root / "app-release.aab"
            bundle_path.write_bytes(b"bundle bytes")
            identity = {
                "application_id": "com.anurag9000.forestrun",
                "version_code": 7,
                "version_name": "1.2.3",
            }
            signing = preparer.SigningMaterial(root / "upload.jks", "upload", "secret")
            with patch.object(preparer, "ROOT", root), patch.object(
                preparer, "BUNDLE_PATH", bundle_path
            ), patch.object(
                preparer,
                "verify_bundle_structure",
                return_value={"entries": 5, "dex_files": ["classes.dex"]},
            ), patch.object(
                preparer,
                "inspect_bundle_identity",
                return_value=SimpleNamespace(**identity),
            ), patch.object(
                preparer,
                "verify_bundle_signature",
                return_value=SimpleNamespace(verified=True, signer_sha256="c" * 64),
            ) as verify_signature:
                report = preparer.verify_bundle(identity, signing, False)
                self.assertTrue(report["signature_verified"])
                self.assertEqual("c" * 64, report["signer_sha256"])
                verify_signature.assert_called_once_with(
                    bundle_path,
                    keystore=signing.keystore,
                    alias=signing.alias,
                    store_password=signing.store_password,
                    allow_unsigned=False,
                )
                preparer.verify_bundle(identity, None, True)
                self.assertEqual(
                    {
                        "keystore": None,
                        "alias": None,
                        "store_password": None,
                        "allow_unsigned": True,
                    },
                    verify_signature.call_args.kwargs,
                )
                for verifier in (
                    "verify_bundle_structure",
                    "inspect_bundle_identity",
                    "verify_bundle_signature",
                ):
                    with patch.object(
                        preparer,
                        verifier,
                        side_effect=preparer.ArtifactVerificationError("artifact rejected"),
                    ):
                        with self.subTest(verifier=verifier), self.assertRaisesRegex(SystemExit, "artifact rejected"):
                            preparer.verify_bundle(identity, signing, False)

    def test_main_orchestrates_asset_dry_run_and_full_build_without_real_gradle(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            build_file = root / "app/build.gradle.kts"
            build_file.parent.mkdir(parents=True)
            build_file.write_text("android {}", encoding="utf-8")
            candidate = {"sha": "a" * 40, "branch": "main"}
            identity = {"application_id": "com.example.app", "version_code": 1}
            graphics, metadata, screenshots, audio = [], [], {"images": []}, []
            bundle = {"path": "release.aab"}
            mapping = {"path": "mapping.txt"}
            with ExitStack() as stack:
                stack.enter_context(patch.object(preparer, "ROOT", root))
                stack.enter_context(patch.object(preparer, "BUILD_FILE", build_file))
                mocks = {
                    "verify_candidate_tree": stack.enter_context(patch.object(preparer, "verify_candidate_tree", return_value=candidate)),
                    "verify_project_identity": stack.enter_context(patch.object(preparer, "verify_project_identity", return_value=identity)),
                    "verify_release_signing": stack.enter_context(patch.object(preparer, "verify_release_signing", return_value=None)),
                    "verify_graphics": stack.enter_context(patch.object(preparer, "verify_graphics", return_value=graphics)),
                    "verify_metadata": stack.enter_context(patch.object(preparer, "verify_metadata", return_value=metadata)),
                    "verify_screenshots": stack.enter_context(patch.object(preparer, "verify_screenshots", return_value=screenshots)),
                    "verify_audio_assets": stack.enter_context(patch.object(preparer, "verify_audio_assets", return_value=audio)),
                    "run_gradle": stack.enter_context(patch.object(preparer, "run_gradle")),
                    "verify_bundle": stack.enter_context(patch.object(preparer, "verify_bundle", return_value=bundle)),
                    "verify_r8_mapping": stack.enter_context(patch.object(preparer, "verify_r8_mapping", return_value=mapping)),
                    "write_summaries": stack.enter_context(patch.object(preparer, "write_summaries")),
                }
                with patch.object(sys, "argv", ["prepare_play_release.py", "--skip-build", "--allow-placeholder-id", "--allow-unsigned"]), redirect_stdout(io.StringIO()):
                    self.assertEqual(0, preparer.main())
                mocks["run_gradle"].assert_not_called()
                mocks["verify_bundle"].assert_not_called()
                mocks["verify_candidate_tree"].assert_called_once_with()
                dry_payload = mocks["write_summaries"].call_args.args[0]
                self.assertEqual({"allow_placeholder_id": True, "allow_unsigned": True, "skip_build": True}, dry_payload["dry_run_overrides"])
                mocks["write_summaries"].reset_mock()
                mocks["verify_candidate_tree"].reset_mock()
                with patch.object(sys, "argv", ["prepare_play_release.py"]), redirect_stdout(io.StringIO()):
                    self.assertEqual(0, preparer.main())
                mocks["run_gradle"].assert_called_once_with(["clean", "testDebugUnitTest", "lintRelease", "bundleRelease"])
                self.assertEqual(3, mocks["verify_candidate_tree"].call_count)
                full_payload = mocks["write_summaries"].call_args.args[0]
                self.assertEqual(bundle, full_payload["bundle"])
                self.assertEqual(mapping, full_payload["r8_mapping"])


if __name__ == "__main__":
    unittest.main()
