# Forest Run product-completion continuation — 2026-08-15

This record continues `2026-08-15_product_completion_sweep.md` after the first exact-head connected-device validation exposed one stale instrumentation contract. It records direct-to-`main` source work only. It is not physical-device, human, store, legal, signing, rights, or final-release acceptance evidence.

## 1. Connected accessibility failure: root cause and repair

The host/release job for `1ee0d496...` was green, but API-35 connected validation failed deterministically in `AccessibilityVirtualNodeInstrumentedTest.installedGameViewPublishesAndRoutesVirtualAccessibilityNodes`.

The installed `GameView` accessibility root exposed four Menu children while the old test still required three. Production source was correct: the stable Menu semantic order is:

1. Begin forest run;
2. Open Garden;
3. Open feedback settings;
4. Open Forest Journal.

The repair did not weaken the assertion to a new count only. The connected contract now requires the Journal node to exist, describe itself as `Open Forest Journal`, be clickable on Menu, disappear while Settings owns the semantic tree, and reappear after Settings closes.

The lower-level `GameAccessibilitySemanticsTest` already protects the same stable ID/focus ordering, so the device test now verifies the installed provider rather than contradicting it.

## 2. Journal lifecycle state is now correctly ephemeral

Journal filters remain:

- All;
- Progress;
- Bonds;
- Memories;
- Families.

The selected filter is UI state, not progression. `ForestJournalActivity` now saves only the selected enum name into `savedInstanceState` and restores it on recreation. Missing or unknown restored values fail safely back to `All`.

No SharedPreferences/save-game key was added.

`ForestJournalLifecycleInstrumentedTest` selects `Memories`, recreates the real Activity, requires `Memories Journal section, selected` after recreation, and requires the game-progress preference map to remain unchanged.

`scripts/test_forest_journal_lifecycle_contract.py` additionally rejects persistence APIs from the Activity and locks the Bundle-only ownership rule.

## 3. Garden purchase feedback parity is live and remains above persistence

`GardenPurchaseFeedbackPolicy` is now the single success-to-feedback rule used by both Garden input modalities. `GardenPurchaseInteractionCoordinator` applies it for result-bearing callers:

- invoke the authoritative purchase action exactly once;
- return its exact `GardenPurchaseResult` unchanged;
- emit exactly one growth-feedback cue only for `PURCHASED`;
- emit no growth cue for `INVALID_REQUEST`;
- emit no growth cue for `NOT_NEXT_UNLOCK`;
- emit no growth cue for `CATALOGUE_COMPLETE`;
- emit no growth cue for `INSUFFICIENT_SEEDS`;
- emit no growth cue for `WRITE_FAILED`.

Touch purchases in `GardenScreen` now go through a `GardenPurchaseInteractionCoordinator` built from `persistenceFacade::purchaseNextGardenPlant` and `HapticManager::gardenGrowth`. The existing authoritative result still drives local Seed/unlock state, card animation, and particle feedback exactly as before.

Virtual-accessibility purchases continue to use the existing validated `GameView` Boolean purchase callback, but `LiveGameAccessibilityActions` now applies the same `GardenPurchaseFeedbackPolicy` to that callback's success result and defaults its semantic feedback action to `HapticManager::gardenGrowth`. Wrong semantic actions and rejected purchases emit no cue.

This closes modality parity without moving haptics into persistence, without a global event bus, and without making `GardenPurchaseResult.purchased` perform hidden side effects.

Regression coverage includes:

- `GardenPurchaseInteractionCoordinatorTest` across every `GardenPurchaseStatus`;
- `LiveGameAccessibilityGardenFeedbackTest` for accessibility success, rejection, and wrong-action suppression;
- `scripts/test_garden_purchase_feedback_contract.py`, which requires both live presentation paths and forbids Garden haptic/coordinator ownership inside `GardenPurchaseManager` or `ApplicationPersistenceFacade`.

Persistence therefore remains truthful: a committed purchase determines success, while presentation decides how that success feels.

## 4. Garden catalogue presentation duplication is closed

