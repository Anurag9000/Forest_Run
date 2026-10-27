# Historical audit reconciliation — TASK-A-013

## Scope and authority

This report reconciles the 47 dated audit documents assigned under `TASK-A-013` against the existing repository maps. The assigned documents contain 6,476 lines in the inspected worktree. Their full current-file spans are recorded below so another reviewer can locate the source without treating their historical assertions as current validation.

The repository snapshot used by the surrounding investigation is HEAD `eeccc677107f69039dbf71053c18a9038d4b5ff6` plus a pre-existing dirty worktree. This report is a documentation-only historical reconciliation: no application workflow, test, build, benchmark, device run, or source-edit action was performed. Validation and test claims quoted or summarized from dated audits remain claims about those audits' own historical sessions.

## Reuse and supersession

- `docs/REPO_MEMORY_MAP.md:185-242` is the existing navigation ledger for this batch. It enumerates the audit paths, line counts, and section headings; this report does not duplicate its per-document topical summaries.
- `docs/audits/2026-08-06_documentation_reconciliation_audit.md:465-477` explicitly identifies early "remaining" items later closed and states that its reconciled current-truth sections supersede older present-tense status statements while retaining dated audits as provenance.
- `docs/repository_knowledge/HISTORICAL_ARCHITECTURE_RECONCILIATION.md` provides the source-level status crosswalk for Aug-06 architectural debt: `GOAL-008`–`GOAL-013` / `REQ-011`–`REQ-016` / `CAP-009`–`CAP-014` are recorded as closed in bounded forms in the Aug-09 audit; `GOAL-014` / `REQ-017` remains optional/unselected. It also bounds Sep-20 and Sep-27 records as historical evidence, not current runtime validation.
- `docs/audits/2026-08-09_source_completion_and_remaining_gates.md` is the later closure record for the six Aug-06 source-addressable architecture items and source-level accessibility; `docs/audits/2026-09-20_whole_software_closure.md` is the later whole-software/source checkpoint. These later records supersede their earlier status only within their stated source/revision boundaries.
- `docs/repository_knowledge/DELTA_REVIEW.md` classifies the memory/codebase maps as reusable with delta review and the older audit ledger as partial historical evidence. This reconciliation does not promote historic source or test statements to current-worktree/runtime evidence.

## Distinct chronology and status changes

