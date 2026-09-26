# Forest Run — Remediation Audit Ledger

This file is the chronological remediation ledger. It preserves tranche-local findings and debt statements as historical provenance rather than rewriting them after later work closes them. **Statements below the current reconciliation section are therefore historical unless the reconciliation or a newer canonical document repeats them as current.** Current source/tests, `README.md`, `docs/ARCHITECTURE.md`, and the newest dated audit take precedence.

The ledger remains intentionally conservative: a source change is not called validated merely because it was committed, and automated validation is not treated as physical-device or store acceptance.

## Current reconciliation — 2026-09-26

This is the current source-addressable continuation; the 2026-09-20 reconciliation and all lower tranches are retained as historical checkpoints. It supersedes that checkpoint's claim that no further source defect could be substantiated: the subsequent fresh inspection found real cross-layer and evidence-validation defects. Do not reclassify those defects as imaginary merely because the older source checkpoint had a passing workflow.

**Actual authored product and coverage boundary.** Forest Run remains a hand-authored native Kotlin Android/Canvas game: Willow/Home -> guided opening -> five ordinary biomes -> the nineteen distinct encounter families (five flora, four trees, five birds, five animals) -> Seeds/eight-Seed Bloom -> Rest -> persistent Garden (nine plant unlocks), wardrobe (eight styles), relationships, ghosts and read-only Forest Journal. The current deterministic catalogue contains 28 scenarios. Four focused scenarios intentionally have explicit authored player-action scripts; other scenarios have encounter schedules but do not falsely claim an automatic action trace. The encounter catalogue derives biome reachability and scenario/fairness coverage from actual source owners. No trainable model/dataset/training runner is applicable.

**Complete tracked-tree inventory at source-bearing checkpoint `8516880d371345b8de187e8652578e861592f012`:** 833 tracked files, 187 production Kotlin files, 227 JVM/Robolectric Kotlin test files, eight instrumented Kotlin test files, 123 Python test files, 53 non-test Python scripts, 29 runtime sprite PNGs, 15 runtime OGGs, one runtime font and three GitHub workflows. These are source inventory counts, not proof of artistic or functional acceptance.

**Substantiated and fixed defects since the 2026-09-20 historical audit, each with a dated focused audit and regression evidence:**

- Candidate release summaries now reject duplicate JSON keys, non-finite JSON values, invalid numeric/audio types, alternate bundle/R8 paths, incorrect audio names and symlinked summaries.
- Raw capture, curated capture, screenshot sidecar/session, curation and Play graphics manifest entry points now use strict, bounded evidence admission. The raw screenshot gate shares the full PNG chunk/CRC/zlib/geometry verifier instead of accepting a matching digest over a fabricated header. The remaining permissive JSON call in the scenario source parser decodes a Kotlin string literal, not a release-evidence file.
- Bloom nearby-world reactions use actual entity reference identity rather than colliding identity-hash integers.
- Authored scenario steps seed stochastic entity details independently; Eagle staged offscreen encounters survive until viewport entry; Owl alert/screech is visibility gated; compound Dog/Tit/Chickadee collisions prioritize direct HIT over a different subcomponent's near-miss; Chickadee's advertised gap excludes every live bird hitbox; species-specific warning/companion sound/persistence paths were repaired.
- Deterministic jump release now uses each authored hold interval rather than the hard-coded 0.35 s release. Wolf/Eagle scenarios no longer inject unauthored extra entities. Scripted actions now enter before same-tick player physics/collision rather than one tick late.
- Render/post input latency only closes after an actual successful SurfaceHolder post; synthetic injected render callbacks no longer fabricate posted-frame evidence.
- CI surfaced a two-case test-only error from using `EncounterStepDefinition.type` instead of its real `entity_type`; that source-contract regression was corrected. Its failed prior workflow must not be represented as a product failure or a passing run.

**Verification authority:** a green run on an *earlier SHA* is historical, not automatic proof for the current head. The last fully observed Android host + API-35 validation before this continuation was run `36238682407` at `09e3a7b8`. The Python suite and source-immutability step were observed successful on `8516880d`, along with both repository-local N/A-training workflows. Consult the exact latest source/documentation HEAD's Android workflow for its host, lint, package, R8, and connected results; do not fill an in-progress or canceled result with a historical PASS.

**Outstanding acceptance boundary:** source-level integrity and regression tests do not supply the actual five-class physical-device evidence, thermal/battery/frame/input-latency measurements, human gameplay/fairness/TalkBack/Switch Access, final sprite/biome/audio/haptic creative review, binary art frame-by-frame inspection, rights/licence approvals, signed-and-installed artifact identity, Play internal-track delivery, public privacy-policy URL, security reporting configuration, release governance or accountable final go/no-go. The five known bird base/flying byte-identical alias pairs remain a creative/provenance review item, not automatically accepted distinct flying art. No such external evidence is fabricated or inherited across new candidate SHAs.

