# Forest Run — Chickadee flutter cue fits the actual Player body (2026-09-28)

## Source-derived contradiction

`ChickadeeGroup.updateFlutterPocket` previously accepted a bird-height-based minimum of 18% and drew an internal/fallback cue at 42% of the 54px baseline bird height (~22.7px). A real Player airborne JUMPING collision rectangle can be ~100px tall (120px presentation scale less two 10px hitbox insets), so the visual "safe pocket" itself could not contain the action it advertised. `updatePlayerInteraction` nevertheless marked `readPocket=true` if *any* sliver of the Player overlapped the highlighted cue, granting the extra unique-pass Seed and score for an uncompleted thread. Prior tests only proved cue/bird disjointness and placed an artificially shrunken player rectangle within the cue.

## Correction

Use a conservative full-body minimum of `Player.BASE_HEIGHT + 2 * padding` (108px at current tuning) when selecting any internal gap and when placing a below-flock fallback; that fallback is anchored to the actual grounded Player hitbox and is omitted entirely if a bird occupies the grounded lane. Retain the bounded no-allocation disjointness scan and existing wing/flock motion. Change the pocket-specific read to require complete containment of the live Player hitbox. This makes the highlighted window and bonus describe actual instantaneous full-body clearance rather than a one-pixel brush. No collision lethality, orbital reward, RNG, audio asset or encounter spawn curve is changed.

Robolectric regression checks the full airborne hitbox dimensions, uses the unmodified grounded Player y position to verify the fallback is genuinely traversable, proves partial overlap grants no read, then full-body alignment earns the single pocket Seed. The existing spread/crowding test now requires a Player-sized cue in addition to disjointness from every bird. Full dynamic moving-flock traversal across time, pairwise action recovery, real-device readability and human acceptance remain OPEN; the instantaneous cue correction must not be described as a complete fairness proof.
