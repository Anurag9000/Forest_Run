from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from PIL import Image

import visual_baseline_provenance as provenance


class VisualBaselineProvenanceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.baseline = self.root / "baseline"
        self.baseline.mkdir()
        self.manifest = self.root / "manifest.json"
        self.manifest.write_text(
            json.dumps(
                {
                    "screenshots": [
                        {
                            "order": 1,
                            "raw_file": "01_FOREST.png",
                            "final_file": "01_forest.png",
                            "scenario": "FOREST",
                            "title": "Forest",
                            "purpose": "Provenance test",
                        },
                        {
                            "order": 2,
                            "raw_file": "02_BLOOM.png",
                            "final_file": "02_bloom.png",
                            "scenario": "BLOOM",
                            "title": "Bloom",
                            "purpose": "Provenance test",
                        },
                    ]
                }
            ),
            encoding="utf-8",
        )
        for index, name in enumerate(("01_forest.png", "02_bloom.png"), start=1):
            Image.new("RGB", (5, 4), (index, index + 1, index + 2)).save(
                self.baseline / name,
                format="PNG",
            )
        self.candidate_sha = "a" * 40

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def test_visual_baseline_schema_version_must_be_integer(self) -> None:
        for invalid in (True, False, 1.0, "1", None):
            with self.subTest(value=invalid):
                payload = provenance.build_provenance(
                    manifest_path=self.manifest,
                    baseline_dir=self.baseline,
                    filename_field="final_file",
                    baseline_candidate_sha=self.candidate_sha,
                )
                payload["schemaVersion"] = invalid
                output = self.root / "baseline-provenance.json"
                provenance.publish(output, payload)
                with self.assertRaisesRegex(
                    provenance.VisualBaselineProvenanceError, "schema/kind"
                ):
                    provenance.verify_provenance(
                        output,
                        manifest_path=self.manifest,
                        baseline_dir=self.baseline,
                        filename_field="final_file",
                    )

    def test_build_and_verify_round_trip(self) -> None:
        payload = provenance.build_provenance(
            manifest_path=self.manifest,
            baseline_dir=self.baseline,
            filename_field="final_file",
            baseline_candidate_sha=self.candidate_sha,
        )
        self.assertEqual("visual_baseline_identity", payload["kind"])
        self.assertEqual(2, payload["screenshotCount"])
        self.assertEqual(self.candidate_sha, payload["baselineCandidateSha"])
        output = self.root / "baseline-provenance.json"
        provenance.publish(output, payload)
        self.assertEqual(
            payload,
            provenance.verify_provenance(
                output,
                manifest_path=self.manifest,
                baseline_dir=self.baseline,
                filename_field="final_file",
            ),
        )

    def test_modified_baseline_is_rejected(self) -> None:
        payload = provenance.build_provenance(
            manifest_path=self.manifest,
            baseline_dir=self.baseline,
            filename_field="final_file",
            baseline_candidate_sha=self.candidate_sha,
        )
        output = self.root / "baseline-provenance.json"
        provenance.publish(output, payload)
        Image.new("RGB", (5, 4), (99, 98, 97)).save(
            self.baseline / "01_forest.png",
            format="PNG",
        )
        with self.assertRaisesRegex(
            provenance.VisualBaselineProvenanceError,
            "does not match",
        ):
            provenance.verify_provenance(
                output,
                manifest_path=self.manifest,
                baseline_dir=self.baseline,
                filename_field="final_file",
            )

    def test_modified_manifest_is_rejected(self) -> None:
        payload = provenance.build_provenance(
            manifest_path=self.manifest,
            baseline_dir=self.baseline,
            filename_field="final_file",
            baseline_candidate_sha=self.candidate_sha,
        )
        output = self.root / "baseline-provenance.json"
        provenance.publish(output, payload)
        source = json.loads(self.manifest.read_text(encoding="utf-8"))
        source["screenshots"][0]["purpose"] = "Changed manifest identity"
        self.manifest.write_text(json.dumps(source), encoding="utf-8")
        with self.assertRaisesRegex(
            provenance.VisualBaselineProvenanceError,
            "does not match",
        ):
            provenance.verify_provenance(
                output,
                manifest_path=self.manifest,
                baseline_dir=self.baseline,
                filename_field="final_file",
            )

    def test_tampered_descriptor_and_duplicate_keys_fail_closed(self) -> None:
        payload = provenance.build_provenance(
            manifest_path=self.manifest,
            baseline_dir=self.baseline,
            filename_field="final_file",
            baseline_candidate_sha=self.candidate_sha,
        )
        output = self.root / "baseline-provenance.json"
        payload["screenshotSetSha256"] = "0" * 64
        provenance.publish(output, payload)
        with self.assertRaises(provenance.VisualBaselineProvenanceError):
            provenance.verify_provenance(
                output,
                manifest_path=self.manifest,
                baseline_dir=self.baseline,
                filename_field="final_file",
            )

        output.write_text(
            '{"schemaVersion":1,"schemaVersion":1}',
            encoding="utf-8",
        )
        with self.assertRaisesRegex(
            provenance.VisualBaselineProvenanceError,
            "duplicate JSON object key",
        ):
            provenance.verify_provenance(
                output,
                manifest_path=self.manifest,
                baseline_dir=self.baseline,
                filename_field="final_file",
            )

    def test_candidate_sha_is_exact_lowercase_git_sha(self) -> None:
        for candidate in ("A" * 40, "a" * 39, "main"):
            with self.subTest(candidate=candidate):
                with self.assertRaisesRegex(
                    provenance.VisualBaselineProvenanceError,
                    "40 lowercase",
                ):
                    provenance.build_provenance(
                        manifest_path=self.manifest,
                        baseline_dir=self.baseline,
                        filename_field="final_file",
                        baseline_candidate_sha=candidate,
                    )


