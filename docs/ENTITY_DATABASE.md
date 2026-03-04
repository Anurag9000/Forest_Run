# Forest_Run — Entity Database

Complete specification for every entity in the game. Each entity overrides a `performUniqueAction()` method. No generic obstacle class — use a **Component-Based System** so each plant and animal has its own distinct AI behaviour.

---

## SECTION 1 — Ground Flora (Timing Hazards)

These are the primary ground-level obstacles. Each plant dictates a **different jump rhythm** and has a unique visual identity. All flora have `SwayComponent` applied (sine-wave wind).

---

### 🌿 Lily of the Valley

| Property | Value |
|---|---|
| **Category** | Ground Flora |
| **Threat Level** | Low |
| **Hitbox** | Tiny (smaller than it appears visually) |
| **Height Class** | Very Low |
| **Biome Affinity** | All biomes; glows in Night Mode |
| **Spawn Frequency** | Common (early game) |

**Unique Identity:** *The Ghost Flower*

**Gameplay Role:**
- Tiny hitbox that punishes careless low jumps.
- In **Night Mode**, it emits a soft white glow — this creates a **visual distraction** near the player's feet, drawing attention away from upcoming obstacles.
- Acts as a **Lure**: A Seed orb is frequently spawned directly above it, tempting the player to jump — potentially into a bird trap above.

**Behaviour (`performUniqueAction()`):**
- At Night: activate glow particle emitter (white/cyan pixels drifting upward).
- Seed spawn rate above this entity ×2.
- No movement. Static obstacle.

**Visual:** Short white bell-shaped flowers in clusters of 2–4 on green stems. Glows faintly in night palette.

**Particle FX:** White sparkle wisps when player jumps over at Night.

---

### 💜 Hyacinth

| Property | Value |
|---|---|
| **Category** | Ground Flora |
| **Threat Level** | Low–Medium |
| **Hitbox** | Medium width, low height |
| **Height Class** | Low |
| **Biome Affinity** | Spring Orchard, Flowering Meadow |
| **Spawn Frequency** | Common |

**Unique Identity:** *The Cluster*

**Gameplay Role:**
- Always spawns in **groups of 3**, spaced close together.
- Requires one **long, sustained jump** (hold input) rather than a quick tap — a quick hop over the first ends in collision with the second.
- If the player **brushes** the side of a Hyacinth cluster without fully jumping, they lose 50% scroll speed for 3 seconds, making them vulnerable to birds.

**Behaviour (`performUniqueAction()`):**
- On player proximity: trigger a group of 3 spawned as one logical unit.
- On brush collision (partial): apply `speedDebuff(0.5f, 3000ms)`.
- No movement. Static group obstacle.

**Visual:** Dense vertical purple/pink flower spikes in a tight row.

**Particle FX:** Purple pollen dust when touched or jumped over.

---

### 🌿 Eucalyptus

| Property | Value |
|---|---|
| **Category** | Ground Flora |
| **Threat Level** | Medium |
| **Hitbox** | Wider at the top than the bottom (trapezoid) |
| **Height Class** | Medium–Tall |
| **Biome Affinity** | Ancient Grove |
| **Spawn Frequency** | Moderate |

**Unique Identity:** *The Leaner*

**Gameplay Role:**
- The sprite is **slanted** (leaning forward at an angle).
- The hitbox is **wider at the top** — punishes players who jump too late ("early jumpers" who expect it to match the sprite base).
- Requires a full standard jump to clear.

**Behaviour (`performUniqueAction()`):**
- `SwayComponent`: Sways **faster** than other plants, with a sharp whipping motion (high `speed`, low `intensity`).
- Emits green leaf particles on sway peak.

**Visual:** Tall, slender pale-grey stalks with silver-green long leaves, leaning at ~15° forward.

**Particle FX:** Green leaf particles detatch and fly into the foreground on wind peak.

---

### 🌸 Vanilla Orchid

| Property | Value |
|---|---|
| **Category** | Ground Flora / Vine |
| **Threat Level** | Medium |
| **Hitbox** | Low base + overhead branch hitbox |
| **Height Class** | Medium (with overhead constraint) |
| **Biome Affinity** | Violet Path |
| **Spawn Frequency** | Moderate |

