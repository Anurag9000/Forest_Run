# Forest Run — Remediation Audit Ledger

This ledger records the current state of the exhaustive repository remediation on canonical `main`. It distinguishes implemented behavior, automated contracts, validation evidence, and work that still requires code, CI visibility, physical hardware, signing material, or product approval.

The ledger is intentionally conservative: a source change is not called validated merely because it was committed, and automated validation is not treated as physical-device or store acceptance.

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
- dedicated single-worker atomic persistence;
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
- `scripts/test_ghost_persistence_namespace_contract.py`, the migrated promotion contract, and `scripts/test_recovery_evidence_maintenance_contract.py` prevent worker or maintenance namespace recapture and global publication leakage.

Still requiring physical evidence:

- long-run save latency and file size;
- process-death/relaunch behavior across OEM devices;
- playback readability near dense hazards;
- disk I/O under thermal and memory pressure;
- physical-device ADB recovery-maintenance behavior across a namespace switch.

Bounded debt:

- one global executor serializes all namespace work and explicit recovery conservatively blocks while any worker is active.

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
- exact `ParallaxBackground.kt` diff inspection confirming only one profile line, two public-admission lines, and three draw-defense lines changed.

Currently not executable or observable from this environment:

- a complete local repository checkout and the full expanded Python/Kotlin/Android test suites, because the container cannot resolve GitHub for cloning;
- exact-head Gradle compilation, JUnit/Robolectric, lint, packaging, connected emulator, and physical-device terminal-impact/return-history/relationship-copy/ghost-namespace/recovery-maintenance/Parallax-Bloom acceptance;
- push-triggered GitHub Actions check-run conclusions for the latest `main` SHA, because the installed connector exposes only pull-request-triggered workflow runs.

Therefore the current tree must not be described as exact-head green until the host/release and connected-emulator runs are observed for one frozen commit. Focused compilation, source contracts, filesystem and in-memory harnesses, and exact-diff review establish the intended ordering, arithmetic, relationship-scoring, namespace-isolation, recovery-maintenance, and Bloom-presentation boundaries but do not replace Android or hardware execution. The evidence compilers and validators prove internal consistency only when run against real evidence; they do not create physical measurements, signed delivery, visual approval, or policy approval. Until all external gates pass, the project remains a feature-rich alpha rather than a release candidate.
