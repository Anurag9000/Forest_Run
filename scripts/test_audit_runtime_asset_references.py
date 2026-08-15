from __future__ import annotations

import os
import tempfile
import unittest
from pathlib import Path

from audit_runtime_asset_references import (
    RuntimeAssetReferenceError,
    audit_runtime_asset_references,
    verify_repository,
)


class RuntimeAssetReferenceAuditTest(unittest.TestCase):
    def test_repository_runtime_assets_are_fully_owned(self) -> None:
        evidence = verify_repository()
        self.assertEqual(evidence.declared_asset_count, evidence.packaged_asset_count)
        self.assertEqual(
            evidence.duplicate_content_groups,
            evidence.allowed_duplicate_content_groups,
        )

    def test_exact_declared_inventory_passes(self) -> None:
        with self.fixture(
            declarations=("sprites/a.png", "fonts/font.ttf"),
            files={"sprites/a.png": b"sprite", "fonts/font.ttf": b"font"},
        ) as (source, assets):
            evidence = audit_runtime_asset_references(source, assets)

        self.assertEqual(2, evidence.declared_asset_count)
        self.assertEqual(2, evidence.packaged_asset_count)
        self.assertEqual(0, evidence.duplicate_content_groups)

    def test_undeclared_packaged_asset_is_rejected(self) -> None:
        with self.fixture(
            declarations=("sprites/a.png",),
            files={"sprites/a.png": b"a", "sprites/orphan.png": b"orphan"},
        ) as (source, assets):
            with self.assertRaisesRegex(RuntimeAssetReferenceError, "no AssetPaths owner"):
                audit_runtime_asset_references(source, assets)

    def test_missing_declared_asset_is_rejected(self) -> None:
        with self.fixture(
            declarations=("sprites/a.png", "sprites/missing.png"),
            files={"sprites/a.png": b"a"},
        ) as (source, assets):
            with self.assertRaisesRegex(RuntimeAssetReferenceError, "are missing"):
                audit_runtime_asset_references(source, assets)

    def test_new_byte_identical_group_is_rejected(self) -> None:
        with self.fixture(
            declarations=("sprites/a.png", "sprites/b.png"),
            files={"sprites/a.png": b"same", "sprites/b.png": b"same"},
        ) as (source, assets):
            with self.assertRaisesRegex(RuntimeAssetReferenceError, "Unexpected byte-identical"):
                audit_runtime_asset_references(source, assets)

    def test_explicit_alias_group_is_accepted_but_not_required_to_stay_duplicate(self) -> None:
        alias = frozenset({"sprites/base.png", "sprites/flying.png"})
        allowed = frozenset({alias})

        with self.fixture(
            declarations=tuple(sorted(alias)),
            files={path: b"same" for path in alias},
        ) as (source, assets):
            evidence = audit_runtime_asset_references(
                source,
                assets,
                allowed_duplicate_groups=allowed,
            )
        self.assertEqual(1, evidence.duplicate_content_groups)
        self.assertEqual(1, evidence.allowed_duplicate_content_groups)

        with self.fixture(
            declarations=tuple(sorted(alias)),
            files={"sprites/base.png": b"base", "sprites/flying.png": b"new art"},
        ) as (source, assets):
            evidence = audit_runtime_asset_references(
                source,
                assets,
                allowed_duplicate_groups=allowed,
            )
        self.assertEqual(0, evidence.duplicate_content_groups)

    def test_known_alias_plus_third_duplicate_is_rejected(self) -> None:
        alias = frozenset({"sprites/base.png", "sprites/flying.png"})
        files = {
            "sprites/base.png": b"same",
            "sprites/flying.png": b"same",
            "sprites/third.png": b"same",
        }
        with self.fixture(tuple(files), files) as (source, assets):
            with self.assertRaisesRegex(RuntimeAssetReferenceError, "Unexpected byte-identical"):
                audit_runtime_asset_references(
                    source,
                    assets,
                    allowed_duplicate_groups=frozenset({alias}),
                )

    @unittest.skipUnless(hasattr(os, "symlink"), "symlinks unavailable")
    def test_symlinked_runtime_asset_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            assets = root / "assets"
            assets.mkdir()
            target = root / "target.png"
            target.write_bytes(b"outside")
            os.symlink(target, assets / "linked.png")
            source = self.write_source(root, ("linked.png",))

            with self.assertRaisesRegex(RuntimeAssetReferenceError, "must not contain symlinks"):
                audit_runtime_asset_references(source, assets)

    class fixture:
        def __init__(
            self,
            declarations: tuple[str, ...],
            files: dict[str, bytes],
        ) -> None:
            self.declarations = declarations
            self.files = files
            self.temp: tempfile.TemporaryDirectory[str] | None = None

        def __enter__(self) -> tuple[Path, Path]:
            self.temp = tempfile.TemporaryDirectory()
            root = Path(self.temp.name)
            assets = root / "assets"
            assets.mkdir()
            for relative, payload in self.files.items():
                path = assets / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(payload)
            return RuntimeAssetReferenceAuditTest.write_source(
                root,
                self.declarations,
            ), assets

        def __exit__(self, exc_type, exc, tb) -> None:
            assert self.temp is not None
            self.temp.cleanup()

    @staticmethod
    def write_source(root: Path, declarations: tuple[str, ...]) -> Path:
        source = root / "AssetPaths.kt"
        lines = ["object AssetPaths {"]
        for index, relative in enumerate(declarations):
            lines.append(f'    const val ASSET_{index} = "{relative}"')
        lines.append("}")
        source.write_text("\n".join(lines) + "\n", encoding="utf-8")
        return source


if __name__ == "__main__":
    unittest.main()
