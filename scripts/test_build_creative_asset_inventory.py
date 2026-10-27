from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
import os
from unittest.mock import patch
from pathlib import Path

from build_creative_asset_inventory import (
    CreativeAssetInventoryError,
    INVENTORY_KIND,
    build_inventory,
    collect_creative_asset_entries,
    write_inventory,
)


class CreativeAssetInventoryTest(unittest.TestCase):
    CANDIDATE = "a" * 40

    def test_repository_inventory_is_complete_unverified_and_candidate_bound(self) -> None:
        inventory = build_inventory(Path(__file__).resolve().parent.parent, self.CANDIDATE)

        self.assertEqual(INVENTORY_KIND, inventory["kind"])
        self.assertEqual(self.CANDIDATE, inventory["candidateCommit"])
        self.assertEqual(inventory["assetCount"], len(inventory["assets"]))
        self.assertGreater(inventory["assetCount"], 0)
        self.assertEqual(
            inventory["assetCount"],
            inventory["reviewSummary"]["provenanceUnverified"],
        )
        self.assertEqual(
            inventory["assetCount"],
            inventory["reviewSummary"]["licenseReviewRequired"],
        )
        self.assertEqual(0, inventory["reviewSummary"]["provenanceVerified"])
        self.assertEqual(0, inventory["reviewSummary"]["licenseReviewComplete"])
        self.assertTrue(
            all(asset["provenanceStatus"] == "unverified" for asset in inventory["assets"])
        )
        self.assertTrue(
            all(asset["licenseReviewStatus"] == "required" for asset in inventory["assets"])
        )

        identity = {
            "kind": inventory["kind"],
            "candidateCommit": inventory["candidateCommit"],
            "assets": inventory["assets"],
        }
        canonical = (
            json.dumps(identity, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
            + "\n"
        ).encode("utf-8")
        self.assertEqual(hashlib.sha256(canonical).hexdigest(), inventory["inventorySha256"])

    def test_inventory_is_deterministic_for_same_tree_and_candidate(self) -> None:
        root = Path(__file__).resolve().parent.parent
        first = build_inventory(root, self.CANDIDATE)
        second = build_inventory(root, self.CANDIDATE)
        self.assertEqual(first, second)

    def test_candidate_identity_changes_inventory_digest(self) -> None:
        root = Path(__file__).resolve().parent.parent
        first = build_inventory(root, "1" * 40)
        second = build_inventory(root, "2" * 40)
        self.assertNotEqual(first["inventorySha256"], second["inventorySha256"])

    def test_malformed_candidate_sha_is_rejected(self) -> None:
        for malformed in ("", "abc", "g" * 40, "a" * 39, "a" * 41, "A" * 40):
            with self.assertRaisesRegex(CreativeAssetInventoryError, "candidate SHA"):
                build_inventory(Path("."), malformed, validate_runtime_inputs=False)

    def test_inventory_publication_rejects_symlink_without_modifying_target(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / "target.json"
            target.write_text("preserve this\\n", encoding="utf-8")
            output = root / "inventory.json"
            try:
                output.symlink_to(target)
            except (OSError, NotImplementedError):
                self.skipTest("symlinks unavailable")

            with self.assertRaisesRegex(CreativeAssetInventoryError, "symbolic link"):
                write_inventory(output, {"status": "valid"})

            self.assertEqual("preserve this\\n", target.read_text(encoding="utf-8"))
            self.assertTrue(output.is_symlink())

    def test_inventory_publication_is_atomic_and_canonical(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "inventory.json"
            payload = {"z": 2, "a": 1}

            write_inventory(output, payload)

            raw = output.read_bytes()
            self.assertEqual(payload, json.loads(raw.decode("utf-8")))
            self.assertEqual(
                (json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode(),
                raw,
            )
            self.assertFalse(list(output.parent.glob(".inventory.json.*.tmp")))

    @unittest.skipUnless(hasattr(os, "symlink"), "symlink support required")
    def test_inventory_publication_rejects_symlinked_parent(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / "real"
            target.mkdir()
            alias = root / "alias"
            try:
                alias.symlink_to(target, target_is_directory=True)
            except (OSError, NotImplementedError):
                self.skipTest("symlinks unavailable")

            with self.assertRaisesRegex(CreativeAssetInventoryError, "traverse a symbolic link"):
                write_inventory(alias / "inventory.json", {"status": "valid"})

            self.assertFalse((target / "inventory.json").exists())

    def test_file_content_change_changes_entry_digest(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            asset = root / "app/src/main/assets/sprites/test.png"
            audio = root / "app/src/main/res/raw/sfx_test.ogg"
            asset.parent.mkdir(parents=True)
            audio.parent.mkdir(parents=True)
            asset.write_bytes(b"first")
            audio.write_bytes(b"audio")

            before = collect_creative_asset_entries(root)
            asset.write_bytes(b"second")
            after = collect_creative_asset_entries(root)

        self.assertEqual([entry.path for entry in before], [entry.path for entry in after])
        self.assertNotEqual(before[0].sha256, after[0].sha256)
        self.assertEqual(before[1].sha256, after[1].sha256)

    def test_symlinked_creative_input_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            asset_root = root / "app/src/main/assets"
            raw_root = root / "app/src/main/res/raw"
            asset_root.mkdir(parents=True)
            raw_root.mkdir(parents=True)
            (raw_root / "sfx_test.ogg").write_bytes(b"audio")
            target = root / "outside.png"
            target.write_bytes(b"outside")
            try:
                (asset_root / "linked.png").symlink_to(target)
            except (OSError, NotImplementedError):
                self.skipTest("symlinks unavailable")

            with self.assertRaisesRegex(CreativeAssetInventoryError, "rejects symlinks"):
                collect_creative_asset_entries(root)

    def test_asset_replacement_between_stat_and_open_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            assets = root / "app/src/main/assets"
            raw = root / "app/src/main/res/raw"
            assets.mkdir(parents=True)
            raw.mkdir(parents=True)
            asset = assets / "image.png"
            asset.write_bytes(b"original")
            (raw / "sfx_test.ogg").write_bytes(b"audio")
            real_open = os.open
            replaced = False

            def replace_before_open(path, flags, *args, **kwargs):
                nonlocal replaced
                if Path(path) == asset and not replaced:
                    replacement = assets / "replacement"
                    replacement.write_bytes(b"changed!")
                    replacement.replace(asset)
                    replaced = True
                return real_open(path, flags, *args, **kwargs)

            with patch("build_creative_asset_inventory.os.open", side_effect=replace_before_open):
                with self.assertRaisesRegex(CreativeAssetInventoryError, "changed while being opened"):
                    collect_creative_asset_entries(root)


if __name__ == "__main__":
    unittest.main()
