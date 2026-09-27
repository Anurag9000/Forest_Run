# Forest Run — Whole-software continuation and closure evidence (2026-09-27)

## Scope and independent reference

This is a source-specific delta audit and a consolidated closure matrix against the user's authored Forest Run requirements. It reuses the established inventory and mechanism ledger in `docs/AUDIT_LEDGER.md` and `docs/audits/2026-09-20_whole_software_closure.md`, reopens their earlier completeness judgments when newer evidence contradicts them, and newly inspects live engine/frame and geometry pathways. It must not be read as a fresh human visual inspection of every binary or a new physical-device test.

## Reconciled repository state

Source-bearing commit: `505d3679dc8d55b2ccb8413b6a8e3d1bde5886e1`. Tree evidence: 841 tracked files, 187 production Kotlin files, 227 JVM/Robolectric Kotlin test files, 8 instrumentation test files, 123 Python tests, 53 non-test Python scripts, 29 sprite PNGs, 15 audio OGGs, 61 dated audit documents, 3 workflows. Main is the sole branch; no open PRs. This document increases the count by one in its final documentation commit.

The product is a native, lightweight Android/SurfaceView/Canvas authored game; the core chain is Willow/Home -> guided run -> five ordinary biomes -> 19 unique encounter families -> Seed/8-Seed Bloom -> terminal Rest -> persistent Garden, wardrobe, relationships, Forest Journal and ghost memory. Four of the 28 deterministic scenarios include authored input scripts. There are no trainable models, data-training sets, GPU training loops or training orchestration requirements applicable to this repository.

## Current coverage matrix

| Layer | Authoritative owner and implementation | Test/evidence | Honest status |
| --- | --- | --- | --- |
| Runtime and input | `GameView`, `GameThread`, `InputHandler`, `Player`, `GameStateManager` | JVM source/physics tests, API-35 connected, per-frame speed regression | Implemented; exact-final-head automation required; physical feel OPEN |
| Encounters/fairness | `EntityManager`, `EntityFactory`, 19 species, `EncounterDirector`, `SpawnPacing`, `ReadabilityProfile` | outcome/arbitration tests, authored traces, numeric origin-gap grid | Implemented; pairwise action/telegraph feasibility OPEN |
| Bloom and rewards | `GameStateManager`, `BloomWorldReaction`, `SeedOrbManager` | saturation, lifecycle, collision exclusivity and source regression | Source addressed; visual/physical feel OPEN |
| Persistent state | `SaveManager`, atomic Garden purchase, outcome/ghost recovery, relationship/route stores | concurrency, idempotence, recovery and schema tests | Source addressed; installed-candidate recovery evidence OPEN |
| UI and accessibility | Menu/Garden/Rest/HUD/Journal, `SafeContentTransform`, accessibility semantic providers | JVM transform/ownership tests and API-35 instrumented tests | Source addressed; real TalkBack/Switch Access and cutout devices OPEN |
| Art, music and feedback | sprite/scene owners, SFX/music/haptics assets and adapters | asset-reference, package, integrity/provenance tooling | Sources present; final sprite/audio/haptic creative review and rights OPEN |
| Build and release | Gradle, host/connected workflows, signing/package/Play scripts, strict evidence readers | current-source tests, candidate-bound CI and evidence validators | Automated tests conditional on final exact-head result; external release OPEN |
| ML/training/GPU | No applicable retained trainable surface | both N/A applicability checks | NOT APPLICABLE; do not invent training jobs |

## New defect taxonomy and tested corrections

1. **Frame-state drift:** distance/score formerly used old speed while entities/parallax used newly accelerated/debuffed speed. One pre-frame captured speed now serves both, with 200-frame and debuff-boundary tests.
2. **Geometry publication interleaving:** surface dimensions and insets formerly mutated outside the runtime lock. Both callbacks now hold the existing monitor through transform publication; the source-contract test protects this.
3. **Unjustified proof label:** `SpawnFairnessEnvelope.isFiniteAndFair` certified only its declared origin-spacing formula. It is renamed to `isFiniteAndWithinDeclaredBounds` and physical action recovery is not claimed from a 0.39-second nominal floor. This flags genuinely missing feasibility evidence, not a new proved impossible encounter.
4. **Prior-turn lifecycle corrections retained:** near-contact mercy is provisional until actual safe passage; same-entity HIT/STUMBLE can supersede it; Bloom conversion is exclusive; terminal frames end after the committed summary rather than recording a new ghost frame or granting late progress.
5. **Previous release and concurrency corrections retained:** strict typed/bounded evidence readers, canonical candidate identities, single-lock earned Seed + Garden transactions, actual posted-frame telemetry, scenario population/timed input fixes, entity-identity Bloom reactions.

No undocumented orphan deletion or speculative ML/cloud system was introduced. No final-rights approval or binary-frame correctness was inferred from an asset filename or digest.

## Verification and closure

The last fully observed exact-head Android pass before this delta was run `36252747722` at `a69945cb`. As of authoring, the source-bearing `505d3679` Android run `36310526978` was still in progress, while its Python tooling and N/A-training workflows had passed. The document-only final HEAD needs its own exact-head conclusion; do not read this dated observation as a forecast.

A = substantial source implementation and scoped bug repair, but no honest universal no-bugs/perfect-mechanics claim. B = exact-head automated closure **conditional** on workflow completion. C = physical/human/creative/legal/signed-install evidence **OPEN**. D = production Play/governance/store acceptance **OPEN**. In particular, passing the declared 780 px minimum origin-gap test does not establish physically fair mixed encounter sequences; these require the real player/enemy geometry/trajectory and action-switch feasibility sweep plus human/device testing.
