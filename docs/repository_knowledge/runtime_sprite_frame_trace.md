# Packaged Runtime Sprite Frame and Caller Trace

## Scope and evidence boundary

This report traces the 29 packaged PNGs under `app/src/main/assets/sprites/`, their `AssetPaths` constants, `SpriteManager` declarations/validation, `SpriteSheet` crop and frame-selection rules, and the production callers that choose or draw the sheets. Visual-layout observations are reused from `docs/repository_knowledge/asset_visual_inventory.md` (runtime-sprite rows at lines 45–73); dimensions and SHA-256 values below were read from the current PNG files. The baseline is `eeccc677107f69039dbf71053c18a9038d4b5ff6` plus the current dirty worktree, snapshot date 2026-10-27. Current scoped worktree differences: `GardenScreen.kt` is modified, and `asset_visual_inventory.md` is untracked; the 29 asset files and the other inspected Kotlin source files are clean against HEAD. The GardenScreen statements therefore describe current worktree source, not necessarily baseline HEAD.

No tests, build, script, application, or device workflow was run. This is static source inspection plus image metadata and previously recorded local visual inspection; it does not establish rendered runtime appearance, animation timing quality, or intended art provenance.

## Frame contract

`AssetPaths` provides literal asset paths, grouped by player, plants, trees, birds, and animals (`app/src/main/java/com/anurag9000/forestrun/engine/AssetPaths.kt:3-49`). `SpriteManager` loads every listed runtime sheet during initialization. Player constants declare 48 run, 48 jump-strip, 48 duck, 12 hit, and 12 death source frames (`SpriteManager.kt:15-24`); each plant and animal is passed to `loadEntity(..., 4)`, while trees use `loadTreeEntity(..., 4)` (`SpriteManager.kt:192-229`). `loadEntity` makes a looping 8-fps sheet; `loadTreeEntity` makes a looping 6-fps sheet (`SpriteManager.kt:378-403`). The loader validates that each bitmap is nonempty, within 16,384×4,096, and has a width exactly divisible by the declared count; resulting frame width must be at least 8 px (`SpriteManager.kt:278-307`). Debug builds can substitute generated placeholder strips on load/validation exceptions; non-debuggable builds throw (`SpriteManager.kt:232-276`).

`SpriteSheet` treats the bitmap as a horizontal strip. `frameWidth = bitmap.width / totalFramesInBitmap`, and `frameHeight = bitmap.height` (`app/src/main/java/com/anurag9000/forestrun/engine/SpriteSheet.kt:10-37`). `update` advances looping or terminal current-frame state (`:52-88`); `setFrame` clamps to the sheet's logical frame range (`:90-98`). `draw` calculates `absoluteFrame = startFrame + currentFrame` and draws the full-height horizontal crop `[absoluteFrame*frameWidth, (absoluteFrame+1)*frameWidth)` into the caller's destination rectangle (`:100-114`). It does not detect semantic sub-drawings, rows, labels, transparent padding, or subcells. `copy()` shares the bitmap but gives the copy independent animation state (`:116-117`). `SpriteSizing.widthForHeight` preserves this *declared crop* aspect ratio, subject to caller minimum width (`app/src/main/java/com/anurag9000/forestrun/engine/SpriteSizing.kt:3-14`).

Bird sheets receive extra sanitization: each loaded bird bitmap is copied to mutable ARGB pixels and each of its four equal-width frames has edge-connected near-seed opaque pixels cleared (`SpriteManager.kt:309-376`). This operates per declared quarter, so it does not use a drawing-aware mask. The exact visual effect on each asset is unverified at runtime.

## Per-file layout and declared crop

Dimensions come from PNG metadata. `logical crop` is width divided by the production-declared frame count; every row below has an exact integer division. Visual descriptions and the eight-wolf-drawings / numbered-plant-label observations are from the corresponding runtime asset rows in `asset_visual_inventory.md`.

