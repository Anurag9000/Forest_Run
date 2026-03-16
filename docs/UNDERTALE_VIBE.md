# Forest_Run — Undertale Personality & Charm System (Restored)

This document restores the original personality design target. Completion status and missing work are tracked in [docs/TODO_MATRIX.md](/home/anurag-basistha/Projects/TODO/Forest_Run/docs/TODO_MATRIX.md).

The goal is simple: the forest feels inhabited, remembers you, and has a sense of humour.

> "The Eucalyptus fills you with determination."

---

## 1. Visual Style — Earthbound/Undertale Pixel Aesthetic

### Character Proportions — Chibi Stylisation

- The woman should be slightly chibi.
- Her eyes should be large and expressive.
- The art should feel charming and imperfect rather than sterile.

### Expressive Eye States

| Event | Eye State | Pixel Description |
|---|---|---|
| Running | Default | two oval dots |
| Jump | Determined | `> <` |
| Apex | Excited | `★ ★` |
| Eagle diving | Scared | `O O` |
| Close Call | Smug | `^ ^` |
| Landing hard | Dizzy | `@ @` |
| Ducking | Focused | `- -` |
| Bloom | Joyful | `✿ ✿` |
| Rest | Peaceful | `- u -` |

### Colour Palette Discipline

- max 16 simultaneous colours per entity
- strong high-contrast night scenes
- Lily glow should dominate the night scene locally
- Flora reads should feel authored, not generic: lure, rhythm, wind, and thread-the-gap should each be obvious before impact
- Tree reads should feel scenic and dangerous at once: curtain, canopy, precision gap, and gust should each be felt before collision
- use dithering instead of smooth gradients within sprites

### Current Status

- Implemented: player sprite animation basics.
- Partial: separate face/eye layer exists; fuller expressive presentation and stronger intentional imperfection are still needed.

---

## 2. Flavour Text System — FlavorTextManager

Every significant moment in the run should trigger a brief popup.

### Rest Screen Determination Quotes

Universal examples:

- Stay determined.
- That was a good try.
- The forest remembers.
- Almost.
- You're getting warmer.
- The seeds are proud of you.

Biome-specific and killer-specific quote pools should exist as well.

### In-Run Action Popups

Examples:

- close call: Boop, Zoom, Close, Phew
- seed collection: +, Yes
- cat kindness: Meow?, Friendship
- wolf charge: Uh oh, Here it comes
- fox mirror jump: Copycat, Mirror, Gotcha
- dog bark: BORF
- eagle screech: !
- Bloom: RUSH, BLOOM, YES
- perfect biome: FRIEND
- repeat killer: Again?, Really?

### Current Status

- Implemented: floating flavor text manager.
- Partial: rest quote system and dialogue bubble staging now exist, and tracked Cat/Fox/Wolf/Dog encounter beats now swap history-aware lines in ordinary play; much richer trigger coverage and repeat-killer messaging are still needed.
- Partial: Bloom now reads more like a celebratory power beat through stronger world transformation and conversion spectacle, but still needs real-device proofing to confirm it lands instantly.

---

## 3. Mercy & Pacifist Systems

### 3.1 Close Call Detection — Mercy Miss

- distinguish HIT vs MERCY_MISS
- MERCY_MISS should create a heart, flavor text, score reward, and haptic signal

### 3.2 Mercy Hearts -> Spare System

| Hearts | Effect |
|---|---|
| 3 | next Cat gives larger kindness reward |
| 5 | next Fox sits and waves instead of jumping |
| 8 | next Wolf disengages peacefully |
| 10 | PACIFIST banner / major reward |

### 3.3 Friendship Bonus — Full-Biome Mercy

- If the player completes a biome cleanly, award a friendship bonus.
- The forest should feel at peace with the player.

### Current Status

- Partial: mercy hearts, pacifist rewards, and friendship persistence exist.
- TODO: full mercy reward language, stronger spare visibility, and route-like world-state payoff.

---

## 4. Persistent Memory System

The game should remember across sessions.

### 4.1 Entity Encounter Counters

- track encounters
- track spared counts
- track hits taken from each type

### 4.2 Visual Evolution — Costumes

Examples:

- Cat: hat, then flower crown
- Dog: bandana, then bowtie
- Fox: scarf
- Wolf: scar
- Hedgehog: sunglasses
- Owl: moon charm

### 4.3 Déjà Vu Effect

- repeated killer types should comment on it
- some hazards should gain cosmetic markers after repeated dominance

### 4.4 Ghost Run — Spirit Of The Best

- ghost should replay your best run
- no gameplay hitbox
- should wave and vanish elegantly

### Current Status

- Implemented: ghost persistence, persistent memory manager baseline, and costume wardrobe baseline.
- Partial: ghost readability now delays reveal at run start and suppresses after impacts so it stops crowding the live runner during recovery.
- TODO: richer repeat-killer system and final tasteful ghost UX tuning.

