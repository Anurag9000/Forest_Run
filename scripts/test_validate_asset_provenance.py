from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from validate_asset_provenance import AssetProvenanceError, validate


FIELDS = {
    "assetKind": "test-asset",
    "status": "review-required",
    "source": "",
    "license": "",
    "attribution": "",
    "reviewer": "",
    "reviewedAt": "",
}


class AssetProvenanceValidationTest(unittest.TestCase):
    def test_complete_review_required_coverage_passes_but_strict_release_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.write_asset(root, "app/src/main/assets/sprites/tree.png")
            self.write_asset(root, "app/src/main/res/raw/theme.ogg")
            registry = self.write_registry(
                root,
                [
                    self.rule("app/src/main/assets/sprites/**"),
                    self.rule("app/src/main/res/raw/**"),
                ],
            )

            report = validate(root, registry)
            with self.assertRaisesRegex(
                AssetProvenanceError,
                "review remains incomplete for 2 files",
            ):
                validate(root, registry, require_approved=True)

        self.assertEqual(2, report["assetCount"])
        self.assertEqual(2, report["ruleCount"])
        self.assertEqual(0, report["approvedAssetCount"])
        self.assertEqual(2, report["reviewRequiredAssetCount"])

    def test_valid_reviewed_first_and_third_party_rules_pass_strict_release(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.write_asset(root, "app/src/main/assets/fonts/font.ttf")
            self.write_asset(root, "app/src/main/res/drawable/icon.xml")
            registry = self.write_registry(
                root,
                [
                    self.rule(
                        "app/src/main/assets/fonts/**",
                        status="approved-third-party",
                        source="https://example.invalid/font-source",
                        license="Example-License-1.0",
                        attribution="Example attribution",
                        reviewer="reviewer-a",
                        reviewedAt="2026-08-01",
                    ),
                    self.rule(
                        "app/src/main/res/drawable/**",
                        status="approved-first-party",
                        source="internal-design-record-1",
                        license="All-rights-owned",
                        reviewer="reviewer-b",
                        reviewedAt="2026-08-02",
                    ),
                ],
            )

            report = validate(root, registry, require_approved=True)

        self.assertEqual(2, report["approvedAssetCount"])
        self.assertEqual(0, report["reviewRequiredAssetCount"])

    def test_uncovered_overlapping_and_stale_rules_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.write_asset(root, "app/src/main/assets/sprites/tree.png")

            uncovered = self.write_registry(
                root,
                [self.rule("app/src/main/assets/fonts/**")],
                name="uncovered.json",
            )
            with self.assertRaisesRegex(AssetProvenanceError, "not covered"):
                validate(root, uncovered)

            overlapping = self.write_registry(
                root,
                [
                    self.rule("app/src/main/assets/**"),
                    self.rule("app/src/main/assets/sprites/**"),
                ],
                name="overlap.json",
            )
            with self.assertRaisesRegex(AssetProvenanceError, "overlapping"):
                validate(root, overlapping)

            stale = self.write_registry(
                root,
                [
                    self.rule("app/src/main/assets/sprites/**"),
                    self.rule("app/src/main/res/raw/**"),
                ],
                name="stale.json",
            )
            with self.assertRaisesRegex(AssetProvenanceError, "do not match"):
                validate(root, stale)

    def test_duplicate_patterns_unsafe_patterns_and_unknown_status_fail(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.write_asset(root, "app/src/main/assets/sprites/tree.png")

            duplicate = self.write_registry(
                root,
                [
                    self.rule("app/src/main/assets/sprites/**"),
                    self.rule("app/src/main/assets/sprites/**"),
                ],
                name="duplicate.json",
            )
            with self.assertRaisesRegex(AssetProvenanceError, "duplicate"):
                validate(root, duplicate)

            unsafe = self.write_registry(
                root,
                [self.rule("../outside/**")],
                name="unsafe.json",
            )
            with self.assertRaisesRegex(AssetProvenanceError, "unsafe"):
                validate(root, unsafe)

            unknown = self.write_registry(
                root,
                [
                    self.rule(
                        "app/src/main/assets/sprites/**",
                        status="probably-fine",
                    )
                ],
                name="unknown.json",
            )
            with self.assertRaisesRegex(AssetProvenanceError, "unsupported status"):
                validate(root, unknown)

    def test_review_required_rule_cannot_smuggle_unreviewed_claims(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.write_asset(root, "app/src/main/assets/sprites/tree.png")
            registry = self.write_registry(
                root,
                [
                    self.rule(
                        "app/src/main/assets/sprites/**",
                        source="someone said it was ours",
                    )
                ],
            )

            with self.assertRaisesRegex(AssetProvenanceError, "unreviewed claims"):
                validate(root, registry)

    def test_approved_rule_requires_evidence_and_non_future_iso_date(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.write_asset(root, "app/src/main/assets/sprites/tree.png")
            missing = self.write_registry(
                root,
                [
                    self.rule(
                        "app/src/main/assets/sprites/**",
                        status="approved-first-party",
                    )
                ],
                name="missing.json",
            )
            with self.assertRaisesRegex(AssetProvenanceError, "requires source"):
                validate(root, missing)

            future = self.write_registry(
                root,
                [
                    self.rule(
                        "app/src/main/assets/sprites/**",
                        status="approved-first-party",
                        source="record",
                        license="owned",
                        reviewer="reviewer",
                        reviewedAt="2999-01-01",
                    )
                ],
                name="future.json",
            )
            with self.assertRaisesRegex(AssetProvenanceError, "future"):
                validate(root, future)

            invalid_date = self.write_registry(
                root,
                [
                    self.rule(
                        "app/src/main/assets/sprites/**",
                        status="approved-first-party",
                        source="record",
                        license="owned",
                        reviewer="reviewer",
                        reviewedAt="August 1",
                    )
                ],
                name="date.json",
            )
            with self.assertRaisesRegex(AssetProvenanceError, "YYYY-MM-DD"):
                validate(root, invalid_date)

    def test_asset_and_registry_symlinks_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            asset_target = root / "target.png"
            asset_target.write_bytes(b"png")
            asset_link = root / "app/src/main/assets/sprites/tree.png"
            asset_link.parent.mkdir(parents=True)
            asset_link.symlink_to(asset_target)
            registry = self.write_registry(
                root,
                [self.rule("app/src/main/assets/sprites/**")],
            )
            with self.assertRaisesRegex(AssetProvenanceError, "symbolic links"):
                validate(root, registry)

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.write_asset(root, "app/src/main/assets/sprites/tree.png")
            target = self.write_registry(
                root,
                [self.rule("app/src/main/assets/sprites/**")],
                name="target.json",
            )
            link = root / "registry.json"
            link.symlink_to(target)
            with self.assertRaisesRegex(AssetProvenanceError, "non-symlink"):
                validate(root, link)

    def test_duplicate_json_key_is_rejected_by_strict_parser(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.write_asset(root, "app/src/main/assets/sprites/tree.png")
            registry = root / "registry.json"
            registry.write_text(
                '{"schemaVersion":1,"schemaVersion":1,"rules":[]}',
                encoding="utf-8",
            )
            with self.assertRaisesRegex(AssetProvenanceError, "duplicate"):
                validate(root, registry)

    def test_mipmap_directories_are_discovered_without_other_asset_roots(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.write_asset(root, "app/src/main/res/mipmap-hdpi/icon.png")
            registry = self.write_registry(
                root,
                [self.rule("app/src/main/res/mipmap-*/**")],
            )

            report = validate(root, registry)

        self.assertEqual(1, report["assetCount"])
        self.assertEqual(
            "app/src/main/res/mipmap-hdpi/icon.png",
            report["coverage"][0]["path"],
        )

    def write_asset(self, root: Path, relative: str) -> Path:
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b"asset")
        return path

    def write_registry(
        self,
        root: Path,
        rules: list[dict[str, str]],
        name: str = "registry.json",
    ) -> Path:
        path = root / name
        path.write_text(
            json.dumps({"schemaVersion": 1, "rules": rules}),
            encoding="utf-8",
        )
        return path

    def rule(self, pattern: str, **updates: str) -> dict[str, str]:
        rule = {"pattern": pattern, **FIELDS}
        rule.update(updates)
        return rule


if __name__ == "__main__":
    unittest.main()