**Closure interpretation:** A (game/source) has extensive implemented owners and additional verified fixes, but no honest assertion of exhaustive visual/hardware polish or absence of all future undiscovered bugs. B (automated source/host/connected) is conditional on exact-head workflow success. C (physical/human/signed candidate acceptance) OPEN. D (governance/Play/final release) OPEN. Evidence validators and their test fixtures are not real external acceptance measurements.

---

## Current reconciliation — 2026-09-20

The repository-specific whole-software re-audit was continued from the live `main`
tree through runtime ownership, deterministic evidence, persistence isolation,
connected-test semantics, release tooling, and cross-thread observation boundaries.
The final source-bearing checkpoint before this reconciliation record is
`7757b40b7e87b3d33412f9a070c068e92f62ccf2`.

GitHub Actions run `35510934262` completed successfully on that exact SHA for
both the host/release/lint/package/R8 job and the API-35 connected smoke and
deterministic-evidence job. The repository-specific training-control applicability
audit and estate-local certificate also passed on the same SHA in runs
`35510934112` and `35510934149`.

The September continuation now includes both the earlier closure work and the
subsequent runtime/evidence hardening:

- Forest Run's training-control command remains repository-specific and fail-closed:
  trainable ML surfaces are explicitly N/A unless a real retained training surface
  is introduced, at which point the local authority must turn red rather than
  manufacturing a PASS.
- Android validation uses the hosted SDK plus explicit platform/build-tools/emulator
  packages instead of the obsolete SDK `tools` bootstrap path.
- posted-frame input-latency evidence is recorded only after a successful Canvas
  post; render lock/post failures remain failures.
- the Wolf loader matches the authored four-frame sheet; the Google Play operator
  path remains the canonical `scripts/prepare_main_release.sh` boundary; stale
  generated release summaries and workstation-specific links remain excluded.
- cross-layer animation ownership remains explicit: `playerStandUp` is a live
  Menu/willow-home presentation owner, not an orphan, and the known bird
  base/flying byte aliases remain documented provenance/creative-review items.
- `GameView` lifecycle ownership was hardened so render-thread stop/restart
  handoff is tokenized and does not overlap producers. Runtime state initialization
  remains idempotent across thread replacement.
- debug/scenario launch is latest-request-owned across `MainActivity` and
  `GameView`: stale deferred requests are cancelled or superseded, debug launch
  remains debuggable-runtime guarded, and READY evidence is emitted only after
  the requested scenario/run mode is actually applied.
- deterministic debug/capture/performance runs exercise gameplay locally without
  promoting debug-only score, Seeds, relationship state, summaries, or ghost
  progress into ordinary durable progression. A legitimate ordinary high-score
  floor survives a debug interlude without adopting the debug score.
- connected instrumentation mutations that touch live owners use the
  stop–mutate–resume boundary. State polled asynchronously by instrumentation has
  an explicit publication boundary, including Player Bloom invincibility, biome,
  Menu phase, ghost readiness, render-thread identity, active scenario, active
  entity count, frame counter, and the dedicated ghost-recorder frame count.
- the connected-validation runner is candidate-bound before and after execution:
  it requires the exact requested HEAD and a clean source worktree, rechecks both
  afterward, and rechecks canonical `origin/main` for ordinary non-detached
  invocations. GitHub Actions skips only that remote-main check while retaining
  exact event-SHA and cleanliness checks.
- ghost test reset is fail-closed and serialized. It blocks new promotion
  admission, requires all pending namespace writes to quiesce within the bounded
  test reset window, then clears publications, pending ownership, telemetry,
  preference namespaces, recovery journals, and ghost artifacts.
- the API-35 entity-roster proof was corrected rather than weakening production
  logic. Its earlier 18/19 failure identified `Eagle` because the test spawned
  it thousands of pixels beyond its intentional `screenWidth + 150` far-right
  despawn boundary. The final proof stages all nineteen types at the common valid
  pre-entry lane `screenWidth + 100`, performs one deterministic update while
  the runtime producer is stopped, asserts the exact nineteen-type set/count, and
  then separately proves the live loop resumes.

The continuation from the prior documented checkpoint
`e1de33bb90a41ffd9df6fdf1e8d52d234c77726e` to the final source-bearing
checkpoint comprises 45 commits touching 24 files. The final audited tree contains
813 tracked files: 187 production Kotlin files, 226 JVM/Robolectric Kotlin tests,
8 Android instrumentation Kotlin tests, 173 Python files below `scripts/`,
8 shell scripts, 3 GitHub workflows, 29 runtime PNG sprites, 15 raw audio files,
and 1 runtime font.

