# Forest_Run — Undertale Personality & Charm System

This document covers every Undertale-inspired mechanic, system, and design principle layered on top of the base Forest_Run design. The goal: the forest feels inhabited, remembers you, and has a sense of humour.

> *"The Eucalyptus fills you with determination."*

---

## 1. Visual Style — Earthbound/Undertale Pixel Aesthetic

### Character Proportions — "Chibi" Stylisation

- The woman's sprite should be **slightly chibi**: head-to-body ratio leans toward 1:3 (not realistic 1:7).
- Her **eyes are large and expressive** — a few pixels, but they communicate everything.
- She is NOT polished corporate pixel art. Imagine Toby Fox drew her at 2 AM. That's the energy.

### Expressive Eye States (Contextual Face Changes)

The character's face layer is a **separate overlay** drawn on top of the body sprite. Based on game events, the eyes switch states:

| Event | Eye State | Pixel Description |
|---|---|---|
| Running (normal) | Default | Two small oval dots |
| Jump (ascending) | Determined | `> <` squint |
| Jump (apex/peak) | Excited | `★ ★` star pupils |
| Eagle diving at her | Scared | `O O` wide circles |
| Close Call success | Smug | `^ ^` curved arcs |
| Landing hard | Dizzy | `@ @` spirals |
| Ducking/Sliding | Focused | `- -` thin lines |
| Bloom State | Joyful | `✿ ✿` flower pupils |
| REST / Sitting | Peaceful | `- u -` tired smile |

### Colour Palette Discipline

- **Maximum 16 simultaneous colours per entity** (not per screen).
- Night/Violet Path biome: **High-contrast colours against near-black background**, like Undertale's Waterfall area.
- The Lily of the Valley glow in Night Mode should be the *only bright thing on screen* when it appears — everything else dims 30%.
- Avoid smooth gradients within sprites. Use **dithering** (checkerboard pattern) instead for the retro feel.

---

## 2. Flavour Text System — `FlavorTextManager`

Every significant moment in the run triggers a brief, quirky 1–3 word popup that floats and fades. This is the soul of the Undertale feel.

### REST Screen — "Determination" Quotes

When the character sits down after a run, a randomised flavour quote appears. These are **biome-contextual and action-contextual**:

#### Universal Quotes (any biome)
```
"Stay determined."
"That was a good try."
"The forest remembers."
"Almost."
"You're getting warmer."
"The seeds are proud of you."
"Not bad."
"Try again?"
"The path was long."
```

#### Biome-Specific Quotes

| Biome | Sample Quotes |
|---|---|
| Home Grove | "The Cactus was just doing its job." · "Home is always worth running back to." |
| Spring Orchard | "The petals are not sorry." · "The Bamboo has no hard feelings." |
| Ancient Grove | "The Eucalyptus fills you with peace." · "Even Wolves get tired eventually." |
| Violet Path | "The Jacaranda drops petals in your honour." · "The Owls are unimpressed. Keep trying." |
| Stormy Ridge | "Brave. Foolish. Same thing." · "The storm always passes." · "Even the Ridge admits that was close." |

#### Animal Encounter Quotes (if that animal caused the run to end)

| Animal | Quote |
|---|---|
| Wolf | "The Wolf thinks you're a fast learner." |
| Cat | "The Cat is indifferent about your feelings." |
| Fox | "The Fox thinks you two are evenly matched." |
| Hedgehog | "You were defeated by something the size of your fist. Respect." |
| Dog | "The Dog was just excited to meet you." |
| Owl | "The Owl says nothing. This is worse." |
| Eagle | "The Eagle will not be apologising." |
| Cactus | "The Cactus is just built different." |

### In-Run Flavour Text — Action Popups

Small text popups float up from the relevant entity and fade in 1.5 seconds. These trigger on game events:

