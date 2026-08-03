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

`RunState` owns `PLAYING`, `DYING`, `GAME_OVER`, and `RESTARTING`. `RunResetManager` advances death timing, restart fade, reset, and return to the Garden.

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

Only the selected entity receives effects. Collision arbitration precedes pass processing. Resolved encounters and clean passes persist centrally and once.

Allocation-free expanded-rectangle probes preserve entity-specific mercy geometry without temporary `RectF` allocation.

## 8. Seed Orbs and Bloom conversion

`SeedOrbManager` stages collectibles ahead of the player and removes missed Orbs after screen exit.

During Bloom, a passed pending entity resolves exclusively as `BLOOM_CONVERTED`; ordinary clean-pass, unique-action, and Orb rewards cannot also occur.

Bloom coordinates Player invincibility, aura/trail emitters, world/conversion bursts, HUD states, camera feedback, SFX, music, haptics, and environment response. `GameStateManager` owns the only authoritative Bloom timer; rewards earned during Bloom do not restart it.

## 9. Biomes and background

`BiomeManager` blends Meadow, Orchard, Ancient Grove, Dusk Canyon, and Night Forest.

`ParallaxBackground` owns authored/cached scene composition, parallax layers, sky/ground/foliage transitions, mist, leaves, petals, fireflies, horizon light, speed response, and Bloom response.

Some scenic layers remain procedural. Final art-direction acceptance or replacement is unresolved.

## 10. Rendering and safe content

Menu, Garden, HUD, rest, and debug content share `SafeContentTransform`, which preserves aspect ratio, maps physical cutout/system-bar bounds into logical coordinates, clips essential content to the safe logical rectangle, and inversely maps touch input.

Menu, HUD, and rest clocks reject malformed deltas, cap lifecycle catch-up, and repair poisoned local animation state. Host geometry tests do not replace phone/tablet/cutout/unusual-aspect acceptance.

Reusable paints, rectangles, cinematic profiles, Bloom objects, indexed hot-path traversals, and one-shot particle presets reduce audited churn but do not replace allocation profiling.

## 11. Particle system

`ParticleManager` owns a fixed-capacity pool and continuous emitters. Named one-shot presets reuse cached `ParticleEmitter` objects. Active traversal uses indexed loops.

Reduced-motion settings apply at the particle-count boundary. Continuous Bloom emitters attach to the Player and are removed on Bloom exit, rest, or reset.

## 12. Ghost recording, identity, and persistence

`GhostRecorder` samples pose at 30 Hz for up to twenty minutes. Terminal HIT detaches the completed buffer in O(1). `RunOutcomePersistenceCoordinator` decides eligibility against `GhostPersistenceManager.bestDistanceFloor(...)`, which includes durable and accepted-pending distance.

The frame file remains `SaveManager` ghost format version 2. `PlayerState` entries must not be removed or reordered without migration.

Two sidecars are scoped to the active ghost filename:

```text
<ghost>.promotion  transient in-progress receipt
<ghost>.manifest   persistent artifact-to-distance identity
```

### Sidecar compatibility

Version-1 24-byte sidecars remain readable and contain distance, frame count, and a 64-bit FNV frame fingerprint.

All new writes use version-2 56-byte sidecars that add a 32-byte SHA-256 digest. New writes reject digest-less values.

`GhostRunIdentity` hashes a canonical big-endian stream containing accepted distance raw bits, frame count, and every persisted frame field. Version-2 validation requires both FNV and SHA-256. Changing only distance invalidates the strong association.

SHA-256 provides collision-resistant local identity, not authenticated authorship; no key, MAC, certificate, or signature exists.

### Durable order

Accepted frames publish immediately in memory. The worker performs:

```text
version-2 AtomicFile receipt
→ AtomicFile ghost
→ version-2 AtomicFile manifest
→ synchronous monotonic best-distance commit
→ receipt clear
```

The in-memory publication carries distance, FNV, and SHA-256. Failure cleanup removes only the matching publication across all three dimensions.

### Recovery

A pending version-2 receipt validates ghost structure, count, FNV, and SHA-256 over receipt distance plus frames. A pending version-1 receipt validates FNV and writes a version-2 manifest before distance repair.

A nonmatching receipt is abandoned without modifying the existing ghost or threshold; any older manifest is validated using the already-loaded ghost.

A manifest-only repair loads and hashes the ghost only when best distance is below manifest distance. Version-1 manifests upgrade to version 2 before repair. Frame, count, digest, or distance mismatch produces `CORRUPT_MANIFEST`.

