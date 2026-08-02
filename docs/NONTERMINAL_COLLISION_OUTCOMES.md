# Forest Run — Nonterminal Collision Outcome Seam

## Purpose

`STUMBLE` and `MERCY_MISS` are nonterminal collision results, but each still coordinates several independent concerns:

- live run-state mutation;
- relationship history;
- ghost visibility;
- Player state;
- screen flash color and duration;
- audio, haptics, particles, and camera response;
- authored dialogue and flavor text;
- entity deactivation.

Those responsibilities previously lived directly in the `GameView` collision switch. `NonTerminalCollisionOutcomeCoordinator` now owns their exact ordering while leaving private live-state mutations behind a small `GameView` effect adapter.

## GameView boundary

The `STUMBLE` branch now only:

1. resolves the killer type;
2. captures the current biome foliage color, with the existing pink fallback;
3. constructs one `StumbleCollisionOutcome`;
4. calls `completeStumble(...)` once;
5. supplies a final callback that deactivates the selected entity.

The `MERCY_MISS` branch now only:

1. resolves the entity type;
2. captures route tier, mercy hearts, kindness chain, and Player position;
3. constructs one `MercyMissCollisionOutcome`;
4. calls `completeMercyMiss(...)` once.

Neither branch directly records relationship history, invokes audio/haptics/camera/particles, mutates the flash timer, selects authored copy, or spawns presentation queues.

## Stumble ordering contract

`completeStumble(...)` preserves the pre-extraction sequence:

```text
record run hit
→ record persistent known-killer relationship hit
→ suppress ghost for 0.9 seconds
→ trigger Player stumble
→ show biome-dominant flash
→ play nonlethal hit SFX
→ apply hit camera shake
→ emit medium haptic
→ present canonical STUMBLE bubble and flavor line
→ deactivate selected entity
```

Relationship history remains gated by both conditions:

```text
persistEncounter == true
killerType != null
```

Deterministic or persistence-disabled encounters therefore retain local mechanics and authored feedback without modifying permanent relationship history.

The entity deactivation is supplied as a callback rather than embedding an Android-backed `Entity` object in the pure coordinator. This keeps unit tests independent of entity construction while retaining deactivation as the final ordered action.

## Mercy-miss ordering contract

`completeMercyMiss(...)` preserves:

```text
show green mercy flash
→ play mercy-miss SFX
→ emit double-tap haptic
→ present canonical mercy bubble and flavor line
→ emit mercy stars at Player center
→ apply mercy camera shake
```

The coordinator computes the star position from the captured Player origin and the canonical `Player.BASE_WIDTH` and `Player.BASE_HEIGHT` constants.

## Production adapters

### Authored presentation

`AndroidNonTerminalCollisionFeedbackPresenter` owns text selection and presentation geometry.

For STUMBLE it delegates to:

```kotlin
RunFlavorPresentation.collisionCue(
    result = CollisionResult.STUMBLE,
    ...
)
```

It preserves the original geometry and timing:

- bubble anchor: Player center X, Player Y minus 24;
- flavor anchor: Player X plus 20 percent of width, Player Y minus 10;
- flavor lifetime: 1.0 second;
- cue-provided colors and size.

For MERCY_MISS it delegates to `RunFlavorPresentation.mercyCue(...)` and preserves:

- bubble anchor: Player center X, Player Y minus 24;
- flavor anchor: Player X plus 22 percent of width, Player Y minus 12;
- flavor lifetime: 1.15 seconds;
- cue-provided colors and size.

`DialogueBubbleManager` and `FlavorTextManager` already reject nonfinite anchors, so malformed positions cannot poison either presentation queue.

### Relationship history

`AndroidNonTerminalCollisionRelationshipRecorder` is the only extracted production adapter for STUMBLE relationship-hit recording.

### Live runtime effects

`GameViewNonTerminalCollisionEffects` is a private inner adapter because the affected mutable state remains private to `GameView`. It owns:

- `GameStateManager.recordHit()`;
- ghost suppression;
- Player stumble transition;
- flash timer/color mutation;
- SFX;
- camera effects;
- haptics;
- mercy-star particles.

This centralizes the side-effect surface and removes it from result branches, but it does not yet make those runtime effects independent of `GameView`.

## Tests

### Pure coordinator tests

`NonTerminalCollisionOutcomeCoordinatorTest` covers:

- persistent STUMBLE exact ordering;
- nonpersistent STUMBLE relationship isolation;
- unknown-killer behavior;
- MERCY_MISS exact ordering;
- Player-center star coordinates;
- presentation input pass-through.

### Android presenter tests

`NonTerminalCollisionFeedbackPresenterIntegrationTest` covers under Robolectric:

- exact canonical STUMBLE cue parity;
- exact canonical MERCY_MISS cue parity;
- one bubble and one flavor line per outcome;
- preserved flavor lifetimes;
- fail-closed nonfinite anchors.

### Source ownership contract

`scripts/test_nonterminal_collision_outcome_contract.py` enforces:

- one delegation call per `GameView` result branch;
- no direct effect or presentation ownership in those branches;
- complete captured input identity;
- exact STUMBLE and MERCY_MISS coordinator ordering;
- persistent known-killer relationship gating;
- authored presenter ownership;
- live runtime calls contained in the private effect adapter.

## Evidence boundary

Executed in this session:

- focused Kotlin compilation of the production seam against Android/game stubs;
- an executable fake-seam harness covering both canonical sequences;
- focused compilation of the checked-in unit-test surface with fake JUnit declarations;
- Python source-contract syntax validation;
- representative source-contract execution;
- exact inspection of the large `GameView` integration patch;
- a separate preservation-only commit restoring four unintentionally changed rendering comments, verified as exactly four additions and four deletions with no executable change.

The checked-in JUnit and Robolectric tests were not executed through an exact-head Android Gradle environment in this session. This work does not establish lint, release-build, packaging, connected-emulator, or physical-device acceptance.

## Remaining architecture work

- extract or type the complete collision-result dispatcher rather than retaining the `when` in `GameView`;
- move the private live-effect adapter behind narrower runtime owners where practical;
- extract the immediate terminal HIT impact path, which still coordinates Player, ghost, camera, audio, music, and haptics inline;
- retain deterministic severity and selected-entity semantics during any further decomposition;
- complete exact-head Android and physical-device validation.