Only `main` exists and there are no open pull requests. The final repository
search returned no live TODO/FIXME markers, workstation `/home/` or `/Users/`
paths, direct `prepare_play_release.py` operator reference, or the checked
credential-marker strings used by the closure scan.

After the final source-bearing re-audit and exact-head validation, no additional
source-addressable correctness defect, missing promised player feature, broken
owner contract, or justified repository architecture addition was substantiated.
The audit therefore does not invent ML infrastructure, cloud/account systems,
advertising, multiplayer, new encounter families/biomes, speculative asset/frame
mappings, or declaration-only orphan deletions merely to create work.

Remaining blockers are intentionally external or candidate-bound: representative
physical hardware and human fairness/accessibility review; p95/p99 performance,
memory/GC/audio-thread/ghost-I/O, thermal, battery and long-session evidence;
production signing, signed-install and Play internal/store-delivery evidence;
final creative/art/audio/haptic approval; creator/source/licence/attribution and
dependency/source-code licensing decisions; privacy policy hosting, data-safety,
content-rating, target-audience and current Play-policy declarations; private
vulnerability-reporting repository configuration; and accountable final release
approval/tag. Source code must not fabricate those approvals or measurements.


## Historical tranche ledger

Everything below this heading records the state when that tranche was written. In particular, older phrases such as “dispatcher remains,” “private effect adapter remains,” “persistence should be consolidated,” or “observe exact-head CI” are superseded by the current reconciliation above and must not be read as present-day debt.

## 1. Repository and delivery policy

Implemented:

- `main` is the only active branch and the sole development surface.
- Routine development is committed directly to `main`; no active pull requests or feature branches are used.
- Published history is preserved; no force-push or history rewriting is part of the workflow.
- Permanent Android validation is read-only, checks out the exact event SHA with credentials disabled, and never commits or pushes source.
- Release preparation starts only from a clean named `main` worktree.
- `scripts/verify_origin_main.sh` freshly fetches canonical `origin/main`, rejects stale or unpushed local main commits, and verifies that the remote actually identifies `Anurag9000/Forest_Run` rather than merely being named `origin`.
- Canonical-origin identity accepts normalized GitHub HTTPS, SCP-style SSH, and `ssh://` forms while rejecting unrelated remotes before fetch.
- `scripts/prepare_main_release.sh` freezes the exact local/canonical SHA before preparation and re-verifies both after every release gate.
- The canonical wrapper aligns `JAVA_HOME/bin` with the Java runtime Gradle will use instead of validating one runtime and building with another.
- Prior human and machine release summaries are quarantined during preparation; any failure removes partial replacements and restores the last published pair.
- Python and shell contract tests cover cleanliness, named-main ownership, detached/feature rejection, root/SHA mismatch, missing or unrelated origin, unpushed commits, remote advancement, Java alignment, preflight ordering, summary rollback, and final local/remote rechecks.

Still required:

- observe a full exact-head host/release and connected-emulator run through GitHub Actions;
- freeze and tag a candidate only after every release gate is satisfied.

## 2. Frame-time, numeric, and presentation boundaries

Implemented:

- `GameThread` supplies finite frame deltas capped at 50 ms.
- Player, Menu, HUD, Rest/Game Over, Garden, camera, particles, dialogue, flavour text, sway, encounter scripts, and reset timing reject malformed/non-positive deltas.
- Face and costume-overlay animation clocks also cap direct updates to 50 ms and recover poisoned phases on the next valid frame.
- Presentation owners cap lifecycle catch-up and recover previously poisoned finite state where recovery is meaningful.
- Sprite animation advances in O(1), rejects invalid timing/FPS, and requires exact physical-frame divisibility.
- Placeholder bitmap generation validates drawable geometry, detects multiplication overflow, caps allocation, and reuses paints.
- Shared sizing, safe-content, cinematic, lighting, parallax, and layout builders reject or normalize malformed geometry.
- `EntityFactory` gives all 19 entity families one finite positive geometry boundary and preserves valid spawn origins.
- Global speed, Bloom, mercy, spawn-gap, biome-length, wind, catalogue, costume, biome-cycle, scenario, and pacifist-route assumptions have executable invariant tests.
- `ParallaxBackground.update(...)` rejects malformed delta/speed pairs, applies bounded values, and repairs poisoned ambience and Bloom clocks before advancement.
- `BloomPresentationAdmission.level(...)` maps every non-finite Bloom presentation level to `0f` and preserves ordinary finite `[0, 1]` clamping.
- `ParallaxBackground.setBloomState(...)` independently admits activation and afterglow through that shared boundary while retaining the existing active/inactive target mapping.
- `resolveParallaxAtmosphereProfile(...)` and `drawBloomTransformation(...)` reuse the same boundary so direct profile callers and final drawing cannot consume non-finite Bloom strength.
- pure and Robolectric integration tests cover NaN, both infinities, below/above-range finite values, valid fractional values, stored renderer fields, and target independence.
- `scripts/test_parallax_bloom_admission_contract.py` locks the shared owner, all production call sites, integration coverage, and removal of the former direct activation/afterglow clamp calls.

