# Forest Run — Eagle authored staging and lifecycle (2026-09-26)

## Reconstructed contract

The deterministic encounter director schedules `EAGLE_MARK` at `screenWidth + 520` and `screenWidth + 1060`, and `BIRD_SHOWCASE` at `screenWidth + 1280`. `EntityManager.update` spawns those directives and immediately calls `Eagle.update` in the same frame. The Eagle previously despawned whenever `x > screenWidth + 150`, including before it had started its lock-on movement. As a result, all three authored Eagle steps would be removed at their first update without ever reaching the player.

The September 20 historical audit narrowed an instrumentation roster fixture to `screenWidth + 100` and described the prior Eagle loss as a test-only placement error. That correction proved the close-in roster fixture, but not the real director's larger authored offsets. This is a fresh production scenario/runtime contract defect and supersedes the historical conclusion for those paths.

## Remediation

Eagle now tracks whether its body has actually entered the horizontal viewport. The right-departure condition applies only after entry, retaining the existing 150-pixel tolerance for genuinely departing birds. Ground and left-edge despawn continue unchanged; movement, target mark, grace, hitbox, SFX, and authored scenario offsets are preserved. The bird can therefore be staged offscreen, announce a reticle and approach after lock-on rather than disappearing.

`EagleTest` uses real `Eagle`, `Player` and `GameStateManager`, and covers all three exact authored offsets, the first update, the lock-on period, continued life during approach and eventual horizontal entry. It would fail with the former unconditional far-right despawn.

## Verification boundary

Run the exact new head's JVM and connected tests. Source correctness does not establish actual physical-device perception of its distant telegraph or human fairness/creative acceptance.
