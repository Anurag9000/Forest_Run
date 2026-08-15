# Forest Run — Technical Architecture

This document describes the canonical runtime architecture on `main`. It distinguishes source/build properties from claims that still require emulator, physical-device, human, signing, legal, or Play/store evidence.

## 1. Platform and build boundary

- Native Android/Kotlin custom `SurfaceView`/`Canvas` engine; no Compose runtime dependency.
- Namespace/application ID: `com.anurag9000.forestrun`.
- Debug application ID: `com.anurag9000.forestrun.debug`.
- Min SDK 24; compile/target SDK 36.
- Android source targets Java/Kotlin 17 bytecode; CI runs on Java 21.
- Orientation is **fixed landscape by product/source design**. Representative-device acceptance is still required for cutouts, density, unusual aspect ratios, refresh rates, and touch comfort.
- Release builds use R8 minification and resource shrinking.
- Signing credentials come only from external Gradle properties/environment variables; secrets are not committed.
- `main` is the canonical development branch for this project workflow.

Source layout:

```text
app/src/main/java/com/anurag9000/forestrun/
├── MainActivity.kt
├── ForestJournalActivity.kt
├── engine/      loop, state, persistence, encounters, memory, audio, haptics
├── entities/    Player and flora/tree/bird/animal implementations
├── systems/     particles, Seed Orbs, ghost record/playback/persistence
├── ui/          Menu, HUD, Garden, Rest, dialogue, debug surfaces
└── utils/       bitmap/math helpers
```

Sprites are under `app/src/main/assets/sprites/`; Android audio resources are under `app/src/main/res/raw/`.

## 2. Activity and surface lifecycle

`MainActivity` owns Android lifecycle integration, safe-area insets, feedback-settings initialization, save repair, repeated `singleTask` intents, and `GameView` creation/teardown.

Manifest/runtime behavior includes:

- `launchMode="singleTask"`;
- `screenOrientation="landscape"`;
- explicit configuration-change handling;
- immersive/keep-screen-on behavior;
- haptic permission and process-owned feedback teardown.

Repeated launch intents flow through `onNewIntent`. Debug/recovery intent handling is isolated from normal release behavior and must not leak stale scenario requests into later sessions.

`GameView` owns the active `SurfaceView`, logical safe-content transform, runtime input admission, and composition of the live gameplay owners. Activity teardown stops the game thread and releases process-owned audio/haptic resources.

## 3. Game thread and telemetry

`GameThread` performs the bounded frame cycle:

1. calculate delta time;
2. update `GameView`;
3. lock and render a `Canvas`;
4. record timing telemetry;
5. sleep for the remainder of the nominal frame budget.

Shutdown uses interruption plus bounded join and does not deliberately render stale work after stop.

`FramePerformanceMonitor` stores timing samples in bounded primitive buffers. Host/source telemetry can expose update/render/processing percentiles and heap observations, but source instrumentation alone does **not** prove physical-device FPS, jank, allocations, GC pressure, thermals, power, audio-thread behavior, or long-session stability.

## 4. Application and run-state ownership

Two state layers intentionally coexist:

- `AppGameState` owns the visible application surface (`MENU`, `GARDEN`, `PLAYING` in the live flow).
- `RunState` owns run lifecycle (`PLAYING`, `DYING`, `GAME_OVER`, `RESTARTING`).

Legacy `AppGameState.BLOOM`/`REST` compatibility entries must not become new runtime owners. Bloom is orthogonal gameplay state owned by `GameStateManager`; Rest/death progression belongs to run-session state.

`RunSessionTransitionPlanner` is the pure transition table. `RunSessionTransitionCoordinator` executes ordered effects through `LiveRunSessionEffects`. `GameView` adopts the target state only after the transition is valid and its required effects complete.

## 5. Input and Player locomotion

`InputHandler` owns gesture arbitration before Player mutation:

- tap launches immediately and releases into a short-hop cap;
- hold retains more ascent for the full jump;
- early release trims upward velocity and never adds energy;
- swipe-down is classified before jump commitment and enters duck when legal;
- cancel/state transition clears the active gesture without synthesizing a delayed action.

Gameplay input is admitted only during the live gameplay state. Menu, Garden, Rest/death, restart, lifecycle transitions, and surface changes cancel stale gestures.

`Player` owns locomotion states, finite/bounded physics admission, landing, stumble/rest presentation, and Bloom visual power. Poisoned/nonfinite values are repaired or rejected at admission boundaries rather than being allowed to propagate through the frame loop.

