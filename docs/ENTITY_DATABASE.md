# Forest_Run — Entity Database (Restored)

This document restores the original entity planning detail while marking what still must be built or improved in play.

## Entity Rule

No generic obstacle feeling is acceptable. Every entity should have a distinct silhouette, distinct motion, distinct gameplay role, and distinct emotional personality.

## Ground Flora

### Lily of the Valley

- Role: tiny low hazard, ghost-flower lure, often paired with tempting seed placement.
- Dream traits: glows at night, distracts near the player’s feet, creates tricky seed traps.
- Current: glow/readability pass now exists with stronger visual identity and payoff.
- TODO: finish stronger seed-trap staging and final on-device tuning.

### Hyacinth

- Role: clustered rhythm hazard that encourages longer jumps or risky brush interactions.
- Dream traits: grouped feel, pollen, partial-brush punishment, distinctive timing.
- Current: brush/mercy zone and clearer visual brush read now exist.
- TODO: make the clustered rhythm identity stronger on device.

### Eucalyptus

- Role: forward-leaning plant that punishes late reads.
- Dream traits: fast whip sway, trapezoid feel, leaf drama.
- Current: stronger leaning/readability pass now exists.
- TODO: finish leaf drama and final high-threat tuning.

### Vanilla Orchid

- Role: vertical-window obstacle with vine and overhead branch pressure.
- Dream traits: safe window between low and high colliders.
- Current: safe-window readability pass now exists.
- TODO: finish final staging so the two-zone read feels unmistakable in live play.

### Cactus

- Role: classic runner baseline hazard.
- Dream traits: rigid, harsh silhouette, contrast against the softer forest.
- Current: classic static hazard exists.
- TODO: stronger iconic readability and repeated-memory integration.

## Trees

### Weeping Willow

- Role: curtain hazard and core visual icon of the game.
- Dream traits: forces ducking, obscures what comes next, creates canopy mood.
- Current: implemented obstacle, iconography, and stronger curtain-read pass.
- TODO: finish obscured-read pressure and scenic dominance.

### Jacaranda

- Role: purple-canopy atmosphere tree with petal drift.
- Dream traits: visual noise, mood, overhead tension.
- Current: obstacle exists.
- TODO: stronger petal curtain spectacle and memorable encounter feel.

### Bamboo

- Role: vertical-barrier precision hazard.
- Dream traits: narrow gap threading, jitter sway.
- Current: stronger gap-readability pass now exists.
- TODO: finish signature threat identity and final device validation.

### Cherry Blossom

- Role: wind-making environmental modifier.
- Dream traits: petal blinding, gust influence, gentle beauty with danger.
- Current: obstacle exists.
- TODO: restore stronger wind-gust and petal-storm personality.

## Birds

### Owl

- Role: night watcher that punishes reckless jumping.
- Dream traits: sleeping perch, reactive dive, eerie glow.
- Current: dive trigger logic exists.
- TODO: stronger night mood and clearer telegraphing.

### Duck

- Role: low flyer that teaches ducking instead of jumping.
- Dream traits: unmistakable head-height obstruction and clear quack cue.
- Current: low-flying hazard exists.
- TODO: make it larger and more readable on phone.

### Eagle

- Role: hunter dive threat.
- Dream traits: screech cue, target lock, diagonal punishment.
- Current: dive hazard exists.
- TODO: stronger dramatic cue and player understanding.

### TitGroup

- Role: rhythm-wave flock.
- Dream traits: group sine motion and timing-based reads.
- Current: group hazard exists.
- TODO: make flock behavior visually appreciable during play.

### ChickadeeGroup

- Role: erratic aerial chaos.
- Dream traits: unpredictable altitude shifts and cute panic energy.
- Current: group hazard exists.
- TODO: stronger charm and clarity.

## Animals

### Wolf

- Role: sprinter/charger.
- Dream traits: howl, then charge; intimidating but readable.
- Current: howl/charge code exists.
- TODO: make the drama impossible to miss in actual play.

### Cat

- Role: kindness-rewarding decoy.
- Dream traits: tiny optional reward hazard, kindness bonus, spare-like warmth.
- Current: kindness reward logic exists.
- TODO: make the reward and personality actually read to players.

### Fox

- Role: mirror-jump trickster.
- Dream traits: sly counter-jump, playful line delivery, mercy-based wave-off.
- Current: mirror-jump logic exists.
- TODO: stronger telegraphing and cuteness.

### Hedgehog

- Role: small friction threat that debuffs instead of killing.
- Dream traits: fair but sneaky, visible enough to feel earned, not cheap.
- Current: speed-debuff trap exists.
- TODO: fix fairness through size/readability.

### Dog

- Role: barker and occasional running buddy.
- Dream traits: bark projectile timing plus lovable buddy variant.
- Current: both modes exist in code.
- TODO: make buddy mode memorable and bark timing readable.

## Mandatory Entity TODOs

- DONE: enlarge entity screen presence materially.
- DONE: reduce empty space between encounters materially.
- TODO: verify all entity-specific behaviors on actual device.
- TODO: ensure flavor text and mercy feedback are visible whenever personality is supposed to land.
