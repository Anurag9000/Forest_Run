# Forest Run — Fox/Wolf stand-down versus ordinary pass accounting (2026-09-28)

## Finding

Fox and Wolf used the same pattern previously corrected for Cat: EntityManager classified a safe departure as CLEAN_PASS, then each species' ordinary pass callback could switch into its relationship spare/stand-down state and record a spare. After that callback EntityManager still incremented clean-pass statistics, persisted pass history, emitted generic pass presentation and could stage a clean-pass Orb. Journal/history therefore counted one relationship stand-down as both pass and spare.

## Correction

Move only the existing spare threshold branches to Entity.resolveSpecialSafeDeparture:
- Fox: five Mercy Hearts, existing 120-point / 2-Seed relationship-tuned reward and SPARED state.
- Wolf: eight Mercy Hearts, existing relationship/history-tuned reward, particles and SPARED state.

The shared manager already treats a claimed special safe departure as exclusive of ordinary clean-pass accounting/cue/orb. Their technical terminal lifecycle remains the canonical CLEAN_PASS bucket because SPARED is a relationship subresolution rather than a sixth EncounterOutcome. Below threshold, Fox mirror-pass and Wolf charge-pass behavior is unchanged.

The integration regression runs the real EntityManager with both concrete species and verifies exactly one encounter + one spare, zero pass history/clean-pass count/generic pass Orb, preserved positive reward, and no duplicate resolution.

## Boundary

This establishes source/statistical outcome exclusivity. Hardware acceptance of Fox motion, Wolf howl/charge/stand-down readability, art and timing remains separate.
