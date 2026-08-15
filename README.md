# Forest Run

Forest Run is a native Kotlin Android endless runner built around a custom `SurfaceView` loop and a persistent emotional world. The player begins beneath a willow, crosses five changing biomes, meets nineteen forest families, gathers Seeds, enters Bloom, practices mercy or causes harm, rests when a run ends, and returns to a Garden that remembers what happened.

**Core loop:** willow → five-biome run → Seeds/Bloom/encounters → Rest → changed Garden → remembered next run

## Current source status

The major source-architecture remediation queue is closed on `main`. Core input, Bloom, encounter arbitration, terminal/nonterminal outcomes, persistence/recovery, relationships, Garden progression, wardrobe, ghosts, accessibility, deterministic scenario evidence, release provenance, dependency/SBOM tooling, store evidence, and candidate-readiness orchestration all have explicit runtime owners.

The current product surface also includes a player-facing **Forest Journal** that exposes the history the game was already persisting: encounter discovery, clean passes, mercy/harm, relationship stages, Garden growth, wardrobe state, route history, peaceful-biome traces, best-run legacy, forest mood, last Rest, durable story pages, and derived long-horizon milestones. The Journal is a read-only projection; it does not create a parallel achievement or progression database.

Source completeness is **not** the same as production acceptance. Final creative approval, rights/licensing decisions, real production signing, privacy-policy hosting, private vulnerability-reporting configuration, Play Console declarations/delivery, and accountable release approval remain external release work.

## Player experience

### Movement

- quick tap → short jump;
- hold → higher/full jump;
- early release trims upward motion;
- downward swipe → duck;
- gameplay input is state-owned and cancellation-safe rather than leaking through menus/Garden/Rest.

### Five biomes

1. Meadow
2. Orchard
3. Ancient Grove
4. Dusk Canyon
5. Night Forest

The run remains one continuous world; biome presentation, spawn pools, scenery, atmosphere, and music change over distance.

### Nineteen encounter families

**Flora:** Cactus, Lily of the Valley, Hyacinth, Eucalyptus, Vanilla Orchid  
**Trees:** Weeping Willow, Jacaranda, Bamboo, Cherry Blossom  
**Birds:** Duck, Tit, Chickadee, Owl, Eagle  
**Animals:** Cat, Fox, Wolf, Hedgehog, Dog

`EncounterFamilyCatalogue` is the structural authority for the complete roster and derives biome/scenario/variant/relationship capability from existing runtime owners.

### Seeds and Bloom

- Bloom threshold: **8 Seeds**;
- Bloom active window: **6 seconds**;
- Bloom does not freeze ordinary player physics;
- active Bloom has one authoritative duration;
- incoming Seeds do not restart/extend the active timer;
- Bloom presentation spans aura, particles, music, haptic identity, HUD feedback, and encounter conversion behavior.

### Encounter outcomes

Collision arbitration selects exactly one result for an encounter interaction. Runtime dispatch separates terminal impact, nonterminal stumble/mercy consequences, relationship writes, summary persistence, visual/audio/haptic feedback, and state transitions instead of letting multiple ad-hoc branches reward/punish the same encounter.

### Persistent relationships

Cat, Fox, Wolf, Dog, Owl, and Eagle have persistent relationship arcs. The game remembers how the player repeatedly meets, passes, spares, or harms them. Relationship stages, warm/strained tone, Bond rewards, home presence, return moments, rituals, and some wardrobe unlocks persist across runs.

## Forest Journal

The willow menu exposes **MEMORY → FOREST JOURNAL** as a real touch and accessibility action.

The Journal uses native Android scrolling/text controls for long-form accessibility and has ephemeral filters for:

- All
- Progress
- Bonds
- Memories
- Families

It derives its state from gameplay owners and does not mutate progression.

### Journal progression views

**Run Legacy**
- high score;
- best distance;
- total remembered runs;
- current/dominant forest mood and mood streak;
- Gentle/Steady/Fearful/Reckless run history;
- most recent Rest summary.

**Collection Path**
- Forest Families discovered;
- Living Bonds completed;
- Garden completion;
- Wardrobe completion;
- Peace in Every Biome.