| Trigger | Example Popups |
|---|---|
| Close Call with any animal | `"Boop!"` · `"Zoom!"` · `"Close!"` · `"Phew~"` · `"Yikes!"` |
| Collecting a Seed | `"+"` · `"*" ` · `"Yes!"` |
| Cat Kindness Bonus | `"Meow?"` · `"How kind."` · `"Friendship!"` |
| Wolf Charge activates | `"Uh oh."` · `"Here it comes."` |
| Fox Mirror Jump triggers | `"Copycat!"` · `"Mirror!"` · `"Gotcha!"` |
| Dog Bark projectile fires | `"BORF!"` · `"WOOF!"` |
| Eagle screech cue | `"!"` ← just a single exclamation mark, Undertale style |
| Bloom State activates | `"RUSH!"` · `"BLOOM!"` · `"YES!!!"` |
| Perfect 500m biome | `"FRIEND!"` ← Friendship Bonus |
| Hitting the same hazard twice in a row | `"Again?"` · `"Really?"` (from the hazard) |

### Implementation — `FlavorTextManager.kt`

```kotlin
data class FlavorText(
    val text: String,
    var x: Float,
    var y: Float,
    var alpha: Float = 1f,
    var lifetime: Float = 1.5f,
    var elapsed: Float = 0f,
    val colour: Int = Color.WHITE
)

class FlavorTextManager {
    private val activeTexts = mutableListOf<FlavorText>()

    fun spawn(text: String, x: Float, y: Float, colour: Int = Color.WHITE) {
        activeTexts.add(FlavorText(text, x, y, colour = colour))
    }

    fun update(deltaTime: Float) {
        activeTexts.forEach { ft ->
            ft.elapsed += deltaTime
            ft.y -= 40f * deltaTime          // Float upward
            ft.alpha = 1f - (ft.elapsed / ft.lifetime)
        }
        activeTexts.removeAll { it.elapsed >= it.lifetime }
    }

    fun draw(canvas: Canvas, paint: Paint) {
        activeTexts.forEach { ft ->
            paint.alpha = (ft.alpha * 255).toInt()
            paint.color = ft.colour
            canvas.drawText(ft.text, ft.x, ft.y, paint)
        }
    }

    fun getRandomRestQuote(biome: Biome, lastKillerEntity: EntityType?): String {
        // Priority: killer-specific > biome-specific > universal
        return lastKillerEntity?.let { killerQuotes[it]?.random() }
            ?: biomeQuotes[biome]?.random()
            ?: universalQuotes.random()
    }
}
```

---

## 3. Mercy & Pacifist Systems

This is the mechanical core of the Undertale feel. Avoiding conflict is *rewarded*, not just required.

### 3.1 Close Call Detection — "Mercy Miss"

The collision system distinguishes between two types of near-misses:

```kotlin
enum class CollisionResult {
    HIT,        // Direct collision → REST state
    MERCY_MISS, // Passed within mercy threshold but didn't hit
    CLEAR       // Passed with comfortable clearance
}

fun checkCollision(player: Player, entity: Entity): CollisionResult {
    val expanded = entity.hitbox.inset(-MERCY_THRESHOLD)  // Larger zone around hitbox
    return when {
        player.hitbox.intersects(entity.hitbox) -> CollisionResult.HIT
        player.hitbox.intersects(expanded)       -> CollisionResult.MERCY_MISS
        else                                     -> CollisionResult.CLEAR
    }
}
```

On `MERCY_MISS`:
- A **pink heart** appears briefly at the clearance point.
- `FlavorTextManager.spawn("Close!")` fires.
- +1 to `mercyHeartCount`.
- +50 score bonus.
- Haptic: subtle double-tap pulse.

### 3.2 Mercy Hearts → Spare System

`mercyHeartCount` accumulates across a run. Once thresholds are hit:

| Hearts Collected | Effect |
|---|---|
| 3 Hearts | The next Cat gives ×3 Seeds instead of ×2 |
| 5 Hearts | The next Fox does NOT jump — it sits and watches you pass, then waves |
| 8 Hearts | The next Wolf stops halfway, turns around, and trots offscreen peacefully |
| 10 Hearts | "PACIFIST" banner flashes at top of screen for 3 seconds |

The **Spare system** — when an animal is "Spared":
- It plays a short unique "friendly" animation.
- It gives bonus Seeds.
- A flavour text pops: `"Spared."` or `"Friends now?"`.
- It then gently scrolls off-screen with a wave.

