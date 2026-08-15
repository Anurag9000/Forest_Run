# Forest Run product-completion implementation sweep — 2026-08-15

This document records the direct-to-`main` source/product sweep that continued after the earlier architecture-remediation queue had already closed. It is a **source implementation record**, not physical-device, human, store, legal, signing, or release acceptance evidence.

## Scope

The sweep deliberately excluded:

- physical-device execution and performance acceptance;
- human gameplay/accessibility/art/audio review;
- Play Console actions;
- production signing credentials;
- legal/licensing decisions that require an owner/reviewer;
- external privacy-policy hosting;
- dataset/model work (Forest Run has no justified runtime ML dependency).

The goal was to exhaust remaining repository-addressable product value, remove stale/template source debt, and then harden the new product surfaces without inventing another architecture merely to create work.

## Major product work completed

### Forest Journal / Memory Book

A first-class Forest Journal is reachable from the willow Menu by touch and accessibility action. It uses native Android views for long-form scrolling and semantics while the runner remains Canvas/SurfaceView based.

The Journal is a strictly observational projection over existing authorities. It does not create a second progression store and opening it must not materialize missing gameplay state.

It now surfaces:

- all 19 encounter families;
- discovery state;
- lifetime meetings, clean passes, mercy/spares, and hits;
- authored temperament and field notes;
- known biome/variant context;
- six persistent relationship families and already-persisted stage/tone history;
- already-unlocked Bond rewards, rituals, and wearable memories;
- five collection tracks;
- derived long-horizon milestones;
- the six-pillar whole-forest completion capstone;
- full eight-style wardrobe lock/available/equipped state;
- exact nine-plant Garden sanctuary progression;
- Seed balance and next-plant affordability without allowing purchases;
- Kind/Merciful/Peaceful path history;
- `Every Gentle Shape` derived recognition;
- high score and best distance;
- total run and sanitized forest-mood history;
- most recent persisted Rest summary;
- persistent history marks;
- durable story/memory pages with player-facing titles/categories;
- pattern-specific prose for creature, Rest, weather, route, biome, Garden, relationship, repeated-encounter, and Bloom memory-page families.

Ephemeral Journal filters are:

- All;
- Progress;
- Bonds;
- Memories;
- Families.

They intentionally do not write to persistence.

### Journal read-only invariant hardening

A deeper continuation audit found a real contradiction between design and implementation: the original Journal projection called relationship APIs whose `stageFor()` fallback can invoke `refreshStage()`. On an old or repaired save with encounter history but no materialized stage, merely opening the Journal could therefore save a stage and potentially a Bond milestone.

That has been removed.

Current behavior:

- `ForestJournalComposer` reads persisted relationship stages directly with `SaveManager.loadRelationshipStage`;
- a relationship-capable family with history but no persisted stage is presented conservatively as `FIRST_IMPRESSION` rather than repaired on read;
- strongest-relationship presentation is derived from the read-only Journal snapshot;
- Bond completion counts are derived from snapshot stages rather than `relationshipsAtOrAbove()`;
- warm/strained Journal tone is calculated from read-only spare/hit/kindness/tender counters using the relationship system's tone semantics;
- Journal collection code no longer calls stage-refreshing relationship APIs;
- `CostumeManager.availableCostumes`/`activeCostume` are used instead of `refreshUnlocks`;
- memory pages are read through `StoryFragmentSystem.unlockedMemoryPages` rather than story methods that unlock new pages.

Two regression layers protect the invariant:

1. `ForestJournalReadOnlyTest` uses a dedicated compatibility preference namespace, creates Fox history strong enough for a Bond while deliberately omitting saved stage/milestone state, renders both Journal and collection projections, and requires the entire preference map to remain unchanged.
2. `scripts/test_forest_journal_read_only_contract.py` rejects mutating relationship/costume/story dependencies in the projection source.

### Collection/progression projections

Read-only projection owners separate player-facing interpretation from persistence mutation:

- `ForestCollectionProgressComposer`;
- `ForestGardenHistoryComposer`;
- `ForestPathHistoryComposer`;
- `ForestRunLegacyComposer`;
- `ForestCompletionCapstoneComposer`;
- `ForestMemoryPagePresenter` / `ForestMemoryPageNarrative`.

No new currency, achievement flag, capstone flag, or save namespace was introduced.

The whole-forest capstone combines the five bounded collection tracks with one sixth route-history pillar. Until all six are complete it presents **A Forest Still Becoming**; once all are complete it presents **The Forest Knows Your Name**. Completion is recalculated from authoritative histories rather than stored separately.