**Garden Sanctuary**
- exact canonical nine-plant order;
- grown/next/locked state;
- current Seed balance;
- affordability of the one legal next purchase;
- no purchasing from the Journal.

**Path History**
- Kind Path count;
- Merciful Path count;
- Peaceful Path count;
- derived `Every Gentle Shape` recognition when all three have returned home.

**Living Bonds**
- current relationship stage;
- warm/strained/learning tone;
- Bond milestone title/summary;
- Bond ritual;
- wearable memory when applicable.

**Memory Pages**
- creature thoughts;
- Rest memories;
- Garden memories;
- route/biome/mood/weather memories;
- repeated-encounter memories;
- Bloom memories;
- relationship/return histories.

Internal persistent page IDs are converted into player-facing titles/categories and pattern-specific authored prose.

See [`docs/FOREST_JOURNAL.md`](docs/FOREST_JOURNAL.md).

## Garden

The Garden is a persistent sanctuary rather than a post-run score screen. It already reflects relationship visitors, home characters, route/mood history, return moments, wardrobe state, story fragments, memory traces, weather/lighting, particles, and other long-horizon presentation.

`GardenEconomy` owns stable progression order, player-facing plant names, and Seed costs:

| Order | Plant | Seed cost |
|---:|---|---:|
| 1 | Lily | 15 |
| 2 | Cactus | 20 |
| 3 | Hyacinth | 25 |
| 4 | Eucalyptus | 30 |
| 5 | Vanilla Orchid | 40 |
| 6 | Weeping Willow | 50 |
| 7 | Jacaranda | 60 |
| 8 | Bamboo | 75 |
| 9 | Cherry Blossom | 100 |

The first catalogue entry is the initial Garden state; subsequent purchases are sequential and atomic through `GardenPurchaseManager`.

## Wardrobe

Current styles:

1. Classic
2. Flower Crown
3. Vine Scarf
4. Moon Cape
5. Bell Charm
6. Lantern Pin
7. Sky Sash
8. Bloom Ribbon

Unlocks are derived from actual run/relationship history and equipped state persists across sessions.

## Feedback and presentation

### Audio

Forest Run already has distinct state-aware music/SFX ownership rather than one generic loop:

- willow/menu/Garden identity;
- running music states and biome progression;
- Bloom transition/signature;
- Rest cadence;
- jump/land/Seed cues;
- species/encounter cues;
- mercy/hit/Bloom-conversion feedback;
- fail-safe fallback behavior for optional audio assets.

### Haptics

`HapticManager` exposes a semantic vocabulary for new call sites:

- `lightTick()`;
- `stumbleImpact()`;
- `terminalImpact()`;
- `mercyAcknowledgement()`;
- `gardenGrowth()`;
- `bloomSurge()`.

Compatibility wrappers preserve existing feedback behavior while future tuning can target meaning instead of raw durations.

### Visual identity

The Android-template launcher identity has been removed. Launcher resources now use an original Forest Run willow-leaf / Seed / Bloom vector mark, with adaptive, monochrome, round, and pre-adaptive vector coverage. Stock density-specific Android launcher WebPs were removed.

The app resource palette and cold-launch/system-bar background now use Forest Run forest/willow/Seed colors rather than stock Material purple/teal or template black.

Final creative production direction is defined in [`docs/CREATIVE_DIRECTION.md`](docs/CREATIVE_DIRECTION.md). Final human artistic approval and rights clearance are still external gates.

## Accessibility

The custom Canvas game exposes a real Android virtual-node hierarchy rather than pretending Canvas labels are accessible automatically.

Implemented source architecture includes:

- stable semantic node IDs;
- Menu, Settings, Playing, Garden, and Rest semantic surfaces;
- typed action validation/routing;
- touch-aligned semantic geometry;
- truthful Garden/wardrobe state;
- live-region/coalesced announcements;
- reduced-motion/audio/haptic controls;
- the Forest Journal as a stable Menu accessibility action;
- native semantic controls inside the Journal itself.

See [`docs/ACCESSIBILITY.md`](docs/ACCESSIBILITY.md).

