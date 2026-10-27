# Historical audit reconciliation — TASK-A-015

## Scope and evidence boundary

This report records a read-only reconciliation of all 46 dated audit documents assigned by `TASK-A-015` in `docs/repository_knowledge/MASTER_EXECUTION_LEDGER.json`. Repository identity is Forest_Run; the task baseline is `eeccc677107f69039dbf71053c18a9038d4b5ff6`, with pre-existing dirty worktree state preserved. Each listed audit body was read in full. The 2026-10-03 `REPO_CODEBASE_MAP.md`, `REPO_MEMORY_MAP.md`, `DELTA_REVIEW.md`, `HISTORICAL_ARCHITECTURE_RECONCILIATION.md`, and `AUDIT_LEDGER.md` were reused as prior summaries, so this report focuses on unique chronology and qualifications rather than restating every audit as a new task.

This is historical documentation evidence. The audit reports themselves describe source corrections, tests, CI runs, or emulator observations at their stated times. Those claims were not independently re-executed or externally checked in this task. Where an audit supplies a SHA or workflow run, it remains tied to that candidate. Where it supplies no source revision, the claim has weaker revision provenance. Current behavior must be established from current source and current evidence, not inferred solely from a dated note.

## Distinct chronological findings

### 2026-09-28 — collision sampling, reachability, and ordinary spawn admission

The early swept-contact correction was intentionally scoped to unconditional primary bodies: narrow Cactus/Lily HIT and Hedgehog STUMBLE cases were repaired without broadening hitboxes or treating multipart encounters as aggregate solids. Follow-on records added per-band Orchid contact while preserving its safe thread, and expanded unconditional primary-core eligibility to additional species. They explicitly retained exclusions for multipart trees/flocks, staged Owl/Eagle attacks, and other conditional mechanics. Later audits add their own narrowly defined coverage (completed diving birds on 2026-09-29; Cherry trunk/branch components on 2026-10-02). This is cumulative, species-specific collision coverage, not a general continuous-collision guarantee.

Seed Orb audits established several distinct contracts: sampled rather than continuous jump-apex limits; speed-aware horizontal lead including bob/jitter/pickup-core geometry; rejection of placements through unresolved pending encounter corridors; and swept pickup when an admitted max-speed frame could cross the Orb. A later same-time moving Player/Orb correction replaced the stationary-Player assumption when valid Player motion samples exist. These changes preserve authored reward and core geometry according to the reports, but leave physical human reaction time, compact-screen discoverability, non-linear trajectory certification, and future/moving hazard baiting unresolved.

Ordinary random encounter admission was extended beyond world-distance spacing: wait for Player RUNNING/LANDING and no active persistence-owning PENDING encounter. The 2026-09-29 follow-up described an all-family, supported-height action-lead test including a conservative sampled jump envelope and post-creation-frame geometry. This is source/test-level timing evidence, not proof of human fairness, every deterministic scenario, or every compact-device presentation.

The Tit rhythm audit corrected the mismatch between a narrow drawn trough and the full airborne Player body, requiring full-body containment to earn the beat. The stable-root audit rationalized the quadratic rise-time calculation to avoid cancellation and rejected a sentinel being mistaken for a feasible finite rise. The RuntimeCadence note documents an epsilon-boundary modulo correction to prevent duplicate polls. These are isolated mathematical or presentation contracts, not broad behavior certification.

### 2026-09-28–10-02 — training-control applicability is N/A, not a missing ML project

Repeated audits agree Forest Run is a native Android game and does not retain an ML training pipeline. CUDA-first training, optimizer parity, model registries, model checkpoints, and ML datasets are therefore not applicable product requirements. The material evolution is hardening the repository-specific “no retained training surface” certificate: an empty or incomplete tree must not pass; canonical Android build/manifest/runtime anchors and retained dependencies are required; then source-type, dynamic-import, artifact, symlink, notebook, opaque-binary, dependency-configuration, and source/scope/authority-binding cases are progressively covered. This is a fail-closed applicability mechanism, not a training implementation.

The dated records report different candidate commits, test counts, scan counts, and CI runs (for example, 2026-09-28, 2026-09-29, 2026-10-01, and 2026-10-02 each name their own snapshots). Preserve each as historical, candidate-bound evidence; do not merge them into one current certificate. They do not close Android product correctness, physical-device, human, signing, store, legal, privacy, or release gates.

### 2026-10-01 — accessibility action ownership and state publication

