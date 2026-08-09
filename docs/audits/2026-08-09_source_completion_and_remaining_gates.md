# Forest Run — Source Completion and Remaining Gates Audit (2026-08-09)

## 1. Purpose and authority

This audit continues the exhaustive documentation/source reconciliation recorded in `docs/audits/2026-08-06_documentation_reconciliation_audit.md`. It answers four questions against the current canonical repository:

1. What did Forest Run set out to build?
2. Which previously identified source-addressable items are now implemented?
3. What additional defects or contradictions were discovered while closing them, and how were they resolved?
4. What genuinely remains before Forest Run can be called a physically accepted, signed, store-ready production release?

Current source/tests and current canonical documentation take precedence over older tranche-local statements. `docs/AUDIT_LEDGER.md` now explicitly preserves its older sections as historical provenance rather than present-day debt.

The exact **source-bearing validation checkpoint** for this audit is:

- commit: `414bf30b36ce051f0d5ef75f6143ed6bf8fa5884`
- Android validation run: `31297723150`
- host/release/lint/package/R8 job: success
- API-35 connected-behavior job: success

Later documentation-only commits may advance `main`; they do not change the source conclusions below and must still pass their own exact-head validation before being called validated.

## 2. Reconstructed product and engineering mission

Forest Run was not intended to be only a minimal endless-runner prototype. The combined mission is a coherent handcrafted Android game and a defensible release pipeline:

- native Kotlin custom `SurfaceView`/Canvas game loop;
- willow/menu ritual leading into a five-biome run;
- responsive tap, hold, release, and swipe-down controls with cancellation-safe ownership and no gesture leakage across screens;
- deterministic player physics and bounded lifecycle catch-up;
- five authored runtime biomes with transitions and environmental response;
- exactly nineteen encounter families spanning flora, trees, birds, and animals;
- deterministic collision arbitration with one selected outcome and no double-resolution;
- Seed collection, Seed Orbs, Bloom, mercy, kindness, pacifist/clean-run progression, score, distance, and authored run summaries;
- outcome-earned relationships, encounter memory, repeat-friend/strained-bond continuity, world memory, return moments, and persistent narrative traces;
- soft failure through Rest rather than a punitive hard game-over, followed by a changed persistent Garden;
- nine-plant Garden economy with atomic spending, persistent wardrobe/costume progression, and remembered next-run state;
- deterministic ghost recording/playback plus recoverable best-run promotion and compatibility support;
- lifecycle, save repair, corruption recovery, process-death resilience, namespace isolation, and fail-closed maintenance;
- accessibility for the custom Canvas UI, including real semantic navigation/actions rather than synthetic touch coordinates;
- deterministic debug/fairness/readability scenarios and evidence capture;
- performance telemetry and representative-device evidence tooling;
- release integrity, screenshot/graphics/metadata/provenance/SBOM/security/privacy/device-acceptance tooling;
- direct-to-`main` delivery with preserved history, no development PR/branch churn, and read-only permanent validation.

## 3. Previously identified source-addressable queue — closure audit

### 3.1 Collision-result dispatcher — CLOSED

The former top-level collision-result branching in `GameView` has been replaced by `CollisionOutcomeDispatcher` as the single dispatcher for terminal HIT, STUMBLE, MERCY_MISS, and NONE results.

It delegates to:

- `TerminalHitImpactCoordinator` for immediate impact ordering;
- `TerminalHitOutcomeCoordinator` for relationship/presentation/summary/quote/persistence completion;
- `NonTerminalCollisionOutcomeCoordinator` for STUMBLE and MERCY_MISS.

The live owner supplies a typed capture once and consumes a typed dispatch result. The former behavior-sensitive dispatcher debt is therefore closed.

### 3.2 Private live collision-effect adapters — CLOSED

The former `GameViewTerminalHitImpactEffects` and `GameViewNonTerminalCollisionEffects` adapters were removed. One shared `LiveCollisionEffects` adapter now maps terminal and nonterminal effects to Player, ghost, camera, audio, haptic, particle, flash, and run-state owners while coordinator tests preserve exact ordering.

### 3.3 Run-session coordinator and top-level state ownership — CLOSED

`RunSessionTransitionPlanner` is the pure table for Menu, Garden, Playing, DYING, GAME_OVER, and RESTARTING transitions. `RunSessionTransitionCoordinator` executes ordered effects through `LiveRunSessionEffects`; `GameView` publishes the after-state only through one authoritative `applyRunSessionEvent(...)` boundary.

