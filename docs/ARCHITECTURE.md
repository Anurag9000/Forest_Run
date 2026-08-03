# Forest Run — Technical Architecture

This document describes the canonical `main` branch. It distinguishes implemented contracts from release claims that still require physical-device evidence.

## 1. Platform and build

- Language: Kotlin
- Rendering: custom `SurfaceView`/`Canvas` engine; no Compose
- Namespace/application ID: `com.anurag9000.forestrun`
- Debug application ID: `com.anurag9000.forestrun.debug`
- Min SDK: 24
- Compile/target SDK: 36
- Android bytecode target: Java 17
- CI runtime: Java 21
- Orientation: fixed landscape, pending final product/device acceptance
- Release build: R8 minification plus resource shrinking
- Signing: optional external Gradle/environment credentials; no key material is committed
- Repository workflow: coherent direct commits to `main`; no active development branches or pull requests

Source layout:

```text
app/src/main/java/com/anurag9000/forestrun/
├── MainActivity.kt
├── engine/      lifecycle, game loop, state, persistence, audio, haptics
├── entities/    Player, Entity base, flora, trees, birds, animals
├── systems/     particles, seed orbs, ghost recording/playback/persistence
├── ui/          menu, HUD, Garden, rest/game-over, dialogue, debug tools
└── utils/       bitmap and math helpers
```

Sprites are packaged under `app/src/main/assets/sprites/`; audio is packaged under `app/src/main/res/raw/`.

## 2. Activity and surface lifecycle

`MainActivity` owns Android lifecycle integration, safe-area insets, feedback settings initialization, save repair, repeated `singleTask` intents, and creation/teardown of `GameView`.

The manifest currently uses:

- `launchMode="singleTask"`
- `screenOrientation="landscape"`
- `configChanges="orientation|screenSize|keyboardHidden|screenLayout"`
- immersive/keep-screen-on behavior
- `VIBRATE` permission

Repeated launch intents are handled through `onNewIntent` rather than relying on Activity recreation. Every asynchronous debug-launch retry carries an identity token from `LatestRequestGate`; a newer intent or Activity destruction invalidates all older retries before they can reach `GameView`. Audio and haptic managers have explicit teardown/recreation paths.

Debug-only recovery maintenance intents are processed after `SaveIntegrityManager.repair(...)`. A cold `onCreate` may inspect, safely retry, or deliberately discard one confirmed recovery-evidence domain before `GameView` exists. A reused `singleTask` Activity is inspection-only: recover and discard commands reject with `reason=active_session` before constructing maintenance handlers, preventing races with gameplay or the ghost worker. Command extras are consumed once in `finally`, and non-debuggable builds reject the surface through `ApplicationInfo.FLAG_DEBUGGABLE`.

## 3. Game thread

`GameThread` drives update and rendering on one dedicated thread:

1. compute a bounded `deltaTime`;
2. invoke `GameView.update(deltaTime)`;
3. render through a locked `Canvas`;
4. record update, render, and total processing durations;
5. sleep for the remainder of the nominal 60 Hz budget.

Shutdown uses interruption plus a bounded join. It restores caller interruption and refuses to render a stale frame after a stop request.

### Frame telemetry

`FramePerformanceMonitor` records primitive nanosecond samples into fixed-size ring buffers without allocating per frame. `FramePerformanceTelemetry` exposes a process-wide context-free monitor so measurements survive Activity and Surface recreation.

Out-of-band snapshots report:

- sampled and cumulative frame counts;
- mean update/render/processing duration;
- p50/p95/p99 processing duration;
- maximum processing duration;
- frames exceeding the 60 Hz processing budget;
- current/max Java heap observations.

This instrumentation enables profiling; it does **not** prove performance acceptance. Representative-device frame time, allocation, GC, I/O, audio-thread, thermal, memory, and long-run measurements remain release gates.

## 4. Runtime state ownership

Three different concerns are intentionally separate.

### Active application screen

`AppGameState` actively uses:

- `MENU`
- `GARDEN`
- `PLAYING`

