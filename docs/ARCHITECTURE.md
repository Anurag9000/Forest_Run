# Forest Run — Technical Architecture

This document describes the canonical `main` branch. It separates implemented contracts from claims that still require exact-head Android, emulator, physical-device, signing, or store evidence.

## 1. Platform and build

- Kotlin custom `SurfaceView`/`Canvas` engine; no Compose
- Namespace/application ID: `com.anurag9000.forestrun`
- Debug application ID: `com.anurag9000.forestrun.debug`
- Min SDK 24; compile/target SDK 36
- Java 17 Android bytecode target; Java 21 CI runtime
- Fixed landscape orientation pending final product/device acceptance
- Release build uses R8 minification and resource shrinking
- Signing accepts external properties/environment variables only
- Direct coherent commits to `main`; no active development branches or pull requests

Source layout:

```text
app/src/main/java/com/anurag9000/forestrun/
├── MainActivity.kt
├── engine/      lifecycle, game loop, state, persistence, audio, haptics
├── entities/    Player, Entity base, flora, trees, birds, animals
├── systems/     particles, Seed Orbs, ghost recording/playback/persistence
├── ui/          menu, HUD, Garden, rest/game-over, dialogue, debug tools
└── utils/       bitmap and math helpers
```

Sprites are under `app/src/main/assets/sprites/`; audio is under `app/src/main/res/raw/`.

## 2. Activity and surface lifecycle

`MainActivity` owns Android lifecycle integration, safe-area insets, settings initialization, save repair, repeated `singleTask` intents, and `GameView` creation/teardown.

Manifest behavior includes:

- `launchMode="singleTask"`
- `screenOrientation="landscape"`
- `configChanges="orientation|screenSize|keyboardHidden|screenLayout"`
- immersive and keep-screen-on behavior
- `VIBRATE` permission

Repeated launch intents flow through `onNewIntent`. `LatestRequestGate` invalidates stale asynchronous debug-scenario retries after a newer intent or Activity destruction. Audio and haptic owners have explicit teardown/recreation paths.

Debug recovery maintenance runs after `SaveIntegrityManager.repair(...)`. Cold `onCreate` may inspect, safely recover, or deliberately discard one confirmed evidence domain before `GameView` exists. A reused live Activity is inspection-only; mutating commands reject with `reason=active_session`. Intent extras are consumed once in `finally`, and non-debuggable builds reject the surface.

## 3. Game thread and telemetry

`GameThread`:

1. computes bounded `deltaTime`;
2. invokes `GameView.update(...)`;
3. renders through a locked `Canvas`;
4. records update, render, and total processing times;
5. sleeps for the remainder of the nominal 60 Hz budget.

Shutdown uses interruption plus bounded join, restores caller interruption, and refuses to render a stale frame after stop.

`FramePerformanceMonitor` uses fixed-size primitive ring buffers without per-frame allocation. Process-wide telemetry exposes sampled/cumulative frame counts, mean update/render/processing time, p50/p95/p99 processing time, maximum time, budget overruns, and Java heap observations.

Instrumentation enables measurement but does not prove representative-device performance, allocation, GC, thermal, audio-thread, I/O, memory, or long-run acceptance.

## 4. Runtime state ownership

`AppGameState` actively uses `MENU`, `GARDEN`, and `PLAYING`. Legacy `BLOOM` and `REST` entries remain for compatibility and must not drive new runtime flow.

`RunState` owns `PLAYING`, `DYING`, `GAME_OVER`, and `RESTARTING`. `RunResetManager` advances death timing and restart mechanics. `RunSessionTransitionPlanner` is the pure transition table, `RunSessionTransitionCoordinator` executes its ordered effects, and `LiveRunSessionEffects` adapts those effects to the current Android/game owners. `GameView` adopts a transition only after all required effects succeed, so screen/run state cannot advance after a failed live effect.

Bloom is an orthogonal flag owned by `GameStateManager`; it does not replace locomotion state.

## 5. Player locomotion and input

`InputHandler` arbitrates tap, hold, swipe-down, cancellation, and silent reset before Player mutation. Input is accepted only when application and run state permit gameplay.

