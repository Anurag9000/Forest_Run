# Forest Run — Game Design

**Platform:** Android (native Kotlin, SurfaceView)  
**Package:** `com.yourname.forest_run`  
**Orientation:** `sensorLandscape`  
**Target FPS:** 60  
**Tone:** Ghibli × Stardew Valley — cottagecore, intimate, alive

---

## 1. Vision & Design Pillars

Forest Run is a high-fidelity endless runner for Android. It is not a score-chaser. It is a handcrafted forest journey with a living world, creature personality, mercy mechanics, Bloom power spikes, and a restorative garden meta-loop. The intended feeling is indie game, not prototype.

| Pillar | Description |
|---|---|
| Juicy | Every input triggers satisfying visual, haptic, and audio feedback |
| Alive | The world breathes: wind, petals, fireflies, day/night atmosphere |
| Readable | Each entity has a unique silhouette, motion, and emotional identity |
| Rewarding | Seeds → Garden meta-loop gives every run long-term meaning |
| Behavioural | Animals react to the player; the forest has formed a soft opinion |

No generic obstacle feeling is acceptable. Every entity must have a distinct personality that the player can learn to love, fear, or trust.

---

## 2. Art Style

- **Aesthetic:** high-saturation vibrant pixel art, Ghibli × Stardew Valley
- **Palette:** deep forest greens, floral pastels, earthy browns, warm gold, deep violet
- **Rule:** nothing is static. All flora sways. All particles drift. The forest is never still.
- **Character:** slightly chibi proportions, large expressive eyes, charming imperfection over sterile precision

### Expressive Eye States

| State | Eyes |
|---|---|
| Running | `• •` default |
| Jump | `> <` determined |
| Apex | `★ ★` excited |
| Eagle dive | `O O` scared |
| Close call | `^ ^` smug |
| Hard landing | `@ @` dizzy |
| Ducking | `- -` focused |
| Bloom | `✿ ✿` joyful |
| Rest | `- u -` peaceful |

### Colour Discipline

- Max 16 simultaneous colours per entity
- Strong high-contrast night scenes
- Lily glow dominates the night scene locally
- Use dithering instead of smooth gradients within sprites

---

## 3. Session Lifecycle

### Cold Start — The Garden

The player opens the app to see the woman sitting under a Weeping Willow in her personal garden. No hard menu buttons. Ambient forest audio plays. First tap: she stands. Second tap: she begins her run. A rhythmic acoustic beat fades in, synced to her footstep cadence.

### Early Game (0–500m)

Biome: Meadow. Simple early hazards. Gentle scroll speed. Seeds begin spawning. Music starts minimal. The first 30 seconds use a curated guided opening: visible tap/hold/duck teaching chips, a brief random-spawn lockout, and a curated early-game pool so the lane teaches itself without a sterile tutorial.

### Mid Game (500m–1500m)

Every 500m a biome transition fires. Background tints shift. Spawn pools change. Speed rises. Music layers deepen. Foxes, Wolves, Ducks, and complex threats are introduced.

### Bloom State

Triggered when the Bloom meter fills (8 seeds). Full invincibility for 6 seconds. Petal trail, full-screen world shift, audiovisual surge. Entities passed during Bloom convert into bonus rewards. Nearby entities react visibly before the pass-conversion line — the lane opens around the player. Conversion-streak escalation swells the player, camera, and screen response as momentum grows.

### Late Game

Dense overlapping threat patterns. Maximum wind and atmosphere. Strong milestone feedback. Fully layered music.

### Soft Fall — Rest

On collision the character does not feel cheaply deleted. She stumbles, slows, and sits tired but peaceful. A staged DYING→REST settle window, an authored recovery panel with a run-kept summary and homeward preview card, and a varied post-run reflection follow. The emotional flow supports returning.

### Meta-Loop — The Garden

Seeds collected during runs unlock new plants. The garden becomes a personalized forest over many sessions. The next locked plant is always visible as motivation.

---

## 4. Input System

| Input | Action |
|---|---|
| Single tap | Standard jump |
| Hold / long press | High jump — force proportional to hold duration |
| Swipe down | Duck / slide |

Short tap = low hop. Full hold = maximum arc. "Mario abort" mechanic: releasing while rising cuts upward velocity by half.

---

## 5. Scoring System

