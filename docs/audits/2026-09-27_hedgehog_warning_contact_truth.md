# Forest Run — Hedgehog contact versus avoided-mercy contract (2026-09-27)

## Source-derived defect

The authoritative encounter definition requires Hedgehog body contact to be a nonlethal STUMBLE/debuff, while MERCY denotes passing dangerously close without touching. The live Hedgehog previously returned MERCY_MISS for physical body overlap if its warning had begun but the 0.18-second warning lead had not expired. After the new deferred-mercy manager, this physical contact could be recorded provisionally and later awarded a Heart on passage. Its own mercy selection contained an old branch for a body overlap that was now unreachable when final selection occurs after passage.

## Repair

Keep the existing warning/armed telegraph and speed-debuff mechanics. The physical hitbox always returns STUMBLE regardless of warning phase. Only the expanded padding outside the body emits a provisional MERCY_MISS, which EntityManager finalizes after completed safe passage; an intervening body hit supersedes it. Remove unreachable body-contact mercy dialogue and retain the ordinary "Eep!" near-miss cue.

Robolectric tests assert warning-window and post-warning body STUMBLE, exactly-once debuff/no later ordinary rewards, untouched padded-band deferral and eventual single Heart, and padded approach followed by body contact. Existing clear-read pass reward remains unchanged.

This is a specific contract correction, not a claim that every mixed encounter sequence is physically fair or that production sprites/creative/legal approval is complete. Require exact-head host and connected CI before marking automated closure.