Jump behavior preserves immediate full-force ascent, hold-dependent release cap, quick-tap minimum force, longer-hold retained force, no upward energy on release, deterministic apex gravity, and deterministic landing.

Player update rejects nonfinite/nonpositive deltas, caps lifecycle catch-up at 50 ms, repairs poisoned kinematics/timers on the next valid frame, normalizes speed, and keeps Bloom presentation finite.

Current locomotion states are running, jump start, jumping, apex, falling, landing, ducking, stumble, and rest. `PlayerState.BLOOM` remains a reserved legacy ordinal because old ghost frames store enum ordinals.

## 6. Game state and economy

`GameStateManager` owns per-run scroll speed, time, distance, score, run/lifetime Seed views, Bloom meter/timer/conversions, input discovery, debuffs, score multipliers, and run mercy/pacifist statistics.

Persistent currency remains authoritative in save infrastructure. Run reset reloads externally changed lifetime Seeds so Garden purchases cannot be overwritten by stale in-memory state. Nonfinite onboarding durations cannot complete discovery falsely.

## 7. Entity lifecycle and encounter arbitration

`EntityManager` owns spawning, updates, reactive mechanics, collision arbitration, terminal outcomes, pass resolution, Bloom conversion, and Seed Orbs.

Pooling remains disabled because entity subclasses carry incompatible timers, projectiles, movement modes, dialogue, and reward state without complete reset contracts.

Random spawning is distance-based. `SpawnPacing.requiredGapPx` stabilizes world-space separation across speed changes. Debug `EncounterDirector` scenarios bypass ordinary persistence.

Each entity resolves once from `PENDING` to one outcome:

```text
HIT
STUMBLE
MERCY
CLEAN_PASS
BLOOM_CONVERTED
```

Collision queries are pure. Selection severity is deterministic:

```text
HIT > STUMBLE > MERCY
```

Only the selected entity receives effects. Collision arbitration precedes pass processing. `CollisionOutcomeDispatcher` is the single result dispatcher for terminal HIT, STUMBLE, MERCY_MISS, and NONE; it delegates ordered behavior to the terminal-impact, terminal-completion, and nonterminal coordinators. Resolved encounters and clean passes persist centrally and once.

Allocation-free expanded-rectangle probes preserve entity-specific mercy geometry without temporary `RectF` allocation.

## 8. Seed Orbs and Bloom conversion

`SeedOrbManager` stages collectibles ahead of the player and removes missed Orbs after screen exit.

During Bloom, a passed pending entity resolves exclusively as `BLOOM_CONVERTED`; ordinary clean-pass, unique-action, and Orb rewards cannot also occur.

Bloom coordinates Player invincibility, aura/trail emitters, world/conversion bursts, HUD states, camera feedback, SFX, music, haptics, and environment response. `GameStateManager` owns the only authoritative Bloom timer; rewards earned during Bloom do not restart it.

## 9. Biomes and background

`BiomeManager` blends Meadow, Orchard, Ancient Grove, Dusk Canyon, and Night Forest.

`ParallaxBackground` owns authored/cached scene composition, parallax layers, sky/ground/foliage transitions, mist, leaves, petals, fireflies, horizon light, speed response, and Bloom response.

Its public frame update rejects malformed delta/speed pairs, applies bounded delta and scroll speed, and repairs poisoned ambience/Bloom clocks before advancement. `BloomPresentationAdmission.level(...)` maps nonfinite Bloom strength to `0f` and clamps finite values to `[0, 1]`; `setBloomState(...)`, atmosphere-profile resolution, and final Bloom drawing all use that shared boundary.

Some scenic layers remain procedural. Final art-direction acceptance or replacement is unresolved.

## 10. Rendering and safe content

Menu, Garden, HUD, rest, and debug content share `SafeContentTransform`, which preserves aspect ratio, maps physical cutout/system-bar bounds into logical coordinates, clips essential content to the safe logical rectangle, and inversely maps touch input.

Menu, HUD, and rest clocks reject malformed deltas, cap lifecycle catch-up, and repair poisoned local animation state. Host geometry tests do not replace phone/tablet/cutout/unusual-aspect acceptance.

