# Forest Run — Owl pre-entry alert admission (2026-09-26)

The Owl implementation documents its mechanic as an alert/dive if the player jumps **while the owl is visible**. `EntityManager.update` intentionally calls `updatePlayerInteraction` on every active pending encounter, including staged objects still beyond the right edge. Owl's previous logic did not check its visible horizontal interval, so any early jump could alert it before entry and start a dive offscreen. This also makes the newly wired one-shot screech fire before the player can see its visual telegraph.

Owl now receives the factory's sanitized screen width, and admits player-triggered alert only when active and some portion of its body is inside the viewport. This retains its current alert/dive state machine, telegraph duration, hitboxes and sound ownership. The factory passes `safeScreenWidth`; the concrete Owl tests explicitly provide screen width. A Robolectric test checks no offscreen alert or warning, subsequent alert after scrolling into view, and no repeated state transition on another interaction.

This fixes a source-level mechanic/telegraph mismatch. Physical alert audibility, gameplay speed/fairness, and human visual review are separate acceptance gates.