Bounded debt:

- `GameView.update()` remains a large coordinator whose direct-call boundary still relies on the render-thread delta contract; production `GameThread` calls are bounded, but the coordinator should eventually be decomposed and given a narrow public admission layer.

## 3. Garden economy, screen, wardrobe, and sanctuary

Implemented:

- canonical Garden costs are centralized in `GardenEconomy`;
- displayed Garden costs, startup repair bounds, purchase charging, and layout plant count are regression-locked to the canonical catalogue size and prices;
- `GardenPurchaseManager` performs one synchronized committed progress-and-Seed transaction;
- `GardenScreen` invokes the atomic purchase boundary directly and adopts the returned persisted state as its only local source of truth;
- the old screen-level split `saveGardenProgress`/`saveLifetimeSeeds` sequence is prohibited by CI source contract;
- load/refresh clamp Garden progress and Seed state;
- malformed taps cannot invoke run/back/equip/purchase actions;
- Garden frame time is finite, capped, and self-recovering;
- Garden tests cover atomic purchase persistence, local state adoption, malformed touches, invalid deltas, lifecycle catch-up, pricing synchronization, and primary-save namespace isolation;
- `GardenLayoutPlanner` owns shared visual/touch geometry across supported landscape sizes;
- return moments are consumed only on visible Garden entry;
- Garden particles advance only while Garden is active;
- `CostumeStyle.NONE` cannot persist as an unlocked reward or become a blank featured costume card;
- locked active costumes and malformed wardrobe sets are repaired, while ordinary equipped/featured carry-over remains intact.

Sanctuary arithmetic:

- the precedence-prone chained `+ if ... else ...` atmosphere expressions were removed;
- `SanctuaryAtmosphere` composes fireflies, petals, Bloom patches, mist, lanterns, ground glow, and canopy shade as independent bounded modifiers;
- pure tests cover simultaneous modifiers, additions plus subtractions, malformed restored counters, and output bounds;
- planner integration tests verify exact baseline atmosphere publication.

## 4. Gameplay, encounters, Seed Orbs, Bloom, and terminal impact

Implemented:

- responsive tap/hold jump behavior and swipe-down arbitration;
- gesture cancellation and secondary-pointer ownership;
- malformed hold durations cannot complete onboarding or poison jump force;
- Bloom remains orthogonal to locomotion;
- one authoritative Bloom timer;
- active Bloom rewards do not restart the timer;
- one terminal outcome per entity;
- deterministic overlap severity `HIT > STUMBLE > MERCY`;
- collision queries are presentation-free and mutation-free;
- only the selected overlap receives effects;
- Bloom conversion is exclusive from ordinary pass/unique-action/Orb rewards;
- Seed Orbs validate lifecycle/geometry, claim terminally, stage inside a reachable visible band, and remain bounded;
- entity spawn pacing is distance-based and finite;
- opening guidance replaces zero, negative, NaN, or infinite spawn intervals with a conservative positive cadence rather than permitting a spawn-every-frame loop;
- deterministic scenarios validate metadata and chronological schedules, have unique titles, cover all 19 entity types, and restrict dog-specific variants to dog steps;
- unsafe heterogeneous entity pooling remains disabled;
- entity encounter bounds drive pass/Bloom/shared lifecycle decisions;
- `TerminalHitImpactCoordinator` owns the exact terminal HIT impact order: run-hit accounting, 1.35-second ghost suppression, Player rest, camera shake, hit SFX, rest music, long haptic, then post-impact capture;
- `TerminalHitImpactCapture` is created only after all immediate effects and carries detached ghost, killer, biome, route tier, and Player presentation coordinates;
- capture construction rejects killer-identity drift between terminal presentation and completion;
- `GameViewTerminalHitImpactEffects` maps each coordinator effect one-to-one to the original live owner;
- the HIT branch no longer calls Player, ghost, camera, SFX, music, or haptic impact owners directly;
- `GameView` still owns collision selection, one impact invocation, one terminal-completion invocation, summary assignment, death-timer trigger, and `RunState.DYING` transition;
- the exact `GameView` replacement was inspected and contained only coordinator construction, HIT delegation/capture, and the private adapter.

Automated contracts:

- `TerminalHitImpactCoordinatorTest` covers exact effect order, suppression duration, capture-last behavior, fail-fast capture suppression, and killer-identity validation;
- `scripts/test_terminal_hit_impact_contract.py` forbids direct impact calls in the HIT branch and locks post-impact capture plus completion/death ordering;
- `scripts/test_terminal_hit_outcome_contract.py` now locks the impact-to-capture-to-completion boundary rather than expecting obsolete inline calls;
- CI automatically discovers both contracts through the repository-wide `scripts/test_*.py` pattern.

Still requiring ordinary-play/hardware acceptance:

- high-speed encounter combinations;
- every telegraph/hitbox/outcome agreement;
- terminal-impact feel, audio timing, haptic intensity, and death-transition continuity on representative devices;
- Bloom visual clarity under dense hazards;
- long-run balance and fairness.

Bounded architecture debt:

- the full collision-result `when` dispatcher remains in `GameView`;
- STUMBLE and MERCY_MISS live-state effects remain in `GameViewNonTerminalCollisionEffects`.

## 5. Persistence, progression, and return history

Implemented:

- startup save schema repair and future-schema compatibility storage;
- unknown preference keys and unknown future string sets are preserved;
- invalid types/enums/sets and non-finite values are repaired;
- known memory-page and history-mark sets reject non-string, blank, and oversized IDs, sort deterministically, and cap persisted cardinality;
- malformed unlocked-costume sets remove `NONE` and invalid values;
- incomplete run summaries are discarded rather than fabricated;
- derived counters are bounded and saturating;
- Forest mood, mercy, pacifist, Seed, score, time, and relationship-facing counters fail closed;
- persistent-memory selectors clamp nonpositive caller minima to one, so untouched creatures and biomes cannot be featured;
- persistent-memory tie-breaking is deterministic and repeat-killer severity uses `Long` arithmetic;
- deterministic scenarios cannot contaminate permanent score, relationships, Garden, summaries, or ghosts;
- ghost files are versioned, bounded, atomically written, and legacy-readable;
- return-day identity uses the local calendar date;
- `SafeProgressionArithmetic.saturatingIncrement(...)` normalizes restored rough-run streaks and saturates them at `Int.MAX_VALUE / 16`;
- `ReturnMomentsSystem.recordRunOutcome(...)` uses that shared saturating helper, while non-rough runs reset the streak to zero;
- `SafeProgressionArithmetic.elapsedAtLeast(...)` rejects negative timestamps, negative thresholds, and clock rollback before subtracting timestamps;
- long-absence detection uses that rollback-safe predicate with an inclusive 36-hour threshold;
- unit, Robolectric integration, and source-contract coverage lock saturation, reset, pathological rollback, exact threshold behavior, and production ownership.

Bounded debt:

- persistence ownership remains distributed across several managers and should be consolidated only after behavior remains stable.

## 6. Relationships and authored continuity

Implemented:

- appearances alone are capped at Recognition;
- Trust/Bond progression requires positive outcomes;
- hits delay or strain relationships;
- milestone rewards, home presence, rituals, repeat friends, strained bonds, and Garden traces are persisted and presented;
- every relationship-facing derived counter is clamped by `SaveManager` to `Int.MAX_VALUE / 16` before use;
- under that cap, the maximum stage, affinity, and strain expressions remain well below `Int.MAX_VALUE`;
- extreme raw `Int.MAX_VALUE` preference values are regression-tested through stage progression, strongest-bond selection, encounter tuning, and strained dialogue;
- selector minima and tie-breaking outside the monolithic relationship owner are fail-closed and deterministic;
- `RelationshipArcSystem.familiarityWarmth(...)` delegates stage, pass, spare, kindness-streak, and encounter inputs to the pure `FamiliarityWarmthScoring` model;
- stage base and all five authored warmth modifiers accumulate independently through explicit `bonus(...)` additions, avoiding Kotlin conditional-expression precedence;
- restored negative counters normalize to zero before threshold evaluation, while the Boolean score model remains bounded to eight;
- PERSONAL and BONDED thresholds remain five and seven;
- pure tests cover every modifier independently and together, and public integration verifies combined Cat history reaches the BONDED line `You came back to our quiet.`;
- `scripts/test_familiarity_warmth_contract.py` locks scorer delegation, normalization, additive modifiers, thresholds, public copy coverage, and absence of the former inline conditional-chain shape.

Still requiring ordinary-play/hardware acceptance:

- long-run relationship progression and authored-copy cadence across all tracked creatures;
- visual/readability acceptance of the deepest PERSONAL and BONDED lines during dense encounters;
- localization review if translated authored relationship copy is introduced.

## 7. Ghost recording, playback, and I/O

Implemented:

