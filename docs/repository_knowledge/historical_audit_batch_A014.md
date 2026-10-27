# Historical audit batch A-014

## Scope and evidence boundary

This note reconciles the 47 dated audit documents assigned in TASK-A-014. Each listed document was read in full at repository worktree `main`, with baseline `eeccc677107f69039dbf71053c18a9038d4b5ff6`. Audit filename/title dates range from 2026-09-26 through 2026-09-28. Their claims describe the source and evidence available at each historical point; they are not, by themselves, current-HEAD runtime validation.

Most audits describe a correction and regression coverage but do not identify a resulting source commit or a completed workflow result. Where they require exact-head CI or device acceptance, that remains a stated evidence condition, not a passed check. The current investigation performed no workflow, test, script, build, app, or device execution and made no source changes.

## Unique historical developments to retain

These are deltas and refinements that help preserve the history without repeating the detailed current reconciliations:

1. **Spawn evidence narrowed and refined.** The 2026-09-27 spawn audit distinguishes declared origin-gap arithmetic from action feasibility. On 2026-09-28 the random-spawn action lead was successively made to account for same-frame entity motion, fastest immediate approach speed across the full reaction window, and a future return from a temporary speed debuff. The resulting envelope is still a source-side model, not measured human reaction time or physical-device fairness evidence. See `2026-09-27_spawn_fairness_evidence_boundary.md` lines 3–13; `2026-09-28_compact_random_spawn_action_lead.md` lines 3–15; `2026-09-28_full_window_fastest_random_approach.md` lines 3–13; and `2026-09-28_debuff_expiry_random_spawn_lead.md` lines 3–13.
2. **Swept collision scope became component-specific.** The audits add same-time swept contact for Bamboo's physical stalk components, Dog's bark projectile and hazardous body, and individual Chickadee/Tit flock birds while keeping aggregate tunnels, guides, and safe pockets non-solid. This establishes only the named sampled-contact paths, not universal multipart collision coverage. See `2026-09-28_bamboo_component_swept_contact.md` lines 1–7; `2026-09-28_bark_projectile_same_time_contact.md` lines 3–11; and `2026-09-28_flown_component_swept_contact.md` lines 3–11.
3. **Safe relationship departures became exclusive.** Cat's special safe departure was separated from ordinary pass accounting; Fox and Wolf were moved onto the same exclusive seam. A later test-only correction removed an obsolete expectation that a persistent spare also increments ordinary pass history. Technical CLEAN_PASS remains the lifecycle bucket, while spare is the relationship subresolution. See `2026-09-28_cat_safe_departure_exclusivity.md` lines 3–17; `2026-09-28_fox_wolf_safe_departure_exclusivity.md` lines 3–19; and `2026-09-28_exclusive_spare_debug_persistence_test.md` lines 1–5.
4. **Durable record ownership was refined in stages.** The sequence proceeds from monotonic score/distance records, to reconciling terminal high-score previews against the canonical record, to strict ownership of an equal-score record by the run that published that exact value. Best run distance was separately stored from the legacy best-Ghost promotion floor so a pause checkpoint could not suppress later Ghost promotion. These are process-local preference semantics, not cloud or multi-device consensus. See `2026-09-27_monotonic_lifetime_records.md` lines 3–15; `2026-09-27_terminal_high_score_truth.md` lines 3–11; `2026-09-28_high_score_tie_ownership.md` lines 3–15; `2026-09-28_exact_high_score_record_ownership.md` lines 3–19; and `2026-09-28_independent_best_distance_memory.md` lines 3–21.
5. **Reachability claims were tied to real Player geometry and sampled movement.** The audit sequence replaces point-in-time or hand-sized fixture claims with bounded full traversal, reachable Bamboo gaps, marked Orchid threads, actual Duck/tree stance geometry, and a full-body Chickadee pocket. Action feasibility was corrected from a continuous jump approximation to the admitted discrete update law (495px sampled rise versus the earlier 540px continuous estimate); malformed feasibility inputs are validated independently. These bounded models do not prove arbitrary encounter-pair fairness, human perception, or device acceptance. See the bamboo, Orchid, Duck/tree, Chickadee, and action-feasibility records in the inventory below.
6. **Compact-device and timing edge cases were made explicit.** Impossible authored Bamboo geometry is skipped without substituting another species; a compact Buddy Dog departure is retained until normal pass resolution; and random spawn lead accounts for compact-screen approach. Input stance hitboxes are published before the next frame captures prior collision state, while terminal Rest completion exits the current update before post-terminal mutation. These are scoped corrections, not global guarantees.

