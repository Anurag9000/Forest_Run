# Forest Run — Complete Documentation Reconciliation Audit

**Date:** 2026-08-06 (Asia/Kolkata)  
**Repository:** `Anurag9000/Forest_Run`  
**Canonical branch:** `main`  
**Source-bearing validation baseline:** `f6d1fc1077326e160ddd829cd7279158793616eb`  
**Successful workflow:** Android validation run `31080357879`

## 1. Purpose and authority

This audit was produced after reading the complete checked-in documentation corpus line by line:

- 28 current design, architecture, persistence, recovery, input, performance, release, store, and evidence specifications under `docs/`;
- 30 dated implementation, reconciliation, and validation records under `docs/audits/`;
- the root `README.md`;
- the current `GameView` collision/update ownership and the final aggregate publisher;
- the exact-head GitHub Actions result, branch topology, and open-pull-request state.

The documentation has three different kinds of statement and they must not be confused:

1. **Normative product or engineering contract** — what the game or release system is required to do.
2. **Historical tranche evidence** — what one dated implementation session had or had not executed at that time.
3. **Current repository status** — what the latest source, tests, and exact-head workflow prove now.

When they conflict, current source/tests and exact-head evidence govern present status. Dated audit records remain immutable provenance and are not rewritten to pretend that later work existed earlier.

## 2. Complete documentation inventory reviewed

### 2.1 Current specifications

The following current documents were reviewed completely:

- `ARCHITECTURE.md`
- `AUDIT_LEDGER.md`
- `DETERMINISTIC_SCENARIO_EVIDENCE.md`
- `DEVICE_ACCEPTANCE.md`
- `DEVICE_ACCEPTANCE_AGGREGATE_SOURCE_BINDING.md`
- `DEVICE_ACCEPTANCE_AGGREGATION.md`
- `DEVICE_ACCEPTANCE_COMPILATION.md`
- `FAMILIARITY_WARMTH_SCORING.md`
- `FINITE_POINTER_INPUT_ADMISSION.md`
- `FRAME_INPUT_ADMISSION.md`
- `GAME_DESIGN.md`
- `GHOST_NAMESPACE_SCHEDULING.md`
- `GHOST_PERSISTENCE_NAMESPACES.md`
- `GHOST_PERSISTENCE_RECOVERY.md`
- `GHOST_PROMOTION_RECOVERY.md`
- `NONTERMINAL_COLLISION_OUTCOMES.md`
- `PARALLAX_BLOOM_ADMISSION.md`
- `PERFORMANCE.md`
- `RECOVERY_EVIDENCE_MAINTENANCE.md`
- `RELATIONSHIP_FAMILIARITY_SCORING.md`
- `RELEASE.md`
- `RELEASE_EVIDENCE_INDEX.md`
- `REMEDIATION_CONTINUATION_2026-08-01.md`
- `RETURN_MOMENT_ARITHMETIC.md`
- `RUN_OUTCOME_PERSISTENCE.md`
- `RUN_OUTCOME_RECOVERY.md`
- `STORE_EVIDENCE.md`
- `TERMINAL_HIT_COMPLETION.md`

### 2.2 Historical audit and evidence trail

The following dated records were reviewed completely and chronologically:

- `2026-08-01_post_merge_audit.md`
- `2026-08-02_aggregate_publication_audit.md`
- `2026-08-02_exact_aggregate_source_binding_audit.md`
- `2026-08-02_game_view_frame_input_audit.md`
- `2026-08-02_ghost_atomic_recovery_audit.md`
- `2026-08-02_ghost_promotion_recovery_audit.md`
- `2026-08-02_ghost_recovery_compile_evidence.md`
- `2026-08-02_nonterminal_collision_outcome_audit.md`
- `2026-08-02_parallax_frame_input_audit.md`
- `2026-08-02_pointer_input_admission_audit.md`
- `2026-08-02_recovery_evidence_maintenance_audit.md`
- `2026-08-02_relationship_familiarity_scoring_audit.md`
- `2026-08-02_return_moment_arithmetic_audit.md`
- `2026-08-02_route_counter_ceiling_audit.md`
- `2026-08-02_run_outcome_recovery_audit.md`
- `2026-08-02_terminal_hit_completion_audit.md`
- `2026-08-02_terminal_run_persistence_audit.md`
- `2026-08-02_trace_fairness_aggregation_audit.md`
- `2026-08-03_familiarity_warmth_reconciliation.md`
- `2026-08-03_ghost_artifact_manifest_audit.md`
- `2026-08-03_ghost_manifest_receipt_abandonment_addendum.md`
- `2026-08-03_ghost_persistence_namespace_isolation.md`
- `2026-08-03_return_moment_arithmetic_reconciliation.md`
- `2026-08-03_sha256_ghost_identity_audit.md`
- `2026-08-03_terminal_hit_impact_extraction_audit.md`
- `2026-08-04_parallax_bloom_admission.md`
- `2026-08-04_recovery_maintenance_namespace_binding.md`
- `2026-08-05_ghost_namespace_recovery_admission.md`
- `2026-08-05_ghost_namespace_serial_scheduling.md`
- `2026-08-06_exact_head_audit.md`

## 3. Reconstructed complete mission

Forest Run was not specified as a generic endless runner. The complete mission combines product identity, deterministic mechanics, durable memory, accessibility, performance, and release proof.

### 3.1 Product identity and emotional loop

Build a native Kotlin Android runner with a handcrafted cottagecore personality. The canonical loop is:

```text
willow opening ritual
→ run through five living biomes
→ collect Seeds and enter Bloom
→ meet creatures through harm, clean passage, mercy, or transformation
→ soft failure and Rest
→ changed persistent Garden
→ remembered return beneath the willow
```

The design must be judged by voice, memory, forgiveness, charming imperfection, sanctuary, and leitmotif continuity—not feature count alone.

### 3.2 Controls and state admission

- tap for a short jump;
- hold for a bounded higher jump;
- release to stop held-jump influence cleanly;
- swipe down for duck/drop behavior;
- no ambiguity between jump and duck intent;
- no input leakage across menu, Garden, death, restart, lifecycle, debug, or recreated surfaces;
- finite coordinate admission and finite/bounded frame admission;
- cutout-, density-, aspect-, and tablet-safe essential interaction regions.

### 3.3 World, pacing, and fairness

- five distinct runtime biomes with coherent visual, audio, encounter, and narrative identity;
- distance-based speed and encounter pacing;
- geometry- and travel-time-aware spawn gaps;
- bounded reaction windows and reachable rewards;
- deterministic behavior across frame cadences;
- special-case entities whose hitboxes, targeting, sway, lanes, and pass semantics remain fair;
- deterministic scenarios and fairness traces instead of intuition-only validation.

### 3.4 Encounters and terminal outcome invariant

- nineteen encounter families with mechanically and emotionally legible roles;
- collision priority independent of entity-list order;
- exactly one terminal result for every physical encounter;
- no hit-plus-pass, repeated mercy, repeated Bloom conversion, repeated projectile claim, or pooled-object reuse during iteration;
- ordered `HIT`, `STUMBLE`, `MERCY_MISS`, and clean-pass ownership.

### 3.5 Seeds and Bloom

- eight Seeds make Bloom ready;
- six-second active Bloom window;
- readiness, activation, timing, presentation, and conversion ownership remain separate and deterministic;
- Bloom preserves locomotion physics instead of becoming an unrelated movement mode;
- every entity converts at most once and rewards cannot stack accidentally;
- Bloom visibly and audibly transforms the world and leaves bounded afterglow;
- malformed presentation values fail closed.

### 3.6 Mercy, routes, relationships, and memory

- explicit clean-pass, spare, mercy, hit, conversion, kindness-chain, and route counters;
- KIND, MERCIFUL, and PEACEFUL route consequences;
- relationship stages and warmth derived from accepted history rather than mere spawning;
- repeated friends and killers affect dialogue and return moments;
- forest mood, story fragments, result text, Garden visitors, and willow dialogue reflect durable history;
- debug/deterministic scenarios never contaminate production progression.