A final audit found that debug scenario/autostart launch code still assigned `appState` and `runState` directly. That exception is now removed: both debug launch paths emit `DEBUG_PLAYING_STATE_REQUESTED` through the same session owner.

The planner now explicitly distinguishes:

- a declared/accepted transition;
- an accepted idempotent debug request whose target state is already published;
- an invalid or stale ordinary no-op.

This preserves repeated `singleTask` debug-intent behavior without allowing stale ordinary events to masquerade as successful transitions.

### 3.4 Application-level persistence facade — CLOSED

`ApplicationPersistenceFacade` is no longer merely a unit-tested seam. One shared live facade owns application mutation entrypoints for:

- terminal run-outcome persistence;
- encounter/pass/hit memory;
- terminal and nonterminal relationship writes;
- Garden purchases;
- wardrobe/costume writes;
- reduced-motion/audio/haptic settings;
- recovery inspection/retry/discard operations.

`EntityManager`, `GameView`, Menu settings, Garden touch actions, and accessibility actions route through the facade where appropriate.

This does **not** claim one global transaction across unrelated durability mechanisms. SharedPreferences state, non-ghost recovery journals, and AtomicFile ghost protocols remain intentionally independent recoverable domains.

### 3.5 Canonical encounter/content catalogue — CLOSED

The repository already contained `EncounterFamilyCatalogue`; a temporary duplicate catalogue was detected during the audit and removed rather than allowed to become a competing authority.

The canonical catalogue now covers all nineteen families while deriving instead of duplicating existing authorities for:

- ordinary biome reachability;
- deterministic scenario coverage;
- focused single-family fairness/readability coverage;
- relationship eligibility;
- authored variants;
- implementation/factory/asset wiring.

Tests/contracts require every encounter type to remain reachable, factory/asset-wired, deterministically covered, and backed by a focused readability/fairness scenario.

### 3.6 Ordinary-player recovery UI — CLOSED

Recovery was found to be further along than older audits claimed. The live implementation now has a typed ordinary-player path through `RecoveryEvidencePresentation`, `RecoveryEvidenceUserController`, and `RecoveryEvidenceDialogCoordinator`.

The UI:

- exposes privacy-safe domain/status copy only;
- never surfaces raw journal payloads, local paths, hashes, ghost frames, or exception text;
- revalidates every requested action;
- makes safe retry non-destructive;
- requires a second explicit confirmation for destructive discard;
- reports races/no-longer-applicable outcomes safely.

Debug/ADB recovery maintenance remains a separate support/acceptance surface using the same fail-closed domain rules.

### 3.7 Real custom-Canvas accessibility hierarchy — CLOSED AT SOURCE LEVEL

The previous fixed-coordinate accessibility delegate and synthetic touch dispatch were removed. `GameView` now owns a real Android virtual-node hierarchy through `GameAccessibilityNodeProvider`.

The accessibility stack includes:

- stable semantic node IDs for Menu, Settings, Playing, Garden, wardrobe, and Rest;
- `GameAccessibilitySemantics` as the semantic-tree builder;
- `GameAccessibilityActionRouter` with stale/disabled/unsupported-action rejection;
- `LiveGameAccessibilityActions` routing directly to session/input/persistence owners;
- layout-derived semantic bounds;
- virtual accessibility focus;
- click/checkable state publication;
- truthful state for all Garden plants and wardrobe styles;
- Rest continuation disabled until GAME_OVER;
- content-change notification after successful state mutation;
- `AccessibilityAnnouncementPolicy` live under accessibility + touch exploration, coalescing routine Playing distance while prioritizing meaningful surface/Bloom/Garden/settings changes.

The installed API-35 suite now queries the actual `GameView` provider, navigates Menu → accessibility Settings, toggles persisted reduced motion through `ACTION_CLICK`, closes Settings, and verifies the semantic tree changes without synthetic coordinates.

Physical TalkBack usability remains a release gate; source integration is not a substitute for human screen-reader acceptance.

## 4. Additional defects discovered during closure

The exhaustive closure work found and fixed several issues that were not merely items copied from the old queue.

### 4.1 Internal persistence types leaked through public Kotlin constructors — FIXED

Facade adoption initially exposed internal persistence types through public constructors in `EntityManager`, `MainMenuScreen`, and `GardenScreen`, which Kotlin rejected during real compilation. Their injected constructors are now internal, preserving encapsulation without widening persistence implementation types into the app's public API. A permanent source contract locks this boundary.

### 4.2 Accessibility actions crashed while Android accessibility was globally off — FIXED

