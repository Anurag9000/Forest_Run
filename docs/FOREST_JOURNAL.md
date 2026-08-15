# Forest Journal — persistent memory and collection contract

The Forest Journal is the player-facing view of Forest Run's existing long-horizon memory. It is deliberately **not** a second progression system.

## Purpose

Forest Run already remembers encounters, clean passes, mercy, harm, relationship stages, route history, Garden growth, wardrobe unlocks, biome friendship, return moments, and story pages. The Journal makes that persistence legible to the player so the forest's memory is visible rather than only influencing hidden presentation.

The Journal is reachable from the willow menu through the **MEMORY → FOREST JOURNAL** action and is rendered with native Android views so long-form content remains scrollable and accessible independently from gameplay frame timing.

## Source-of-truth rule

Opening or reading the Journal must not:

- award Seeds;
- purchase Garden plants;
- refresh or fabricate costume unlocks;
- increase encounter/pass/spare/hit counters;
- mutate relationship affinity or tone;
- mark a milestone as completed in a new achievement store;
- create story pages;
- change route counters;
- alter the active costume.

Every displayed value is derived from an existing runtime owner.

| Journal surface | Runtime authority |
| --- | --- |
| 19-family discovery and encounter history | `EncounterFamilyCatalogue` + `PersistentMemoryManager` |
| relationship stage/tone/Bond reward | `RelationshipArcSystem` |
| Garden completion | `SaveManager` + `GardenEconomy` |
| wardrobe completion/equipped style | `SaveManager` + `CostumeManager` + `CostumeStyle` |
| peaceful-biome history | `PersistentMemoryManager` + `Biome` |
| Kind/Merciful/Peaceful route history | `SaveManager` + `PacifistRouteTier` |
| memory pages | `StoryFragmentSystem` + `SaveManager` |
| history marks | `PersistentMemoryManager` |

## Collection paths

The current Journal derives five bounded completion tracks:

1. **Forest Families** — discovered encounter families out of the complete 19-family catalogue.
2. **Living Bonds** — persistent creature relationships that have reached `RelationshipStage.MILESTONE` out of the relationship-tracked family set.
3. **Garden** — currently grown Garden catalogue entries out of `GardenEconomy.catalogueSize`.
4. **Wardrobe** — available styles out of `CostumeStyle.entries`, including Classic.
5. **Peace in Every Biome** — biomes whose friendship history is surfaced by persistent memory out of all ordinary `Biome` entries.

Totals are derived at runtime rather than duplicated as Journal constants. If the authoritative catalogue grows, the Journal total grows with it.

## Legacy milestones

Legacy milestones are **derived recognitions**, not saved achievements. Their state is recomputed from monotonic game history whenever the Journal opens.

Current recognitions include:

- **First Footprint** — at least one family discovered;
- **Every Path Has a Name** — every encounter family discovered;
- **Known by the Wild** — at least one persistent relationship reaches Bond;
- **Every Quiet Promise** — every persistent relationship reaches Bond;
- **Garden in Full** — the entire current Garden catalogue is grown;
- **Dressed by Memory** — every current wardrobe style is available;
- **Peace in Every Biome** — every biome carries persistent friendship history;
- **Peace Carried Home** — at least one complete Peaceful route has been persisted.

Because there is no Journal-specific completion store, save recovery and migration continue to be owned by the systems that already own the underlying facts.

## Living Bonds

For every discovered relationship-tracked creature, the Journal can show:

- current relationship stage;
- current warm/strained/learning tone;
- a plain-language interpretation of that tone;
- Bond milestone title and summary when unlocked;
- the Bond ritual when unlocked;
- the wearable memory associated with that Bond when one exists.

The Journal does not duplicate relationship thresholds. It asks `RelationshipArcSystem` for the live stage, tone, and milestone reward.

## Wardrobe memories

The Journal lists the complete current `CostumeStyle` catalogue and distinguishes:

- locked;
- available;
- currently equipped.

The unlock hint comes from the costume's authored `unlockLabel`. Reading the Journal does not call `CostumeManager.refreshUnlocks`; unlock decisions remain with the normal progression flow.

## Memory pages

`StoryFragmentSystem` persists memory-page IDs when Rest, Garden, creature, weather, route, biome, Bloom, relationship, or return contexts become durable story memories.

`ForestMemoryPagePresenter` converts those internal keys into player-facing titles and categories without exposing raw persistence identifiers. Known families receive specific presentation rules, while future/unknown IDs have a deterministic human-readable fallback.

Current categories include:

- Creature Memory;
- Forest Weather;
- Garden Memory;
- Rest Memory;
- Return Memory;
- Path Memory;
- Bloom Memory;
- general Forest Memory.

The presenter is intentionally a view projection. It does not decide whether a page is unlocked.

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
- relationship stage where applicable;
- preferred biomes;
- known authored variant count.

Undiscovered families remain named but do not reveal their authored lore/history until the persistent encounter history says they have been met.

## Accessibility and layout

The willow menu Journal entry is a stable virtual accessibility node and shares the exact same geometry as the visible/touch Journal chip.

Inside `ForestJournalActivity`, content uses standard Android `ScrollView`, `LinearLayout`, `TextView`, and `Button` controls. Future portrait art, stamps, portraits, or notebook decoration must not replace readable semantic text with bitmap-only information.

## Expansion rule

New long-horizon progression should first ask: **does an existing gameplay owner already know this fact?**

If yes, extend the Journal projection rather than creating another persistence namespace.

A new persistent Journal store is justified only for information that is genuinely Journal-owned and cannot be reconstructed from authoritative gameplay history. Decorative filters, sort order, or local view preferences should remain ephemeral unless there is a concrete user need to persist them.