| Runtime asset path | PNG size | Declared frames | Crop per frame | Observed layout / trace note |
|---|---:|---:|---:|---|
| `app/src/main/assets/sprites/char/runner_girl_technical_48frame.png` | 3840×106 | 48 | 80×106 | 48 horizontal character drawings. |
| `app/src/main/assets/sprites/char/runner_girl_jump_48frame.png` | 3840×106 | 48 | 80×106 | 48 horizontal drawings; same source is subdivided among jump-state sheets. |
| `app/src/main/assets/sprites/char/runner_girl_duck_48frame.png` | 3840×106 | 48 | 80×106 | 48 horizontal drawings. |
| `app/src/main/assets/sprites/char/runner_girl_hit_sequence.png` | 1920×213 | 12 | 160×213 | 12 horizontal drawings. |
| `app/src/main/assets/sprites/char/runner_girl_death_sequence.png` | 1920×209 | 12 | 160×209 | 12 horizontal drawings. |
| `app/src/main/assets/sprites/plants/cactus_4frames.png` | 620×172 | 4 | 155×172 | Four horizontal potted cactus drawings. |
| `app/src/main/assets/sprites/plants/lily_of_valley_4frames.png` | 740×214 | 4 | 185×214 | Four horizontal drawings, no visible pot/base. |
| `app/src/main/assets/sprites/plants/hyacinth_4frames.png` | 448×269 | 4 | 112×269 | Four horizontal potted drawings; visible printed numerals 1–4 are pixels within frames. |
| `app/src/main/assets/sprites/plants/eucalyptus_4frames.png` | 392×147 | 4 | 98×147 | Four horizontal potted plant drawings. |
| `app/src/main/assets/sprites/plants/vanilla_orchid_4frames.png` | 520×245 | 4 | 130×245 | Four horizontal plant drawings; visible printed numerals 1–4 are within the full-height crops. |
| `app/src/main/assets/sprites/trees/weeping_willow_4frames.png` | 1060×288 | 4 | 265×288 | Four horizontal poses. |
| `app/src/main/assets/sprites/trees/jacaranda_4frames.png` | 880×243 | 4 | 220×243 | Four horizontal poses. |
| `app/src/main/assets/sprites/trees/bamboo_4frames.png` | 572×203 | 4 | 143×203 | Four horizontal clumps. |
| `app/src/main/assets/sprites/trees/cherry_blossom_4frames.png` | 996×249 | 4 | 249×249 | Four horizontal poses. |
| `app/src/main/assets/sprites/birds/duck_4frames.png` | 640×207 | 4 | 160×207 | Four drawings with ground/soil details; byte-identical to flying-named alias. |
| `app/src/main/assets/sprites/birds/duck_flying.png` | 640×207 | 4 | 160×207 | Byte-identical to `duck_4frames.png`; the distinct path has no distinct pixel content. |
| `app/src/main/assets/sprites/birds/tit_4frames.png` | 640×148 | 4 | 160×148 | Four bird drawings with branch details; byte-identical to flying-named alias. |
| `app/src/main/assets/sprites/birds/tit_flying.png` | 640×148 | 4 | 160×148 | Byte-identical to `tit_4frames.png`. |
| `app/src/main/assets/sprites/birds/chickadee_4frames.png` | 552×114 | 4 | 138×114 | Four bird drawings; byte-identical to flying-named alias. |
| `app/src/main/assets/sprites/birds/chickadee_flying.png` | 552×114 | 4 | 138×114 | Byte-identical to `chickadee_4frames.png`. |
| `app/src/main/assets/sprites/birds/owl_4frames.png` | 632×143 | 4 | 158×143 | Four owl drawings with branch details; byte-identical to flying-named alias. |
| `app/src/main/assets/sprites/birds/owl_flying.png` | 632×143 | 4 | 158×143 | Byte-identical to `owl_4frames.png`. |
| `app/src/main/assets/sprites/birds/eagle_4frames.png` | 632×165 | 4 | 158×165 | Four eagle-like drawings with branch/leaf details; byte-identical to flying-named alias. |
| `app/src/main/assets/sprites/birds/eagle_flying.png` | 632×165 | 4 | 158×165 | Byte-identical to `eagle_4frames.png`. |
| `app/src/main/assets/sprites/animals/cat_4frames.png` | 600×130 | 4 | 150×130 | Four horizontal cat drawings. |
| `app/src/main/assets/sprites/animals/dog_4frames.png` | 580×149 | 4 | 145×149 | Four horizontal dog drawings; the fourth appears materially darker in the visual inventory. |
| `app/src/main/assets/sprites/animals/fox_4frames.png` | 632×166 | 4 | 158×166 | Four horizontal fox drawings. |
| `app/src/main/assets/sprites/animals/hedgehog_4frames.png` | 532×166 | 4 | 133×166 | Four horizontal hedgehog drawings. |
| `app/src/main/assets/sprites/animals/wolf_8frames.png` | 1280×156 | 8 | 160×156 | Eight authored wolf poses; each runtime crop selects one pose. |

The 1280×156 strip contains eight distinct 160×156 poses and is now packaged as `wolf_8frames.png`. `SpriteManager` selects eight crops at 8 fps; the importer, asset path, runtime sheet, and current creative documentation agree. The prior four-frame interpretation combined two adjacent poses per crop and is superseded by the source correction recorded in `source_completion_delta_2026-10-28.md`. The source correction establishes frame selection and packaging; it does not establish human approval of animation appeal or provenance.

