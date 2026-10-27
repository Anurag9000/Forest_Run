# Deep inspection shard: partially inspected Kotlin files (TASK-A-024)

## Provenance and scope

- Repository: `Forest_Run`, baseline commit `eeccc677107f69039dbf71053c18a9038d4b5ff6`.
- Snapshot inspected: current shared worktree at that baseline with substantial pre-existing dirty changes. Tracked modifications were preserved; no source was edited.
- Scope: the 22 Kotlin production files marked `PARTIALLY_INSPECTED` in `docs/repository_knowledge/file_inventory.json` when this follow-up began. The inventory recorded the prior evidence generically as `docs/REPO_CODEBASE_MAP.md and/or docs/REPO_MEMORY_MAP.md where scope applies; variable depth`; those maps are reused only as prior context. For every file in this shard, the current entire file was read, including dirty files, so these findings describe the current worktree source rather than assuming old text remains unchanged.
- Line accounting: full current file contents were read in bounded contiguous ranges. Range convention below is inclusive; a few commands requested a final range through line 1200 where the file ended earlier. Counts are the actual file line counts. `GameView.kt` is 1,965 lines (read 1–320, 321–640, 641–960, 961–1280, 1281–1600, 1601–1965); `GardenScreen.kt` is 1,190 (1–240, 241–480, 481–720, 721–960, 961–1190); `MainMenuScreen.kt` is 633 (1–160, 161–320, 321–480, 481–633). The other files were read completely in one or two contiguous ranges as indicated.
- Evidence class throughout: static source inspection only. No build, tests, app workflows, benchmark, training, inference, or runtime verification was performed. Descriptions of behavior are source-derived, not runtime claims.
- This shard is supplementary evidence for the master inventory. It does not update shared ledgers or the master document.

## File-by-file reconstruction

### 1. `app/src/main/java/com/anurag9000/forestrun/ForestJournalActivity.kt` — 518 lines; read 1–260, 261–518

`ForestJournalActivity : AppCompatActivity` owns the separate native Android Journal screen. Its lifecycle setup repairs persisted state before rendering, constructs a scrollable/view-based presentation, restores a saved section selection, and projects the persisted run, Garden, collection, relationship and memory records into sections. The file defines the section model/selection and view-building helpers locally; it is a presentation reader rather than the owner of gameplay, Garden purchases, or persistent encounter mutations. It uses the save and memory managers plus presentation planners/composers to render grouped journal content. The rendering is dynamically built from Android views (labels, rows/cards and containers) rather than a Compose route. Section choices cover the full journal and the progress, bonds, memories, and families-oriented views; selection state is restored/saved through Activity state. Evidence: class and lifecycle/setup at lines 1–150; saved-section and section routing/rendering through the middle; data projection and view helpers at lines 151–518. MainActivity is the Activity boundary/launcher integration; data owners remain `SaveManager`, `PersistentMemoryManager`, Garden and relationship systems. Static limitation: no accessibility/service behavior or rendering was executed here.

### 2. `app/src/main/java/com/anurag9000/forestrun/MainActivity.kt` — 392 lines; read 1–220, 221–392

`MainActivity` configures the landscape game Activity, immersive system-bar behavior and insets, creates audio/haptic/game services and `GameView`, and coordinates lifecycle pause/resume/release. Insets/safe-content information is forwarded to the view; layout/surface size changes are considered separately from lifecycle state so the game view can be retained where geometry allows. Recovery startup first repairs save integrity, then validates runtime assets and creates the gameplay view; the evidence dialog is posted after initialization. Incoming debug-maintenance intent parsing is guarded by `ApplicationInfo.FLAG_DEBUGGABLE`, accepts only recognized scenario/actions, and removes action/domain extras in a `finally` path. Initial creation can allow recovery mutation; `onNewIntent` route is read-only for mutations. Debug scenario requests use `LatestRequestGate` and bounded retries, and the ready indication is tied to surface/debug-frame readiness rather than request receipt. `onDestroy` releases owned managers. Evidence: `onCreate`/window and recovery setup lines 45–150; lifecycle/insets lines 150–230; debug-intent and retry helpers through end. Callers/consumers: Android launcher/runtime; `GameView`, `SaveIntegrityManager`, `RecoveryEvidenceDialogCoordinator`, `HapticManager`, `SfxManager`, `LeitmotifManager`. Runtime readiness is not established by this static inspection.

