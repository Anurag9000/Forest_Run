# Forest Run — Garden semantic actions share touch post-commit presentation (2026-10-02)

## Reproduced modality split

The Garden's touch purchase path used `GardenPurchaseInteractionCoordinator`, adopted the canonical purchase result into local card/Seed state, started the unlock animation and emitted the Seed-growth burst. The semantic accessibility path committed the same purchase through `ApplicationPersistenceFacade`, and `LiveGameAccessibilityActions` correctly emitted the same success-only growth haptic, but `GameView` then called only `gardenScreen.load()`. That reload reflected durable numbers while skipping the authored growth animation/burst. Costume equip similarly reloaded durable state but skipped the wardrobe confirmation line/timer used by touch.

This was not a persistence error: Garden economy/spending and haptic ownership were already canonical. It was a post-commit presentation divergence by input modality.

## Repair

`GardenScreen.adoptPurchaseResult` now owns only local/presentation consequences of an already-completed canonical purchase: canonical result values, successful-card animation and Seed burst. It never spends currency or emits the haptic. Touch calls it after the existing `purchaseInteraction.purchase`; semantic accessibility calls it after `ApplicationPersistenceFacade.purchaseNextGardenPlant`, while the existing LiveGameAccessibilityActions feedback policy remains the single semantic haptic owner.

`GardenScreen.adoptCommittedCostume` similarly owns the common equipped-card/message/timer projection after a successful CostumeManager persistence operation. Touch and accessibility both use it.

## Regression

Robolectric GardenScreen tests invoke the post-commit purchase adopter after a real canonical purchase and require unlock animation plus deferred Seed burst without changing the already-committed balance. A costume regression requires the same authored confirmation state after a precommitted accessible style.

This improves modality parity but does not substitute for human TalkBack/Switch Access evaluation or physical Garden visual/haptic approval.
