# Forest Run — Cat pass/spare branch exclusivity (2026-09-28)

## Reproduced contract mismatch

The current game design says Cat hit, mercy, ordinary pass, spare/reward and exit branches must be mutually coherent. Live source resolved a safe Cat as technical CLEAN_PASS, ran the ordinary pass reward and pass dialogue, then—when Mercy Hearts were at least five—also switched the same instance into waving spare state, persisted a spare and incremented the run's spare counter. EntityManager then additionally incremented clean-pass statistics, recorded pass history, emitted generic pass presentation and could stage a clean-pass Seed Orb. One encounter therefore surfaced as both an ordinary pass and a spare/reward.

## Correction

Entity now exposes a narrow `resolveSpecialSafeDeparture` hook. A true result means the entity has claimed an exclusive relationship-specific safe departure, so EntityManager keeps the canonical technical CLEAN_PASS lifecycle bucket/encounter-count ownership but skips ordinary clean-pass statistics, pass history/cue and optional pass Orb.

Cat uses this hook only at its existing five-Mercy-Heart spare threshold. The same kindness reward value is retained, followed by the existing wave/spare persistence and `recordSpare`; the ordinary PASS dialogue is not emitted. Below the threshold Cat follows its unchanged ordinary pass path. No sixth terminal EncounterOutcome is invented.

A Robolectric integration regression drives the real EntityManager/Cat path and proves one encounter + one spare, zero ordinary pass history/count, zero generic clean-pass Orb, positive Cat reward, and exactly-once repeated collision checks.

## Boundary

This fixes source/stat/presentation exclusivity. It does not substitute for human judgment of Cat's wave animation, relationship tone, art, haptics, or device readability.