## 6. Per-run economy and Bloom

`GameStateManager` owns per-run:

- scroll speed and run time;
- score and distance;
- current run Seed/Bloom state;
- Bloom timer and conversion count;
- mercy/kindness/pacifist statistics;
- score/debuff state;
- input-discovery/onboarding state.

Persistent Seed currency remains authoritative in save infrastructure. A run reset reloads durable lifetime Seeds so a Garden purchase cannot be overwritten by stale in-memory state.

Bloom contract:

- threshold: 8 Seeds;
- one authoritative six-second active timer;
- normal locomotion continues while Bloom is active;
- incoming Seeds do not restart/extend the active timer;
- converted encounters resolve exclusively as Bloom conversions rather than stacking ordinary pass/action/Orb rewards;
- presentation spans Player aura/trail, world particles, HUD, camera, music/SFX, semantic haptic surge, and conversion feedback.

## 7. Encounter lifecycle and arbitration

`EntityManager` owns ordinary spawning/update, reactive mechanics, pure collision arbitration, terminal/pass resolution, Bloom conversion, and Seed-Orb interaction.

Pooling remains disabled because encounter subclasses carry incompatible timers, projectiles, movement modes, dialogue, and reward state without one complete reset contract.

Every encounter resolves once from pending to one terminal encounter outcome:

```text
HIT
STUMBLE
MERCY
CLEAN_PASS
BLOOM_CONVERTED
```

Collision arbitration precedes pass processing. Simultaneous collision severity is deterministic:

```text
HIT > STUMBLE > MERCY
```

Only the selected result can emit stateful effects. Expanded mercy/collision probes reuse geometry objects rather than allocating transient rectangles in the hot path.

`EncounterFamilyCatalogue` is the structural inventory for all 19 encounter families and derives ordinary biome/scenario/variant/relationship capability from existing owners.

## 8. Collision effect ownership

`CollisionOutcomeDispatcher` is the live result dispatcher. It delegates to two behavioral layers rather than allowing `GameView` to maintain independent hit/stumble/mercy branches.

### Terminal HIT

`TerminalHitImpactCoordinator` owns the immediate terminal impact sequence:

```text
record run hit
→ suppress ghost
→ Player rest presentation
→ camera impact
→ hit SFX
→ Rest music
→ terminal-impact haptic
→ capture post-impact immutable state
```

The haptic boundary is semantic (`terminalImpactHaptic`) even though compatibility adapters may still delegate to the older `longPulse` primitive.

`TerminalHitOutcomeCoordinator` then owns relationship recording, authored feedback, one final summary, Rest quote resolution, and exactly one run-outcome commit.

The completed terminal result returns through `RunSessionEvent.TERMINAL_COLLISION_COMPLETED`; the run-session layer owns transition to `DYING`.

### Nonterminal collision outcomes

`NonTerminalCollisionOutcomeCoordinator` owns ordered STUMBLE and MERCY_MISS completion.

STUMBLE includes run hit accounting, optional relationship persistence, ghost suppression, Player stumble, flash/SFX/camera response, **stumble-impact haptic**, authored copy, and selected-entity deactivation.

MERCY_MISS includes mercy flash/SFX, **mercy-acknowledgement haptic**, authored copy, mercy particles, and camera response.

`LiveCollisionEffects` is the shared live adapter. Compatibility primitive names remain only at adapter/test boundaries where changing them would provide no behavioral benefit.

## 9. Biomes, background, rendering, and safe content

`BiomeManager` blends the five live biome identities:

1. Meadow;
2. Orchard;
3. Ancient Grove;
4. Dusk Canyon;
5. Night Forest.

`ParallaxBackground` owns cached scenic composition, parallax layers, biome atmosphere, mist/leaves/petals/fireflies, speed response, and Bloom visual response.

`SafeContentTransform` maps between physical display bounds and the logical game canvas. Essential Menu/Garden/HUD/Rest content and touch/accessibility geometry share the same safe logical space rather than using unrelated hard-coded screen assumptions.

Some scenic layers remain procedural. That is a creative-direction choice, not evidence that physical readability or final-art acceptance has already passed.

## 10. Particle system

`ParticleManager` owns a bounded pool, one-shot presets, and continuous emitters. Active traversal uses indexed/bounded structures. Reduced-motion settings scale decorative particle intensity without changing collision geometry or gameplay physics.