## Historical exact-head failures and their limits

The following documents report specific earlier validation failures and corrections. Preserve each result at its cited revision; do not transfer it to the current baseline or recast it as a production failure where the document says the defect was in a test fixture.

| Audit | Historical evidence and documented correction |
|---|---|
| [`2026-09-27_flavor_text_test_isolation.md`](../audits/2026-09-27_flavor_text_test_isolation.md#L3) | Lines 3–11: Android run `36311697173`, SHA `5c79ca07`, compiled tests and ran 1,139 JVM tests; one test failed because a singleton was cleared only in `@After`. The audit describes adding `@Before` cleanup. |
| [`2026-09-27_tree_true_mercy_test_geometry.md`](../audits/2026-09-27_tree_true_mercy_test_geometry.md#L3) | Lines 3–11: Android run `36334229005`, SHA `5e3c5282`, ran 1,156 JVM tests; two near-miss fixtures crossed the solid trunk. The audit describes correcting fixtures and adding a distinct trunk-HIT assertion without weakening production geometry. |
| [`2026-09-28_factory_catalogue_source_scope.md`](../audits/2026-09-28_factory_catalogue_source_scope.md#L1) | Lines 1–5: host validation run `36347167727` failed a whole-file lexical count after valid helper mentions were added; the source contract was narrowed to the constructor `when` body. |
| [`2026-09-28_exclusive_spare_debug_persistence_test.md`](../audits/2026-09-28_exclusive_spare_debug_persistence_test.md#L1) | Lines 1–5: run `36385936580` exposed an obsolete test expectation for ordinary pass history after a persistent spare; production code was unchanged by this correction. |
| [`2026-09-28_frame_cadence_timing.md`](../audits/2026-09-28_frame_cadence_timing.md#L13) | Lines 13–17: the audit records a stale Python source-contract assertion that expected a removed frame-count constant after elapsed-time cadence was introduced. It gives no resulting workflow SHA or pass result. |

The audits generally state that exact-resulting-head host/JVM, Android, connected-device, packaging, or other checks are required. They do not provide a resulting commit or completed workflow for most entries. No current-HEAD verification conclusion is made here.

## Prior reconciliation to reuse

The dated audit index and the broader reconciliations remain authoritative for their respective scopes; this note supplements them rather than copying their summaries:

- [`REPO_MEMORY_MAP.md`](../REPO_MEMORY_MAP.md) lines 235–282 indexes the scoped 2026-09-26 through 2026-09-28 audits by title and path.
- [`AUDIT_LEDGER.md`](../AUDIT_LEDGER.md) lines 23–33 covers the 2026-09-28 compact Bamboo limitation and closure boundary.
- [`AUDIT_LEDGER.md`](../AUDIT_LEDGER.md) lines 37–65 covers the 2026-09-27 late encounter/collision checkpoint, the tree test-fixture failure, spawn-evidence limits, and open acceptance gates.
- [`AUDIT_LEDGER.md`](../AUDIT_LEDGER.md) lines 69–91 covers the 2026-09-26 continuation, including strict evidence intake, deterministic authored behavior, posted-frame telemetry, and external acceptance limits.

## Audited document inventory

The line ranges below cover each document body read in full. Paths are repository-relative.

### 2026-09-26 — 8 documents

| Path | Lines |
|---|---:|
| `docs/audits/2026-09-26_screenshot_evidence_integrity.md` | 1–17 |
| `docs/audits/2026-09-26_scripted_hold_duration.md` | 1–11 |
| `docs/audits/2026-09-26_species_sound_wiring.md` | 1–7 |
| `docs/audits/2026-09-26_species_spare_persistence.md` | 1–7 |
| `docs/audits/2026-09-26_step_seeded_encounter_randomness.md` | 1–13 |
| `docs/audits/2026-09-26_strict_schema_version_types.md` | 1–13 |
| `docs/audits/2026-09-26_surface_post_telemetry.md` | 1–13 |
| `docs/audits/2026-09-26_terminal_frame_exclusivity.md` | 1–13 |

### 2026-09-27 — 18 documents

| Path | Lines |
|---|---:|
| `docs/audits/2026-09-27_bamboo_full_traversal.md` | 1–13 |
| `docs/audits/2026-09-27_bamboo_vertical_reachability.md` | 1–15 |
| `docs/audits/2026-09-27_eagle_escape_lifecycle.md` | 1–15 |
| `docs/audits/2026-09-27_flavor_text_test_isolation.md` | 1–11 |
| `docs/audits/2026-09-27_frame_movement_speed_consistency.md` | 1–15 |
| `docs/audits/2026-09-27_hedgehog_warning_contact_truth.md` | 1–13 |
| `docs/audits/2026-09-27_hyacinth_brush_outcome.md` | 1–15 |
| `docs/audits/2026-09-27_monotonic_lifetime_records.md` | 1–15 |
| `docs/audits/2026-09-27_orchid_true_thread_reachability.md` | 1–15 |
| `docs/audits/2026-09-27_owl_eagle_escape_lifecycle.md` | 1–15 |
| `docs/audits/2026-09-27_pure_all_species_collision_queries.md` | 1–11 |
| `docs/audits/2026-09-27_real_player_duck_flight_lane.md` | 1–15 |
| `docs/audits/2026-09-27_real_player_duck_lanes.md` | 1–11 |
| `docs/audits/2026-09-27_spawn_fairness_evidence_boundary.md` | 1–13 |
| `docs/audits/2026-09-27_surface_geometry_ownership.md` | 1–11 |
| `docs/audits/2026-09-27_terminal_high_score_truth.md` | 1–11 |
| `docs/audits/2026-09-27_tree_full_duck_traversal.md` | 1–11 |
| `docs/audits/2026-09-27_tree_true_mercy_test_geometry.md` | 1–11 |

### 2026-09-28 — 21 documents

| Path | Lines |
|---|---:|
| `docs/audits/2026-09-28_bamboo_component_swept_contact.md` | 1–7 |
| `docs/audits/2026-09-28_bark_projectile_same_time_contact.md` | 1–11 |
| `docs/audits/2026-09-28_cat_safe_departure_exclusivity.md` | 1–17 |
| `docs/audits/2026-09-28_chickadee_full_body_pocket.md` | 1–11 |
| `docs/audits/2026-09-28_compact_bamboo_admission.md` | 1–11 |
| `docs/audits/2026-09-28_compact_buddy_departure.md` | 1–13 |
| `docs/audits/2026-09-28_compact_random_spawn_action_lead.md` | 1–15 |
| `docs/audits/2026-09-28_debuff_expiry_random_spawn_lead.md` | 1–13 |
| `docs/audits/2026-09-28_discrete_player_action_feasibility.md` | 1–13 |
| `docs/audits/2026-09-28_exact_high_score_record_ownership.md` | 1–19 |
| `docs/audits/2026-09-28_exclusive_spare_debug_persistence_test.md` | 1–5 |
| `docs/audits/2026-09-28_factory_catalogue_source_scope.md` | 1–5 |
| `docs/audits/2026-09-28_flown_component_swept_contact.md` | 1–11 |
| `docs/audits/2026-09-28_fox_wolf_safe_departure_exclusivity.md` | 1–19 |
| `docs/audits/2026-09-28_frame_cadence_timing.md` | 1–17 |
| `docs/audits/2026-09-28_full_window_fastest_random_approach.md` | 1–13 |
| `docs/audits/2026-09-28_hedgehog_speed_aware_warning_lead.md` | 1–15 |
| `docs/audits/2026-09-28_high_score_tie_ownership.md` | 1–15 |
| `docs/audits/2026-09-28_immediate_stance_collision_geometry.md` | 1–11 |
| `docs/audits/2026-09-28_independent_best_distance_memory.md` | 1–21 |
| `docs/audits/2026-09-28_independent_feasibility_admission.md` | 1–11 |

**Coverage count:** 8 + 18 + 21 = 47 audit documents. This count is this batch's document-read coverage, not a claim that all repository history or current implementation was revalidated.