### 4.5 Forest Memory Layer

- the world should remember tone, not only counters
- gentle recent play should make the sanctuary feel calmer and more welcoming
- repeated panic, repeated collisions, or repeated harm from the same creature should create emotional consequences
- repeated kindness should cause visible trust signs, calmer ambience, and more warmth in the Garden
- the player should feel that the forest has formed a soft opinion about them

### 4.6 Named Relationship Arcs

- Cat should become a shy comfort presence over time
- Fox should become a playful recurring rival-friend
- Wolf should evolve from fear to respect
- Dog should become loyal surprise energy
- Owl should become eerie but familiar
- Eagle should stay awe-filled and intimidating, but remembered
- every major creature should have first-impression, recognition, trust, and milestone stages

### 4.7 Personal Return Moments

- the game should notice when the player returns
- the first run of the day should be able to greet the player specially
- a long absence should be answerable with a line or mood shift that implies the forest noticed
- several failures in a row should be able to trigger comfort instead of flat repetition
- a personal milestone should sometimes cause a favorite creature to appear or react in the Garden

### 4.8 Quiet Story Fragments

- use short rest quotes instead of exposition
- add one-line creature thoughts and weather-linked reflections
- use rare Garden reflections and unlockable poetic memory pages
- preserve mystery by saying less, not more

### Current Status

- Partial: forest mood and personal return moments now exist in baseline form.
- Partial: repeated harm now leaves cautious sanctuary, return, and reflection beats in baseline form.
- Partial: named relationship arcs now exist in baseline runtime form for Cat, Fox, Wolf, Dog, Owl, and Eagle, with persisted stages, stage-aware dialogue/payoff, bonded Garden return hooks, and milestone keepsake rewards.
- Partial: quiet story fragments now exist in baseline runtime form through fragment-driven rest quotes, Garden reflections, weather-linked thoughts, bonded creature thoughts, milestone-gentleness / Bloom-afterglow reflections, and memory-page unlocks.
- TODO: deepen forest mood consequences, relationship-stage consequences, fragment coverage, and return-moment richness beyond the current baseline.

---

## 5. Undertale Dog — Running Buddy Mode

- 20% chance of harmless buddy mode
- runs beside the player for 3–5 seconds
- barks rhythmically
- departs with a cute line

### Current Status

- Implemented: buddy variant exists in code.
- Partial: bonded Dog runs now keep company longer, speak with relationship-aware buddy lines, and leave more visible celebratory payoff on departure.
- TODO: make it obvious and delightful enough that players actually notice and remember it.

---

## 6. Music — Leitmotif System

The forest leitmotif should appear in all music states in different arrangements.

| Track | Leitmotif Treatment |
|---|---|
| Garden | slow piano |
| Early Run | hidden in rhythm |
| Mid Run | flute melody |
| Late Run | faster, heavier variant |
| Bloom | orchestral triumph |
| Rest | music-box reflection |
| Pacifist Bonus | reward harmony |

### Current Status

- Implemented: music state transitions.
- Partial: Bloom now has a stronger visual/haptic identity in runtime, while the full leitmotif treatment still remains open.
- TODO: full leitmotif system and stronger authored score.

---

## 7. Non-Enemy Character Dialogue Bubbles

Animals should visibly react with brief speech bubbles during notable interactions.

Examples:

- Cat: Meow?, Phew
- Fox: Gotcha, Heh, Next time
- Dog: BORF, Hi!!
- Hedgehog: Eep
- Wolf: GRRR...
- Owl: Hoot
- Duck: Quack

### Current Status

- Implemented: formal dialogue bubble system baseline.
- Partial: bird-family warning/payoff bubbles are now more authored in ordinary play instead of relying on generic fallback lines.
- TODO: expand the rest of the bubble catalog, staging, and trigger richness.

---

## 8. New Kotlin Systems Summary

Dream-spec supporting systems include:

- `FlavorTextManager`
- `PersistentMemoryManager`
- `GhostRecorder`
- `GhostPlayer`
- `MercySystem`
- `PacifistTracker`
- `LeitmotifManager`
- `DialogueBubble`
- `CostumeOverlay`

### Current Status

- Implemented: `FlavorTextManager`, `GhostRecorder`, `GhostPlayer`, `LeitmotifManager` in partial form.
- Implemented: `PersistentMemoryManager`, `PacifistTracker`, `DialogueBubbleManager`, and a baseline `FaceManager`.
- Implemented: `MercySystem` and `CostumeOverlay` baselines.
- TODO: deepen those systems into fuller route, unlock, relationship, richer return-moment, and fragment-driven payoffs.

---

## 9. Design Mantras

When implementing this game, always ask:

1. Does it have a voice?
2. Does it remember?
3. Is it forgiving?
4. Is it imperfect in a charming way?
5. Is the leitmotif in there?

### Current Status

- TODO: make these mantras the actual implementation standard across the whole project, not just a historical note.