Bloom continuous emitters attach to the Player/world state and are removed on Bloom exit, Rest, or run reset.

## 11. Garden ownership

Garden persistence has distinct responsibilities:

- `GardenEconomy` — canonical ordered catalogue metadata: stable index, full name, compact card name, Seed cost;
- `GardenPurchaseManager` / `ApplicationPersistenceFacade` — atomic purchase mutation and remaining Seed balance;
- `GardenScreen` / `SpriteManager` — sprites, colours, layout, atmosphere, visitor/wardrobe presentation;
- `GardenSanctuaryPlanner` and memory/relationship systems — post-run sanctuary consequences and long-horizon presentation.

Canonical Garden costs are 15, 20, 25, 30, 40, 50, 60, 75, and 100 Seeds in the live nine-entry order documented in `docs/GAME_DESIGN.md`.

The Canvas presentation currently carries a matching local compact-name/cost list alongside its visual colour/emoji metadata. `scripts/test_garden_catalogue_contract.py` binds that presentation list and canonical docs to `GardenEconomy`, preventing silent drift until the large Canvas owner is next safely migrated to derive those fields directly.

## 12. Persistent memory and Forest Journal

Long-horizon gameplay state remains owned by gameplay/persistence systems, not by the Journal.

Important authorities include:

- `SaveManager` — scores, Seeds, Garden progress, wardrobe values, summaries, route counts, mood/return/story keys, ghost compatibility format;
- `PersistentMemoryManager` — encounter/pass/spare/hit histories, biome friendship, repeated-history marks;
- `RelationshipArcSystem` — relationship stage, tone, Bond rewards and rituals;
- `CostumeManager` — available/equipped wardrobe presentation;
- `StoryFragmentSystem` — memory-page unlock ownership;
- `ForestMoodSystem` / route systems — durable run-world interpretation.

`ForestJournalActivity` is a native-view read-only presentation surface. Its projection layer includes:

- `ForestCollectionProgressComposer`;
- `ForestGardenHistoryComposer`;
- `ForestPathHistoryComposer`;
- `ForestRunLegacyComposer`;
- `ForestMemoryPagePresenter` and `ForestMemoryPageNarrative`;
- `ForestCompletionCapstoneComposer`.

The Journal must not award/spend Seeds, buy Garden entries, refresh costume unlocks, mutate relationships, create story pages, or increment counters.

Its whole-forest capstone is derived from the five current collection tracks plus the all-positive-route-history pillar; it is deliberately not persisted as a second achievement flag.

See `docs/FOREST_JOURNAL.md`.

## 13. Feedback architecture

### Audio

Music/SFX ownership is state-aware rather than a single generic loop. Menu/Garden, run progression, Bloom, Rest, encounter, mercy/hit, Seed, jump/landing, and fallback behavior have explicit owners.

### Haptics

`HapticManager` is preference-aware and owns Android vibrator access. Semantic cues include:

- `lightTick()`;
- `stumbleImpact()`;
- `terminalImpact()`;
- `mercyAcknowledgement()`;
- `gardenGrowth()`;
- `bloomSurge()`.

Compatibility wrappers (`shortPulse`, `mediumPulse`, `longPulse`, `doubleTap`) preserve existing physical timing for old adapters. Domain orchestration should use semantic names.

## 14. Accessibility architecture

The custom Canvas UI exposes a real Android virtual-node tree through the accessibility provider rather than relying on painted text.

The accessibility layer owns:

- stable virtual node IDs;
- semantic trees for Menu, Settings, Playing, Garden, and Rest;
- bounds that share visible/touch layout planning;
- typed action validation/routing;
- truthful checkable/disabled state;
- coalesced announcements;
- reduced-motion/audio/haptic settings;
- Garden/wardrobe actions;
- Rest continuation semantics.

The Forest Journal itself uses native Android `ScrollView`/`TextView`/`Button` controls for long-form reading and filtered navigation.

Source integration does not replace representative TalkBack, large-font/display-scale, switch/keyboard, cutout, and human accessibility acceptance.

See `docs/ACCESSIBILITY.md`.

## 15. Ghost recording, persistence, and recovery

Ghost capture/playback is a separate durability domain from ordinary run-outcome persistence.

`GhostRecorder` samples bounded Player pose/state. `GhostPersistenceManager` and associated artifact stores persist versioned frame data, promotion receipts/manifests, namespace identity, and monotonic best-distance relationships.

Primary and compatibility namespaces keep preference/ghost filenames bound together. Same-namespace writes serialize; different namespaces can use the bounded backend independently.

