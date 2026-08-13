from __future__ import annotations

import hashlib
import json
import os
import tempfile
import unittest
from pathlib import Path

import validate_supply_chain_evidence as validator


CANDIDATE = "a" * 40
SHA = "b" * 64


def canonical_bytes(value: object) -> bytes:
    return (
        json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
        + "\n"
    ).encode("utf-8")


def declared_payload() -> dict[str, object]:
    entries: list[dict[str, str]] = [
        {
            "ecosystem": "maven",
            "configuration": "implementation",
            "name": "androidx.core:core-ktx",
            "version": "1.13.1",
        },
        {
            "ecosystem": "maven",
            "configuration": "androidTestImplementation",
            "name": "androidx.test.espresso:espresso-core",
            "version": "3.6.1",
        },
        {
            "ecosystem": "maven",
            "configuration": "testImplementation",
            "name": "junit:junit",
            "version": "4.13.2",
        },
    ]
    return {
        "schemaVersion": 1,
        "candidateSha": CANDIDATE,
        "scope": "declared-direct-dependencies-only",
        "entryCount": len(entries),
        "inventorySha256": hashlib.sha256(canonical_bytes(entries)).hexdigest(),
        "sourceFiles": [
            {"path": "app/build.gradle.kts", "sha256": SHA},
            {"path": "build.gradle.kts", "sha256": "c" * 64},
        ],
        "entries": entries,
    }


def resolved_payload() -> dict[str, object]:
    components: list[dict[str, str]] = [
        {
            "type": "library",
            "bom-ref": "pkg:maven/androidx.core/core-ktx@1.13.1",
            "group": "androidx.core",
            "name": "core-ktx",
            "version": "1.13.1",
            "purl": "pkg:maven/androidx.core/core-ktx@1.13.1",
        },
        {
            "type": "library",
            "bom-ref": "pkg:maven/androidx.test.espresso/espresso-core@3.6.1",
            "group": "androidx.test.espresso",
            "name": "espresso-core",
            "version": "3.6.1",
            "purl": "pkg:maven/androidx.test.espresso/espresso-core@3.6.1",
        },
    ]
    component_digest = hashlib.sha256(
        canonical_bytes(sorted(components, key=lambda item: item["purl"]))
    ).hexdigest()
    source_reports = [
        {
            "configuration": "releaseRuntimeClasspath",
            "path": "build/supply-chain/releaseRuntimeClasspath.txt",
            "sha256": "d" * 64,
        },
        {
            "configuration": "debugAndroidTestRuntimeClasspath",
            "path": "build/supply-chain/debugAndroidTestRuntimeClasspath.txt",
            "sha256": "e" * 64,
        },
    ]
    return {
        "bomFormat": "CycloneDX",
        "specVersion": "1.6",
        "serialNumber": "urn:uuid:00000000-0000-0000-0000-000000000000",
        "version": 1,
        "metadata": {
            "component": {
                "type": "application",
                "name": "Forest Run",
                "version": CANDIDATE,
            },
            "properties": [
                {"name": "forest-run:candidate-sha", "value": CANDIDATE},
                {
                    "name": "forest-run:resolved-configurations",
                    "value": "releaseRuntimeClasspath,debugAndroidTestRuntimeClasspath",
                },
                {"name": "forest-run:component-set-sha256", "value": component_digest},
                {"name": "forest-run:scope", "value": "resolved-gradle-components"},
            ],
        },
        "components": components,
        "properties": [
            {
                "name": "forest-run:source-reports",
                "value": json.dumps(source_reports, sort_keys=True, separators=(",", ":")),
            },
            {"name": "forest-run:limitations", "value": "diagnostic limitations"},
        ],
    }