**Unique Identity:** *The Vine*

**Gameplay Role:**
- Low-hanging orchid vines hang from a branch overhead.
- The player must jump — but **not too high** or they'll hit the overhead branch.
- Creates a **tight vertical window**: jump too low and you hit the vine. Jump too high and you hit the branch.

**Behaviour (`performUniqueAction()`):**
- Two colliders: one for the vine body (low), one for the overhead branch (high).
- Gently sways the vine portion independently from the branch.

**Visual:** Creamy white vanilla orchid flowers on hanging vines, drooping from a dark wooden branch overhead.

**Particle FX:** White sweet-smelling sparkle particles on pass.

---

### 🌵 Cactus

| Property | Value |
|---|---|
| **Category** | Ground Flora |
| **Threat Level** | High (Classic) |
| **Hitbox** | Tight, exact to sprite outline |
| **Height Class** | Tall |
| **Biome Affinity** | All biomes |
| **Spawn Frequency** | High (classic obstacle baseline) |

**Unique Identity:** *The Standard*

**Gameplay Role:**
- The **baseline threat** — imported directly from the classic runner genre.
- No special AI. Static. Jagged and sharp, visually contrasting with the soft forest palette.
- **Instant death** on any contact.
- Serves as the reference difficulty point against which all other obstacles are measured.

**Behaviour (`performUniqueAction()`):**
- No unique action. `performUniqueAction()` is a no-op.
- Does NOT have a `SwayComponent` — it is rigid, contrasting with the living forest around it.

**Visual:** Classic tall green cactus with visible sharp spines. Intentionally harsh and angular.

**Particle FX:** None.

---

## SECTION 2 — Trees (Space Constrictors / Overhead Hazards)

Trees define the **upper and lower boundaries** of the screen. They create spatial challenges — the player can be squeezed vertically, forced to duck, or forced to navigate "windows."

---

### 🌿 Weeping Willow

| Property | Value |
|---|---|
| **Category** | Tree / Canopy Hazard |
| **Threat Level** | Medium |
| **Hitbox** | Long horizontal band covering 60% screen width, mid-height |
| **Height Class** | Full-screen (trunk hidden at top, leaves cascade down) |
| **Biome Affinity** | Ancient Grove, Home Grove |
| **Spawn Frequency** | Moderate |

**Unique Identity:** *The Curtain*

**Gameplay Role:**
- Its long, trailing leaves cascade down and cover **60% of the play area vertically**.
- The player **must duck/slide** to pass under the leaf curtain.
- Crucially, the leaves partially **obscure what is coming next** — the player must react to an obstacle they can't fully see until they're already inside the willow's shadow.

**Behaviour (`performUniqueAction()`):**
- `SwayComponent`: Slow, wide oscillation (low `speed`, high `intensity`). Gives a graceful, heavy feel.
- As player enters the shadow, apply a subtle **darkening overlay** to simulate being under the canopy.
- Leaf particles detach at sway peak and drift into foreground.

**Visual:** Enormous deep-green tree with long drooping curtain branches. Trunk exits screen top. Leaves brush the ground-plane.

**Particle FX:** Long thin leaf strands drift slowly left (foreground layer).

---

### 💜 Jacaranda

| Property | Value |
|---|---|
| **Category** | Tree / Canopy Hazard |
| **Threat Level** | Medium |
| **Hitbox** | High branch zone (upper 30% of screen) |
| **Height Class** | Tall, branches at high elevation |
| **Biome Affinity** | Violet Path |
| **Spawn Frequency** | Moderate |

**Unique Identity:** *The Canopy*

**Gameplay Role:**
- High decorative branches that the player must **duck under** when the branch dips low.
- Periodically drops **purple Jacaranda petals** that act as **visual noise**, partially obscuring the next upcoming obstacle on the ground.
- Creates a mechanic of: "should I jump that cactus I can barely see through the petals?"

**Behaviour (`performUniqueAction()`):**
- `SwayComponent`: Medium speed, medium intensity. Purple petals fall continuously.
- Petal fall rate intensifies with `WindSpeed`.
- Spawns a **petal curtain particle effect** that drifts across the full screen width when passing underneath.

