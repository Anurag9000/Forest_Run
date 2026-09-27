# Forest Run — Orchid marked thread must fit the actual Player (2026-09-27)

## Forensic finding

Vanilla Orchid's authored contract is two visible danger bands with one intentional asymmetric safe thread. Its previous top/bottom collision bands shared only 20% of the sprite width and 38% of its height. After visual insets, the marked `threadRect` at the reference minimum 112px sprite width was only about 15px wide, while the live standing Player collision body is 52px wide and jump-start collision body is 70px wide. The old `VanillaOrchidTest` manually replaced `Player.hitbox` with the tiny marked rectangle and therefore could report `NONE` without demonstrating that the live Player could ever occupy the actual highlighted corridor. The visual promise was not supported.

## Source repair

Retain the existing two-band/one-thread encounter and its actual lethal HIT and padded near-miss contract, but make the bottom and overhead hazard rectangles overlap across 78% of the sprite width, leaving a central marked thread wide enough even at the COMPACT profile's minimum sprite width. Open the vertical gap between those physical danger bands and reduce the decorative inset while keeping the highlighted rectangle strictly inside the collision-free space. Change the two matching sprite destination rectangles so each dangerous region remains inside its visibly rendered segment instead of adding hidden collision geometry. Keep the asymmetry, score, random selection, and clearance classifications intact. Do not misstate this source geometry change as final artistic approval.

## Regression

A Robolectric test forces an extremely narrow synthetic sprite so `SpriteSizing` uses the authored minimum width across COMPACT/BALANCED/ROOMY density profiles. It checks clearance for the largest standing/jump/landing collision dimensions, then uses the real `Player.onJumpPressed` / `Player.update(1/60)` and its actual state-dependent hitbox to verify that the marked corridor is occupied collision-free during a sampled full jump. The previous arbitrary tiny-player rectangle test remains to check the band ordering. Exact resulting HEAD's Android host and API-35 validation are the execution authority.

This establishes one-encounter marked-lane reachability at the three tested reference heights, not all possible pairwise approach timings, device feel, or final visual/rights review.
