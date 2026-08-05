# Forest Run exhaustive mission, implementation, and remaining-gates audit

Date: 2026-08-06 (Asia/Kolkata)

This document reconstructs the complete intent of the earlier Forest Run audit, reconciles it against the repository after the historical pull-request work and subsequent direct-to-`main` development, records the source-verifiable work completed during this audit, and separates genuine remaining work from tasks that require physical devices, release credentials, artistic judgment, or product decisions.

## 1. Repository-governance result

The requested repository model is already in force:

- `main` is the default branch.
- `main` is the only live branch.
- Pull request 2 was merged, preserving its 535-commit development history.
- Pull request 1 is closed as superseded.
- There are no open pull requests.
- Historical pull-request records remain visible because GitHub does not support deleting merged or closed pull-request records; deleting those records is neither necessary nor desirable for preserving provenance.
- All work in this audit was committed directly to `main` without force-pushing, rebasing published history, creating a development branch, or opening another pull request.

## 2. Reconstructed original mission

The original request was much larger than “make an endless runner.” It described a coherent game, emotional loop, technical quality bar, verification system, and release process.

### 2.1 Product identity and emotional loop

Forest Run was intended to be a handcrafted, cottagecore, emotionally gentle runner rather than a generic score chase. Its canonical loop was:

1. begin in a quiet willow/rest ritual;
2. enter a living forest run;
3. meet creatures and hazards whose outcomes can be harmful, clean, merciful, or transformed;
4. collect Seeds and activate Bloom;
5. end in a reflective rest/result moment;
6. carry persistent memory and currency into the Garden;
7. improve the sanctuary without turning it into a coercive economy;
8. return to the willow and run again.

The game had to preserve voice, memory, forgiveness, charming imperfection, and leitmotif continuity. Mechanical correctness alone was not considered completion.

### 2.2 Controls and player-state semantics

The requested input and movement contract included:

- tap to jump;
- hold to sustain a higher jump within a bounded window;
- release to end the held-jump effect cleanly;
- downward swipe or equivalent gesture to duck/drop where appropriate;
- deterministic separation of jump, duck, hit, invulnerability, death, rest, Bloom, and menu states;
- no stale input leaking across pause, lifecycle recreation, debug scenarios, or screen transitions;
- touch targets and safe-area behavior that remain usable on cutout phones, tablets, varied densities, and high-refresh devices.

### 2.3 World and biome structure

The requested world included five distinct biomes with readable silhouettes, transitions, atmosphere, and encounter identity. Difficulty was to rise primarily with distance while remaining fair and legible. Biome changes were not meant to be cosmetic swaps only: they were part of the pacing, audio, encounter, color, and narrative system.

### 2.4 Encounter catalogue and single-outcome invariant

The planned catalogue covered nineteen creature, plant, bird, and obstacle categories. Every encounter needed a readable role, fair hitbox, coherent spawn lane, and one terminal gameplay outcome.

The central invariant was:

> One physical encounter may resolve exactly once.

A single entity must not be counted as both a hit and a clean pass, both spared and collided, repeatedly mercied every frame, repeatedly converted by Bloom, or returned to an object pool while still being iterated. Collision ordering, pass ordering, swept movement, pooling, and special encounter behavior all had to respect this invariant.

### 2.5 Fairness and difficulty

The request required more than increasing speed:

- distance-based pacing with bounded acceleration;
- minimum reaction windows;
- spawn spacing based on geometry and travel time rather than frame timing alone;
- safe lane and height combinations;
- readable cactus, eagle, hedgehog, dog, cat, bird, orb, and other special cases;
- no impossible conditional branches;
- no reward objects placed outside reachable movement envelopes;
- hitboxes that follow visible sway/animation closely enough to feel fair;
- deterministic scenarios for regression and capture;
- device-independent behavior under variable frame cadence.

### 2.6 Seeds and Bloom

The original Bloom contract included:

- collect eight Seeds to become Bloom-ready;
- activate a six-second Bloom state;
- maintain orthogonal readiness, active-state, duration, presentation, and cooldown/retrigger semantics;
- prevent readiness from being consumed or recreated accidentally by unrelated state transitions;
- prevent stacked rewards or repeated conversion of the same entity;
- make Bloom visibly and audibly transform the world;
- preserve fair collision and encounter accounting during activation and expiry;
- test timing deterministically rather than relying only on visual observation.

### 2.7 Mercy, pacifist routes, and relationships

The game was intended to remember how the player treated the forest. Requested behavior included:

- mercy outcomes that occur once per encounter;
- clean-pass, spared, hit, Bloom-conversion, kindness-chain, and related counters with explicit ownership;
- pacifist route tiers and route summaries;
- persistent relationship/world-memory state that is derived from accepted outcomes, not merely from entities being spawned;
- return moments and result text that reflect actual history;
- no debug scenario contaminating ordinary progression or production save data.

### 2.8 Audio, haptics, and leitmotif

The requested presentation system included:

- adaptive music/leitmotif behavior tied to state and biome;
- bounded update cadence rather than per-frame parameter churn;
- event-specific sound and haptic feedback;
- settings for audio, haptics, and reduced motion;
- lifecycle-safe ownership and release of players/resources;
- no duplicate playback or stale references after pause/recreation;
- manual listening and feel checks on real hardware, because source tests cannot prove perceived mix quality.

### 2.9 Garden and sanctuary progression

The Garden was intended to be the emotional and economic continuation of a run:

- Seeds earned in a run become durable lifetime currency exactly once;
- Garden purchases are atomic and cannot deduct currency without granting progression or grant progression without deducting currency;
- the sanctuary reflects progress and atmosphere;
- result, Garden, menu, and return-to-willow navigation remain coherent;
- repeated taps, lifecycle interruptions, and process death do not duplicate purchases or rewards;
- the Garden remains restorative rather than a disconnected shop screen.

### 2.10 Persistence, crash recovery, and ghost data

The original and evolved audit required robust persistence for:

- best score/distance;
- run summaries;
- lifetime Seeds;
- Garden progress;
- route counters;
- forest mood and return moments;
- relationship state;
- settings;
- ghost frames and ghost identity;
- recoverable run-outcome publication;
- recoverable best-ghost/best-distance promotion.

Correctness requirements included synchronous durability at transaction boundaries, namespace consistency, bounded file formats, strong identity binding, safe legacy migration, fail-closed handling of corrupt evidence, explicit maintenance operations, and no hidden cross-namespace writes.

### 2.11 Debugging and deterministic evidence

The request called for deterministic scenario tooling rather than ad hoc manual reproduction. The repository therefore needed:

- named debug scenarios;
- scripted input traces;
- source-derived scenario hashes;
- replay/capture identity bound to a candidate commit;
- fixed trace contracts across Kotlin and Python tooling;
- scenarios for opening readability, specific encounters, Bloom, ghost behavior, Garden transactions, lifecycle recovery, and store screenshots;
- production isolation so debug mode cannot silently alter ordinary progression.

### 2.12 Performance and runtime architecture

The requested quality bar covered:

- no unsafe collection mutation or object-pool reuse;
- bounded entity, particle, text, dialogue, and Seed-Orb workload;
- frame and workload telemetry;
- ghost I/O outside the frame-critical path;
- namespace-serial ordering with bounded cross-namespace concurrency;
- lifecycle-safe shutdown;
- repeatable performance profiles;
- p95/p99 frame-time, slow-frame, memory, crash, and ANR thresholds;
- physical-device evidence rather than emulator-only performance claims.

### 2.13 Release, store, and evidence chain

The release mission included:

- compile debug, release, unit-test, and androidTest sources;
- run host tests and connected tests;
- run debug and release lint;
- produce a debug APK, androidTest APK, and release bundle;
- verify R8 output;
- validate runtime assets and raw audio structurally;
- verify release artifact contents and candidate identity;
- create candidate-bound graphics and screenshot evidence;
- require signed-artifact and internal-store installation evidence;
- cover a mandatory physical-device matrix;
- collect manual visual, touch, audio, haptic, reduced-motion, lifecycle, artwork, privacy, metadata, content-rating, audience, and store-policy approvals;
- publish acceptance aggregates atomically without allowing evidence mutation between validation and publication.

