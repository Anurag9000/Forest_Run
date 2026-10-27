from __future__ import annotations

import hashlib
import json
import os
import tempfile
import unittest
from pathlib import Path

import validate_supply_chain_evidence as validator


CANDIDATE = "a" * 40
SOURCE_FILE_CONTENTS = {
    "app/build.gradle.kts": b"app build source fixture\n",
    "build.gradle.kts": b"root build source fixture\n",
    "gradle/wrapper/gradle-wrapper.properties": b"distributionUrl=fixture\n",
    "scripts/requirements-ci.txt": b"Pillow==11.3.0\n",
}
REPORT_FILE_CONTENTS = {
    "build/supply-chain/releaseRuntimeClasspath.txt": b"release dependency report fixture\n",
    "build/supply-chain/debugAndroidTestRuntimeClasspath.txt": b"android test dependency report fixture\n",
}


def sha256(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


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
            {"path": "app/build.gradle.kts", "sha256": sha256(SOURCE_FILE_CONTENTS["app/build.gradle.kts"])},
            {"path": "build.gradle.kts", "sha256": sha256(SOURCE_FILE_CONTENTS["build.gradle.kts"])},
            {
                "path": "gradle/wrapper/gradle-wrapper.properties",
                "sha256": sha256(SOURCE_FILE_CONTENTS["gradle/wrapper/gradle-wrapper.properties"]),
            },
            {
                "path": "scripts/requirements-ci.txt",
                "sha256": sha256(SOURCE_FILE_CONTENTS["scripts/requirements-ci.txt"]),
            },
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
            "sha256": sha256(REPORT_FILE_CONTENTS["build/supply-chain/releaseRuntimeClasspath.txt"]),
        },
        {
            "configuration": "debugAndroidTestRuntimeClasspath",
            "path": "build/supply-chain/debugAndroidTestRuntimeClasspath.txt",
            "sha256": sha256(REPORT_FILE_CONTENTS["build/supply-chain/debugAndroidTestRuntimeClasspath.txt"]),
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
        for relative, content in {**SOURCE_FILE_CONTENTS, **REPORT_FILE_CONTENTS}.items():
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
        declared_path.write_text(json.dumps(declared or declared_payload()), encoding="utf-8")
        resolved_path.write_text(json.dumps(resolved or resolved_payload()), encoding="utf-8")
        return declared_path, resolved_path

    def validate_pair(
        self, declared_path: Path, resolved_path: Path, expected_sha: object = CANDIDATE
    ) -> None:
        validator.validate(
            declared_path,
            resolved_path,
            expected_sha,
            repository_root=declared_path.parent,
        )

    def test_declared_schema_version_must_be_integer(self) -> None:
        for invalid in (True, False, 1.0, "1", None):
            with self.subTest(value=invalid):
                payload = declared_payload()
                payload["schemaVersion"] = invalid
                with self.assertRaisesRegex(
                    validator.SupplyChainEvidenceError, "schemaVersion"
                ):
                    validator.validate_declared(payload, CANDIDATE)

    def test_declared_entry_count_must_be_integer_not_boolean(self) -> None:
        payload = declared_payload()
        payload["entryCount"] = True
        with self.assertRaisesRegex(
            validator.SupplyChainEvidenceError, "entryCount does not match"
        ):
            validator.validate_declared(payload, CANDIDATE)

    def test_resolved_bom_version_must_be_integer_not_boolean(self) -> None:
        payload = resolved_payload()
        payload["version"] = True
        with self.assertRaisesRegex(
            validator.SupplyChainEvidenceError, "CycloneDX version"
        ):
            validator.validate_resolved(payload, CANDIDATE, set())

    def test_valid_pair_is_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            declared_path, resolved_path = self.write_pair(Path(temporary))
            self.validate_pair(declared_path, resolved_path, CANDIDATE)

    def test_candidate_binding_must_match_in_both_layers(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            declared = declared_payload()
            declared["candidateSha"] = "f" * 40
            declared_path, resolved_path = self.write_pair(Path(temporary), declared=declared)
            with self.assertRaisesRegex(validator.SupplyChainEvidenceError, "candidateSha"):
                self.validate_pair(declared_path, resolved_path, CANDIDATE)

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
                self.validate_pair(declared_path, resolved_path, CANDIDATE)

    def test_declared_inventory_digest_is_recomputed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            declared = declared_payload()
            declared["inventorySha256"] = "0" * 64
            declared_path, resolved_path = self.write_pair(Path(temporary), declared=declared)
            with self.assertRaisesRegex(validator.SupplyChainEvidenceError, "does not match entries"):
                self.validate_pair(declared_path, resolved_path, CANDIDATE)

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
                self.validate_pair(declared_path, resolved_path, CANDIDATE)

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
                self.validate_pair(declared_path, resolved_path, CANDIDATE)

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
                self.validate_pair(declared_path, resolved_path, CANDIDATE)

    def test_duplicate_json_keys_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            declared_path, resolved_path = self.write_pair(root)
            declared_path.write_text(
                '{"schemaVersion":1,"schemaVersion":1}',
                encoding="utf-8",
            )
            with self.assertRaisesRegex(validator.SupplyChainEvidenceError, "duplicate JSON"):
                self.validate_pair(declared_path, resolved_path, CANDIDATE)

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
                self.validate_pair(declared_path, resolved_path, CANDIDATE)

    def test_expected_commit_must_be_exact_lowercase_sha(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            declared_path, resolved_path = self.write_pair(Path(temporary))
            for value in ("A" * 40, "a" * 39, "main"):
                with self.subTest(value=value):
                    with self.assertRaisesRegex(validator.SupplyChainEvidenceError, "40 lowercase"):
                        self.validate_pair(declared_path, resolved_path, value)

    def test_declared_source_paths_must_use_canonical_posix_relative_form(self) -> None:
        for path in (
            r"C:\\repo\\app\\build.gradle.kts",
            "C:/repo/app/build.gradle.kts",
            "app//build.gradle.kts",
            "app/./build.gradle.kts",
            "app/../build.gradle.kts",
            "app/line\x01break.gradle.kts",
            "/repo/app/build.gradle.kts",
        ):
            with self.subTest(path=path):
                payload = declared_payload()
                source_files = payload["sourceFiles"]
                assert isinstance(source_files, list)
                source = source_files[0]
                assert isinstance(source, dict)
                source["path"] = path
                with self.assertRaisesRegex(
                    validator.SupplyChainEvidenceError,
                    "canonical repository-relative POSIX path",
                ):
                    validator.validate_declared(payload, CANDIDATE)

    def test_resolved_source_report_paths_must_use_canonical_posix_relative_form(self) -> None:
        resolved = resolved_payload()
        properties = resolved["properties"]
        assert isinstance(properties, list)
        source_reports = None
        source_property = None
        for item in properties:
            assert isinstance(item, dict)
            if item.get("name") == "forest-run:source-reports":
                source_property = item
                source_reports = json.loads(item["value"])
                break
        assert source_property is not None and isinstance(source_reports, list)
        source_reports[0]["path"] = r"C:\\repo\\build\\releaseRuntimeClasspath.txt"
        source_property["value"] = json.dumps(
            source_reports, sort_keys=True, separators=(",", ":")
        )
        runtime_direct = {
            ("androidx.core", "core-ktx"),
            ("androidx.test.espresso", "espresso-core"),
        }
        with self.assertRaisesRegex(
            validator.SupplyChainEvidenceError,
            "canonical repository-relative POSIX path",
        ):
            validator.validate_resolved(resolved, CANDIDATE, runtime_direct)

    def test_escaped_unpaired_surrogates_fail_with_validator_errors(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            declared = declared_payload()
            source_files = declared["sourceFiles"]
            assert isinstance(source_files, list)
            source = source_files[0]
            assert isinstance(source, dict)
            source["path"] = "source-\ud800.gradle.kts"
            declared_path, resolved_path = self.write_pair(
                Path(temporary), declared=declared
            )
            with self.assertRaisesRegex(
                validator.SupplyChainEvidenceError,
                "invalid Unicode",
            ):
                self.validate_pair(declared_path, resolved_path, CANDIDATE)

            declared = declared_payload()
            entries = declared["entries"]
            assert isinstance(entries, list)
            entry = entries[0]
            assert isinstance(entry, dict)
            entry["unexpected"] = "\ud800"
            declared_path, resolved_path = self.write_pair(
                Path(temporary), declared=declared
            )
            with self.assertRaisesRegex(
                validator.SupplyChainEvidenceError,
                "not valid Unicode",
            ):
                self.validate_pair(declared_path, resolved_path, CANDIDATE)

    def test_non_string_expected_commit_is_rejected_without_regex_type_error(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            declared_path, resolved_path = self.write_pair(Path(temporary))
            for value in (None, 7, b"a" * 40):
                with self.subTest(value=value):
                    with self.assertRaisesRegex(
                        validator.SupplyChainEvidenceError,
                        "expected commit must be exactly 40",
                    ):
                        self.validate_pair(declared_path, resolved_path, value)

    def test_source_and_report_file_digests_are_recomputed_from_disk(self) -> None:
        for relative in (
            "app/build.gradle.kts",
            "build/supply-chain/releaseRuntimeClasspath.txt",
        ):
            with self.subTest(relative=relative), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                declared_path, resolved_path = self.write_pair(root)
                (root / relative).write_bytes(b"tampered source evidence\n")
                with self.assertRaisesRegex(
                    validator.SupplyChainEvidenceError,
                    "digest does not match referenced file",
                ):
                    self.validate_pair(declared_path, resolved_path)

    def test_repository_root_symlink_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            parent = Path(directory)
            root = parent / "checkout"
            root.mkdir()
            declared_path, resolved_path = self.write_pair(root)
            alias = parent / "checkout-alias"
            alias.symlink_to(root, target_is_directory=True)
            try:
                with self.assertRaisesRegex(validator.SupplyChainEvidenceError, "symbolic link"):
                    validator.validate(
                        declared_path,
                        resolved_path,
                        CANDIDATE,
                        repository_root=alias,
                    )
            finally:
                alias.unlink()

    def test_declared_inventory_must_cover_the_exact_dependency_source_set(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            declared = declared_payload()
            source_files = declared["sourceFiles"]
            assert isinstance(source_files, list)
            source_files.pop()
            declared_path, resolved_path = self.write_pair(root, declared=declared)
            with self.assertRaisesRegex(
                validator.SupplyChainEvidenceError,
                "do not match the required dependency inputs.*missing=",
            ):
                self.validate_pair(declared_path, resolved_path)

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            declared = declared_payload()
            source_files = declared["sourceFiles"]
            assert isinstance(source_files, list)
            source_files.pop()
            extra_content = b"not a dependency input\n"
            (root / "README.md").write_bytes(extra_content)
            source_files.append({
                "path": "README.md",
                "sha256": sha256(extra_content),
            })
            declared_path, resolved_path = self.write_pair(root, declared=declared)
            with self.assertRaisesRegex(
                validator.SupplyChainEvidenceError,
                "do not match the required dependency inputs.*extra=",
            ):
                self.validate_pair(declared_path, resolved_path)

    @unittest.skipUnless(hasattr(os, "symlink"), "symlink support required")
    def test_referenced_source_symlink_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            declared_path, resolved_path = self.write_pair(root)
            source = root / "app/build.gradle.kts"
            target = root / "external-source.txt"
            target.write_bytes(source.read_bytes())
            source.unlink()
            source.symlink_to(target)
            with self.assertRaisesRegex(
                validator.SupplyChainEvidenceError,
                "symbolic link",
            ):
                self.validate_pair(declared_path, resolved_path)


if __name__ == "__main__":
    unittest.main()
