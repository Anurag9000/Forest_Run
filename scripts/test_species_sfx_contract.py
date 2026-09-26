"""Source wiring contract for authored species SFX at one-shot threat cues."""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ENTITIES = ROOT / "app/src/main/java/com/anurag9000/forestrun/entities"
ENGINE = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine"


class SpeciesSfxContractTest(unittest.TestCase):
    def test_eagle_screeches_once_at_the_mark_announcement_boundary(self) -> None:
        source = (ENTITIES / "birds/Eagle.kt").read_text(encoding="utf-8")
        announcement = source.split("private fun announceTarget() {", 1)[1].split(
            "override fun update(", 1
        )[0]
        self.assertRegex(
            announcement,
            r"if \(targetAnnounced\) return\s+targetAnnounced = true\s+SfxManager\.playScreech\(\)",
        )
        self.assertEqual(1, source.count("SfxManager.playScreech()"))

    def test_owl_screeches_on_first_jump_alert_not_in_idle_or_dive_loop(self) -> None:
        source = (ENTITIES / "birds/Owl.kt").read_text(encoding="utf-8")
        interaction = source.split("override fun updatePlayerInteraction(", 1)[1].split(
            "override fun onCollision(", 1
        )[0]
        first_alert = interaction.split("if (!hasWarned) {", 1)[1]
        self.assertRegex(
            first_alert,
            r"hasWarned = true[\s\S]*?SfxManager\.playScreech\(\)",
        )
        self.assertEqual(1, source.count("SfxManager.playScreech()"))
        self.assertNotIn("SfxManager.playScreech()", source.split("override fun update(", 1)[1].split("override fun draw(", 1)[0])

    def test_species_only_use_their_authored_sfx_and_sample_is_required(self) -> None:
        sfx = (ENGINE / "SfxManager.kt").read_text(encoding="utf-8")
        self.assertIn('idScreech = load("sfx_screech")', sfx)
        self.assertIn("fun playScreech() = play(idScreech, 1.0f)", sfx)
        self.assertIn("if (!FeedbackSettings.audioEnabled || !sampleReadiness.isReady(id)) return", sfx)
        for family, filename, cue in (
            ("animals", "Dog.kt", "playBark"),
            ("animals", "Wolf.kt", "playHowl"),
        ):
            source = (ENTITIES / family / filename).read_text(encoding="utf-8")
            self.assertIn(f"SfxManager.{cue}()", source)
            self.assertNotIn("SfxManager.playScreech()", source)

        all_species = list(ENTITIES.rglob("*.kt"))
        screech_owners = {
            path.stem for path in all_species
            if "SfxManager.playScreech()" in path.read_text(encoding="utf-8")
        }
        self.assertEqual({"Eagle", "Owl"}, screech_owners)


if __name__ == "__main__":
    unittest.main()
