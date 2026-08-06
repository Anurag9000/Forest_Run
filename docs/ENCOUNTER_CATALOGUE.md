# Encounter family catalogue

`EncounterFamilyCatalogue` is the structural inventory for all nineteen authored encounter families. It exists so validation, debug tooling, authoring tools, and future content reports can enumerate the complete game without duplicating mutable gameplay constants.

## Covered families

- Flora: Cactus, Lily of the Valley, Hyacinth, Eucalyptus, Vanilla Orchid.
- Trees: Weeping Willow, Jacaranda, Bamboo, Cherry Blossom.
- Birds: Duck, Tit Group, Chickadee Group, Owl, Eagle.
- Animals: Cat, Wolf, Fox, Hedgehog, Dog.

Each descriptor records:

- canonical `EntityType`;
- family group;
- concrete implementation class;
- implementation source path.

The catalogue validates at initialization that every `EntityType` appears exactly once and that every source path is unique. Kotlin tests enforce family counts and lookup identity. A Python source contract also verifies that each descriptor agrees with `EntityFactory` and that the referenced implementation file exists.

## Deliberately not duplicated

The catalogue does not copy:

- movement speed or lane positions;
- hitboxes or collision geometry;
- mercy radius, cooldown, score, or kindness values;
- cue timing or telegraph animation values;
- sprite fields or animation frames;
- authored dialogue or flavor strings;
- persistence counters or route thresholds.

Those values remain with their existing owners:

| Dimension | Authoritative owner |
|---|---|
| Movement and lane behavior | Concrete entity implementation |
| Collision and mercy behavior | Concrete entity implementation and collision pipeline |
| Fairness cues | Concrete entity implementation |
| Assets and animation sheets | `SpriteManager` plus entity implementation |
| Run flavor copy | `RunFlavorPresentation` and related presentation systems |
| Relationship memory | `PersistentMemoryManager` and relationship systems |
| Pacifist route contribution | `GameStateManager` and route systems |

## Adding a new encounter

A new family is incomplete until all of these agree:

1. `EntityType` contains the new type.
2. A concrete implementation exists in the appropriate source package.
3. `EntityFactory` has an exhaustive branch.
4. `EncounterFamilyCatalogue` has exactly one matching descriptor.
5. Sprite, animation, collision, mercy, fairness, flavor, relationship, and route behavior are authored in their existing owners.
6. Deterministic debug scenarios and tests cover the new behavior.
7. Physical-device fairness, readability, performance, audio, haptic, and accessibility acceptance is updated.

The catalogue is an inventory and ownership boundary, not a replacement for per-entity design or acceptance evidence.
