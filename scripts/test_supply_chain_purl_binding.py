from __future__ import annotations

import hashlib
import json
import unittest

import build_resolved_dependency_sbom as builder
import validate_supply_chain_evidence as validator


CANDIDATE = "a" * 40


def canonical_bytes(value: object) -> bytes:
    return (
        json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
        + "\n"
    ).encode("utf-8")


def resolved_payload(
    *,
    group: str = "example.group",
    name: str = "sample-lib",
    version: str = "1.0+meta",
) -> dict[str, object]:
    purl = builder.purl(group, name, version)
    components: list[dict[str, str]] = [
        {
            "type": "library",
            "bom-ref": purl,
            "group": group,
            "name": name,
            "version": version,
            "purl": purl,
        }
    ]
    digest = hashlib.sha256(canonical_bytes(components)).hexdigest()
    reports = [
        {
            "configuration": "releaseRuntimeClasspath",
            "path": "build/supply-chain/releaseRuntimeClasspath.txt",
            "sha256": "b" * 64,
        },
        {
            "configuration": "debugAndroidTestRuntimeClasspath",
            "path": "build/supply-chain/debugAndroidTestRuntimeClasspath.txt",
            "sha256": "c" * 64,
        },
    ]
    return {
        "bomFormat": "CycloneDX",
        "specVersion": "1.6",
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
                {"name": "forest-run:component-set-sha256", "value": digest},
                {"name": "forest-run:scope", "value": "resolved-gradle-components"},
            ],
        },
        "components": components,
        "properties": [
            {
                "name": "forest-run:source-reports",
                "value": json.dumps(reports, sort_keys=True, separators=(",", ":")),
            }
        ],
    }


def rewrite_digest(payload: dict[str, object]) -> None:
    components = payload["components"]
    assert isinstance(components, list)
    digest = hashlib.sha256(
        canonical_bytes(sorted(components, key=lambda item: item["purl"]))
    ).hexdigest()
    metadata = payload["metadata"]
    assert isinstance(metadata, dict)
    properties = metadata["properties"]
    assert isinstance(properties, list)
    for item in properties:
        assert isinstance(item, dict)
        if item.get("name") == "forest-run:component-set-sha256":
            item["value"] = digest
            return
    raise AssertionError("component-set digest property missing")


class SupplyChainPurlBindingTest(unittest.TestCase):
    def test_validator_canonicalizer_matches_sbom_builder_encoding(self) -> None:
        cases = (
            ("androidx.core", "core-ktx", "1.13.1"),
            ("example.group", "sample-lib", "1.0+meta"),
            ("example.group", "sample_lib", "2.0-rc.1+build"),
        )
        for group, name, version in cases:
            with self.subTest(group=group, name=name, version=version):
                self.assertEqual(
                    builder.purl(group, name, version),
                    validator._canonical_maven_purl(group, name, version),
                )

    def test_rehashed_noncanonical_purl_is_rejected(self) -> None:
        payload = resolved_payload()
        components = payload["components"]
        assert isinstance(components, list)
        component = components[0]
        assert isinstance(component, dict)
        # Deliberately leave '+' unescaped. The component digest is recomputed so
        # this proves the identity binding rather than merely the outer digest.
        component["purl"] = "pkg:maven/example.group/sample-lib@1.0+meta"
        component["bom-ref"] = component["purl"]
        rewrite_digest(payload)

        with self.assertRaisesRegex(
            validator.SupplyChainEvidenceError,
            "canonical encoding",
        ):
            validator.validate_resolved(
                payload,
                CANDIDATE,
                {("example.group", "sample-lib")},
            )

    def test_purl_cannot_name_a_different_module_than_component_fields(self) -> None:
        payload = resolved_payload()
        components = payload["components"]
        assert isinstance(components, list)
        component = components[0]
        assert isinstance(component, dict)
        component["purl"] = builder.purl("other.group", "other-lib", "1.0+meta")
        component["bom-ref"] = component["purl"]
        rewrite_digest(payload)

        with self.assertRaisesRegex(
            validator.SupplyChainEvidenceError,
            "canonical encoding",
        ):
            validator.validate_resolved(
                payload,
                CANDIDATE,
                {("example.group", "sample-lib")},
            )


if __name__ == "__main__":
    unittest.main()
