from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
TOP_LEVEL = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
APP = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
SETTINGS = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
REQUIREMENTS = (ROOT / "scripts/requirements-ci.txt").read_text(encoding="utf-8")


class DependencyAuthorityTest(unittest.TestCase):
    def test_no_stale_parallel_version_catalog_exists(self) -> None:
        self.assertFalse((ROOT / "gradle/libs.versions.toml").exists())
        self.assertNotIn("libs.", TOP_LEVEL)
        self.assertNotIn("libs.", APP)

    def test_gradle_plugins_and_modules_use_fixed_versions(self) -> None:
        plugin_versions = re.findall(
            r'id\("[^"]+"\)\s+version\s+"([^"]+)"',
            TOP_LEVEL,
        )
        self.assertEqual(2, len(plugin_versions))
        dependencies = re.findall(
            r'(?:implementation|testImplementation|androidTestImplementation)\('
            r'"([^":]+:[^":]+):([^"]+)"\)',
            APP,
        )
        self.assertGreaterEqual(len(dependencies), 7)
        coordinates = [coordinate for coordinate, _ in dependencies]
        self.assertEqual(len(coordinates), len(set(coordinates)))
        for version in plugin_versions + [version for _, version in dependencies]:
            self.assertNotIn("+", version)
            self.assertNotIn("SNAPSHOT", version.upper())
            self.assertNotIn("latest", version.lower())
            self.assertRegex(version, r"^[0-9]+(?:\.[0-9A-Za-z-]+)+$")

    def test_non_compose_canvas_architecture_remains_explicit(self) -> None:
        self.assertIn("compose = false", APP)
        self.assertNotRegex(APP, re.compile(r"androidx\.compose|material3", re.IGNORECASE))
        self.assertNotIn("org.jetbrains.kotlin.plugin.compose", TOP_LEVEL)

    def test_repositories_are_centralized(self) -> None:
        self.assertIn(
            "repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)",
            SETTINGS,
        )
        self.assertNotRegex(APP, re.compile(r"\brepositories\s*\{"))

    def test_python_ci_dependencies_are_exactly_pinned(self) -> None:
        lines = [
            line.strip()
            for line in REQUIREMENTS.splitlines()
            if line.strip() and not line.lstrip().startswith("#")
        ]
        self.assertTrue(lines)
        for line in lines:
            self.assertRegex(
                line,
                r"^[A-Za-z0-9_.-]+==[0-9]+(?:\.[0-9A-Za-z-]+)+$",
            )
            self.assertNotIn(";", line)
            self.assertNotIn("@", line)


if __name__ == "__main__":
    unittest.main()