The accessibility action path acquired explicit Player-stance admission for Jump/Long jump/Duck, with live handlers independently preflighting capability before callbacks. A separate correction adds targeted semantic-node content-change events when Jump or Duck availability changes, avoiding dependence on a service re-querying without notification. Delayed accessibility Duck release was also given identity-token ownership so an old delayed callback cannot release a later Duck stance or leak across a run/session transition. These are Android source ownership/discoverability contracts; TalkBack/Switch Access timing, focus behavior, and human/device acceptance remain open.

### 2026-10-02 — atomic progression, monotonic unlocks, and presentation ownership

Several audits address in-process races in SharedPreferences-backed state: derived counter increments, whole Forest Mood transforms, spare/hit memory event transitions, Return Moment tuple transforms, and relationship counter-snapshot/stage publication. Permanent history, memory-page, relationship-milestone, and costume unlock sets become monotonic unions; the legacy `CostumeStyle.NONE` sentinel is removed from canonical unlock storage. The reports explicitly scope concurrency guarantees to the single-process application; they do not establish multi-process transactions or a transaction across independent persistence stores.

Garden story selection is split into read-only preview versus progression-owning entry behavior: ordinary initialization/wardrobe refresh must not persist story-page unlocks, while actual Garden entry updates durable story context before deriving visible page count and sanctuary state. Return Moment selection and acknowledgement are moved to post-presentation ownership: preview remains non-mutating, the first rendered Garden frame acknowledges once, and one captured timestamp identifies the same local-day moment across midnight. A test-source correction obtains day identity through the public acknowledgement behavior rather than a private helper; it did not change production API or behavior.

A SurfaceView pixel resize not accompanied by a configuration callback is routed through coherent Activity recreation instead of mutating only reported dimensions while dimension-bound systems retain stale geometry. The recovery-launch source-contract correction merely adapts the test anchor to an evolved `GameView` constructor; its report says production launch order was unchanged.

### 2026-10-03 — platform and transient-feedback boundaries

The Android 16 large-screen note reports an API 36 emulator configuration where a fixed-landscape custom Canvas app appeared portrait/overlapped; the manifest categorized the app as a game and temporarily opted out of restricted-resizability enforcement, with game content shown letterboxed. It also records explicit modern and legacy backup exclusions across supported storage domains. This is emulator-specific historical evidence, not broad tablet/OEM behavior; the report itself notes the resizability opt-out is temporary and unavailable at target API 37.

The transient feedback audit extends lifecycle ownership from GameThread/music to active SoundPool effects and in-flight vibration: pause and audio-off stop currently tracked SFX; pause cancels haptics; resume intentionally restarts persistent music but not stale transient cues. The bounded SFX ledger reflects the lack of completion callbacks. Reported JVM/source-contract tests do not establish device mixer latency, OEM vibration behavior, loudness balance, or human quality.

## Reconciliation with prior maps

The 2026-10-03 `docs/AUDIT_LEDGER.md` already aggregates the broad gameplay and persistence status, including substantial implemented source corrections while leaving mixed-encounter fairness, physical-device/human acceptance, creative/rights approval, signing and Play delivery open (`AUDIT_LEDGER.md:13,33`). `docs/REPO_MEMORY_MAP.md` establishes that current source owns runtime truth, dated audit claims are historical, validators bind evidence rather than create truth, and ML training is N/A for the retained product (`REPO_MEMORY_MAP.md:3-8`). These documents are reusable summaries, not substitutes for each historical file or current source verification.

No novel product goal or independent backlog item was established solely by this batch. The surviving obligations are the already-recorded boundaries: exact-current-source reconciliation when behavior is in question; candidate-bound verification for later code changes; mixed-sequence and physical/human acceptance; release/creative/provenance gates; and reevaluation of the N/A training certificate if a real ML surface is later introduced.

## Complete read ledger

All 46 files below were fully read; ranges are inclusive and are relative to each file. The counts sum to 1,088 lines.

### 2026-09-28 (16 files)