if __name__ == "__main__":
    unittest.main()

class VisualBaselineProvenanceBoundaryTest(VisualBaselineProvenanceTest):
    def test_rejects_invalid_manifest_field_candidate_and_file(self) -> None:
        with self.assertRaisesRegex(provenance.VisualBaselineProvenanceError, "filename field"):
            provenance.build_provenance(manifest_path=self.manifest, baseline_dir=self.baseline,
                filename_field="other", baseline_candidate_sha=self.candidate_sha)
        for path, content, expected in (
            (self.root / "empty.json", b"", "invalid size"),
            (self.root / "bad.json", b"{", "manifest"),
            (self.root / "large.json", b"x" * (4 * 1024 * 1024 + 1), "invalid size"),
        ):
            path.write_bytes(content)
            with self.subTest(path=path.name), self.assertRaisesRegex(
                provenance.VisualBaselineProvenanceError, expected
            ):
                provenance.build_provenance(manifest_path=path, baseline_dir=self.baseline,
                    filename_field="final_file", baseline_candidate_sha=self.candidate_sha)
        link = self.root / "manifest-link.json"
        link.symlink_to(self.manifest)
        with self.assertRaisesRegex(provenance.VisualBaselineProvenanceError, "non-symlink"):
            provenance.build_provenance(manifest_path=link, baseline_dir=self.baseline,
                filename_field="final_file", baseline_candidate_sha=self.candidate_sha)

    def test_rejects_missing_bad_and_escaping_baseline_files(self) -> None:
        for file_name in ("missing.png", "../outside.png", str(self.root / "outside.png")):
            source = json.loads(self.manifest.read_text())
            source["screenshots"] = source["screenshots"][:1]
            source["screenshots"][0]["final_file"] = file_name
            self.manifest.write_text(json.dumps(source))
            with self.subTest(file_name=file_name), self.assertRaises(provenance.VisualBaselineProvenanceError):
                provenance.build_provenance(manifest_path=self.manifest, baseline_dir=self.baseline,
                    filename_field="final_file", baseline_candidate_sha=self.candidate_sha)

    def test_rejects_symlink_root_nested_symlink_and_non_directory_root(self) -> None:
        outside = self.root / "outside"
        outside.mkdir()
        (outside / "img.png").write_bytes((self.baseline / "01_forest.png").read_bytes())
        (self.baseline / "nested").symlink_to(outside, target_is_directory=True)
        source = json.loads(self.manifest.read_text())
        source["screenshots"] = source["screenshots"][:1]
        source["screenshots"][0]["final_file"] = "01_forest.png"
        self.manifest.write_text(json.dumps(source))
        (self.baseline / "01_forest.png").unlink()
        (self.baseline / "01_forest.png").symlink_to(outside / "img.png")
        with self.assertRaisesRegex(provenance.VisualBaselineProvenanceError, "non-symlink"):
            provenance.build_provenance(manifest_path=self.manifest, baseline_dir=self.baseline,
                filename_field="final_file", baseline_candidate_sha=self.candidate_sha)
        root_link = self.root / "baseline-link"
        root_link.symlink_to(self.baseline, target_is_directory=True)
        with self.assertRaisesRegex(provenance.VisualBaselineProvenanceError, "non-symlink directory"):
            provenance.build_provenance(manifest_path=self.manifest, baseline_dir=root_link,
                filename_field="final_file", baseline_candidate_sha=self.candidate_sha)
        with self.assertRaisesRegex(provenance.VisualBaselineProvenanceError, "missing visual baseline root"):
            provenance.build_provenance(manifest_path=self.manifest, baseline_dir=self.root / "absent",
                filename_field="final_file", baseline_candidate_sha=self.candidate_sha)

    def test_rejects_non_png_corrupt_and_oversized_images(self) -> None:
        source = json.loads(self.manifest.read_text())
        source["screenshots"] = source["screenshots"][:1]
        self.manifest.write_text(json.dumps(source))
        target = self.baseline / "01_forest.png"
        Image.new("RGB", (2, 2)).save(target, format="JPEG")
        with self.assertRaisesRegex(provenance.VisualBaselineProvenanceError, "must be PNG"):
            provenance.build_provenance(manifest_path=self.manifest, baseline_dir=self.baseline,
                filename_field="final_file", baseline_candidate_sha=self.candidate_sha)
        target.write_bytes(b"not an image")
        with self.assertRaisesRegex(provenance.VisualBaselineProvenanceError, "could not decode"):
            provenance.build_provenance(manifest_path=self.manifest, baseline_dir=self.baseline,
                filename_field="final_file", baseline_candidate_sha=self.candidate_sha)
        Image.new("RGB", (provenance.MAX_IMAGE_PIXELS + 1, 1)).save(target, format="PNG")
        with self.assertRaisesRegex(provenance.VisualBaselineProvenanceError, "dimensions are invalid"):
            provenance.build_provenance(manifest_path=self.manifest, baseline_dir=self.baseline,
                filename_field="final_file", baseline_candidate_sha=self.candidate_sha)

    def test_verify_rejects_non_object_unknown_key_and_invalid_json(self) -> None:
        output = self.root / "descriptor.json"
        for raw in (b"[]", b"{", b'{"extra":1}'):
            output.write_bytes(raw)
            with self.subTest(raw=raw), self.assertRaises(provenance.VisualBaselineProvenanceError):
                provenance.verify_provenance(output, manifest_path=self.manifest,
                    baseline_dir=self.baseline, filename_field="final_file")

    def test_publish_is_atomic_and_rejects_symlink_output(self) -> None:
        payload = provenance.build_provenance(manifest_path=self.manifest, baseline_dir=self.baseline,
            filename_field="final_file", baseline_candidate_sha=self.candidate_sha)
        output = self.root / "out" / "nested" / "descriptor.json"
        provenance.publish(output, payload)
        self.assertEqual(payload, json.loads(output.read_text()))
        self.assertEqual([], list(output.parent.glob("*.tmp")))
        target = self.root / "target.json"
        target.write_text("preserve")
        link = self.root / "link.json"
        link.symlink_to(target)
        with self.assertRaisesRegex(provenance.VisualBaselineProvenanceError, "symbolic link"):
            provenance.publish(link, payload)
        self.assertEqual("preserve", target.read_text())

    def test_cli_build_verify_and_error_codes(self) -> None:
        import contextlib
        import io
        output = self.root / "cli.json"
        args = ["build", "--manifest", str(self.manifest), "--baseline-dir", str(self.baseline),
            "--baseline-candidate-sha", self.candidate_sha, "--output", str(output)]
        stdout, stderr = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            self.assertEqual(0, provenance.main(args))
        self.assertEqual("valid", json.loads(stdout.getvalue())["status"])
        verify_args = ["verify", "--provenance", str(output), "--manifest", str(self.manifest),
            "--baseline-dir", str(self.baseline)]
        stdout = io.StringIO()
        with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            self.assertEqual(0, provenance.main(verify_args))
        self.assertEqual("valid", json.loads(stdout.getvalue())["status"])
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(stderr):
            self.assertEqual(1, provenance.main(["build", "--manifest", str(self.root / "absent"),
                "--baseline-dir", str(self.baseline), "--baseline-candidate-sha", self.candidate_sha,
                "--output", str(output)]))
        self.assertIn("provenance error", stderr.getvalue())

    def test_filesystem_inspection_errors_are_reported(self) -> None:
        from unittest import mock
        with mock.patch.object(Path, "lstat", side_effect=PermissionError("denied")):
            with self.assertRaisesRegex(provenance.VisualBaselineProvenanceError, "could not inspect curation manifest"):
                provenance.build_provenance(manifest_path=self.manifest, baseline_dir=self.baseline,
                    filename_field="final_file", baseline_candidate_sha=self.candidate_sha)
        with mock.patch.object(Path, "lstat", side_effect=PermissionError("denied")):
            with self.assertRaisesRegex(provenance.VisualBaselineProvenanceError, "could not inspect visual baseline root"):
                provenance._root(self.baseline)

    def test_image_change_during_inspection_is_rejected(self) -> None:
        from unittest import mock
        target = self.baseline / "01_forest.png"
        with mock.patch.object(provenance, "_sha256", side_effect=("a", "b")):
            with self.assertRaisesRegex(provenance.VisualBaselineProvenanceError, "changed while being inspected"):
                provenance._image_identity(target, scenario="FOREST", file_name=target.name)

    def test_verify_rejects_invalid_candidate_digest_before_rebuild(self) -> None:
        payload = provenance.build_provenance(manifest_path=self.manifest, baseline_dir=self.baseline,
            filename_field="final_file", baseline_candidate_sha=self.candidate_sha)
        payload["baselineCandidateSha"] = "G" * 40
        output = self.root / "bad-sha.json"
        provenance.publish(output, payload)
        with self.assertRaisesRegex(provenance.VisualBaselineProvenanceError, "baselineCandidateSha"):
            provenance.verify_provenance(output, manifest_path=self.manifest,
                baseline_dir=self.baseline, filename_field="final_file")

    def test_verify_digest_shape_guards_remain_defensive(self) -> None:
        from unittest import mock
        payload = provenance.build_provenance(manifest_path=self.manifest, baseline_dir=self.baseline,
            filename_field="final_file", baseline_candidate_sha=self.candidate_sha)
        output = self.root / "digest-shape.json"
        for field, message in (("screenshotSetSha256", "screenshotSetSha256"),
                               ("manifestSha256", "manifestSha256")):
            tampered = dict(payload)
            tampered[field] = "x"
            provenance.publish(output, tampered)
            with mock.patch.object(provenance, "build_provenance", return_value=tampered):
                with self.assertRaisesRegex(provenance.VisualBaselineProvenanceError, message):
                    provenance.verify_provenance(output, manifest_path=self.manifest,
                        baseline_dir=self.baseline, filename_field="final_file")

    def test_publish_detects_symlink_swap_and_cleans_temp(self) -> None:
        from unittest import mock
        payload = provenance.build_provenance(manifest_path=self.manifest, baseline_dir=self.baseline,
            filename_field="final_file", baseline_candidate_sha=self.candidate_sha)
        output = self.root / "swap.json"
        target = self.root / "kept.json"
        target.write_text("keep")
        real_fsync = provenance.os.fsync
        def swap_link(descriptor: int) -> None:
            real_fsync(descriptor)
            output.symlink_to(target)
        with mock.patch.object(provenance.os, "fsync", side_effect=swap_link):
            with self.assertRaisesRegex(provenance.VisualBaselineProvenanceError, "became a symbolic link"):
                provenance.publish(output, payload)
        self.assertEqual("keep", target.read_text())
        self.assertEqual([], list(self.root.glob(".swap.json.*.tmp")))

    def test_module_entrypoint_runs_as_script(self) -> None:
        import subprocess
        result = subprocess.run(["python3", str(Path(provenance.__file__)), "--help"],
            capture_output=True, text=True, check=False)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("build", result.stdout)
        self.assertIn("verify", result.stdout)

    def test_baseline_path_helper_rejects_lexical_and_resolved_escape(self) -> None:
        outside = self.root / "outside.png"
        outside.write_bytes((self.baseline / "01_forest.png").read_bytes())
        with self.assertRaisesRegex(provenance.VisualBaselineProvenanceError, "escapes visual baseline root"):
            provenance._baseline_file(self.baseline, "../outside.png")
        nested = self.baseline / "nested"
        nested.mkdir()
        (nested / "escape.png").symlink_to(outside)
        from unittest import mock
        with mock.patch.object(provenance, "_regular_file", return_value=nested / "escape.png"):
            with self.assertRaisesRegex(provenance.VisualBaselineProvenanceError, "resolve inside"):
                provenance._baseline_file(self.baseline, "nested/escape.png")

    def test_script_entrypoint_executes_build(self) -> None:
        import subprocess
        output = self.root / "entrypoint.json"
        result = subprocess.run(["python3", str(Path(provenance.__file__)), "build",
            "--manifest", str(self.manifest), "--baseline-dir", str(self.baseline),
            "--baseline-candidate-sha", self.candidate_sha, "--output", str(output)],
            capture_output=True, text=True, check=False)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("valid", json.loads(result.stdout)["status"])
        self.assertTrue(output.is_file())