### 3. `app/src/main/java/com/anurag9000/forestrun/RecoveryEvidenceDialogCoordinator.kt` — 127 lines; read 1–127

`RecoveryEvidenceDialogCoordinator` turns a privacy-safe `RecoveryEvidenceUiRow` into Android dialogs. `showIfNeeded` guards finishing/destroyed Activities and duplicate dialogs, refreshes via `RecoveryEvidenceUserController`, and picks the first actionable row. Safe retry is directly offered; discard actions open a second explicit confirmation. Results continue with the next actionable row, request confirmation if the controller requires it, or show generic failure text that claims existing data was left unchanged. It intentionally does not render journal payloads, ghost frames, paths, hashes, or exception strings. `dismiss`, `showRow`, `confirmDiscard`, `handleResult`, `showFailure`, and `destructiveAction` are the meaningful methods. MainActivity owns/invokes it; the controller and facade own the data operation. No dialog/UI behavior was run.

### 4. `app/src/main/java/com/anurag9000/forestrun/engine/ApplicationPersistenceFacade.kt` — 188 lines; read 1–188

This is a composition boundary, not a single transactional store. `ApplicationRunOutcomePort` extends `RunOutcomeCommitter` with new-run reset; `CoordinatorRunOutcomePort` adapts it to `RunOutcomePersistenceCoordinator`. `ApplicationEncounterPersistence` and Android adapter route encounter/pass/hit records to `PersistentMemoryManager`. `ApplicationRecoveryMaintenance` exposes inspect/recover/discard operations through `AndroidRecoveryEvidenceMaintenance`. `ApplicationPersistenceFacade` also implements collision relationship interfaces and delegates terminal outcomes, Garden purchase, feedback preference writes, costume equip, encounter mutations, and recovery methods. The `android(context)` factory captures `applicationContext`, wires run outcome sink, `GardenPurchaseManager`, `FeedbackSettings`, `CostumeManager`, encounter adapter and recovery maintenance. Its documentation explicitly says SharedPreferences, AtomicFile ghost artifacts, and journals are independent durability domains and not ACID together. Consumers include MainActivity, GameView, GardenScreen and FeedbackSettingsPanel; adapters centralize discovery but the actual stores retain ownership.

### 5. `app/src/main/java/com/anurag9000/forestrun/engine/CollisionOutcomeDispatcher.kt` — 71 lines; read 1–71

`CollisionOutcomeDispatcher.dispatch` is a pure branch dispatcher over `CollisionOutcome`: `HIT`, `STUMBLE`, `MERCY_MISS`, `NONE`. A callback bundle separates selected effects from input capture. Lazy callbacks defer reading mutable runtime state until the chosen branch. HIT applies impact and then terminal completion; STUMBLE deactivates the collided entity and commits nonterminal behavior; MERCY_MISS dispatches its specific nonterminal completion; NONE has no effects. `GameView` builds callbacks and is the execution consumer. This centralizes routing, not the gameplay rules themselves.

### 6. `app/src/main/java/com/anurag9000/forestrun/engine/EncounterFamilyCatalogue.kt` — 210 lines; read 1–210

`EncounterFamilyCatalogue` declares 19 entity descriptors in `EntityType` order, with family/profile information for catalogue coverage. Ownership metadata deliberately assigns movement/collision/fairness mechanics to entity implementations, assets to `SpriteManager`, flavor to `RunFlavorPresentation`, relationship data to `PersistentMemoryManager`, and route contribution to `GameStateManager`. It derives biome pools, deterministic scenario coverage, focused same-type fairness cases, variants and relationship tracking from the catalogue. `require`/`check` guard catalogue completeness and invariants. This is descriptive/derivation infrastructure rather than proof every declared mechanic is integrated or empirically fair; the actual entity behavior and GameView encounter dispatch remain separate.

### 7. `app/src/main/java/com/anurag9000/forestrun/engine/GameStateManager.kt` — 526 lines; read 1–526

