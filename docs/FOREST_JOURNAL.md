# Forest Journal — persistent memory and collection contract

The Forest Journal is the player-facing view of Forest Run's existing long-horizon memory. It is deliberately **not** a second progression system.

## Purpose

Forest Run already remembers encounters, clean passes, mercy, harm, relationship stages, route history, Garden growth, wardrobe unlocks, biome friendship, run legacy, forest mood, return moments, and story pages. The Journal makes that persistence legible to the player so the forest's memory is visible rather than only influencing hidden presentation.

The Journal is reachable from the willow menu through the **MEMORY → FOREST JOURNAL** action and is rendered with native Android views so long-form content remains scrollable and accessible independently from gameplay frame timing.

## Source-of-truth rule

Opening or reading the Journal must not:

- award or spend Seeds;
- purchase Garden plants;
- refresh or fabricate costume unlocks;
- increase encounter/pass/spare/hit counters;
- mutate or materialize relationship stage, affinity, tone, or milestones;
- mark a milestone or capstone as completed in a new achievement store;
- create story pages;
- change route counters or forest mood;
- alter the active costume;
- overwrite high score, distance, or last-run history.

Every displayed value is derived from an existing runtime owner or an observational projection over existing stored facts.

| Journal surface | Runtime authority |
| --- | --- |
| 19-family discovery and encounter history | `EncounterFamilyCatalogue` + `PersistentMemoryManager` |
| persisted relationship stage/Bond reward | `SaveManager` + `RelationshipArcSystem` reward metadata |
| relationship tone shown in the Journal | read-only spare/hit/kindness/tender counters using the same tone semantics as the relationship system |
| Garden order/names/costs | `GardenEconomy` |
| Garden progress and Seed balance | `SaveManager` |
| wardrobe completion/equipped style | `SaveManager` + read-only `CostumeManager` accessors + `CostumeStyle` |
| peaceful-biome history | `PersistentMemoryManager` + `Biome` |
| Kind/Merciful/Peaceful route history | `SaveManager` + `PacifistRouteTier` |
| high score, distance, last Rest, world mood/run history | `SaveManager` + sanitized `ForestMoodState` projection |
| memory pages | read-only `StoryFragmentSystem.unlockedMemoryPages` + `SaveManager` |
| history marks | `PersistentMemoryManager` |
| whole-forest completion capstone | derived from collection tracks + path history |

## Strictly observational relationship reads

Normal gameplay owns relationship-stage materialization. `RelationshipArcSystem.refreshStage()` can write a new stage and can persist a newly reached Bond milestone; therefore the Journal must never call that path directly or indirectly.

`ForestJournalComposer` reads the already-persisted stage with `SaveManager.loadRelationshipStage`. For a relationship-capable family whose old/repaired save contains encounter history but no saved stage, the Journal deliberately presents **First Impression** rather than repairing the save while it is being read. A later normal gameplay outcome remains responsible for refreshing/materializing the stage.

Likewise:

- Bond completion counts come from the stages already present in the Journal snapshot, not `relationshipsAtOrAbove()`;
- strongest-relationship display is derived from the same read-only snapshot;
- warm/strained Journal tone is computed from read-only counters and mirrors the relationship tone rules without calling a stage-refreshing API;
- Bond reward/ritual metadata may be read only when the corresponding persisted milestone is already unlocked.

`ForestJournalReadOnlyTest` seeds enough relationship history to qualify for a Bond while leaving the saved stage absent, opens/composes the Journal, and requires the complete preference map to remain unchanged. `scripts/test_forest_journal_read_only_contract.py` also prevents the projection layer from regressing to relationship-refresh, costume-refresh, or story-unlock APIs.

## Journal sections

The long-form Journal has ephemeral native-view filters:

- **All**;
- **Progress**;
- **Bonds**;
- **Memories**;
- **Families**.

The selected filter is intentionally not a progression fact and is not written to game persistence.

## Run Legacy

`ForestRunLegacyComposer` exposes durable run history that was already saved before the Journal existed:

- high score;
- best distance;
- total remembered runs;
- current forest mood and its streak;
- dominant remembered mood;
- Gentle/Steady/Fearful/Reckless run counts;
- the most recent persisted Rest summary, including score, distance, route tier, mood, clean passes, mercy, hits, Seeds, Bloom conversions, and Rest quote.

The projection sanitizes invalid negative mood counters before dominant-mood selection and rejects nonfinite/negative distance values before presentation. It does not repair the underlying save while being read.

## Collection paths

The current Journal derives five bounded completion tracks:

1. **Forest Families** — discovered encounter families out of the complete 19-family catalogue.
2. **Living Bonds** — persisted relationship stages at `RelationshipStage.MILESTONE` out of the six relationship-capable families.
3. **Garden** — currently grown Garden catalogue entries out of `GardenEconomy.catalogueSize`.
4. **Wardrobe** — available styles out of `CostumeStyle.entries`, including Classic.
5. **Peace in Every Biome** — biomes whose friendship history is surfaced by persistent memory out of all ordinary `Biome` entries.

Totals are derived at runtime rather than duplicated as Journal constants. If an authoritative catalogue grows, the corresponding Journal total grows with it.

### Whole-forest capstone

`ForestCompletionCapstoneComposer` combines those five collection tracks with one sixth pillar: whether **Kind**, **Merciful**, and **Peaceful** paths have each returned home at least once.