Reusable paints, rectangles, cinematic profiles, Bloom objects, indexed hot-path traversals, and one-shot particle presets reduce audited churn but do not replace allocation profiling.

## 11. Particle system

`ParticleManager` owns a fixed-capacity pool and continuous emitters. Named one-shot presets reuse cached `ParticleEmitter` objects. Active traversal uses indexed loops.

Reduced-motion settings apply at the particle-count boundary. Continuous Bloom emitters attach to the Player and are removed on Bloom exit, rest, or reset.

## 12. Ghost recording, identity, persistence, and namespaces

`GhostRecorder` samples pose at 30 Hz for up to twenty minutes. Terminal HIT detaches the completed buffer in O(1). `RunOutcomePersistenceCoordinator` decides eligibility against `GhostPersistenceManager.bestDistanceFloor(...)`, which includes durable and accepted-pending distance for the captured namespace.

The frame file remains `SaveManager` ghost format version 2. `PlayerState` entries must not be removed or reordered without migration.

Two sidecars are scoped to the captured ghost filename:

```text
<ghost>.promotion  transient in-progress receipt
<ghost>.manifest   persistent artifact-to-distance identity
```

### Namespace snapshot

`GhostPersistenceNamespace` contains one preference name and its canonical ghost filename. Capture reads the active preference namespace once and derives the filename rather than independently reading two mutable fields.

Canonical mappings are:

```text
forest_run_prefs              → ghost_run.bin
forest_run_prefs_compat_vN    → ghost_run_compat_vN.bin
```

`NamespaceBoundGhostPromotionArtifactStore` binds `SharedPreferences(namespace.prefsName)` and `AtomicFile(filesDir / namespace.ghostFilename)` at construction. It never consults mutable active namespace state afterward.

Each manager request carries one immutable namespace through recovery, admission, publication, worker execution, receipt, ghost, manifest, distance, and cleanup.

### Sidecar compatibility

Version-1 24-byte sidecars remain readable and contain distance, frame count, and a 64-bit FNV frame fingerprint.

All new writes use version-2 56-byte sidecars that add a 32-byte SHA-256 digest. New writes reject digest-less values.

`GhostRunIdentity` hashes a canonical big-endian stream containing accepted distance raw bits, frame count, and every persisted frame field. Version-2 validation requires both FNV and SHA-256. Changing only distance invalidates the strong association.

SHA-256 provides collision-resistant local identity, not authenticated authorship; no key, MAC, certificate, or signature exists.

### Durable order and scheduling

Accepted frames publish immediately in a concurrent map keyed by namespace. Every worker performs:

```text
version-2 AtomicFile receipt
→ AtomicFile ghost
→ version-2 AtomicFile manifest
→ synchronous monotonic best-distance commit
→ receipt clear
```

The in-memory publication carries namespace, distance, FNV, and SHA-256. Failure cleanup removes only the matching publication across all four dimensions.

Primary and compatibility publications cannot leak into each other. `GhostNamespaceSerialScheduler` preserves FIFO/non-overlap for tasks targeting the same namespace and dispatches different namespace queues onto a fixed two-thread daemon backend. Queues share the bounded backend rather than owning one thread each.

`GhostNamespacePendingWriteRegistry` tracks the latest queued task per namespace. Save admission and explicit recovery block only when that same namespace has unfinished work; an active worker in another namespace does not cause `IO_FAILURE`.

`awaitPendingWrites(...)` waits for every active namespace-latest future under one shared monotonic timeout. Same-namespace FIFO means that latest future also represents all earlier tasks in that queue. The old global `latestSubmittedWrite` shortcut no longer exists.

### Recovery

A pending version-2 receipt validates ghost structure, count, FNV, and SHA-256 over receipt distance plus frames. A pending version-1 receipt validates FNV and writes a version-2 manifest before distance repair.

A nonmatching receipt is abandoned without modifying the existing ghost or threshold; any older manifest is validated using the already-loaded ghost.

A manifest-only repair loads and hashes the ghost only when best distance is below manifest distance. Version-1 manifests upgrade to version 2 before repair. Frame, count, digest, or distance mismatch produces `CORRUPT_MANIFEST`.