| Event | Reward |
|---|---|
| Distance | 1.5 points per metre |
| Close call (MERCY_MISS) | Score bonus + screen flash + haptic |
| Seed collected | Bloom meter fill + lifetime seed count |
| Kindness/pass rewards | Multiplier-weighted bonus points + seeds |
| 1000-point milestone | Camera nudge + haptic + authored popup |

Score and distance advance using the same scroll speed per frame (synchronized in `GameStateManager.update()`).

---

## 6. Seeds & Bloom

Seeds are vibrant glowing orbs. Some are placed as tempting traps above hazards. Some drop from interactions. Seeds feed two systems simultaneously:

1. **In-run:** fill the Bloom meter (8 seeds → 6s invincibility)
2. **Meta-game:** lifetime garden currency for plant unlocks

Bloom meter has three states with distinct HUD/world framing: near-ready charge, active-power, and afterglow settle.

---

## 7. Mercy & Pacifist Systems

### Close Call Detection

Collisions resolve into `HIT`, `STUMBLE`, `MERCY_MISS`, or `NONE`. A MERCY_MISS awards a mercy heart, a border flash in the biome's dominant color, a score bonus, and a haptic pulse.

### Route Tiers

| Tier | Conditions |
|---|---|
| `KIND` | ≤1 hit, ≥2 mercy hearts or ≥1 spare or (≥4 kindness chain + ≥4 clean passes) |
| `MERCIFUL` | 0 hits, ≥2 spared or (≥3 hearts + ≥7 chain + ≥6 clean) |
| `PEACEFUL` | 0 hits, ≥5 hearts, ≥2 spared, ≥10 clean passes |

Route tiers carry through rest, Garden, return moments, sanctuary state, and persisted world-opinion signals. Peaceful biome routes leave named world-state signs visible across startup, rest, and Garden.

### Biome Friendship

Completing a biome cleanly (≥3 clean passes, no hit) awards a friendship bonus and leaves a named biome-friendship sign in the sanctuary.

---

## 8. Forest Memory Architecture

The world remembers tone, not only totals. Six interlocking systems drive this:

### ForestMoodSystem
Classifies recent run tone (gentle, reckless, fearful, steady). Drives Garden ambience, sanctuary lighting, carry-home framing, and arrival badge presentation.

### RelationshipArcSystem
Tracks Cat, Fox, Wolf, Dog, Owl, and Eagle through first-impression, recognition, trust, and milestone stages. Stages persist across sessions and drive dialogue, encounter generosity, spare tuning, Garden keepsakes, named bond rituals, milestone costume rewards, and featured sanctuary home-presence lines.

### ReturnMomentsSystem
Detects re-entry context: first run of day, long absence, failure streak, milestone bonds, Bloom-heavy runs, repeated kindness, repeated harm, route tier. Surfaces distinct authored return moments instead of generic greetings. Rest previews the likely Garden return beat without consuming the saved state.

### StoryFragmentSystem
Drives rest quotes, Garden reflections, weather-linked thoughts, creature-thought fragments, and unlockable memory pages. All writing is brief and emotionally suggestive, validated by automated style tests to stay intimate and avoid exposition.

### SessionArcComposer
Derives menu atmosphere, opening home-sign, launch copy, rest recovery text, carry-home wording, and Garden arrival lines from the same shared emotional state. The macro loop reads as one authored arc.

### GardenSanctuaryPlanner
Derives visible sanctuary ambience, bond traces, mist bands, lantern glows, arrival badges, ground-light cues, a persistent home-character label, and explicit homecoming consequence chips from mood, route, bond, history, and world state.

---

## 9. Audio Design

### Music States

| State | Character |
|---|---|
| Garden / Menu | Soft ambient acoustic — slow leitmotif piano |
| Running — early | Simple drum beat — leitmotif hidden in rhythm |
| Running — mid | Bass + flute layer |
| Running — late | Full layered track — heavier leitmotif variant |
| Bloom | Orchestral triumph — leitmotif peak |
| Rest | Music-box reflection — leitmotif coda |

Dynamic tempo scales with scroll speed. Each state resolves through an explicit named motif signature that also informs the cinematic overlay finish layer.

### SFX

Jump (whoosh), land (soft thud + grass rustle), seed collect (soft ping), bark, screech, howl, Bloom chime, Bloom convert, rest exhale, mercy miss, milestone.

---

## 10. Haptic Feedback

| Event | Haptic |
|---|---|
| Jump | Short pulse |
| Landing | — |
| Bloom activates | Long surge |
| Collision / game over | Long strong pulse |
| Close call | Double-tap |
| 1000-point milestone | Medium pulse |

