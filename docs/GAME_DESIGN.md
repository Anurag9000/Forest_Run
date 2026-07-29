# Forest Run — Game Design

**Platform:** native Android/Kotlin with a custom `SurfaceView` engine  
**Package:** `com.anurag9000.forestrun`  
**Current orientation:** fixed landscape, pending final product/device acceptance  
**Nominal frame target:** 60 Hz  
**Tone:** cottagecore, intimate, expressive, and restorative

This document states intended player experience and current mechanic truth. Visual identity must not be described as physics unless the runtime actually applies that mechanic.

## 1. Vision and design pillars

Forest Run is an endless runner built around a living forest, creature personality, mercy, Bloom, persistent memory, and a restorative Garden loop. It should feel authored rather than like a generic obstacle stream.

| Pillar | Requirement |
|---|---|
| Responsive | Tap, hold, swipe, cancellation, and state transitions are immediate and predictable |
| Alive | Wind, particles, animation, dialogue, music, and persistent reactions give the forest presence |
| Readable | Hazards advertise their true collision geometry and required response before contact |
| Forgiving | Mercy windows, nonlethal stumbles, and soft failure reward learning rather than surprise |
| Persistent | Seeds, relationships, Garden state, route history, and return moments give later sessions meaning |
| Honest | Documentation, telegraphs, hitboxes, and rewards must agree with runtime behavior |

Every creature should have a recognizable voice and memory, but uniqueness may come from motion, timing, geometry, reward, relationship, or presentation. It does not need invented physics merely to sound distinct.

## 2. Art and presentation direction

- Vibrant pixel-art characters and sprites
- Deep greens, floral pastels, warm earth, gold, and violet
- Expressive eyes and secondary motion
- Strong local contrast in night scenes
- Charming imperfection over sterile symmetry
- Motion used to communicate state, not to conceal collision geometry

Procedural scenic layers currently supplement authored sprites. Replacing or accepting those layers as final art remains a product decision.

## 3. Session lifecycle

### Home ritual

The player begins beneath the willow. The opening establishes home, then deliberately transitions into the run. Returning from the Garden resets this ritual rather than resuming an already-consumed menu state.

### Guided opening

The first portion of the run uses curated guidance, restricted random spawning, and readable early encounters to teach:

- tap for a short jump;
- hold for a higher jump;
- swipe down to duck;
- spacing and clean-pass timing.

### Run progression

Five biome identities cycle through the run:

1. Meadow
2. Orchard
3. Ancient Grove
4. Dusk Canyon
5. Night Forest

Scroll speed and encounter complexity increase, while world-distance pacing preserves minimum readable separation as speed changes. “Late game” should mean richer decisions and atmosphere, not unavoidable overlap.

### Bloom

Eight Seeds fill the Bloom meter. Bloom lasts six seconds and is orthogonal to locomotion: the player can continue jumping, falling, landing, and ducking while invincible.

Bloom communicates through:

- player aura and trail;
- HUD active state and remaining time;
- camera and screen response;
- music, SFX, and haptics;
- nearby-world reaction;
- exclusive conversion rewards for passed entities.

Bloom rewards cannot restart the active timer or stack ordinary pass, unique-action, and Orb rewards on the same entity.

### Soft failure and Rest

A lethal outcome advances through the explicit death/game-over/restart flow. The emotional goal is reflection rather than punishment:

- coherent failure animation;
- run-kept summary;
- contextual Rest writing;
- return to the Garden;
- no contradictory hit/pass/mercy statistics.

### Garden return

Seeds are persistent Garden currency. The Garden reflects unlocked plants, relationships, route history, recent run tone, and return context. Spending must remain authoritative across run resets.

## 4. Input and locomotion

| Input | Runtime behavior |
|---|---|
| Tap | Immediate launch followed by an early release cap for a short hop |
| Hold | Release later, preserving more of the initial ascent for a higher arc |
| Swipe down | Classified before jump commitment and enters duck while legal |
| Cancel/interruption | Silently clears the gesture without producing a delayed action |