When best distance already meets manifest distance, automatic recovery returns without loading the ghost. Explicit maintenance still performs full validation on demand.

Manager disk fallback reads ghost and distance from one bound store before computing and publishing identity.

Ghost files reject oversized, truncated, trailing, nonfinite, invalid-state, and nonmonotonic data. Newer-schema data is preserved rather than destructively rewritten by an older build.

`GhostPlayer` is contextual visual playback only and has no hitbox.

See `docs/GHOST_PROMOTION_RECOVERY.md`, `docs/GHOST_PERSISTENCE_NAMESPACES.md`, and `docs/GHOST_NAMESPACE_SCHEDULING.md`.

## 13. Collision outcomes and persistent memory

Low-level persistence remains split among specialized durability owners, while live application mutations share `ApplicationPersistenceFacade`:

- `SaveManager` — scores, Seeds, summaries, Garden/costume values, ghost compatibility paths;
- `PersistentMemoryManager` — encounters, hits, passes, spares, relationships, return/history signals;
- `RunOutcomePersistenceCoordinator` and ghost promotion stores — independently recoverable terminal/ghost protocols;
- `SaveIntegrityManager` — migration, type repair, bounds, saturation, incomplete-summary rejection, compatibility storage;
- `ApplicationPersistenceFacade` — the live application boundary for terminal outcome commits, encounter/pass/hit memory, Garden purchases, wardrobe writes, feedback settings, and recovery maintenance.

The facade deliberately does **not** claim a global ACID transaction across SharedPreferences, AtomicFile ghost artifacts, and recovery journals. Each durability domain retains its own atomic/recovery protocol.

### Immediate terminal HIT impact owner

`TerminalHitImpactCoordinator` owns the behavior-sensitive immediate sequence:

```text
record run hit
→ suppress ghost for 1.35 seconds
→ Player rest
→ camera shake
→ hit SFX
→ rest music
→ long haptic
→ post-impact capture callback
```

The shared `LiveCollisionEffects` adapter maps terminal and nonterminal live effects one-to-one to `GameStateManager`, `GhostPlayer`, `Player`, camera, audio, haptic, particle, and flash owners. The former private terminal/nonterminal `GameView` effect adapters no longer exist.

The capture callback runs only after all effects and captures detached ghost, killer, biome, route tier, and Player coordinates. `TerminalHitImpactCapture` requires presentation and completion killer identities to match.

`GameView` captures the live collision inputs once and calls `CollisionOutcomeDispatcher`. A terminal dispatch result supplies the completed summary; `GameView` stores that presentation state and emits `RunSessionEvent.TERMINAL_COLLISION_COMPLETED`. The session transition coordinator owns the death effect and `DYING` transition. `GameView` no longer invokes terminal impact/completion coordinators or death state mutation directly.

### Terminal completion owner

`TerminalHitOutcomeCoordinator` owns:

```text
persistent known-killer relationship hit
→ authored HIT bubble/flavor
→ exactly one summary snapshot
→ rest quote
→ completed summary
→ exactly one RunOutcomeCommitter call
→ result
```

Production adapters isolate relationship recording, feedback presentation, and rest-quote resolution.

### Nonterminal owner

`NonTerminalCollisionOutcomeCoordinator` owns ordered STUMBLE and MERCY_MISS work.

STUMBLE preserves run hit, optional persistent relationship hit, ghost suppression, Player stumble, flash, SFX, shake, haptic, authored copy, and selected-entity deactivation.

MERCY_MISS preserves flash, SFX, haptic, authored copy, mercy particles, and shake.

STUMBLE and MERCY_MISS share the same `LiveCollisionEffects` adapter as terminal HIT. Deterministic/persistence-disabled scenarios retain local feedback without permanent relationship writes, while persistent relationship writes flow through the shared application facade.

### Exactly-once non-ghost persistence

`RunOutcomePersistenceCoordinator` claims one per-run token before mode/storage checks. Nonpersistent runs consume it without writes; duplicate delivery returns `ALREADY_COMMITTED`; unresolved corrupt/conflicting evidence returns `RECOVERY_BLOCKED`; incomplete final clear returns `RECOVERY_PENDING`.