**Visual:** Tall tree with sprawling purple-flowering branches at upper screen. Trunk at right side, canopy spreads left across screen.

**Particle FX:** Constant slow-falling purple pixel petals from canopy. Petal storm during high wind.

---

### 🎋 Bamboo

| Property | Value |
|---|---|
| **Category** | Tree / Vertical Barrier |
| **Threat Level** | High |
| **Hitbox** | Thin but precise vertical columns with gaps |
| **Height Class** | Full screen height |
| **Biome Affinity** | Spring Orchard |
| **Spawn Frequency** | Moderate |

**Unique Identity:** *The Bars*

**Gameplay Role:**
- Spawns as a **dense cluster of 5 vertical stalks** crossing the full screen height.
- Cannot be jumped over — they touch the sky.
- Player must find the **narrow gap** between stalks and time their position to pass through it.
- Requires precision horizontal positioning, not just jump timing.

**Behaviour (`performUniqueAction()`):**
- `SwayComponent`: Stiff, quick jitter (high `speed`, very low `intensity`) — thin bamboo tension.
- Gap position is randomised per spawn.
- Consider adding a subtle "whoosh" audio on pass-through.

**Visual:** Tall green segmented cylindrical stalks. Prominent nodes. Leaves at the very top only.

**Particle FX:** Leaf rustle at sway peaks. Green dust when player barely passes through gap.

---

### 🌸 Cherry Blossoms

| Property | Value |
|---|---|
| **Category** | Tree / Environmental Modifier |
| **Threat Level** | Medium (indirect) |
| **Hitbox** | Cluster of low branches to duck under |
| **Height Class** | Mid-height branches |
| **Biome Affinity** | Spring Orchard |
| **Spawn Frequency** | Moderate |

**Unique Identity:** *The Wind-Maker*

**Gameplay Role:**
- Spawns a **Wind Gust** environmental effect when passed.
- This gust pushes the player character slightly backward (reduces effective clearing distance on next jump).
- Makes upcoming obstacles effectively "arrive faster."
- Also spawns a **constant slow-falling Pink/Purple petal rain** across the full screen during the Spring Orchard biome — "Petal Blinding" — which hides small hazards like Hedgehogs in the visual noise.

**Behaviour (`performUniqueAction()`):**
- On pass: temporarily increase `globalWindSpeed` for 3 seconds.
- Continuously emit pink/magenta petal particles (Petal Drift) that fall slowly left-and-down across screen.
- Increase `SwayComponent` intensity of all other flora during wind gust.

**Visual:** Low spreading branches with dense pink blossom clusters. Delicate and soft-looking.

**Particle FX:** Constant pink petal drift. Gust burst of petals on trigger. Petals fall and accumulate on the ground-plane (decal layer).

---

## SECTION 3 — Birds (Aerial Hazards / Vertical AI)

Birds prevent the player from **jumping constantly** as a defensive strategy. Each bird occupies a specific altitude zone and has unique movement logic.

---

### 🦉 Owl

| Property | Value |
|---|---|
| **Category** | Bird / Aerial Hazard |
| **Threat Level** | Medium |
| **Altitude** | High–mid, perched on branches |
| **Spawn Condition** | Night Mode ONLY |
| **Spawn Frequency** | Common at Night |

**Unique Identity:** *The Night Watch*

**Gameplay Role:**
- Sits **stationary** on a high branch.
- Does **not move unless the player jumps**.
- If the player jumps while the Owl is on screen and within activation range, the Owl wakes and **dives downward**.
- Counter-strategy: **Do not jump** while an Owl is visible. Stay low and run under it.
- Eyes emit a soft **amber glow** in night mode, creating an eerie visual.

**Behaviour (`performUniqueAction()`):**
- `state = SLEEPING` by default.
- On `player.isJumping == true` AND `owl.isOnScreen`: transition to `DIVING`, velocity moves diagonally down-forward.
- Glowing eyes particle: amber/orange points emitting soft halos.

