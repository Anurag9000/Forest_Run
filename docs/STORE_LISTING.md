# Forest Run — canonical store listing and brand brief

This document is the checked-in human-authored source for Forest Run's public
store story. Candidate-bound files under `release/google-play/metadata/` remain
generated/manual release evidence and are intentionally not treated as durable
product truth.

The wording below must remain consistent with the shipping application. If a
future build adds networking, accounts, ads, telemetry, purchases, portrait
support, or other material behavior, this document and the privacy/store policy
review must change together.

## Product identity

- **Title:** Forest Run
- **Package:** `com.anurag9000.forestrun`
- **Primary orientation:** landscape only
- **Genre:** endless runner / cozy arcade adventure
- **Core promise:** a forgiving forest journey where movement, mercy, memory,
  and a persistent Garden matter more than chasing a raw high score.
- **Online requirement:** none in the current source product.
- **Account requirement:** none in the current source product.
- **Advertising:** none in the current source product.

## Google Play title

```text
Forest Run
```

## Short description

```text
Run gently through a living forest that remembers your choices.
```

## Full description

```text
Begin beneath the willow and follow a changing path through meadow, orchard,
ancient grove, dusk canyon, and night forest.

Forest Run is a handcrafted landscape endless runner built around responsive
movement and a softer kind of progression. Tap for a short jump, hold for more
height, release early to trim the arc, and swipe down to duck beneath danger.
Collect Seeds as you travel. Gather enough and Bloom briefly transforms the
run without taking control away from you.

The forest is more than an obstacle course. Nineteen families of plants,
trees, birds, and animals can become familiar across repeated journeys. Clean
passes and merciful choices shape relationships, remembered encounters,
return moments, story fragments, and the mood that follows you home.

After a difficult run you Rest rather than meet a harsh game-over. Your Seeds,
memories, relationships, unlocked styles, and earlier choices continue into a
persistent Garden. Grow its nine plants, revisit the creatures and traces that
have become part of your story, and open the Forest Journal to see what the
world remembers about your path.

Forest Run includes:

• responsive tap, hold, release, and swipe-down controls
• five authored forest biomes with changing atmosphere
• nineteen encounter families with distinct behaviors and variants
• Seeds, Seed Orbs, Bloom, mercy, kindness, and pacifist progression
• persistent relationships, memories, return moments, and story fragments
• a nine-plant Garden and unlockable wardrobe
• a Forest Journal that reflects your remembered encounters and bonds
• optional audio and haptics plus a reduced-motion setting
• an offline-first experience with no account, ads, or analytics in the current build

Every run is temporary. What the forest remembers is not.
```

## Positioning rules

Public copy should emphasize:

1. **forgiving movement** rather than punishing difficulty;
2. **mercy and relationship continuity** rather than combat;
3. **the persistent Garden and Forest Journal** rather than generic metagame;
4. **five authored environments and nineteen encounter families** rather than
   claiming procedural infinity;
5. **offline-first, no-account play** only while those statements remain true of
   the shipping source and final policy review.

Do not market Forest Run as a Studio Ghibli, Stardew Valley, or other third-party
licensed property. Internal aesthetic references are creative direction, not
store claims, affiliations, endorsements, or trademark permissions.

## Screenshot story

The release screenshot pipeline already captures deterministic source-bound
scenarios. Human curation should tell one coherent story rather than choosing
only visually dense frames.

Recommended eight-image sequence:

1. **Beneath the Willow** — calm opening/menu ritual and title identity.
2. **A Path That Moves With You** — readable ordinary traversal in Meadow or
   Orchard with the player clearly visible.
3. **Meet the Forest** — a clean encounter frame showing one distinctive animal
   or bird without visual clutter.
4. **Bloom** — active Bloom with aura/particles while locomotion remains legible.
5. **Five Changing Biomes** — a strong Dusk Canyon or Night Forest transition.
6. **Mercy Is Remembered** — relationship/mercy feedback after a gentle outcome.
7. **The Garden Changes** — persistent Garden with unlocked growth, visitor, or
   memory trace visible.
8. **Forest Journal** — the memory book showing discovered families and a bond,
   without exposing private/debug evidence.

Screenshots should not use debug overlays, fabricated counters, impossible
progression combinations, unreadable particle density, or marketing text that
covers gameplay-critical visual information.

## Feature graphic creative brief

Create a 1024 × 500 Google Play feature graphic centered on the product's
emotional loop rather than a generic action pose:

- willow silhouette framing one side;
- runner moving toward layered forest depth;
- subtle visual progression from Meadow warmth toward Night Forest;
- a restrained Bloom glow as the focal accent;
- one or two recognizable friendly encounter silhouettes;
- Garden/memory motif hinted at rather than shown as a second unrelated scene;
- clear negative space for the Forest Run wordmark;
- no third-party logos, characters, visual marks, or imitated title treatments.

The final graphic remains a human creative approval item and must be bound to the
accepted candidate through the existing store-evidence workflow.

## App icon creative brief

The adaptive icon should stay recognizable at small launcher sizes. Preferred
concept: a simplified willow leaf / Seed / Bloom mark using the game's own
palette and silhouette language.

Requirements:

- one dominant silhouette, not a miniature gameplay screenshot;
- strong foreground/background separation;
- safe adaptive-icon inset;
- no text required to recognize the mark;
- no dependence on a thin border that launchers may crop;
- distinct from third-party game or animation branding.

The current checked-in launcher icon remains the runtime source until final art
is approved and replaced deliberately.

## Wordmark and visual identity

Use **Forest Run** consistently. A final wordmark should feel warm, handmade,
and legible rather than mimicking another game's typography. Preserve enough
contrast to work over both Meadow-light and Night-Forest-dark backgrounds.

The product palette should continue to derive from the game's forest greens,
seed/gold warmth, flower accents, dusk warmth, and Bloom luminosity. Store art
may simplify those colors but should not introduce a contradictory neon or
combat-forward identity.

## Release metadata handoff

For an accepted candidate, copy/finalize the title, short description, and full
description into:

```text
release/google-play/metadata/en-US/title.txt
release/google-play/metadata/en-US/short-description.txt
release/google-play/metadata/en-US/full-description.txt
```

Then follow `docs/STORE_EVIDENCE.md` and run the candidate-bound metadata
finalizer/verifier. Any claim changed during final policy, creative, or product
review must be changed here first so checked-in product truth and candidate
metadata do not diverge.

## External decisions that this document cannot complete

The repository cannot truthfully complete these by itself:

- Google Play category selection if store taxonomy/policy has changed;
- current content-rating questionnaire responses;
- current target-audience declarations;
- Data Safety submission;
- distribution countries and commercial terms;
- support/contact URLs;
- final privacy-policy HTTPS publication;
- final artwork approval and rights approval;
- final Play Console listing publication.

Those remain accountable release-owner decisions against the exact accepted
candidate.
