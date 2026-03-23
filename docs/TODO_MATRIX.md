# Forest_Run — Exhaustive TODO Matrix

This file is the strict implementation ledger for everything from the original dream specs that is not fully realized yet.

For the current non-hardware implementation burn-down list, use [docs/ACTIVE_IMPLEMENTATION_TODO.md](/home/anurag-basistha/Projects/TODO/Forest_Run/docs/ACTIVE_IMPLEMENTATION_TODO.md). Remove items from that file only when they are fully implemented and verified.

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
- PARTIAL: Bloom now has a stronger activation burst, player-following effects, world-tint transformation, conversion world bursts, stronger HUD paneling, a distinct surge haptic, and stronger parallax-world ambience during the power state; finish real-device proofing and the last authored power-event polish.

## Documentation And Canonical Spec TODOs

- DONE: canonical runtime truth is frozen at 5 biomes, 8-seed / 6-second auto-Bloom, and rest-to-Garden failure flow.
- DONE: formal device acceptance checklist now exists and mirrors deterministic scenario names.
- DONE: historical exactness debt is now canonically resolved as traceability-only by default for old Bloom variants, six-biome variants, side-split input, and exact historical asset/file-name parity.
- TODO: keep touched docs and stale comments synchronized with canonical runtime truth and active product gaps only.

## GDD TODOs

- PARTIAL: startup now uses shared session-arc copy plus sanctuary-derived atmosphere, arrival badges, a stronger secondary opening line, a visible home-sign chip, a persistent sanctuary home-character chip/line, richer carried-home petals/bloom patches, authored stand/ready launch copy, and a visible home-sign lane instead of generic phase text; finish real-device proof.
- PARTIAL: the first 20–30 seconds now use an authored onboarding layer with curated opening spawn lockout, gentler guided spawn pacing, a curated early pool, and visible tap/hold/duck guidance chips; finish real-device proof and any final feel tuning.
- PARTIAL: Bloom spectacle now includes a full-screen/world shift, stronger conversion spectacle, and stronger active-state HUD framing; finish final authored polish and device validation.
- PARTIAL: rest now has a staged DYING-to-REST settle window, authored recovery title/line, a visible homeward preview card, sanctuary recovery ambience, a visible persistent home-character beat, a stronger run-kept summary, and a varied post-run reflection focus instead of only quote-plus-stats; finish real-device proof.
- PARTIAL: the Garden now has mood-based ambience, sanctuary carry-home framing, visible bond traces, arrival badges, mist/lantern/ground-glow atmosphere, a persistent home-character sign/line, an explicit shared homecoming-consequence layer, and a stronger arrival line when no special return beat is active, while startup now also surfaces a clearer home-sign atmosphere; finish the full restorative scene and remaining deeper homecoming consequence work.

## Personality TODOs

- PARTIAL: contextual rest quotes now react to biome, killer, route tier, clean returns, peaceful Bloom afterglow, merciful familiarity, and stronger bond/repeat-friend state, and rest/Garden now compose varied route/world/weather/creature reflection sets instead of a fixed line order; expand quote pools and trigger richness further.
- PARTIAL: `PersistentMemoryManager` / `SaveManager` now also provide a canonical repeated-history snapshot plus saved unlock marks for kindness, caution, repeat-killer, clean-pass, and peaceful-biome memory, and sanctuary/menu copy now consume that shared presentation state instead of re-deriving it ad hoc; deepen the downstream emotional consequences further.
- DONE: canonical post-run summary payload now exists across rest and Garden.
- PARTIAL: costume overlays and wardrobe flow now include milestone-earned Cat/Fox/Wolf/Dog/Owl/Eagle wardrobe rewards, and Garden now surfaces relationship-aware unlock messaging plus featured reward/cosmetic pairing instead of generic wardrobe notices; expand broader repeated-encounter unlock depth and presentation further.
- PARTIAL: repeat-killer deja vu lines now exist in rest, Garden reflections, sanctuary badges, stronger `Same Shadow` return/carry-home beats, and new in-run collision flavor; deepen them further into stronger visual and narrative payoff.
- DONE: dedicated dialogue bubble system.
- PARTIAL: biome-level friendship bonus baseline now escalates into save-visible pacifist route tiers plus named peaceful-biome carry-home, menu/rest/Garden copy, route-sensitive reflections, and explicit homecoming consequence chips/cards; deepen it further into broader sanctuary/world-state consequence.
- PARTIAL: encounter-level history now surfaces through tracked Cat/Fox/Wolf/Dog/Owl/Eagle cues plus new collision, mercy-miss, clean-pass, and milestone run flavor instead of fallback one-liners alone; expand the same depth across more creatures and remaining normal-run situations.
- PARTIAL: stronger spare events and pacifist route feel now include explicit `Kind` / `Merciful` / `Peaceful` run tiers, route-aware return moments, route-aware Garden reflections, route-specific memory pages, stronger mercy-miss and route-reward presentation, and clearer rest/Garden route language; complete broader world-state and feedback.
- DONE: face/eye state system baseline for the heroine.

