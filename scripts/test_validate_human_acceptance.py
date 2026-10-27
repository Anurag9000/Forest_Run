from __future__ import annotations

import copy
import hashlib
import json
import os
import tempfile
import unittest
from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path
from unittest.mock import patch

import compile_human_acceptance as compiler
import test_validate_device_acceptance as device_fixture
import validate_human_acceptance as human


HUMAN_BYTES = b"forest-run-human-review-evidence-v1\n"


def _human_draft(root: Path) -> dict:
    device = device_fixture.valid_bundle()
    device_fixture.materialize_files(root, device)
    device_path = root / "device-acceptance.json"
    device_path.write_text(json.dumps(device, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    candidate = device["candidate"]
    reviews = []
    reviewers = ("release-owner", "independent-reviewer")
    for index, session in enumerate(device["sessions"]):
        device_class = session["device"]["class"]
        evidence = root / "human" / f"{device_class}.txt"
        evidence.parent.mkdir(parents=True, exist_ok=True)
        evidence.write_bytes(HUMAN_BYTES + device_class.encode("utf-8") + b"\n")
        reviews.append(
            {
                "review_id": f"review-{device_class}-{index}",
                "device_acceptance_session_id": session["session_id"],
                "device_class": device_class,
                "reviewer": reviewers[index % 2],
                "talkback_version": "TalkBack-current-release",
                "switch_access_version": "not_applicable",
                "started_at_utc": "2026-08-01T10:21:00Z",
                "completed_at_utc": "2026-08-01T10:41:00Z",
                "gameplay_checks": {name: "pass" for name in human.GAMEPLAY_CHECKS},
                "accessibility_checks": {
                    name: ("not_applicable" if name == "switch_access" else "pass")
                    for name in human.ACCESSIBILITY_CHECKS
                },
                "presentation_checks": {
                    name: "pass" for name in human.PRESENTATION_CHECKS
                },
                "evidence_files": [f"human/{device_class}.txt"],
                "notes": f"Reviewed {device_class} on the exact internal-store candidate.",
            }
        )

    return {
        "generated_at_utc": "2026-08-01T13:00:00Z",
        "candidate": {
            "repository": candidate["repository"],
            "branch": candidate["branch"],
            "application_id": candidate["application_id"],
            "commit_sha": candidate["commit_sha"],
            "version_code": candidate["version_code"],
            "artifact_sha256": candidate["artifact_sha256"],
            "upload_certificate_sha256": candidate["upload_certificate_sha256"],
            "app_signing_certificate_sha256": candidate["store_delivery"]["app_signing_certificate_sha256"],
        },
        "device_acceptance": "device-acceptance.json",
        "reviews": reviews,
        "final_review": {
            "decision": "approved",
            "reviewers": list(reviewers),
            "reviewed_at_utc": "2026-08-01T12:30:00Z",
            "notes": "Two reviewers independently accepted the complete human matrix.",
        },
    }


class HumanAcceptanceTest(unittest.TestCase):
    def test_compile_file_rejects_output_symlink_without_modifying_target(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft = _human_draft(root)
            draft_path = root / "human-acceptance-draft.json"
            draft_path.write_text(json.dumps(draft), encoding="utf-8")
            target = root / "unrelated.json"
            target.write_text("preserve me", encoding="utf-8")
            output = root / "human-acceptance.json"
            output.symlink_to(target)

            with self.assertRaisesRegex(
                compiler.HumanCompilationError,
                "output paths must not be symlinks",
            ):
                compiler.compile_file(draft_path, output)

            self.assertEqual("preserve me", target.read_text())
            self.assertTrue(output.is_symlink())

    def test_compile_file_rejects_symlinked_draft(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft_path = root / "draft.json"
            draft_path.write_text(json.dumps(_human_draft(root)), encoding="utf-8")
            alias = root / "draft-alias.json"
            alias.symlink_to(draft_path)
            output = root / "output.json"
            with self.assertRaisesRegex(compiler.HumanCompilationError, "non-symlink"):
                compiler.compile_file(alias, output)
            self.assertFalse(output.exists())

    def test_compile_file_rejects_draft_and_summary_output_aliases(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft_path = root / "draft.json"
            draft_path.write_text(json.dumps(_human_draft(root)), encoding="utf-8")
            with self.assertRaisesRegex(compiler.HumanCompilationError, "must not overwrite the draft"):
                compiler.compile_file(draft_path, draft_path)
            output = root / "output.json"
            with self.assertRaisesRegex(compiler.HumanCompilationError, "distinct from draft/output"):
                compiler.compile_file(draft_path, output, summary_path=output)
            self.assertFalse(output.exists())

    def test_failed_rollback_retains_human_acceptance_backups(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            first = root / "human-acceptance.json"
            second = root / "human-acceptance-summary.json"
            first.write_text("old human acceptance", encoding="utf-8")
            second.write_text("old summary", encoding="utf-8")
            with patch("compile_human_acceptance.atomic_publish_files", side_effect=compiler.StableFileError("injected publication failure")):
                with self.assertRaises(compiler.HumanCompilationError) as raised:
                    compiler._publish_transaction(
                        [(first, {"new": 1}), (second, {"new": 2})]
                    )

            self.assertEqual("old human acceptance", first.read_text())
            self.assertEqual("old summary", second.read_text())
            self.assertIn("injected publication failure", str(raised.exception))

    def compile_valid(self, root: Path) -> tuple[dict, human.HumanAcceptanceSummary]:
        return compiler.compile_bundle(_human_draft(root), base_dir=root)

    def test_source_bytes_must_match_human_acceptance_object(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            with self.assertRaisesRegex(human.HumanAcceptanceError, "source bytes do not encode"):
                human.validate_bundle(compiled, source_bytes=b"{}", evidence_base=root)

    def test_compiler_hashes_device_and_review_evidence_then_validates(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, summary = self.compile_valid(root)
            self.assertEqual(device_fixture.SHA, summary.candidate_sha)
            self.assertEqual(5, summary.review_count)
            self.assertEqual(5, len(summary.covered_device_classes))
            self.assertEqual(5, summary.evidence_file_count)
            expected_device = hashlib.sha256((root / "device-acceptance.json").read_bytes()).hexdigest()
            self.assertEqual(expected_device, compiled["device_acceptance"]["sha256"])
            for review in compiled["reviews"]:
                self.assertRegex(review["evidence_files"][0]["sha256"], r"^[0-9a-f]{64}$")

    def test_compile_bundle_rejects_non_object_and_non_json_python_inputs(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            with self.assertRaisesRegex(compiler.HumanCompilationError, "draft must be an object"):
                compiler.compile_bundle(None, base_dir=root)  # type: ignore[arg-type]

            draft = _human_draft(root)
            draft["unexpected_python_value"] = float("nan")
            with self.assertRaisesRegex(
                compiler.HumanCompilationError,
                "draft contains values that are not valid JSON",
            ):
                compiler.compile_bundle(draft, base_dir=root)

    def test_compile_bundle_generates_timestamp_and_normalizes_copy_and_validation_errors(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft = _human_draft(root)
            draft.pop("generated_at_utc")
            compiled, _ = compiler.compile_bundle(draft, base_dir=root)
            self.assertRegex(compiled["generated_at_utc"], r"^\d{4}-\d\d-\d\dT.*Z$")

            class BrokenCopy:
                def __deepcopy__(self, memo):
                    raise ValueError("copy refused")

            with self.assertRaisesRegex(compiler.HumanCompilationError, "cannot be copied safely"):
                compiler.compile_bundle({"bad": BrokenCopy()}, base_dir=root)

            invalid = _human_draft(root)
            invalid.pop("final_review")
            with self.assertRaisesRegex(compiler.HumanCompilationError, "final_review"):
                compiler.compile_bundle(invalid, base_dir=root)

    def test_compile_bundle_rejects_malformed_draft_shapes_timestamps_and_paths(self) -> None:
        cases = (
            ("device path type", lambda draft: draft.__setitem__("device_acceptance", 7), "relative path string"),
            ("device traversal", lambda draft: draft.__setitem__("device_acceptance", "../outside.json"), "safe relative path"),
            ("reviews type", lambda draft: draft.__setitem__("reviews", {}), "reviews must be an array"),
            ("empty reviews", lambda draft: draft.__setitem__("reviews", []), "reviews must not be empty"),
            ("review type", lambda draft: draft["reviews"].__setitem__(0, None), "reviews\\[0\\] must be an object"),
            ("evidence array type", lambda draft: draft["reviews"][0].__setitem__("evidence_files", "evidence.txt"), "evidence_files must be an array"),
            ("empty evidence", lambda draft: draft["reviews"][0].__setitem__("evidence_files", []), "must not be empty"),
            ("evidence path type", lambda draft: draft["reviews"][0].__setitem__("evidence_files", [9]), "relative path string"),
            ("missing evidence", lambda draft: draft["reviews"][0].__setitem__("evidence_files", ["missing.txt"]), "missing"),
            ("invalid timestamp", lambda draft: draft.__setitem__("generated_at_utc", "tomorrow"), "generated_at_utc"),
        )
        for label, mutate, expected in cases:
            with self.subTest(case=label), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                draft = _human_draft(root)
                mutate(draft)
                with self.assertRaisesRegex(compiler.HumanCompilationError, expected):
                    compiler.compile_bundle(draft, base_dir=root)

    def test_compiler_cli_round_trip_and_invalid_input_result(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft_path = root / "draft.json"
            output_path = root / "final.json"
            draft_path.write_text(json.dumps(_human_draft(root)), encoding="utf-8")
            stdout = StringIO()
            with redirect_stdout(stdout):
                self.assertEqual(
                    0,
                    compiler.main(
                        [str(draft_path), str(output_path), "--generated-at-utc", "2026-08-01T13:00:00Z"]
                    ),
                )
            self.assertEqual("valid", json.loads(stdout.getvalue())["status"])
            self.assertTrue(output_path.is_file())

            invalid_draft = root / "invalid.json"
            invalid_draft.write_text("{", encoding="utf-8")
            failure = StringIO()
            with redirect_stdout(failure):
                self.assertEqual(1, compiler.main([str(invalid_draft), str(root / "invalid-output.json")]))
            self.assertEqual("invalid", json.loads(failure.getvalue())["status"])

    def test_missing_device_class_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            compiled["reviews"].pop()
            raw = json.dumps(compiled, sort_keys=True).encode()
            with self.assertRaises(human.HumanAcceptanceError) as raised:
                human.validate_bundle(compiled, source_bytes=raw, evidence_base=root)
            self.assertIn("missing device classes", str(raised.exception))

    def test_failed_gameplay_or_talkback_check_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            for group, name in (
                ("gameplay_checks", "touch_latency"),
                ("accessibility_checks", "talkback_focus_order"),
                ("presentation_checks", "wolf_animation"),
            ):
                with self.subTest(group=group, name=name):
                    mutated = copy.deepcopy(compiled)
                    mutated["reviews"][0][group][name] = "fail"
                    raw = json.dumps(mutated, sort_keys=True).encode()
                    with self.assertRaises(human.HumanAcceptanceError) as raised:
                        human.validate_bundle(mutated, source_bytes=raw, evidence_base=root)
                    self.assertIn("must be pass or not_applicable", str(raised.exception))

    def test_not_applicable_is_narrow_and_cutout_must_pass(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            mutated = copy.deepcopy(compiled)
            mutated["reviews"][0]["gameplay_checks"]["touch_latency"] = "not_applicable"
            with self.assertRaises(human.HumanAcceptanceError):
                human.validate_bundle(
                    mutated,
                    source_bytes=json.dumps(mutated).encode(),
                    evidence_base=root,
                )

            mutated = copy.deepcopy(compiled)
            cutout = next(review for review in mutated["reviews"] if review["device_class"] == "cutout_phone")
            cutout["accessibility_checks"]["cutout_and_aspect_variants"] = "not_applicable"
            with self.assertRaises(human.HumanAcceptanceError) as raised:
                human.validate_bundle(
                    mutated,
                    source_bytes=json.dumps(mutated).encode(),
                    evidence_base=root,
                )
            self.assertIn("must pass on cutout_phone", str(raised.exception))

    def test_review_must_reference_real_device_session_and_matching_class(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            mutated = copy.deepcopy(compiled)
            mutated["reviews"][0]["device_acceptance_session_id"] = "session-does-not-exist"
            with self.assertRaises(human.HumanAcceptanceError) as raised:
                human.validate_bundle(mutated, source_bytes=json.dumps(mutated).encode(), evidence_base=root)
            self.assertIn("unknown device acceptance session", str(raised.exception))

            mutated = copy.deepcopy(compiled)
            mutated["reviews"][0]["device_class"] = "tablet"
            with self.assertRaises(human.HumanAcceptanceError) as raised:
                human.validate_bundle(mutated, source_bytes=json.dumps(mutated).encode(), evidence_base=root)
            self.assertIn("does not match referenced session", str(raised.exception))

    def test_device_manifest_and_human_evidence_tampering_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            (root / "human" / "older_phone.txt").write_bytes(b"tampered\n")
            with self.assertRaises(human.HumanAcceptanceError) as raised:
                human.validate_bundle(compiled, source_bytes=json.dumps(compiled).encode(), evidence_base=root)
            self.assertIn("human evidence digest mismatch", str(raised.exception))

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            (root / "device-acceptance.json").write_text("{}\n", encoding="utf-8")
            with self.assertRaises(human.HumanAcceptanceError) as raised:
                human.validate_bundle(compiled, source_bytes=json.dumps(compiled).encode(), evidence_base=root)
            self.assertIn("device acceptance manifest digest mismatch", str(raised.exception))

    def test_review_evidence_cannot_be_reused_by_path_or_hardlink(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            mutated = copy.deepcopy(compiled)
            mutated["reviews"][1]["evidence_files"][0] = copy.deepcopy(
                mutated["reviews"][0]["evidence_files"][0]
            )
            with self.assertRaises(human.HumanAcceptanceError) as raised:
                human.validate_bundle(mutated, source_bytes=json.dumps(mutated).encode(), evidence_base=root)
            self.assertIn("evidence path is reused", str(raised.exception))

    def test_final_review_requires_two_distinct_review_authors(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            for reviewers, fragment in (
                (["release-owner"], "at least two"),
                (["release-owner", "Release-Owner"], "must be distinct"),
                (["release-owner", "non-author"], "must have authored"),
            ):
                mutated = copy.deepcopy(compiled)
                mutated["final_review"]["reviewers"] = reviewers
                with self.assertRaises(human.HumanAcceptanceError) as raised:
                    human.validate_bundle(
                        mutated,
                        source_bytes=json.dumps(mutated).encode(),
                        evidence_base=root,
                    )
                self.assertIn(fragment, str(raised.exception))

    def test_compile_file_publishes_manifest_and_summary_without_overwriting_draft(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft = _human_draft(root)
            draft_path = root / "human-acceptance-draft.json"
            final_path = root / "human-acceptance.json"
            summary_path = root / "human-acceptance-summary.json"
            draft_path.write_text(json.dumps(draft, indent=2, sort_keys=True) + "\n", encoding="utf-8")
            summary = compiler.compile_file(
                draft_path,
                final_path,
                summary_path=summary_path,
            )
            self.assertEqual(device_fixture.SHA, summary.candidate_sha)
            self.assertTrue(final_path.is_file())
            self.assertTrue(summary_path.is_file())
            self.assertEqual(draft["device_acceptance"], json.loads(draft_path.read_text())["device_acceptance"])
            revalidated = human.load_and_validate(final_path)
            self.assertEqual(summary.candidate_sha, revalidated.candidate_sha)


    def test_human_manifest_loader_rejects_symlink_fifo_and_oversize(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            oversized = root / "large.json"
            with oversized.open("wb") as stream:
                stream.truncate(human.MAX_MANIFEST_BYTES + 1)
            with self.assertRaisesRegex(human.HumanAcceptanceError, "invalid size"):
                human.load_and_validate(oversized)
            target = root / "target.json"
            target.write_text("{}", encoding="utf-8")
            alias = root / "alias.json"
            alias.symlink_to(target)
            with self.assertRaisesRegex(human.HumanAcceptanceError, "non-symlink"):
                human.load_and_validate(alias)
            fifo = root / "manifest.fifo"
            os.mkfifo(fifo)
            with self.assertRaisesRegex(human.HumanAcceptanceError, "non-symlink"):
                human.load_and_validate(fifo)

    def test_bound_device_manifest_is_validated_from_the_hashed_bytes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            with patch.object(Path, "read_bytes", side_effect=AssertionError("manifest reopened")):
                summary = human.validate_bundle(
                    compiled,
                    source_bytes=json.dumps(compiled, sort_keys=True).encode(),
                    evidence_base=root,
                )
            self.assertEqual(device_fixture.SHA, summary.candidate_sha)

    def test_hash_fields_reject_uppercase_hex_encodings(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            compiled["candidate"]["artifact_sha256"] = compiled["candidate"]["artifact_sha256"].upper()
            with self.assertRaisesRegex(human.HumanAcceptanceError, "lowercase 64-hex"):
                human.validate_bundle(
                    compiled,
                    source_bytes=json.dumps(compiled).encode(),
                    evidence_base=root,
                )

    def test_hash_fields_reject_surrounding_whitespace(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            original, _ = self.compile_valid(root)
            mutations = (
                ("candidate.commit_sha", lambda data: data["candidate"].__setitem__(
                    "commit_sha", data["candidate"]["commit_sha"] + " "
                )),
                ("candidate.artifact_sha256", lambda data: data["candidate"].__setitem__(
                    "artifact_sha256", data["candidate"]["artifact_sha256"] + " "
                )),
                ("candidate.upload_certificate_sha256", lambda data: data["candidate"].__setitem__(
                    "upload_certificate_sha256", data["candidate"]["upload_certificate_sha256"] + " "
                )),
                ("candidate.app_signing_certificate_sha256", lambda data: data["candidate"].__setitem__(
                    "app_signing_certificate_sha256", data["candidate"]["app_signing_certificate_sha256"] + " "
                )),
                ("device_acceptance.sha256", lambda data: data["device_acceptance"].__setitem__(
                    "sha256", data["device_acceptance"]["sha256"] + " "
                )),
                ("reviews.evidence_files.sha256", lambda data: data["reviews"][0]["evidence_files"][0].__setitem__(
                    "sha256", data["reviews"][0]["evidence_files"][0]["sha256"] + " "
                )),
            )
            for label, mutate in mutations:
                with self.subTest(field=label):
                    data = copy.deepcopy(original)
                    mutate(data)
                    with self.assertRaisesRegex(human.HumanAcceptanceError, "lowercase .*hex"):
                        human.validate_bundle(
                            data,
                            source_bytes=json.dumps(data, sort_keys=True).encode(),
                            evidence_base=root,
                        )

    def test_symlink_component_cannot_alias_human_evidence(self) -> None:
        if not hasattr(Path, "symlink_to"):
            self.skipTest("symbolic links are unavailable")
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            alias = root / "human-alias"
            try:
                alias.symlink_to(root / "human", target_is_directory=True)
            except OSError as exc:
                self.skipTest(f"symbolic links unavailable: {exc}")
            review = next(
                item for item in compiled["reviews"]
                if item["device_class"] == "older_phone"
            )
            review["evidence_files"][0]["path"] = "human-alias/older_phone.txt"
            with self.assertRaises(human.HumanAcceptanceError) as raised:
                human.validate_bundle(
                    compiled,
                    source_bytes=json.dumps(compiled).encode(),
                    evidence_base=root,
                )
            self.assertIn("must not traverse a symbolic link", str(raised.exception))

    def test_symlinked_evidence_root_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            alias = root.parent / f"{root.name}-alias"
            try:
                alias.symlink_to(root, target_is_directory=True)
            except OSError as exc:
                self.skipTest(f"symbolic links unavailable: {exc}")
            try:
                with self.assertRaisesRegex(
                    human.HumanAcceptanceError,
                    "evidence root.*symbolic link",
                ):
                    human.validate_bundle(
                        compiled,
                        source_bytes=json.dumps(compiled).encode(),
                        evidence_base=alias,
                    )
            finally:
                alias.unlink(missing_ok=True)


if __name__ == "__main__":
    unittest.main()
