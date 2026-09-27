# Forest Run — Pure collision probes for all authored encounter families (2026-09-27)

## Whole-family source check

All nineteen first-party `onCollision` methods across animals, birds, flora and trees were inspected. Eighteen already use read-only contact geometry or local flags; `Hedgehog.onCollision` was the exception: each padded near-miss probe modified a retained `mercyRect` via `RectF.set`. Although the current helper returned the same normal contact answer, a collision query is specified as pure, and retaining mutable geometry in the probe introduces unnecessary state coupled to a frequently repeated arbitration path.

## Correction and prevention

Remove the unused mutable `mercyRect` cache and use the existing allocation-free `Entity.intersectsExpanded(player.hitbox, hitbox, mercyPad)` helper. Physical overlap still returns STUMBLE, untouching padded proximity remains provisional MERCY_MISS, and anything farther is NONE. The Dog's nested bark-projectile near-miss probe also used to allocate a new `RectF` on each query; it now delegates to the same pure expanded-rectangle predicate, avoiding per-projectile/per-frame heap churn. A Robolectric regression isolates the bark from the Dog body, checks repeated near misses leave its rectangle unchanged, and tests direct-HIT and distant-NONE boundaries. No spawn timing, warning duration, sound, reward or asset changes. Existing Robolectric Hedgehog tests cover warned/armed physical contact, provisional safe passage and later HIT precedence. New Python source-contract tests inventory all nineteen species and reject mutable rectangle setters and presentation/persistence calls inside collision probe bodies. The Hedgehog-specific guard asserts the shared allocation-free helper is used.

Exact resulting HEAD's Python, JVM, lint/release and connected evidence is required; source checking is not real device/human fairness approval.