---

## 11. Garden Progression — Plant Unlocks

| Plant | Seed Cost |
|---|---|
| Cactus | 5 |
| Lily of the Valley | 10 |
| Hyacinth | 15 |
| Eucalyptus | 20 |
| Vanilla Orchid | 25 |
| Bamboo | 30 |
| Cherry Blossom | 40 |
| Weeping Willow | 50 |
| Jacaranda | 60 |

Animals and birds are not v1.0 garden unlockables. Unlocking a plant also equips it as a possible Garden background presence.

---

## 12. Entity Reference

Every entity has a distinct silhouette, distinct motion, distinct gameplay role, and distinct emotional personality. Generic obstacle feeling is not acceptable.

### Ground Flora

**Lily of the Valley** — tiny low hazard, ghost-flower lure. Glows at night. Distracts near the player's feet. Creates tricky low seed-traps. Glow dominates the night scene locally. Strong lure descent → low trap band → clear pass reward.

**Hyacinth** — clustered rhythm hazard. Three-beat identity, pollen, partial-brush punishment. Brush-vs-hit band is explicit. Rhythm payoff is authored.

**Eucalyptus** — forward-leaning whip plant. Fast whip sway, trapezoid feel. Earlier whip read, clearer lean lane, layered gust guides. Punishes late reads.

**Vanilla Orchid** — vertical-window obstacle. Safe thread between low and high colliders. Two explicit hazard zones with a narrower true safe window.

**Cactus** — classic runner baseline. Rigid, harsh silhouette, history-aware payoff text. Persistent clean-pass memory. Named `Needle Bloom` Garden trace. Carried-home sign.

### Trees

**Weeping Willow** — curtain hazard, core game icon. Forces ducking. Denser canopy silhouette, clearer curtain read, explicit duck lane. Obscures what comes next.

**Jacaranda** — purple-canopy atmosphere tree. Layered canopy halo, cascading petal veil, clearer underside lane. Petal curtain reads as intentional pressure.

**Bamboo** — vertical-barrier precision hazard. Narrow gap threading, jitter sway. Featured seam, tighter secondary gaps, clearer precision-line staging.

**Cherry Blossom** — wind-making environmental modifier. Gust pressure band, broader storm veil, crosswind staging. Petal storm reads as distinct from other trees.

### Birds

**Owl** — night watcher, punishes reckless jumping. Sleeping perch → reactive dive → eerie glow. Stronger same-shadow alert cueing, visible memory ring, familiar-night clean pass, night-glow mood.

**Duck** — low flyer, teaches ducking. Staged quack call, explicit low-lane answer, stronger answered-the-quack pass reward. Unmistakable head-height obstruction.

**Eagle** — hunter dive threat. Screech cue, target lock, diagonal punishment. Clearer dive corridor, held-mark prompt, stronger clean-line pass reward.

**TitGroup** — rhythm-wave flock. Group sine motion, timing-based reads. Staged beat count-in, visible trough guide, stronger kept-the-beat reward.

**ChickadeeGroup** — erratic aerial chaos. Unpredictable altitude shifts, cute panic energy. Featured lead-bird charm cue, clearer flutter pocket, warmer clean-read reward.

### Animals

**Wolf** — sprinter/charger. Howl → charge; intimidating but readable. Relationship-aware charge cueing, respectful spare-history lines, visible stand-down aura/trail, earned-respect spare reward.

**Cat** — kindness-rewarding decoy. Tiny optional reward hazard, kindness bonus, spare-like warmth. Relationship-aware near-miss cueing, personal repeated-friend lines, shared-quiet aura, familiar pass reward.

**Fox** — mirror-jump trickster. Sly counter-jump, playful line delivery, mercy-based wave-off. Knowingly playful repeat-memory lines, brighter trail aura, stronger remembered-the-trick reward.

**Hedgehog** — small friction threat. Debuffs scroll speed instead of killing. Fair-hop arming window, visible low-lane read, clearer warning staging, clean-clear reward.

**Dog** — barker and running buddy. 20% chance of harmless buddy mode: runs beside the player for 3–5 seconds, barks rhythmically, departs with a line. Fuller escort dialogue sequence, visible celebration trail, distinct bonded finale burst and reward.

---

## 13. Design Mantras

Every implementation decision should pass these five checks:

1. Does it have a voice?
2. Does it remember?
3. Is it forgiving?
4. Is it imperfect in a charming way?
5. Is the leitmotif in there?
