# Forest Run product-completion continuation — 2026-08-15

This record continues `2026-08-15_product_completion_sweep.md` after the first exact-head connected-device validation exposed one stale instrumentation contract. It records direct-to-`main` source work only. It is not physical-device, human, store, legal, signing, rights, or final-release acceptance evidence.

## 1. Connected accessibility failure: root cause and repair

The host/release job for `1ee0d496...` was green, but API-35 connected validation failed deterministically in `AccessibilityVirtualNodeInstrumentedTest.installedGameViewPublishesAndRoutesVirtualAccessibilityNodes`.

The installed `GameView` accessibility root exposed four Menu children while the old test still required three. Production source was correct: the stable Menu semantic order is:

1. Begin forest run;
2. Open Garden;
3. Open feedback settings;
4. Open Forest Journal.

The repair did not weaken the assertion to a new count only. The connected contract now requires the Journal node to exist, describe itself as `Open Forest Journal`, be clickable on Menu, disappear while Settings owns the semantic tree, and reappear after Settings closes.

The lower-level `GameAccessibilitySemanticsTest` already protects the same stable ID/focus ordering, so the device test now verifies the installed provider rather than contradicting it.

## 2. Journal lifecycle state is now correctly ephemeral

Journal filters remain:

- All;
- Progress;
- Bonds;
- Memories;
- Families.

The selected filter is UI state, not progression. `ForestJournalActivity` now saves only the selected enum name into `savedInstanceState` and restores it on recreation. Missing or unknown restored values fail safely back to `All`.

No SharedPreferences/save-game key was added.

`ForestJournalLifecycleInstrumentedTest` selects `Memories`, recreates the real Activity, requires `Memories Journal section, selected` after recreation, and requires the game-progress preference map to remain unchanged.

`scripts/test_forest_journal_lifecycle_contract.py` additionally rejects persistence APIs from the Activity and locks the Bundle-only ownership rule.

## 3. Garden purchase feedback policy is implemented above persistence

`GardenPurchaseInteractionCoordinator` now defines the presentation policy for Garden growth feedback:

- invoke the authoritative purchase action exactly once;
- return its exact `GardenPurchaseResult` unchanged;
- emit exactly one growth-feedback cue only for `PURCHASED`;
- emit no growth cue for `INVALID_REQUEST`;
- emit no growth cue for `NOT_NEXT_UNLOCK`;
- emit no growth cue for `CATALOGUE_COMPLETE`;
- emit no growth cue for `INSUFFICIENT_SEEDS`;
- emit no growth cue for `WRITE_FAILED`.

`GardenPurchaseInteractionCoordinatorTest` covers every status.

`scripts/test_garden_purchase_feedback_contract.py` protects the architectural boundary: neither `GardenPurchaseManager` nor `ApplicationPersistenceFacade` may import `HapticManager`, call `gardenGrowth()`, or absorb the presentation coordinator merely to avoid proper UI integration.

This means persistence remains truthful: a committed purchase determines success, while presentation decides how success feels.

## 4. Deliberately not shortcut: live Garden wiring

The remaining Garden feedback integration is narrow but spans two unusually large live owners:

- `GardenScreen.kt` owns touch purchase, Garden animation/particles, local presentation metadata, and wardrobe interaction;
- `GameView.kt` owns the virtual-accessibility purchase action and semantic-tree invalidation.

There is no smaller existing callback seam that can give touch and accessibility one shared interaction owner without editing both files.

The continuation therefore did **not** move haptic behavior into persistence, did **not** create a global event bus, and did **not** make `GardenPurchaseResult.purchased` perform hidden side effects. Those approaches would reduce diff size at the cost of architecture and testability.

A future coordinated large-owner edit should:

1. inject/use one `GardenPurchaseInteractionCoordinator` for both purchase paths;
2. let successful touch purchase retain its existing animation/particle behavior;
3. let successful accessibility purchase refresh the same Garden state and semantic tree;
4. ensure `HapticManager.gardenGrowth()` is emitted once per committed purchase regardless of input modality;
5. ensure every rejected/failed result emits no growth cue;
6. add an installed connected test for that parity.

Until then, `gardenGrowth()` is vocabulary plus tested policy, not a claim that the live Garden already emits the cue.

## 5. Garden catalogue presentation duplication is closed

`GardenEconomy` is now the sole source of Garden progression order, full names, compact card names, and Seed costs.

`GardenScreen` keeps only local visual metadata that genuinely belongs to the Canvas presentation: card colour, fallback emoji, and sprite ordering. It no longer stores a second copy of compact names or costs.

The live card loop resolves the canonical entry with `GardenEconomy.plantForIndex(i)` and renders `economy.compactName` and `economy.seedCost`. The screen also fails fast if either its visual catalogue size or sprite catalogue size diverges from `GardenEconomy.catalogueSize`.

`scripts/test_garden_catalogue_contract.py` now protects the completed ownership model by requiring:

- exactly nine contiguous canonical economy entries with unique names;
- canonical README and Game Design tables;
- `GardenScreen` import/use of `GardenEconomy`;
- visual/sprite catalogue-size alignment with the canonical catalogue;
- card resolution through `GardenEconomy.plantForIndex(i)`;
- rendering through `economy.compactName` and `economy.seedCost`;
- absence of local `GardenPlant.name` / `GardenPlant.seedCost` fields;
- continued purchase routing through the application persistence facade rather than direct Garden/Seed writes.

This closes the previous presentation-maintainability debt without changing purchase indices, visual order, animation, touch geometry, sprites, colours, emoji, wardrobe behavior, or persistence semantics.

## 6. Current source-addressable remainder

After this continuation, the known source-only remainder is deliberately small:

1. live-wire `GardenPurchaseInteractionCoordinator` through both touch and virtual-accessibility Garden purchase paths;
2. opportunistically retire duration-shaped haptic compatibility adapter names when the corresponding large live adapter is next edited.

No missing gameplay state machine, Bloom system, collision arbiter, persistence layer, relationship engine, Garden economy, visitor system, Journal persistence store, accessibility semantic architecture, ghost system, runtime ML model, cloud/account system, advertising system, or multiplayer layer is justified by the current product goals.

## 7. Still external / candidate-bound

The following remain intentionally unresolved by source work:

- representative physical hardware evidence;
- performance/thermal/battery acceptance on real devices;
- human gameplay/fairness/accessibility acceptance;
- final art/animation/audio/haptic creative approval;
- rights/provenance/licence/attribution decisions;
- source-code licence decision;
- private vulnerability-reporting setting/channel verification;
- stable HTTPS privacy-policy publication;
- Play Console ownership and declarations;
- production signing/upload identity;
- final screenshots/store graphics tied to a frozen candidate;
- exact release notes/version/tag and accountable release approval.

These must remain open until real external evidence or owner decisions exist.

## 8. Validation rule

Because each direct-to-`main` commit starts a new candidate SHA, only the final exact-head workflow may be used as completion evidence. Superseded or cancelled runs are useful diagnostics but are not a substitute for a green host/release job and green API-35 connected job on the same final SHA.
