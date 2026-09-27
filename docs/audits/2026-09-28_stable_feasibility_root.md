# Forest Run — Numerically stable rise-time feasibility root (2026-09-28)

## Source-derived defect

The single-action verifier solved the earliest ballistic rise time using `(v - sqrt(v² - 2gh)) / g`. This mathematically valid but numerically unstable subtraction can cancel when the requested rise is small relative to a large upward velocity. With positive finite Float inputs `v = h = Float.MAX_VALUE`, `g = 1`, the intermediate discriminant is representable in Double but the subtraction rounds to exactly zero, falsely allowing a 0.5-second reaction window although the true earliest rise time is approximately 1 second. The model also used `Float.MAX_VALUE` as the unattainable-rise sentinel; it must not be admitted as a genuinely feasible finite rise time.

## Correction and regression

Use the conjugate rationalized smaller quadratic root, `2h / (v + sqrt(v² - 2gh))`, in Double before bounded Float conversion, and reject the sentinel explicitly in the jump-feasibility predicate. Existing physical constants, game movement integration, authored encounter data, opening pacing, latency and release evidence remain unchanged. Two tests cover the formerly false-positive extreme finite case and a small positive clearance. Previous monotonicity, apex, input admission and wide production pacing tests continue to own the normal domain. Exact-HEAD CI is needed to establish executable status.

This proves a better isolated arithmetic boundary; mixed encounter feasibility, full player trajectories and human/device fairness remain separate OPEN requirements.
