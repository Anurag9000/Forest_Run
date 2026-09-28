# Forest Run — Hedgehog warning owns real reaction lead at speed (2026-09-28)

## Reproduced readability defect

Hedgehog moves at 1.15 times world scroll speed. At the supported 2,000 px/s ceiling and 50 ms admitted recovery frame it advances 115 px per sample. The warning detector previously extended only five staging pads (about 50 px at balanced density) ahead of the body, so a legal frame could move from outside the warning directly into physical STUMBLE. The existing 0.18 s warning timer then described presentation state but did not provide that amount of reaction time.

## Correction

Use one shared `APPROACH_SPEED_MULTIPLIER` for Hedgehog movement and warning-space arithmetic. The forward detector is at least:
`approachSpeed * (warningLeadDuration + MAX_DELTA_SECONDS)`.
The extra one-frame distance absorbs worst-case sampled admission, leaving the full authored warning duration after the first frame that observes the player. The existing five-pad visual floor remains for low/zero speed. Body contact is still always STUMBLE; the outer mercy band and 50%/3 s debuff are unchanged.

The Robolectric regression drives GameState to the production speed ceiling, measures the real warning plane, stages a real Hedgehog one pixel outside it, advances one legal 50 ms EntityManager frame, and proves the warning has fired while a complete authored reaction interval still separates the body from the Player.

This is deterministic source timing evidence, not human proof that the cue is visually understood on every device. Exact-head Android/JVM/API-35 CI remains required.
