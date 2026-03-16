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
- PARTIAL: Bloom now has a stronger activation burst, player-following effects, world-tint transformation, conversion world bursts, stronger HUD paneling, and a distinct surge haptic; finish real-device proofing and the last authored power-event polish.

## Documentation And Canonical Spec TODOs

- DONE: canonical runtime truth is frozen at 5 biomes, 8-seed / 6-second auto-Bloom, and rest-to-Garden failure flow.
- DONE: formal device acceptance checklist now exists and mirrors deterministic scenario names.
- TODO: update every remaining doc section and stale comment to match the canonical truth exactly.

## GDD TODOs

- PARTIAL: startup now uses shared session-arc copy so the menu and Garden carry the last run forward more intentionally; finish full authored garden atmosphere and launch staging.
- TODO: stronger early-game readability and onboarding.
- PARTIAL: Bloom spectacle now includes a full-screen/world shift, stronger conversion spectacle, and stronger active-state HUD framing; finish final authored polish and device validation.
- PARTIAL: rest now has a softer authored recovery subtitle, carry-home preview, and homeward prompt; finish the full restorative failure scene and richer reflection range.
- PARTIAL: the Garden now has mood-based ambience, sanctuary carry-home framing, visible bond traces, and a stronger arrival line when no special return beat is active; finish the full restorative scene and startup atmosphere.

## Personality TODOs

- PARTIAL: contextual rest quotes by biome and last killer exist; expand quote pools and trigger richness.
- PARTIAL: `PersistentMemoryManager` now also tracks save-backed kindness and tender streaks so repeated mercy or repeated hurt can surface in return moments, fragments, and sanctuary carry-home; expand it further into full repeated-encounter payoff and unlock architecture.
- DONE: canonical post-run summary payload now exists across rest and Garden.
- PARTIAL: costume overlays and wardrobe flow exist, Cat/Fox/Wolf relationship milestones can now unlock matching outfits, and Garden now surfaces stronger unlock messaging; expand broader repeated-encounter unlock depth and presentation.
- PARTIAL: repeat-killer deja vu lines exist in baseline form, and repeated tender history now changes return moments and sanctuary carry-home; deepen them into stronger visual and narrative payoff.
- DONE: dedicated dialogue bubble system.
- PARTIAL: biome-level friendship bonus baseline exists; deepen it into stronger sanctuary and route-like payoff.
- PARTIAL: encounter-level history now surfaces through tracked Cat/Fox/Wolf/Dog cues instead of fallback one-liners; expand the same depth across more creatures and normal-run payoff.
- PARTIAL: stronger spare events and pacifist route feel started; complete broader world-state and feedback.
- DONE: face/eye state system baseline for the heroine.

## Emotional Attachment Expansion TODOs

### Forest Memory Layer

- DONE: added a save-backed `ForestMoodSystem` with run classification into gentle, reckless, fearful, and steady.
- PARTIAL: current tone now affects Garden palette, rest/Garden labeling, sanctuary ambience, and visible carry-home framing; deepen creature warmth and broader presentation response.
- PARTIAL: repeated panic or repeated harm from the same creature now surfaces through cautious sanctuary traces, softer return moments, Garden caution reflections, and save-backed tender streaks; deepen broader creature warmth and world-state response.
- PARTIAL: repeated kindness can now brighten the sanctuary through added ambience, bloom patches, trust traces, warmer rest fragments, and dedicated `Stayed Gentle` returns; deepen the effect and tie it more directly to repeated creature-specific history.
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
- PARTIAL: Garden return moments now use bonded visitors and stronger mercy/clean-play/high-score hooks, including milestone-bond warmth, Bloom-linger, and stronger absence-sensitive returns.
- PARTIAL: return moments now bind more deeply to many mercies, clean runs, stronger bonds, Bloom-heavy runs, repeated tenderness, and repeated kindness streaks; continue expanding authored combinations.
- DONE: rest flow can now preview the likely Garden return beat without mutating save state, so return continuity can be written before the actual hub transition.
- TODO: ensure return sessions feel intimate instead of mechanically identical.

### Quiet Story Fragments

- DONE: short rest quotes now run through a first-class fragment-driven writing layer.
- PARTIAL: rare Garden reflections and unlockable poetic memory pages now exist in baseline form.
- PARTIAL: one-line creature thoughts for bonded creatures now exist in the Garden baseline.
- PARTIAL: weather-linked thoughts and caution-oriented Garden reflections now exist in baseline Garden form, alongside milestone-gentleness, repeated-kindness, and Bloom-afterglow pages; broaden authored coverage further.
- TODO: deepen poetic memory pages so they reveal feeling without lore-dumping.
- TODO: keep fragment writing brief, intimate, and suggestive rather than expository.
- TODO: ensure mystery and emotional residue are preserved through restraint.

