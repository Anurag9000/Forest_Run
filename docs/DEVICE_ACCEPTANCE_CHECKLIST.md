# Forest_Run — Device Acceptance Checklist

This is the runtime truth checklist for real-phone verification. A feature is not done until the matching deterministic scenario and the matching device check both pass.

## Rules

- Run these checks after `testDebugUnitTest` and `assembleDebug`.
- Use the deterministic scenario names exactly as listed here.
- If a scenario fails on device, retune before push.
- Record failures in the matching runtime/docs tranche instead of deferring them silently.

## Core Flow Checks

| Scenario | Device Acceptance |
|---|---|
| `OPENING_READABILITY` | Opening 20–30 seconds teach duck, jump, and spacing without confusion. |
| `BLOOM_SHOWCASE` | A first-time player can tell Bloom activated, the world changed, the HUD entered a power state, and rewards transformed. |
| `GHOST_READABILITY` | Ghost never reads like a broken duplicate and never obscures the live runner. |
| `REST_LOOP` | Run failure, rest summary, fade, and Garden return feel continuous and readable. |

## Flora Checks

| Scenario | Device Acceptance |
|---|---|
| `CACTUS_READ` | Cactus silhouette reads instantly and jump timing feels fair. |
| `LILY_GLOW` | Lily glow and seed-lure identity are visible without squinting. |
| `HYACINTH_BRUSH` | Brush-vs-hit difference is obvious in motion. |
| `EUCALYPTUS_WHIP` | Lean/whip read is early enough to feel fair. |
| `ORCHID_WINDOW` | The safe two-window path reads immediately on phone. |

## Tree Checks

| Scenario | Device Acceptance |
|---|---|
| `WILLOW_CURTAIN` | Willow feels scenic and obscuring without becoming unreadable. |
| `JACARANDA_PETALS` | Petal curtain reads as intentional pressure, not visual mud. |
| `BAMBOO_GAP` | Precision threading is clear and fair. |
| `CHERRY_GUST` | Gust-pressure feel is visible and distinct from other trees. |

## Bird Checks

| Scenario | Device Acceptance |
|---|---|
| `DUCK_TEACH` | Duck-lane cue and duck-through timing are unmistakable. |
| `TIT_WAVE` | Rhythm-wave flock reads as one timing pattern. |
| `CHICKADEE_SWERVE` | Flutter path feels erratic but still readable. |
| `OWL_DIVE` | Owl alert, glow, and dive timing are legible in normal phone play. |
| `EAGLE_MARK` | Eagle reticle and mark cue create clear fear/read timing. |

## Animal Checks

| Scenario | Device Acceptance |
|---|---|
| `CAT_KINDNESS` | Cat kindness reward and spare warmth are obvious in normal play. |
| `FOX_MIRROR` | Fox mirror-jump and landing payoff feel playful, not vague. |
| `WOLF_CHARGE` | Howl, charge, and spare payoff are unmistakable. |
| `HEDGEHOG_DEBUFF` | Hedgehog warning, hit, and debuff are fair and visible. |
| `DOG_HAZARD` | Bark projectile timing is readable on first sight. |
| `DOG_BUDDY` | Buddy mode feels memorable and clearly harmless. |

## Sign-Off Gate

- Every scenario above passes on at least:
  - one low-end Android
  - one mid-range Android
  - one high-refresh Android
- Long runs remain readable after repeated Bloom, ghost, and dense-entity sequences.
- If any item fails, do not push a “done” claim for that tranche.
