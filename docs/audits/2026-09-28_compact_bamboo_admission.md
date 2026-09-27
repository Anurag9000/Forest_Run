# Forest Run — Compact-surface Bamboo admission (2026-09-28)

## Source-derived crash

Bamboo's canonical full five-stalk lane uses `BambooGapPlacement.GAP_HEIGHT_PX = Player.BASE_HEIGHT * 3` (300px). On short native landscape surfaces (e.g. 640×360, ground 295.2px; also 540px-high), the complete-traversal range does not exist. `EntityManager.spawn` unconditionally used `EntityFactory.create(BAMBOO)`; the constructor's deliberate geometry `require` therefore threw and could abort ordinary or scripted gameplay. Returning an arbitrary smaller slit would make the encounter physically impossible; substituting Cactus would lie about the authored species.

## Correction and scope

Factor the existing exact range arithmetic into one nullable pure function, used by the throwing constructor helper and new Boolean `canStageAtGround`; retain fail-closed direct constructor semantics and normal-height authored gap values. `EntityFactory.canStageBamboo` evaluates actual/fallback height with the same ground ratio. Production `EntityManager.spawn` skips only Bamboo when complete geometry is not viable; it does not inject a different type or claim to have spawned Bamboo. The ordinary pool is filtered before the existing selection bag, so a compact device does not waste a random spawn on an impossible Bamboo and the other biome-authorized families retain their relative order. Existing normal-height Bamboo retains its authored behavior.

Robolectric tests cover short and normal heights, preservation of the remaining ordinary pool, and prove the manager can continue to spawn Cactus after skipping impossible Bamboo. The deterministic Bamboo showcase cannot be certified on a screen too short for its authored geometry; the per-device scenario acceptance matrix must record that explicit limitation. This source guard is not a visual adaptation, device-coverage reduction, or a replacement for real landscape-tablet/phone acceptance.