## Entity TODOs

### Flora

- PARTIAL: Lily of the Valley now stages its lure and lower seed-trap more explicitly through glow, stem-guidance, and pass reward; finish device-tuning and final trap readability.
- PARTIAL: Hyacinth now reads more like a single clustered rhythm unit with pulse rings and stronger payoff; finish device validation and final rhythm feel.
- PARTIAL: Eucalyptus now sells the whipping leaf path more clearly with layered gust guides and stronger pass reward; finish final threat tuning on device.
- PARTIAL: Vanilla Orchid now stages its two hazard zones and safe thread much more explicitly; finish device validation and final live-play clarity.
- PARTIAL: Cactus now has stronger warning staging, history-aware payoff text, and a clearer reward beat; finish device-proofing and broader memory/cosmetic payoff.

### Trees

- PARTIAL: Weeping Willow now stages a stronger curtain lane and shadow-zone pressure; finish scenic dominance and device-proofing.
- PARTIAL: Jacaranda now stages a fuller canopy halo and stronger petal-curtain reward; finish device-proofing and final spectacle tuning.
- PARTIAL: Bamboo now stages its precision gaps more explicitly with stronger lane guidance and payoff; finish device validation and signature threat identity.
- PARTIAL: Cherry Blossom now sells gust pressure more clearly with layered trails and stronger pass reward; finish device-proofing and final storm feel.

### Birds

- PARTIAL: Duck now has a stronger low-flight lane pulse, explicit down cue, and cleaner duck-through payoff; finish on-device validation and final cue polish.
- PARTIAL: Tit flocks now warn and reward around their shared rhythm-wave instead of only existing as moving hitboxes; finish device-proofing and stronger rhythm payoff.
- PARTIAL: Chickadee groups now surface their jitter path and flutter-spread payoff more clearly; finish final clarity/charm tuning.
- PARTIAL: Owl now has stronger alert-ring telegraphing, night-glow mood, and relationship-aware alert cueing; finish device-proofing and deeper repeated-memory payoff.
- PARTIAL: Eagle now has a clearer target zone, stronger lock-on read, and relationship-aware mark cueing; finish device-proofing and stronger dramatic cue understanding.

### Animals

- PARTIAL: Cat kindness bonus is much more obvious and warm-bond passes/spares now leave visible reward feedback; finish repeated-friend payoff and device validation.
- PARTIAL: Fox mirror jump reads more clearly and warm-memory landings/passes now leave visible payoff; finish final telegraph charm and repeated-memory payoff.
- PARTIAL: Wolf howl/charge drama is materially stronger and spare payoff is now more visibly rewarding; finish device-proofing and final spare feel tuning.
- PARTIAL: Hedgehog fairness is materially stronger with warning-stage messaging and clearer debuff feedback; finish final tuning on phone.
- PARTIAL: Dog bark projectile and buddy mode are clearer, and bonded buddy runs now feel more celebratory; finish memorable buddy payoff and device validation.

## Architecture TODOs

- PARTIAL: dedicated readability tuning layer now exists via `ReadabilityProfile`, including central spawn pacing plus full flora, tree, bird, and animal sizing/staging/mercy baselines; final device retuning is still required.
- PARTIAL: ghost presentation now uses delayed reveal and post-impact suppression so it stops competing with the live runner at the most confusing moments; final device-proof tuning is still required.
- PARTIAL: persistent encounter memory architecture exists; broaden it into richer payoff and authoring tools.
- DONE: costume overlay architecture baseline exists.
- PARTIAL: pacifist and friendship tracking systems exist; broaden them into full mercy-route architecture.
- DONE: deterministic encounter verification harness now includes broad acceptance scenarios for families, individual entities, Bloom, ghost, and rest-loop testing.
- TODO: deeper authored biome scene system.
- PARTIAL: first-class emotional systems now exist for forest mood, personal return moments, relationship arcs, and quiet story fragments, now with explicit kindness/tender streak carry-over plus shared session-arc composition for menu/rest/Garden continuity; deepen authored coverage, stronger Garden consequences, and richer payoff.

## Visual And Audio TODOs

- TODO: full bespoke parallax/background artwork.
- TODO: denser petals, fireflies, leaf drift, and living-forest ambience.
- PARTIAL: Bloom audiovisual transformation is materially stronger through the new world shift, screen treatment, and surge haptic; finish final music/SFX polish and on-device tuning.
- TODO: complete forest leitmotif treatment across all music states.
- TODO: final polish pass for haptics, SFX timing, and visual juice.

## Release TODOs

- DONE: device acceptance checklist doc now mirrors deterministic scenario names.
- TODO: full manual device verification for all entity behaviors.
- TODO: performance audit on low-end, mid-range, and high-refresh phones.
- TODO: final store-ready art, screenshots, and release pass once gameplay vision is truly met.