| Path | Lines |
|---|---:|
| `docs/audits/2026-09-28_narrow_core_swept_contact.md` | 1–15 |
| `docs/audits/2026-09-28_orb_sampled_apex_test.md` | 1–7 |
| `docs/audits/2026-09-28_orchid_independent_band_swept_contact.md` | 1–9 |
| `docs/audits/2026-09-28_random_action_recovery_admission.md` | 1–19 |
| `docs/audits/2026-09-28_recovery_evidence_ghost_floor_fixture.md` | 1–18 |
| `docs/audits/2026-09-28_runtime_cadence_epsilon_remainder.md` | 1–11 |
| `docs/audits/2026-09-28_same_time_seed_orb_pickup.md` | 1–11 |
| `docs/audits/2026-09-28_seed_orb_approach_reachability.md` | 1–13 |
| `docs/audits/2026-09-28_seed_orb_clear_approach.md` | 1–13 |
| `docs/audits/2026-09-28_seed_orb_swept_pickup.md` | 1–15 |
| `docs/audits/2026-09-28_single_core_sweep_family_coverage.md` | 1–13 |
| `docs/audits/2026-09-28_stable_feasibility_root.md` | 1–11 |
| `docs/audits/2026-09-28_tit_full_body_trough.md` | 1–11 |
| `docs/audits/2026-09-28_training_applicability_fail_closed.md` | 1–26 |
| `docs/audits/2026-09-28_training_control_applicability.md` | 1–71 |
| `docs/audits/2026-09-28_training_control_no_ml_surface.md` | 1–59 |

### 2026-09-29 (7 files)

| Path | Lines |
|---|---:|
| `docs/audits/2026-09-29_all_family_random_action_lead.md` | 1–16 |
| `docs/audits/2026-09-29_completed_bird_escape_swept_contact.md` | 1–17 |
| `docs/audits/2026-09-29_hyacinth_brush_swept_contact.md` | 1–20 |
| `docs/audits/2026-09-29_opening_seeded_guidance_alignment.md` | 1–15 |
| `docs/audits/2026-09-29_training_control_and_opening_reset_validation.md` | 1–68 |
| `docs/audits/2026-09-29_training_control_applicability.md` | 1–106 |
| `docs/audits/2026-09-29_training_control_no_ml_closure.md` | 1–67 |

### 2026-10-01 (4 files)

| Path | Lines |
|---|---:|
| `docs/audits/2026-10-01_accessibility_action_admission.md` | 1–19 |
| `docs/audits/2026-10-01_accessibility_action_availability_invalidation.md` | 1–17 |
| `docs/audits/2026-10-01_accessibility_duck_release_ownership.md` | 1–21 |
| `docs/audits/2026-10-01_training_control_non_ml_closure.md` | 1–83 |

### 2026-10-02 (17 files)

| Path | Lines |
|---|---:|
| `docs/audits/2026-10-02_atomic_derived_progression_counters.md` | 1–15 |
| `docs/audits/2026-10-02_atomic_forest_mood_state.md` | 1–11 |
| `docs/audits/2026-10-02_atomic_memory_event_transitions.md` | 1–11 |
| `docs/audits/2026-10-02_atomic_return_moment_state.md` | 1–11 |
| `docs/audits/2026-10-02_cherry_component_swept_contact.md` | 1–15 |
| `docs/audits/2026-10-02_costume_none_sentinel_cleanup.md` | 1–5 |
| `docs/audits/2026-10-02_garden_story_entry_ownership.md` | 1–19 |
| `docs/audits/2026-10-02_monotonic_history_unlock_union.md` | 1–11 |
| `docs/audits/2026-10-02_monotonic_permanent_unlock_sets.md` | 1–13 |
| `docs/audits/2026-10-02_recovery_launch_contract_constructor.md` | 1–11 |
| `docs/audits/2026-10-02_relationship_stage_snapshot_atomicity.md` | 1–11 |
| `docs/audits/2026-10-02_return_moment_day_identity.md` | 1–17 |
| `docs/audits/2026-10-02_return_moment_render_acknowledgement.md` | 1–17 |
| `docs/audits/2026-10-02_return_moment_test_public_day_identity.md` | 1–3 |
| `docs/audits/2026-10-02_surface_pixel_resize_ownership.md` | 1–19 |
| `docs/audits/2026-10-02_training_control_dynamic_import_alias.md` | 1–11 |
| `docs/audits/2026-10-02_training_control_no_ml_authority.md` | 1–81 |

### 2026-10-03 (2 files)

| Path | Lines |
|---|---:|
| `docs/audits/2026-10-03_android16_large_screen_and_backup_rules.md` | 1–21 |
| `docs/audits/2026-10-03_transient_feedback_lifecycle.md` | 1–15 |

## Execution boundary

No tests, builds, scripts, app workflows, or external workflow queries were run for this reconciliation. No source, configuration, or shared ledger was edited. The only change for this assignment is this new documentation report. Historical test/build/CI results mentioned here remain claims contained in their dated source documents and were not revalidated.