## Persistence and recovery

Persistent state spans:

- high score / best distance;
- lifetime Seeds;
- Garden progress;
- wardrobe unlock/equip state;
- encounter/pass/spare/hit history;
- relationship stages and milestones;
- biome friendship;
- forest mood/run history;
- route-tier counts;
- Rest/last-run summary;
- memory pages/history marks;
- return-moment state;
- ghost run data and recovery metadata.

`ApplicationPersistenceFacade` is the shared live mutation boundary for high-level application writes. Low-level durability remains intentionally separated by storage domain; the project does not claim a fake global transaction across unrelated files/preferences.

## Ghosts

Ghost capture/replay includes bounded recording, stable state coding, recovery-aware persistence, namespace isolation, candidate/source identity tooling, and monotonic/distance validity checks. Ghost recovery remains intentionally separate from ordinary run-outcome persistence.

## Release/evidence engineering

The repository contains source tooling for:

- Android debug/release compilation and packaging;
- lint/R8/source-immutability checks;
- deterministic scenario evidence;
- physical-device acceptance manifests/aggregation;
- input-latency/performance evidence;
- accessibility/human acceptance manifests;
- candidate-bound screenshots;
- store graphics and metadata verification;
- dependency/SBOM evidence;
- native/page-size inspection;
- installed candidate identity;
- Play-delivery evidence;
- security/licensing/privacy/store governance evidence;
- final release-evidence indexing and readiness cross-binding.

These systems can validate evidence supplied to them; they cannot manufacture external facts such as a real Play upload, physical human approval, legal rights, or production signing identity.

## Release-facing product copy

Canonical human-authored store positioning, description, screenshot story, icon/wordmark direction, and feature-graphic brief live in [`docs/STORE_LISTING.md`](docs/STORE_LISTING.md).

Candidate-specific `title.txt`, `short-description.txt`, `full-description.txt`, screenshots, graphics manifests, and release summaries are intentionally generated/finalized for one frozen candidate rather than committed as misleading timeless evidence.

## Privacy

The checked-in application behavior is designed around an offline local game with no account, ads, analytics SDK, or cloud gameplay dependency. The source-backed policy is [`PRIVACY.md`](PRIVACY.md).

A public release still requires the accepted privacy policy to be hosted at a stable HTTPS URL and the Play declarations to match the exact shipping candidate.

## Security and licensing

Security/licensing governance is intentionally fail-closed:

- the repository must not claim private vulnerability reporting is enabled until the repository setting is actually enabled and verified;
- source-code licensing is **not** silently selected by the codebase;
- creative assets, audio, fonts, promotional media, trademarks, and third-party dependencies require their own owner-approved rights/notice decisions;
- hashes/inventories prove byte identity, not legal permission.

See [`SECURITY.md`](SECURITY.md), [`docs/SECURITY_AND_LICENSING_GOVERNANCE.md`](docs/SECURITY_AND_LICENSING_GOVERNANCE.md), and [`docs/CREATIVE_ASSET_PROVENANCE.md`](docs/CREATIVE_ASSET_PROVENANCE.md).

## What remains outside source implementation

The major remaining blockers are not another collision/persistence/relationship architecture. They are principally:

- final production art/animation selection and approval;
- final music/SFX/haptic mastering and human acceptance;
- creator/source/licence/attribution review for every shipping creative asset;
- owner decision on source/asset/audio/font/trademark/contribution licensing;
- required third-party notices after the resolved dependency review;
- enabling and verifying the chosen private vulnerability-reporting mechanism;
- publishing the accepted privacy policy at a stable HTTPS URL;
- real production upload key / Play App Signing configuration;
- Play Console application ownership, Data Safety, target audience, content rating, category, regions, support/privacy metadata, and release tracks;
- candidate-specific signed artifacts, store metadata, screenshots, release notes, and final production tag once the exact accepted candidate exists.

These should not be marked complete merely because source validators exist.

## Development workflow

`main` is the only active development branch and the source of repository truth for this project workflow.

