# Encounter family catalogue

`EncounterFamilyCatalogue` is the single structural and derived audit inventory for all nineteen authored encounter families. It exists so validation, debug tooling, authoring tools, and future content reports can enumerate the complete game without duplicating mutable gameplay constants.

## Authority model

The catalogue keeps explicit only the structural implementation identity that otherwise has no queryable owner: canonical `EntityType`, family group, concrete implementation class, and implementation source path. It then derives higher-level coverage from the runtime systems that already own it:

- `Biome.preferredPool` → ordinary biome reachability;
- `EncounterScenario` → deterministic scenario/fairness/readability coverage and authored variants;
- `RelationshipArcSystem.isTracked` → relationship capability;
- `EntityFactory` + `SpriteManager` remain the concrete creation/asset authorities and are checked independently by source contracts.

Mutable movement, lane, collision, mercy, cue, animation, flavor, relationship-threshold, and route values are **not** copied into the catalogue.

## Complete roster and derived coverage

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

`DOG` is currently the only encounter with multiple authored deterministic variants: `DEFAULT`, `DOG_HAZARD`, and `DOG_BUDDY`. The other eighteen derive only `DEFAULT` from the current scenario set.

## Authoring ownership

| Dimension | Authoritative owner |
|---|---|
| Structural implementation identity | `EncounterFamilyCatalogue` |
| Ordinary biome eligibility | `Biome.preferredPool` |
| Deterministic coverage and variants | `EncounterScenario` |
| Movement and lane behavior | Concrete entity implementation |
| Collision and mercy behavior | Concrete entity implementation and collision pipeline |
| Fairness cues/telegraphs | Concrete entity implementation; deterministic focused scenarios exercise them |
| Assets and animation sheets | `EntityFactory` + `SpriteManager` + entity implementation |
| Run flavor copy | `RunFlavorPresentation` and related presentation systems |
| Relationship memory/tuning | `RelationshipArcSystem` and persistence systems |
| Pacifist route contribution | `GameStateManager` and route systems |

## Automated drift protection

`EncounterFamilyCatalogueTest` verifies:

- exactly 19 profiles in `EntityType` ordinal order;
- family counts of 5 flora, 4 trees, 5 birds, and 5 animals;
- every type remains in at least one ordinary biome pool;
- every type has deterministic scenario coverage;
- every type retains at least one focused single-type fairness/readability scenario;
- all five biomes and every deterministic scenario remain represented;
- relationship capability exactly follows `RelationshipArcSystem.isTracked`;
- Dog's authored variants remain discoverable without copying factory rules.

`scripts/test_encounter_family_catalogue_contract.py` verifies every descriptor against the real implementation source and exhaustive `EntityFactory` branch, including the expected sprite authority for all 19 types. A new `EntityType` therefore cannot quietly ship without structural implementation, factory/assets, ordinary biome reachability, and deterministic focused coverage.

## Adding a new encounter

A new family is incomplete until all of these agree:

1. `EntityType` contains the new type.
2. A concrete implementation exists in the appropriate source package.
3. `EntityFactory` has the exhaustive concrete/asset branch.
4. `EncounterFamilyCatalogue` has exactly one matching structural descriptor.
5. At least one ordinary `Biome.preferredPool` contains the type.
6. At least one deterministic focused scenario demonstrates its fairness/readability contract; showcase/mixed scenarios may add broader coverage.
7. Relationship tracking is added only if intentionally authored in `RelationshipArcSystem`.
8. Physical-device fairness, readability, performance, audio, haptic, and accessibility acceptance is updated.

The catalogue proves source coverage and ownership consistency. It does not turn deterministic source coverage into human fairness or release-acceptance evidence.