The production sink synchronously journals raw summary plus mood, return, and route before/after states. Recovery accepts after-state, advances before-state and verifies, or blocks on a third state.

`SharedPreferencesRunOutcomeSummarySnapshotStore` atomically writes sanitized summary plus expected route count, avoiding replay of hidden increment behavior.

```text
PREPARED
→ mood
→ MOOD_APPLIED
→ return
→ RETURN_APPLIED
→ summary + route
→ SUMMARY_APPLIED
→ clear
```

Ghost and non-ghost protocols remain independently recoverable, not one global terminal transaction.

### Recovery maintenance owner

`RecoveryEvidenceMaintenanceCoordinator` covers `RUN_OUTCOME` and `GHOST_PROMOTION`, reports `CLEAN`, `PENDING`, `CORRUPT`, `BLOCKED`, or `IO_FAILURE`, and separates inspection, safe retry, corrupt discard, and pending discard.

Safe retry preserves corrupt evidence. Pending discard retries canonical recovery. I/O failure never permits deletion. Successful clear is verified.

The run handler cannot publish ghosts or advance distance. The ghost handler never opens the run journal. Ghost inspection validates version-2 distance-bound SHA-256 or version-1 FNV compatibility. Receipt cleanup preserves a valid manifest; manifest cleanup preserves the ghost file. Support output contains only fixed status codes.

`AndroidRecoveryEvidenceMaintenance` captures one immutable namespace during construction. Both evidence handlers use namespace-bound stores, so switching the active `SaveManager` namespace afterward does not redirect inspection, recovery, or cleanup performed by that maintenance instance.

Ordinary players also have a fail-closed recovery surface: `RecoveryEvidencePresentation` produces privacy-safe rows, `RecoveryEvidenceUserController` revalidates every requested action through `ApplicationPersistenceFacade`, and `RecoveryEvidenceDialogCoordinator` is attached by `MainActivity`. Safe retry is non-destructive; corrupt/pending discard requires a second explicit confirmation. Debug/ADB maintenance remains a separate acceptance/support surface.

Deterministic scenarios remain isolated from permanent score, encounter, relationship, Garden, summary, and ghost history while receiving local authored feedback.

Relationship familiarity from appearance is capped at Recognition. Trust and Bond require positive outcomes; hits delay progression. Familiarity warmth delegates to the pure additive `FamiliarityWarmthScoring` model so stage, pass, spare, kindness, and encounter modifiers accumulate independently.

## 14. Garden, return moments, and menu ritual

`GardenLayoutPlanner` supplies visual panels and touch targets. Catalogue, statistics, last-run, wardrobe, and run regions have multiple landscape-size tests.

Garden spending writes persistent currency and cannot be refunded by stale game state. Sanctuary counts are nonnegative. Garden particles update only while active.

Return moments are consumed on visible Garden entry. Day boundaries use local calendar date. Rough-run streaks use saturating arithmetic, and long-absence detection rejects invalid timestamps and clock rollback before subtraction. Returning home resets the willow ritual.

## 15. Text and authored presentation

`DialogueBubbleManager` and `FlavorTextManager` use bounded, wrapped, deduplicated, screen-clamped queues with pre-sized/reused hot-path collections.

`RunFlavorPresentation` coordinates run copy; flora, tree, bird, and animal writing remain separated. Game-over composition and persistence reads are cached rather than rebuilt every draw frame.

## 16. Audio, haptics, and feedback settings

`SfxManager` tracks `SoundPool` readiness and failure. Mandatory assets fail non-debug validation; optional Bloom sounds have explicit fallback.

`LeitmotifManager` owns deterministic music transitions and crossfades; repeated parameter writes are throttled.

Independent persistent settings control reduced motion, music/SFX, and haptics at camera, particle, cinematic, music, SFX, and vibration boundaries. Hardware loudness, latency, vibration intensity, and lifecycle behavior remain acceptance gates.

## 17. Assets and release contracts

`RuntimeAssetValidator` checks required sprites, audio, and fonts outside debug execution. Sprite sheets must decode, divide by frame count, and remain within sane dimensions. Mandatory raw resources must resolve and be readable.