### 3.7 Garden, wardrobe, and sanctuary economy

- nine-plant Garden progression;
- Seeds become lifetime currency exactly once;
- purchases are atomic: currency and progression cannot diverge;
- wardrobe unlocks and active costume remain valid and repaired;
- sanctuary atmosphere composes independently and remains restorative rather than coercive;
- repeated taps, interruption, process death, and recovery cannot duplicate purchases or run rewards.

### 3.8 Presentation and accessibility

- adaptive music and authored leitmotifs;
- event-specific SFX, haptics, particles, camera shake, faces, dialogue, flavour text, and wrapped bounded queues;
- persistent audio, haptic, and reduced-motion settings enforced at owner boundaries;
- safe-content transformation for landscape/cutout/aspect variation;
- lifecycle-safe resource acquisition and release;
- final feel still requires human visual, listening, touch, and haptic acceptance.

### 3.9 Persistence, recovery, and ghost runs

- durable best distance, summaries, Seeds, Garden, routes, mood, return state, relationships, settings, and ghost data;
- exactly-once run-outcome ownership;
- crash-recoverable non-ghost terminal journal with before/after snapshots;
- atomic ghost frame storage with base/backup recovery;
- receipt → ghost → persistent manifest → monotonic best distance → receipt clear;
- versioned FNV compatibility and distance-bound SHA-256 identity;
- immutable persistence namespace capture;
- same-namespace serial scheduling and bounded cross-namespace concurrency;
- namespace-scoped recovery admission and maintenance;
- fail-closed corruption handling with explicit inspect/recover/discard commands;
- no claim that SHA-256 authenticates a trusted writer or that ghost/non-ghost stores form one global transaction.

### 3.10 Deterministic evidence and release chain

- candidate-bound scenario definitions and scripted input traces;
- independent Python reconstruction of Kotlin scenario contracts;
- exact trace, artifact, candidate, device, screenshot, graphics, and approval identities;
- strict finite JSON, duplicate-key rejection, bounded reads, stable inode/byte checks, alias/hard-link/symlink rejection, atomic publication, and source reconstruction;
- physical acceptance compilation and aggregation across mandatory hardware classes;
- final release evidence index covering every approved file;
- exact-SHA read-only CI;
- real signing, internal-store delivery, physical testing, and policy approval before release readiness.

## 4. What is complete now

### 4.1 Repository governance — complete

- `main` is the sole live branch and development surface.
- Historical PR #2 was merged with its 535-commit history preserved.
- PR #1 is closed as superseded.
- No open pull requests remain.
- Current work uses normal fast-forward commits directly to `main`.
- No force-push, rebase rewrite, or history replacement is part of the process.

### 4.2 Core game loop — source-complete

The source implements the willow/menu opening, ordinary run, all five biomes, Rest/game-over transition, Garden return, Garden-to-run continuation, persistent history, wardrobe, and ghost playback. This is no longer a prototype skeleton.

### 4.3 Input and malformed-value boundaries — complete

Closed items include:

- pending tap/hold/swipe arbitration;
- finite menu/settings coordinate admission;
- public `GameView.update()` finite/bounded delta admission;
- parallax delta/speed admission;
- Bloom presentation finite normalization;
- malformed ghost-playback delta no-op behavior;
- state-exclusive input routing and gesture cancellation.

### 4.4 Encounter and collision correctness — complete at behavior level

Implemented and regression-locked:

- collision classification before pass finalization;
- deterministic severity and order independence;
- one terminal outcome per encounter;
- mercy/Bloom idempotence;
- safe deferred removal and pooling;
- reachable Seed Orbs;
- special encounter fixes;
- terminal HIT impact coordinator;
- terminal HIT completion coordinator;
- nonterminal STUMBLE/MERCY coordinator;
- exactly-once terminal persistence coordinator.

### 4.5 Bloom, mercy, relationship, and return arithmetic — complete

