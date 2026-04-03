# Forest Run — Release & Validation

This is the only file tracking open work. The engine is fully implemented. Everything remaining is hardware validation and store pipeline.

---

## Build Commands

```bash
# Unit tests
bash gradlew testDebugUnitTest

# Debug APK
bash gradlew assembleDebug

# Instrumented test APK
bash gradlew assembleDebugAndroidTest

# Connected device tests
bash gradlew connectedDebugAndroidTest
```

**Output locations:**
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Android test APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`

---

## Release Asset Pipeline

```bash
# Generate Google Play feature art
python3 scripts/generate_store_assets.py

# Capture deterministic store screenshots on connected device
bash scripts/capture_store_screenshots.sh

# Curate final screenshot set from raw captures
python3 scripts/curate_store_screenshots.py

# Verify release packaging and emit build summary
python3 scripts/prepare_play_release.py
```

---

## Project Setup Reference

| Setting | Value |
|---|---|
| Package Name | `com.yourname.forest_run` |
| Min SDK | API 24 |
| Target SDK | API 34 |
| Language | Kotlin |
| Build System | Gradle |
| Orientation | `sensorLandscape` |
| Java | 17 |

**AndroidManifest requirements:** `sensorLandscape`, `configChanges="orientation|screenSize|keyboardHidden"`, immersive full-screen, `keepScreenOn`, `VIBRATE` permission.

---

## Device Acceptance Checklist

A feature is not done until both the deterministic scenario and the real-device check pass. Use the exact scenario names listed here when launching via the debug scenario launcher.

### Rules

- Run unit tests and assemble debug before device testing.
- If a scenario fails on device, retune before marking done.
- Validate on at least one low-end, one mid-range, and one high-refresh Android device.
- Long runs must remain readable after repeated Bloom, ghost, and dense-entity sequences.

### Core Flow

| Scenario | Acceptance Criterion |
|---|---|
| `OPENING_READABILITY` | First 20–30 seconds teach duck, jump, and spacing without confusion |
| `BLOOM_SHOWCASE` | First-time player can tell Bloom activated, world changed, HUD entered power state, rewards transformed |
| `GHOST_READABILITY` | Ghost never reads like a broken duplicate and never obscures the live runner |
| `REST_LOOP` | Run failure, rest summary, fade, and Garden return feel continuous and readable |

### Flora

| Scenario | Acceptance Criterion |
|---|---|
| `CACTUS_READ` | Cactus silhouette reads instantly and jump timing feels fair |
| `LILY_GLOW` | Lily glow and seed-lure identity are visible without squinting |
| `HYACINTH_BRUSH` | Brush-vs-hit difference is obvious in motion |
| `EUCALYPTUS_WHIP` | Lean/whip read is early enough to feel fair |
| `ORCHID_WINDOW` | The safe two-window path reads immediately on phone |

### Trees

| Scenario | Acceptance Criterion |
|---|---|
| `WILLOW_CURTAIN` | Willow feels scenic and obscuring without becoming unreadable |
| `JACARANDA_PETALS` | Petal curtain reads as intentional pressure, not visual mud |
| `BAMBOO_GAP` | Precision threading is clear and fair |
| `CHERRY_GUST` | Gust-pressure feel is visible and distinct from other trees |

### Birds

| Scenario | Acceptance Criterion |
|---|---|
| `DUCK_TEACH` | Duck-lane cue and duck-through timing are unmistakable |
| `TIT_WAVE` | Rhythm-wave flock reads as one timing pattern |
| `CHICKADEE_SWERVE` | Flutter path feels erratic but still readable |
| `OWL_DIVE` | Owl alert, glow, and dive timing are legible in normal phone play |
| `EAGLE_MARK` | Eagle reticle and mark cue create clear fear/read timing |

### Animals

| Scenario | Acceptance Criterion |
|---|---|
| `CAT_KINDNESS` | Cat kindness reward and spare warmth are obvious in normal play |
| `FOX_MIRROR` | Fox mirror-jump and landing payoff feel playful, not vague |
| `WOLF_CHARGE` | Howl, charge, and spare payoff are unmistakable |
| `HEDGEHOG_DEBUFF` | Hedgehog warning, hit, and debuff are fair and visible |
| `DOG_HAZARD` | Bark projectile timing is readable on first sight |
| `DOG_BUDDY` | Buddy mode feels memorable and clearly harmless |

---

## Open Items

These are the only remaining open items for the entire project. All engine features are fully implemented.

### Hardware Validation (required before release)

- [ ] Pass every scenario in the checklist above on real hardware (low-end + mid-range + high-refresh)
- [ ] Validate entity readability and scale on physical phone screens
- [ ] Validate ghost playback visual fade/overlap tuning on real hardware
- [ ] Validate Bloom spectacle, Bloom conversion feel, and power-surge escalation on real hardware
- [ ] Validate forest mood, sanctuary lighting, and cinematic overlay on low/mid/high-end hardware
- [ ] Validate haptic tuning across device types
- [ ] Full performance audit — confirm stable 60 FPS across long runs on representative hardware

### Store Release Pipeline (after hardware validation)

- [ ] Capture live screenshots on a connected device using `capture_store_screenshots.sh`
- [ ] Run `curate_store_screenshots.py` against captured set to produce final Google Play screenshot set
- [ ] Complete final Play Console upload and release pass using prepared metadata and release bundle
- [ ] Final release-doc cleanup after Play Console submission is confirmed