Legacy `BLOOM` and `REST` enum entries are retained only for compatibility and must not drive new runtime flow.

### Death/restart flow

`RunState` owns:

- `PLAYING`
- `DYING`
- `GAME_OVER`
- `RESTARTING`

`RunResetManager` advances death timing, restart fade, reset execution, and return to the Garden.

### Bloom

Bloom is an orthogonal power flag owned by `GameStateManager`. It does not replace locomotion state. Gravity, jumping, falling, landing, and ducking continue while Bloom is active.

## 5. Player locomotion and input

`InputHandler` arbitrates tap, hold, swipe-down, cancellation, and silent reset before mutating player locomotion. Gameplay callbacks are accepted only while both application and run states permit live input.

Player jump behavior:

1. press starts an immediate full-force ascent for responsiveness;
2. release caps upward velocity according to hold duration;
3. quick taps approach `MIN_JUMP_FORCE`;
4. longer holds preserve more of the initial `MAX_JUMP_FORCE`;
5. release never adds upward energy;
6. apex gravity and landing transitions remain deterministic.

The public Player update boundary rejects non-finite/non-positive deltas, caps lifecycle catch-up to one 50 ms render budget, repairs poisoned kinematics/timers on the next valid frame, normalizes run speed, and keeps Bloom presentation inputs finite.

Current locomotion states are running, jump start, jumping, apex, falling, landing, ducking, stumble, and rest. `PlayerState.BLOOM` is a reserved legacy ordinal because old ghost frames store enum ordinals; current Bloom uses `Player.isInvincible` instead.

## 6. Game-state and economy ownership

`GameStateManager` owns mutable per-run values such as:

- scroll speed, time, distance, and score;
- run and lifetime Seed views;
- Bloom meter, active flag, timer, and conversion count;
- opening input discovery;
- debuffs and score multipliers;
- run-level mercy/pacifist statistics.

Persistent currency remains loaded through save infrastructure. Run resets reload externally changed lifetime Seeds so Garden spending cannot be overwritten by stale in-memory state. Non-finite onboarding hold durations cannot falsely complete input discovery.

## 7. Entity lifecycle and encounter arbitration

`EntityManager` owns spawning, updates, player-reactive mechanics, collision arbitration, terminal outcomes, pass resolution, Bloom conversion, and Seed Orbs.

Entity pooling is deliberately disabled. Concrete entities contain incompatible timers, projectiles, movement modes, dialogue state, and reward state; reuse is unsafe until every class has a complete reset contract.

Random spawning is distance-based. `SpawnPacing.requiredGapPx` keeps world-space separation stable as scroll speed changes. Deterministic `EncounterDirector` scenarios bypass ordinary random persistence.

Every entity begins with `EncounterOutcome.PENDING` and may resolve once to exactly one terminal outcome:

- `HIT`
- `STUMBLE`
- `MERCY`
- `CLEAN_PASS`
- `BLOOM_CONVERTED`

Collision queries are pure. `EntityManager` selects one overlap with deterministic severity:

```text
HIT > STUMBLE > MERCY
```

Only the selected entity receives effects. Collision arbitration precedes pass processing. Resolved encounters and ordinary clean passes are persisted centrally and once.

### Allocation-free mercy geometry

The `Entity` base provides allocation-free expanded-rectangle probes with symmetric, axis-specific, or per-edge padding. Entity-specific safe windows—including Vanilla Orchid, Bamboo, Cherry Blossom, Jacaranda, and Weeping Willow—retain their original geometry without constructing temporary `RectF` objects during collision queries.

## 8. Seed Orbs and Bloom conversion

`SeedOrbManager` stages collectible Orbs ahead of the player and removes missed Orbs after they leave the screen.

During Bloom, a passed pending entity resolves as `BLOOM_CONVERTED`. Conversion is exclusive: it cannot also award ordinary clean-pass, entity unique-action, or Orb rewards.

Bloom presentation coordinates:

- player invincibility and continuous aura/trail emitters;
- world and conversion bursts;
- HUD readiness/active/afterglow states;
- camera feedback;
- SFX, music, and haptics;
- environment response.

