# Forest Run — Chickadee flutter pocket collision correctness (2026-09-26)

## Failure revealed by exact-head test

The `5ac06f84` GitHub host validation failed one of 1112 JVM tests: `ChickadeeGroupTest.chickadee group exposes a readable flutter pocket around the lead bird` at line 52. The newer compound collision priority correctly checked every bird for direct HIT, revealing that the existing visually advertised "safe" pocket could contain the lead bird itself. The previous early mercy return from an earlier nearby bird had masked that direct hit.

The old pocket constructed its vertical range using `minOf(bird.bottom) + 4` to `maxOf(bird.top) - 4` over the entire flock. With three independently spaced birds, that range can span the middle bird's physical hitbox. A correct outcome arbiter must not be weakened to make a misleading guide seem safe.

## Remediation

`ChickadeeGroup.updateFlutterPocket` now performs a bounded, allocation-free scan across its 2–4 live bird rectangles for an actual empty vertical gap, with padding and minimum height. It chooses a gap near the lead bird and caps the cue's visual height. If flock geometry is too crowded, it locates the pocket just below every bird instead of crossing any occupied body. Its horizontal relationship to the lead and player-interaction/cue ownership remain unchanged.

The existing real-player pocket test now remains a correct behavioral requirement under HIT-first collision ordering. Additional Robolectric cases assert non-intersection with **every** live bird across separated, moderately spaced and densely crowded configurations and across subsequent updates.

## Validation boundary

This is source and emulator-testable geometric fairness, not a substitute for live-device readability, motion feel, accessibility or final creative signoff. Treat the exact resulting commit's workflow as the authority.