Ghost validation rejects malformed/truncated/oversized/nonfinite/state-invalid/nonmonotonic data. Versioned sidecars bind distance/frame identity using FNV compatibility and SHA-256 for current strong local content identity. SHA-256 here is integrity identity, **not authenticated authorship**.

`GhostPlayer` is contextual visual playback and does not participate in collision arbitration.

Specialized contracts live in:

- `docs/GHOST_PROMOTION_RECOVERY.md`;
- `docs/GHOST_PERSISTENCE_NAMESPACES.md`;
- `docs/GHOST_NAMESPACE_SCHEDULING.md`.

## 16. Persistence and exactly-once run completion

`ApplicationPersistenceFacade` is the shared live application mutation boundary for terminal outcome commits, encounter/pass/hit history, Garden purchases, wardrobe writes, feedback settings, and recovery actions.

It does **not** pretend unrelated SharedPreferences/AtomicFile/journal domains form one global ACID transaction.

`RunOutcomePersistenceCoordinator` claims one per-run completion token. Nonpersistent/debug runs consume the token without permanent writes; duplicate delivery is rejected; unresolved corrupt/conflicting evidence blocks new completion rather than silently overwriting it.

Run summary, mood, return state, and route state have an ordered recoverable protocol. Ghost promotion has its own recoverable protocol. They remain independently recoverable by design.

`SaveIntegrityManager` owns schema/type/bounds repair and compatibility behavior rather than spreading migration logic through gameplay owners.

## 17. Recovery user experience

`RecoveryEvidenceMaintenanceCoordinator` and the user-facing recovery presentation/controller separate:

- inspection;
- safe retry;
- destructive discard requiring explicit confirmation;
- failure/blocked states where deletion is not offered.

Ordinary-player copy does not expose raw file paths, hashes, exception traces, or ghost-frame payloads. Debug/support maintenance remains a separate surface with fail-closed domain rules.

See `docs/RECOVERY_USER_EXPERIENCE.md` and `docs/RECOVERY_EVIDENCE_MAINTENANCE.md`.

## 18. Deterministic and automated evidence

The repository contains host/JVM/Robolectric/instrumentation and evidence tooling for source contracts, gameplay invariants, deterministic scenarios, recovery, accessibility semantics, Garden/encounter catalogues, release provenance, dependencies/SBOM, screenshots/store graphics, installed-candidate identity, Play delivery, human acceptance, and release governance.

Permanent validation is read-only with respect to repository source and validates the exact event SHA.

Important evidence documents include:

- `docs/PERFORMANCE.md`;
- `docs/DEVICE_ACCEPTANCE.md`;
- `docs/INSTALLED_CANDIDATE_IDENTITY.md`;
- `docs/HUMAN_ACCEPTANCE.md`;
- `docs/RELEASE_GOVERNANCE_EVIDENCE.md`;
- `docs/RELEASE_EVIDENCE_INDEX.md`;
- `docs/RELEASE_READINESS.md`.

Automated evidence can prove source/build/emulator facts for its candidate. It cannot manufacture physical-device timing, human fairness/accessibility judgment, legal rights, production signing identity, or Play delivery.

## 19. External release boundary

The following remain external until real evidence exists for one frozen candidate:

- representative physical-device performance/input/readability acceptance;
- human gameplay and accessibility review;
- final art/animation/audio/haptic approval;
- creative-asset and dependency rights/licence/notice decisions;
- source-code licensing decision;
- private vulnerability-reporting configuration;
- stable HTTPS privacy-policy publication;
- production upload/app-signing identity;
- Play Console declarations and delivery;
- exact candidate screenshots/metadata/release notes/tag;
- independent final release approval.

Source validators should fail closed when those facts are missing rather than interpreting “schema exists” as “release approved.”

## 20. Architecture rules going forward

1. Prefer one authoritative owner per mutable fact.
2. Project persistent history into UI rather than creating duplicate save namespaces.
3. Keep collision queries pure and side effects behind selected outcomes.
4. Keep Bloom orthogonal to locomotion and run-screen state.
5. Use semantic feedback vocabulary at domain boundaries.
6. Bind documents/tests to canonical catalogues where player-facing duplication is temporarily unavoidable.
7. Avoid broad rewrites of large Canvas owners unless a concrete behavior/maintainability result justifies the risk.
8. Do not equate automated source/build success with physical, human, legal, signing, or store acceptance.
