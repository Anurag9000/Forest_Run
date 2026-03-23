# Forest_Run — Active Implementation TODO

This file is the active non-hardware implementation queue.

Use it as the day-to-day burn-down list for product work that still remains from the dream spec.

Rules:

- This file excludes hardware-only validation and device-proof retuning. Those stay in `docs/DEVICE_ACCEPTANCE_CHECKLIST.md`.
- Remove an item from this file only when it is fully implemented, integrated, and verified.
- Do not mark items done for partial wiring, placeholders, or scaffolding.
- Every tranche should ship complete, professional, deployable code for that slice.
- No half-baked stubs.
- Update affected docs before implementation work.

Completion standard for removing an item:

- the runtime behavior is fully implemented
- the authored content for that slice is present, not placeholder-only
- the affected tests/build are green
- the docs were updated to reflect the new truth

## 1. Onboarding And Session Arc

- [ ] Tighten the first 20–30 seconds of onboarding/readability so the early game teaches clearly without feeling sterile.
- [ ] Finish the restorative rest/failure scene so the player’s fall, pause, and recovery feel emotionally complete.
- [ ] Expand reflection range after runs so rest and Garden do not feel mechanically repetitive.
- [ ] Finish long-term sanctuary feel so startup, run, rest, and Garden read as one authored emotional loop.
- [ ] Deepen visible homecoming consequences from mood, route tier, bonds, repeated kindness, repeated harm, and peaceful-biome state.

## 2. Entity Closure

### Flora

- [ ] Finish Lily of the Valley’s final lure/trap readability design.
- [ ] Finish Hyacinth’s final rhythm identity.
- [ ] Finish Eucalyptus’s final high-threat fairness design.
- [ ] Finish Vanilla Orchid’s final safe-thread clarity.
- [ ] Finish Cactus’s broader memory/cosmetic payoff.

### Trees

- [ ] Finish Weeping Willow’s scenic-dominance behavior without cluttering readability.
- [ ] Finish Jacaranda’s final canopy/petal spectacle design.
- [ ] Finish Bamboo’s final precision identity.
- [ ] Finish Cherry Blossom’s final gust-pressure distinction.

### Birds

- [ ] Finish Duck’s clearer quack/event payoff.
- [ ] Finish TitGroup’s stronger rhythm payoff.
- [ ] Finish ChickadeeGroup’s final charm/readability payoff.
- [ ] Finish Owl’s deeper repeated-memory/night payoff.
- [ ] Finish Eagle’s stronger dramatic cue understanding.

### Animals

- [ ] Finish Cat’s deeper repeated-friend warmth.
- [ ] Finish Fox’s deeper repeated-memory charm.
- [ ] Finish Wolf’s deeper spare payoff.
- [ ] Finish Hedgehog’s final fairness logic and payoff.
- [ ] Finish Dog’s truly memorable buddy-mode payoff.

### Cross-Family

- [ ] Make flavor text and mercy feedback land reliably in ordinary play, not only in showcase scenarios.

## 3. Emotional Systems

- [ ] Expand `PersistentMemoryManager` / `SaveManager` into richer repeated-history presentation and unlock state.
- [ ] Broaden `RelationshipArcSystem` positive familiarity so live encounters feel more personally warm.
- [ ] Broaden `RelationshipArcSystem` negative consequence so disappointment, fear, caution, and tension are more specific.
- [ ] Add broader milestone rewards that feel relational, not merely numeric or cosmetic.
- [ ] Broaden `PacifistTracker` / `PacifistPresentation` world-state consequence beyond current route text and peaceful-biome signs.
- [ ] Broaden `CostumeManager` surfacing so costume unlocks matter emotionally outside the wardrobe UI.
- [ ] Finish `GhostPlayer` logic/policy so it is consistently helpful and never confusing in dense play.
- [ ] Make the world’s “opinion” of the player feel stronger and more legible across run, rest, and Garden.

## 4. Authored Return, Fragment, Quote, And Dialogue Coverage

- [ ] Expand `ReturnMomentsSystem` for more mercy-heavy combinations.
- [ ] Expand `ReturnMomentsSystem` for more clean-streak combinations.
- [ ] Expand `ReturnMomentsSystem` for more bond-milestone combinations.
- [ ] Expand `ReturnMomentsSystem` for more absence + mood + bond combinations.
- [ ] Expand `ReturnMomentsSystem` for more repeated-negative-history combinations.
- [ ] Expand `StoryFragmentSystem` creature-thought coverage.
- [ ] Expand `StoryFragmentSystem` weather-linked reflection coverage.
- [ ] Expand `StoryFragmentSystem` biome-linked reflection coverage.
- [ ] Expand `StoryFragmentSystem` memory-page variety and unlock depth.
- [ ] Broaden `RestQuoteManager` quote pools across universal, biome, killer, route, and emotional-state contexts.
- [ ] Broaden `DialogueBubbleManager` trigger coverage and authored line variety.
- [ ] Broaden `FlavorTextManager` and `RunFlavorPresentation` coverage so ordinary-play authored voice stays rich across more situations.
- [ ] Keep all new writing brief, intimate, suggestive, restrained, and non-expository.

## 5. Bloom Completion

- [ ] Finish the final authored Bloom polish pass beyond the current stronger visual state.
- [ ] Deepen nearby-world Bloom reaction where it materially improves the power-state fantasy.
- [ ] Finish Bloom’s distinct music/SFX identity.
- [ ] Finish Bloom’s top-end power-fantasy presentation so it feels fully unforgettable.

## 6. Visual And Audio Production

- [ ] Replace remaining procedural/parallax-heavy scenery with bespoke scenic background art.
- [ ] Deepen ambient life density: petals, leaves, fireflies, mist variation, and living-forest motion.
- [ ] Deepen world wind drama and secondary motion.
- [ ] Strengthen heroine hair/clothing secondary motion.
- [ ] Finish richer authored lighting identity across world states.
- [ ] Expand `LeitmotifManager` from playback shaping into fuller authored motif treatment across menu, Garden, run, Bloom, and rest.
- [ ] Finish final cinematic polish across visuals and audio.

## 7. Release-Ready Productization

- [ ] Prepare final store-ready art and screenshots.
- [ ] Finish packaging and publishing readiness.
- [ ] Finish release-doc cleanup once the remaining product work is actually complete.

## 8. Documentation Hygiene

- [ ] Keep touched docs and stale comments synchronized with current runtime truth.
- [ ] Remove items from this file only when they are fully complete.
- [ ] Keep `docs/TODO_MATRIX.md` as the exhaustive ledger and this file as the active queue.
