# Forest Run — Frame-cadence-independent presentation timing (2026-09-28)

## Finding

The production `GameThread` deliberately targets 60 Hz, but it passes the measured elapsed interval into the simulation and can miss its 16.67 ms target under real work. `GameView` nevertheless drove the run cinematic shimmer with `debugFrameCounter / 60f` and sampled accessibility semantics every 30 updates. After a slow 33–50 ms frame, physics/presentation systems using delta time advanced by the admitted elapsed interval while these two paths advanced as if only 16.67 ms had passed. The fixed frame-count accessibility gate likewise stretched its nominal ~0.5-second sampling cadence when processing fell below target.

## Repair

Add a tiny `RuntimeCadenceClock` advanced by the same validated and bounded delta already admitted by `GameView`. Cinematic shimmer consumes that elapsed-seconds clock. Accessibility snapshot sampling consumes a 0.5-second elapsed-time gate, preserving the previous ideal-60-Hz intent without coupling it to the number of updates completed. The existing `AccessibilityAnnouncementPolicy` still owns semantic coalescing and its 10-second routine interval.

Unit tests verify one real second partitioned into 30, 60 and 120 update slices produces the same elapsed time and two accessibility polls, plus single-consume, malformed-input and reset behavior. These are update-partition experiments, not a claim that the production loop renders at 120 Hz.

## Boundary

This corrects source timing semantics under variable processing cadence. It does not prove display refresh behavior, physical TalkBack latency, or high-refresh-device acceptance.