`GardenEconomy` is now the sole source of Garden progression order, full names, compact card names, and Seed costs.

`GardenScreen` keeps only local visual metadata that genuinely belongs to the Canvas presentation: card colour, fallback emoji, and sprite ordering. It no longer stores a second copy of compact names or costs.

The live card loop resolves the canonical entry with `GardenEconomy.plantForIndex(i)` and renders `economy.compactName` and `economy.seedCost`. The screen also fails fast if either its visual catalogue size or sprite catalogue size diverges from `GardenEconomy.catalogueSize`.

`scripts/test_garden_catalogue_contract.py` now protects the completed ownership model by requiring:

- exactly nine contiguous canonical economy entries with unique names;
- canonical README and Game Design tables;
- `GardenScreen` import/use of `GardenEconomy`;
- visual/sprite catalogue-size alignment with the canonical catalogue;
- card resolution through `GardenEconomy.plantForIndex(i)`;
- rendering through `economy.compactName` and `economy.seedCost`;
- absence of local `GardenPlant.name` / `GardenPlant.seedCost` fields;
- purchase routing through the Garden interaction coordinator and application persistence facade rather than direct Garden/Seed writes.

This closes the previous presentation-maintainability debt without changing purchase indices, visual order, animation, touch geometry, sprites, colours, emoji, wardrobe behavior, or persistence semantics.

The corresponding Robolectric boundary test was also reconciled after this deduplication. `GardenScreenBoundaryTest` no longer tries to read removed presentation-owned `GardenPlant.name` / `seedCost` fields; instead it verifies visual-catalogue cardinality against `GardenEconomy`, contiguous canonical indices, nonblank/unique canonical names, and positive canonical prices while retaining the existing frame/tap/purchase boundary coverage.

## 5. Collision haptic ports are semantic at the domain boundary

The final source-maintainability debt from the earlier sweep is now closed.

`TerminalHitImpactEffectSink` no longer exposes `longPulse`; its haptic port is only `terminalImpactHaptic`. `NonTerminalCollisionEffectSink` no longer exposes `mediumPulse` or `doubleTap`; its haptic ports are only `stumbleImpactHaptic` and `mercyAcknowledgementHaptic`.

`TerminalHitImpactCoordinator` and `NonTerminalCollisionOutcomeCoordinator` therefore describe collision intent rather than device-duration primitives. `LiveCollisionEffects` maps those semantic ports to the same pre-existing runtime callbacks, so effect ordering, suppression durations, physical haptic waveform selection, game state, and persistence are unchanged.

The large `GameView` owner was deliberately not rewritten for naming alone. Its narrow adapter-construction callback labels (`longPulseAction`, `mediumPulseAction`, `doubleTapAction`) still describe the physical callbacks supplied to `LiveCollisionEffects`; those names are now confined below the domain interface rather than leaking into collision orchestration.

Focused Kotlin tests now assert semantic haptic events, while `scripts/test_terminal_hit_impact_contract.py` and `scripts/test_nonterminal_collision_outcome_contract.py` require the semantic ports and reject reintroduction of the old physical methods into the coordinator interfaces.

The Git-object update that made this atomic temporarily changed those two Python contract file modes; a follow-up metadata-only fast-forward restored their historical `100644` modes. No source content was reverted.

## 6. Candidate-bound store graphics and release entrypoint are fail-closed

The store-metadata evidence path already used the repository's strict JSON admission policy, but the graphics manifest verifier still had a materially weaker parser/admission boundary. That asymmetry is now closed.

`scripts/verify_store_graphics.py` now:

- parses the manifest through `strict_json` with duplicate-key, UTF-8/BOM, depth, non-finite-number, and byte-size protections;
- requires the exact top-level schema and exact source/output entry schemas;
- requires the exact 40-hex release-candidate SHA and generator identity;
- admits the graphics directory and every evidence file with `lstat`, rejecting symbolic links and non-regular substitutions;
- bounds manifest, source-asset, and generated-graphic byte sizes;
- reads each evidence file once and rejects size/mtime/inode changes observed across the read;
- hashes the same admitted bytes used for evidence checks;
- verifies the exact source-asset set, source byte counts, SHA-256 values, and duplicate absence;
- verifies the exact generated-output set, dimensions, image mode, byte counts, SHA-256 values, and duplicate/basename safety;
- rejects unmanifested, missing, symbolic-link, and non-file entries in the graphics directory;
- decodes and verifies PNGs from the already-admitted byte stream rather than reopening a different path instance.