## Emotional Attachment Expansion TODOs

### Forest Memory Layer

- DONE: added a save-backed `ForestMoodSystem` with run classification into gentle, reckless, fearful, and steady.
- PARTIAL: current tone now affects Garden palette, menu/rest/Garden labeling, sanctuary ambience, visible carry-home framing, sanctuary arrival-badge presentation, and explicit homecoming consequence chips/cards; deepen creature warmth and broader presentation response.
- PARTIAL: repeated panic or repeated harm from the same creature now surfaces through cautious sanctuary traces, strained-bond `Held At A Distance` returns, stronger `Same Shadow` repeat-killer beats, Garden caution/strained reflections, and save-backed tender streaks; deepen broader creature warmth and world-state response.
- PARTIAL: repeated kindness can now brighten the sanctuary through added ambience, bloom patches, trust traces, repeat-friend `Shared Path` traces, warmer rest fragments, dedicated `Stayed Gentle` / `Kept Finding You` returns, stronger lantern/ground-glow atmosphere, and named peaceful-biome carry-home signs; deepen the effect and tie it more directly to repeated creature-specific history.
- TODO: ensure the player can feel that the world has formed an opinion about how they play.

### Named Relationship Arcs

- DONE: formal relationship stages now exist for Cat, Fox, Wolf, Dog, Owl, and Eagle.
- DONE: each relationship now has first-impression, recognition, trust, and milestone states.
- PARTIAL: relationship stages now drive dialogue, threat/pass/spare lines, encounter generosity/telegraph tuning, Garden strongest-bond presentation, bonded visitors, sanctuary traces, milestone keepsake rewards, featured home-presence/carry-home framing, and milestone-reactive run/Garden presentation.
- PARTIAL: relationship milestones now unlock named keepsakes, matching Cat/Fox/Wolf/Dog/Owl/Eagle costume paths, featured sanctuary home-presence lines, bond-specific score-milestone reactions, and fallback bonded Garden visitor reactions; deepen broader milestone presentation and remaining non-costume rewards.
- PARTIAL: repeated positive interactions now also surface through repeat-friend familiarity beats, `Familiar Return` badges, creature-specific warmth lines, and shared familiarity tiers that now warm ordinary passes, mercy cues, creature thoughts, and bonded returns; deepen Garden payoff further.
- PARTIAL: repeated negative interactions now surface stronger caution/tension through strained-bond live cue swaps, return beats, sanctuary traces, and fragment writing; deepen disappointment/fear coverage and broader in-run consequence further.
- TODO: add milestone rewards that feel relational, not merely numerical.

### Personal Return Moments

