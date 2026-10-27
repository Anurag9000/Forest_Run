# TASK-A-018 — Android resources and visual/audio assets

**Snapshot:** `eeccc677107f69039dbf71053c18a9038d4b5ff6` plus dirty worktree, 2026-10-27. **Disposition:** 80 assigned paths accounted for: eight XML files read as text; 72 binary files classified using file metadata and SHA-256, without dumping payloads or visual/audio review. No source changes or application workflows.

## Creative references

Twenty-seven `Final_Assets (2)/` PNGs are 640×640 RGB regular files. The existing repository map classifies this folder as source/reference creative material, not packaged runtime assets. Names and hashes cannot establish visual contents, authorship, rights, intended mappings or approval. No provenance conclusion for this subset was established.

## Runtime assets and exact duplicates

Thirty packaged assets comprise 29 PNG sprites and `app/src/main/assets/fonts/PressStart2P-Regular.ttf`. Existing `AssetPaths`, `SpriteManager`, runtime validation and UI code provide ownership/consumer links; `REPO_CODEBASE_MAP.md:653` has the exact path listing. Hashes confirm five byte-identical bird base/flying pairs (chickadee, duck, eagle, owl, tit), consistent with the documented alias allowlist, plus the duplicate launcher XML pair below. This establishes byte identity, not visual suitability or approval. Font metadata names Press Start 2P Project Authors and 2012 copyright; it does not establish distribution rights.

Fifteen Ogg/Vorbis files under `app/src/main/res/raw/` map to six music states (`LeitmotifManager.kt:108-113`) and nine effects (`SfxManager.kt:139-150`); `RuntimeAssetValidator.kt:49-66` declares required names. Container metadata does not establish audible content, quality, suitable duration, licensing or human approval.

Across all 80 files, 74 unique SHA-256 values were found: five bird alias pairs and byte-identical `mipmap-anydpi-v26/ic_launcher.xml` / `ic_launcher_round.xml`. The 27 creative-reference PNGs had no exact digest matches against runtime files; this does not settle visual equivalence or ancestry.

## XML resources

`AndroidManifest.xml:7-20` selects app label/theme and launcher icons. `drawable/ic_launcher_background.xml:1-19` is a 108dp forest background; `ic_launcher_foreground.xml:1-50` defines leaf/vein/seed vector content. Any-density wrappers select vector icons; API-26 adaptive wrappers reference background/foreground and monochrome foreground. `values/colors.xml:1-11` defines forest/basic colors. `themes.xml:1-18` defines fullscreen no-action-bar styling and system/window bars.

## Provenance and evidence boundary

`ASSET_PROVENANCE.md` and `CREATIVE_ASSET_PROVENANCE.md` keep applicable packaged groups review-required pending source/creator, license, attribution and reviewer evidence bound to a candidate. Those fields and external approvals are not supplied by hashes. Visual contents, frame order/cropping, creative-direction fit and correspondence of reference assets to runtime sprites remain unknown; audio content and listening quality remain unknown. Do not convert these unknowns into source defects without authorized human review and a defined contract.
