from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ACTIVITY = ROOT / "app/src/main/java/com/anurag9000/forestrun/ForestJournalActivity.kt"
INSTRUMENTED = ROOT / "app/src/androidTest/java/com/anurag9000/forestrun/ForestJournalLifecycleInstrumentedTest.kt"


class ForestJournalLifecycleContractTest(unittest.TestCase):
    def test_section_selection_is_bundle_only_ui_state(self) -> None:
        activity = ACTIVITY.read_text(encoding="utf-8")

        self.assertIn('STATE_JOURNAL_SECTION = "forest_journal_selected_section"', activity)
        self.assertIn("override fun onSaveInstanceState(outState: Bundle)", activity)
        self.assertIn("outState.putString(STATE_JOURNAL_SECTION, selectedSection.name)", activity)
        self.assertIn("savedInstanceState", activity)
        self.assertIn("JournalSection.entries.firstOrNull", activity)
        self.assertIn("?: JournalSection.ALL", activity)

        for forbidden in (
            "getSharedPreferences(",
            "SaveManager.",
            "PersistentMemoryManager.",
        ):
            self.assertNotIn(forbidden, activity)

    def test_device_contract_recreates_activity_without_progress_write(self) -> None:
        test = INSTRUMENTED.read_text(encoding="utf-8")

        self.assertIn('findButton(activity.window.decorView, "Memories")', test)
        self.assertIn("scenario.recreate()", test)
        self.assertIn('"Memories Journal section, selected"', test)
        self.assertIn("val before = gamePrefs.all.toMap()", test)
        self.assertIn("assertEquals(before, gamePrefs.all.toMap())", test)


if __name__ == "__main__":
    unittest.main()