- DONE: added baseline first-run-of-day greeting logic, long-absence recognition, rough-run comfort returns, and milestone-sensitive Garden messages.
- PARTIAL: Garden return moments now use bonded visitors and stronger mercy/clean-play/high-score hooks, including milestone-bond warmth, Bloom-linger, and stronger absence-sensitive returns.
- PARTIAL: return moments now bind more deeply to many mercies, clean runs, stronger bonds, Bloom-heavy runs, repeated tenderness, repeated kindness streaks, repeat-friend familiarity, long-absence familiarity, repeat-killer patterns, and explicit kind/merciful/peaceful route states; continue expanding authored combinations.
- DONE: rest flow can now preview the likely Garden return beat without mutating save state, so return continuity can be written before the actual hub transition.
- TODO: ensure return sessions feel intimate instead of mechanically identical.

### Quiet Story Fragments

- DONE: short rest quotes now run through a first-class fragment-driven writing layer.
- PARTIAL: rare Garden reflections and unlockable poetic memory pages now exist in baseline form.
- PARTIAL: one-line creature thoughts for bonded creatures now exist in the Garden baseline.
- PARTIAL: weather-linked thoughts and caution-oriented Garden reflections now exist in stronger Garden form, alongside milestone-gentleness, repeated-kindness clean-return pages, repeat-friend familiarity, merciful familiarity, repeat-killer `Same Shadow`, peaceful-Bloom pages, Bloom-afterglow, and kind/peaceful-route pages; broaden authored coverage further.
- TODO: deepen poetic memory pages so they reveal feeling without lore-dumping.
- TODO: keep fragment writing brief, intimate, and suggestive rather than expository.
- TODO: ensure mystery and emotional residue are preserved through restraint.

## Entity TODOs

### Flora

- PARTIAL: Lily of the Valley now stages its lure and lower seed-trap through stronger local glow dominance, a clearer lure descent, a more explicit low trap band, safer low-hit readability, and clearer pass reward; finish device-tuning only.
- PARTIAL: Hyacinth now reads like a clearer three-beat clustered rhythm unit with explicit beat nodes, a clearer brush-vs-hit band, stronger rhythm payoff text, and locked brush/hit distinction in tests; finish device validation only.
- PARTIAL: Eucalyptus now sells the whipping leaf path more honestly with an earlier whip read, a clearer lean lane, an explicit danger band, layered gust guides, and stronger pass reward; finish device-tuning only.
- PARTIAL: Vanilla Orchid now stages its two hazard zones and safe thread through a narrower true overlap window, explicit guide markers, and a collision-safe center thread; finish device validation only.
- PARTIAL: Cactus now has stronger warning staging, history-aware payoff text, persistent clean-pass memory, a named `Needle Bloom` Garden trace, a carried-home cactus sign, and dedicated reflection-page payoff; finish device-proofing only.

### Trees

- PARTIAL: Weeping Willow now stages scenic dominance through a denser canopy silhouette, a stronger curtain read, and an explicit duck lane that stays collision-honest; finish device-proofing only.
- PARTIAL: Jacaranda now stages spectacle through a layered canopy halo, a cascading petal veil, and a clearer underside lane that stays collision-honest; finish device-proofing only.
- PARTIAL: Bamboo now stages its precision gaps through a featured seam, tighter secondary gaps, and clearer precision-line staging; finish device validation only.
- PARTIAL: Cherry Blossom now sells gust pressure through a true pressure band, a broader storm veil, and clearer crosswind staging; finish device-proofing only.

### Birds

- PARTIAL: Duck now has a staged quack call, a more explicit low-lane answer, and a stronger answered-the-quack pass reward on top of the earlier low-flight readability work; finish device-proofing only.
- PARTIAL: Tit flocks now use a staged beat count-in, a visible trough guide, and a stronger kept-the-beat reward on top of the earlier rhythm-wave readability work; finish device-proofing only.
- PARTIAL: Chickadee groups now feature a highlighted lead bird, a clearer flutter pocket, and a warmer clean-read reward on top of the earlier jitter-path readability work; finish device-proofing only.
- PARTIAL: Owl now has a stronger same-shadow alert, a visible memory ring, and a more familiar-night clean pass on top of the earlier telegraph/night-glow work; finish device-proofing only.
- PARTIAL: Eagle now has a clearer dive corridor, a held-mark prompt, and a stronger clean-line pass on top of the earlier target-zone and lock-on readability work; finish device-proofing only.