When best distance already meets manifest distance, automatic recovery returns without loading the ghost. Explicit maintenance still performs full validation on demand.

Ghost files reject oversized, truncated, trailing, nonfinite, invalid-state, and nonmonotonic data. Newer-schema data is preserved rather than destructively rewritten by an older build.

`GhostPlayer` is contextual visual playback only and has no hitbox.

## 13. Collision outcomes and persistent memory

Persistence remains split among:

- `SaveManager` — scores, Seeds, summaries, Garden/costume values, ghost compatibility paths;
- `PersistentMemoryManager` — encounters, hits, passes, spares, relationships, return/history signals;
- `SaveIntegrityManager` — migration, type repair, bounds, saturation, incomplete-summary rejection, compatibility storage.

### Immediate terminal gameplay owner

`GameView` retains the live HIT sequence:

```text
record run hit
→ suppress ghost
→ Player rest
→ camera/SFX/music/haptic feedback
→ detach ghost
→ resolve killer
→ TerminalHitOutcomeCoordinator.complete(...)
→ store returned summary
→ trigger death timing and DYING
```

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

`GameViewNonTerminalCollisionEffects` remains a private live-state adapter for Player, ghost, flash, camera, audio, haptic, and particles. Deterministic/persistence-disabled scenarios retain local feedback without permanent relationship writes.

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

Deterministic scenarios remain isolated from permanent score, encounter, relationship, Garden, summary, and ghost history while receiving local authored feedback.

Relationship familiarity from appearance is capped at Recognition. Trust and Bond require positive outcomes; hits delay progression.

## 14. Garden, return moments, and menu ritual

`GardenLayoutPlanner` supplies visual panels and touch targets. Catalogue, statistics, last-run, wardrobe, and run regions have multiple landscape-size tests.

Garden spending writes persistent currency and cannot be refunded by stale game state. Sanctuary counts are nonnegative. Garden particles update only while active.

Return moments are consumed on visible Garden entry. Day boundaries use local calendar date. Returning home resets the willow ritual.

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

Coverage includes input, physics, malformed frames, Bloom, all entity families, persistence isolation, relationships, Garden, save repair, future schemas, ghost persistence, safe-content geometry, settings, latest-intent lifecycle, shutdown, collision geometry, assets, allocation bounds, telemetry, terminal/nonterminal ordering, exactly-once ownership, non-ghost recovery, v1/v2 receipt/manifest recovery, distance-bound SHA-256 golden identity, legacy upgrade, digest/distance tampering, lazy manifest validation, maintenance policy, cold-start mutation, one-shot commands, and payload-free logging.

This architecture description does not assert that the current commit has attached successful checks.

## 19. Debug scenarios

`EncounterDirector` scenarios mirror device-acceptance cases and are selectable through launch intents or the overlay. Scenario entities use persistence-disabled context.

Recovery maintenance is a separate debug support surface. Reused Activities permit inspection only; recovery/discard requires cold start before gameplay systems exist.

Debug scenarios and focused harnesses do not replace ordinary-play or physical-device acceptance.

## 20. Known debt and unresolved evidence

- `GameView` remains large and requires incremental behavior-preserving decomposition.
- The complete collision-result `when` dispatcher remains in `GameView`.
- STUMBLE and MERCY_MISS live effects remain in `GameViewNonTerminalCollisionEffects`.
- Immediate HIT impact still directly coordinates Player, ghost, camera, audio, music, and haptics.
- Ghost/distance mismatches predating persistent manifests cannot be reconstructed.
- Version-1 sidecars retain noncryptographic identity until replay requires strong upgrade.
- The healthy already-applied path avoids repeated hashing; maintenance performs full validation.
- SHA-256 identifies content/distance but does not authenticate a trusted writer.
- Ghost and non-ghost recovery are independent rather than one global transaction.
- Compatibility namespace switching during an active worker or maintenance instance is unsupported.
- Automatic recovery remains fail-closed; deliberate remediation is debug/support-only with no end-user UI.
- Exact-head Gradle, lint, build, emulator, physical-device, ADB, signing, installation, store path, screenshots, metadata, privacy/data-safety, content rating, and current Play-policy evidence remain unresolved.
- Entity readability, artwork/animation—including Wolf—fixed landscape, procedural scenic layers, audio/haptics, frame time, allocation, GC, memory, I/O, thermal, and long-run behavior require representative-device acceptance.

See `docs/RELEASE.md`. The project remains a feature-rich alpha until those gates are satisfied.
