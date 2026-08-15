# Forest Run

Forest Run is a native Kotlin Android endless runner built around a custom `SurfaceView` loop and a persistent emotional world. The player begins beneath a willow, crosses five changing biomes, meets nineteen forest families, gathers Seeds, enters Bloom, practices mercy or causes harm, rests when a run ends, and returns to a Garden that remembers what happened.

**Core loop:** willow → five-biome run → Seeds/Bloom/encounters → Rest → changed Garden → remembered next run

## Current source status

The major source-architecture remediation queue is closed on `main`. Input arbitration, Bloom, encounter outcomes, persistence/recovery, relationships, Garden progression, wardrobe, ghosts, accessibility, deterministic scenario evidence, release provenance, dependency/SBOM tooling, store evidence, and candidate-readiness orchestration have explicit runtime owners.

The product also contains a player-facing **Forest Journal** that projects the history already persisted by gameplay: encounter discovery, clean passes, mercy/harm, relationship stages, Garden growth, wardrobe state, route history, peaceful-biome traces, high-score/distance legacy, forest mood, last Rest, durable memory pages, derived collection milestones, and the whole-forest completion capstone. The Journal is read-only and does not create a parallel achievement database.

Source completeness is **not** production acceptance. Final creative approval, rights/licensing decisions, physical-device and human acceptance, production signing, privacy-policy hosting, private vulnerability-reporting configuration, Play Console declarations/delivery, and accountable release approval remain external gates.

## Player experience

### Movement

- quick tap → short jump;
- hold → higher/full jump;
- early release trims upward motion;
- downward swipe → duck;
- gameplay input is state-owned and cancellation-safe across Menu, Garden, Rest, and run transitions.

### Five biomes

1. Meadow
2. Orchard
3. Ancient Grove
4. Dusk Canyon
5. Night Forest

### Nineteen encounter families

**Flora:** Cactus, Lily of the Valley, Hyacinth, Eucalyptus, Vanilla Orchid  
**Trees:** Weeping Willow, Jacaranda, Bamboo, Cherry Blossom  
**Birds:** Duck, Tit, Chickadee, Owl, Eagle  
**Animals:** Cat, Fox, Wolf, Hedgehog, Dog

`EncounterFamilyCatalogue` is the structural authority for the roster and derives biome/scenario/variant/relationship capability from existing owners.

### Seeds and Bloom

- Bloom threshold: **8 Seeds**;
- active window: **6 seconds**;
- Bloom does not freeze ordinary player physics;
- one authoritative active timer;
- incoming Seeds do not restart or extend an active Bloom;
- presentation spans aura, particles, HUD, music, haptic identity, and encounter conversion behavior.

### Encounter outcomes and relationships

Collision arbitration selects exactly one outcome for each encounter interaction. Runtime dispatch separates terminal impact, stumble/mercy consequences, relationship writes, summary persistence, feedback, and state transitions.

Cat, Fox, Wolf, Dog, Owl, and Eagle have persistent relationship arcs. Relationship stages, warm/strained tone, Bond rewards, home presence, return moments, rituals, and selected wardrobe memories persist across runs.

## Forest Journal

The willow menu exposes **MEMORY → FOREST JOURNAL** as both a touch and accessibility action. The Journal uses native Android scrolling/text controls and ephemeral filters for **All**, **Progress**, **Bonds**, **Memories**, and **Families**.

### Run Legacy

- high score and best distance;
- total remembered runs;
- current/dominant forest mood and mood streak;
- Gentle/Steady/Fearful/Reckless run history;
- most recent persisted Rest summary.

### Collection Path

- Forest Families;
- Living Bonds;
- Garden completion;
- Wardrobe completion;
- Peace in Every Biome.

A derived whole-forest capstone combines those five tracks with all three gentle route shapes. It is recalculated from authoritative history rather than latched in a new save flag.

### Garden Sanctuary

The Journal shows all nine canonical Garden entries as **grown**, **next**, or **locked**, plus current Seed balance and whether the one legal next purchase is affordable. Purchasing remains owned by the Garden transaction boundary.

### Path History

- Kind Path count;
- Merciful Path count;
- Peaceful Path count;
- `Every Gentle Shape` when all three have returned home.

### Living Bonds and Memory Pages

The Journal shows relationship stage/tone, Bond milestone and ritual, wearable memories, history marks, and durable story pages. Internal page IDs are converted into player-facing titles, categories, and pattern-specific prose for creature, Rest, Garden, route, biome, weather, repeated-encounter, relationship, return, and Bloom memories.

See [`docs/FOREST_JOURNAL.md`](docs/FOREST_JOURNAL.md).

## Garden economy

`GardenEconomy` owns stable progression order, full/compact names, and Seed costs:

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

The first catalogue entry is the initial Garden state; later purchases are sequential and atomic through `GardenPurchaseManager`.