The player launches responsively at maximum initial upward velocity. Releasing while rising caps velocity according to hold duration and never adds energy. A quick tap approaches `MIN_JUMP_FORCE`; a full hold preserves the larger arc.

Input is accepted only during live gameplay. Menu, Garden, death, game-over, restart, and screen transitions cancel active gameplay gestures.

## 5. Score, Seeds, and Bloom economy

| Event | Result |
|---|---|
| Distance | Score and distance advance from the same captured speed value |
| Mercy miss | One mercy event, presentation feedback, and bonus |
| Clean pass | One terminal pass, entity action, persistent outcome, and route progress |
| Seed Orb | Bloom charge plus persistent Seed currency |
| Bloom conversion | Exclusive conversion reward; no ordinary pass/action/Orb stacking |
| Milestone | Bounded authored feedback, camera, audio, and optional haptic response |

Seed Orbs are staged ahead of the player at reachable heights and cleaned up off-screen.

## 6. Encounter outcome contract

Every entity starts pending and resolves exactly once to one of:

- `HIT`
- `STUMBLE`
- `MERCY`
- `CLEAN_PASS`
- `BLOOM_CONVERTED`

Collision arbitration runs before pass resolution. Simultaneous overlaps use:

```text
HIT > STUMBLE > MERCY
```

Collision queries must be pure. Only the selected result may emit effects or change state. A Hedgehog stumble, for example, cannot later become a clean pass for the same encounter.

Mercy geometry must agree with visible staging. Expanded collision probes are allocation-free but preserve each entity’s intended asymmetric safe window.

## 7. Mercy and pacifist routes

Mercy rewards close, non-contact avoidance. It is awarded once per encounter, not once per overlapping frame.

Route tiers are derived from actual run outcomes:

| Tier | General intent |
|---|---|
| Kind | Some positive conduct despite limited mistakes |
| Merciful | No lethal mistakes and repeated positive outcomes |
| Peaceful | Sustained clean, merciful play across a substantial run |

The exact thresholds live in `PacifistTracker` and its tests; this document intentionally does not duplicate constants that can drift.

Route signals influence Rest, Garden, return moments, world opinion, sanctuary presentation, and persistent history.

## 8. Forest memory

### Forest mood

Recent conduct is summarized into a mood such as gentle, reckless, fearful, or steady. It affects sanctuary and post-run presentation.

### Relationships

Cat, Fox, Wolf, Dog, Owl, and Eagle can progress through familiarity and meaningful relationship stages. Appearance alone may reach Recognition; Trust and Bond require positive outcomes, while hits delay progress.

### Return moments

Return writing considers local day, absence, rough-run streak, route, Bloom use, relationships, repeated kindness, and repeated harm. A return moment is consumed only when visibly shown in the Garden.

### Story and session composition

Story fragments, Rest quotes, menu atmosphere, Garden arrival, and carry-home wording are composed from shared state so the macro loop remains coherent rather than presenting independent random text.

## 9. Garden progression

Current plant unlock order and costs:

| Plant | Seed cost |
|---|---:|
| Cactus | 5 |
| Lily of the Valley | 10 |
| Hyacinth | 15 |
| Eucalyptus | 20 |
| Vanilla Orchid | 25 |
| Bamboo | 30 |
| Cherry Blossom | 40 |
| Weeping Willow | 50 |
| Jacaranda | 60 |

Catalogue, statistics, last-run, wardrobe, and run controls share one tested layout plan. Hardware acceptance is still required for density, cutouts, unusual aspect ratios, text size, and touch comfort.

## 10. Audio, haptics, and accessibility

Music transitions through menu/Garden, early/mid/late run, Bloom, and Rest identities. Crossfade ownership is deterministic and frequent parameter writes are throttled.

SFX loading is explicit. Mandatory missing assets fail non-debug validation; optional Bloom sounds have defined fallbacks.

Persistent controls independently manage:

- reduced motion;
- music/SFX;
- haptics.

