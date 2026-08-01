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
- `scripts/verify_origin_main.sh` freshly fetches canonical `origin/main` and rejects stale or unpushed local main commits.
- `scripts/prepare_main_release.sh` verifies local main and origin/main before preparation, freezes the full SHA, runs the existing release preparer, then re-verifies the same local and remote SHA afterward.
- Python contract tests cover clean-main acceptance, dirty/untracked rejection, feature/detached rejection, SHA mismatch, missing origin, unpushed commits, and remote advancement.

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
- Global speed, Bloom, mercy, spawn-gap, biome-length, wind, catalogue, costume, and pacifist-route ordering assumptions have executable invariant tests.

Bounded debt:

- `GameView.update()` remains a large coordinator whose direct-call boundary still relies on the render-thread delta contract; production `GameThread` calls are bounded, but the coordinator should eventually be decomposed and given a narrow public admission layer.
- `ParallaxBackground` still relies on finite production inputs from `GameThread` and `GameStateManager`; malformed direct/debug calls should eventually be normalized at its own public boundary after the large owner is decomposed.

## 3. Garden economy, screen, wardrobe, and sanctuary

Implemented:

- canonical Garden costs are centralized in `GardenEconomy`;
- `GardenPurchaseManager` performs one synchronized committed progress-and-Seed transaction;
- `GardenScreen` invokes the atomic purchase boundary directly and adopts the returned persisted state as its only local source of truth;
- the old screen-level split `saveGardenProgress`/`saveLifetimeSeeds` sequence is prohibited by CI source contract;
- load/refresh clamp Garden progress and Seed state;
- malformed taps cannot invoke run/back/equip/purchase actions;
- Garden frame time is finite, capped, and self-recovering;
- Garden tests cover atomic purchase persistence, local state adoption, malformed touches, invalid deltas, lifecycle catch-up, and primary-save namespace isolation;
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

## 4. Gameplay, encounters, Seed Orbs, and Bloom

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
- opening guidance now replaces zero, negative, NaN, or infinite spawn intervals with a conservative positive cadence rather than permitting a spawn-every-frame loop;
- unsafe heterogeneous entity pooling remains disabled;
- entity encounter bounds drive pass/Bloom/shared lifecycle decisions.

Still requiring ordinary-play/hardware acceptance:

- high-speed encounter combinations;
- every telegraph/hitbox/outcome agreement;
- Bloom visual clarity under dense hazards;
- long-run balance and fairness.

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
- return-day identity uses the local calendar date.

Bounded debt:

- `ReturnMomentsSystem.recordRunOutcome()` still performs a raw source-level `roughRunStreak + 1`; persistence preserves saturation when the stored streak is already maximum, but the source arithmetic should eventually use an explicit saturating helper.
- long-absence detection still uses raw timestamp subtraction. Persisted timestamps are nonnegative and ordinary clock rollback currently evaluates as not absent, but a future refactor should use an explicit overflow-safe elapsed predicate.
- persistence ownership remains distributed across several managers and should be consolidated only after behavior remains stable.

## 6. Relationships and authored continuity

Implemented:

- appearances alone are capped at Recognition;
- Trust/Bond progression requires positive outcomes;
- hits delay or strain relationships;
- milestone rewards, home presence, rituals, repeat friends, strained bonds, and Garden traces are persisted and presented;
- persistent input counters are bounded before relationship code reads them;
- selector minima and tie-breaking outside the monolithic relationship owner are now fail-closed and deterministic.

Bounded debt:

- `RelationshipArcSystem.computeStage()` adds and multiplies persistent counters in `Int`; extreme valid bounded counters can still overflow stage scoring.
- `RelationshipArcSystem.affinityScore()` has the same `Int` multiplication/addition risk and can invert ranking under extreme counters.
- `RelationshipArcSystem.familiarityWarmth()` contains an authored-precedence defect similar to the former sanctuary arithmetic: later warmth modifiers are nested inside preceding `else` branches instead of accumulating independently.
- `RelationshipArcSystem.strainedConsequence()` adds hit and tender-streak counters in `Int` and can overflow severity.
- These functions are precisely isolated, but the surrounding file is a roughly 1,400-line authored dialogue catalogue. They require a verified checkout/patch path or decomposition before changing them without risking unrelated narrative loss.

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
- coherent synchronized ghost-I/O telemetry snapshots with concurrent invariant tests.

Still requiring physical evidence:

- long-run save latency and file size;
- process-death/relaunch behavior across OEM devices;
- playback readability near dense hazards;
- disk I/O under thermal and memory pressure.

## 8. Performance and workload evidence

Implemented:

- allocation-free fixed primitive timing rings;
- coherent sequence-locked frame timing snapshots including slow-frame and maximum counters;
- coherent current/peak workload pairs without blocking the single game-thread producer;
- coherent ghost-I/O telemetry;
- means, p50/p95/p99, maximums, slow-frame ratio, heap observations, workload pressure, and ghost-write evidence;
- `FramePerformanceReport` rejects impossible counts, percentile ordering, heap bounds, workload pairs, and ghost-write relationships before serializing JSON;
- concurrent stress tests repeatedly construct reports from live snapshots;
- deterministic physical profiling scenarios and Python evidence evaluators.

Still required:

- measured thresholds for p95/p99/slow frames/memory/I/O;
- representative older, mid-range, high-refresh, cutout/aspect, and tablet devices as supported;
- allocation/GC, audio-thread, thermal, battery, and long-session traces;
- remediation and remeasurement of any observed hotspot.

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

## 10. Assets, packaging, and release preparation

Implemented:

- final application identity `com.anurag9000.forestrun`;
- API 36 compile/target configuration and Java 17 Android bytecode;
- required sprites, font, and mandatory audio contracts;
- mandatory raw resources must be addressable and nonempty;
- sprite sheets must decode, divide exactly by frames, satisfy edge dimensions, and remain within a four-megapixel decode budget;
- debug-only generated placeholders are prohibited in release;
- R8 minification/resource shrinking and effective class-renaming checks;
- release artifact identity, structure, signing, graphics, metadata, and screenshot tooling;
- exact-origin-main release wrapper and tests.

Still required:

- real upload credentials;
- signed, minified artifact build and direct installation;
- internal-store delivery test and certificate/package/version verification;
- final artwork/atlas/animation inspection, including Wolf;
- final screenshots and metadata approval;
- privacy, data-safety, content-rating, target-audience, and current Play-policy review.

## 11. Validation truth

Locally verified during remediation:

- pure sanctuary atmosphere Kotlin model compilation and expected combined outputs;
- Python local-main verifier tests;
- Python origin/main local-remote repository tests.

Not currently observable through the installed GitHub connector:

- push-triggered GitHub Actions check-run conclusions for the latest `main` SHA.

Therefore the current tree must not be described as exact-head green until the host/release and connected-emulator runs are observed for one frozen commit. Even after that, the project remains a feature-rich alpha until the physical-device, signed-artifact, visual, and store gates above are complete.
