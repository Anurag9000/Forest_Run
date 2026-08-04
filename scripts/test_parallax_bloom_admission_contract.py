#!/usr/bin/env python3
"""Source contract for finite Parallax Bloom presentation admission."""

from __future__ import annotations

import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
ENGINE = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine"
SOURCE = ENGINE / "ParallaxBackground.kt"
ADMISSION = ENGINE / "BloomPresentationAdmission.kt"
INTEGRATION = (
    ROOT
    / "app/src/test/java/com/anurag9000/forestrun/engine/ParallaxBloomAdmissionIntegrationTest.kt"
)


def extract_braced_block(source: str, signature: str) -> str:
    start = source.index(signature)
    brace = source.index("{", start)
    depth = 0
    index = brace
    while index < len(source):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start : index + 1]
        index += 1
    raise AssertionError(f"Unbalanced block for {signature!r}")


class ParallaxBloomAdmissionContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = SOURCE.read_text(encoding="utf-8")
        cls.admission = ADMISSION.read_text(encoding="utf-8")
        cls.integration = INTEGRATION.read_text(encoding="utf-8")

    def test_shared_admission_fails_nonfinite_values_closed(self) -> None:
        self.assertIn("internal object BloomPresentationAdmission", self.admission)
        self.assertIn("value.takeIf { it.isFinite() }", self.admission)
        self.assertIn("?.coerceIn(0f, 1f) ?: 0f", self.admission)

    def test_set_bloom_state_uses_shared_admission_for_both_channels(self) -> None:
        block = extract_braced_block(self.source, "fun setBloomState(")
        self.assertIn("bloomTarget = if (isActive) 1f else 0f", block)
        self.assertIn(
            "bloomActivationLevel = BloomPresentationAdmission.level(activationLevel)",
            block,
        )
        self.assertIn(
            "bloomAfterglowLevel = BloomPresentationAdmission.level(afterglowLevel)",
            block,
        )
        self.assertNotIn("activationLevel.coerceIn", block)
        self.assertNotIn("afterglowLevel.coerceIn", block)

    def test_profile_and_draw_paths_reuse_the_same_boundary(self) -> None:
        profile = extract_braced_block(
            self.source,
            "internal fun resolveParallaxAtmosphereProfile(",
        )
        self.assertIn(
            "val bloom = BloomPresentationAdmission.level(bloomStrength)",
            profile,
        )

        draw = extract_braced_block(self.source, "private fun drawBloomTransformation(")
        self.assertIn(
            "val bloomStrength = BloomPresentationAdmission.level(bloomLevel)",
            draw,
        )
        self.assertIn(
            "val activationBoost = BloomPresentationAdmission.level(bloomActivationLevel)",
            draw,
        )
        self.assertIn(
            "val afterglowStrength = BloomPresentationAdmission.level(bloomAfterglowLevel)",
            draw,
        )

    def test_integration_covers_nonfinite_and_finite_inputs(self) -> None:
        for token in (
            "Float.NaN",
            "Float.POSITIVE_INFINITY",
            "Float.NEGATIVE_INFINITY",
            'privateFloat("bloomActivationLevel")',
            'privateFloat("bloomAfterglowLevel")',
            "activationLevel = -0.25f",
            "afterglowLevel = 1.75f",
            "activationLevel = 0.35f",
            "afterglowLevel = 0.65f",
        ):
            self.assertIn(token, self.integration)


if __name__ == "__main__":
    unittest.main()