| Period | Historical change or status transition | Evidence and limit |
|---|---|---|
| 2026-08-01 | Post-merge audit sets out the product mission and records a broad closed-core claim, a bounded source-debt list, candidate acceptance infrastructure, and external physical/signing/store/creative gates. Its baseline is `0c767fe9037bf949751c385b898b6af87a00332e`; PR #2 is described as 535 commits. | `2026-08-01_post_merge_audit.md:3-8,10-32,33-57,65-108,128-141`. Historical snapshot; not the current checkout. |
| 2026-08-02 | A sequence of focused changes narrows runtime debt: public GameView/parallax frame admission and menu pointer admission; saturating/elapsed return arithmetic and route-counter parity; familiarity scoring integration; exactly-once terminal summary persistence and a separate recoverable run-outcome journal; typed HIT and nonterminal outcome sequencing; ghost promotion recovery and maintenance; deterministic trace/fairness and fail-closed aggregate publication. The separate audits repeatedly distinguish source/focused local evidence from exact-head Android/device/release acceptance. | Representative boundaries: `2026-08-02_game_view_frame_input_audit.md:7-35,48-70`; `2026-08-02_return_moment_arithmetic_audit.md:9-47`; `2026-08-02_ghost_atomic_recovery_audit.md:15-40,64-84`; `2026-08-02_terminal_run_persistence_audit.md:7-69,93-127`; `2026-08-02_terminal_hit_completion_audit.md:7-40,125-142`; `2026-08-02_nonterminal_collision_outcome_audit.md:7-35,108-146`; `2026-08-02_trace_fairness_aggregation_audit.md:16-20,148-183`; `2026-08-02_aggregate_publication_audit.md:195-239`. Several Aug-02 documents give date/branch but no complete source revision, so do not attach them to a fabricated commit. |
| 2026-08-03 | Stale early claims are corrected: familiarity warmth was already wired to an additive scorer; the return arithmetic helpers are reconciled against production and the former call-site debt is closed. Ghost durability evolves from transient receipt to persistent manifest, immutable namespace binding, and versioned SHA-256 identity while preserving frame format v2 and legacy v1 sidecar reads. Terminal HIT's seven immediate effects are extracted, while the complete dispatcher remains open at this date. | `2026-08-03_familiarity_warmth_reconciliation.md:3-25,126-168`; `2026-08-03_return_moment_arithmetic_reconciliation.md:3-15,47-105,204-223`; `2026-08-03_ghost_artifact_manifest_audit.md:3-21,56-73,132-191,350-361`; `2026-08-03_sha256_ghost_identity_audit.md:3-18,84-160,329-354`; `2026-08-03_terminal_hit_impact_extraction_audit.md:61-127,301-319`. The namespace audit records starting head `2ab6a5af0272a6880b05fa7cc69ffd9dd3e3cb4d`; the warmth audit records `3484050e47e7998cc0cb69e59a490a9d24e62be5`; not every Aug-03 note declares a head. |
| 2026-08-04 | Non-finite Bloom presentation input is normalized/fails closed at profile, public state, and draw boundaries. Recovery maintenance captures one immutable persistence namespace for its handlers. | `2026-08-04_parallax_bloom_admission.md:3-34,174-189`; `2026-08-04_recovery_maintenance_namespace_binding.md:3-40,162-181`. The latter states starting head `a910afe5ecfc12b4965c4cb8b1955ffb49d4257e`; local focused evidence is not exact-head Android/device evidence. |
| 2026-08-05 | Recovery admission becomes namespace-scoped; the next tranche then replaces global queued-write serialization with per-namespace serial scheduling and bounded concurrency only across disjoint namespaces. Same-namespace writes remain serial. This supersedes the earlier Aug-05 audit's global-serial limitation, not its same-namespace ordering constraints. | `2026-08-05_ghost_namespace_recovery_admission.md:13-65,259-280`; `2026-08-05_ghost_namespace_serial_scheduling.md:13-73,152-164`. Starting heads are `d7dd25d38fe46e8bef3f213c2a9eaedd243ee29f` and `f28db821c3bd8b25855818890ed66edbf0590681`, respectively. Focused harness claims do not substitute for the explicitly unclaimed Android/device checks. |
| 2026-08-06 | Two audits broaden the mission and distinguish source-ready status from release readiness. The architecture/release audit identifies source evidence-index hardening, workflow/dependency authority, privacy contract, and external licensing/vulnerability/reporting/release-note gaps; its collision-dispatcher integration remains open then. It records exact-head workflow run `31105597864` for validated head `cc24e86784bd0d6c53114c55e37ff447e1b55bc5`. The exhaustive audit records source completion claims and separates bounded architectural debt, optional product choices, and external gates. | `2026-08-06_architecture_release_integrity_privacy_audit.md:3-8,26-70,314-357`; `2026-08-06_exact_head_audit.md:19-24,187-249,270-314,383-410`. These are historical candidate/revision claims only. Later Aug-09 source-closure evidence resolves the six architecture items; the historical crosswalk above is authoritative for that disposition. |
| 2026-08-15 | Product-completion sweep adds a first-class observational Forest Journal and derived projections, strengthens read-only semantics, canonicalizes Garden catalogue ownership, and records accessibility/identity/product work. A continuation repairs a stale connected accessibility assertion, makes Journal filter state ephemeral, closes Garden feedback/catalogue and semantic-haptic seams, and hardens candidate-bound graphics/release preparation. The continuation says no known source-addressable item remains in its bounded queue, but exact-head validation was pending for its document-inclusive candidate and physical/human/store/legal gates remained open. | `2026-08-15_product_completion_sweep.md:3-19,21-82,100-149,216-248`; `2026-08-15_product_completion_continuation.md:5-18,20-88,102-161`. The continuation records host/release success and a connected failure on an earlier SHA, then explicitly does not claim final candidate green. |
| 2026-09-26 | Fourteen focused records address source-level discrepancies rather than adding product goals: exact JSON revision types; atomic Seed awarding versus Garden purchase; authored scenario population; Bloom entity identity; canonical release artifact identity; Chickadee safe-pocket geometry; HIT-first checks across Dog/Tit/Chickadee multi-hitboxes; mercy resolution only after safe passage; Eagle staging; Owl visibility admission; raw-PNG structural validation; strict external manifest intake; strict release summary identity/types; and same-frame scripted input dispatch. | The 14 assigned paths are individually listed below. They generally add source corrections and regression specifications while reserving exact-head workflow, physical readability, human acceptance, signing, and delivery as separate evidence. `2026-09-26_chickadee_pocket_safety.md:3-17` uniquely reports a prior exact-head host failure (1/1112 at `5ac06f84`) and says the exact resulting commit's workflow remains authoritative. `2026-09-26_release_summary_integrity.md:3-19` gives baseline `bcf88c749917161cedb709405fda1612cd72c439` and scope of 813 tracked files. |

