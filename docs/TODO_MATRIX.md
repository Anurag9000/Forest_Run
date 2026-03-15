# Forest_Run — Exhaustive TODO Matrix

This file is the strict implementation ledger for everything from the original dream specs that is not fully realized yet.

## Recently Closed

- DONE: increased on-screen readability of birds, plants, trees, and animals on phone.
- DONE: tightened spawn pacing so the world no longer opens with long dead stretches.
- DONE: fixed the ghost playback presentation so it no longer reads like a broken duplicate body.
- DONE: made seeds, Bloom meter, Bloom activation, hearts, score, distance, and the Garden hub much more visible in normal play.
- DONE: added readable dialogue bubbles, pacifist rewards, face-state presentation, and clearer owl/eagle telegraphs.
- DONE: added a deterministic acceptance suite with per-family and per-entity verification scenarios, including Bloom, ghost, and rest-loop checks.
- DONE: strengthened Lily, Hyacinth, Eucalyptus, Vanilla Orchid, Weeping Willow, and Bamboo readability/payoff delivery.
- DONE: strengthened Duck, TitGroup, ChickadeeGroup, Cat, Fox, Wolf, Hedgehog, and Dog readability/telegraph/payoff delivery.
- DONE: introduced a canonical persisted `RunSummary` shared by the rest screen and Garden carry-home panel.
- DONE: strengthened Cactus, Jacaranda, Cherry Blossom, Owl, and Eagle staging/payoff delivery.

## Immediate Remaining Product Gaps

- TODO: verify every entity’s unique behavior on actual device and retune any behavior that still fails to read in normal play.
- TODO: finish final device-proofing for the latest cactus, jacaranda, cherry blossom, owl, eagle, and broader bird/animal passes that still need real phone validation.
- PARTIAL: Bloom now has a stronger activation burst, player-following effects, HUD conversion feedback, and environmental reactions; finish the full world-transforming power-event feel.

## Documentation And Canonical Spec TODOs

- DONE: canonical runtime truth is frozen at 5 biomes, 8-seed / 6-second auto-Bloom, and rest-to-Garden failure flow.
- TODO: update every remaining doc section and stale comment to match the canonical truth exactly.

## GDD TODOs

- TODO: full authored garden atmosphere at startup.
- TODO: stronger early-game readability and onboarding.
- TODO: full Bloom spectacle, including stronger transformation feel.
- TODO: softer authored failure presentation with richer reflection.
- PARTIAL: the Garden now has mood-based ambience, sanctuary carry-home framing, and visible bond traces; finish the full restorative scene and startup atmosphere.

## Personality TODOs

- PARTIAL: contextual rest quotes by biome and last killer exist; expand quote pools and trigger richness.
- PARTIAL: `PersistentMemoryManager` exists; expand it into full repeated-encounter payoff, unlock, and presentation architecture.
- DONE: canonical post-run summary payload now exists across rest and Garden.
- PARTIAL: costume overlays and wardrobe flow exist, and Cat/Fox/Wolf relationship milestones can now unlock matching outfits; expand broader repeated-encounter unlock depth and presentation.
- PARTIAL: repeat-killer deja vu lines exist in baseline form; deepen them into stronger visual and narrative payoff.
- DONE: dedicated dialogue bubble system.
- PARTIAL: biome-level friendship bonus baseline exists; deepen it into stronger sanctuary and route-like payoff.
- PARTIAL: stronger spare events and pacifist route feel started; complete broader world-state and feedback.
- DONE: face/eye state system baseline for the heroine.

## Emotional Attachment Expansion TODOs

### Forest Memory Layer

- DONE: added a save-backed `ForestMoodSystem` with run classification into gentle, reckless, fearful, and steady.
- PARTIAL: current tone now affects Garden palette, rest/Garden labeling, sanctuary ambience, and visible carry-home framing; deepen creature warmth and broader presentation response.
- PARTIAL: repeated panic or repeated harm from the same creature now surfaces through cautious sanctuary traces, softer return moments, and Garden caution reflections; deepen broader creature warmth and world-state response.
- PARTIAL: repeated kindness can now brighten the sanctuary through added ambience, bloom patches, and trust traces; deepen the effect and tie it more directly to repeated creature-specific history.
- TODO: ensure the player can feel that the world has formed an opinion about how they play.

### Named Relationship Arcs

- DONE: formal relationship stages now exist for Cat, Fox, Wolf, Dog, Owl, and Eagle.
- DONE: each relationship now has first-impression, recognition, trust, and milestone states.
- PARTIAL: relationship stages now drive dialogue, threat/pass/spare lines, encounter generosity/telegraph tuning, Garden strongest-bond presentation, bonded visitors, sanctuary traces, and milestone keepsake rewards.
- PARTIAL: relationship milestones now unlock named keepsakes and matching Cat/Fox/Wolf costume paths; deepen broader milestone rewards and cosmetics.
- TODO: make repeated positive interactions produce more visible warmth and familiarity.
- TODO: make repeated negative interactions produce stronger tension, fear, disappointment, or caution where appropriate.
- TODO: add milestone rewards that feel relational, not merely numerical.