`GameStateManager` is mutable run-scoped authoritative state. It tracks elapsed time/distance, speed, score and bonus multiplier, seeds/mercy/Bloom, pacifist-route counters, encounter progress, summaries and persisted bests. Numeric state is guarded against nonfinite/negative input and saturates or clamps where defined. Distance influences scroll speed; the current published speed is used by the frame update. `addBonus` sets score-multiplier behavior and expiry, with expiration processed after the current active frame; source search in earlier audit found no production call supplying boost/duration, so exercised activation is unresolved. Seeds persist only when caller sets `persistProgress`; timer is not actively replenished while Bloom is active. Bloom thresholds/counters use `GameConstants`. Pacifist, mercy, opening-guidance, encounter-selection bag, route/mood summary and high-score compare/write operations live here or delegate to persistence managers. Reset reloads high score/currency and clears run state. `undebuffedScrollSpeed` is retained as a speed ceiling/state input. Main consumer is `GameView`; persistent writes delegate to `SaveManager`/facade. Status: structurally implemented, not runtime verified; do not infer all fields are active solely from their existence.

### 8. `app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt` — 1,965 lines; read six contiguous ranges listed above

`GameView : SurfaceView` is the principal real-time controller, renderer, accessibility surface and bridge between game state and subsystem owners. It coordinates a dedicated `GameThread`, surface lifecycle, runtime-state lock, restart gating, bounded stop/pause behavior, audio pause/resume, and cleanup of SFX/haptics. It avoids concurrent game-state saving if its thread failed to stop, rather than reading mutable state while that thread may still run. Surface creation waits for previous thread termination before initialization; `resumeGameThreadWhenStopped` prevents overlapping threads. Debug scenario requests are guarded and latest-request-wins.

Input goes through `InputHandler`; stale touch sequences are cancelled on transitions. Accessibility virtual nodes/actions describe bounded state and relevant actions, including active settings, Garden/wardrobe, Bloom, jump/duck and Rest. The frame update gates by app/run state, updates audio/haptics/Bloom and scenic presentation, runs deterministic encounter/script update before player physics, updates entities/orbs/ghost, then dispatches collision through `CollisionOutcomeDispatcher`. Terminal outcome persists summary/ghost and transitions without advancing extra progress after terminal collision; pacifist reward and milestone cues are dispatched through managers. Ghost recording/playback is state-aware, and nearby hazard context affects visibility. Drawing handles Menu/Garden separately; gameplay draws camera-shaken background/entities/orbs/ghost/player/effects, then overlays, lighting and safe-content HUD/accessibility/game-over presentation. Fresh-run setup resets entities/player/GameState, ghost and audio/cues. MainActivity owns lifecycle; GameStateManager, collision/effect/persistence/audio/input/UI subsystems are downstream collaborators. The class is structurally integrated but no runtime execution or pixel-level correctness was checked. Important detailed methods are the SurfaceHolder callbacks, `pause`/`resume`, thread stop/resume helpers, `onTouchEvent`, accessibility snapshot/action helpers, update loop, terminal-completion paths, debug scenario application, fresh-run initialization, and draw path; exact definitions are locatable by these names within the inspected source.

### 9. `app/src/main/java/com/anurag9000/forestrun/engine/HapticManager.kt` — 134 lines; read 1–134

`HapticManager` exposes semantic game cues plus compatibility wrappers. Pulse/waveform helpers check haptic preference and service availability, select `VibratorManager` on API 31+ or legacy `Vibrator`, and wrap platform operations in `runCatching`. Pause/cancel and release are explicit. Garden growth, Bloom, collision/near-miss and related events map to semantic patterns. MainActivity/GameView and Garden purchase interaction invoke it. Current dirty worktree content was inspected; no device vibration was executed.

### 10. `app/src/main/java/com/anurag9000/forestrun/engine/InputHandler.kt` — 258 lines; read 1–258