## Wardrobe

Current styles are Classic, Flower Crown, Vine Scarf, Moon Cape, Bell Charm, Lantern Pin, Sky Sash, and Bloom Ribbon. Unlock/equip state persists across sessions.

## Feedback and presentation

### Audio

Forest Run has state-aware menu/Garden/run/Bloom/Rest music behavior plus jump, landing, Seed, species, mercy, hit, and Bloom-conversion cues with fallback behavior for optional audio assets.

### Haptics

`HapticManager` exposes semantic cues for `lightTick()`, `stumbleImpact()`, `terminalImpact()`, `mercyAcknowledgement()`, `gardenGrowth()`, and `bloomSurge()`. Compatibility wrappers preserve existing behavior while tuning can target meaning rather than raw durations.

### Visual identity

Android-template launcher art is removed. Launcher resources use a Forest Run willow-leaf / Seed / Bloom vector mark with adaptive, monochrome, round, and pre-adaptive coverage. The app palette and cold-launch/system-bar presentation use Forest Run forest/willow/Seed colors.

Final creative direction is in [`docs/CREATIVE_DIRECTION.md`](docs/CREATIVE_DIRECTION.md); human artistic approval and rights clearance remain external.

## Accessibility

The Canvas game exposes a real Android virtual-node hierarchy with stable semantic IDs, typed action validation, touch-aligned geometry, truthful Garden/wardrobe state, coalesced announcements, reduced-motion/audio/haptic controls, and the Journal as a stable Menu accessibility action. The Journal itself uses native semantic views.

See [`docs/ACCESSIBILITY.md`](docs/ACCESSIBILITY.md).

## Persistence and recovery

Persistent state includes high score/best distance, lifetime Seeds, Garden progress, wardrobe state, encounter/pass/spare/hit history, relationship stages/milestones, biome friendship, forest mood/run history, route-tier counts, Rest/last-run summary, memory pages/history marks, return moments, and ghost data/recovery metadata.

`ApplicationPersistenceFacade` is the shared application mutation boundary. Low-level durability remains intentionally separated by storage domain; the project does not claim a fake global transaction across unrelated files/preferences.

## Release and evidence engineering

The repository contains source tooling for Android build/lint/R8/source-immutability checks, deterministic scenarios, performance/input-latency evidence, physical-device acceptance, installed-candidate identity, Play-delivery evidence, human/accessibility acceptance, candidate screenshots and store graphics, dependency/SBOM evidence, native/page-size inspection, release governance, final evidence indexing, and readiness cross-binding.

Canonical evidence-layer documentation:

- [`docs/PERFORMANCE.md`](docs/PERFORMANCE.md) — physical performance and diagnostics;
- [`docs/DEVICE_ACCEPTANCE.md`](docs/DEVICE_ACCEPTANCE.md) — candidate-bound physical/device acceptance;
- [`docs/INSTALLED_CANDIDATE_IDENTITY.md`](docs/INSTALLED_CANDIDATE_IDENTITY.md) — measured installed package/split/signing identity and device matrix;
- [`docs/HUMAN_ACCEPTANCE.md`](docs/HUMAN_ACCEPTANCE.md) — gameplay, TalkBack/accessibility, and presentation review;
- [`docs/RELEASE_GOVERNANCE_EVIDENCE.md`](docs/RELEASE_GOVERNANCE_EVIDENCE.md) — security, licensing, privacy, Play/store, provenance, and accountable approvals;
- [`docs/RELEASE_EVIDENCE_INDEX.md`](docs/RELEASE_EVIDENCE_INDEX.md) — immutable evidence-set indexing and binding;
- [`docs/RELEASE_READINESS.md`](docs/RELEASE_READINESS.md) — final cross-layer readiness gate.

These systems validate evidence supplied to them; they cannot manufacture a real Play upload, human approval, legal rights, production signing identity, or physical-device result.

## Store, privacy, security, and licensing

Canonical product/store copy lives in [`docs/STORE_LISTING.md`](docs/STORE_LISTING.md). Candidate-specific screenshots, metadata, manifests, and release notes are finalized only after a real candidate is frozen.

The source-backed privacy behavior is in [`PRIVACY.md`](PRIVACY.md). A public release still requires the accepted policy to be hosted at stable HTTPS and Play declarations to match the shipping candidate.

Security/licensing governance is fail-closed. The repository must not claim private vulnerability reporting is enabled until the repository setting is actually enabled and verified; source-code licensing is not silently selected; and creative assets, audio, fonts, trademarks, and dependencies require owner-approved rights/notice decisions. See [`SECURITY.md`](SECURITY.md), [`docs/SECURITY_AND_LICENSING_GOVERNANCE.md`](docs/SECURITY_AND_LICENSING_GOVERNANCE.md), and [`docs/CREATIVE_ASSET_PROVENANCE.md`](docs/CREATIVE_ASSET_PROVENANCE.md).