**Visual:** Large round-headed owl with huge amber eyes. Sitting on branch, wings folded. Eyes are lit even at night.

---

### 🦆 Duck

| Property | Value |
|---|---|
| **Category** | Bird / Aerial Hazard |
| **Threat Level** | Low–Medium |
| **Altitude** | Exactly waist/head height |
| **Spawn Condition** | Day / Morning only |
| **Spawn Frequency** | Common |

**Unique Identity:** *The Low-Flyer*

**Gameplay Role:**
- Flies at **exactly head/waist height** — the worst altitude for avoidance.
- **Cannot be jumped over** — it flies too high off the ground to clear with a jump.
- Must be **ducked/slid under**.
- Creates a reflex check: instinct says "jump at obstacle." Duck forces the opposite.

**Behaviour (`performUniqueAction()`):**
- Constant horizontal flight, moderate speed.
- No altitude variation.
- Quack audio cue 0.5s before it enters screen.

**Visual:** Classic stout duck silhouette, colourful plumage (green-headed mallard). Wing flap animation.

---

### 🦅 Eagle

| Property | Value |
|---|---|
| **Category** | Bird / Aerial Hazard |
| **Threat Level** | High |
| **Altitude** | Spawns off-screen top; dives diagonally |
| **Spawn Condition** | Mid–Late game |
| **Spawn Frequency** | Moderate |

**Unique Identity:** *The Hunter*

**Gameplay Role:**
- Spawns off the top of the screen with a **screech audio cue**.
- **Locks onto the player's Y-coordinate** at the moment of spawn.
- Dives in a straight diagonal line aimed at the player's position.
- Highly dangerous because it requires accurately predicting the dive angle.
- Counter-strategy: Change altitude AFTER the Eagle locks on (requires reacting to screech cue).

**Behaviour (`performUniqueAction()`):**
- On spawn: capture `targetY = player.y`.
- Calculate dive vector: `velocity = normalize(targetPos - spawnPos) * diveSpeed`.
- Play screech SFX.
- After passing through play area, exits screen bottom and despawns.

**Visual:** Brown eagle with wide wingspan, talons extended during dive. Sharp angular silhouette.

---

### 🐦 Tits (Blue Tit)

| Property | Value |
|---|---|
| **Category** | Bird / Aerial Hazard — Swarm |
| **Threat Level** | Medium |
| **Altitude** | Mid, varies in a Sine wave pattern |
| **Spawn Condition** | Day |
| **Spawn Frequency** | Moderate |

**Unique Identity:** *The Wave*

**Gameplay Role:**
- Move in a **group of 3–5**, all oscillating up and down in a **Sine wave**.
- The player must **time their jump to pass during the trough** (low point) of the wave.
- Jumping at the peak = collision.
- Forces rhythm-based avoidance.

**Behaviour (`performUniqueAction()`):**
- Group Y-position: `y = baseLine + sin(time * frequency) * amplitude`
- All birds in group share same wave, offset slightly per bird.
- Chirping SFX continuously while on screen.

**Visual:** Small blue-and-yellow birds, flapping rapidly. Cute but grouped dangerously.

---

### 🐦 Chickadees

| Property | Value |
|---|---|
| **Category** | Bird / Aerial Hazard — Erratic |
| **Threat Level** | Medium–High |
| **Altitude** | Unpredictable, changes every ~1 second |
| **Spawn Condition** | Day / Morning |
| **Spawn Frequency** | Moderate |

**Unique Identity:** *The Erratic*

**Gameplay Role:**
- Small birds that **"hop" — suddenly change altitude** every ~1 second.
- Extremely unpredictable — no pattern to learn.
- Groups of 2–4 birds spread across different heights simultaneously.
- Tiny hitbox per bird, but grouped clusters create full coverage.

**Behaviour (`performUniqueAction()`):**
- Timer-based altitude change: every `1.0 ± 0.3s`, snap to a new Y within valid range.
- Add brief "flutter" animation on altitude change.
- Rapid wing-flap animation (high frame rate).

**Visual:** Tiny black-capped chickadees, white cheeks, rapid wing motion.

---

## SECTION 4 — Animals (Ground Hazards / Behavioural AI)