- 30 Hz bounded recording for up to twenty minutes;
- malformed direct/debug pose samples are skipped without poisoning an otherwise valid run;
- capture, in-memory publication, and persistence share the same frame validation contract;
- completed best-run buffers detach in O(1);
- bounded two-worker namespace-serial atomic persistence;
- corrupt, oversized, truncated, trailing, non-finite, invalid-state, and non-monotonic payload rejection;
- format magic/version and stable state codes;
- legacy raw-ordinal reads;
- future-version rejection without destructive rewrite;
- context-aware ghost visibility and no gameplay hitbox;
- coherent synchronized ghost-I/O telemetry snapshots with concurrent invariant tests;
- `GhostPersistenceNamespace` captures one preference namespace and derives the canonical primary or compatibility ghost filename from that single value;
- `NamespaceBoundGhostPromotionArtifactStore` binds ghost AtomicFile and best-distance preferences to the same immutable namespace without later dynamic `SaveManager` namespace reads;
- queued workers carry one namespace through recovery, receipt, ghost, manifest, distance, and cleanup;
- immediate publications are stored in a concurrent map keyed by namespace, so primary and compatibility playback/floors cannot leak into one another;
- failed-worker cleanup requires namespace, distance, FNV fingerprint, and SHA-256 identity;
- bound-store tests preserve version-2 writes, legacy reads, exact-size rejection, and invalid-candidate durability;
- integration tests cover immediate primary/compatibility switching, queued writes, durable separation, and switching back and forth after completion;
- `AndroidRecoveryEvidenceMaintenance` captures one immutable `GhostPersistenceNamespace` and passes it to both domain handlers;
- `NamespaceBoundRunOutcomeMaintenanceStateStore` binds best-distance, mood, return, summary, and route reads/writes to one preference namespace with synchronous maintenance writes;
- recovery maintenance receipt, manifest, ghost, and distance operations share one `NamespaceBoundGhostPromotionArtifactStore`;
- manifest validation loads frames from the captured artifact store rather than dynamic `SaveManager` state;
- integration tests prove valid-manifest inspection, run-journal replay, and unwritten-receipt abandonment remain on the captured namespace after an active switch;
- `GhostNamespacePendingWriteRegistry` stores the latest submitted task independently for each immutable namespace;
- pre-write and explicit recovery admission block only when that same namespace has unfinished work, so active primary work no longer blocks compatibility recovery and vice versa;
- completed and cancelled tasks are evicted lazily with exact namespace-and-future comparison, preserving a newer same-namespace task when an older task completes;
- `GhostNamespaceSerialScheduler` preserves FIFO/non-overlap inside one namespace and permits bounded overlap across different namespaces on a two-thread daemon backend;
- failed scheduler tasks continue the same-namespace queue through `finally` and cannot strand later work;
- `awaitPendingWrites(...)` waits for every active namespace-latest future under one shared monotonic timeout budget rather than relying on one global latest task;
- pure scheduler tests cover same-namespace FIFO, peak concurrency one, cross-namespace peak concurrency two, failure propagation, and failure continuation;
- registry tests cover namespace independence, completion, cancellation, latest-task authority, all-namespace waiting, timeout behavior, completed-entry cleanup, and clearing;
- `scripts/test_ghost_persistence_namespace_contract.py`, `scripts/test_ghost_promotion_recovery_contract.py`, and `scripts/test_recovery_evidence_maintenance_contract.py` lock namespace capture, scheduler ownership, bounded concurrency, recovery admission, transaction order, and maintenance isolation.

Still requiring physical evidence:

- long-run save latency and file size;
- process-death/relaunch behavior across OEM devices;
- playback readability near dense hazards;
- disk I/O under thermal and memory pressure;
- physical-device ADB recovery-maintenance behavior across a namespace switch;
- physical-device cross-namespace recovery while another namespace is actively writing;
- simultaneous Android AtomicFile and process-death behavior while both namespace workers are active.

## 8. Performance, physical-device, and store-delivery evidence

Implemented:

- allocation-free fixed primitive timing rings;
- coherent sequence-locked frame timing snapshots including slow-frame and maximum counters;
- coherent current/peak workload pairs without blocking the single game-thread producer;
- coherent ghost-I/O telemetry;
- means, p50/p95/p99, maximums, slow-frame ratio, heap observations, workload pressure, and ghost-write evidence;
- `FramePerformanceReport` rejects impossible counts, percentile ordering, heap bounds, workload pairs, and ghost-write relationships before serializing JSON;
- concurrent stress tests repeatedly construct reports from live snapshots;
- deterministic physical profiling scenarios and Python evidence evaluators;
- physical profiling starts and ends on the same clean canonical `origin/main` SHA and records both local and remote candidate identities;
- threshold manifests are hashed portably through Python, and evaluator input must satisfy complete frame, heap, workload, and ghost-I/O consistency before threshold comparison;
- connected validation rejects malformed timeout configuration, missing prerequisites, ambiguous devices, and non-exact ADB serial matches while preserving application-test failures;
- `validate_device_acceptance.py` fail-closes one signed internal-track candidate across required device classes, scenarios, thresholds, manual checks, approvals, package/version/certificate identity, and cryptographically hashed evidence files;
- `compile_device_acceptance.py` converts tester-authored relative-path drafts into one candidate-bound, fully hashed, already validated manifest and optional summary through atomic publication;
- device/store evidence contracts require unique evidence ownership, two distinct final reviewers, exact signed artifact and certificate matching, and no mixing of local APKs or different candidates.

