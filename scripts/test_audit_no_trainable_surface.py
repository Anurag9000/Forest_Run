from __future__ import annotations

import contextlib
import importlib
import io
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from types import ModuleType, SimpleNamespace
from unittest.mock import patch

import audit_no_trainable_surface as audit_cli
import stable_file_reader


class NoTrainableSurfaceCliTest(unittest.TestCase):
    def test_complete_audit_writes_json_and_returns_success(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            with patch.object(audit_cli, "ROOT", root), contextlib.redirect_stdout(io.StringIO()) as stdout:
                status = audit_cli.main()

            output = root / "artifacts/training_control/no_trainable_surface.json"
            report = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(0, status)
            self.assertTrue(report["complete"])
            self.assertEqual(report, json.loads(stdout.getvalue()))
            self.assertFalse(output.with_suffix(".json.tmp").exists())

    def test_incomplete_audit_writes_report_and_returns_failure(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            failed = {"complete": False, "findings": [{"path": "app/Main.kt"}]}
            with patch.object(audit_cli, "ROOT", root), patch.object(
                audit_cli,
                "audit",
                return_value=SimpleNamespace(complete=False, to_dict=lambda: failed),
            ), contextlib.redirect_stdout(io.StringIO()) as stdout:
                status = audit_cli.main()

            output = root / "artifacts/training_control/no_trainable_surface.json"
            self.assertEqual(2, status)
            self.assertEqual(failed, json.loads(output.read_text(encoding="utf-8")))
            self.assertEqual(failed, json.loads(stdout.getvalue()))
            self.assertFalse(output.with_suffix(".json.tmp").exists())

    def test_report_publisher_rejects_symlinked_output_and_parent(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "repo"
            outside = Path(temporary) / "outside"
            root.mkdir()
            outside.mkdir()
            target = outside / "protected.json"
            target.write_text("keep", encoding="utf-8")
            report_dir = root / "artifacts/training_control"
            report_dir.mkdir(parents=True)
            output = report_dir / "no_trainable_surface.json"
            try:
                output.symlink_to(target)
            except (OSError, NotImplementedError) as exc:
                self.skipTest(f"symlinks unavailable: {exc}")
            with patch.object(audit_cli, "ROOT", root):
                with self.assertRaisesRegex(OSError, "symbolic link"):
                    audit_cli._publish_report(output, {"complete": True})
            self.assertEqual("keep", target.read_text(encoding="utf-8"))

            output.unlink()
            report_dir.rmdir()
            (root / "artifacts").rmdir()
            (root / "artifacts").symlink_to(outside, target_is_directory=True)
            with patch.object(audit_cli, "ROOT", root):
                with self.assertRaisesRegex(OSError, "symbolic link or non-directory"):
                    audit_cli._publish_report(output, {"complete": True})
            self.assertFalse((outside / "training_control").exists())

    def test_report_parent_swap_cannot_redirect_atomic_publication(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "repo"
            root.mkdir()
            output = root / "artifacts/training_control/no_trainable_surface.json"
            output.parent.mkdir(parents=True)
            moved = root / "moved"
            external = Path(temporary) / "external"
            external.mkdir()
            real_open = os.open
            swapped = False

            def swap_before_temp_open(path, flags, *args, **kwargs):
                nonlocal swapped
                if (
                    not swapped
                    and kwargs.get("dir_fd") is not None
                    and isinstance(path, str)
                    and path.startswith(".no_trainable_surface.json.")
                ):
                    swapped = True
                    output.parent.rename(moved)
                    output.parent.symlink_to(external, target_is_directory=True)
                return real_open(path, flags, *args, **kwargs)

            with patch.object(audit_cli, "ROOT", root), patch.object(
                stable_file_reader.os,
                "open",
                side_effect=swap_before_temp_open,
            ):
                with self.assertRaisesRegex(OSError, "symbolic link or non-directory"):
                    audit_cli._publish_report(output, {"complete": True})

            self.assertTrue(swapped)
            self.assertFalse((external / output.name).exists())
            self.assertFalse((moved / output.name).exists())
            self.assertEqual([], list(moved.glob(".no_trainable_surface.json.*.tmp")))

    def test_cli_reports_invalid_when_output_path_is_a_symlink(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "repo"
            outside = Path(temporary) / "outside.json"
            root.mkdir()
            outside.write_text("keep", encoding="utf-8")
            output = root / "artifacts/training_control/no_trainable_surface.json"
            output.parent.mkdir(parents=True)
            try:
                output.symlink_to(outside)
            except (OSError, NotImplementedError) as exc:
                self.skipTest(f"symlinks unavailable: {exc}")
            with patch.object(audit_cli, "ROOT", root), contextlib.redirect_stdout(io.StringIO()) as stdout:
                status = audit_cli.main()
            self.assertEqual(2, status)
            self.assertEqual("invalid", json.loads(stdout.getvalue())["status"])
            self.assertEqual("keep", outside.read_text(encoding="utf-8"))

    def test_report_publisher_rejects_directory_and_cleans_failed_replace_temp(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            report_dir = root / "artifacts/training_control"
            report_dir.mkdir(parents=True)
            output = report_dir / "no_trainable_surface.json"
            output.mkdir()
            with patch.object(audit_cli, "ROOT", root):
                with self.assertRaisesRegex(OSError, "regular file"):
                    audit_cli._publish_report(output, {"complete": True})

            output.rmdir()
            output.write_text('{"old":true}\n', encoding="utf-8")
            with patch.object(audit_cli, "ROOT", root), patch.object(
                stable_file_reader,
                "_rename_exchange",
                side_effect=OSError("injected replace failure"),
            ):
                with self.assertRaisesRegex(OSError, "injected replace failure"):
                    audit_cli._publish_report(output, {"complete": False})
            self.assertEqual('{"old":true}\n', output.read_text(encoding="utf-8"))
            self.assertEqual([], list(report_dir.glob(".no_trainable_surface.json.*.tmp")))

    def test_module_entrypoint_bootstraps_path_and_publishes_in_temp_root(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            fake_script = root / "scripts/audit_no_trainable_surface.py"
            fake_script.parent.mkdir()
            result_data = {"complete": True, "findings": []}
            fake_authority = ModuleType("training_control.forest_no_trainable_authority")
            fake_authority.audit = lambda selected_root: SimpleNamespace(
                complete=True, to_dict=lambda: result_data
            )
            fake_package = ModuleType("training_control")
            fake_package.__path__ = []
            source = Path(audit_cli.__file__).read_text(encoding="utf-8")
            namespace = {"__name__": "__main__", "__file__": str(fake_script)}
            original_path = sys.path[:]
            try:
                with patch.dict(
                    sys.modules,
                    {
                        "training_control": fake_package,
                        "training_control.forest_no_trainable_authority": fake_authority,
                    },
                ), contextlib.redirect_stdout(io.StringIO()) as stdout:
                    with self.assertRaises(SystemExit) as exit_result:
                        exec(compile(source, str(audit_cli.__file__), "exec"), namespace)
                self.assertEqual(0, exit_result.exception.code)
                self.assertEqual(result_data, json.loads(stdout.getvalue()))
                self.assertEqual(
                    result_data,
                    json.loads((root / "artifacts/training_control/no_trainable_surface.json").read_text()),
                )
                self.assertEqual(str(root), sys.path[0])
            finally:
                sys.path[:] = original_path


if __name__ == "__main__":
    unittest.main()