Animals are the most complex entities. They all have **unique reactive AI** — they respond to the player's actions, making them feel alive rather than scripted.

---

### 🐺 Wolf

| Property | Value |
|---|---|
| **Category** | Ground Animal |
| **Threat Level** | High |
| **Hitbox** | Full body, medium height |
| **Speed Pattern** | Starts slow, then charges at 2× speed |
| **Biome Affinity** | Ancient Grove, Stormy Ridge |

**Unique Identity:** *The Sprinter / The Charger*

**Gameplay Role:**
- Appears at the **left edge of the screen**, moving slowly.
- As it reaches the **halfway point**, it plays a **howl animation + audio cue**, then **doubles its sprint speed** toward the player.
- Forces the player to jump earlier than they expect, because the wolf's speed will suddenly change.
- Rewarding to master; terrifying when first encountered.

**Behaviour (`performUniqueAction()`):**
```
if (wolf.x < screenWidth * 0.5f && !wolf.hasCharged) {
    wolf.playAnimation("WOLF_HOWL")
    wolf.velocityX = wolf.velocityX * 2.0f
    wolf.hasCharged = true
}
```

**Visual:** Grey wolf, lean and low to the ground. Running animation. Eyes flash red on howl.

**Particle FX:** Dirt/dust cloud behind paws during charge. 

---

### 🐱 Cat

| Property | Value |
|---|---|
| **Category** | Ground Animal |
| **Threat Level** | Low (rewarding to engage) |
| **Hitbox** | Small, seated |
| **Speed Pattern** | Zero — completely static |
| **Biome Affinity** | All biomes |

**Unique Identity:** *The Decoy / The Zen*

**Gameplay Role:**
- Sits **perfectly still** in the middle of the path.
- Is a very **small obstacle** — easy to jump over.
- If the player successfully jumps over the cat, it gives a **"Kindness Bonus"**: double Seeds + score multiplier ×2.
- It is technically an **optional reward hazard** — the player should be motivated to target it.
- Named "The Decoy" because veteran players will wonder if it's a trap.

**Behaviour (`performUniqueAction()`):**
- Trigger zone detection: if player clears the cat cleanly → `awardKindnessBonus()`.
- Occasionally animates a tail-flick or yawn (ambient, non-threatening).

**Visual:** Small sitting cat (classic tabby or calico). Peaceful expression. Tail curled around paws.

**Particle FX:** Heart/sparkle particles on Kindness Bonus.

---

### 🦊 Fox

| Property | Value |
|---|---|
| **Category** | Ground Animal |
| **Threat Level** | Medium–High (tricky) |
| **Hitbox** | Medium, dynamic during jump |
| **Speed Pattern** | Moderate, steady approach |
| **Biome Affinity** | Violet Path, Flowering Meadow |

**Unique Identity:** *The Mimic / The Mirror Jump*

**Gameplay Role:**
- The Fox has a **Trigger Zone** around it.
- If the player presses Jump while the Fox is within that zone, the Fox **also jumps simultaneously** — "mirroring" the player.
- If both jump at the same time, they **collide in mid-air**.
- Counter-strategy: Wait until the last possible moment (almost touching the Fox) to jump, so the Fox jumps but the player has already cleared it and is descending.

**Behaviour (`performUniqueAction()`):**
```
if (player.isJumping && fox.isWithinRange(player) && !fox.hasJumped) {
    fox.velocityY = -jumpImpulse
    fox.playAnimation("FOX_JUMP")
    fox.hasJumped = true
}
```

**Detection Zone:** Approx. 3× the fox's body width ahead of it.

**Visual:** Russet-coloured fox with white chest, black legs, and bushy tail. Trot animation at approach.

**Particle FX:** Leaf scatter when fox lands.

---

### 🦔 Hedgehog

| Property | Value |
|---|---|
| **Category** | Ground Animal |
| **Threat Level** | High (through difficulty to see) |
| **Hitbox** | Tiny — very small, very low |
| **Speed Pattern** | Fast horizontal scroll |
| **Biome Affinity** | Spring Orchard (hidden in petal visual noise) |