class SupplyChainEvidenceValidatorTest(unittest.TestCase):
    def write_pair(
        self,
        root: Path,
        declared: dict[str, object] | None = None,
        resolved: dict[str, object] | None = None,
    ) -> tuple[Path, Path]:
        declared_path = root / "declared.json"
        resolved_path = root / "resolved.json"
        declared_path.write_text(json.dumps(declared or declared_payload()), encoding="utf-8")
        resolved_path.write_text(json.dumps(resolved or resolved_payload()), encoding="utf-8")
        return declared_path, resolved_path

    def test_valid_pair_is_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            declared_path, resolved_path = self.write_pair(Path(temporary))
            validator.validate(declared_path, resolved_path, CANDIDATE)

    def test_candidate_binding_must_match_in_both_layers(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            declared = declared_payload()
            declared["candidateSha"] = "f" * 40
            declared_path, resolved_path = self.write_pair(Path(temporary), declared=declared)
            with self.assertRaisesRegex(validator.SupplyChainEvidenceError, "candidateSha"):
                validator.validate(declared_path, resolved_path, CANDIDATE)

            declared = declared_payload()
            resolved = resolved_payload()
            metadata = resolved["metadata"]
            assert isinstance(metadata, dict)
            component = metadata["component"]
            assert isinstance(component, dict)
            component["version"] = "f" * 40
            declared_path, resolved_path = self.write_pair(
                Path(temporary), declared=declared, resolved=resolved
            )
            with self.assertRaisesRegex(validator.SupplyChainEvidenceError, "expected candidate"):
                validator.validate(declared_path, resolved_path, CANDIDATE)

    def test_declared_inventory_digest_is_recomputed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            declared = declared_payload()
            declared["inventorySha256"] = "0" * 64
            declared_path, resolved_path = self.write_pair(Path(temporary), declared=declared)
            with self.assertRaisesRegex(validator.SupplyChainEvidenceError, "does not match entries"):
                validator.validate(declared_path, resolved_path, CANDIDATE)

    def test_resolved_component_digest_is_recomputed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            resolved = resolved_payload()
            metadata = resolved["metadata"]
            assert isinstance(metadata, dict)
            properties = metadata["properties"]
            assert isinstance(properties, list)
            for item in properties:
                assert isinstance(item, dict)
                if item.get("name") == "forest-run:component-set-sha256":
                    item["value"] = "0" * 64
            declared_path, resolved_path = self.write_pair(Path(temporary), resolved=resolved)
            with self.assertRaisesRegex(validator.SupplyChainEvidenceError, "digest does not match"):
                validator.validate(declared_path, resolved_path, CANDIDATE)

    def test_runtime_direct_module_must_appear_in_resolved_graph(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            resolved = resolved_payload()
            components = resolved["components"]
            assert isinstance(components, list)
            components.pop()
            metadata = resolved["metadata"]
            assert isinstance(metadata, dict)
            properties = metadata["properties"]
            assert isinstance(properties, list)
            digest = hashlib.sha256(
                canonical_bytes(sorted(components, key=lambda item: item["purl"]))
            ).hexdigest()
            for item in properties:
                assert isinstance(item, dict)
                if item.get("name") == "forest-run:component-set-sha256":
                    item["value"] = digest
            declared_path, resolved_path = self.write_pair(Path(temporary), resolved=resolved)
            with self.assertRaisesRegex(validator.SupplyChainEvidenceError, "missing runtime/test direct"):
                validator.validate(declared_path, resolved_path, CANDIDATE)

    def test_exact_resolution_configuration_pair_is_required(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            resolved = resolved_payload()
            metadata = resolved["metadata"]
            assert isinstance(metadata, dict)
            properties = metadata["properties"]
            assert isinstance(properties, list)
            for item in properties:
                assert isinstance(item, dict)
                if item.get("name") == "forest-run:resolved-configurations":
                    item["value"] = "releaseRuntimeClasspath"
            declared_path, resolved_path = self.write_pair(Path(temporary), resolved=resolved)
            with self.assertRaisesRegex(validator.SupplyChainEvidenceError, "resolved configurations"):
                validator.validate(declared_path, resolved_path, CANDIDATE)

    def test_duplicate_json_keys_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            declared_path, resolved_path = self.write_pair(root)
            declared_path.write_text(
                '{"schemaVersion":1,"schemaVersion":1}',
                encoding="utf-8",
            )
            with self.assertRaisesRegex(validator.SupplyChainEvidenceError, "duplicate JSON"):
                validator.validate(declared_path, resolved_path, CANDIDATE)

    def test_direct_symlink_evidence_is_rejected(self) -> None:
        if not hasattr(os, "symlink"):
            self.skipTest("symlinks are unavailable")
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            declared_path, resolved_path = self.write_pair(root)
            target = root / "declared-target.json"
            declared_path.replace(target)
            try:
                declared_path.symlink_to(target.name)
            except OSError as exc:
                self.skipTest(f"symlink creation unavailable: {exc}")
            with self.assertRaisesRegex(validator.SupplyChainEvidenceError, "non-symlink"):
                validator.validate(declared_path, resolved_path, CANDIDATE)

    def test_expected_commit_must_be_exact_lowercase_sha(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            declared_path, resolved_path = self.write_pair(Path(temporary))
            for value in ("A" * 40, "a" * 39, "main"):
                with self.subTest(value=value):
                    with self.assertRaisesRegex(validator.SupplyChainEvidenceError, "40 lowercase"):
                        validator.validate(declared_path, resolved_path, value)


if __name__ == "__main__":
    unittest.main()
