# Forest Run — Discrete Player rise in action-feasibility evidence (2026-09-28)

## Defect and independently derived physical reference

The existing source-side `EncounterActionFeasibility` used a continuous gravity parabola. It identified an ideal 540 px full-jump rise as reachable and reported the continuous earliest crossing time. But `Player.updatePhysics` is semi-implicit Euler and the admitted public frame is capped to 0.05 s. With maximum initial upward speed 1,800 px/s and gravity 3,000 px/s², its twelve 50 ms upward steps have an actual peak of 495 px. The verifier could therefore certify 500 px clearance at generous lead although that height is unreachable under an admitted 50 ms step. Reduced APEX gravity begins after the upward velocity has reached zero, so it does not repair the missing rise.

## Correction

Use the identical sampled-height law, `rise(n) = n v dt - g dt² n(n+1)/2`, with `dt=FrameInputAdmission.MAX_DELTA_SECONDS`. Inspect only the two integer steps straddling the discrete parabola vertex (bounded O(1) even for extreme finite inputs) and return their actual maximum. Solve earliest rise using the numerically stable smaller root of the equivalent sampled parabola with effective speed `v - g dt/2`; round up to an admissible complete frame and verify the resulting sample actually achieves the target. Keep existing malformed-input failure semantics and avoid changing gameplay physics, difficulty constants or actual spawning.

Regression checks distinguish continuous 540 from discrete 495, reject 500, permit 495, and do not credit tiny rise before the first admitted update. A separate real-Player Robolectric integration test verifies the sampled physical trajectory and the verifier's boundary. This is a conservative one-action source test, **not** evidence that arbitrary encounter pairs or physical-device reaction time are fair.

Run exact-head JVM, Android packaging and API35 connected CI before claiming execution passed.