SHA-256 equality confirms five exact base/flying duplicate pairs:

- `birds/chickadee_4frames.png` = `birds/chickadee_flying.png` (`0fa00161d46c8b3bbcbf3d816c0a096202205b86d87489bab9de5a156b7c9b61`)
- `birds/duck_4frames.png` = `birds/duck_flying.png` (`6c8784a6ca02e4f3c0ef681dbba7821902697356c47292afa26557c9c116554e`)
- `birds/eagle_4frames.png` = `birds/eagle_flying.png` (`e857abbd761d3481510ad46b416e07a06ad68df08e1c69a436f4f50a1121ce16`)
- `birds/owl_4frames.png` = `birds/owl_flying.png` (`c786b00351076d6e5ae25d8c170840198573e978beaaee2a55f96eeabb5ee25f`)
- `birds/tit_4frames.png` = `birds/tit_flying.png` (`011afae97578c4cfe4e097a6c5681f07679c83a92d77c487d538398d485b26de`)

Each pair is loaded from separate asset paths into separate `SpriteSheet` objects; byte equality means the visual source pixels are the same, not that runtime state is shared. The asset inventory records those same identities and visual observations at `asset_visual_inventory.md:50-59`.

## Selection and production call sites

### Player

`SpriteManager.kt:78-189` binds the five player assets. Run and duck are full 48-frame looping sheets (24 fps and 12 fps). Hit and death are full 12-frame nonlooping sheets (15 fps and 12 fps). The 48-frame jump bitmap produces distinct views over one shared bitmap: jump-start frames `[0,2)` at 20 fps; jumping `[2,14)` at 15 fps looping; apex `[14,18)` at 8 fps looping; falling `[18,24)` at 12 fps looping; landing `[24,28)` at 25 fps nonlooping; and a menu-only stand-up presentation `[0,18)` at 12 fps nonlooping. These constructor ranges are at `SpriteManager.kt:92-149`; the source strip itself remains 48 frames. `SpriteSheet.draw` converts each view's relative current frame to the source frame using `startFrame` (`SpriteSheet.kt:100-113`). `Player` consumes state sheets and can explicitly pin a terminal frame at `app/src/main/java/com/anurag9000/forestrun/entities/Player.kt:454`; wider owner selection is described in the existing code map. No claim here that each authored visual pose semantically matches its state is made.

### Flora and labels

`EntityFactory.create` passes copies of cactus, lily, hyacinth, eucalyptus, and orchid sheets to the corresponding entities (`app/src/main/java/com/anurag9000/forestrun/entities/EntityFactory.kt:74-79`). Their entities update and draw their `SpriteSheet` (`Cactus.kt:63,94`; `LilyOfValley.kt:98,175`; `Hyacinth.kt:92-107,110-146`; `Eucalyptus.kt:89,118`; `VanillaOrchid.kt:84-92,95-127`). The sheet crop remains full-height, so in the hyacinth and vanilla orchid assets the printed “1–4” marks inside each cell are not removed by any cropping rule; they are part of the displayed source rectangle. Runtime screenshots were not captured, so actual perceived legibility is unverified.

GardenScreen's current visual catalogue also creates independent copies of five plant and four tree sheets (`app/src/main/java/com/anurag9000/forestrun/ui/GardenScreen.kt:82-108`), advances them (`:387`), draws current frames into card rectangles (`:575-607`), and overlays emoji labels (`:607`). These UI labels are separate from the numeral marks baked into two source images. The file is modified in the current worktree and must be treated as such. Visitor sprites select cat, fox, dog, owl, eagle, wolf, or cat fallback (`GardenScreen.kt:1105-1112`); the visual inventory is not evidence that every asset is used in that view.

VanillaOrchid draws the *same current sprite frame twice* into two destination rectangles (bottom and top segments) at `VanillaOrchid.kt:115-127`; it does not pick different frames for those segments. Lily/Hyacinth and other flora maintain their own copied animation state from the shared manager source sheet.

### Animals and wolf

`EntityFactory.kt:130-145` supplies copied cat, wolf, fox, hedgehog, and dog sheets. `Wolf.update` advances its sheet once per update (`app/src/main/java/com/anurag9000/forestrun/entities/animals/Wolf.kt:96-98`); `Wolf.draw` draws the selected crop into one aspect-ratio-derived rectangle (`:180-181`). Wolf now receives eight 160×156 source crops at the 8-fps manager rate; no Wolf-specific selector is needed. Cat, Dog, Fox, and Hedgehog retain their four-frame sheets and their own `update`/`draw` methods.