### Reusable corrections and unresolved boundaries

- The Aug-02 ghost convenience-method inconsistency, return arithmetic, input boundaries, and other bounded source issues should not be recreated as open tasks where the later closure records cited above cover them.
- The Aug-06 collision-dispatcher, private effect adapters, run-session coordinator, persistence facade, structural catalogue, and ordinary-player recovery UI are closed in bounded source forms in Aug-09, per the existing architecture reconciliation. Broader data-driven authoring remains optional and unselected. Do not revive these as current implementation debt without new source evidence.
- The docs support a persistent distinction between implemented/source-integrated mechanisms and external release acceptance. Physical device/performance/human accessibility and creative acceptance, production signing and internal-store delivery, rights/licensing, privacy hosting, and policy decisions are not completed by validators, source contracts, or focused test reports.
- The dated audits do not establish current behavior at the investigation HEAD. Revision metadata is uneven; where an audit lacks an explicit commit, its filename date is not a substitute for a revision identifier. Current dirty changes and runtime status require their own evidence.

## Exact assigned-document spans

The ranges below cover the complete current contents of each assigned file. File count: **47**. Total assigned lines: **6,476**.

### 2026-08-01 — 1 document

| Path | Lines |
|---|---:|
| `docs/audits/2026-08-01_post_merge_audit.md` | 1–141 |

### 2026-08-02 — 17 documents

| Path | Lines |
|---|---:|
| `docs/audits/2026-08-02_aggregate_publication_audit.md` | 1–239 |
| `docs/audits/2026-08-02_exact_aggregate_source_binding_audit.md` | 1–162 |
| `docs/audits/2026-08-02_game_view_frame_input_audit.md` | 1–72 |
| `docs/audits/2026-08-02_ghost_atomic_recovery_audit.md` | 1–96 |
| `docs/audits/2026-08-02_ghost_promotion_recovery_audit.md` | 1–337 |
| `docs/audits/2026-08-02_ghost_recovery_compile_evidence.md` | 1–39 |
| `docs/audits/2026-08-02_nonterminal_collision_outcome_audit.md` | 1–146 |
| `docs/audits/2026-08-02_parallax_frame_input_audit.md` | 1–56 |
| `docs/audits/2026-08-02_pointer_input_admission_audit.md` | 1–49 |
| `docs/audits/2026-08-02_recovery_evidence_maintenance_audit.md` | 1–309 |
| `docs/audits/2026-08-02_relationship_familiarity_scoring_audit.md` | 1–49 |
| `docs/audits/2026-08-02_return_moment_arithmetic_audit.md` | 1–56 |
| `docs/audits/2026-08-02_route_counter_ceiling_audit.md` | 1–91 |
| `docs/audits/2026-08-02_run_outcome_recovery_audit.md` | 1–218 |
| `docs/audits/2026-08-02_terminal_hit_completion_audit.md` | 1–142 |
| `docs/audits/2026-08-02_terminal_run_persistence_audit.md` | 1–127 |
| `docs/audits/2026-08-02_trace_fairness_aggregation_audit.md` | 1–183 |