## 3. Audit of what is complete

### 3.1 Core engine and encounter correctness

The historical PR and direct-to-main work have closed the original critical engine defects. The current source and test contracts cover:

- functional jump, held-jump, release, and duck/drop state transitions;
- ordered collision/pass resolution;
- one terminal outcome per entity;
- mercy and Bloom idempotence;
- safe deferred removal and pooling;
- reachable Seed-Orb placement and bounded collection;
- corrected special-case encounter conditions and targeting;
- swept and cadence-independent encounter handling;
- fairness-oriented spawn and scenario contracts;
- explicit route and outcome accounting.

The earlier audit’s P0 gameplay failures are no longer open source defects.

### 3.2 Bloom, mercy, memory, and narrative continuity

The current architecture separates Bloom readiness, activation, timing, presentation, world reaction, and admission. Run outcomes feed route, relationship, mood, result, and return systems through explicit accepted transitions. Debug scenarios have dedicated contracts and are prevented from silently becoming production progression.

### 3.3 Garden economy and persistent transactions

Garden purchasing uses a single atomic boundary. Run-outcome publication, lifetime currency, Garden progression, route counters, summaries, mood, and return state have transaction/recovery coverage. The audit found no remaining source evidence of the old split-write Garden path.

### 3.4 Ghost publication and recovery

The current ghost system includes:

- immutable frame snapshots;
- distance-bound FNV compatibility identity and SHA-256 strong identity;
- versioned receipt and manifest sidecars;
- recoverable receipt → ghost → manifest → best-distance ordering;
- namespace-bound artifact owners;
- immediate publication with later durable persistence;
- serial ordering within a namespace;
- bounded concurrency across namespaces;
- pending-write tracking and bounded waits;
- corrupt/unknown evidence that blocks unsafe promotion rather than being silently discarded;
- explicit inspect, safe-recover, discard-corrupt, and discard-unresolved-pending maintenance seams.

### 3.5 Deterministic scenario and evidence tooling

The repository contains candidate-bound deterministic scenario definitions, scripted inputs, hashes, trace validators, screenshot capture tooling, curated-set validation, physical-device acceptance compilation, aggregation, and atomic publication. The tooling checks path safety, duplicate identities, hard links, digest drift, timestamp order, required device classes/scenarios, performance thresholds, and approval completeness.

### 3.6 Assets and release-source validation

The source catalogue currently verifies:

- 30 runtime assets;
- 29 PNG assets;
- 1 font asset;
- 15 required raw audio resources.

PNG structure/CRC/geometry, font table structure, Ogg checksums/codecs/EOS, and supported audio-container structure are validated before Android build work. Runtime and release catalogues are cross-checked.

### 3.7 CI and immutable-source policy

The Android validation workflow is read-only with respect to repository contents. It checks out the exact candidate SHA, runs immutable source contracts, installs pinned Python test dependencies, runs the full Python tooling suite, validates the Gradle wrapper, compiles/tests/lints/packages the checked-in tree, verifies R8 output, and runs API 35 connected behavior in a separate job. It also confirms validation did not mutate tracked or staged source.

## 4. Work completed in this audit

Starting from `aaf7672f2b70dac30a1e2f9b1800259653b50c57`, this audit made fourteen direct, fast-forward commits before this document:

1. removed the obsolete empty `res/raw/.gitkeep` sentinel that the strict release validator correctly treated as an unsupported raw resource;
2. added a pinned CI-only Pillow dependency for image-evidence tests;
3. installed that dependency explicitly in Android validation;
4. synchronized release-asset tests with the current 30-asset/15-audio catalogue;
5. made AssetPaths reference extraction identifier-safe;
6. scoped screenshot contracts to the Python writer/finalizer/verifier that owns the asserted behavior;
7. repaired deterministic scenario mutation so tests modify `CACTUS_READ`, not an earlier duplicate entity step;
8. replaced brittle recovery source scanning with implementation-scoped Kotlin block extraction;
9. removed call-like wording from recovery documentation that falsely resembled an executable legacy write path;
10. preserved candidate artifact bytes in the acceptance alias fixture so the intended alias rejection is reached;
11. made failed performance fixtures internally valid before testing threshold rejection;
12. isolated store-graphics mode evidence from prior dimension mutation;
13. mutated acceptance evidence only after the actual final validation pass;
14. indexed the successful ghost receipt-clear path rather than the earlier mismatch cleanup path.

The result is not a weakening of the verification system. No test was skipped, no invariant was deleted, no production failure was reclassified as success, and no workflow was granted write permission. The fixes make tests exercise the intended branch and ensure optional dependencies are explicit.

## 5. What genuinely remains

### 5.1 External release evidence

These tasks cannot be honestly completed from source access alone:

- run the mandatory scenario matrix on actual older, midrange, high-refresh, cutout, and tablet hardware;
- collect at least the policy-required session count per device class;
- verify touch feel, jump readability, safe areas, typography, contrast, and reduced-motion behavior by observation;
- listen to the real mix and verify haptic feel on devices;
- collect sustained frame-time, memory, crash, and ANR evidence under real thermal/device conditions;
- sign the release with the real protected signing identity;
- deliver it through the intended internal store track and verify the installed package/version/certificate/artifact identity;
- capture candidate-bound final screenshots and approve the curated set;
- approve final artwork/animation, including every entity and special state;
- complete current privacy, data-safety, content-rating, target-audience, metadata, and store-policy review;
- publish a final acceptance manifest/aggregate containing genuine evidence and independent reviewer approval.

Placeholders or fabricated approvals must not be used to close these gates.

### 5.2 Product decisions

The source supports a complete loop, but several additions are decisions rather than defects:

- whether procedural scenic layers should remain dynamic or be replaced by fixed authored landscape art;
- whether to add more sanctuary/Garden stages;
- whether to add a noncompetitive encounter scrapbook/codex;
- whether to deepen route-specific return moments;
- whether additional accessibility presets should alter timing as well as presentation;
- whether any new encounter or biome should enter the release scope.

These should be approved as product scope before implementation because each expands art, balancing, localization, QA, screenshot, and policy work.

### 5.3 Bounded architectural debt

No current evidence makes these release blockers, but they are the best next engineering investments:

1. **Run-session coordinator extraction** — reduce `GameView`’s orchestration responsibility by moving state transitions, lifecycle admission, and subsystem sequencing into an explicit coordinator.
2. **Collision-resolution system extraction** — move collision effects and terminal outcome dispatch behind a typed resolver, preserving the single-outcome invariant while reducing UI-engine coupling.
3. **Persistence facade** — expose one transaction-oriented application boundary above the currently distributed stores, while retaining namespace-bound low-level owners and recovery evidence.
4. **Content catalogue extraction** — make encounter capabilities, lanes, fairness envelopes, assets, flavor, and route effects data-oriented without replacing deterministic code with unvalidated remote content.
5. **Release evidence index** — maintain a generated, candidate-bound index that links every physical session, screenshot set, performance profile, approval, and aggregate.

These should be implemented incrementally with characterization tests; a broad rewrite would create more risk than value.

## 6. Additional models, architectures, pipelines, experiments, datasets, categories, features, and tasks

Forest Run does not currently need a machine-learning model. Its critical behavior is deterministic, local, explainable, and privacy-preserving. Adding ML merely to satisfy a “model” category would increase APK size, nondeterminism, data obligations, and test complexity without solving a demonstrated problem.

The useful interpretation of those categories for this project is below.

### 6.1 Models and controllers

Recommended only behind explicit experiment gates:

- a deterministic local difficulty controller driven by distance, recent accepted outcomes, and bounded reaction-time constraints;
- a local comfort controller that changes presentation intensity, not rewards, when reduced motion or repeated input difficulty is observed;
- an authored leitmotif state graph rather than a learned music model;
- a rule-based return-moment selector with replayable inputs and stable priority rules.