`InputHandler` owns a single-primary-pointer gesture state machine and callback interface. A 75 ms arbitration window delays jump commitment so a downward gesture can classify as duck before jump starts; a downward distance threshold of 80 px selects duck. Hold duration is capped at 0.6 s. Positive finite `dt` is required; emitted callback/telemetry timestamps and gesture state are managed internally. Quick up commits a press/release sequence; cancel clears state without synthesizing a surprise jump. Additional pointers are ignored while preserving the primary sequence. Reset/cancel supports stale gesture cleanup by GameView. `touchStartX` is stored though horizontal displacement is not used by the inspected behavior. This is source-derived timing and threshold logic; no touch/device latency validation was run.

### 11. `app/src/main/java/com/anurag9000/forestrun/engine/LeitmotifManager.kt` — 550 lines; read 1–280, 281–550

`LeitmotifManager` owns synchronized `MediaPlayer` playback and track state: menu, three distance/run tracks, Bloom, and Rest. It maps tracks to raw resources, uses fixed menu/rest profiles and distance thresholds at 500 m and 1,500 m, and adjusts tempo from scroll speed. Bloom receives a signature/profile from time and conversion count with bounds/throttling. Track changes instantiate the new player at zero volume and crossfade over 1.5 seconds at 50 ms steps on a daemon worker, guarded by generation identity so stale transitions do not control current players; replaced players are released. `EvaluationThrottle` is a helper with a confirmed caller (corrected from earlier uncertainty). Optional player parameters are guarded. `destroy` invalidates/reset playback and releases resources. GameView/MainActivity manage it. Actual audio transitions are not runtime-verified.

### 12. `app/src/main/java/com/anurag9000/forestrun/engine/LiveCollisionEffects.kt` — 61 lines; read 1–61

`LiveCollisionEffects` is a small adapter from collision result to injected game callbacks. It delegates entity activation/impact and outcome completion rather than owning collision detection or persistence. Its narrow API allows the dispatcher to invoke hit/stumble/mercy outcomes. Consumer is `GameView` through `CollisionOutcomeDispatcher`; source establishes callback routing only.

### 13. `app/src/main/java/com/anurag9000/forestrun/engine/RecoveryEvidenceUserController.kt` — 127 lines; read 1–127

`RecoveryEvidenceUserController` is the UI-independent action policy over `ApplicationPersistenceFacade`. `refresh` obtains/rebuilds evidence presentation. `perform` refreshes before acting, verifies requested action remains available for the target domain, requires explicit confirmation for destructive discard, then invokes safe recovery or the domain-specific discard and refreshes the final report. It returns dispositions (`COMPLETED`, `REFRESHED`, `CONFIRMATION_REQUIRED`, `NOT_AVAILABLE`, `ACTION_FAILED`) and presentation model, not raw sensitive payload. It catches `RuntimeException` around operations and converts it to a failure disposition. `from(facade)` adapts facade methods. `RecoveryEvidenceDialogCoordinator` is its UI consumer; Android persistence maintenance owns actual mutation. Other throwable categories are not shown as caught by this controller.

### 14. `app/src/main/java/com/anurag9000/forestrun/engine/RunSessionTransitionCoordinator.kt` — 71 lines; read 1–71

The coordinator applies a planner-produced ordered effect list to injected effect handlers. It catches `RuntimeException`, records/stops at the failed effect, and returns an outcome used by the caller; it does not itself publish authoritative app/run state. Adoption policy is exposed through `mayAdoptAfterState`, requiring accepted transition and no failed effect. GameView is the principal consumer. This is a transition side-effect boundary, not a full transactional rollback mechanism.

### 15. `app/src/main/java/com/anurag9000/forestrun/engine/RunSessionTransitionPlanner.kt` — 169 lines; read 1–169

`RunSessionTransitionPlanner` is a pure table-like planner over `(AppGameState, RunState)` and an event. It maps menu/Garden/run/death/Rest/restart/debug events to accepted state transition plus ordered effects. Invalid or stale ordinary events produce unaccepted/no-op plans; debug entry is state-agnostic and idempotent. The coordinator applies effects; GameView later publishes state only when coordinator outcome permits. Planner completeness is only relative to declared events/states; runtime behavior was not exercised.

### 16. `app/src/main/java/com/anurag9000/forestrun/engine/SaveIntegrityManager.kt` — 366 lines; read 1–366

