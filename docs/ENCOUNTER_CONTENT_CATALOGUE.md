# Encounter content catalogue

Forest Run has exactly 19 authored encounter types. `EncounterContentCatalogue` is the queryable audit/debug view over that roster. It is intentionally **derived** from existing runtime authorities rather than becoming another place where spawn, asset, relationship, or collision rules can drift.

## Authority model

- `EntityType` owns the 19-type roster and stable enum order.
- `EntityFactory` owns concrete class creation and sprite selection.
- `Biome.preferredPool` owns ordinary biome reachability.
- `EncounterScenario` owns deterministic debug/fairness/readability coverage and authored variants.
- concrete entity classes own movement, collision geometry, telegraphs, projectiles, stumble/lethal/mercy behavior, and Bloom conversion behavior.
- `RelationshipArcSystem` owns the six relationship-tracked types and their thresholds/tuning.
- `EncounterContentCatalogue` derives one immutable profile per type and fails closed if a type loses ordinary biome reachability, deterministic scenario coverage, or a focused single-type fairness/readability scenario.

It does not duplicate spawn probabilities, sprite paths, collision results, or relationship thresholds.

## Complete roster

| Type | Family | Ordinary preferred biomes | Focused deterministic read | Relationship arc | Factory sprite authority |
| --- | --- | --- | --- | --- | --- |
| `CACTUS` | Flora | Meadow, Dusk Canyon | `CACTUS_READ` | No | `cactusSprite` |
| `LILY_OF_VALLEY` | Flora | Meadow, Night Forest | `LILY_GLOW` | No | `lilySprite` |
| `HYACINTH` | Flora | Meadow, Orchard | `HYACINTH_BRUSH` | No | `hyacinthSprite` |
| `EUCALYPTUS` | Flora | Ancient Grove, Dusk Canyon | `EUCALYPTUS_WHIP` | No | `eucalyptusSprite` |
| `VANILLA_ORCHID` | Flora | Orchard, Ancient Grove, Night Forest | `ORCHID_WINDOW` | No | `orchidSprite` |
| `WEEPING_WILLOW` | Tree | Ancient Grove, Night Forest | `WILLOW_CURTAIN` | No | `willowSprite` |
| `JACARANDA` | Tree | Orchard, Dusk Canyon | `JACARANDA_PETALS` | No | `jacarandaSprite` |
| `BAMBOO` | Tree | Ancient Grove, Dusk Canyon, Night Forest | `BAMBOO_GAP` | No | `bambooSprite` |
| `CHERRY_BLOSSOM` | Tree | Meadow, Orchard | `CHERRY_GUST` | No | `cherryBlossomSprite` |
| `DUCK` | Bird | Meadow | `DUCK_TEACH` | No | `duckFlying` |
| `TIT` | Bird | Meadow, Orchard | `TIT_WAVE` | No | `titFlying` |
| `CHICKADEE` | Bird | Orchard, Night Forest | `CHICKADEE_SWERVE` | No | `chickadeeFlying` |
| `OWL` | Bird | Ancient Grove, Night Forest | `OWL_DIVE` | Yes | `owlSprite` + `owlFlying` |
| `EAGLE` | Bird | Ancient Grove, Dusk Canyon | `EAGLE_MARK` | Yes | `eagleFlying` |
| `CAT` | Animal | Meadow, Orchard, Night Forest | `CAT_KINDNESS` | Yes | `catSprite` |
| `WOLF` | Animal | Ancient Grove, Dusk Canyon, Night Forest | `WOLF_CHARGE` | Yes | `wolfSprite` |
| `FOX` | Animal | Orchard, Ancient Grove, Dusk Canyon | `FOX_MIRROR` | Yes | `foxSprite` |
| `HEDGEHOG` | Animal | Meadow, Ancient Grove | `HEDGEHOG_DEBUFF` | No | `hedgehogSprite` |
| `DOG` | Animal | Orchard, Dusk Canyon | `DOG_HAZARD`, `DOG_BUDDY` | Yes | `dogSprite` |

`DOG` is currently the only type with multiple authored deterministic variants: `DEFAULT`, `DOG_HAZARD`, and `DOG_BUDDY`. Every other type currently resolves to the default variant in deterministic scenario coverage.

## Automated drift protection

`EncounterContentCatalogueTest` verifies:

- all 19 `EntityType` values are represented exactly once in ordinal order;
- the family cardinalities remain 5 flora, 4 trees, 5 birds, 5 animals;
- every type remains reachable from at least one ordinary biome;
- every type remains covered by at least one deterministic scenario;
- every type retains at least one focused single-type fairness/readability scenario;
- catalogue scenario coverage still spans every `EncounterScenario`;
- catalogue biome coverage still spans all five biomes;
- relationship capability exactly follows `RelationshipArcSystem.isTracked`;
- Dog remains the only multi-variant encounter unless authored content intentionally changes.

`scripts/test_encounter_content_catalogue_contract.py` independently checks the source roster and the exhaustive `EntityFactory` branches, including the expected sprite authority for every type. If a new encounter is added to `EntityType` without factory/assets/biome/scenario coverage, CI fails instead of silently shipping a partially wired family.

## How to extend the roster safely

A new `EntityType` is not complete merely because it compiles. Add the concrete entity/factory asset wiring, place it in at least one ordinary biome pool, add a deterministic focused scenario that demonstrates its fairness/readability contract, add relationship tracking only if intentionally authored, and update tests/evidence. The derived catalogue should then admit the type without adding a duplicate hand-maintained biome/scenario map.

Physical playtesting still decides whether a telegraph or avoidance window is actually fair. The catalogue proves authored/source coverage; it does not convert deterministic source coverage into human acceptance evidence.