`GameStateManager` owns the only authoritative Bloom timer. Rewards earned while Bloom is already active do not restart it.

## 9. Biomes and background

`BiomeManager` selects and blends five biome identities:

- Meadow
- Orchard
- Ancient Grove
- Dusk Canyon
- Night Forest

`ParallaxBackground` owns authored/cached scene composition, parallax layers, sky/ground/foliage transitions, mist, leaves, petals, fireflies, horizon light, speed response, and Bloom response.

Some scenic layers remain procedural. Final art-direction acceptance or replacement is explicitly unresolved.

## 10. Rendering and safe content

Essential menu, Garden, HUD, rest, and debug content share one `SafeContentTransform`:

- preserves aspect ratio;
- maps physical cutout/system-bar bounds into logical coordinates;
- clips essential content to the safe logical rectangle;
- inversely maps touch coordinates back into that logical space.

Menu, HUD, and Rest presentation clocks reject malformed deltas, cap lifecycle catch-up to 50 ms, and repair previously poisoned local animation state. Geometry is covered by host tests, but phone/tablet/cutout/unusual-aspect acceptance remains a physical-device gate.

Paints, reusable rectangles, cinematic profiles, Bloom presentation objects, hot-path traversals, and one-shot particle emitters are cached or reused where currently audited. These safeguards reduce known churn; they are not a substitute for allocation profiling.

## 11. Particle system

`ParticleManager` owns a fixed-capacity particle pool and continuous emitters. Named one-shot presets reuse cached `ParticleEmitter` instances rather than constructing an emitter for every event. Active particle traversal uses indexed loops to avoid iterator churn.

Reduced-motion settings are applied at the particle-count boundary. Continuous Bloom emitters are attached to the player and explicitly removed on Bloom exit, rest, or reset.

## 12. Ghost recording, playback, and persistence

`GhostRecorder` samples player pose at 30 Hz for up to twenty minutes. On a terminal hit, the completed buffer is detached in O(1) regardless of run mode. `RunOutcomePersistenceCoordinator` decides whether the detached buffer is eligible to replace the best ghost.

Eligibility compares completed distance against `GhostPersistenceManager.bestDistanceFloor(...)`, which includes durable best distance and any accepted in-memory promotion still awaiting worker completion. A shorter run cannot queue behind and overwrite a longer pending candidate.

The validated frame payload remains `SaveManager` ghost format version 2. New promotions add two fixed-size AtomicFile sidecars without changing that codec:

```text
<ghost>.promotion  transient in-progress receipt
<ghost>.manifest   persistent artifact-to-distance identity
```

Both store distance, frame count, and a raw-bit fingerprint of every persisted frame component. Accepted frames remain immediately visible in playback memory, while the single daemon worker performs:

```text
AtomicFile promotion receipt
→ AtomicFile ghost write
→ AtomicFile artifact manifest
→ synchronous monotonic best-distance commit
→ receipt clear
```

The manifest remains after receipt clearing, making newly promoted artifact bundles self-describing. A matching receipt can reconstruct a missing or stale manifest. When only a manifest remains, a lower threshold is repaired only after full ghost validation. When the threshold already meets the manifest distance, automatic recovery returns without loading or hashing the ghost, avoiding repeated long-file work during healthy startup.

Corrupt receipts, corrupt manifests, manifest/artifact mismatch before a required repair, and I/O failure block new promotions. Explicit maintenance inspection performs full manifest identity validation and can remove corrupt association evidence without deleting the ghost frame file.

Ghost files reject oversized, truncated, trailing, non-finite, invalid-state, and non-monotonic data. Newer-schema ghost data is preserved rather than destructively rewritten by an older build.

`GhostPlayer` provides context-aware visibility around the live player and hazards. Ghosts have no gameplay hitbox.

Legacy ghost frames store `PlayerState.ordinal`; PlayerState entries must not be removed or reordered without a migration.

## 13. Collision outcomes and persistent memory

Persistence remains split by storage responsibility:

- `SaveManager`: scores, Seeds, run summaries, Garden/costume values, ghost compatibility paths;
- `PersistentMemoryManager`: encounters, hits, passes, spares, relationships, return/history signals;
- `SaveIntegrityManager`: schema migration, type repair, bounds, saturating counters, incomplete-summary rejection, and compatibility storage.

Terminal `HIT` processing has two explicit coordinator layers.

### Immediate terminal gameplay owner

`GameView` retains only the live terminal impact sequence:

- record the run-level hit;
- suppress the ghost;
- trigger Player rest;
- invoke camera, SFX, music, and haptic impact feedback;
- detach the completed ghost;
- resolve the killer;
- call `TerminalHitOutcomeCoordinator.complete(...)` once;
- store the returned summary;
- trigger death timing and enter `RunState.DYING`.

### Terminal completion owner

`TerminalHitOutcomeCoordinator` owns the behavior-preserving deterministic completion sequence:

1. record known-killer relationship history when permanent progression is allowed;
2. present the canonical authored HIT bubble and flavor line;
3. invoke the live summary builder exactly once;
4. resolve the rest quote from the preview, biome, and killer;
5. create one completed summary;
6. invoke `RunOutcomeCommitter` exactly once;
7. return the completed summary and commit result.

The production adapters are `AndroidTerminalHitRelationshipRecorder`, `AndroidTerminalHitFeedbackPresenter`, and `AndroidTerminalHitRestQuoteResolver`. Their interfaces are replaced with recording fakes in pure ordering tests.

### Nonterminal outcome owner

`STUMBLE` and `MERCY_MISS` branches capture immutable inputs and delegate once to `NonTerminalCollisionOutcomeCoordinator`.

For STUMBLE, the coordinator preserves:

```text
run hit accounting
→ persistent known-killer relationship hit
→ 0.9-second ghost suppression
→ Player stumble
→ biome-dominant flash
→ nonlethal hit SFX
→ hit camera shake
→ medium haptic
→ authored STUMBLE bubble/flavor copy
→ selected-entity deactivation
```

For MERCY_MISS, it preserves:

```text
green mercy flash
→ mercy-miss SFX
→ double-tap haptic
→ authored mercy bubble/flavor copy
→ mercy stars at Player center
→ mercy camera shake
```

`AndroidNonTerminalCollisionFeedbackPresenter` owns authored-copy selection and presentation geometry. `AndroidNonTerminalCollisionRelationshipRecorder` owns the extracted STUMBLE relationship write. `GameViewNonTerminalCollisionEffects` is a private inner adapter for live state that remains private to `GameView`, including Player, ghost, flash, camera, audio, haptic, and particle mutations.

Deterministic or persistence-disabled STUMBLE encounters retain local mechanics and feedback but do not write permanent relationship history.

### Exactly-once and recoverable persistence owners

`RunOutcomePersistenceCoordinator` implements `RunOutcomeCommitter` and owns one per-run terminal token:

- it claims the token before checking run mode or touching a sink;
- non-persistent deterministic runs consume the token without writing;
- repeated or re-entrant terminal delivery returns `ALREADY_COMMITTED`;
- coordinator construction and both run-start paths retry older non-ghost recovery evidence;
- corrupt or conflicting non-ghost evidence returns `RECOVERY_BLOCKED`;
- an applied non-ghost bundle whose final clear failed returns `RECOVERY_PENDING`.

Before ghost eligibility or progression writes, the production sink synchronously journals:

- raw completed summary;
- forest-mood before and expected after-state;
- return-moment before and expected after-state;
- pacifist-route count before and expected after-state.

Recovery compares live state with both snapshots. An already-applied state is accepted without replay; an unchanged before-state is advanced and verified; any third state is a conflict.

`SharedPreferencesRunOutcomeSummarySnapshotStore` writes the sanitized last-run summary and expected route counter in one synchronous transaction, avoiding replay of the hidden counter increment inside `SaveManager.saveLastRunSummary`.

The non-ghost order is:

```text
PREPARED journal
→ mood state
→ MOOD_APPLIED
→ return state
→ RETURN_APPLIED
→ atomic summary plus route count
→ SUMMARY_APPLIED
→ journal clear
```