`SaveIntegrityManager` validates and repairs known SharedPreferences fields while preserving unknown keys. Primary schema is version 1; a future schema version is handled via compatibility namespace rather than mutating newer primary data. It validates expected primitive types/counters, finite distances, enum names, Garden bounds and sets/sizes for memories/history, costume consistency, and dynamic entity/biome keys. Last-run summary is treated as a required field group: incomplete group is removed together. Repair writes only if changed and reports whether write commit succeeded; startup path calls repair before normal presentation. It also separates damaged/corrupt from unresolved-pending evidence for recovery. MainActivity and ForestJournalActivity trigger repair; recovery UI delegates to this boundary. Current dirty source inspected; no repair was run, so real preference states remain unexamined.

### 17. `app/src/main/java/com/anurag9000/forestrun/engine/SfxManager.kt` — 298 lines; read 1–298

`SfxManager` loads a bounded `SoundPool` (capacity 8) and tracks active streams in a bounded ledger (32). It distinguishes required and optional samples, has explicit readiness/generation handling for asynchronous load callbacks, and documented sample fallbacks. Playback gates on audio preference/readiness, clamps volume/rate, and tracks stream IDs; pause stops tracked streams; destroy invalidates listeners and releases pool. Bloom SFX profiles scale volume/rate by bounded conversion count. MainActivity constructs/lifecycle-manages it; GameView dispatches cues. Static code does not establish sample decode success on device.

### 18. `app/src/main/java/com/anurag9000/forestrun/ui/FeedbackSettingsPanel.kt` — 204 lines; read 1–204

`FeedbackSettingsPanel` is a fixed-size Canvas panel for reduced motion, audio, and haptics preferences, with precomputed layout/hit regions and finite-coordinate handling. It optionally exposes Journal entry callback. Drawing and taps use injected `ApplicationPersistenceFacade` for preference writes and invoke callbacks; font loading falls back to a system typeface. MainMenuScreen owns it and only routes/draws it in IDLE state. Panel state is presentation of persisted settings; actual Android settings behavior was not executed.

### 19. `app/src/main/java/com/anurag9000/forestrun/ui/GardenScreen.kt` — 1,190 lines; read 1–240, 241–480, 481–720, 721–960, 961–1190

`GardenScreen` is both meta-loop Canvas UI and projection layer. Nine visual plants/sprites are required to match `GardenEconomy.catalogueSize`; canonical cost/name data comes from `GardenEconomy`, while the screen keeps local `unlockedCount`/`lifeSeeds` presentation cache. `GardenPurchaseInteractionCoordinator` invokes facade purchase and growth haptic; `adoptPurchaseResult` only adopts already-committed result, emits visual seed particle for successful purchase, and does not itself spend currency or emit haptic. Wardrobe cards delegate equip to facade/CostumeManager, and locked items show unlock labels. `load`/`refresh` read Garden seeds/progress and refresh wardrobe/stats. The UI has Run/back actions, stats, last-run narrative, wardrobe, plant catalogue, mood palette, sanctuary lighting, return visitor, traces and particle effects.

`refreshStats` combines SaveManager, PersistentMemoryManager, ForestMoodSystem, ReturnMomentsSystem, RelationshipArcSystem, StoryFragmentSystem, SessionArcComposer, `GardenSanctuaryPlanner`, and `PostRunReflectionPlanner`. Entering Garden can select a return moment and set a pending acknowledgement; actual acknowledgement is issued during draw only after presentation commands, using the captured timestamp. Story helpers selected on entry may persist Garden-context pages before projections are rebuilt. Thus a screen refresh is not purely read-only; this is deliberate in source comments. `draw` performs background, mood/sanctuary/return content, stats/run/last-run/wardrobe, plant cards, particles. `update` caps frame delta at 0.05s, wraps elapsed time, updates sprites/visitor/particles and animation timers. `onTap` validates finite coordinates, refreshes interactive layout, routes Run, wardrobe, bottom-edge Back and next plant purchase. Text wrap/ellipsize and visitor sprite mapping are local helpers. GameView is screen owner/caller. No purchase or presentation side effects were run.

### 20. `app/src/main/java/com/anurag9000/forestrun/ui/HUD.kt` — 540 lines; read 1–540