Generated placeholder sprites are rejected in non-debug runtime. Debug placeholder creation validates geometry, overflow, a sixteen-megapixel allocation budget, and paint reuse.

Release signing values are external only:

```text
FOREST_RUN_KEYSTORE
FOREST_RUN_STORE_PASSWORD
FOREST_RUN_KEY_ALIAS
FOREST_RUN_KEY_PASSWORD
```

An unsigned minified bundle is a build artifact, not signed-upload proof.

## 18. Testing and CI

The permanent workflow is read-only and intended to validate the exact event SHA through source contracts, debug/release/unit/instrumentation compilation, JVM/Robolectric tests, lint, APK/AAB assembly, R8-renaming verification, and API-35 instrumentation.

Coverage includes input, physics, malformed frames, Bloom, all entity families, persistence isolation, relationships, Garden, save repair, future schemas, ghost persistence, safe-content geometry, settings, latest-intent lifecycle, shutdown, collision geometry, assets, allocation bounds, telemetry, terminal-impact ordering/failure capture, terminal-completion ordering, nonterminal ordering, exactly-once ownership, non-ghost recovery, v1/v2 receipt/manifest recovery, distance-bound SHA-256 golden identity, legacy upgrade, digest/distance tampering, lazy manifest validation, namespace-bound ghost codecs, per-namespace publication, namespace-scoped pending-write admission, namespace-serial FIFO, bounded cross-namespace overlap, failed-task continuation, all-namespace waiting, maintenance policy, cold-start mutation, one-shot commands, and payload-free logging.

All `scripts/test_*.py` files are discovered by the source-contract job, including `test_terminal_hit_impact_contract.py`, `test_ghost_persistence_namespace_contract.py`, and the namespace-aware `test_ghost_promotion_recovery_contract.py`.

This architecture description does not assert that the current commit has attached successful checks.

## 19. Debug scenarios

`EncounterDirector` scenarios mirror device-acceptance cases and are selectable through launch intents or the overlay. Scenario entities use persistence-disabled context.

Recovery maintenance is a separate debug support surface. Reused Activities permit inspection only; recovery/discard requires cold start before gameplay systems exist. A maintenance instance remains bound to the namespace captured during its construction even if the caller later selects another active namespace.

Debug scenarios and focused harnesses do not replace ordinary-play or physical-device acceptance.

## 20. Known debt and unresolved evidence

- `GameView` remains a large SurfaceView orchestration host, but the previously identified collision-result dispatcher, live collision-effect adapters, and top-level run-session transition table/effect execution have been extracted. Further decomposition should be driven by measured maintainability or device findings rather than broad rewrites.
- Low-level persistence remains intentionally separated by durability domain behind `ApplicationPersistenceFacade`; there is no global transaction across SharedPreferences and AtomicFile protocols.
- Ghost/distance mismatches predating persistent manifests cannot be reconstructed.
- Version-1 sidecars retain noncryptographic identity until replay requires strong upgrade.
- The healthy already-applied path avoids repeated hashing; maintenance performs full validation.
- SHA-256 identifies content/distance but does not authenticate a trusted writer.
- Source integration does not replace physical-device fairness, TalkBack, performance/thermal/battery, signed-install, store-delivery, or final asset/policy acceptance evidence.
- Ghost and non-ghost recovery are independent rather than one global transaction.
- Automatic recovery remains fail-closed; ordinary players now have a privacy-safe retry/discard UI with explicit destructive confirmation, while debug/support maintenance remains available for acceptance and diagnosis.
- Simultaneous two-namespace AtomicFile activity and process-death recovery require exact-head Android and physical-device acceptance.
- Exact-head Gradle, lint, build, emulator, physical-device, ADB, signing, installation, store path, screenshots, metadata, privacy/data-safety, content rating, and current Play-policy evidence remain unresolved.
- Entity readability, artwork/animation—including Wolf—fixed landscape, procedural scenic layers, audio/haptics, frame time, allocation, GC, memory, I/O, thermal, and long-run behavior require representative-device acceptance.

See `docs/RELEASE.md`. The project remains a feature-rich alpha until those gates are satisfied.