### 3.3 Friendship Bonus — Full-Biome Mercy

If the player completes an **entire 500m biome** without:
- Hitting any animal
- Jumping "too close" to an animal (within 1.5× mercy threshold)

Then:
- Screen flashes a large pink heart for 1 second.
- `FlavorTextManager.spawn("FRIEND!")` fires in large text.
- +300 score bonus.
- The next biome begins with a slightly lower spawn rate (the forest is at peace with you).

---

## 4. Persistent Memory System — `PersistentMemoryManager`

The game **remembers across sessions**. This is the deepest Undertale layer.

### 4.1 Entity Encounter Counters

Every entity type has a persistent encounter counter stored in `SharedPreferences`:

```kotlin
object PersistentMemoryManager {

    data class EntityMemory(
        val entityType: EntityType,
        var totalEncounters: Int = 0,
        var totalSpared: Int = 0,
        var totalHitByPlayer: Int = 0
    )

    fun recordEncounter(type: EntityType) { /* increment totalEncounters */ }
    fun recordSpared(type: EntityType) { /* increment totalSpared */ }
    fun recordHit(type: EntityType) { /* increment totalHitByPlayer */ }

    fun getEncounters(type: EntityType): EntityMemory { /* read from prefs */ }
}
```

### 4.2 Visual Evolution — Entity "Costumes"

Based on encounter count, entities visually change:

| Entity | Threshold | Visual Change |
|---|---|---|
| Cat | 10 encounters | Wears a tiny pixel hat 🎩 |
| Cat | 25 encounters | Hat changes to a flower crown 🌸 |
| Dog | 10 encounters | Wears a tiny bandana |
| Dog | 25 encounters | Wears a tiny bowtie |
| Fox | 15 encounters | Gets a tiny scarf |
| Wolf | 20 encounters | Scar added to sprite (it's survived many encounters too) |
| Hedgehog | 10 encounters | Has tiny sunglasses |
| Owl | 10 encounters (night) | Small crescent moon charm on wing |

These costume changes are drawn as **1–3 pixel overlays** on top of the base sprite. They are purely cosmetic and do not affect hitboxes.

### 4.3 The "Déjà Vu" Effect — Repeat Hazard Memory

The game tracks **which specific type of hazard ended the last run**. If the same type ends the NEXT run too:

- The hazard gets a small text bubble the run AFTER that: `"Again? Really?"` (flavour text floating above it just before it reaches the player)
- The Cactus variant specifically gets a tiny pixel skull-and-crossbones badge if it has ended 5+ runs.

Implementation:

```kotlin
// In SaveManager
fun saveLastKiller(type: EntityType) { prefs.putString("last_killer", type.name) }
fun getLastKiller(): EntityType? { /* read from prefs */ }

// In EntityManager, when spawning the same type as lastKiller:
if (entity.type == lastKiller && persistentMemory.getEncounters(entity.type).totalHitByPlayer >= 2) {
    flavorTextManager.scheduleDelayed("Again?", entity, delaySeconds = 0.8f)
}
```

### 4.4 The Ghost Run — "Spirit of the Best"

A **semi-transparent ghost character** plays back the exact path of the player's personal best run alongside the current run.

- Ghost colour: White/light blue with 40% opacity.
- Ghost has NO hitbox — it is purely visual.
- Ghost plays the same frame animations from the recorded run.
- When the ghost reaches the point where the previous best run ended, it gently fades out.
- If the player overtakes the ghost (surpasses previous best distance), the ghost waves and then vanishes with a sparkle.

#### Ghost Recording System

```kotlin
data class GhostFrame(val x: Float, val y: Float, val animFrame: Int, val timestamp: Float)

class GhostRecorder {
    val frames = mutableListOf<GhostFrame>()
    var elapsed = 0f

    fun record(player: Player, deltaTime: Float) {
        elapsed += deltaTime
        frames.add(GhostFrame(player.x, player.y, player.currentFrame, elapsed))
    }
}

class GhostPlayer(private val frames: List<GhostFrame>) {
    private var elapsed = 0f
    private var frameIndex = 0

    fun update(deltaTime: Float) {
        elapsed += deltaTime
        while (frameIndex < frames.size - 1 && frames[frameIndex + 1].timestamp <= elapsed) {
            frameIndex++
        }
    }

    fun draw(canvas: Canvas, ghostPaint: Paint) {
        if (frameIndex >= frames.size) return
        val frame = frames[frameIndex]
        ghostPaint.alpha = 100  // 40% opacity
        // Draw ghost sprite at frame.x, frame.y with frame.animFrame
    }
}
```

Best-run frames are saved to internal storage as a compact binary array on new personal bests.

---

## 5. The "Undertale Dog" — Annoying Dog Mechanic

The Dog in Forest_Run has a special behaviour variant that triggers 1-in-5 spawns:

### "Running Buddy" Mode

Instead of sitting in the path as a static obstacle:

- The Dog **runs alongside the player** in the same direction for 3–5 seconds.
- It barks in a **musical rhythm** during this time (short SFX pulses that accidentally sound melodic).
- Its bark SFX syncs loosely to the current music tempo.
- While in Running Buddy mode, the Dog has **no hitbox** — it cannot harm the player.
- After 3–5 seconds, the Dog winks (2-frame animation) and dashes off-screen ahead of the player.
- Flavour text on departure: `"See ya!"` or `"Good run!"` or `"BORF!"`

This transforms the Dog from pure hazard to something genuinely charming and memorable.

---

## 6. Music — Leitmotif System

The **Forest Leitmotif** is a 5-note melody:

```
Notes: E - G - A - C - B  (approximate, in a pentatonic scale)
```

This same 5-note phrase appears in every musical track, just in a different arrangement:

| Track | Leitmotif Treatment |
|---|---|
| Garden / Menu | Slow, soft piano. Notes played individually with pauses. |
| Early Run | Hidden in the hi-hat rhythm — tap pattern matches the melody. |
| Mid Run | Flute plays the melody clearly at normal tempo. |
| Wolf Chase / Late Game | Same melody at 2× speed with heavy drums, in a minor key. |
| Bloom State | Full orchestral arrangement — strings + woodwinds, triumphant. |
| REST Screen | Single music box / celesta note at a time, very slow, contemplative. |
| Pacifist Bonus | A brief 4-bar harmony of the full melody plays as a reward jingle. |

This creates a sense that **everything in the forest is the same song**, just felt differently depending on what you're doing.

### Implementation Note for Claude

```
"Implement an AudioManager that layers music tracks. The 'Forest Leitmotif' 
(a 5-note E-G-A-C-B phrase) must appear in every music state. Use MediaPlayer 
with looping and a cross-fade function to transition between states. Music tempo
increases linearly with scrollSpeed: tempo = BASE_TEMPO * (scrollSpeed / BASE_SPEED).
At maximum speed, tempo is 1.8× base."
```

---

## 7. "Non-Enemy" Character Dialogue Bubbles

When the player performs any notable interaction near an animal, a small text bubble pops from that animal for ~1.5 seconds. This gives animals personality:

| Animal | Trigger | Animal Says |
|---|---|---|
| Cat | Player jumps over | `"Meow?"` or `"Phew."` |
| Cat | Player misses kindness | `"..."` |
| Fox | Fox jumps (mirror) | `"Gotcha!"` or `"Heh."` |
| Fox | Player beats the Fox | `"Next time..."` |
| Dog (buddy mode) | Running alongside | `"BORF!"` · `"WOOF!"` · `"Hi!!"` |
| Dog | Bark fires | `"SPEAK!"` |
| Hedgehog | Player jumps just in time | `"Eep!"` |
| Wolf | Charge activates | `"GRRR..."` |
| Wolf | Player clears wolf | `"..."` (stony silence — it's embarrassed) |
| Owl | Player jumps near, owl does NOT dive | `"..."` |
| Owl | Owl dives | `"HOOT!"` |
| Duck | Player ducks under | `"Quack."` |

These are drawn as a simple pixel-art **speech bubble sprite** anchored above the entity, with text overlaid in a small retro font (PressStart2P or a custom pixel font).

---

## 8. New Kotlin Systems Summary

These are the new classes to add to the architecture alongside the base systems:

| Class | File | Purpose |
|---|---|---|
| `FlavorTextManager` | `systems/FlavorTextManager.kt` | Manages floating in-run and REST screen text |
| `PersistentMemoryManager` | `systems/PersistentMemoryManager.kt` | Tracks per-entity encounters, saves to disk |
| `GhostRecorder` | `systems/GhostRecorder.kt` | Records current run path frame-by-frame |
| `GhostPlayer` | `systems/GhostPlayer.kt` | Replays best-run ghost alongside player |
| `MercySystem` | `systems/MercySystem.kt` | Tracks mercy hearts, triggers Spare events |
| `PacifistTracker` | `systems/PacifistTracker.kt` | Monitors per-biome interaction for Friendship Bonus |
| `LeitmotifManager` | `systems/LeitmotifManager.kt` | Cross-fades music layers, tempo scaling |
| `DialogueBubble` | `ui/DialogueBubble.kt` | Entity speech bubble rendering |
| `CostumeOverlay` | `entities/CostumeOverlay.kt` | Draws encounter-based costume overlays |

---

## 9. The Claude 4.6 Prompt Additions

Add this block to any prompt generating the codebase:

```
Final Requirement: Infuse Forest_Run with Undertale's personality.

1. Implement a FlavorTextManager that displays quirky floating messages 
   ('Boop!', 'Zoom!', 'Again?', 'Stay Determined') tied to specific 
   player/entity interactions.

2. Add a PersistentMemoryManager (SharedPreferences-backed) that tracks 
   how many times the player has 'met' each animal. After thresholds 
   (e.g., 10 Cat encounters), draw a costume overlay on the entity sprite.

3. CollisionLogic must distinguish HIT vs MERCY_MISS. MERCY_MISS rewards 
   the player with +50 score, a pink heart particle, and increments the 
   MercySystem counter. At 5 mercy hearts, the next Fox is Spared (waves 
   and leaves without being an obstacle).

4. Implement a GhostRecorder that records the current run's path 
   (x, y, animFrame per frame). On new personal best, save to file. 
   On next run, replay as a semi-transparent GhostPlayer alongside the 
   active player.

5. Implement a FlavorText-based REST screen. Do NOT display 'Game Over'. 
   Display a randomised, context-aware determination quote based on the 
   biome the player was in and the entity that ended the run.

6. The Dog must have a 20% chance 'Running Buddy' behaviour where it 
   runs beside the player harmlessly for 3-5 seconds before waving and 
   departing. During buddy mode, its bark SFX pulses musically.

7. AudioManager must implement a LeitmotifSystem. Every music state 
   (Garden, Run, Bloom, REST) must contain the same 5-note phrase 
   (E-G-A-C-B) in a different arrangement. Music tempo scales linearly 
   with scrollSpeed.

8. Character face is a separate draw layer. Based on PlayerState and 
   nearby entity events, switch the eye sprite (e.g., 'O O' for Eagle 
   dive, '^ ^' for close call smug, '★ ★' at Bloom activation).

9. Movement must feel 'hand-crafted' and snappy, not simulation-accurate. 
   Prioritise charm over physical realism. Jump curve should be slightly 
   'floaty' at apex (reduce gravity by 40% for 0.2 seconds at jump peak).
```

---

## 10. Design Mantras (For Every Implementation Decision)

When in doubt about how to implement something in this system, ask:

1. **"Does it have a voice?"** — Would Toby Fox give this entity a line of dialogue? If yes, add a speech bubble.
2. **"Does it remember?"** — Should the game track this interaction for future runs? If yes, hook into `PersistentMemoryManager`.
3. **"Is it forgiving?"** — Does a near-miss feel like a punishment or an invitation to try again? It should feel like the latter.
4. **"Is it imperfect?"** — Did you reach for a polished animation? Replace it with a 3–4 frame choppy one instead. Charm comes from imperfection.
5. **"Is the leitmotif in there?"** — Every audio cue, even SFX, can hint at the 5-note phrase. A seed pickup that hits E-G is an invisible nod.

---

*Document version: 1.0 | Project: Forest_Run | Last updated: 2026-03-04*
