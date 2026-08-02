# Forest Run — Nonterminal Collision Outcome Extraction Audit

Date: 2026-08-02  
Repository: `Anurag9000/Forest_Run`  
Canonical branch: `main`

## Completed scope

This tranche extracted the ordered `STUMBLE` and `MERCY_MISS` outcome sequences from the `GameView` collision branches.

Implemented directly on `main`:

1. `NonTerminalCollisionOutcomeCoordinator.kt`
   - immutable STUMBLE and MERCY_MISS input records;
   - fakeable relationship, authored-feedback, and live-effect seams;
   - Android relationship and presentation adapters;
   - canonical STUMBLE and MERCY_MISS ordering;
   - callback-based selected-entity deactivation.
2. `GameView.kt`
   - one long-lived nonterminal coordinator;
   - one private live-effect adapter;
   - one STUMBLE delegation call;
   - one MERCY_MISS delegation call;
   - no direct relationship, presentation, audio, haptic, camera, particle, flash, ghost-suppression, or Player-stumble calls in those result branches.
3. `NonTerminalCollisionOutcomeCoordinatorTest.kt`
   - five pure ordering and data-flow tests.
4. `NonTerminalCollisionFeedbackPresenterIntegrationTest.kt`
   - three Robolectric production-presenter tests.
5. `test_nonterminal_collision_outcome_contract.py`
   - ten source ownership, data-flow, adapter, and ordering contracts.
6. `NONTERMINAL_COLLISION_OUTCOMES.md`
   - canonical ownership, ordering, tests, evidence, and remaining-debt specification.
7. `ARCHITECTURE.md`
   - synchronized collision outcome ownership and known debt.

## Preserved STUMBLE sequence

The coordinator retains:

```text
run-level hit accounting
→ persistent known-killer relationship hit
→ 0.9-second ghost suppression
→ Player stumble transition
→ biome-dominant flash
→ nonlethal hit SFX
→ hit camera shake
→ medium haptic
→ authored STUMBLE bubble/flavor copy
→ selected-entity deactivation
```

The relationship write is skipped when persistence is disabled or killer identity is unavailable. Every other local mechanic and presentation step still runs.

## Preserved MERCY_MISS sequence

The coordinator retains:

```text
green mercy flash
→ mercy-miss SFX
→ double-tap haptic
→ authored mercy bubble/flavor copy
→ mercy stars at Player center
→ mercy camera shake
```

Authored copy still receives entity type, route tier, mercy hearts, and kindness chain from the authoritative live game state.

## Large-owner preservation evidence

The pre-integration `GameView.kt` blob was:

```text
d1d3df1fb5bb2dfb707fc7fe39a771ece63733cc
```

The production integration commit was:

```text
176a2ddc179045201431c9dddc1756f368df07ce
```

Its intended executable changes were limited to:

1. construct `NonTerminalCollisionOutcomeCoordinator` once;
2. replace inline STUMBLE work with one typed delegation and final deactivation callback;
3. replace inline MERCY_MISS work with one typed delegation;
4. add the private `GameViewNonTerminalCollisionEffects` adapter.

Immediate patch inspection also found four unintended punctuation-only rendering-comment edits. They were restored in:

```text
597b4fd7a9df6057c5f0a5f3a3e33834da66fca9
```

That preservation commit was verified as exactly four additions and four deletions. Its patch changed only:

```text
// 3b:  → // 3b.
// 4:   → // 4.
// 5:   → // 5.
// 5: World-space → // 5. World-space
```

No executable statement changed in the cleanup commit.

## Validation performed

Executed in this session:

- focused production Kotlin compilation with Android/game stubs;
- executable fake-seam harness with output `nonterminal collision checks passed`;
- focused compilation of the checked-in unit-test surface using fake JUnit declarations;
- Python syntax compilation for the source contract;
- representative source-contract execution;
- correction of a real contract expectation: both outcome types correctly reset the shared flash timer, so the effect adapter contains two timer assignments;
- exact branch and adapter source inspection;
- exact large-owner compare and preservation-only compare.

Added but not executed through Android Gradle in this session:

- `NonTerminalCollisionOutcomeCoordinatorTest`;
- `NonTerminalCollisionFeedbackPresenterIntegrationTest`;
- the full exact-source Python contract against a checked-out repository.

No complete exact-head unit, Robolectric, lint, release-build, packaging, connected-emulator, or physical-device result is claimed.

## Debt removed

The prior architecture item “STUMBLE and MERCY_MISS still own presentation and effect sequencing inline in `GameView`” is closed at the result-branch ownership level.

Those branches now capture inputs and delegate. The ordering and authored-presentation responsibilities are testable without constructing `GameView`.

## Remaining debt

1. The complete collision-result `when` dispatcher still lives inside `GameView`.
2. Live STUMBLE and MERCY_MISS effects are centralized but remain implemented by a private `GameView` inner adapter.
3. Immediate terminal HIT impact still directly coordinates Player, ghost, camera, audio, music, and haptics.
4. Terminal persistence remains non-transactional across SharedPreferences and asynchronous ghost storage.
5. `GameView` remains a large coordinator despite the reduced result branches.
6. Exact-head Android and physical release evidence remains outstanding.

## Classification

This is a behavior-preserving architectural extraction with explicit order and ownership contracts. It does not complete release acceptance. Forest Run remains a feature-rich alpha until exact-head execution, physical acceptance, signing, delivery, visual, accessibility, and policy gates are complete.
