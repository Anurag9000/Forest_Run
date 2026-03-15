# Forest Run Game Design Document

## 1. Product Identity

`Forest Run` is meant to be a high-feel 2D Android endless runner with a cottagecore forest identity, a strong personality layer, and a restorative meta-loop. The goal is not only to dodge obstacles. The goal is to make the player feel like they are running through a living, reactive forest that notices them, rewards grace, and slowly grows with them across many sessions.

## 2. Original Dream Vision

The original dream was:

- a lush Ghibli x Stardew Valley inspired forest runner
- a ritualized menu where the heroine sits under a willow, stands, and then starts running
- five biomes that each feel like a different emotional chapter
- highly readable but cute entities with unique silhouettes, reactions, and mechanics
- seeds that matter both within the run and across the whole save
- Bloom as a huge audiovisual payoff state
- soft failure, reflective rest, and a garden that makes each run meaningful
- an Undertale-like mercy and personality layer: near misses, flavor text, sparing, ghost memory, repeated encounter memory, determination-like tone

## 3. Intended Full Player Journey

### 3.1 Cold Start

- Open app into a personal garden scene.
- See the heroine resting under a tree.
- Hear ambient forest audio.
- First tap stands her up.
- Second tap begins the run.

### 3.2 Run Start

- Early pacing should feel readable and inviting.
- Entities should be big enough to recognize on a phone instantly.
- Seeds should be visible and motivating.
- HUD should make score, distance, Bloom, seeds, and hearts obvious.

### 3.3 Mid-Run Journey

- Biomes shift every 500m.
- Music, color, danger, and spawn identity evolve.
- Animals introduce personality, not just threat.
- Near misses create mercy hearts and flavor payoff.
- Clean passes and kind interactions feed bonus systems.

### 3.4 Bloom Catharsis

- Seeds fill the Bloom meter.
- Bloom activates as a dramatic power spike.
- Invulnerability, spectacle, audio swell, and motion clarity should all be felt immediately.

### 3.5 Failure And Return

- A bad collision should feel like a soft fall, not a cheap hard fail.
- The game should preserve rhythm through dying, rest, game-over, and restart.
- The next run should feel informed by memory: better understanding, ghost replay, persistence, garden growth.

### 3.6 Meta-Loop

- Lifetime seeds unlock a growing garden.
- The garden should feel chill, rewarding, and emotionally opposite to the run.
- The player should want “one more run” for both score and garden progress.

## 4. Core Run Mechanics

### Controls

- Tap: short jump
- Hold: higher jump
- Swipe down: duck or slide

### Progression

- Distance increases speed and score.
- Spawn frequency ramps with difficulty.
- Biomes rotate every 500m.
- Mercy hearts accumulate from near misses.
- Seeds build toward Bloom.

### Bloom

Dream behavior:

- visibly obvious seeds
- clearly readable Bloom meter
- activation at 8 seeds
- roughly 6 seconds of invulnerability
- clear audio/visual transformation
- meaningful “I am unstoppable right now” feeling

## 5. Entity Design Principles

Every entity should satisfy all of the following:

- readable at phone scale
- cute and distinct in silhouette and motion
- mechanically unique enough that a player can describe it from memory
- emotionally flavored enough that it feels like a forest inhabitant, not filler
- staged often enough that the player can actually perceive its personality

## 6. World Structure

The biome cycle is:

- Meadow
- Orchard
- Ancient Grove
- Dusk Canyon
- Night Forest

Each biome should alter:

- sky and ambient color
- foliage and ground feel
- music tone
- spawn pool emphasis
- danger profile
- mood and atmosphere

## 7. Personality Layer

The original dream included:

- flavor text
- mercy hearts
- spare thresholds
- determination/rest quotes
- repeated encounter memory
- costumes unlocked by encounter counts
- ghost replay of best run
- dialogue-like reactions from creatures
- a world that feels like it notices your behavior

## 8. Current Implemented State

Implemented in code today:

- menu scene with stand-then-run flow
- gameplay with jump, hold, duck, collisions, score, distance
- five-biome tint system and biome-based spawn pools
- 19 entities with different code paths
- seed orbs, Bloom meter, Bloom activation, ghost replay, garden persistence
- mercy hearts and flavor text infrastructure
- animal-specific behavior in several classes

## 9. Current Gap To Vision

The current build is still short of the dream in several important ways.

### Readability Gap

- User-reported: birds, plants, trees, and animals feel too small on phone.
- User-reported: spacing is so wide that encounters do not feel alive or appreciable.
- Consequence: personality and uniqueness are effectively invisible even if the code contains them.

### Ghost Clarity Gap

- User-reported: the ghost runner reads like a broken second runner.
- Consequence: it confuses core control readability and can make the live run feel visually wrong.

### System Visibility Gap

- User-reported: seeds, Bloom, Bloom window, HUD values, hearts, and garden loop do not read as obvious during real play.
- Consequence: the connective tissue of the game is hidden from the player.

### Personality Gap

- User-reported: creatures do not yet feel cute, unique, interactive, and Undertale-like enough.
- Consequence: the most distinctive promised layer is not landing strongly enough in runtime experience.

### Atmosphere Gap

- The world still lacks the full density of particles, art direction, environment life, and bespoke scene craft imagined in the original vision.

## 10. What Must Become True

To satisfy the original vision, all of the following must become true in actual play:

- entities are comfortably readable on a phone without squinting
- each entity’s unique behavior is obvious within normal gameplay
- ghost replay never harms clarity
- seeds and Bloom are constantly legible and motivating
- mercy hearts and flavor text visibly sell the personality layer
- garden progression is emotionally meaningful, not hidden
- biomes feel like mood chapters
- the whole session arc feels authored, not accidental