`HUD` renders the gameplay top bar and in-run status through reusable Paints/geometry. It presents score/distance/seeds/mercy/Bloom and meter fill, with modes controlling appearance, bounded formatting and finite-time handling. Opening-guidance presentation includes line wrapping/cache behavior. The width-aware layout is static for its configured screen size; GameView owns and calls it after safe-area transform. It is presentation only; values originate in GameStateManager and related state. No frame/render tests or device screenshots were run.

### 21. `app/src/main/java/com/anurag9000/forestrun/ui/MainMenuScreen.kt` — 633 lines; read 1–160, 161–320, 321–480, 481–633

`MainMenuScreen` is a Canvas menu plus a two-stage character ritual: IDLE → STANDING_UP → READY. `onTap` first routes feedback settings and the bottom-left Garden region, then moves IDLE to standing, ignores taps while the animation runs, and latches a run request in READY. `performAccessibilityPrimaryAction` mirrors the primary ritual (not the Garden/feedback affordances). `update` rejects nonfinite/nonpositive delta, caps frame delta to 0.05s, and moves the standing animation to READY at 1.8 s or sprite completion. `consumeStartRunRequest` makes launch one-shot; `resetRitual` clears phase/request and resets sprites. GameView calls/update/draws it and consumes run/Garden callbacks.

Copy comes from `SessionArcComposer.menuCopy`; Garden sanctuary state is built from last-run summary via `GardenSanctuaryPlanner`. It draws sky/ground, sanctuary lighting/mist/fireflies/lanterns/petals/Bloom patches, willow, bird, character, cinematic overlay, title/copy, arrival[local absolute path omitted] labels, launch cue and phase-specific prompts. `FeedbackSettingsPanel` is only drawn/routed in IDLE. Paints and sprite copies are retained. Text wrapping is limited to two lines but long first words may exceed width (no hard split). This screen reads persisted last-run/presentation state via planners; it is not a gameplay owner. Runtime UI/accessibility behavior was not exercised.

### 22. `app/src/main/java/com/anurag9000/forestrun/utils/MathUtils.kt` — 85 lines; read 1–85

Pure `MathUtils` object has `lerp`, `clamp`, `clamp01`, `normalise`, `map`, `smoothStep`, `sinDeg`, `cosDeg`, `dpToPx`, and `sign`. `lerp` permits extrapolation; `smoothStep` clamps its interpolation parameter; `normalise` returns zero for zero-width input range, uses Double arithmetic and clamps finite endpoints to `[0,1]`, but the nonfinite-endpoint branch uses Float arithmetic and `clamp01`; behavior for NaN values in that branch is not specified by comments and should not be asserted without future focused analysis. `map` inherits normalization clamping, so it is a normalized range map rather than unbounded affine map. Source declares no Android imports/state. Consumers are utility call sites throughout game/UI code. No numerical tests were executed.

## Cross-file system relationships

- **Activity/runtime:** `MainActivity` owns process/Activity lifecycle and constructs `GameView`; recovery presentation is delegated to `RecoveryEvidenceDialogCoordinator`; journal is a distinct Activity.
- **Game frame:** `GameView` connects `InputHandler`, `GameStateManager`, entity/system updates, `CollisionOutcomeDispatcher`/`LiveCollisionEffects`, `RunSessionTransitionPlanner`/Coordinator, persistence facade, HUD, audio and haptics.
- **Persistence:** `ApplicationPersistenceFacade` exposes separate persistence capabilities. `SaveIntegrityManager` repairs/assesses SharedPreferences state; run outcome, Garden purchase, feedback, wardrobe, relationship and recovery owners remain separate durability domains.
- **Meta-loop/UI:** `MainMenuScreen` routes into Garden through callback. `GardenScreen` uses canonical economy and purchase coordination, relationship/story/sanctuary planners and persistent managers. `ForestJournalActivity` reads/projections persistent history; it is not a mutation owner for Garden purchase.
- **Presentation/audio:** `HUD`, `FeedbackSettingsPanel`, menu, Garden, SFX and leitmotif managers consume state or callbacks, with GameView/Activity as orchestration points.

## Reused prior evidence and delta treatment

