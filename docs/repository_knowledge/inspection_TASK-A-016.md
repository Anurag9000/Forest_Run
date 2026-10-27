# TASK-A-016 — Product, architecture, evidence, and release documents

**Snapshot:** `eeccc677107f69039dbf71053c18a9038d4b5ff6` plus dirty worktree, 2026-10-27. **Disposition:** all 54 assigned paths accounted for; 52 textual artifacts read fully, two graphics inspected through safe file metadata/digests only. All assigned paths were clean against baseline HEAD. No script, workflow, test, build, device, or external operation ran.

## Reuse boundary

`REPO_MEMORY_MAP.md:138-181` and `REPO_CODEBASE_MAP.md` are reusable navigation/source evidence where they identify current owners/call paths. `AUDIT_LEDGER.md:1-5` explicitly frames entries as historical; its latest reconciliation is 2026-10-02, so later/current status claims must be delta checked. `DELTA_REVIEW.md:16-22` describes this boundary. The assigned file-by-file spans are recorded in the A-016 handoff in the parent result: 48 Markdown documents, three text projections, one curation JSON, and two PNGs.

## Current-intent requirements and architecture

`ARCHITECTURE.md:1-373` and `GAME_DESIGN.md:1-312` agree on native landscape game ownership, five biomes, 19 encounter families, deterministic/fair outcome ordering, Seeds/Bloom, persistent Garden and Journal, and candidate-bound release evidence. `ACCESSIBILITY.md:1-87`, `FOREST_JOURNAL.md:1-223`, and `RECOVERY_USER_EXPERIENCE.md:1-89` specify product-facing interfaces while retaining human/device acceptance as separate gates. The admission/method documents describe concrete contracts for pointer and frame input, parallax/Bloom, familiarity, arithmetic, encounters and deterministic scenario evidence. Ghost namespace, persistence, promotion, recovery and maintenance docs define bounded recovery contracts; device/human/performance/latency/installed-candidate/release/store docs are protocols and do not prove those operations ran.

Rights, creative approval, provenance, dependency/SBOM, security/vulnerability response, privacy policy, signing, store declarations and delivery remain candidate-bound external/accountable evidence requirements, not capabilities proven by validators alone. `REMEDIATION_CONTINUATION_2026-08-01.md` is historical.

## Documentation-to-source status contradictions

Several old tranche status paragraphs are stale relative to later canonical architecture and closure evidence:

- `NONTERMINAL_COLLISION_OUTCOMES.md:179-185` and `TERMINAL_HIT_COMPLETION.md:240-246` still frame dispatcher/effects extraction and recovery UI as unfinished; current `ARCHITECTURE.md:146-174,315-324` and the Aug-09 closure crosswalk record the bounded dispatcher/adapters/session/facade/catalogue/recovery UI as closed. Treat the old paragraphs as historical status, not active work.
- `GHOST_PROMOTION_RECOVERY.md:381-390`, `RUN_OUTCOME_PERSISTENCE.md:309-319` and `RUN_OUTCOME_RECOVERY.md:358-367` frame remediation as debug-only or deny namespace switching. This conflicts with ordinary-player recovery UI in `RECOVERY_USER_EXPERIENCE.md:9-38,74-89` and supported switch-after-capture in `GHOST_PERSISTENCE_NAMESPACES.md:263-298`, `GHOST_PROMOTION_RECOVERY.md:191-203,325-339`, and `RECOVERY_EVIDENCE_MAINTENANCE.md:77-102`. Retain the narrower live in-flight run/shared-store constraints; the blanket statements are stale.
- `AUDIT_LEDGER.md` is historical below its recorded reconciliation; do not use older remaining-work bullets as present backlog absent fresh evidence.

These are documentation-status discrepancies, not reasons to reopen completed source tasks. Update only in a later documentation-maintenance scope; this investigation did not modify pre-existing docs.

## Release artifact snapshot

The assigned release subtree contains feature graphic, promo square, three locale text projections, screenshot capture plan and curation manifest. It contains no graphics/metadata manifest, raw/final screenshot payload, or curated set. The code map explicitly says screenshot validation fails closed when `screenshots/raw/` is absent. The PNGs are RGBA, 1024×500 (149,197 bytes; SHA-256 `b229ec21108ceaaa046289ecae72c41cedf4deba04d201b8150bc00d225e7a73`) and 512×512 (60,925 bytes; SHA-256 `4eead39a34ce5dfe26586a58a652d82089d64d75543d7e90e2107817964a5640`). Dimensions/hashes do not establish candidate-bound generation or approval. The three en-US text projections match the fenced canonical copy in `STORE_LISTING.md:1-193`; without metadata manifest they are not frozen candidate evidence. The 68-line curation JSON describes eight planned scenarios, not captures. External gates remain open under the listed device/human/security/licensing/dependency contracts.

No application-source task is asserted from the missing release evidence. Any later release execution requires a selected candidate and authorized accountable owners.