The real API-35 instrumentation test directly exercised semantic actions in an emulator where accessibility services were disabled. The action itself was valid, but framework event emission attempted `AccessibilityManager.sendAccessibilityEvent(...)` and threw `IllegalStateException`.

`GameAccessibilityNodeProvider` now checks the framework `AccessibilityManager.isEnabled` state before emitting content/click/focus events. Semantic queries and routing remain deterministic while accessibility is off; framework events are emitted only when the framework can accept them.

### 4.3 Repeated debug intents rejected an already-published PLAYING state — FIXED

The typed debug event originally produced an unchanged transition when the activity was already PLAYING/PLAYING. The coordinator classified all unchanged transitions as non-adoptable no-ops, so repeated `singleTask` scenario intents were treated as rejected and crashed the connected test process.

`RunSessionTransition` now records whether an event/state pair is declared (`accepted`). An accepted idempotent debug request may be acknowledged without effects, while invalid/stale ordinary events remain unaccepted and fail closed.

### 4.4 Stale source/governance assertions — FIXED

Several permanent tests had encoded old architecture as requirements: direct Garden manager ownership, debug state forcing outside session routing, a deleted accessibility delegate, and older wording/announcement constants. Those assertions were reconciled to the actual current ownership rather than changing correct production code to satisfy stale prose.

### 4.5 Stale canonical documentation — FIXED

Current documentation contained historical claims after the source had moved on. The reconciliation corrected:

- accessibility docs that described the deleted delegate/future provider;
- recovery UX text that described a future panel despite the live controller/dialog;
- architecture text that described private collision adapters/direct state ownership/distributed live persistence;
- README source status and remaining blockers;
- `docs/AUDIT_LEDGER.md`, which now explicitly marks older tranche-local debt as historical;
- the final architecture debt line, which now records exact source/build/emulator success at the source-bearing checkpoint rather than claiming those checks remain unobserved.

Dated audits themselves remain provenance and are not rewritten to erase history.

## 5. Automated evidence at the source-bearing checkpoint

Android validation run `31297723150` checked out exact source commit `414bf30b36ce051f0d5ef75f6143ed6bf8fa5884` and completed successfully.

### Host/release/lint/package/R8 job — SUCCESS

The successful host job covered:

- immutable-source/governance contracts;
- the complete discovered Python validation suite;
- Java 21 / Android API 36 / Gradle setup and wrapper validation;
- candidate-bound declared/resolved dependency evidence construction;
- debug and release Kotlin compilation;
- unit-test and instrumentation-test Kotlin compilation;
- the complete JVM/Robolectric suite;
- debug and release lint;
- debug APK and androidTest APK assembly;
- minified/resource-shrunk release AAB construction;
- packaged 16 KB page-size/native-code inspection;
- effective R8 application-class renaming verification;
- post-build source immutability.

### API-35 connected-behavior job — SUCCESS

The successful connected job covered the installed app on API 35, including the new real `GameView` accessibility provider flow and repeated debug-intent/session behavior, followed by connected-source immutability verification.

This is strong source/build/emulator evidence. It does **not** prove physical-device performance, human fairness, TalkBack usability, signing, store delivery, or policy acceptance.

## 6. Current source-state conclusion

After the exhaustive re-audit, **no known source-addressable item remains open from the prior collision/session/persistence/catalogue/accessibility/recovery architecture queue.**

That statement is deliberately narrower than “the game is finished.” Forest Run remains a **source-ready feature-rich alpha** because the remaining release decision depends on candidate-bound evidence, external credentials/services, legal/product choices, and human/physical acceptance.

Further broad decomposition of `GameView` is not recommended merely to create activity. It remains a large SurfaceView orchestration host, but its high-risk collision/session/persistence/accessibility ownership has been extracted or bounded. Future decomposition should be motivated by measured maintainability, device performance, or an observed correctness defect and should proceed incrementally with exact contracts.

## 7. Remaining candidate-bound and external gates

### 7.1 Candidate freeze and representative physical matrix

Freeze one exact candidate only when external release inputs are available, then exercise at least:

- older phone;
- midrange phone;
- high-refresh phone;
- cutout/unusual-aspect phone;
- tablet.

Run both deterministic scenarios and ordinary play on the exact accepted artifact.

### 7.2 Physical performance and stability evidence

Collect and review, per candidate/device/scenario:

- p95 and p99 frame processing time;
- slow-frame ratio and worst-frame behavior;
- allocation and GC pressure;
- Java/native/PSS memory;
- disk I/O and ghost-write behavior;
- audio-thread behavior;
- crashes and ANRs;
- thermal throttling/temperature behavior;
- battery impact;
- long-session behavior;
- dense Bloom stress;
- all-entity stress;
- simultaneous/overlapping persistence and recovery scenarios where practical.

### 7.3 Human gameplay/fairness acceptance

Review on hardware:

- touch latency;
- short-jump and hold-jump feel;
- swipe-down duck reliability;
- gesture cancellation around lifecycle/screen transitions;
- telegraph/hitbox/outcome agreement for all nineteen families;
- high-speed encounter combinations;
- Bloom readability under dense hazards;
- Rest/restart continuity;
- safe areas/cutouts/unusual aspect ratios;
- text/contrast/readability;
- Garden/wardrobe continuity;
- ghost readability;
- long-run relationship/progression cadence.

### 7.4 Physical accessibility acceptance

Test the exact candidate with current TalkBack (and Switch Access where applicable), including:

- focus order and traversal;
- labels and state descriptions;
- click/checkable action reliability;
- Settings toggles;
- Playing controls;
- Garden plants and wardrobe;
- Rest and recovery dialogs;
- announcement cadence and non-chatter behavior;
- lifecycle/resume/configuration change;
- large font/display scale;
- cutout/aspect variants;
- audio coexistence;
- reduced-motion behavior.

### 7.5 Signing, installation, and store delivery

External signing material is still required. The release sequence must prove:

- real keystore/certificate identity;
- signed minified artifact construction;
- signed install and update smoke tests;
- certificate continuity;
- internal Play-track upload/delivery;
- receipt/update/store-path behavior for the exact candidate.

Signing secrets must remain external and must not be committed.

### 7.6 Final art, audio, haptic, and presentation approval

Human review remains required for:

- final artwork and animations, including Wolf;
- encounter readability/telegraphs;
- procedural scenic layers versus desired final art direction;
- fixed-landscape product decision;
- screenshots and store graphics;
- audio balance/latency/loudness;
- haptic intensity/cadence;
- reduced-motion presentation;
- final accessibility presentation.

### 7.7 Privacy, Play policy, security, licensing, and provenance decisions

Still required externally or as explicit owner decisions:

- publish the privacy policy at a stable HTTPS location;
- finalize Play Data Safety answers;
- finalize content rating and target audience;
- review current Play policies at candidate time;
- resolve dependency vulnerability review and dependency verification policy;
- finalize source-code licence choice;
- finalize dependency and asset licence attribution/provenance/distribution approval;
- enable/verify the intended private vulnerability-reporting path before claiming it exists;
- bind signed-artifact provenance to the accepted candidate.

The repository should not invent legal/licensing/security approvals merely to make a checklist green.

### 7.8 Final candidate-bound evidence and independent decision

After all preceding gates, compile and independently review:

- exact commit/version/application identity;
- signed artifact SHA-256;
- signing certificate SHA-256;
- physical-device manifests and measurements;
- deterministic trace evidence;
- human fairness/accessibility approvals;
- screenshot/graphics/metadata approvals;
- dependency/SBOM/security/licence/provenance evidence;
- privacy/policy decisions;
- release notes/changelog for the accepted version;
- aggregate acceptance result;
- release-evidence index and hashes;
- independent reviewer identities/decision.

Only then should the release be tagged/promoted as an accepted production candidate.

## 8. Intentional limitations that remain

These are known boundaries, not hidden implementation TODOs:

- ghost/distance mismatches from before persistent manifests cannot be reconstructed retroactively;
- healthy legacy sidecars can remain legacy until strong validation/upgrade is needed;
- SHA-256 binds local content/distance identity but does not authenticate a trusted writer;
- ghost promotion recovery and non-ghost run-outcome recovery remain separate durability domains;
- emulator automation cannot prove real-device timing, thermal, battery, haptic, audio, screen-reader usability, or subjective fairness;
- final product/legal/store decisions cannot be generated truthfully without their external evidence/owners.

## 9. Release classification

As of this audit:

- **Source-ready feature-rich alpha:** YES.
- **Previously identified source-architecture queue closed:** YES.
- **Exact source/build/API-35 emulator validation green at the named checkpoint:** YES.
- **Physically accepted release candidate:** NO.
- **Signed/store-delivered accepted candidate:** NO.
- **Public production release:** NO.

The correct next phase is candidate-bound physical, human, signing, store, policy, provenance, and independent acceptance—not speculative source churn.