`ForestRunLegacyComposer` also now sanitizes negative/corrupt mood history before selecting dominant mood and rejects nonfinite/negative distance values before presentation. Focused JVM tests cover those edge cases.

### Garden catalogue ownership

`GardenEconomy` exposes stable catalogue metadata rather than an anonymous cost array:

| Index | Full name | Compact card name | Seeds |
|---:|---|---|---:|
| 0 | Lily | Lily | 15 |
| 1 | Cactus | Cactus | 20 |
| 2 | Hyacinth | Hyacinth | 25 |
| 3 | Eucalyptus | Eucalyptus | 30 |
| 4 | Vanilla Orchid | Orchid | 40 |
| 5 | Weeping Willow | Willow | 50 |
| 6 | Jacaranda | Jacaranda | 60 |
| 7 | Bamboo | Bamboo | 75 |
| 8 | Cherry Blossom | Cherry | 100 |

`GardenPurchaseManager` remains the purchase owner. Garden visuals/sprites remain `GardenScreen`/`SpriteManager` owned.

The current Canvas `GardenScreen` still contains a matching local presentation list for colours/emoji/sprite ordering. That literal duplication is a small presentation-maintainability debt, **not** a duplicate purchase/economy authority.

To prevent it from becoming correctness drift, `scripts/test_garden_catalogue_contract.py` now parses the runtime `GardenEconomy` catalogue and requires:

- exactly nine contiguous, unique entries;
- the live Seed-cost sequence;
- exact matching order/cost tables in README and `docs/GAME_DESIGN.md`;
- exact matching compact name/cost pairs in `GardenScreen`;
- purchase routing through `persistenceFacade.purchaseNextGardenPlant(i)` rather than direct Garden/Seed SaveManager writes.

### Semantic haptic vocabulary and orchestration

`HapticManager` exposes meaning-based cues:

- `lightTick()`;
- `stumbleImpact()`;
- `terminalImpact()`;
- `mercyAcknowledgement()`;
- `gardenGrowth()`;
- `bloomSurge()`.

Existing `shortPulse`/`mediumPulse`/`longPulse`/`doubleTap` wrappers remain compatibility aliases so physical timing does not change accidentally.

The continuation sweep also moved collision **domain orchestration** to semantic names:

- terminal HIT → `terminalImpactHaptic()`;
- STUMBLE → `stumbleImpactHaptic()`;
- MERCY_MISS → `mercyAcknowledgementHaptic()`.

Compatibility primitive methods remain only at adapter/test boundaries. This is no longer a domain-model debt.

### Launcher/application visual identity

The stock Android Studio launcher artwork was removed.

Source now contains:

- Forest Run adaptive icon background;
- original willow-leaf / Seed / Bloom foreground mark;
- adaptive monochrome support through the same mark;
- pre-adaptive `mipmap-anydpi` vector icon;
- pre-adaptive round vector icon;
- no remaining mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi stock launcher WebPs.

The stock Material purple/teal colour file was replaced by Forest Run forest/willow/Seed/Bloom colours, and the app cold-launch/system-bar window background uses the forest identity rather than template black.

Final artistic approval and legal provenance are still separate external gates; source authorship does not equal rights approval.

## Canonical documentation and contract hardening

The continuation sweep found that some current canonical documents had fallen behind live source even though dated audits were allowed to remain historical.

Corrected current truth includes:

- README restored every candidate-evidence-layer link required by the release contract after the earlier rewrite accidentally dropped installed-candidate/human/governance references;
- `docs/GAME_DESIGN.md` now uses the live Garden order/costs and treats fixed landscape as the current source/product decision rather than a pending code decision;
- `docs/ARCHITECTURE.md` now reflects current run/collision ownership, semantic haptics, Garden authority, Journal projection, accessibility, persistence/recovery, and candidate evidence layers;
- `docs/FOREST_JOURNAL.md` now specifies the strictly observational relationship/story/costume read boundary rather than implying that the Journal may call live stage-refreshing APIs.

Permanent contracts added in this sweep:

- `scripts/test_garden_catalogue_contract.py`;
- `scripts/test_current_architecture_contract.py`;
- `scripts/test_forest_journal_read_only_contract.py`;
- focused Kotlin `ForestJournalProjectionTest`;
- Robolectric `ForestJournalReadOnlyTest`.

## CI regression discovered and repaired

The first exact-head validation after the large product sweep failed before Android compilation because the rewritten README omitted evidence-layer document links required by `test_candidate_evidence_layers_contract.py`.

