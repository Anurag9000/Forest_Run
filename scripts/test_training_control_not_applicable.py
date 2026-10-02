from __future__ import annotations

import io
import json
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import run_all_training as launcher

from training_control.forest_no_trainable_authority import (
    audit, require_no_trainable_surface, REQUIRED_APPLICATION_FILES,
)


class TrainingControlNotApplicableTest(unittest.TestCase):
    def test_live_repository_is_certified_as_non_trainable(self) -> None:
        result = audit(ROOT)
        self.assertTrue(result.complete)
        self.assertEqual((), result.findings)

    def test_generic_application_vocabulary_does_not_create_false_training_surface(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "scripts" / "ordinary_release.py"
            source.parent.mkdir(parents=True)
            source.write_text(
                "model = 'x86_64'\n"
                "task = 'google_apis'\n"
                "metrics = {'p95_frame_ms': 16, 'crashes': 0}\n"
                "scenario = {'stage': 'release', 'strategy': 'safe'}\n",
                encoding="utf-8",
            )
            self.assertTrue(audit(root).complete)

    def test_real_framework_marker_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "app" / "trainer.py"
            source.parent.mkdir(parents=True)
            marker = "to" + "rch"
            source.write_text(f"import {marker}\n", encoding="utf-8")
            result = audit(root)
            self.assertFalse(result.complete)
            self.assertEqual("py" + "to" + "rch", result.findings[0].category)

    def test_python_comments_and_string_literals_do_not_create_training_surface(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "scripts" / "audit_expectations.py"
            source.parent.mkdir(parents=True)
            source.write_text(
                "# torch tensorflow jax sklearn are words in prose only\n"
                "expected = 'pytorch tensorflow jax sklearn optimizer.step backward'\n"
                "joined = 'py' + 'torch'\n",
                encoding="utf-8",
            )
            result = audit(root)
            self.assertTrue(result.complete, result.findings)

    def test_dynamic_framework_import_string_still_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "app" / "dynamic_loader.py"
            source.parent.mkdir(parents=True)
            framework = "to" + "rch"
            source.write_text(
                "import importlib\n"
                f"backend = importlib.import_module('{framework}')\n",
                encoding="utf-8",
            )
            result = audit(root)
            self.assertFalse(result.complete)
            self.assertEqual("pytorch", result.findings[0].category)
            self.assertEqual("app/dynamic_loader.py", result.findings[0].path)


    def test_direct_importlib_helper_alias_still_detects_executable_framework_load(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "app" / "aliased_loader.py"
            source.parent.mkdir(parents=True)
            framework = "to" + "rch"
            source.write_text(
                "from importlib import import_module as load_module\n"
                f"backend = load_module('{framework}')\n",
                encoding="utf-8",
            )

            result = audit(root)

            self.assertFalse(result.complete)
            self.assertEqual(1, len(result.findings))
            self.assertEqual("pytorch", result.findings[0].category)
            self.assertEqual("app/aliased_loader.py", result.findings[0].path)

    def test_dunder_dynamic_framework_import_still_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "scripts" / "loader.py"
            source.parent.mkdir(parents=True)
            framework = "tensor" + "flow"
            source.write_text(
                f"backend = __import__('{framework}')\n",
                encoding="utf-8",
            )
            result = audit(root)
            self.assertFalse(result.complete)
            self.assertEqual("tensorflow", result.findings[0].category)

    def test_only_root_launcher_is_exempt_not_new_nested_train_entrypoint(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            nested = root / "scripts" / "run_all_training.py"
            nested.parent.mkdir(parents=True)
            marker = "to" + "rch"
            nested.write_text(f"import {marker}\\n", encoding="utf-8")
            result = audit(root)
            self.assertFalse(result.complete)
            self.assertEqual("scripts/run_all_training.py", result.findings[0].path)

    def test_shell_and_android_manifest_source_are_in_scope(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            launch = root / "scripts" / "model_runner.sh"
            manifest = root / "app" / "src" / "main" / "AndroidManifest.xml"
            launch.parent.mkdir(parents=True)
            manifest.parent.mkdir(parents=True)
            marker = "to" + "rch"
            framework = "tensor" + "flow"
            launch.write_text(f"python -m {marker}\\n", encoding="utf-8")
            manifest.write_text(f'<service android:name="{framework}"/>\\n', encoding="utf-8")
            result = audit(root)
            self.assertFalse(result.complete)
            self.assertEqual(
                {"scripts/model_runner.sh", "app/src/main/AndroidManifest.xml"},
                {finding.path for finding in result.findings},
            )

    def test_generated_audit_evidence_does_not_poison_future_source_scans(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            artifact = root / "artifacts" / "training_control" / "old_result.json"
            artifact.parent.mkdir(parents=True)
            marker = "to" + "rch"
            artifact.write_text(f'{{"prior_failure": "{marker}"}}\\n', encoding="utf-8")
            result = audit(root)
            self.assertTrue(result.complete)
            self.assertNotIn("artifacts/training_control/old_result.json", result.scanned_files)

    def test_notebook_markdown_is_ignored_but_code_cell_framework_import_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            notebook = root / "experiments" / "probe.ipynb"
            notebook.parent.mkdir(parents=True)
            notebook.write_text(json.dumps({
                "cells": [
                    {"cell_type": "markdown", "source": ["torch tensorflow prose only"]},
                    {"cell_type": "code", "source": ["import " + ("to" + "rch") + "\\n"]},
                ],
                "metadata": {},
                "nbformat": 4,
                "nbformat_minor": 5,
            }), encoding="utf-8")
            result = audit(root)
            self.assertFalse(result.complete)
            self.assertEqual(("experiments/probe.ipynb",), result.scanned_files)
            self.assertEqual("pytorch", result.findings[0].category)

    def test_malformed_notebook_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            notebook = root / "experiments" / "broken.ipynb"
            notebook.parent.mkdir(parents=True)
            notebook.write_text("{not-json", encoding="utf-8")
            result = audit(root)
            self.assertFalse(result.complete)
            self.assertEqual("notebook-parse-error", result.findings[0].category)

    def test_dependency_text_manifests_detect_active_framework_but_ignore_comments(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            requirements = root / "requirements-ci.txt"
            requirements.write_text(
                "# torch==0.0 is documentation only\n"
                + ("to" + "rch") + "==2.5.1\n",
                encoding="utf-8",
            )
            result = audit(root)
            self.assertFalse(result.complete)
            self.assertEqual("pytorch", result.findings[0].category)
            requirements.write_text("# torch==0.0 is documentation only\n", encoding="utf-8")
            self.assertTrue(audit(root).complete)

    def test_model_artifact_in_product_asset_archive_is_not_hidden_by_source_skip(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            artifact = root / "Final_Assets (2)" / "models" / "policy.tflite"
            artifact.parent.mkdir(parents=True)
            artifact.write_bytes(b"fixture")
            result = audit(root)
            self.assertFalse(result.complete)
            rows = [row for row in result.findings if row.category == "ml-model-artifact"]
            self.assertEqual(["Final_Assets (2)/models/policy.tflite"], [row.path for row in rows])

    def test_retained_ml_model_artifact_invalidates_non_ml_classification(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            asset = root / "app" / "src" / "main" / "assets" / "policy.onnx"
            asset.parent.mkdir(parents=True)
            asset.write_bytes(b"not-a-real-model")
            result = audit(root)
            self.assertFalse(result.complete)
            self.assertEqual("ml-model-artifact", result.findings[0].category)
            self.assertEqual("app/src/main/assets/policy.onnx", result.findings[0].path)
            self.assertEqual(0, result.findings[0].line)

    def test_docs_model_named_examples_do_not_create_runtime_surface(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            example = root / "docs" / "architecture.onnx"
            example.parent.mkdir(parents=True)
            example.write_bytes(b"documentation-fixture")
            self.assertTrue(audit(root).complete)

    def test_non_implementation_gradle_ml_dependency_is_detected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            gradle = root / "app" / "build.gradle.kts"
            gradle.parent.mkdir(parents=True)
            runtime = "onnx" + "runtime"
            coordinate = f"com.microsoft.{runtime}:{runtime}-android:1.20.0"
            gradle.write_text(
                f'debugImplementation("{coordinate}")\n',
                encoding="utf-8",
            )
            result = audit(root)
            self.assertFalse(result.complete)
            self.assertIn("ml-dependency", {row.category for row in result.findings})
            self.assertEqual(
                (f'app/build.gradle.kts:debugImplementation("{coordinate}")',),
                result.android_dependencies,
            )

    def test_gradle_dependency_inventory_excludes_unrelated_function_calls(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            gradle = root / "app" / "build.gradle.kts"
            gradle.parent.mkdir(parents=True)
            gradle.write_text(
                'create("release")\n'
                'implementation("androidx.core:core-ktx:1.13.1")\n'
                'testImplementation("junit:junit:4.13.2")\n',
                encoding="utf-8",
            )
            result = audit(root)
            self.assertEqual(
                (
                    'app/build.gradle.kts:implementation("androidx.core:core-ktx:1.13.1")',
                    'app/build.gradle.kts:testImplementation("junit:junit:4.13.2")',
                ),
                result.android_dependencies,
            )

    def test_scope_manifest_changes_when_unscanned_product_path_is_added(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "scripts" / "ordinary_release.py"
            source.parent.mkdir(parents=True)
            source.write_text("value = 1\\n", encoding="utf-8")
            first = audit(root)
            asset = root / "Final_Assets (2)" / "char" / "new_sprite.png"
            asset.parent.mkdir(parents=True)
            asset.write_bytes(b"png-fixture")
            second = audit(root)
            self.assertEqual(first.source_manifest_sha256, second.source_manifest_sha256)
            self.assertNotEqual(first.scope_manifest_sha256, second.scope_manifest_sha256)
            self.assertRegex(second.scope_manifest_sha256, r"^[0-9a-f]{64}$")

    def test_source_manifest_digest_is_stable_and_content_sensitive(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "scripts" / "ordinary_release.py"
            source.parent.mkdir(parents=True)
            source.write_text("value = 1\n", encoding="utf-8")
            first = audit(root)
            second = audit(root)
            self.assertEqual(first.source_manifest_sha256, second.source_manifest_sha256)
            self.assertRegex(first.source_manifest_sha256, r"^[0-9a-f]{64}$")
            source.write_text("value = 2\n", encoding="utf-8")
            third = audit(root)
            self.assertNotEqual(first.source_manifest_sha256, third.source_manifest_sha256)

    def test_symlinked_source_fails_without_dereferencing_target(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            external = root.parent / (root.name + "-external-trainer.py")
            marker = "to" + "rch"
            external.write_text(f"import {marker}\n", encoding="utf-8")
            linked = root / "scripts" / "external.py"
            linked.parent.mkdir(parents=True)
            try:
                linked.symlink_to(external)
            except (OSError, NotImplementedError) as exc:
                self.skipTest(f"symlinks unavailable: {exc}")
            try:
                result = audit(root)
                self.assertFalse(result.complete)
                self.assertEqual(("scripts/external.py",), tuple(
                    row.path for row in result.findings if row.category == "symlink-source"
                ))
                self.assertNotIn("py" + "torch", {row.category for row in result.findings})
                self.assertNotIn("scripts/external.py", result.scanned_files)
            finally:
                external.unlink(missing_ok=True)

    def test_required_application_file_symlink_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for relative in REQUIRED_APPLICATION_FILES:
                file = root / relative
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text(
                    'implementation("androidx.core:core-ktx:1.13.1")\n'
                    if relative == "app/build.gradle.kts" else "// retained app fixture\n",
                    encoding="utf-8",
                )
            required = root / "app/src/main/java/com/anurag9000/forestrun/MainActivity.kt"
            target = root / "MainActivity.real.kt"
            target.write_text(required.read_text(encoding="utf-8"), encoding="utf-8")
            required.unlink()
            try:
                required.symlink_to(target)
            except (OSError, NotImplementedError) as exc:
                self.skipTest(f"symlinks unavailable: {exc}")
            with self.assertRaisesRegex(RuntimeError, "not symlinks"):
                require_no_trainable_surface(root)

    def test_empty_repository_cannot_be_certified_as_non_trainable(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.assertTrue(audit(root).complete)  # scanner has nothing to find
            with self.assertRaisesRegex(RuntimeError, "incomplete Android source tree"):
                require_no_trainable_surface(root)

    def test_missing_individual_app_or_build_source_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for relative in REQUIRED_APPLICATION_FILES:
                file = root / relative
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text(
                    'implementation("androidx.core:core-ktx:1.13.1")\\n'
                    if relative == "app/build.gradle.kts" else "// retained app fixture\\n",
                    encoding="utf-8",
                )
            self.assertTrue(require_no_trainable_surface(root).complete)
            for relative in REQUIRED_APPLICATION_FILES:
                file = root / relative
                original = file.read_bytes()
                file.unlink()
                try:
                    with self.subTest(missing=relative):
                        with self.assertRaisesRegex(RuntimeError, "incomplete Android source tree"):
                            require_no_trainable_surface(root)
                finally:
                    file.write_bytes(original)

    def test_empty_android_dependency_manifest_cannot_be_certified(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for relative in REQUIRED_APPLICATION_FILES:
                file = root / relative
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text("// retained app fixture\\n", encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "no Android dependencies"):
                require_no_trainable_surface(root)

    def test_failed_audit_replaces_previous_pass_with_failure_report(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            output = root / "coverage.json"
            output.write_text('{"status":"pass"}', encoding="utf-8")
            with mock.patch.object(launcher, "ROOT", root), \
                 mock.patch.object(launcher, "build_certificate",
                                   side_effect=RuntimeError("retained ML marker")), \
                 redirect_stdout(io.StringIO()) as stdout:
                status = launcher.main(["--output", str(output)])
            self.assertEqual(status, 2)
            report = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual("fail", report["status"])
            self.assertEqual("unresolved", report["classification"])
            self.assertIsNone(report["ml_training_applicable"])
            self.assertFalse(report["execution_claim_emitted"])
            self.assertIn("retained ML marker", report["error"])
            self.assertNotIn('"status": "pass"', stdout.getvalue())

    def test_publication_error_cannot_leave_old_pass_at_output_path(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            output = root / "coverage.json"
            output.write_text('{"status":"pass"}', encoding="utf-8")
            with mock.patch.object(launcher, "ROOT", root), \
                 mock.patch.object(launcher, "build_certificate",
                                   return_value={"status": "pass"}), \
                 mock.patch.object(launcher, "_atomic_json",
                                   side_effect=OSError("disk unavailable")):
                with self.assertRaisesRegex(OSError, "disk unavailable"):
                    launcher.main(["--output", str(output)])
            self.assertFalse(output.exists(), "stale PASS must not survive failed publication")

    def test_root_command_emits_truthful_n_a_certificate(self) -> None:
        result = subprocess.run(
            [sys.executable, str(ROOT / "run_all_training.py"), "--training-control-audit"],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)
        payload = json.loads(
            (ROOT / ".training_control/coverage_report.json").read_text(encoding="utf-8")
        )
        self.assertFalse(payload["ml_training_applicable"])
        self.assertEqual("no_retained_trainable_surface", payload["classification"])
        self.assertEqual("pass", payload["status"])
        self.assertFalse(payload["ordinary_application_registries_are_training_surfaces"])
        scan_digest = payload["scan"]["source_manifest_sha256"]
        dataset_digest = payload["dataset_cohorts"]["source_manifest_sha256"]
        scope_digest = payload["scan"]["scope_manifest_sha256"]
        dataset_scope_digest = payload["dataset_cohorts"]["scope_manifest_sha256"]
        self.assertEqual(scan_digest, dataset_digest)
        self.assertEqual(scope_digest, dataset_scope_digest)
        self.assertRegex(scan_digest, r"^[0-9a-f]{64}$")
        self.assertRegex(scope_digest, r"^[0-9a-f]{64}$")


if __name__ == "__main__":
    unittest.main()
