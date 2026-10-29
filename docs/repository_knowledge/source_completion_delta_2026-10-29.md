# Source Completion Delta — 2026-10-29

## Scope and baseline

This is a targeted correction against repository `Forest_Run`, branch `main`, starting HEAD `b586f17a12c359718d8056c9fe7983cd157c894c`. The working tree already contained user-owned untracked evidence and knowledge artifacts; those are preserved. Existing investigations and exact-candidate evidence in the master knowledge map were reused. This delta is not a fresh whole-repository audit and does not replace human, physical-device, legal, or release acceptance.

## Confirmed defect: player animation atlas slicing

Direct image inspection of `Final_Assets (2)/char/runner_girl_duck_48frame.png` shows a regular 6×6 layout with 36 character poses. The importer previously divided it into 48 cells, creating narrow/cut cells and empty frames. The runtime path and `SpriteManager.PLAYER_DUCK_FRAMES` also declared 48. The runtime strip now has the truthful name `runner_girl_duck_36frame.png`, is extracted as 36 full cells, and the runtime asset contract expects 36.

`Final_Assets (2)/char/runner_girl_jump_48frame.png` has a nonuniform layout: six cells in each of the first three rows and eight in each of the last three rows, for 42 poses. Uniform 8×6 slicing visibly cut drawings. The importer now uses that row-specific layout, writes `runner_girl_jump_42frame.png`, and `SpriteManager.PLAYER_JUMP_STRIP_FRAMES` declares 42. The six existing views use ranges `[0,2)`, `[2,14)`, `[14,18)`, `[18,24)`, `[24,28)`, and `[0,18)`, all within the imported strip. The numeric source labels contain gaps/repetitions and are not treated as reliable metadata; row-major order remains the adopted import order. Physical validation of animation quality remains open.

The importer also previously retained detached index glyphs and grid artifacts from the jump/duck atlases and the Hyacinth and Vanilla Orchid plant sheets. It now removes small detached upper-gutter/edge/grid components for the player atlases and removes detached number components below the two plant sheets. Source atlas files are unchanged.

## Implementation changes

- `scripts/import_final_assets.py`: supports variable per-row source cell counts; maps the jump atlas to `(6, 6, 6, 8, 8, 8)` and duck to 6×6; removes detached atlas indices; publishes runtime files with truthful frame-count names.
- `scripts/test_import_final_assets.py`: checks source layouts and frame totals, nonempty output crops, detached annotation removal, and preservation of pose pixels in synthetic frames.
- `AssetPaths.kt` and `SpriteManager.kt`: load the corrected 42- and 36-frame runtime assets.
- `RuntimeAssetContractTest.kt`: checks packaged dimensions and that all jump animation views fit the corrected bitmap frame geometry.
- The two obsolete packaged 48-frame jump/duck outputs were removed.
- `asset_visual_inventory.md`, `runtime_sprite_frame_trace.md`, `docs/ARCHITECTURE.md`, and the historical codebase/symbol maps were reconciled: current runtime paths/counts are authoritative, and old 48-frame names are retained only for the legacy source atlas or explicitly historical snapshots.

## Verification evidence

The Python asset-importer suite was run with `python3 -m unittest test_import_final_assets -v` from `scripts/`: **9 tests passed**. The complete script suite was then run from the repository root with `python3 -m unittest discover -s scripts -p 'test_*.py'`: **1,403 tests passed**. Re-importing all four changed outputs reproduced their SHA-256 values byte-for-byte. Imported contact sheets for all 42 jump poses, all 36 duck poses, and all four Hyacinth and Vanilla Orchid poses were visually inspected. A static per-frame PNG scan confirmed every jump and duck frame is nonempty and each output width divides exactly into its declared count. The generated dimensions are jump `4368×107` (42×104-pixel logical frames) and duck `3600×107` (36×100-pixel logical frames). Hyacinth is `448×269` with four 112-pixel frames; Vanilla Orchid is `520×245` with four 130-pixel frames. All fit existing maximum-sheet constraints.

Under Android Studio bundled Java 21, the full command `./gradlew compileDebugKotlin compileDebugUnitTestKotlin compileReleaseKotlin compileDebugAndroidTestKotlin testDebugUnitTest lintDebug lintRelease assembleDebug assembleDebugAndroidTest bundleRelease` completed successfully. The Gradle report contains **1,330 JVM/Robolectric tests, 0 failures, 0 errors, and 0 skipped**; all four `RuntimeAssetContractTest` cases passed, including the new jump/duck frame-range and packaged-asset checks. Debug and release Kotlin, AndroidTest compilation, lint, debug APK, instrumentation APK, and release AAB packaging all passed. `adb devices -l` returned no devices, so connected instrumentation was not run. The first local Gradle attempt used Java 17 and Robolectric refused SDK 36; rerunning with the installed Android Studio Java 21 succeeded.

No physical device is available, as reported by the user; physical readability, human creative approval, and asset provenance/licensing remain separate external gates.

## Status and remaining checks

The source-addressable slicing defect is corrected at the importer/runtime/contract level and passed the listed static, Python, JVM/Robolectric, compile, lint, and package checks. The row-major interpretation of the irregular jump source is explicit, tested for bounds and frame presence, and visually reviewed; its semantic animation timing still requires physical/human acceptance. No gameplay physics, scoring, encounter, or persistence behavior was intentionally changed. The source delta was committed directly to `main` and pushed as `26fb6ad0fc5ff4e11f703b651216ca5b00f1e357`. Exact-SHA estate-certificate, training-applicability and host/release/lint/package workflows passed; the API 35 connected-validation job and artifact upload passed (run 40833316035, job 123314851778), as did host/release/lint/package, training-applicability, and estate-certificate workflows (EVID-165). No physical-device acceptance is available. Exact workflow evidence and limits are recorded in `execution/EVIDENCE_INDEX.json` as EVID-165; detailed per-test counts were not retrieved from those hosted logs.
