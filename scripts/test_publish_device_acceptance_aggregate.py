from __future__ import annotations

import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import aggregate_device_acceptance as aggregate
import publish_device_acceptance_aggregate as publisher
from acceptance_test_support import materialize_traced_bundle
from test_validate_device_acceptance import valid_bundle


class PublishDeviceAcceptanceAggregateTest(unittest.TestCase):
    @staticmethod
    def materialize(root: Path, *, commit: str | None = None) -> tuple[Path, dict]:
        bundle = valid_bundle()
        if commit is not None:
            bundle["candidate"]["commit_sha"] = commit
            for session in bundle["sessions"]:
                session["build"]["commit_sha"] = commit
        materialize_traced_bundle(root, bundle)
        manifest = root / "manifest.json"
        manifest.write_text(
            json.dumps(bundle, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        return manifest, bundle

    @staticmethod
    def stage(root: Path, manifest: Path, *, baseline: Path | None = None) -> Path:
        staged = root / ".aggregate.staged.tmp"
        payload = aggregate.aggregate(manifest, baseline_path=baseline)
        staged.write_text(
            json.dumps(payload, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        return staged

    def test_publishes_valid_candidate_only_report_atomically(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest, _ = self.materialize(root)
            staged = self.stage(root, manifest)
            output = root / "aggregate.json"

            summary = publisher.publish(manifest, staged, output)

            self.assertFalse(staged.exists())
            self.assertTrue(output.is_file())
            self.assertFalse(summary["baseline_comparison"])
            self.assertEqual(5, summary["session_count"])

    def test_rejects_source_mutation_after_staging(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest, bundle = self.materialize(root)
            staged = self.stage(root, manifest)
            evidence = root / next(
                item["path"]
                for session in bundle["sessions"]
                for scenario in session["scenarios"].values()
                for item in scenario["evidence_files"]
            )
            evidence.write_bytes(b"mutated after aggregation\n")
            output = root / "aggregate.json"

            with self.assertRaisesRegex(publisher.PublicationError, "invalid acceptance manifest"):
                publisher.publish(manifest, staged, output)

            self.assertTrue(staged.is_file())
            self.assertFalse(output.exists())

    def test_rehashes_non_trace_evidence_after_trace_validation(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest, bundle = self.materialize(root)
            staged = self.stage(root, manifest)
            evidence_entry = bundle["sessions"][1]["scenarios"]["ordinary_play_15m"][
                "evidence_files"
            ][0]
            evidence = root / evidence_entry["path"]
            original = publisher.manifest_traces.validate_manifest_traces

            def validate_then_mutate(path: Path, **kwargs):
                result = original(path, **kwargs)
                evidence.write_bytes(b"mutated during trace validation\n")
                return result

            with patch.object(
                publisher.manifest_traces,
                "validate_manifest_traces",
                side_effect=validate_then_mutate,
            ):
                with self.assertRaisesRegex(
                    publisher.PublicationError,
                    "invalid acceptance manifest",
                ):
                    publisher.publish(manifest, staged, root / "aggregate.json")

            self.assertTrue(staged.is_file())
            self.assertFalse((root / "aggregate.json").exists())

    def test_final_digest_snapshot_rejects_post_acceptance_mutation(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest, bundle = self.materialize(root)
            staged = self.stage(root, manifest)
            evidence_entry = bundle["sessions"][1]["scenarios"]["ordinary_play_15m"][
                "evidence_files"
            ][0]
            evidence = root / evidence_entry["path"]
            original = publisher.acceptance.validate_bundle
            calls = 0

            def validate_then_mutate(*args, **kwargs):
                nonlocal calls
                calls += 1
                result = original(*args, **kwargs)
                if calls == 2:
                    evidence.write_bytes(b"mutated after second acceptance\n")
                return result

            with patch.object(
                publisher.acceptance,
                "validate_bundle",
                side_effect=validate_then_mutate,
            ):
                with self.assertRaisesRegex(
                    publisher.PublicationError,
                    "digest changed before publication",
                ):
                    publisher.publish(manifest, staged, root / "aggregate.json")

            self.assertEqual(2, calls)
            self.assertTrue(staged.is_file())
            self.assertFalse((root / "aggregate.json").exists())

    def test_rejects_staged_mutation_during_final_source_validation(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest, _ = self.materialize(root)
            staged = self.stage(root, manifest)
            original = publisher._validate_manifest

            def validate_then_mutate(path: Path):
                result = original(path)
                staged.write_text(
                    staged.read_text(encoding="utf-8") + " ",
                    encoding="utf-8",
                )
                return result

            with patch.object(
                publisher,
                "_validate_manifest",
                side_effect=validate_then_mutate,
            ):
                with self.assertRaisesRegex(
                    publisher.PublicationError,
                    "staged aggregate changed during final source validation",
                ):
                    publisher.publish(manifest, staged, root / "aggregate.json")

            self.assertTrue(staged.is_file())
            self.assertFalse((root / "aggregate.json").exists())

    def test_rejects_protected_source_mutation_after_snapshot(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest, bundle = self.materialize(root)
            staged = self.stage(root, manifest)
            evidence_entry = bundle["sessions"][1]["scenarios"]["ordinary_play_15m"][
                "evidence_files"
            ][0]
            evidence = root / evidence_entry["path"]
            original = publisher._load_aggregate
            calls = 0

            def load_then_mutate(path: Path):
                nonlocal calls
                calls += 1
                result = original(path)
                if calls == 2:
                    evidence.write_bytes(b"mutated after source snapshot\n")
                return result

            with patch.object(
                publisher,
                "_load_aggregate",
                side_effect=load_then_mutate,
            ):
                with self.assertRaisesRegex(
                    publisher.PublicationError,
                    "protected source changed before publication",
                ):
                    publisher.publish(manifest, staged, root / "aggregate.json")

            self.assertEqual(2, calls)
            self.assertTrue(staged.is_file())
            self.assertFalse((root / "aggregate.json").exists())

    def test_rejects_staged_candidate_identity_substitution(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest, _ = self.materialize(root)
            staged = self.stage(root, manifest)
            payload = json.loads(staged.read_text(encoding="utf-8"))
            payload["candidate_summary"]["candidate"]["commit_sha"] = "f" * 40
            staged.write_text(
                json.dumps(payload, indent=2, sort_keys=True) + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(publisher.PublicationError, "candidate commit"):
                publisher.publish(manifest, staged, root / "aggregate.json")

    def test_rejects_baseline_presence_and_identity_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            candidate_root = root / "candidate"
            baseline_root = root / "baseline"
            candidate, _ = self.materialize(candidate_root)
            baseline, _ = self.materialize(baseline_root, commit="2" * 40)

            candidate_only = self.stage(root, candidate)
            with self.assertRaisesRegex(publisher.PublicationError, "missing the supplied baseline"):
                publisher.publish(
                    candidate,
                    candidate_only,
                    root / "aggregate.json",
                    baseline_path=baseline,
                )

            staged = self.stage(root, candidate, baseline=baseline)
            payload = json.loads(staged.read_text(encoding="utf-8"))
            payload["baseline_comparison"]["baseline_commit_sha"] = "e" * 40
            staged.write_text(
                json.dumps(payload, indent=2, sort_keys=True) + "\n",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(publisher.PublicationError, "baseline commit"):
                publisher.publish(
                    candidate,
                    staged,
                    root / "aggregate.json",
                    baseline_path=baseline,
                )

    def test_rejects_output_aliases_and_preserves_protected_source(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest, bundle = self.materialize(root)
            staged = self.stage(root, manifest)
            evidence = root / next(
                item["path"]
                for session in bundle["sessions"]
                for scenario in session["scenarios"].values()
                for item in scenario["evidence_files"]
            )
            alias = root / "aggregate.json"
            os.link(evidence, alias)
            before = evidence.read_bytes()

            with self.assertRaisesRegex(publisher.PublicationError, "must not alias"):
                publisher.publish(manifest, staged, alias)

            self.assertEqual(before, evidence.read_bytes())
            self.assertTrue(os.path.samefile(evidence, alias))
            self.assertTrue(staged.is_file())

    def test_rejects_symlinks_cross_directory_staging_and_same_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest, _ = self.materialize(root)
            staged = self.stage(root, manifest)
            symlink = root / "aggregate-link.json"
            symlink.symlink_to(root / "real-output.json")
            with self.assertRaisesRegex(publisher.PublicationError, "symbolic link"):
                publisher.publish(manifest, staged, symlink)

            other = root / "other"
            other.mkdir()
            cross_directory = other / "staged.json"
            cross_directory.write_bytes(staged.read_bytes())
            with self.assertRaisesRegex(publisher.PublicationError, "output directory"):
                publisher.publish(manifest, cross_directory, root / "aggregate.json")

            with self.assertRaisesRegex(publisher.PublicationError, "distinct files"):
                publisher.publish(
                    manifest,
                    staged,
                    root / "aggregate.json",
                    baseline_path=manifest,
                )

    def test_cli_failure_leaves_staged_file_for_forensics(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest, _ = self.materialize(root)
            staged = self.stage(root, manifest)
            payload = json.loads(staged.read_text(encoding="utf-8"))
            payload["status"] = "invalid"
            staged.write_text(json.dumps(payload), encoding="utf-8")

            self.assertEqual(
                1,
                publisher.main(
                    [str(manifest), str(staged), str(root / "aggregate.json")]
                ),
            )
            self.assertTrue(staged.is_file())
            self.assertFalse((root / "aggregate.json").exists())


if __name__ == "__main__":
    unittest.main()
