from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/GameThread.kt"


class GameThreadPostedFrameContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = SOURCE.read_text(encoding="utf-8")

    def test_latency_completion_happens_only_after_render_returns(self) -> None:
        render_call = self.source.index("renderFrame()")
        telemetry = self.source.index(
            "InputLatencyTelemetryRegistry.recordFrameRendered",
            render_call,
        )
        self.assertLess(render_call, telemetry)

    def test_surface_post_failures_are_not_swallowed(self) -> None:
        start = self.source.index("private fun renderSurfaceFrame")
        block = self.source[start:]
        self.assertIn("SurfaceHolder.lockCanvas returned no Canvas", block)
        self.assertIn("surfaceHolder.unlockCanvasAndPost(canvas)", block)
        self.assertNotIn("catch (_: Exception)", block)
        self.assertIn("reportFailure(FrameStage.RENDER, failure)", self.source)


if __name__ == "__main__":
    unittest.main()