- implementation is committed directly to `main`;
- do not create routine development PRs/branches;
- preserve published history; do not force-push/rewrite it;
- read current blobs and use optimistic-lock SHAs for direct writes;
- keep runtime owners and canonical docs coherent as the product evolves;
- dated audit documents remain provenance records even when later source supersedes old “remaining” statements.

## Build environment

- package/application ID: `com.anurag9000.forestrun`
- minimum Android API: 24
- compile/target API: 36
- Android source target: Java/Kotlin 17 bytecode
- CI runtime: Java 21
- canonical orientation: **landscape**
- current product version: `1.0.0` / version code `1`
- release credentials: external Gradle properties/environment variables only; never commit real signing secrets

Typical host commands:

```bash
python3 -m unittest discover -s scripts -p 'test_*.py'
./gradlew compileDebugKotlin compileDebugUnitTestKotlin compileReleaseKotlin compileDebugAndroidTestKotlin
./gradlew testDebugUnitTest
./gradlew lintDebug lintRelease
./gradlew assembleDebug assembleDebugAndroidTest bundleRelease
```

Canonical release preparation from an exact clean `origin/main` candidate:

```bash
bash scripts/prepare_main_release.sh
```

Expected Android outputs include the debug APK, instrumentation APK, release AAB, and R8 mapping under the standard `app/build/outputs/` paths.

## Documentation map

| Document | Purpose |
|---|---|
| [`docs/GAME_DESIGN.md`](docs/GAME_DESIGN.md) | Product mechanics and design rules |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Runtime ownership and architectural boundaries |
| [`docs/FOREST_JOURNAL.md`](docs/FOREST_JOURNAL.md) | Persistent memory/collection projection contract |
| [`docs/CREATIVE_DIRECTION.md`](docs/CREATIVE_DIRECTION.md) | Final art/animation/audio/haptic direction |
| [`docs/STORE_LISTING.md`](docs/STORE_LISTING.md) | Canonical store copy and public brand brief |
| [`docs/ACCESSIBILITY.md`](docs/ACCESSIBILITY.md) | Accessibility semantics and acceptance boundary |
| [`docs/ENCOUNTER_CATALOGUE.md`](docs/ENCOUNTER_CATALOGUE.md) | Complete nineteen-family encounter contract |
| [`docs/PERFORMANCE.md`](docs/PERFORMANCE.md) | Performance evidence protocol |
| [`docs/DEVICE_ACCEPTANCE.md`](docs/DEVICE_ACCEPTANCE.md) | Physical candidate evidence contract |
| [`docs/HUMAN_ACCEPTANCE.md`](docs/HUMAN_ACCEPTANCE.md) | Gameplay/accessibility/presentation human review |
| [`docs/RELEASE.md`](docs/RELEASE.md) | Release procedure/checklist |
| [`docs/RELEASE_READINESS.md`](docs/RELEASE_READINESS.md) | Final evidence cross-binding/readiness gate |
| [`docs/STORE_EVIDENCE.md`](docs/STORE_EVIDENCE.md) | Candidate-bound graphics/metadata/screenshots |
| [`docs/SUPPLY_CHAIN_AND_SBOM.md`](docs/SUPPLY_CHAIN_AND_SBOM.md) | Dependency/SBOM boundaries |
| [`docs/SECURITY_AND_LICENSING_GOVERNANCE.md`](docs/SECURITY_AND_LICENSING_GOVERNANCE.md) | Security/licensing decision gates |
| [`PRIVACY.md`](PRIVACY.md) | Source-backed privacy behavior |
| [`docs/AUDIT_LEDGER.md`](docs/AUDIT_LEDGER.md) | Historical remediation ledger |

## Canonical runtime direction

- **Branch:** `main`
- **Orientation:** fixed landscape by product/source design
- **Biomes:** 5
- **Encounter families:** 19
- **Tracked relationship families:** 6
- **Garden catalogue:** 9
- **Wardrobe styles:** 8
- **Bloom:** 8 Seeds → 6-second active window
- **Failure flow:** run → Rest → Garden → remembered next run
- **Release model:** exact candidate, external signing, candidate-bound evidence; no committed secrets
