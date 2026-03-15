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
- use dithering instead of smooth gradients within sprites

### Current Status

- Implemented: player sprite animation basics.
- TODO: separate face/eye layer, fuller expressive presentation, stronger intentional imperfection.

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
- TODO: much richer trigger coverage, rest quote system, dialogue bubble staging, repeat-killer messaging.

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

- Implemented: mercy hearts exist, some mercy-linked entity behavior exists.
- TODO: full mercy reward language, pacifist/friendship bonus, stronger spare system visibility.

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

- Implemented: ghost persistence.
- TODO: persistent memory manager, costumes, repeat-killer system, tasteful ghost UX that does not confuse the player.

---

## 5. Undertale Dog — Running Buddy Mode

- 20% chance of harmless buddy mode
- runs beside the player for 3–5 seconds
- barks rhythmically
- departs with a cute line

### Current Status

- Implemented: buddy variant exists in code.
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

- TODO: formal dialogue bubble system.

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
- TODO: `PersistentMemoryManager`, `MercySystem` as dedicated system, `PacifistTracker`, `DialogueBubble`, `CostumeOverlay`.

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
