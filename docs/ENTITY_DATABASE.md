# Forest_Run — Entity Database (Restored)

This document restores the original entity planning detail while marking what still must be built or improved in play.

Historical biome-affinity variants and exact old asset-sheet expectations remain traceability-only unless they still improve the shipped game. Runtime entity closure now means ordinary-play readability, personality delivery, and real-device proof.

## Entity Rule

No generic obstacle feeling is acceptable. Every entity should have a distinct silhouette, distinct motion, distinct gameplay role, and distinct emotional personality.

## Ground Flora

### Lily of the Valley

- Role: tiny low hazard, ghost-flower lure, often paired with tempting seed placement.
- Dream traits: glows at night, distracts near the player’s feet, creates tricky seed traps.
- Current: glow/readability pass now exists with stronger visual identity, explicit lure-to-trap staging, stronger local glow dominance, a clearer lure descent, a more explicit low trap band, and a clearer reward beat.
- TODO: finish on-device validation.

### Hyacinth

- Role: clustered rhythm hazard that encourages longer jumps or risky brush interactions.
- Dream traits: grouped feel, pollen, partial-brush punishment, distinctive timing.
- Current: brush/mercy zone, clustered pulse read, explicit three-beat rhythm identity, clearer brush-vs-hit staging, and stronger reward text now exist.
- TODO: finish on-device validation.

### Eucalyptus

- Role: forward-leaning plant that punishes late reads.
- Dream traits: fast whip sway, trapezoid feel, leaf drama.
- Current: stronger leaning/readability pass now exists with layered gust guides, an earlier whip read, a clearer lean lane, a more explicit danger band, and clearer pass feedback.
- TODO: finish on-device validation.

### Vanilla Orchid

- Role: vertical-window obstacle with vine and overhead branch pressure.
- Dream traits: safe window between low and high colliders.
- Current: safe-window readability pass now exists with explicit top/bottom hazard staging, a narrower true overlap window, explicit guide markers, and a collision-safe center thread.
- TODO: finish on-device validation.

### Cactus

- Role: classic runner baseline hazard.
- Dream traits: rigid, harsh silhouette, contrast against the softer forest.
- Current: classic static hazard now has stronger warning staging, history-aware payoff text, clearer reward feedback, persistent clean-pass memory, a named `Needle Bloom` Garden trace, and carried-home cactus-sign payoff.
- TODO: finish on-device validation.

## Trees

### Weeping Willow

- Role: curtain hazard and core visual icon of the game.
- Dream traits: forces ducking, obscures what comes next, creates canopy mood.
- Current: implemented obstacle, iconography, stronger curtain-read pass, clearer shadow-zone lane staging, a denser canopy silhouette, and an explicit duck lane now exist.
- TODO: finish on-device validation.

### Jacaranda

- Role: purple-canopy atmosphere tree with petal drift.
- Dream traits: visual noise, mood, overhead tension.
- Current: obstacle now has a clearer petal-curtain read, a layered canopy halo, a cascading petal veil, a clearer underside lane, stronger pass reward, and better encounter feel.
- TODO: finish on-device validation.

### Bamboo

- Role: vertical-barrier precision hazard.
- Dream traits: narrow gap threading, jitter sway.
- Current: stronger gap-readability pass now exists with clearer lane guidance, a featured seam, tighter secondary gaps, and stronger payoff.
- TODO: finish on-device validation.

### Cherry Blossom

- Role: wind-making environmental modifier.
- Dream traits: petal blinding, gust influence, gentle beauty with danger.
- Current: obstacle now has clearer gust staging, a true pressure band, a broader storm veil, clearer crosswind staging, better pass reward, and stronger petal-storm identity.
- TODO: finish on-device validation.

## Birds

### Owl

- Role: night watcher that punishes reckless jumping.
- Dream traits: sleeping perch, reactive dive, eerie glow.
- Current: dive trigger logic, stronger same-shadow alert cueing, a visible memory ring, a more familiar-night clean pass, and stronger night-glow mood now exist.
- TODO: finish final device-proofing only.

### Duck

- Role: low flyer that teaches ducking instead of jumping.
- Dream traits: unmistakable head-height obstruction and clear quack cue.
- Current: larger readable hazard, staged quack call, explicit low-lane answer, and stronger answered-the-quack pass reward now exist.
- TODO: finish final device-proofing only.

### Eagle

- Role: hunter dive threat.
- Dream traits: screech cue, target lock, diagonal punishment.
- Current: dive hazard now has a clearer dive corridor, a held-mark prompt, and a stronger clean-line pass on top of the earlier target-zone and lock-on readability work.
- TODO: finish final device-proofing only.

### TitGroup

- Role: rhythm-wave flock.
- Dream traits: group sine motion and timing-based reads.
- Current: wave readability, staged beat count-in, visible trough guide, and a stronger kept-the-beat reward now exist.
- TODO: finish final device-proofing only.

### ChickadeeGroup

- Role: erratic aerial chaos.
- Dream traits: unpredictable altitude shifts and cute panic energy.
- Current: erratic altitude, featured lead-bird charm cue, clearer flutter pocket, and a warmer clean-read reward now exist.
- TODO: finish final device-proofing only.

## Animals

### Wolf

- Role: sprinter/charger.
- Dream traits: howl, then charge; intimidating but readable.
- Current: howl/charge code, stronger threat presentation, relationship-aware charge cueing, more respectful spare-history lines, a visible stand-down aura/trail, and a stronger earned-respect spare reward now exist.
- TODO: finish final device-proofing only.

### Cat

- Role: kindness-rewarding decoy.
- Dream traits: tiny optional reward hazard, kindness bonus, spare-like warmth.
- Current: kindness reward logic, stronger reward presentation, relationship-aware near-miss cueing, more personal repeated-friend lines, a shared-quiet aura, and a stronger familiar pass reward now exist.
- TODO: finish final device-proofing only.

### Fox

- Role: mirror-jump trickster.
- Dream traits: sly counter-jump, playful line delivery, mercy-based wave-off.
- Current: mirror-jump logic, stronger detection/clean-pass presentation, more knowingly playful repeat-memory lines, a brighter trail aura, and a stronger remembered-the-trick pass reward now exist.
- TODO: finish final device-proofing only.

### Hedgehog

- Role: small friction threat that debuffs instead of killing.
- Dream traits: fair but sneaky, visible enough to feel earned, not cheap.
- Current: speed-debuff trap, a true fair-hop arming window, a visible low-lane read, clearer warning-stage messaging, and a stronger clean-clear reward now exist.
- TODO: finish final device-proofing only.

### Dog

- Role: barker and occasional running buddy.
- Dream traits: bark projectile timing plus lovable buddy variant.
- Current: both modes exist, are materially clearer in play, use a fuller escort-style buddy dialogue sequence, leave a visible celebration trail during harmless runs, and now end bonded buddy visits with a more distinct finale burst and reward.
- TODO: finish final device-proofing only.

## Mandatory Entity TODOs

- DONE: enlarge entity screen presence materially.
- DONE: reduce empty space between encounters materially.
- DONE: formal device acceptance checklist now mirrors deterministic scenario coverage in [docs/DEVICE_ACCEPTANCE_CHECKLIST.md](/home/anurag-basistha/Projects/TODO/Forest_Run/docs/DEVICE_ACCEPTANCE_CHECKLIST.md).
- TODO: verify all entity-specific behaviors on actual device.
- TODO: ensure flavor text and mercy feedback are visible whenever personality is supposed to land.
