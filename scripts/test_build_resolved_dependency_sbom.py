from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from build_resolved_dependency_sbom import (
    ResolvedDependencySbomError,
    build_sbom,
    canonical_bytes,
    publish,
    resolved_coordinates,
)


SHA = "1" * 40
REPORT = """releaseRuntimeClasspath - Runtime classpath of '/release'.
+--- androidx.appcompat:appcompat:1.7.0
|    +--- androidx.activity:activity:1.8.0 -> 1.9.2
|    \\--- androidx.core:core:1.13.1 (*)
+--- androidx.core:core-ktx:1.13.1
|    \\--- org.jetbrains.kotlin:kotlin-stdlib:1.9.22
\\--- com.example:shared:1.0 -> 1.2
"""


class ResolvedDependencySbomTest(unittest.TestCase):
    def test_parser_uses_final_selected_versions_and_deduplicates(self) -> None:
        coordinates = resolved_coordinates(REPORT)

        self.assertEqual(
            [
                ("androidx.activity", "activity", "1.9.2"),
                ("androidx.appcompat", "appcompat", "1.7.0"),
                ("androidx.core", "core", "1.13.1"),
                ("androidx.core", "core-ktx", "1.13.1"),
                ("com.example", "shared", "1.2"),
                ("org.jetbrains.kotlin", "kotlin-stdlib", "1.9.22"),
            ],
            coordinates,
        )

    def test_build_is_candidate_bound_deterministic_and_cyclonedx(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            report = root / "release.txt"
            report.write_text(REPORT, encoding="utf-8")

            first = build_sbom([("releaseRuntimeClasspath", report)], SHA)
            second = build_sbom([("releaseRuntimeClasspath", report)], SHA)

        self.assertEqual(first, second)
        self.assertEqual("CycloneDX", first["bomFormat"])
        self.assertEqual("1.6", first["specVersion"])
        self.assertEqual(6, len(first["components"]))
        self.assertEqual(
            f"pkg:generic/forest-run@{SHA}",
            first["metadata"]["component"]["bom-ref"],
        )
        purls = [component["purl"] for component in first["components"]]
        self.assertEqual(sorted(purls), purls)
        self.assertIn("pkg:maven/com.example/shared@1.2", purls)
        properties = {
            item["name"]: item["value"]
            for item in first["metadata"]["properties"]
        }
        self.assertEqual(SHA, properties["forest-run:candidate-sha"])
        self.assertEqual(
            hashlib.sha256(canonical_bytes(first["components"])).hexdigest(),
            properties["forest-run:component-set-sha256"],
        )

    def test_multiple_reports_are_unioned_with_source_digests(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            release = root / "release.txt"
            android_test = root / "android-test.txt"
            release.write_text(
                "+--- com.example:runtime:1.0\n",
                encoding="utf-8",
            )
            android_test.write_text(
                "+--- com.example:runtime:1.0\n"
                "\\--- com.example:test-support:2.0\n",
                encoding="utf-8",
            )

            payload = build_sbom(
                [
                    ("releaseRuntimeClasspath", release),
                    ("debugAndroidTestRuntimeClasspath", android_test),
                ],
                SHA,
            )

        self.assertEqual(2, len(payload["components"]))
        top_properties = {
            item["name"]: item["value"]
            for item in payload["properties"]
        }
        sources = json.loads(top_properties["forest-run:source-reports"])
        self.assertEqual(
            [
                "releaseRuntimeClasspath",
                "debugAndroidTestRuntimeClasspath",
            ],
            [source["configuration"] for source in sources],
        )
        self.assertTrue(all(len(source["sha256"]) == 64 for source in sources))

    def test_conflicting_final_versions_fail_closed(self) -> None:
        report = (
            "+--- com.example:shared:1.0\n"
            "\\--- com.example:shared:2.0\n"
        )
        with self.assertRaisesRegex(
            ResolvedDependencySbomError,
            "conflicting final versions",
        ):
            resolved_coordinates(report)

    def test_invalid_candidate_empty_report_and_duplicate_configuration_fail(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            empty = root / "empty.txt"
            empty.write_text("No dependencies\n", encoding="utf-8")
            valid = root / "valid.txt"
            valid.write_text("+--- com.example:one:1.0\n", encoding="utf-8")

            with self.assertRaisesRegex(
                ResolvedDependencySbomError,
                "candidate SHA",
            ):
                build_sbom([("release", valid)], "ABC")
            with self.assertRaisesRegex(
                ResolvedDependencySbomError,
                "did not contain any Maven coordinates",
            ):
                build_sbom([("release", empty)], SHA)
            with self.assertRaisesRegex(
                ResolvedDependencySbomError,
                "unique",
            ):
                build_sbom(
                    [("release", valid), ("release", valid)],
                    SHA,
                )

    def test_non_utf8_symlink_and_output_symlink_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            invalid = root / "invalid.txt"
            invalid.write_bytes(b"\xff\xfe")
            target = root / "target.txt"
            target.write_text("+--- com.example:one:1.0\n", encoding="utf-8")
            report_link = root / "report-link.txt"
            report_link.symlink_to(target)

            with self.assertRaisesRegex(
                ResolvedDependencySbomError,
                "not UTF-8",
            ):
                build_sbom([("release", invalid)], SHA)
            with self.assertRaisesRegex(
                ResolvedDependencySbomError,
                "non-symlink",
            ):
                build_sbom([("release", report_link)], SHA)

            output_target = root / "output-target.json"
            output_target.write_text("unchanged", encoding="utf-8")
            output_link = root / "output.json"
            output_link.symlink_to(output_target)
            with self.assertRaisesRegex(
                ResolvedDependencySbomError,
                "symbolic link",
            ):
                publish(output_link, {"ok": True})
            self.assertEqual("unchanged", output_target.read_text(encoding="utf-8"))

    def test_publish_is_canonical_and_replaces_regular_file(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "nested" / "sbom.json"
            output.parent.mkdir(parents=True)
            output.write_text("old", encoding="utf-8")
            payload = {"z": 1, "a": [2, 3]}

            publish(output, payload)

            self.assertEqual(canonical_bytes(payload), output.read_bytes())


if __name__ == "__main__":
    unittest.main()