- independent additive familiarity warmth is integrated in production;
- PERSONAL/BONDED thresholds are stable and tested;
- return streak increments saturate;
- long-absence arithmetic rejects rollback and overflow;
- route counter ceilings match storage and recovery;
- route, mood, result, and return selectors consume accepted outcomes.

### 4.6 Garden and progression durability — complete at source level

- Garden prices, nine-plant catalogue, purchase charging, repair bounds, and atomic transactions are covered;
- terminal progression has recoverable journal ownership;
- summary and route state publish atomically within their preference boundary;
- debug scenarios are isolated from permanent progression.

### 4.7 Ghost durability, identity, namespaces, and maintenance — complete for current protocol

- version-2 ghost frame format with legacy reads;
- AtomicFile base/backup availability parity;
- promotion receipt and persistent manifest;
- distance-bound SHA-256 plus legacy FNV continuity;
- monotonic best-distance recovery;
- immutable namespace-bound stores;
- namespace-keyed immediate publications;
- same-namespace serial ordering;
- bounded cross-namespace execution;
- namespace-scoped activity and recovery admission;
- namespace-bound maintenance for both recovery domains;
- explicit fail-closed inspect, safe recover, corrupt discard, and unresolved-pending discard.

### 4.8 Evidence and release infrastructure — complete as tooling

Implemented tooling covers:

- strict JSON preflight;
- runtime/release asset structure;
- deterministic scenario trace identity;
- screenshot capture, finalization, curation, and verification;
- candidate-bound graphics and metadata;
- signed-artifact identity fields;
- physical-device acceptance compilation;
- independent aggregate validation;
- exact aggregate source reconstruction;
- staged/source mutation detection;
- alias, symlink, hard-link, and output separation;
- atomic aggregate publication;
- release evidence index construction.

The final aggregate publisher checks original staged/output paths for symbolic links before path resolution; the previously suspected resolve-before-check weakness is not present in current source.

### 4.9 Exact-head automated validation — complete

The source-bearing baseline `f6d1fc1077326e160ddd829cd7279158793616eb` completed Android validation run `31080357879` successfully.

The exact candidate passed:

- immutable source contracts;
- 420 Python tests;
- debug, release, unit-test, and androidTest Kotlin compilation;
- 927 JVM/Robolectric tests;
- debug and release lint;
- debug APK and instrumentation APK packaging;
- release AAB packaging;
- R8 output verification;
- host post-build source immutability;
- API 35 connected behavior;
- connected post-test source immutability.

The earlier documentation item “observe one exact frozen main SHA through host and connected validation” is therefore closed.

## 5. What is partially complete or remains source-addressable

These are bounded architecture or maintainability improvements. None is currently evidenced as a failing gameplay or release-tooling invariant.

### 5.1 Complete collision-result dispatcher extraction — open

`GameView` still owns the top-level `when (collision.result)` and captures live data for HIT, STUMBLE, and MERCY_MISS. The individual sequences are delegated and tested, but the dispatcher itself has not been extracted.

A safe next tranche should:

1. characterize the current branch ordering and captured values;
2. add a typed dispatcher result that can return terminal summary/death intent without owning rendering;
3. preserve entity deactivation and exactly-once persistence;
4. integrate through a minimal patch;
5. retain source contracts proving no direct branch side effects reappear.

### 5.2 Private live-effect adapters — open

`GameViewTerminalHitImpactEffects` and `GameViewNonTerminalCollisionEffects` remain private adapters over live Player, camera, audio, haptic, particle, and flash state. Their ordering owners are pure, but the adapters still increase `GameView` responsibility.

They should be extracted only after the dispatcher seam is stable.

### 5.3 Run-session coordinator extraction — open

`GameView` remains the top-level owner for:

- screen/run-state admission;
- gameplay frame sequencing;
- Bloom presentation handoff;
- collision-result dispatch;
- summary assignment and death transition;
- run reset and Garden return;
- debug scenario handoff.

