# Repo State And Product Mandate

## Identity

- Project type: native Android game
- Language: Kotlin
- Rendering: `SurfaceView`
- Build system: Gradle
- Persistence: local only
- Product goal: complete the original dream spec, not a reduced audit-safe subset

## Hard Product Mandate

This repo must move toward the original vision in full:

- all dream-spec systems are considered target scope unless explicitly abandoned later by the user
- docs must preserve ambition and reality at the same time
- “implemented in code” is not enough if the player cannot clearly see, feel, understand, or enjoy the mechanic on device
- user-reported experiential failures are product-truth inputs and must be documented as gaps even if the code contains partial implementations

## Current Baseline

The repo currently has:

- one Android app module
- no backend module
- no web frontend
- no Compose UI layer
- local save only
- runtime assets sourced from `Final_Assets (2)` and imported into `app/src/main/assets/sprites/`

## Important Historical Correction

The March 14, 2026 audit corrected real bugs and stabilized the repo, but it also rewrote docs around verified current state. That audit was useful as a reality check, not as the final product definition. The original long-form design remains the north star.

## Active Gaps From Current Play

- Entities are reportedly too small and too infrequent to read comfortably on phone screens.
- Ghost playback is reportedly confusing enough to look like a broken duplicate runner.
- Seeds, Bloom, mercy, HUD signals, and garden progression are not surfacing clearly enough in actual play.
- Entity uniqueness and Undertale-like charm are not landing strongly enough at runtime.
- The game still falls short of the desired authored emotional arc:
  menu ritual -> atmospheric run -> expressive encounters -> Bloom catharsis -> soft failure -> reflection -> garden healing -> remembered return

## Validation Commands

```bash
bash gradlew testDebugUnitTest
bash gradlew assembleDebug
bash gradlew assembleDebugAndroidTest
bash gradlew connectedDebugAndroidTest
```

## Documentation Rule

Every document in this repo should now answer all three questions explicitly:

1. What was originally imagined?
2. What is actually present today?
3. What remains missing, broken, too weak, or too unclear to satisfy the original vision?