### Personal Return Moments

- DONE: added baseline first-run-of-day greeting logic, long-absence recognition, rough-run comfort returns, and milestone-sensitive Garden messages.
- PARTIAL: Garden return moments now use bonded visitors and stronger mercy/clean-play/high-score hooks; deepen trigger richness and broader emotional coverage.
- PARTIAL: return moments now bind more deeply to many mercies, clean runs, stronger bonds, and broader emotional-state combinations; continue expanding authored combinations.
- TODO: ensure return sessions feel intimate instead of mechanically identical.

### Quiet Story Fragments

- DONE: short rest quotes now run through a first-class fragment-driven writing layer.
- PARTIAL: rare Garden reflections and unlockable poetic memory pages now exist in baseline form.
- PARTIAL: one-line creature thoughts for bonded creatures now exist in the Garden baseline.
- PARTIAL: weather-linked thoughts and caution-oriented Garden reflections now exist in baseline Garden form; broaden authored coverage further.
- TODO: deepen poetic memory pages so they reveal feeling without lore-dumping.
- TODO: keep fragment writing brief, intimate, and suggestive rather than expository.
- TODO: ensure mystery and emotional residue are preserved through restraint.

## Entity TODOs

### Flora

- PARTIAL: Lily of the Valley glow identity and lure readability are materially stronger; finish device-tuning and stronger seed-trap staging.
- PARTIAL: Hyacinth brush-vs-hit readability is clearer; finish clustered rhythm identity and device validation.
- PARTIAL: Eucalyptus whip/sway read is stronger; finish leaf-drama delivery and final threat tuning.
- PARTIAL: Vanilla Orchid vertical window is more legible; finish final safe-thread staging and device validation.
- PARTIAL: Cactus now has stronger warning staging and repeated-killer flavor; finish device-proofing and broader memory/cosmetic payoff.

### Trees

- PARTIAL: Weeping Willow curtain feel is stronger; finish scenic dominance and obscured-read pressure.
- PARTIAL: Jacaranda now has a clearer petal-curtain read and stronger pass reward; finish full canopy spectacle and device-proofing.
- PARTIAL: Bamboo narrow-gap readability is stronger; finish signature threat identity and device validation.
- PARTIAL: Cherry Blossom now has clearer gust staging and stronger petal-storm identity; finish full gust-pressure delivery and device-proofing.

### Birds

- PARTIAL: Duck low-flight readability and duck cue clarity are materially stronger; finish on-device validation and final cue polish.
- PARTIAL: Tit flock wave readability is materially stronger; finish device-proofing and stronger rhythm payoff.
- PARTIAL: Chickadee erratic altitude feel is materially stronger; finish final clarity/charm tuning.
- PARTIAL: Owl now has stronger alert-ring telegraphing and night-glow mood; finish device-proofing and deeper repeated-memory payoff.
- PARTIAL: Eagle now has a clearer target zone and stronger lock-on read; finish device-proofing and stronger dramatic cue understanding.

### Animals

- PARTIAL: Cat kindness bonus is much more obvious; finish repeated-friend payoff and device validation.
- PARTIAL: Fox mirror jump reads more clearly; finish final telegraph charm and repeated-memory payoff.
- PARTIAL: Wolf howl/charge drama is materially stronger; finish device-proofing and stronger spare payoff.
- PARTIAL: Hedgehog visibility/fairness is materially stronger; finish final tuning on phone.
- PARTIAL: Dog bark projectile and buddy mode are clearer; finish memorable buddy payoff and device validation.

## Architecture TODOs

- PARTIAL: dedicated readability tuning layer now exists via `ReadabilityProfile`, including central spawn pacing plus full flora, tree, bird, and animal sizing/staging/mercy baselines; final device retuning is still required.
- PARTIAL: stronger presentation architecture for ghost UX.
- PARTIAL: persistent encounter memory architecture exists; broaden it into richer payoff and authoring tools.
- DONE: costume overlay architecture baseline exists.
- PARTIAL: pacifist and friendship tracking systems exist; broaden them into full mercy-route architecture.
- DONE: deterministic encounter verification harness now includes broad acceptance scenarios for families, individual entities, Bloom, ghost, and rest-loop testing.
- TODO: deeper authored biome scene system.
- PARTIAL: first-class emotional systems now exist for forest mood, personal return moments, relationship arcs, and quiet story fragments; deepen authored coverage, stronger Garden consequences, and richer payoff.

## Visual And Audio TODOs

- TODO: full bespoke parallax/background artwork.
- TODO: denser petals, fireflies, leaf drift, and living-forest ambience.
- TODO: stronger Bloom audiovisual transformation.
- TODO: complete forest leitmotif treatment across all music states.
- TODO: final polish pass for haptics, SFX timing, and visual juice.

## Release TODOs

- TODO: full manual device verification for all entity behaviors.
- TODO: performance audit on low-end, mid-range, and high-refresh phones.
- TODO: final store-ready art, screenshots, and release pass once gameplay vision is truly met.