Do not add network-trained personalization, remote telemetry dependency, opaque dynamic difficulty, or behavior that manipulates monetization/reward pressure.

### 6.2 Architecture experiments

- characterize `GameView` sequencing, then extract one responsibility at a time;
- property-test the encounter state machine and transaction state machines;
- add mutation-testing trials for the single-outcome, Bloom, Garden, and recovery invariants;
- compare current rendering with a data-oriented content catalogue before migrating all entities;
- benchmark ghost scheduling at one, two, and bounded higher namespace concurrency only under recorded workload evidence.

### 6.3 Internal datasets/corpora

The valuable datasets are deterministic QA corpora, not player surveillance:

- scenario-definition and scripted-input corpus;
- encounter fairness traces across frame cadences and speed bands;
- save/migration/recovery fixture corpus, including truncated, corrupt, legacy, duplicate, and cross-namespace cases;
- physical-device performance profile corpus;
- candidate-bound screenshot corpus;
- asset structure and geometry manifest;
- audio loudness/peak/duration envelope manifest;
- Garden transaction and run-outcome transition corpus;
- release-evidence manifest and aggregate corpus.

Any corpus containing real device identifiers must remain minimized, hashed where appropriate, access-controlled, and governed by the privacy policy.

### 6.4 New verification pipelines

High-value future pipelines are:

- save-format migration matrix across every supported historical version;
- property-based encounter outcome fuzzing;
- deterministic frame-cadence sweep for each canonical scenario;
- screenshot golden comparison with explicit tolerances and human final approval;
- audio peak, clipping, silence, duration, and loudness-range validation;
- macrobenchmark/baseline-profile evidence on supported hardware;
- dependency/SBOM and license inventory for release candidates;
- signed provenance tying source SHA, bundle digest, certificate digest, store delivery, screenshots, device sessions, and approvals.

### 6.5 Feature candidates

Potential additions that fit the game’s identity, but are not automatically part of the current release:

- an encounter scrapbook populated only by real accepted encounters;
- Garden memories that reflect mercy/route history without becoming a checklist economy;
- route-specific willow dialogue and subtle sanctuary changes;
- expanded accessibility presets for contrast, text duration, input hold windows, haptic intensity, and reduced motion;
- a local practice mode using deterministic scenarios, isolated from progression;
- an optional run-history journal containing summaries, not invasive analytics;
- authored seasonal visual variants with no time-limited pressure or remote dependency.

Every feature must preserve the restorative loop, avoid coercive retention mechanics, and enter through an experiment/release gate with tests, art, accessibility, and physical-device evidence.

## 7. Completion standard

The repository should be considered source-ready only when the exact `main` SHA passes:

- immutable source contracts;
- release-source asset verification;
- the complete Python tooling suite;
- Kotlin/debug/release/androidTest compilation;
- JVM unit tests;
- debug and release lint;
- debug APK, androidTest APK, and release bundle packaging;
- R8 output verification;
- API 35 connected behavior;
- source immutability after both jobs.

It should be considered release-ready only after the external evidence in section 5.1 is complete for that same immutable candidate identity.

## 8. Final audit conclusion

The original Forest Run mission has not been left at the prototype stage. The repository now contains a deeply defended gameplay loop, deterministic encounter semantics, Bloom and mercy invariants, persistent world memory, atomic Garden and outcome transactions, recoverable ghost publication, candidate-bound evidence tooling, strict asset validation, and a substantial release-readiness pipeline.

The main remaining work is not another uncontrolled wave of mechanics or speculative architectures. It is:

1. keep exact-head CI green;
2. perform the mandatory physical-device, signing, store-delivery, visual, audio, haptic, performance, privacy, and policy evidence work;
3. close bounded architectural debt incrementally;
4. add new content only through explicit product and evidence gates.

That distinction prevents the project from claiming false completion while also preventing already-solved engine work from being repeatedly reimplemented.