A `RunSessionCoordinator` or equivalent should be introduced incrementally, not through a broad rewrite. The first seam should own state transitions and subsystem order while drawing and Android surface ownership stay in `GameView`.

### 5.4 Persistence application facade — open as architecture

Run-outcome and ghost persistence now have strong dedicated coordinators and recovery protocols, but application-level persistence remains distributed among specialized owners. A facade could expose transaction-oriented application operations while retaining the low-level namespace-bound stores.

It must not falsely imply one globally atomic transaction across SharedPreferences and ghost AtomicFile storage unless a real cross-store protocol is designed and tested.

### 5.5 Data-oriented content catalogue — optional/open

Encounter capabilities, lanes, fairness envelopes, assets, flavour, and route effects remain primarily authored in deterministic code and catalogues. A data-oriented local catalogue could improve reviewability and test generation, but it is not a release blocker and must not introduce remote content, nondeterminism, or unvalidated schema loading.

### 5.6 End-user recovery UI — optional/open

Recovery maintenance is deliberately debug/support-only and mutating commands are cold-start gated. An end-user UI is a product decision because destructive evidence removal cannot always repair semantically conflicting live state.

### 5.7 Legacy and trust limitations — intentionally retained

- pre-manifest ghost/distance mismatches cannot be reconstructed retroactively;
- healthy legacy sidecars may remain legacy until validation is necessary;
- SHA-256 is collision-resistant identity, not authentication;
- ghost and non-ghost recovery are independent protocols;
- maintenance and automatic recovery cannot prove a malicious writer did not replace both artifact and identity;
- a real device/process-death matrix is still needed.

## 6. What remains external and cannot be completed honestly from source access

### 6.1 Physical-device matrix

Run the required scenario and ordinary-play matrix on:

- an older phone;
- a representative midrange phone;
- a high-refresh phone;
- a cutout/unusual-aspect phone;
- a tablet.

Collect the policy-required number of independent sessions per class using the exact accepted artifact.

### 6.2 Physical performance evidence

Measure and review:

- p95 and p99 frame time;
- slow-frame ratio;
- allocation and GC behavior;
- peak and sustained PSS;
- ghost and save I/O latency;
- audio-thread behavior;
- crashes and ANRs;
- thermal throttling;
- battery behavior;
- long-session stability;
- dense Bloom and all-entity stress.

Thresholds must be evidence-based and frozen for the candidate rather than invented to make a report pass.

### 6.3 Human device acceptance

Manually verify:

- touch latency and jump/duck feel;
- telegraph and hitbox readability;
- safe-content geometry on cutouts, unusual aspects, densities, and tablets;
- text wrapping, contrast, and duration;
- reduced-motion behavior;
- audio mix and silence/clipping perception;
- haptic strength and appropriateness;
- lifecycle recreation and process-death recovery;
- Garden, wardrobe, ghost, and return continuity.

### 6.4 Signing and internal-store delivery

- build with the real protected upload identity;
- verify package, version, certificate, bundle/APK digest, and R8 identity;
- install and smoke-test the signed minified artifact;
- deliver that same artifact through the intended internal track;
- verify the store-installed receipt and update path.

### 6.5 Final art, presentation, and store approval

- approve every entity and animation atlas, including the Wolf sheet;
- decide whether remaining procedural scenic layers and fixed-landscape composition are final product choices;
- capture and curate candidate-bound final screenshots;
- approve graphics and metadata;
- complete current privacy, Data Safety, content rating, target audience, and store-policy review;
- obtain required independent reviewers;
- build and approve the final candidate-bound acceptance manifest, aggregate, and release evidence index.

## 7. Documentation contradictions and reconciled current truth

### 7.1 Exact-head execution

Many dated tranches correctly state that exact-head Gradle, Robolectric, emulator, or CI evidence was unavailable **during that tranche**. Those statements remain valid historical evidence, but they are not current blockers. Exact-head host and API 35 validation later completed successfully for `f6d1fc1077326e160ddd829cd7279158793616eb`.

### 7.2 Namespace switching

