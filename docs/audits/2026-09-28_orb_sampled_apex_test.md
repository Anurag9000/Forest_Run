# Forest Run — Use the real sampled jump apex in Orb staging tests (2026-09-28)

Production Orb staging currently takes 75% of the continuous 540px apex (405px) as its conservative vertical limit, which is still below the real sampled 495px maximum. Its approach-time calculation uses the discrete Player feasibility model. The wide surface/geometry regression, however, used the unconstrained continuous parabola `v²/(2g)=540px` as its test acceptance floor. At the largest admitted 50ms Player update, semi-implicit physics reaches only 495px. The old assertion could pass a future regression that staged orbs in the unachievable 495–540px band.

The exhaustive seeded 5-width × 5-height × 96-geometry staging sweep now uses `EncounterActionFeasibility.observe(...).maximumBallisticRisePx`, asserts its 495px source-aligned boundary, and then checks every random Orb centre against that real envelope. Existing real-Player trajectory integration independently checks 495px. No gameplay physics, spawn geometry, reward value or art is changed. This is an automated conservative reachability check, not physical/human approval of every Orb or encounter sequence.

Require exact final HEAD JVM/Android/API35 workflow success before marking the fix verified.