Ghost and best-distance promotion use the independent receipt/manifest protocol described in section 12. The terminal coordinator submits one distance-aware candidate but never writes the threshold itself. The two protocols protect their own state surfaces; they do not form one global transaction across relationship history, presentation, non-ghost progression, ghost storage, and best distance.

### Recovery evidence maintenance owner

`RecoveryEvidenceMaintenanceCoordinator` provides one policy layer over the independent `RUN_OUTCOME` and `GHOST_PROMOTION` domains. It reports `CLEAN`, `PENDING`, `CORRUPT`, `BLOCKED`, or `IO_FAILURE`, and separates inspection, safe retry, corrupt-evidence discard, and unresolved-pending discard.

Safe retry never clears corrupt evidence. Corrupt discard requires a fresh confirmed corrupt state. Pending discard retries canonical recovery first, returns `RECOVERED_INSTEAD` when recovery succeeds, and clears only evidence still confirmed pending or blocked. Read/recovery I/O failure never authorizes deletion, and successful clear is verified by reinspection.

Production handlers are domain-isolated. `MaintenanceRunOutcomePersistenceSink` can recover complete mood, return, summary, and route snapshots but cannot publish ghosts or advance best distance. The ghost handler owns the promotion receipt, persistent manifest, ghost artifact adapter, and canonical ghost recovery; it never opens the run-outcome journal.

Ghost inspection distinguishes valid manifest, pending receipt, corrupt receipt, corrupt manifest, and manifest/artifact mismatch. Targeted receipt cleanup preserves a valid matching manifest. Targeted manifest cleanup preserves the ghost frame file. Support summaries expose only states and fixed detail codes.

`MainActivity` exposes maintenance only in debuggable builds. Mutating commands run only during cold `onCreate`, after save repair and before `GameView`; a reused live Activity may inspect but cannot recover or discard evidence.

Deterministic scenarios are isolated from permanent score, encounter, relationship, Garden, summary, and ghost history while still receiving local authored feedback.

Relationship familiarity from appearances is capped at Recognition. Trust and Bond require meaningful positive outcomes; hits delay progression.

## 14. Garden, return moments, and menu ritual

The Garden uses a shared `GardenLayoutPlanner` for visual panels and touch targets. Catalogue, statistics, last-run, wardrobe, and run regions are tested at multiple landscape sizes.

Garden spending writes through persistent currency and cannot be refunded by stale game state. Sanctuary counts are clamped non-negative. Garden particles update only while the screen is active.

Return moments are consumed on visible Garden entry rather than hidden construction. Day boundaries use the local calendar date. Returning home resets the willow menu ritual.

## 15. Text and authored presentation

`DialogueBubbleManager` and `FlavorTextManager` use bounded, wrapped, deduplicated, screen-clamped presentation queues. Their hot-path collections are pre-sized/reused.

Run-level text is coordinated by `RunFlavorPresentation`; family-specific writing is separated into flora, tree, bird, and animal flavor modules.

Game-over composition and persistence reads are cached rather than rebuilt every draw frame.

## 16. Audio, haptics, and feedback settings

`SfxManager` explicitly tracks `SoundPool` sample readiness and failures. Mandatory assets fail non-debug runtime validation; optional Bloom sounds have explicit fallback behavior.

`LeitmotifManager` owns music-state transitions and deterministic crossfade ownership. Repeated parameter writes are throttled.

Persistent independent settings control:

- reduced motion;
- music/SFX;
- haptics.

They are enforced at camera, particle, cinematic, music, SFX, and vibration boundaries. Actual loudness, latency, vibration intensity, and lifecycle behavior still require hardware acceptance.

## 17. Assets and release contracts

`RuntimeAssetValidator` checks required sprites, mandatory audio, and fonts outside debug execution. Sprite sheets must decode, divide cleanly by frame count, and remain within sane dimensions. Mandatory raw resources must resolve and contain readable data.

