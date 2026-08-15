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

The goal was to exhaust remaining repository-addressable product value and remove stale/template source debt without inventing another architecture merely to create work.

## Major product work completed

### Forest Journal / Memory Book

A first-class Forest Journal is now reachable from the willow Menu by touch and accessibility action. It uses native Android views for long-form scrolling and semantics while the runner remains Canvas/SurfaceView based.

The Journal is a read-only projection over existing authorities. It does not create a second progression store.

It now surfaces:

- all 19 encounter families;
- discovery state;
- lifetime meetings, clean passes, mercy/spares, and hits;
- authored temperament and field notes;
- known biome/variant context;
- six persistent relationship families and their stage/tone;
- Bond rewards, rituals, and wearable memories;
- five collection tracks;
- derived long-horizon milestones;
- the six-pillar whole-forest completion capstone;
- full eight-style wardrobe lock/available/equipped state;
- exact nine-plant Garden sanctuary progression;
- Seed balance and next-plant affordability without allowing purchases;
- Kind/Merciful/Peaceful path history;
- `Every Gentle Shape` derived recognition;
- high score and best distance;
- total run and forest-mood history;
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

### Collection/progression projections

New read-only projections separate player-facing interpretation from persistence mutation:

- `ForestCollectionProgressComposer`;
- `ForestGardenHistoryComposer`;
- `ForestPathHistoryComposer`;
- `ForestRunLegacyComposer`;
- `ForestCompletionCapstoneComposer`;
- `ForestMemoryPagePresenter` / `ForestMemoryPageNarrative`.

No new currency, achievement flag, capstone flag, or save namespace was introduced.

The whole-forest capstone combines the five bounded collection tracks with one sixth route-history pillar. Until all six are complete it presents **A Forest Still Becoming**; once all are complete it presents **The Forest Knows Your Name**. Completion is recalculated from authoritative histories rather than stored separately.

### Garden catalogue ownership

`GardenEconomy` now exposes stable catalogue metadata rather than only an anonymous cost array:

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

The current Canvas `GardenScreen` still contains a matching local presentation list for colours/emoji/sprite ordering. That literal duplication is a small presentation-maintainability debt, **not** a duplicate purchase/economy authority. A future safe local edit may derive its `name`/`seedCost` fields directly from `GardenEconomy`; there is no justification for rewriting the entire large Canvas screen through a whole-file remote API solely for that cosmetic deduplication.

### Semantic haptic vocabulary

`HapticManager` now exposes meaning-based cues:

- `lightTick()`;
- `stumbleImpact()`;
- `terminalImpact()`;
- `mercyAcknowledgement()`;
- `gardenGrowth()`;
- `bloomSurge()`.

Existing `shortPulse`/`mediumPulse`/`longPulse`/`doubleTap` wrappers remain compatibility aliases so behavior does not change accidentally. Bloom already uses the semantic `bloomSurge` call.

The large existing Canvas owners still contain some generic compatibility names. Renaming those arguments without behavior gain is not a release blocker and was deliberately not used as an excuse for a risky large-file rewrite.

### Launcher/application visual identity

The stock Android Studio launcher artwork was removed.

Source now contains:

- Forest Run adaptive icon background;
- original willow-leaf / Seed / Bloom foreground mark;
- adaptive monochrome support through the same mark;
- pre-adaptive `mipmap-anydpi` vector icon;
- pre-adaptive round vector icon;
- no remaining mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi stock launcher WebPs.

The stock Material purple/teal colour file was replaced by Forest Run forest/willow/Seed/Bloom colours, and the app cold-launch/system-bar window background now uses the forest identity rather than template black.

Final artistic approval and legal provenance are still separate external gates; source authorship does not equal rights approval.

### Store/product/creative source truth

Current checked-in source now includes:

- `docs/STORE_LISTING.md` — canonical public description, screenshot story, icon/wordmark/feature-graphic direction;
- `docs/CREATIVE_DIRECTION.md` — final art, animation, biome, Bloom, Garden, audio, SFX, and haptic direction;
- `docs/FOREST_JOURNAL.md` — Journal ownership/progression contract;
- a reconciled root `README.md` describing the current product instead of an old exact-SHA checkpoint.

## Stale assumptions corrected by the sweep

The following were previously described as “remaining” but are already true in source:

- **Landscape decision:** `MainActivity` is explicitly landscape; this is no longer an unresolved source implementation decision.
- **Wolf asset absence:** a real Wolf sprite sheet exists. Remaining Wolf work, if any, is artistic/rights approval rather than missing implementation.
- **Audio architecture:** adaptive/stateful music and SFX owners already cover Menu/Garden, run states, Rest, Bloom, encounter cues, mercy/hit and fallback behavior; another audio engine is not needed.
- **Garden visitors/memory:** the Garden already has relationship visitors, return moments, home characters, route/mood history, sanctuary traces, story reflections, wardrobe signs and atmosphere effects.
- **Wardrobe architecture:** eight current styles and persistent unlock/equip behavior already exist.
- **Store tooling:** candidate-bound graphics, metadata, screenshots and release-evidence tooling already exist.
- **Software licence:** contrary to one earlier summary, no root `LICENSE` has been selected. Governance deliberately requires an owner/legal decision and must remain unresolved until that decision is real.
- **Private vulnerability reporting:** source policy exists, but the repository setting was observed disabled during this sweep. Documentation must not claim it is enabled until the external setting is actually changed and verified.

## Source markers / template debt

Repository code search during the sweep found no ordinary live `TODO`, `FIXME`, `placeholder`, or `yourname` package marker requiring implementation.

The `Final_Assets (2)` tree is source creative material, not Android-template runtime debris. It was not deleted because source/reference artwork may be required for provenance, replacement, or final creative review.

## Remaining source-addressable debt after this sweep

No missing gameplay/persistence/relationship/Bloom/encounter architecture is known from the original remediation goals.

The remaining source-only items are low-risk polish/debt, principally:

1. **Garden presentation deduplication:** migrate the large Canvas screen's matching compact names/costs to `GardenEconomy` when doing a safe local edit of that file.
2. **Semantic haptic call-site naming:** progressively replace compatibility-wrapper names in large existing owners when those owners are otherwise being edited; behavior already maps to the semantic cues.
3. **Final creative bytes:** source can accept replacements, but choosing/creating final art/audio/animation and approving their quality is a creative/human task, not something source architecture can truthfully self-certify.
4. **Candidate-bound release material:** exact final notes/metadata/screenshots/artifact manifests must be created only after one real production candidate is frozen.

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