Before all six pillars are complete, the Journal presents:

**A Forest Still Becoming**

Once every pillar is complete, it presents:

**The Forest Knows Your Name**

The capstone is recomputed from existing authorities every time the Journal is rendered. There is no `capstoneUnlocked` preference or Journal-owned achievement flag, so catalogue expansion or save recovery cannot leave a stale completion bit behind.

## Garden sanctuary history

`GardenEconomy` exposes the stable progression order, full and compact player-facing plant names, and Seed costs while Garden rendering still owns sprites/layout. `ForestGardenHistoryComposer` combines that catalogue with persisted Garden progress and lifetime Seed balance.

Each Journal entry is derived as one of:

- **Grown** — already part of the persistent sanctuary;
- **Next** — the only catalogue entry currently eligible for purchase;
- **Locked** — later in the ordered sanctuary path.

The Journal can truthfully show whether the one legal next plant is affordable, but it cannot purchase it. `GardenPurchaseManager` remains the only purchase owner.

## Path History

`ForestPathHistoryComposer` turns the already-persisted route-tier counters into three authored path memories:

- **Kind Path**;
- **Merciful Path**;
- **Peaceful Path**.

Each shows how many completed runs of that tier have returned home. **Every Gentle Shape** is a derived recognition when all three route tiers have occurred at least once; it is not another saved achievement flag.

## Legacy milestones

Legacy milestones are **derived recognitions**, not saved achievements. Their state is recomputed from monotonic game history whenever the Journal opens.

Current recognitions include:

- **First Footprint** — at least one family discovered;
- **Every Path Has a Name** — every encounter family discovered;
- **Known by the Wild** — at least one persisted relationship stage has reached Bond;
- **Every Quiet Promise** — every persistent relationship has reached Bond;
- **Garden in Full** — the entire current Garden catalogue is grown;
- **Dressed by Memory** — every current wardrobe style is available;
- **Peace in Every Biome** — every biome carries persistent friendship history;
- **Peace Carried Home** — at least one complete Peaceful route has been persisted.

Because there is no Journal-specific completion store, save recovery and migration continue to be owned by the systems that already own the underlying facts.

## Living Bonds

For every discovered relationship-tracked creature, the Journal can show:

- already-persisted relationship stage;
- current observational warm/strained/learning tone;
- a plain-language interpretation of that tone;
- Bond milestone title and summary when already unlocked;
- the Bond ritual when already unlocked;
- the wearable memory associated with that Bond when one exists.

The Journal does not refresh relationship thresholds or milestone persistence. Missing historical stage materialization remains a gameplay/save-repair concern, not a side effect of opening the book.

## Wardrobe memories

The Journal lists the complete current `CostumeStyle` catalogue and distinguishes:

- locked;
- available;
- currently equipped.

The unlock hint comes from the costume's authored `unlockLabel`. Reading the Journal calls `availableCostumes`/`activeCostume`, not `CostumeManager.refreshUnlocks`; unlock decisions remain with normal progression flow.

## Memory pages

`StoryFragmentSystem` persists memory-page IDs when Rest, Garden, creature, weather, route, biome, Bloom, relationship, or return contexts become durable story memories.

The Journal calls only the read-side `unlockedMemoryPages` accessor. It does not call `restQuote`, `gardenReflection`, `creatureThought`, or `weatherThought`, because those gameplay/presentation paths can unlock additional pages.

`ForestMemoryPagePresenter` converts unlocked internal keys into player-facing titles and categories without exposing raw persistence identifiers. `ForestMemoryPageNarrative` then supplies pattern-specific prose for the actual durable history represented by known page families—for example clean Rest patterns, strained/warm relationships, biome or mood returns, Garden peace, repeated encounters, route memories, and Bloom traces. Future or unknown page IDs retain a deterministic safe fallback.

Current categories include:

- Creature Memory;
- Forest Weather;
- Garden Memory;
- Rest Memory;
- Return Memory;
- Path Memory;
- Bloom Memory;
- general Forest Memory.

Neither presenter decides whether a page is unlocked.

## Encounter entries

All nineteen encounter families retain their own Journal identity:

- distinct display identity;
- temperament;
- authored field note;
- discovery state;
- lifetime meetings;
- clean passes;
- mercy/spare count;
- hit count;
- persisted relationship stage where applicable;
- preferred biomes;
- known authored variant count.

Undiscovered families remain named but do not reveal their authored lore/history until persistent encounter history says they have been met.

## Accessibility and layout

The willow menu Journal entry is a stable virtual accessibility node and shares the exact same geometry as the visible/touch Journal chip.

Inside `ForestJournalActivity`, content uses standard Android `ScrollView`, `LinearLayout`, `TextView`, and `Button` controls. Section filters have explicit selected/action descriptions. Future portrait art, stamps, portraits, or notebook decoration must not replace readable semantic text with bitmap-only information.

## Expansion rule

New long-horizon progression should first ask: **does an existing gameplay owner already know this fact?**

If yes, extend the Journal projection rather than creating another persistence namespace.

A new persistent Journal store is justified only for information that is genuinely Journal-owned and cannot be reconstructed from authoritative gameplay history. Decorative filters, sort order, or local view preferences should remain ephemeral unless there is a concrete user need to persist them.