### Animals

- PARTIAL: Cat kindness bonus is much more obvious and warm-bond passes/spares now leave visible reward feedback, more personal repeated-friend lines, a shared-quiet aura, and a stronger familiar pass reward; finish device-proofing only.
- PARTIAL: Fox mirror jump reads more clearly and warm-memory landings/passes now leave visible payoff, more knowingly playful repeat-memory lines, a brighter trail aura, and a stronger remembered-the-trick pass reward; finish device-proofing only.
- PARTIAL: Wolf howl/charge drama now includes a true stand-down aura/trail, more respectful spare-history lines, and a stronger earned-respect spare reward on top of the earlier readability work; finish device-proofing only.
- PARTIAL: Hedgehog fairness now includes a true fair-hop arming window, a visible low-lane read, and a stronger clean-clear reward on top of the earlier warning/debuff work; finish device-proofing only.
- PARTIAL: Dog bark projectile and buddy mode now include a fuller escort dialogue sequence, a visible celebration trail, and a more distinct bonded finale burst/reward on top of the earlier readability work; finish device-proofing only.

## Architecture TODOs

- PARTIAL: dedicated readability tuning layer now exists via `ReadabilityProfile`, including central spawn pacing plus full flora, tree, bird, and animal sizing/staging/mercy baselines; final device retuning is still required.
- PARTIAL: ghost presentation now uses delayed reveal and post-impact suppression so it stops competing with the live runner at the most confusing moments; final device-proof tuning is still required.
- PARTIAL: persistent encounter memory architecture exists; broaden it into richer payoff and authoring tools.
- DONE: costume overlay architecture baseline exists.
- PARTIAL: pacifist and friendship tracking systems exist; broaden them into full mercy-route architecture.
- DONE: deterministic encounter verification harness now includes broad acceptance scenarios for families, individual entities, Bloom, ghost, and rest-loop testing.
- TODO: deeper authored biome scene system.
- PARTIAL: first-class emotional systems now exist for forest mood, personal return moments, relationship arcs, quiet story fragments, shared session-arc composition, and sanctuary-derived menu/rest/Garden atmosphere, now with explicit kindness/tender streak carry-over, repeat-friend familiarity, save-backed pacifist route tiers, named peaceful-biome carry-home state, repeat-killer escalation beats, and milestone reward/cosmetic carry-home surfacing; deepen authored coverage, stronger Garden consequences, and richer payoff.

## Visual And Audio TODOs

- PARTIAL: parallax/background rendering now includes biome wash, canopy shade, mist bands, stronger wind ribbons, drifting leaves/petals/fireflies, and subtle speed/Bloom world-scale response; full bespoke background artwork is still missing.
- PARTIAL: living-forest ambience density is materially stronger procedurally; finish bespoke scenic art, deeper ambient variety, and device-proof perceptual tuning.
- PARTIAL: Bloom audiovisual transformation is materially stronger through the new world shift, screen treatment, surge haptic, and stronger parallax ambience; finish final music/SFX polish and on-device tuning.
- PARTIAL: sanctuary presentation now includes mist bands, lantern glows, arrival badges, stronger homeward lighting, and a clearer carried-home visual identity across menu/rest/Garden, now including a persistent home-character label/line; finish bespoke scenic art and final device/perceptual tuning.
- PARTIAL: `LeitmotifManager` now resolves state-shaped tempo/volume profiles across menu, run layers, Bloom, and rest; full leitmotif composition treatment across all states is still missing.
- TODO: final polish pass for haptics, SFX timing, and visual juice.

## Release TODOs

- DONE: device acceptance checklist doc now mirrors deterministic scenario names.
- TODO: full manual device verification for all entity behaviors.
- TODO: performance audit on low-end, mid-range, and high-refresh phones.
- TODO: final store-ready art, screenshots, and release pass once gameplay vision is truly met.