### 2026-08-03 — 7 documents

| Path | Lines |
|---|---:|
| `docs/audits/2026-08-03_familiarity_warmth_reconciliation.md` | 1–168 |
| `docs/audits/2026-08-03_ghost_artifact_manifest_audit.md` | 1–361 |
| `docs/audits/2026-08-03_ghost_manifest_receipt_abandonment_addendum.md` | 1–87 |
| `docs/audits/2026-08-03_ghost_persistence_namespace_isolation.md` | 1–227 |
| `docs/audits/2026-08-03_return_moment_arithmetic_reconciliation.md` | 1–223 |
| `docs/audits/2026-08-03_sha256_ghost_identity_audit.md` | 1–354 |
| `docs/audits/2026-08-03_terminal_hit_impact_extraction_audit.md` | 1–319 |

### 2026-08-04 — 2 documents

| Path | Lines |
|---|---:|
| `docs/audits/2026-08-04_parallax_bloom_admission.md` | 1–189 |
| `docs/audits/2026-08-04_recovery_maintenance_namespace_binding.md` | 1–181 |

### 2026-08-05 — 2 documents

| Path | Lines |
|---|---:|
| `docs/audits/2026-08-05_ghost_namespace_recovery_admission.md` | 1–280 |
| `docs/audits/2026-08-05_ghost_namespace_serial_scheduling.md` | 1–164 |

### 2026-08-06 — 2 documents

| Path | Lines |
|---|---:|
| `docs/audits/2026-08-06_architecture_release_integrity_privacy_audit.md` | 1–367 |
| `docs/audits/2026-08-06_exact_head_audit.md` | 1–411 |

### 2026-08-15 — 2 documents

| Path | Lines |
|---|---:|
| `docs/audits/2026-08-15_product_completion_continuation.md` | 1–161 |
| `docs/audits/2026-08-15_product_completion_sweep.md` | 1–250 |

### 2026-09-26 — 14 documents

| Path | Lines |
|---|---:|
| `docs/audits/2026-09-26_all_schema_revision_types.md` | 1–22 |
| `docs/audits/2026-09-26_atomic_seed_awards.md` | 1–15 |
| `docs/audits/2026-09-26_authored_scenario_population.md` | 1–13 |
| `docs/audits/2026-09-26_bloom_entity_identity.md` | 1–15 |
| `docs/audits/2026-09-26_canonical_release_artifact_identity.md` | 1–19 |
| `docs/audits/2026-09-26_chickadee_pocket_safety.md` | 1–17 |
| `docs/audits/2026-09-26_compound_encounter_collision_order.md` | 1–22 |
| `docs/audits/2026-09-26_deferred_mercy_resolution.md` | 1–13 |
| `docs/audits/2026-09-26_eagle_authored_staging.md` | 1–17 |
| `docs/audits/2026-09-26_owl_visibility_gate.md` | 1–7 |
| `docs/audits/2026-09-26_raw_png_integrity.md` | 1–15 |
| `docs/audits/2026-09-26_release_manifest_strict_intake.md` | 1–15 |
| `docs/audits/2026-09-26_release_summary_integrity.md` | 1–19 |
| `docs/audits/2026-09-26_same_tick_scripted_input.md` | 1–13 |

## Reconciliation conclusion

This batch contributes chronology and source-reported status transitions, not new project goals. Use `REPO_MEMORY_MAP.md` for the document navigation index, the Aug-06 documentation and architecture reconciliations for supersession, and the newer whole-software closure/current delta records for later status. Do not rerun historical checks or interpret prior focused execution as evidence for the current dirty worktree. No source, shared ledger, or application file was changed by this task.