Older persistence/recovery documents say compatibility namespace switching during an active worker or maintenance instance is unsupported. Later implementation changed the truth:

- manager requests capture immutable namespaces;
- same-namespace work remains serial;
- different namespaces can execute with bounded concurrency;
- recovery admission is namespace-scoped;
- a maintenance instance captures one immutable namespace and remains bound to it even if the active compatibility namespace changes afterward.

The remaining unverified item is physical Android/process-death behavior under simultaneous namespace activity, not source-level namespace ownership.

### 7.3 Closed source debt

The following items appear as “remaining” in early audit records but are closed in later source and tests:

- `SaveManager.hasGhostRun()` backup recognition;
- `MainMenuScreen.onTap()` finite-coordinate admission;
- `ParallaxBackground.update()` frame admission;
- `GameView.update()` frame admission;
- return streak and long-absence arithmetic;
- relationship familiarity warmth integration;
- route-counter ceiling parity;
- terminal HIT immediate-effect extraction;
- terminal completion extraction;
- nonterminal outcome extraction;
- run-outcome recovery journal;
- ghost receipt/manifest recovery;
- SHA-256 ghost identity;
- namespace-bound maintenance;
- namespace-scoped recovery admission;
- bounded cross-namespace scheduling;
- release evidence index implementation;
- exact-head automated validation observation.

### 7.4 Product classification

The correct present classification is:

```text
source-ready feature-rich alpha
not yet physically accepted or store-release-ready
```

“Source-ready” means the exact source candidate passed the complete automated matrix. It does not mean final artwork, performance, touch, audio, haptics, signing, store delivery, privacy, or policy were approved.

## 8. Recommended next execution order

### Priority 0 — preserve the green source candidate

- keep `main` as the only branch;
- keep every commit coherent and fast-forward;
- require exact-head CI after each documentation or source change;
- do not mix physical evidence from different SHAs or artifacts.

### Priority 1 — freeze and execute external acceptance

This is the shortest path toward a truthful release candidate. Freeze one exact candidate, sign it, deliver it internally, execute the mandatory physical matrix, collect evidence, remediate any discovered issue, and restart the candidate evidence set after any source change.

### Priority 2 — extract the collision dispatcher

Perform the smallest behavior-preserving `GameView` architecture improvement. Do not combine it with content changes, rendering changes, or persistence redesign.

### Priority 3 — extract run-session sequencing

Move update-state transitions and subsystem sequencing behind a tested coordinator while leaving Surface lifecycle and drawing in `GameView`.

### Priority 4 — introduce a persistence application facade

Unify application calls without weakening the existing specialized recovery protocols or pretending that separate stores are globally atomic.

### Priority 5 — catalogue and optional product work

Only after physical acceptance identifies real needs should the project consider:

- data-oriented local encounter catalogue;
- encounter scrapbook/codex;
- deeper route-specific return moments;
- additional sanctuary stages;
- accessibility presets that alter timing as well as presentation;
- local practice mode isolated from progression;
- run-history journal;
- authored seasonal variants.

Do not add ML difficulty, cloud analytics, ads, accounts, leaderboards, telemetry, or new biomes/entities merely to increase scope. They do not address the current release gates and would increase privacy, determinism, art, balance, and maintenance risk.

## 9. Final conclusion

The original mission is substantially built. Forest Run contains the intended emotional loop, five-biome runner, nineteen encounter families, responsive controls, fair deterministic pacing, Bloom, mercy and pacifist routes, relationship memory, persistent Garden and wardrobe, adaptive presentation, accessibility controls, recoverable progression, strongly identified ghost promotion, deterministic evidence, strict release tooling, and a complete exact-head automated validation pipeline.

The project is no longer waiting for broad foundational implementation. Its genuine remaining work is sharply bounded:

1. external physical, signing, delivery, visual, audio, haptic, accessibility, privacy, and policy evidence;
2. incremental `GameView`/collision/session architecture extraction;
3. optional product expansion only after explicit approval and evidence gates.

This reconciliation supersedes old present-tense status statements while preserving every dated audit as historical provenance.