**Unique Identity:** *The Spike / The Friction*

**Gameplay Role:**
- Extremely **small and fast**, difficult to spot against the grass — especially during the Cherry Blossom petal blinding.
- Requires a **short quick tap** jump (not a full hold), because a full jump would over-shoot and land on a bird.
- Critically: hitting a Hedgehog does **not** instantly end the run. Instead, it applies a **speed reduction** effect:
  - Speed drops to 50% for 3 seconds.
  - During this debuff window, the player is highly vulnerable to aerial birds that arrive faster relatively.
  - This creates a "double jeopardy" moment.

**Behaviour (`performUniqueAction()`):**
```
onCollision(player):
    player.applySpeedDebuff(0.5f, 3000ms)
    // Do NOT trigger game over
    playAnimation("HEDGEHOG_CURL")
```

**Visual:** Classic round spiky hedgehog, very low to ground. Brown spines, tiny snout.

**Particle FX:** Brief spike-glint flash on collision.

---

### 🐕 Dog

| Property | Value |
|---|---|
| **Category** | Ground Animal |
| **Threat Level** | Medium |
| **Hitbox** | Body + Bark projectile zone |
| **Speed Pattern** | Stationary; emits projectiles |
| **Biome Affinity** | All biomes |

**Unique Identity:** *The Barker*

**Gameplay Role:**
- The Dog **does not move** — it sits or stands in the path.
- **1 second before appearing** on screen, a **bark audio cue** plays (pre-spawn telegraph).
- When active, it periodically emits a **"Bark" sprite / shockwave** — a horizontal projectile with its own hitbox that travels forward.
- The player must:
  1. **Jump over the dog** (clear its body).
  2. **Also avoid the bark projectile** — which may arrive just as they land.
- Jumping the dog and immediately ducking the bark = advanced combo test.

**Behaviour (`performUniqueAction()`):**
```
// Pre-spawn audio cue
playBarkSFX() // 1 second before Dog.x enters screen

// While on screen, periodic bark
if (timer > barkInterval) {
    spawnBarkProjectile(dog.x - projectileWidth, dog.y + 10)
    timer = 0
}
```

**Visual:** Friendly-looking medium dog (collie or shepherd type). Sitting/standing alert pose. Mouth opens on bark.

**Particle FX:** Shockwave ring sprite on bark. Dust puff.

---

## SECTION 5 — Entity Biome Affinity Map

When a BiomeTransition fires at every 500m, the `SpawnPool` is updated to the biome-specific entity set:

| Biome | Dominant Trees | Dominant Flora | Dominant Animals | Dominant Birds |
|---|---|---|---|---|
| **Home Grove** | Weeping Willow | Cactus, Lily of Valley | Cat, Dog | Ducks, Tits |
| **Spring Orchard** | Cherry Blossom, Bamboo | Hyacinth, Lily of Valley | Hedgehog, Fox | Chickadees, Tits |
| **Ancient Grove** | Weeping Willow, Eucalyptus | Eucalyptus, Cactus | Wolf, Hedgehog | Eagles, Owls (night) |
| **Violet Path** | Jacaranda, Vanilla | Vanilla, Hyacinth | Fox, Cat | Owls, Ducks |
| **Flowering Meadow** | Cherry Blossom | Lily of Valley, Hyacinth, Vanilla | Fox, Cat | Chickadees |
| **Stormy Ridge** | Bamboo, Weeping Willow | Cactus, Eucalyptus | Wolf, Dog | Eagles, Owls |

---

## SECTION 6 — Entity Class Architecture Note

All entity classes must:

1. Extend a base `Entity` class containing: `x`, `y`, `velocityX`, `velocityY`, `hitbox: Rect`, `currentAnimation: String`, `isActive: Boolean`.
2. Override `performUniqueAction()` with the specific AI for their identity.
3. Optionally have a `SwayComponent` (flora/trees only).
4. Use a `ComponentManager` for attaching behaviours (sway, particles, debuff, projectile).

**Do NOT** use a single generic `Obstacle` class for everything.

---

*Document version: 1.0 | Project: Forest_Run | Last updated: 2026-03-04*