Still required:

- measured and approved thresholds for p95/p99/slow frames/memory/I/O;
- complete representative older, mid-range, high-refresh, cutout/aspect, and tablet sessions where supported;
- allocation/GC, audio-thread, thermal, battery, and long-session traces;
- actual internal-track installation and certificate/package/version proof;
- manual completion and approval of every declared physical/device/store result;
- remediation and full-matrix remeasurement of any observed hotspot or correctness failure.

## 9. Audio, haptics, and comfort settings

Implemented:

- generation-safe `SoundPool` readiness;
- stale callback rejection after teardown/reinitialization;
- mandatory and optional SFX are distinguished;
- optional Bloom samples fall back based on actual readiness, not merely nonzero resource IDs;
- optional asynchronous load failures are classified accurately and serialized with init/destroy ownership;
- playback parameters are finite and bounded;
- adaptive music crossfades have deterministic ownership and normalized public inputs;
- haptic service acquisition, cancellation, release, and re-enable paths fail closed;
- reduced motion resets camera trauma immediately;
- particles born before reduced motion is enabled retire on the next game-thread update, while newly emitted particles use the reduced count;
- audio/haptic/reduced-motion preferences repair malformed storage and persist across recreation.

Still requiring hardware acceptance:

- SFX/music loudness and latency;
- crossfade quality over lifecycle transitions;
- vibration intensity and differentiation across OEMs;
- reduced-motion adequacy without losing gameplay telegraphs.

## 10. Assets, store evidence, packaging, and release preparation

Implemented:

- final application identity `com.anurag9000.forestrun`;
- API 36 compile/target configuration and Java 17 Android bytecode;
- one cross-owner contract locks all 30 authored runtime asset paths, all 15 mandatory raw-audio resources, runtime release validation, and Play release requirements together;
- checked-in PNGs receive bounded full signature/chunk/CRC/IHDR/zlib/scanline validation and filename-declared sprite-frame divisibility checks;
- the checked-in SFNT font receives bounded table-directory, required-table, `head`, glyph, cmap, and name-record validation;
- Ogg resources receive page-boundary, CRC, logical-stream sequence, BOS/EOS, and Vorbis/Opus identification checks; WAV, MP3, and M4A inputs receive structural container/frame checks;
- runtime sprite sheets must decode, divide exactly by frames, satisfy edge dimensions, and remain within the runtime decode budget;
- debug-only generated placeholders are prohibited in release;
- generated store graphics are candidate-bound, generator/source-hash-bound, dimension/mode/hash verified, exact-set checked, and atomically published with rollback;
- Play metadata is exact-file-set checked, UTF-8/NFC/LF normalized, whitespace/control/template sanitized, bounded, per-file hashed, candidate-bound, and atomically finalized;
- screenshot capture builds a fresh exact-candidate APK, verifies exact scenario readiness and foreground Activity ownership around every screencap, structurally validates each PNG, atomically writes per-image sidecars, and atomically finalizes one shared capture session;
- curated screenshots require exact manifest membership, unique scenario/title/image coverage, complete PNG integrity, and one shared candidate/APK/device/package/Activity/session identity;
- release AAB verification covers ZIP safety/integrity, required module entries and DEX, application/version identity, complete JAR signing, configured certificate matching, and cross-platform unsafe entry names;
- R8 minification/resource shrinking and effective application-class renaming are checked;
- human and machine release summaries are independently verified against the current bundle/mapping hashes, candidate, screenshot evidence, final application identity, signing status, dry-run disclosures, and exact audio/graphics/metadata counts;
- the canonical wrapper verifies source assets, graphics, metadata, summaries, local main, and canonical origin/main, with transactional restoration of prior summaries on failure;
- `docs/STORE_EVIDENCE.md` documents generation, finalization, verification, invalidation, and the limits of automated evidence.

Still required:

- real upload credentials and upload-key access;
- signed, minified artifact build and direct installation from that exact artifact;
- internal-store delivery test and certificate/package/version verification;
- final artwork/atlas/animation inspection, including Wolf;
- final screenshots and metadata human approval;
- privacy, data-safety, content-rating, target-audience, and current Play-policy review.

