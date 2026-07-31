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

`GhostRecorder` samples player pose at 30 Hz for up to twenty minutes. A completed best-run buffer is detached in O(1), published immediately to playback memory, and handed to `GhostPersistenceManager` for dedicated-worker persistence.

Ghost files are written atomically and reject malformed inputs including oversized, truncated, trailing, non-finite, invalid-state, and non-monotonic data. Newer-schema ghost data is preserved rather than destructively rewritten by an older build.

`GhostPlayer` provides context-aware visibility around the live player and hazards. Ghosts have no gameplay hitbox.

Legacy ghost frames store `PlayerState.ordinal`; therefore PlayerState entries must not be removed or reordered without a schema migration.

## 13. Save integrity and persistent memory

Persistence is split by responsibility:

- `SaveManager`: scores, Seeds, run summaries, Garden/costume values, ghost compatibility paths;
- `PersistentMemoryManager`: encounters, hits, passes, spares, relationships, return/history signals;
- `SaveIntegrityManager`: schema migration, type repair, bounds, saturating counters, incomplete-summary rejection, and compatibility storage.

Deterministic scenarios are isolated from permanent score, encounter, relationship, Garden, summary, and ghost history.

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

The test suite covers input arbitration, physics, malformed frame boundaries, Bloom, encounter outcomes, all entity families, persistence isolation, relationships, Garden transactions/layout, save repair, future-schema behavior, ghost persistence, safe-content geometry, feedback settings, latest-intent lifecycle ownership, thread shutdown, collision geometry, runtime assets, placeholder allocation bounds, hot-path reuse, and frame telemetry.

## 19. Debug scenarios

Debug-only `EncounterDirector` scenarios mirror the device-acceptance checklist and can be selected through repeated launch intents or the in-game overlay. Scenario entities use persistence-disabled context.

Debug scenarios are deterministic test aids, not substitutes for ordinary-play and physical-device acceptance.

## 20. Known architectural debt and unresolved release evidence

The following remain intentionally open:

- `GameView` is still a large coordinator and should be decomposed incrementally after behavioral stability;
- persistence ownership is safer but still distributed across managers;
- entity mechanic/readability claims need ordinary-play and hardware acceptance;
- frame, allocation, GC, memory, I/O, audio-thread, thermal, and long-run metrics need measured thresholds on representative hardware;
- fixed landscape and procedural scenic layers need final product decisions;
- artwork and animation sheets, including Wolf, need visual inspection;
- real upload credentials, signed artifact installation, store-path testing, screenshots, metadata, privacy/data-safety, content rating, and current Play-policy review remain release gates.

See `docs/RELEASE.md` for the evidence checklist. No documentation statement should promote the project beyond a feature-rich alpha until those gates are complete.
