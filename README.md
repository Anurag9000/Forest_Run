# Forest Run

`Forest Run` is a native Android endless runner in Kotlin using a custom `SurfaceView` loop. The intended product is not a minimal score-chaser. The intended product is a handcrafted, personality-rich forest journey with cute reactive entities, a mercy system, Bloom power spikes, a chill garden meta-loop, and enough audiovisual identity to feel like a complete indie game rather than a prototype.

## Product Direction

The non-negotiable direction for this repo is:

- Restore and fully implement the original dream spec from the early long-form docs.
- Treat the original GDD, Undertale-inspired personality layer, garden progression, and polish roadmap as the target product, not as discarded aspirations.
- Use the current implementation only as a partial baseline.
- Close every gap between the shipped build and the originally imagined experience.

## Original Dream

The original vision combined:

- a lush cottagecore endless runner with Ghibli x Stardew Valley tone
- a ritualized start flow: sit in garden, stand, then run
- five atmospheric biomes that feel like mood chapters
- seeds as both in-run power progression and long-term garden currency
- Bloom as a dramatic, audiovisual invincibility state
- animals with individual personality, reactions, mercy logic, and spare outcomes
- flavor text, ghost replay, memory, and Undertale-like charm
- a restorative garden loop that gives every run long-term meaning

See [docs/GDD.md](/home/anurag-basistha/Projects/TODO/Forest_Run/docs/GDD.md), [docs/UNDERTALE_VIBE.md](/home/anurag-basistha/Projects/TODO/Forest_Run/docs/UNDERTALE_VIBE.md), [docs/IMPLEMENTATION_ROADMAP.md](/home/anurag-basistha/Projects/TODO/Forest_Run/docs/IMPLEMENTATION_ROADMAP.md), and [docs/TODO_MATRIX.md](/home/anurag-basistha/Projects/TODO/Forest_Run/docs/TODO_MATRIX.md).

## Current Repo Reality

What exists in code today:

- native Android app module, package `com.yourname.forest_run`
- `SurfaceView` render loop and landscape activity
- two-tap menu start and separate garden screen
- player movement with run, jump, duck, stumble, bloom, and rest states
- 19 entity classes across flora, trees, birds, and animals
- five-biome tint cycle with biome-specific spawn pools
- HUD for score, distance, seeds, Bloom meter, and mercy hearts
- seed persistence, Bloom activation, ghost save/load, and garden unlock persistence
- audio and haptics managers

What is still missing, incomplete, or user-reported as unsatisfactory:

- entities read too small on phone and are spaced too far apart to appreciate visually
- the ghost runner currently undermines readability and can create the impression of a broken double-runner presentation
- many entity-specific personalities exist in code but are not yet delivered with enough clarity, frequency, staging, or charm to feel deliberate in play
- the forest still lacks the full handcrafted art, dense environmental life, and layered spectacle described in the original dream
- the full persistent memory, costume, dialogue, pacifist, and determination-quote systems are not complete
- several dream-spec feedback loops may exist technically but are not surfacing reliably enough to a player during normal play

## Immediate Product Concerns

The latest user-reported playtest concerns must be treated as active product bugs, not minor polish:

- entity readability and scale
- spawn density and encounter pacing
- ghost playback clarity and whether it should be shown by default
- visibility and trustworthiness of seeds, Bloom meter, Bloom state, mercy hearts, and garden loop
- whether each entity behavior actually feels unique and readable on device
- whether the personality layer is emotionally legible during play rather than merely present in code

## Build And Test

```bash
bash gradlew testDebugUnitTest
bash gradlew assembleDebug
bash gradlew assembleDebugAndroidTest
bash gradlew connectedDebugAndroidTest
```

Generated artifacts:

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Android test APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`

## Documentation Map

- [docs/GDD.md](/home/anurag-basistha/Projects/TODO/Forest_Run/docs/GDD.md): full game dream, current implementation, missing experience
- [docs/IMPLEMENTATION_ROADMAP.md](/home/anurag-basistha/Projects/TODO/Forest_Run/docs/IMPLEMENTATION_ROADMAP.md): phase-by-phase path to reach the original target
- [docs/UNDERTALE_VIBE.md](/home/anurag-basistha/Projects/TODO/Forest_Run/docs/UNDERTALE_VIBE.md): personality, mercy, memory, charm systems
- [docs/ENTITY_DATABASE.md](/home/anurag-basistha/Projects/TODO/Forest_Run/docs/ENTITY_DATABASE.md): intended behavior for every entity and current gap
- [docs/TODO_MATRIX.md](/home/anurag-basistha/Projects/TODO/Forest_Run/docs/TODO_MATRIX.md): strict list of unimplemented or insufficient dream-spec items
- [docs/TECHNICAL_ARCHITECTURE.md](/home/anurag-basistha/Projects/TODO/Forest_Run/docs/TECHNICAL_ARCHITECTURE.md): runtime structure and architectural gaps
- [docs/VISUAL_FX_SPEC.md](/home/anurag-basistha/Projects/TODO/Forest_Run/docs/VISUAL_FX_SPEC.md): presentation and feedback target
- [spec.md](/home/anurag-basistha/Projects/TODO/Forest_Run/spec.md): repo truth and product mandate