Generated placeholder sprites are rejected for non-debug runtime. Debug placeholder construction validates frame geometry, detects multiplication overflow, enforces a sixteen-megapixel allocation budget, and reuses paints across frames.

Release signing values are accepted only from external properties/environment variables:

- `FOREST_RUN_KEYSTORE`
- `FOREST_RUN_STORE_PASSWORD`
- `FOREST_RUN_KEY_ALIAS`
- `FOREST_RUN_KEY_PASSWORD`

The unsigned minified bundle is an automated build artifact, not proof that a signed upload artifact works.

## 18. Testing and CI

Permanent CI is read-only and validates the exact event SHA. It performs:

- repository/source contract checks;
- debug/release/unit/instrumentation compilation;
- full JVM/Robolectric suite;
- debug and release lint;
- debug and instrumentation APK assembly;
- minified/resource-shrunk AAB build;
- effective R8-renaming verification;
- API 35 connected instrumentation;
- exact assertion of fourteen tests with zero failures, errors, or skips.

The test suite covers input arbitration, physics, malformed frame boundaries, Bloom, encounter outcomes, all entity families, persistence isolation, relationships, Garden transactions/layout, save repair, future-schema behavior, ghost persistence, safe-content geometry, feedback settings, latest-intent lifecycle ownership, thread shutdown, collision geometry, runtime assets, placeholder allocation bounds, hot-path reuse, frame telemetry, terminal-hit completion ordering/presentation, nonterminal collision ordering/presentation, terminal-run exactly-once ownership, non-ghost crash recovery, receipt/manifest ghost crash recovery, journal/receipt/manifest validation, transition parity, atomic summary-route snapshots, pending-distance admission, frame fingerprint identity, receipt-free manifest repair, already-applied lazy validation, recovery-evidence maintenance policy and Android integration, cold-start mutation gating, one-shot command consumption, and payload-free maintenance logging.

## 19. Debug scenarios

Debug-only `EncounterDirector` scenarios mirror the device-acceptance checklist and can be selected through repeated launch intents or the in-game overlay. Scenario entities use persistence-disabled context.

Recovery maintenance intents are a separate debug support surface. Live reused Activities permit inspection only; recovery or discard requires a cold start before gameplay systems exist.

Debug scenarios are deterministic test aids, not substitutes for ordinary-play and physical-device acceptance.

## 20. Known architectural debt and unresolved release evidence

The following remain intentionally open:

- `GameView` is still a large coordinator and should be decomposed incrementally after behavioral stability;
- the complete collision-result `when` dispatcher remains in `GameView`, although each nonterminal result branch delegates its ordered work;
- STUMBLE and MERCY_MISS live effects remain implemented by the private `GameViewNonTerminalCollisionEffects` adapter;
- immediate HIT impact still directly coordinates Player, ghost, camera, audio, music, and haptic managers before the extracted completion seam;
- ghost/distance mismatches that already existed before persistent artifact manifests cannot be reconstructed;
- the 64-bit ghost fingerprint is noncryptographic and has a theoretical collision risk;
- healthy already-applied automatic recovery avoids repeated full ghost hashing, while explicit maintenance inspection performs full identity validation;
- non-ghost and ghost recovery evidence are independently recoverable rather than one global terminal transaction;
- concurrent compatibility-namespace switching during an active ghost worker or maintenance instance is unsupported;
- automatic recovery remains fail-closed; deliberate remediation is debug/support-only and has no end-user recovery UI;
- physical-device ADB maintenance acceptance remains outstanding;
- entity mechanic/readability claims need ordinary-play and hardware acceptance;
- frame, allocation, GC, memory, I/O, audio-thread, thermal, and long-run metrics need measured thresholds on representative hardware;
- fixed landscape and procedural scenic layers need final product decisions;
- artwork and animation sheets, including Wolf, need visual inspection;
- real upload credentials, signed artifact installation, store-path testing, screenshots, metadata, privacy/data-safety, content rating, and current Play-policy review remain release gates.

See `docs/RELEASE.md` for the evidence checklist. No documentation statement should promote the project beyond a feature-rich alpha until those gates are complete.