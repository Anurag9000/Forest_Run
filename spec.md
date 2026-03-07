# Forest Run - Official Game Specification Document

## Overview
**Forest Run** is a 60 FPS, high-fidelity endless runner designed for Android. The game blends precise, low-latency mechanical platforming with a dynamic, morality-based pacifist storyline. The player guides a female protagonist through an endless, progressively difficult scrolling forest, dodging hazards, sparing wildlife, and harvesting seeds to rebuild a ruined sanctuary.

---

## 1. Core Player Mechanics & Physics 🏃‍♀️

The player physics are tuned for tight, responsive, "Mario-style" controls without floatiness.

### Jump Dynamics
* **Inputs:** Tapping the right side of the screen initiates a jump.
* **Squash & Stretch:** Upon tapping, the character logically squashes for 2 frames (`JUMP_START`), generating explosive upward force, before stretching into the air. 
* **Mario-Abort Mechanic (Variable Height):** The player controls the height of the jump. If the right side of the screen is released *while the character is still rising*, the upward velocity is instantly cut in half, smoothly halting the jump arc.
* **Apex Hover:** At the exact peak of a jump arc, gravity is briefly reduced by 30% for a split second, allowing mid-air maneuvering. 
* **Velocity Sync:** The player's running animation natively scales its frame rate based on the screen's literal scroll-speed (interpolating between 24 FPS to 32 FPS matching parallax acceleration).

### Ducking Dynamics
* **Inputs:** Pressing and holding the left side of the screen initiates a duck/slide.
* **Mechanical Shift:** Instantly drops the physical collision hitbox size by 60%, allowing the player to safely pass underneath low-flying birds and elevated tree branches.

---

## 2. The Morality/Storyline System 🦊

The game features an organic, invisible morality system based on how the player interacts with the forest wildlife.

### The "Mercy" Mechanic
* Animals (Wolves, Foxes, Dogs, Cats) act as organic obstacles pacing the ground.
* **HitBox Dynamics:** If the player collides with their core hitbox, it triggers a `STUMBLE` state — a non-fatal, momentary loss of control accompanied by a red screen flash, pushing the player backwards briefly with temporary invincibility.
* **The Pacifist Route:** A secondary, slightly larger invisible 'Mercy' aura surrounds every animal. If the player brilliantly dodges the animal (clearing it by a few pixels without hitting it), a "Mercy" is registered.
* **The Friendship Bonus:** Accumulating 5 Mercy points completely changes the world state. Animals will cease attacking, sit peacefully on the ground as you pass, spawn dialogue bubbles (e.g., Fox: *"Fine."*), and grant the player an immense "Friendship" score bonus.

---

## 3. The Entity Kingdom 🦅

The game spawns diverse, pattern-based enemies requiring split-second decisions.

### Airborne Hazards
* **Ducks & Eagles:** Fly in a straight line at head-height. The player must Duck.
* **Tits & Chickadees:** Fly in a flock utilizing a vertical sine-wave pattern. The player must time their jump to pass beneath the crest or above the trough of the wave.
* **The Owl:** Sleeps perched high in the UI. If the player jumps wildly while an Owl is on screen, the noise awakens it, triggering a direct, heat-seeking divebomb attack right at the player's last location.

### Biological & Botanical Hazards
* **Wolves & Dogs:** Aggressive ground patrols. The player must Jump.
* **Foxes:** Feature 'Mirror Jump' AI. If the player jumps too early while a fox is approaching, the fox will counter-jump into the air to intercept the player mid-flight!
* **Flora (Cacti, Brambles):** Static ground hazards. Every plant physically sways and bops left-to-right as it passes through the world via the procedural `SwayComponent`.

---

## 4. The Economy & Meta-Progression 🌱

The game does not use micro-transactions. Progression is tied to gameplay loops.

### The Run Economy
* **Seeds:** Golden seeds spawn floating in the air. Collecting them acts as the primary currency.
* **The Bloom Meter:** Collecting 8 consecutive seeds in a single run fills the Bloom Meter. When triggered, the player enters an invincible, high-speed 'Bloom State', emitting a radiant golden aura that effortlessly obliterates any hazards touched for 6 seconds.
* **Scoring:** Measured in horizontal distance travelled + multipliers for pacifist runs and consecutive seed collections. High scores are physically recorded. 
* **The Ghost Run:** Upon restarting after a death, a transparent phantom avatar plays out the exact timestamps and jump physics of your historical best-run, racing against you.

### The Garden (The Shop)
* Entering the 'Garden' from the Main Menu opens the persistent Meta-Loop.
* Players spend lifetime seeds to permanently plant/unlock 9 beautiful foliage tiers (Lilies, Hyacinths, Sakura). Unlocking a plant permanently adds it to the randomly generated background pool of their future runs, dynamically changing the visual aesthetics of their world over time. 

---

## 5. Audio & Visual Impact (Juice) 🌟

The world feels alive, responding violently and beautifully to the player's actions.

* **Parallax Scrolling:** Four distinct high-art pixel layers (Sky, Far Mountains, Treeline, Foreground) scroll at independent mathematical rates for immense depth.
* **Day & Night Cycles:** The world transitions smoothly through 6 time epochs (Dawn, Day, Afternoon, Dusk, Twilight, Night), blending the skies via hardware shaders.
* **Particles & Shakes:** Hitting hazards causes the Android screen to violently shake and vibrate the physical device (Haptics), backed by an internal zero-allocation Particle Array that sprays 512 independent physics objects for dirt, dust, and visual impact.
* **Dynamic Leitmotif Audio:** The music Engine algorithmically shifts and cross-fades audio samples smoothly depending on the time of day and whether the player is resting in the Garden or undergoing a high-speed run. 

---
*Generated: March 2026. Codebase is completely finalized, stable, and production-ready.*