The file inventory identifies `docs/REPO_CODEBASE_MAP.md` and/or `docs/REPO_MEMORY_MAP.md` as earlier evidence for these files but gives no per-file precise line coverage. Those maps were treated as orientation only, not proof of current contents. All 22 current files were reread in full; this directly refreshes evidence for dirty tracked Kotlin files and clean files alike. No source-version delta was inferred from the generic map reference. No historical commit claim was made beyond the repository baseline hash.

## Status and remaining uncertainty

- Inspected completely in this shard: 22/22 scoped current Kotlin files.
- Static integration tracing: relationships summarized above, with primary call paths corroborated in inspected source. A full repository-wide reverse-call index is outside this bounded assignment.
- Runtime validation: none; explicitly prohibited by parent task.
- Main residual uncertainties: behavior on actual Android lifecycle/device/input/audio/haptic surfaces; real persisted preference repair scenarios; pixel/accessibility rendering; canonical correctness of research/gameplay semantics beyond source evidence; external runtime dependencies and build integration. Potential source concerns are deliberately not classified as confirmed defects without independent requirement/runtime evidence.

## Line coverage index

| File | Current lines | Read ranges |
|---|---:|---|
| `app/src/main/java/com/anurag9000/forestrun/ForestJournalActivity.kt` | 518 | 1–260, 261–518 |
| `app/src/main/java/com/anurag9000/forestrun/MainActivity.kt` | 392 | 1–220, 221–392 |
| `app/src/main/java/com/anurag9000/forestrun/RecoveryEvidenceDialogCoordinator.kt` | 127 | 1–127 |
| `app/src/main/java/com/anurag9000/forestrun/engine/ApplicationPersistenceFacade.kt` | 188 | 1–188 |
| `app/src/main/java/com/anurag9000/forestrun/engine/CollisionOutcomeDispatcher.kt` | 71 | 1–71 |
| `app/src/main/java/com/anurag9000/forestrun/engine/EncounterFamilyCatalogue.kt` | 210 | 1–210 |
| `app/src/main/java/com/anurag9000/forestrun/engine/GameStateManager.kt` | 526 | 1–526 |
| `app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt` | 1,965 | 1–320, 321–640, 641–960, 961–1280, 1281–1600, 1601–1965 |
| `app/src/main/java/com/anurag9000/forestrun/engine/HapticManager.kt` | 134 | 1–134 |
| `app/src/main/java/com/anurag9000/forestrun/engine/InputHandler.kt` | 258 | 1–258 |
| `app/src/main/java/com/anurag9000/forestrun/engine/LeitmotifManager.kt` | 550 | 1–280, 281–550 |
| `app/src/main/java/com/anurag9000/forestrun/engine/LiveCollisionEffects.kt` | 61 | 1–61 |
| `app/src/main/java/com/anurag9000/forestrun/engine/RecoveryEvidenceUserController.kt` | 127 | 1–127 |
| `app/src/main/java/com/anurag9000/forestrun/engine/RunSessionTransitionCoordinator.kt` | 71 | 1–71 |
| `app/src/main/java/com/anurag9000/forestrun/engine/RunSessionTransitionPlanner.kt` | 169 | 1–169 |
| `app/src/main/java/com/anurag9000/forestrun/engine/SaveIntegrityManager.kt` | 366 | 1–366 |
| `app/src/main/java/com/anurag9000/forestrun/engine/SfxManager.kt` | 298 | 1–298 |
| `app/src/main/java/com/anurag9000/forestrun/ui/FeedbackSettingsPanel.kt` | 204 | 1–204 |
| `app/src/main/java/com/anurag9000/forestrun/ui/GardenScreen.kt` | 1,190 | 1–240, 241–480, 481–720, 721–960, 961–1190 |
| `app/src/main/java/com/anurag9000/forestrun/ui/HUD.kt` | 540 | 1–540 |
| `app/src/main/java/com/anurag9000/forestrun/ui/MainMenuScreen.kt` | 633 | 1–160, 161–320, 321–480, 481–633 |
| `app/src/main/java/com/anurag9000/forestrun/utils/MathUtils.kt` | 85 | 1–85 |