## 11. Validation truth

Locally verified during remediation where runtime execution was available:

- pure sanctuary atmosphere Kotlin model compilation and expected combined outputs;
- Python local-main verifier tests;
- Python origin/main local-remote repository tests;
- focused Kotlin compilation and executable fake-effect validation for `TerminalHitImpactCoordinator`;
- terminal impact ordering, exact 1.35-second suppression, capture-last behavior, fail-fast capture suppression, and killer-identity validation;
- joint execution of the terminal impact and terminal completion source-contract parsers against the exact extracted HIT/capture/adapter structure;
- exact inspection of the `GameView` replacement commit confirming only three intended hunks;
- focused Kotlin compilation and executable return-arithmetic validation for saturation, rollback, pathological timestamps, and the exact 36-hour threshold;
- source-contract parser validation for return-moment arithmetic ownership and the absence of raw increment/subtraction paths;
- focused Kotlin compilation and executable familiarity-warmth validation for complete accumulation, independent seven-point combinations, negative restored counters, and tier boundaries;
- source-contract parser validation for pure-scorer delegation, five independent modifiers, stable thresholds, and public BONDED-copy coverage;
- focused Kotlin compilation for `GhostPersistenceNamespace`, the namespace-bound artifact store, and the namespace-aware manager surface;
- filesystem-backed primary/compatibility ghost and distance isolation, including capture while the separate mutable ghost filename was intentionally stale;
- source-contract migration preserving recovery ordering, strong identity, legacy upgrade, corruption blocking, and healthy fast-path coverage while adding namespace ownership;
- focused Kotlin compilation of `NamespaceBoundRunOutcomeMaintenanceStateStore`;
- executable in-memory primary/compatibility mood and return-state isolation with synchronous maintenance writes;
- exact production diff inspection confirming only namespace capture, handler wiring, bound state delegation, bound ghost artifact ownership, and bound manifest frame loading changed;
- maintenance source-contract migration removing obsolete dynamic `SaveManager` and `AndroidGhostPromotionArtifactStore` expectations;
- focused Kotlin compilation and executable Bloom presentation admission checks for NaN, both infinities, finite underflow, valid fractional input, and finite overflow;
- source-contract syntax and parser execution covering the shared admission owner, public setter, atmosphere profile, final draw path, and integration fixture;
- exact `ParallaxBackground.kt` diff inspection confirming only one profile line, two public-admission lines, and three draw-defense lines changed;
- focused Kotlin compilation and executable namespace-pending-write registry validation;
- registry behavior for cross-namespace independence, completed/cancelled eviction, latest same-namespace authority, and complete clearing;
- exact `GhostPersistenceManager.kt` diff inspection confirming only activity ownership, namespace-scoped gates, task registration, drain-pointer use, and reset changed;
- namespace and promotion source-contract migration preserving worker order, durable ordering, SHA-256 identity, corruption blocking, disk fallback, and publication cleanup while removing the obsolete global recovery gate;
- focused Kotlin/JVM compilation and executable namespace scheduler/registry validation for same-namespace FIFO, cross-namespace overlap, failed-task continuation, and all-namespace waiting;
- exact scheduler-integration diff inspection confirming only bounded backend construction, namespace-keyed submission, all-namespace waiting, cleanup, imports, constants, and comments changed;
- namespace and promotion source-contract migration preserving transaction, identity, corruption, legacy-upgrade, and disk-fallback coverage while prohibiting a global single-thread executor or single-future drain shortcut.

Currently not executable or observable from this environment:

- a complete local repository checkout and the full expanded Python/Kotlin/Android test suites, because the container cannot resolve GitHub for cloning;
- exact-head Gradle compilation, JUnit/Robolectric, lint, packaging, connected emulator, and physical-device terminal-impact/return-history/relationship-copy/ghost-namespace/recovery-maintenance/Parallax-Bloom/namespace-recovery-admission/parallel-namespace-I/O acceptance;
- push-triggered GitHub Actions check-run conclusions for the latest `main` SHA, because the installed connector exposes only pull-request-triggered workflow runs.

Therefore the current tree must not be described as exact-head green until the host/release and connected-emulator runs are observed for one frozen commit. Focused compilation, source contracts, filesystem and in-memory harnesses, and exact-diff review establish the intended ordering, arithmetic, relationship-scoring, namespace-isolation, recovery-maintenance, Bloom-presentation, namespace-scoped recovery-admission, and bounded cross-namespace scheduling boundaries but do not replace Android or hardware execution. The evidence compilers and validators prove internal consistency only when run against real evidence; they do not create physical measurements, signed delivery, visual approval, or policy approval. Until all external gates pass, the project remains a feature-rich alpha rather than a release candidate.
