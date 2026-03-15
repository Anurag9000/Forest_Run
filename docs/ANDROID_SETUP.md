# Forest_Run — Android Studio Setup Guide (Restored)

This restores the original setup guide as part of the live dream spec. Missing technical setup work belongs in [docs/TODO_MATRIX.md](/home/anurag-basistha/Projects/TODO/Forest_Run/docs/TODO_MATRIX.md).

## 1. Package & Project Setup

| Setting | Value |
|---|---|
| Package Name | `com.yourname.forest_run` |
| Project Name | `Forest_Run` |
| Min SDK | API 24 |
| Target SDK | API 34 |
| Language | Kotlin |
| Build System | Gradle |
| Screen Orientation | Landscape / sensorLandscape |

## 2. AndroidManifest.xml

The project should include:

- `screenOrientation="sensorLandscape"`
- `configChanges="orientation|screenSize|keyboardHidden"`
- immersive full-screen behavior
- `keepScreenOn`
- `VIBRATE` permission
- optional `WAKE_LOCK`

### Current Status

- Implemented: landscape, immersive runtime, haptic permission, native app shell.
- TODO: keep the setup guide aligned with any future platform expansion, release hardening, and final production constraints.

## 3. MainActivity

The intended role of `MainActivity` is:

- own full-screen immersive flags
- host `GameView`
- delegate pause/resume correctly

### Current Status

- Implemented: fullscreen host activity with `GameView`.

## 4. Gradle / Build

Dream-spec setup includes:

- Kotlin Android app
- API 24 min / API 34 target
- Java 17
- release minification
- Gson available for richer persistence when needed

### Current Status

- Implemented: working Android Gradle project.
- TODO: final release hardening when the product is actually feature-complete.

## 5. Asset Folder Structure

The original setup spec expected a rich asset layout covering:

- player sprite sheets
- flora, trees, birds, animals
- particle sprites
- UI art
- parallax backgrounds
- audio assets in `res/raw`

### Current Status

- Implemented: runtime assets and import pipeline exist.
- TODO: full final art pack still does not match the complete dream-spec breadth.

## 6. Audio Files

Dream-spec audio library includes:

- garden music
- multiple run layers
- Bloom music
- rest music
- jump, land, seed, Bloom, bark, screech, howl, kindness, and rest SFX

### Current Status

- Implemented: several core music and SFX files exist.
- TODO: full authored leitmotif-consistent audio coverage.

## 7. Theme & Styles

The app should remain:

- full-screen
- no action bar
- visually clean for uninterrupted gameplay

## 8. Asset Loading

The original setup expected helper utilities for:

- asset bitmap loading
- scaling
- sprite slicing

### Current Status

- Implemented: sprite loading helpers and managers exist.

## 9. Performance Checklist

Still mandatory:

- delta-time based movement
- no bitmap decoding in draw loop
- no `Paint` churn
- pooled or recycled runtime objects where needed
- capped particles
- real-device profiling

### Current Status

- Partial: many runtime systems are optimized.
- TODO: full device-truth profiling and polish once gameplay vision is complete.

## 10. Debugging Physics Checklist

The original setup guide required validation of:

- short hop vs long jump
- duck response
- collisions
- kindness rewards
- fox jump logic
- wolf howl/charge
- hedgehog debuff
- milestones
- Bloom activation
- biome transitions

### Current Status

- TODO: complete this checklist on real hardware with current build and keep it passing after every major gameplay change.
