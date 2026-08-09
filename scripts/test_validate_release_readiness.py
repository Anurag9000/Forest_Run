from __future__ import annotations

import copy
import json
import tempfile
import unittest
from pathlib import Path

import build_release_evidence_index as index_builder
import compile_release_governance
import test_validate_device_acceptance as device_fixture
import test_validate_release_governance as governance_fixture
import validate_release_readiness as readiness


GENERIC_BOUND_KINDS = (
    "artifact_verification",
    "declared_dependencies",
    "sbom",
    "device_aggregate",
    "screenshot_manifest",
    "graphics_manifest",
)


def prepare_complete_evidence(root: Path, *, signed_bundle_path: str = "artifact/app-release.aab") -> dict[str, Path]:
    governance_draft = governance_fixture._governance_draft(root)
    governance_draft_path = root / "release-governance-draft.json"
    governance_path = root / "release-governance.json"
    governance_draft_path.write_text(
        json.dumps(governance_draft, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    compile_release_governance.compile_file(governance_draft_path, governance_path)

    generic_paths: dict[str, str] = {}
    for kind in GENERIC_BOUND_KINDS:
        relative = f"final-index/{kind}.json"
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(
            json.dumps(
                {
                    "candidateSha": device_fixture.SHA,
                    "kind": kind,
                },
                sort_keys=True,
            )
            + "\n",
            encoding="utf-8",
        )
        generic_paths[kind] = relative

    specs = [
        f"signed_bundle={signed_bundle_path}",
        "device_acceptance=device-acceptance.json",
        "human_acceptance=human-acceptance.json",
        "installed_identity_matrix=installed-identity-matrix.json",
        "release_governance=release-governance.json",
    ] + [f"{kind}={generic_paths[kind]}" for kind in GENERIC_BOUND_KINDS]
    index_path = root / "release-evidence-index.json"
    payload = index_builder.build_index(
        root,
        device_fixture.SHA,
        specs,
        generated_at_utc="2026-08-01T16:00:00Z",
        require_bound_kinds=readiness.REQUIRED_BOUND_KINDS,
        output=index_path,
    )
    index_builder.publish_index(index_path, payload, root=root)
    return {
        "device": root / "device-acceptance.json",
        "human": root / "human-acceptance.json",
        "matrix": root / "installed-identity-matrix.json",
        "governance": governance_path,
        "index": index_path,
    }


class ReleaseReadinessTest(unittest.TestCase):
    def validate(self, root: Path, paths: dict[str, Path]):
        return readiness.validate_readiness(
            root=root,
            expected_candidate_sha=device_fixture.SHA,
            device_manifest=paths["device"],
            human_manifest=paths["human"],
            installed_identity_matrix=paths["matrix"],
            governance_manifest=paths["governance"],
            release_index=paths["index"],
        )

    def test_complete_cross_bound_evidence_is_ready(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = prepare_complete_evidence(root)
            summary = self.validate(root, paths)
            self.assertEqual(device_fixture.SHA, summary.candidate_sha)
            self.assertEqual(device_fixture.ARTIFACT_SHA, summary.artifact_sha256)
            self.assertEqual(device_fixture.UPLOAD_CERT_SHA, summary.upload_certificate_sha256)
            self.assertEqual(device_fixture.APP_SIGNING_CERT_SHA, summary.app_signing_certificate_sha256)
            self.assertRegex(summary.installed_identity_matrix_sha256, r"^[0-9a-f]{64}$")
            self.assertGreaterEqual(summary.evidence_entry_count, 11)
            self.assertRegex(summary.evidence_index_sha256, r"^[0-9a-f]{64}$")
            self.assertRegex(summary.evidence_set_sha256, r"^[0-9a-f]{64}$")

    def test_expected_candidate_must_match_all_validated_layers(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = prepare_complete_evidence(root)
            with self.assertRaises(readiness.ReleaseReadinessError) as raised:
                readiness.validate_readiness(
                    root=root,
                    expected_candidate_sha="f" * 40,
                    device_manifest=paths["device"],
                    human_manifest=paths["human"],
                    installed_identity_matrix=paths["matrix"],
                    governance_manifest=paths["governance"],
                    release_index=paths["index"],
                )
            self.assertIn("must all match", str(raised.exception))

    def test_index_must_contain_every_required_candidate_bound_kind(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = prepare_complete_evidence(root)
            payload = json.loads(paths["index"].read_text(encoding="utf-8"))
            payload["entries"] = [
                entry for entry in payload["entries"] if entry["kind"] != "graphics_manifest"
            ]
            payload["entryCount"] = len(payload["entries"])
            # Deliberately leave the old evidenceSetSha256. The independent index
            # verifier must reject this before readiness can claim completion.
            paths["index"].write_text(json.dumps(payload, sort_keys=True) + "\n", encoding="utf-8")
            with self.assertRaises(readiness.ReleaseReadinessError) as raised:
                self.validate(root, paths)
            self.assertIn("release evidence index failed", str(raised.exception))

    def test_indexed_signed_bundle_must_be_the_accepted_artifact(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            # Governance/device acceptance still point to the real candidate AAB.
            governance_fixture._prepare_human(root)
            other = root / "artifact" / "other-release.aab"
            other.parent.mkdir(parents=True, exist_ok=True)
            other.write_bytes(b"different-signed-artifact\n")
            paths = prepare_complete_evidence(root, signed_bundle_path="artifact/other-release.aab")
            with self.assertRaises(readiness.ReleaseReadinessError) as raised:
                self.validate(root, paths)
            self.assertIn("signed_bundle digest does not match", str(raised.exception))

    def test_indexed_manifest_path_must_be_the_manifest_that_was_revalidated(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = prepare_complete_evidence(root)
            copied = root / "final-index" / "copied-device.json"
            copied.write_bytes(paths["device"].read_bytes())

            generic = {
                kind: f"final-index/{kind}.json"
                for kind in GENERIC_BOUND_KINDS
            }
            specs = [
                "signed_bundle=artifact/app-release.aab",
                "device_acceptance=final-index/copied-device.json",
                "human_acceptance=human-acceptance.json",
                "installed_identity_matrix=installed-identity-matrix.json",
                "release_governance=release-governance.json",
            ] + [f"{kind}={generic[kind]}" for kind in GENERIC_BOUND_KINDS]
            payload = index_builder.build_index(
                root,
                device_fixture.SHA,
                specs,
                generated_at_utc="2026-08-01T16:05:00Z",
                require_bound_kinds=readiness.REQUIRED_BOUND_KINDS,
                output=paths["index"],
            )
            index_builder.publish_index(paths["index"], payload, root=root)
            with self.assertRaises(readiness.ReleaseReadinessError) as raised:
                self.validate(root, paths)
            self.assertIn("path does not match the validated input", str(raised.exception))

    def test_tampered_governance_cannot_be_masked_by_a_still_valid_index(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = prepare_complete_evidence(root)
            governance = json.loads(paths["governance"].read_text(encoding="utf-8"))
            governance["final_decision"]["status"] = "pending"
            paths["governance"].write_text(
                json.dumps(governance, indent=2, sort_keys=True) + "\n",
                encoding="utf-8",
            )
            with self.assertRaises(readiness.ReleaseReadinessError) as raised:
                self.validate(root, paths)
            self.assertIn("release governance failed", str(raised.exception))

    def test_symlinked_manifest_component_is_rejected_before_delegation(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = prepare_complete_evidence(root)
            alias = root / "manifest-alias"
            real = root / "manifest-real"
            real.mkdir()
            copied = real / "device-acceptance.json"
            copied.write_bytes(paths["device"].read_bytes())
            try:
                alias.symlink_to(real, target_is_directory=True)
            except OSError as exc:
                self.skipTest(f"symbolic links unavailable: {exc}")
            with self.assertRaises(readiness.ReleaseReadinessError) as raised:
                readiness.validate_readiness(
                    root=root,
                    expected_candidate_sha=device_fixture.SHA,
                    device_manifest=alias / "device-acceptance.json",
                    human_manifest=paths["human"],
                    installed_identity_matrix=paths["matrix"],
                    governance_manifest=paths["governance"],
                    release_index=paths["index"],
                )
            self.assertIn("must not traverse a symbolic link", str(raised.exception))


if __name__ == "__main__":
    unittest.main()