Reduced motion may suppress decorative intensity but must not erase hazard telegraphs or alter gameplay physics.

## 11. Entity reference: mechanic truth

The descriptions below separate current collision/behavior from visual identity. Final fairness and readability require ordinary play and physical-device acceptance.

### Ground flora

**Cactus** — baseline rigid ground obstacle. Its value is immediate silhouette, fair jump timing, and history-aware clean-pass presentation.

**Lily of the Valley** — small low hazard with strong glow/lure identity. The glow and nearby Seed staging must remain visible without masking the low collision band.

**Hyacinth** — low clustered threat with a nonlethal brush/stumble identity. Hit, stumble, mercy, and clean pass must read as separate outcomes.

**Eucalyptus** — leaning flora hazard with fast sway and gust/whip telegraph presentation. Current gameplay danger is its collision geometry; no external force should be claimed unless an explicit timed strike or player force is implemented later.

**Vanilla Orchid** — two separated collision bands with an intentionally asymmetric safe thread. Telegraphs must reveal both danger zones before contact.

### Trees

**Weeping Willow** — canopy/curtain obstruction with a readable low response lane. Obstruction must not hide an unavoidable following encounter.

**Jacaranda** — canopy and petal-pressure presentation around a branch/trunk collision structure. Petals currently communicate atmosphere and staging rather than independent projectile physics.

**Bamboo** — repeated top/bottom barriers forming precision gaps. Gap placement and one-sided mercy windows must remain readable at every supported speed.

**Cherry Blossom** — branch/trunk obstacle with a broad gust/storm visual identity. The gust currently communicates pressure and lane occupation; it does not push the player. Documentation must change only after a real force mechanic is added and tested.

### Birds

**Duck** — low flyer used to teach ducking through a clear lane and timing cue.

**Tit group** — coherent wave/rhythm flock. Motion should remain learnable rather than becoming overlapping visual noise.

**Chickadee group** — lively altitude variation with a predictable enough safe pocket to remain fair.

**Owl** — alert-to-dive night threat. Alert, trajectory, glow, and collision timing must agree.

**Eagle** — live-targeting dive threat with a visible mark, lock point, and escape grace window.

### Animals

**Cat** — optional kindness-oriented encounter. Hit, mercy, pass, spare/reward, and exit are mutually exclusive.

**Fox** — mirror/counter-jump trickster whose movement and outcome should feel playful rather than arbitrary.

**Wolf** — howl/charge encounter with relationship-aware presentation. Sprite frames, charge timing, collision, mercy, and stand-down state require visual hardware acceptance.

**Hedgehog** — nonlethal friction threat. Contact resolves terminally as stumble/debuff and cannot later award a pass.

**Dog** — hazard mode and harmless buddy mode. Projectile lifecycle, buddy harmlessness, departure, dialogue, and persistent behavior must remain mode-consistent.

## 12. Debug scenarios and acceptance

Deterministic scenarios exist for the opening, Bloom, ghost, Rest, every entity family, Garden, safe content, lifecycle, and persistence flows. They are isolated from permanent progression.

A deterministic scenario proves repeatability, not product acceptance. Each scenario still requires ordinary-play checks on representative physical hardware.

## 13. Performance and release honesty

The engine records update/render/processing timing with a fixed-buffer monitor and exposes percentile/heap snapshots outside the frame hot path. Known iterator, emitter, collision-rectangle, and presentation churn has been reduced.

The following statements remain prohibited until measured:

- “allocation free” for the complete frame;
- “locked 60 FPS” on supported devices;
- “release ready”;
- “all art final”;
- “all entities physically validated.”

Release acceptance still requires profiling, representative devices, signed installation, store-path testing, final artwork/screenshots/metadata, and current policy review.

## 14. Design mantras

Every implementation decision should answer:

1. Does it have a recognizable voice?
2. Does meaningful conduct change future response?
3. Is failure readable and recoverable?
4. Does presentation agree with collision and reward semantics?
5. Does it remain charming without sacrificing clarity?
6. Is audio identity present where it adds information or emotion?
