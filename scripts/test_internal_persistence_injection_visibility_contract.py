#!/usr/bin/env python3
"""Permanent source contract for internal persistence injection visibility."""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
ENGINE = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine"
UI = ROOT / "app/src/main/java/com/anurag9000/forestrun/ui"


class InternalPersistenceInjectionVisibilityContractTest(unittest.TestCase):
    def test_internal_persistence_ports_do_not_escape_public_constructors(self) -> None:
        targets = {
            ENGINE / "EntityManager.kt": "class EntityManager internal constructor(",
            UI / "MainMenuScreen.kt": "class MainMenuScreen internal constructor(",
            UI / "GardenScreen.kt": "class GardenScreen internal constructor(",
        }
        for path, declaration in targets.items():
            source = path.read_text(encoding="utf-8")
            self.assertIn(declaration, source, str(path))

    def test_injected_types_remain_internal_implementation_details(self) -> None:
        facade = (ENGINE / "ApplicationPersistenceFacade.kt").read_text(encoding="utf-8")
        self.assertIn("internal interface ApplicationEncounterPersistence", facade)
        self.assertIn("internal class ApplicationPersistenceFacade", facade)
        self.assertIn("internal interface ApplicationRunOutcomePort", facade)

    def test_temporary_visibility_migration_is_absent(self) -> None:
        self.assertFalse(
            (ROOT / "scripts/migrate_internal_persistence_injection_visibility.py").exists()
        )
        self.assertFalse(
            (ROOT / ".github/workflows/internal-persistence-injection-visibility.yml").exists()
        )


if __name__ == "__main__":
    unittest.main()
