# Forest Run — Shared, priority-safe Owl and Eagle flight escape resolution (2026-09-27)

## Neighboring mechanism gap

The 2026-09-27 Eagle pass correction proved that an airborne predator can exit vertically without its bounds passing behind the Player. A subsequent independent Owl inspection found the same end path: `Owl.update` marks the sprite inactive at `y > groundY + birdH` during its alerted dive, while `EntityManager.update` removed it before pass/outcome arbitration. The Owl could silently disappear as `EncounterOutcome.PENDING`, suppressing its safe-passage, near-miss or Bloom outcome despite successfully evading the telegraphed attack. Sleeping Owls that simply drift left already complete through normal horizontal passage and must not be retuned.

## Remediation

`Owl.hasCompletedDiveEscape` is a read-only predicate requiring a genuine visible Owl alert (`hasWarned`), actual DIVING state, and departure. `EntityManager` now collects pending completed Owl/Eagle exits into a shared bird-departure list after update removes them from render and active collision ownership. The single outcome owner arbitrates all live direct HIT/STUMBLE first, then gives completed flights the exact existing exclusive pass/mercy/Bloom result. No new outcome category is invented; no ordinary sleeping Owl or unentered Eagle receives synthetic attack-escape credit. Reset clears the deferred list.

Three new Robolectric tests exercise a real warned Owl dive to vertical departure, the same flight's Bloom conversion, and an unalerted Owl's ordinary horizontal passage without duplicate rewards. The previous real-Eagle tests continue to prove same-frame HIT priority and reset discard. Exact-head host/connected CI must pass before automated closure.

## Remaining scope

Discrete collision tunneling and action-sequence fairness over the full 19-family mixed pool, real touch-to-visible reaction latency, human sprite readability and five-class physical-device matrix remain distinct acceptance tasks. No visual or Play-release approval is inferred.
