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
- Current: classic static hazard now has stronger warning staging and repeated-killer flavor response.
- TODO: finish final device-proofing and broader memory/cosmetic payoff.

## Trees

### Weeping Willow

- Role: curtain hazard and core visual icon of the game.
- Dream traits: forces ducking, obscures what comes next, creates canopy mood.
- Current: implemented obstacle, iconography, and stronger curtain-read pass.
- TODO: finish obscured-read pressure and scenic dominance.

### Jacaranda

- Role: purple-canopy atmosphere tree with petal drift.
- Dream traits: visual noise, mood, overhead tension.
- Current: obstacle now has a clearer petal-curtain read, stronger pass reward, and better encounter feel.
- TODO: finish final device-proofing and fuller canopy spectacle.

### Bamboo

- Role: vertical-barrier precision hazard.
- Dream traits: narrow gap threading, jitter sway.
- Current: stronger gap-readability pass now exists.
- TODO: finish signature threat identity and final device validation.

### Cherry Blossom

- Role: wind-making environmental modifier.
- Dream traits: petal blinding, gust influence, gentle beauty with danger.
- Current: obstacle now has clearer gust staging, better pass reward, and stronger petal-storm identity.
- TODO: finish final device-proofing and full gust-pressure delivery.

## Birds

### Owl

- Role: night watcher that punishes reckless jumping.
- Dream traits: sleeping perch, reactive dive, eerie glow.
- Current: dive trigger logic, clearer telegraphing, stronger alert ring, and stronger night-glow mood now exist.
- TODO: finish final device-proofing and deeper repeated-memory payoff.

### Duck

- Role: low flyer that teaches ducking instead of jumping.
- Dream traits: unmistakable head-height obstruction and clear quack cue.
- Current: larger readable hazard and duck-lane cue now exist.
- TODO: finish final on-device tuning and clearer quack/event payoff.

### Eagle

- Role: hunter dive threat.
- Dream traits: screech cue, target lock, diagonal punishment.
- Current: dive hazard now has a stronger target-lock read, clearer target zone, and better reward payoff on a clean pass.
- TODO: finish final device-proofing and stronger dramatic cue understanding.

### TitGroup

- Role: rhythm-wave flock.
- Dream traits: group sine motion and timing-based reads.
- Current: wave readability and clean-pass payoff are stronger.
- TODO: finish device-proofing and stronger rhythm identity.

### ChickadeeGroup

- Role: erratic aerial chaos.
- Dream traits: unpredictable altitude shifts and cute panic energy.
- Current: erratic altitude and clean-pass payoff are clearer.
- TODO: finish final charm and clarity tuning on device.

## Animals

### Wolf

- Role: sprinter/charger.
- Dream traits: howl, then charge; intimidating but readable.
- Current: howl/charge code, stronger threat presentation, and relationship-aware charge cueing now exist.
- TODO: finish device-proofing and deepen the spare payoff.

### Cat

- Role: kindness-rewarding decoy.
- Dream traits: tiny optional reward hazard, kindness bonus, spare-like warmth.
- Current: kindness reward logic, stronger reward presentation, and relationship-aware near-miss cueing now exist.
- TODO: deepen repeated-friend payoff and device-proof the feel.

### Fox

- Role: mirror-jump trickster.
- Dream traits: sly counter-jump, playful line delivery, mercy-based wave-off.
- Current: mirror-jump logic, stronger detection/clean-pass presentation, and history-aware landing payoff now exist.
- TODO: finish final telegraph charm and repeated-memory payoff.

### Hedgehog

- Role: small friction threat that debuffs instead of killing.
- Dream traits: fair but sneaky, visible enough to feel earned, not cheap.
- Current: speed-debuff trap and stronger readability/fairness pass now exist.
- TODO: finish final size/timing validation on device.

### Dog

- Role: barker and occasional running buddy.
- Dream traits: bark projectile timing plus lovable buddy variant.
- Current: both modes exist, are materially clearer in play, and now use relationship-aware buddy dialogue with longer bonded buddy runs.
- TODO: make buddy mode truly memorable and finish bark timing validation on device.

## Mandatory Entity TODOs

- DONE: enlarge entity screen presence materially.
- DONE: reduce empty space between encounters materially.
- TODO: verify all entity-specific behaviors on actual device.
- TODO: ensure flavor text and mercy feedback are visible whenever personality is supposed to land.
