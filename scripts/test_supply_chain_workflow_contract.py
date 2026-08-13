from __future__ import annotations

import re
import unittest
from pathlib import Path


WORKFLOW = (
    Path(__file__).resolve().parent.parent
    / ".github"
    / "workflows"
    / "android-validation.yml"
).read_text(encoding="utf-8")


class SupplyChainWorkflowContractTest(unittest.TestCase):
    def test_workflow_generates_the_two_documented_dependency_reports(self) -> None:
        for configuration in (
            "releaseRuntimeClasspath",
            "debugAndroidTestRuntimeClasspath",
        ):
            self.assertIn(f"--configuration {configuration}", WORKFLOW)
            self.assertIn(f"{configuration}.txt", WORKFLOW)

    def test_workflow_uses_checked_in_builders_and_cross_validator(self) -> None:
        for script in (
            "build_declared_dependency_inventory.py",
            "build_resolved_dependency_sbom.py",
            "validate_supply_chain_evidence.py",
        ):
            self.assertIn(f"python3 scripts/{script}", WORKFLOW)
        self.assertIn('--candidate-sha "${GITHUB_SHA}"', WORKFLOW)
        self.assertIn('--expected-commit "${GITHUB_SHA}"', WORKFLOW)
        self.assertIn("build/supply-chain", WORKFLOW)

    def test_obsolete_gradle_sbom_tasks_are_not_invoked(self) -> None:
        obsolete_task_line = re.compile(
            r"(?m)^\s+(?:generateSbom|buildResolvedDependencySbom)\s*(?:\\)?\s*$"
        )
        self.assertNotRegex(WORKFLOW, obsolete_task_line)


if __name__ == "__main__":
    unittest.main()