The failure was not hidden or treated as a flaky run. The README was corrected to restore all required canonical layers, including:

- `docs/INSTALLED_CANDIDATE_IDENTITY.md`;
- `docs/HUMAN_ACCEPTANCE.md`;
- `docs/RELEASE_GOVERNANCE_EVIDENCE.md`.

The immediately following run passed the Python/tooling stage before being cancelled by newer direct-main continuation commits. Final exact-head validation is required again after this sweep stops mutating source.

## Stale assumptions corrected by the sweep

The following were previously described as “remaining” but are already true in source:

- **Landscape decision:** the app is explicitly landscape; this is no longer an unresolved source implementation decision.
- **Wolf asset absence:** a real Wolf sprite sheet exists. Remaining Wolf work, if any, is artistic/rights approval rather than missing implementation.
- **Audio architecture:** adaptive/stateful music and SFX owners already cover Menu/Garden, run states, Rest, Bloom, encounter cues, mercy/hit and fallback behavior; another audio engine is not needed.
- **Garden visitors/memory:** the Garden already has relationship visitors, return moments, home characters, route/mood history, sanctuary traces, story reflections, wardrobe signs and atmosphere effects.
- **Wardrobe architecture:** eight current styles and persistent unlock/equip behavior already exist.
- **Store tooling:** candidate-bound graphics, metadata, screenshots and release-evidence tooling already exist.
- **Software licence:** no root `LICENSE` has been selected. Governance deliberately requires an owner/legal decision and must remain unresolved until that decision is real.
- **Private vulnerability reporting:** source policy exists, but the repository setting was observed disabled during this sweep. Documentation must not claim it is enabled until the external setting is actually changed and verified.

## Source markers / template debt

Repository code search during the sweep found no ordinary live `TODO`, `FIXME`, `placeholder`, or `yourname` package marker requiring implementation.

The `Final_Assets (2)` tree is source creative material, not Android-template runtime debris. It was not deleted because source/reference artwork may be required for provenance, replacement, or final creative review.

## Remaining source-addressable debt after this continuation

No missing gameplay/persistence/relationship/Bloom/encounter architecture is known from the original remediation goals.

The remaining source-only items are narrow:

1. **Garden presentation deduplication:** `GardenScreen` still carries compact names/costs next to its local colour/emoji/sprite presentation metadata. It is executable-contract-bound to `GardenEconomy`, so this cannot silently become a gameplay/economy divergence. Migrating the large Canvas file to derive those two fields directly is maintainability polish, not a correctness blocker.
2. **Garden growth haptic integration:** `HapticManager.gardenGrowth()` exists, but the large Canvas Garden purchase call site should only be changed when it can be done safely and tested so a successful atomic purchase emits the cue exactly once and failed/insufficient-Seed attempts emit none.
3. **Compatibility haptic adapter names:** duration-shaped wrappers remain at old adapter/test boundaries. Domain orchestration is already semantic, so opportunistic adapter cleanup is nonblocking.
4. **Final creative bytes:** choosing/creating final art/audio/animation and approving quality is a creative/human task, not something source architecture can truthfully self-certify.
5. **Candidate-bound release material:** exact final notes/metadata/screenshots/artifact manifests must be created only after one real production candidate is frozen.

None of these justify another persistence system, collision system, state machine, relationship engine, Garden visitor engine, runtime ML model, account system, cloud service, ads layer, or multiplayer subsystem.

## External/non-source blockers intentionally left unresolved

- final creative/art/audio/haptic human approval;
- creative rights/provenance/licence/attribution review;
- source-code/asset/audio/font/trademark/contribution licence decision;
- third-party notices after resolved dependency review;
- enabling/verifying private vulnerability reporting or another accepted private channel;
- publishing the accepted privacy policy at stable HTTPS;
- production upload key and Play App Signing identity;
- Play Console ownership/declarations/content rating/target audience/category/regions/support metadata;
- exact signed candidate artifacts and store delivery;
- candidate-specific release notes and production tag;
- accountable final release approval.

These must not be marked complete merely because repository validators or documentation schemas exist.

## Implementation principle going forward

Further work should be driven by a concrete missing player experience, correctness defect, maintainability problem, or external acceptance finding. The repository is past the point where additional broad architecture automatically improves the product.

For persistent features, prefer **projecting existing authoritative history into visible player value** over adding another save namespace. For large Canvas owners, prefer narrow edits tied to real behavior changes over decomposition or renaming churn.