`scripts/test_verify_store_graphics.py` now covers the normal candidate plus adversarial duplicate JSON keys, schema smuggling, missing required source assets, source symlinks, generated-output symlinks, a symlinked graphics directory, stale source evidence, output tampering, duplicate/incomplete manifest entries, wrong dimensions, and wrong mode evidence.

The supported release-preparation boundary is also explicit and mechanically guarded. `scripts/prepare_main_release.sh` remains the canonical candidate entrypoint: it verifies the exact local/remote `main` candidate, source assets/provenance, candidate-bound graphics, listing parity, candidate-bound metadata, invokes the lower-level Play preparer, verifies the newly produced summary, and finally rechecks the candidate against local and remote `main`.

`docs/RELEASE.md` no longer advertises direct invocation of `scripts/prepare_play_release.py`; it labels that file as the lower-level helper it is and names `bash scripts/prepare_main_release.sh` as the only supported candidate-preparation entrypoint. `test_prepare_main_release_contract.py` locks this documentation rule so the old bypass-prone command cannot silently return.

A final documentation-to-suite reconciliation also removed the stale hard-coded statement that the ordinary connected gate contained exactly fourteen tests. The current non-`@LargeTest` suite contains sixteen tests, including the later accessibility and Journal lifecycle coverage. The release contract now states the durable invariant instead: execute the complete ordinary connected suite and require zero failures, zero errors, and zero skips. `test_prepare_main_release_contract.py` rejects reintroduction of the stale `fourteen` cardinality while requiring those semantic gate statements.

This hardening does not manufacture store approval, signing credentials, final creative acceptance, or provenance approval. It only ensures that repository-generated release evidence fails closed and that operators are directed through the strongest source-controlled boundary.

## 7. Current source-addressable remainder

No known correctness, missing-player-feature, or justified source-architecture item remains from the product-completion queue covered by this continuation.

No missing gameplay state machine, Bloom system, collision arbiter, persistence layer, relationship engine, Garden economy, visitor system, Journal persistence store, accessibility semantic architecture, ghost system, runtime ML model, cloud/account system, advertising system, or multiplayer layer is justified by the current product goals.

Compiler warnings that remain are not being promoted into invented product debt. In particular, ghost-ordinal `PlayerState.BLOOM` compatibility and platform-compatibility accessibility APIs must not be rewritten merely to silence deprecation warnings. Small unused-parameter or redundant-initializer warnings are nonblocking maintainability observations unless a future substantive edit naturally removes them.

## 8. Still external / candidate-bound

The following remain intentionally unresolved by source work:

- representative physical hardware evidence;
- performance/thermal/battery acceptance on real devices;
- human gameplay/fairness/accessibility acceptance;
- final art/animation/audio/haptic creative approval;
- rights/provenance/licence/attribution decisions;
- source-code licence decision;
- private vulnerability-reporting setting/channel verification;
- stable HTTPS privacy-policy publication;
- Play Console ownership and declarations;
- production signing/upload identity;
- final screenshots/store graphics tied to a frozen candidate;
- exact release notes/version/tag and accountable release approval.

These must remain open until real external evidence or owner decisions exist.

## 9. Validation rule and current status

Because each direct-to-`main` commit starts a new candidate SHA, only the final exact-head workflow may be used as completion evidence. Superseded or cancelled runs are useful diagnostics but are not a substitute for a green host/release job and green API-35 connected job on the same final SHA.

At the time this record is committed, the source-completion implementation is closed but final exact-head validation is still pending. This document must not be interpreted as claiming a green candidate until both workflow jobs complete successfully on this document-inclusive `main` SHA.