Cat, Dog, Fox, Hedgehog similarly receive four-frame copies and their own `update`/`draw` methods. Relevant draw/update call sites: `Cat.kt:80,93`, `Dog.kt:150,287`, `Fox.kt:99,169`, and `Hedgehog.kt:94,127`; Hedgehog also pins the final frame in one branch (`:196`).

### Birds and alias selection

`SpriteManager.kt:206-223` loads all ten declared bird files as independent four-frame looping 8-fps sheets. `EntityFactory.kt:111-128` uses the flying-named sheet for Duck, TitGroup, ChickadeeGroup and Eagle. Owl receives both base and flying sheets (`:114-120`), and `Owl` holds `currentSprite = idleSprite` initially then changes to `actionSprite` on dive, resets it and draws current selection (`app/src/main/java/com/anurag9000/forestrun/entities/birds/Owl.kt:29-53,98-100,127-138,141-162`). For the five identical pairs, the state/source-path change does not change pixels; Owl's independent `SpriteSheet` state still resets to frame zero on the transition. Duck/Tit/Chickadee/Eagle use the flying-named file directly at their constructor call sites; their update/draw references are `Duck.kt:96,130`, `TitGroup.kt:92,128`, `ChickadeeGroup.kt:93,123`, and `Eagle.kt:145-160,171-193`.

The manager also creates base-name sheets for Duck, Tit and Chickadee, but the inspected primary factory path passes their flying aliases into encounters. This report did not claim global absence of all other possible consumers; a whole-repository call search would be required to establish that. Both base and flying bytes are identical for all five species in the current asset state.

## Status, implications, and unresolved questions

- **Statically established:** all 29 current asset paths are declared and loaded; all declared dimensions divide exactly by their current counts; crop geometry is horizontal and full-height; the caller mappings and frame rates/ranges above follow inspected Kotlin source.
- **Directly observed asset facts reused from local visual inventory:** Wolf's eight-pose strip is now loaded as eight frames; the earlier four-frame conclusion is superseded by the source correction. Hyacinth and vanilla orchid frames show numerals 1–4; the five bird base/flying pairs share identical pixels; dog fourth drawing is darker. These are appearance observations, not established source defects. The inventory states its source/revision and visual limits (`asset_visual_inventory.md:1-6`).
- **Not verified at runtime:** successful Android decode/normalization on target devices, bird sanitizer output, actual rendered label visibility, animation transitions or visual quality, and whether the current worktree's changed GardenScreen behaves as intended.
- **Unknown intent:** whether baked plant numerals are intended in gameplay; whether bird aliases are intentionally retained path aliases or placeholder naming; whether the darker dog frame is intentional; asset authorship/licensing and source-sheet extraction lineage. Wolf frame selection is resolved in the current source delta; human visual approval remains open.
- **Future task disposition:** retain the remaining questions as creative/specification, rights, or physical visual-review matters until authoritative evidence answers them. Do not classify a suspected visual defect solely from filenames or dimensions. No further Wolf frame-count source change is indicated by current evidence; plant-label or alias changes require an approved asset or crop specification.

## Evidence files

- `app/src/main/java/com/anurag9000/forestrun/engine/AssetPaths.kt:3-49`
- `app/src/main/java/com/anurag9000/forestrun/engine/SpriteManager.kt:13-25,71-230,232-276,278-307,309-405`
- `app/src/main/java/com/anurag9000/forestrun/engine/SpriteSheet.kt:10-37,52-117`
- `app/src/main/java/com/anurag9000/forestrun/engine/SpriteSizing.kt:3-14`
- `app/src/main/java/com/anurag9000/forestrun/entities/EntityFactory.kt:74-145`
- `app/src/main/java/com/anurag9000/forestrun/entities/animals/Wolf.kt:36-53,96-106,152-182`
- `app/src/main/java/com/anurag9000/forestrun/entities/flora/Hyacinth.kt:29-48,92-107,110-146`
- `app/src/main/java/com/anurag9000/forestrun/entities/flora/VanillaOrchid.kt:29-49,84-92,95-127`
- `app/src/main/java/com/anurag9000/forestrun/entities/birds/Owl.kt:29-53,98-100,127-138,141-162`
- `app/src/main/java/com/anurag9000/forestrun/ui/GardenScreen.kt:82-115,387,575-607,1105-1112` (current worktree file is modified)
- `docs/repository_knowledge/asset_visual_inventory.md:1-6,45-73` (current worktree untracked report)
