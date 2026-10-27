from __future__ import annotations

import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import build_creative_asset_inventory
import build_declared_dependency_inventory
import build_resolved_dependency_sbom
import compare_visual_regression
import stable_file_reader
import verify_android_page_size_package
import visual_baseline_provenance


class AtomicReportPublisherTest(unittest.TestCase):
    def test_parent_swap_cannot_redirect_single_file_report_publishers(self) -> None:
        publishers = (
            ("creative asset inventory", build_creative_asset_inventory.write_inventory),
            ("declared dependency inventory", build_declared_dependency_inventory.publish),
            ("resolved dependency SBOM", build_resolved_dependency_sbom.publish),
            ("visual regression report", compare_visual_regression._write_json_atomic),
            ("visual baseline provenance", visual_baseline_provenance.publish),
            ("page-size report", verify_android_page_size_package.publish),
        )
        for label, publish in publishers:
            with self.subTest(publisher=label), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                output = root / "reports/result.json"
                output.parent.mkdir()
                moved_parent = root / "reports-moved"
                redirected = root / "redirected"
                redirected.mkdir()
                real_open = os.open
                swapped = False

                def swap_before_temp_open(path, flags, *args, **kwargs):
                    nonlocal swapped
                    if (
                        not swapped
                        and kwargs.get("dir_fd") is not None
                        and isinstance(path, str)
                        and path.startswith(".result.json.")
                    ):
                        swapped = True
                        output.parent.rename(moved_parent)
                        output.parent.symlink_to(redirected, target_is_directory=True)
                    return real_open(path, flags, *args, **kwargs)

                with patch.object(
                    stable_file_reader.os,
                    "open",
                    side_effect=swap_before_temp_open,
                ):
                    with self.assertRaises((
                        build_creative_asset_inventory.CreativeAssetInventoryError,
                        build_declared_dependency_inventory.DeclaredDependencyInventoryError,
                        build_resolved_dependency_sbom.ResolvedDependencySbomError,
                        compare_visual_regression.VisualRegressionError,
                        visual_baseline_provenance.VisualBaselineProvenanceError,
                        verify_android_page_size_package.PageSizeInspectionError,
                    )):
                        publish(output, {"status": "valid"})

                self.assertTrue(swapped)
                self.assertFalse((redirected / output.name).exists())
                self.assertFalse((moved_parent / output.name).exists())
                self.assertEqual([], list(moved_parent.glob(".result.json.*.tmp")))


if __name__ == "__main__":
    unittest.main()