## Remaining external release blockers

- final production art/animation selection and human approval;
- final music/SFX/haptic mastering and hardware acceptance;
- creator/source/licence/attribution review for shipping creative assets;
- owner decision on source/asset/audio/font/trademark/contribution licensing;
- dependency vulnerability/notice review;
- enabling/verifying the chosen private vulnerability-reporting mechanism;
- publishing the accepted privacy policy at stable HTTPS;
- production upload key and Play App Signing configuration;
- Play Console ownership, Data Safety, target audience, content rating, category, regions, support/privacy metadata, and release tracks;
- exact signed candidate artifacts and store delivery;
- candidate-specific screenshots, release notes, production tag, and accountable final approval.

## Development workflow

`main` is the only active development branch and source of truth for this project workflow.

- implementation is committed directly to `main`;
- no routine development PRs/branches;
- preserve published history; do not force-push/rewrite it;
- use current blob SHAs for direct writes;
- keep runtime owners, tests, and canonical docs coherent;
- dated audit documents remain provenance records even when later source supersedes old “remaining” statements.

## Build environment

- application ID: `com.anurag9000.forestrun`
- min API: 24
- compile/target API: 36
- source bytecode: Java/Kotlin 17
- CI runtime: Java 21
- orientation: fixed landscape
- current product version: `1.0.0` / version code `1`
- signing credentials: external Gradle properties/environment only; never commit secrets

Typical host validation:

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

## Documentation map

| Document | Purpose |
|---|---|
| [`docs/GAME_DESIGN.md`](docs/GAME_DESIGN.md) | Mechanics and design rules |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Runtime ownership and boundaries |
| [`docs/FOREST_JOURNAL.md`](docs/FOREST_JOURNAL.md) | Persistent memory/collection projection contract |
| [`docs/CREATIVE_DIRECTION.md`](docs/CREATIVE_DIRECTION.md) | Art/animation/audio/haptic direction |
| [`docs/STORE_LISTING.md`](docs/STORE_LISTING.md) | Canonical public product copy |
| [`docs/ACCESSIBILITY.md`](docs/ACCESSIBILITY.md) | Accessibility architecture and acceptance boundary |
| [`docs/ENCOUNTER_CATALOGUE.md`](docs/ENCOUNTER_CATALOGUE.md) | Nineteen-family encounter contract |
| [`docs/PERFORMANCE.md`](docs/PERFORMANCE.md) | Physical performance evidence protocol |
| [`docs/DEVICE_ACCEPTANCE.md`](docs/DEVICE_ACCEPTANCE.md) | Physical candidate evidence contract |
| [`docs/INSTALLED_CANDIDATE_IDENTITY.md`](docs/INSTALLED_CANDIDATE_IDENTITY.md) | Installed package/split/signing identity |
| [`docs/HUMAN_ACCEPTANCE.md`](docs/HUMAN_ACCEPTANCE.md) | Human gameplay/accessibility/presentation review |
| [`docs/RELEASE_GOVERNANCE_EVIDENCE.md`](docs/RELEASE_GOVERNANCE_EVIDENCE.md) | External governance decision evidence |
| [`docs/RELEASE_EVIDENCE_INDEX.md`](docs/RELEASE_EVIDENCE_INDEX.md) | Final evidence index and digest binding |
| [`docs/RELEASE_READINESS.md`](docs/RELEASE_READINESS.md) | Cross-layer final readiness gate |
| [`docs/RELEASE.md`](docs/RELEASE.md) | Release procedure/checklist |
| [`docs/STORE_EVIDENCE.md`](docs/STORE_EVIDENCE.md) | Candidate-bound screenshots/graphics/metadata |
| [`docs/SUPPLY_CHAIN_AND_SBOM.md`](docs/SUPPLY_CHAIN_AND_SBOM.md) | Dependency/SBOM boundaries |
| [`docs/SECURITY_AND_LICENSING_GOVERNANCE.md`](docs/SECURITY_AND_LICENSING_GOVERNANCE.md) | Security/licensing decision gates |
| [`PRIVACY.md`](PRIVACY.md) | Source-backed privacy behavior |
| [`docs/AUDIT_LEDGER.md`](docs/AUDIT_LEDGER.md) | Historical remediation ledger |

## Canonical runtime direction

- **Branch:** `main`
- **Orientation:** fixed landscape
- **Biomes:** 5
- **Encounter families:** 19
- **Tracked relationship families:** 6
- **Garden catalogue:** 9
- **Wardrobe styles:** 8
- **Bloom:** 8 Seeds → 6-second active window
- **Failure flow:** run → Rest → Garden → remembered next run
- **Release model:** exact candidate, external signing, candidate-bound evidence, no committed secrets
