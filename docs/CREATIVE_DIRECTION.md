# Forest Run — final creative direction

This document defines the authored presentation target for Forest Run. It is a
product specification, not evidence that current assets have passed artistic,
legal, accessibility, or hardware review. Asset byte identity and provenance are
owned separately by `docs/CREATIVE_ASSET_PROVENANCE.md` and the release evidence
pipeline.

## 1. Creative north star

Forest Run should feel like a small living place that remembers the player, not
a generic obstacle course with a cottagecore skin.

Five principles govern every creative choice:

1. **Warmth before spectacle.** Beautiful moments may be luminous, but they
   should not become visual noise.
2. **Readability before decoration.** Encounter silhouette, lane, telegraph and
   player motion must remain understandable at gameplay speed.
3. **Mercy has a visual language.** Gentle outcomes should feel intentionally
   different from collision, panic or punishment.
4. **Memory changes presentation.** Relationships, return moments, Garden traces
   and the Forest Journal should make persistence visible.
5. **Original identity.** Forest Run may draw from broad cozy, pastoral,
   storybook and pixel-art traditions, but must not imitate protected characters,
   logos, signature compositions, or title treatments from third-party works.

## 2. Visual language

### Shape

- Player: compact, readable silhouette with a clearly visible head/body direction.
- Friendly creatures: rounded or flowing secondary shapes where species permits.
- Hazards/pressure: sharper or more vertical shapes, but never horror-coded.
- Bloom: organic curves, petals, motes and soft expansion rather than lasers or
  combat effects.
- Garden: denser detail is allowed because it is a low-speed sanctuary, but
  interactive plants and wardrobe cards still need immediate hierarchy.

### Palette

Use a restrained family of:

- forest and moss greens;
- warm Seed/gold accents;
- flower-specific pink, violet, blue and cream;
- dusk amber/russet;
- moonlit blue/indigo;
- Bloom luminosity as a rare high-value accent.

Avoid a permanently saturated neon palette. Bloom should feel exceptional because
ordinary scenes leave enough tonal headroom for it.

### Contrast hierarchy

At any gameplay frame the priority is:

1. player silhouette;
2. immediate encounter/telegraph;
3. reachable Seed or Seed Orb;
4. ground/lane boundary;
5. UI status;
6. decorative environment.

Decorative particles, fog, petals, parallax and lighting must never invert that
hierarchy.

## 3. Player animation contract

The final player presentation should have deliberate authored reads for:

- idle / willow-rest posture;
- run cycle;
- short jump ascent;
- held/full jump ascent;
- apex/fall;
- duck;
- stumble/recovery;
- terminal Rest transition;
- Bloom-active locomotion;
- restart/return transition where shown;
- each supported costume overlay without destroying the base silhouette.

Animation timing should communicate state before it communicates ornament. A
costume must not hide feet, body direction, collision-relevant posture, or the
read of a duck/jump.

## 4. Encounter-family presentation

All nineteen families already have canonical runtime ownership. Final art should
preserve species identity while making gameplay roles readable.

### Flora

Cactus, Lily of the Valley, Hyacinth, Eucalyptus and Vanilla Orchid should feel
rooted and environmental. Motion comes primarily from sway, blossom, leaf or
ambient response rather than locomotion.

### Trees

Weeping Willow, Jacaranda, Bamboo and Cherry Blossom are environmental characters
as much as obstacles. Their canopy/stem motion should frame the lane rather than
hide it.

### Birds

Duck, Tit, Chickadee, Owl and Eagle require distinct flight/body silhouettes.
Small birds should not become unreadable specks; large birds should not cover the
player for long periods.

### Animals

Cat, Fox, Wolf, Hedgehog and Dog should have clearly distinguishable body language.
The repository already contains a four-frame Wolf sprite; final work is a quality,
readability and rights decision, not a missing-file task.

For every family, final approval should consider:

- ordinary/default appearance;
- any authored deterministic variants;
- mercy/pass reaction;
- hit/stumble readability where applicable;
- Bloom conversion response;
- relationship warmth at deeper familiarity where presentation changes;
- Night Forest visibility without adding a glowing outline to everything.

## 5. Five-biome identity

### Meadow

Open, breathable, morning/early-day warmth. It establishes the visual baseline and
should be the least visually demanding environment.

### Orchard

More vertical rhythm and cultivated repetition, with fruit/blossom cues and warmer
mid-ground detail.

### Ancient Grove

Older trunks, deeper layering, moss and filtered light. Richer atmosphere, but the
lane remains visually stable.

### Dusk Canyon

Warm-to-cool transition, stronger horizon shapes, rock/mesa rhythm and longer
shadows. Avoid turning the player into a dark silhouette against equally dark
terrain.

### Night Forest

Moonlit contrast, sparse luminous accents and stronger depth separation. Do not
solve readability by making every object self-luminous.

Transitions should feel like a journey through one connected world rather than
five hard scene swaps.

## 6. Bloom presentation

Bloom lasts six seconds and remains orthogonal to locomotion. Its presentation
must reinforce that mechanical truth.

Required layers, as supported by the current runtime systems:

- aura/lighting lift;
- controlled particle trail/motes;
- HUD/status change;
- audio transition or musical layer;
- haptic onset where enabled;
- encounter conversion reaction;
- short afterglow rather than an abrupt visual cut.

Bloom must not:

- obscure collision-relevant geometry;
- make the player appear frozen or teleported;
- imply invulnerability rules that are not mechanically true;
- restart visually when additional Seeds arrive during the same activation;
- create strobe-like high-frequency flashes.

Reduced-motion mode should retain state clarity while removing or damping the most
motion-heavy flourish.

## 7. Garden direction

The Garden is a sanctuary and memory surface, not a shop screen placed after a
run.

Its final art should make these systems visible where possible:

- nine-plant growth progression;
- relationship-derived visitors/home presence;
- persistent traces and history marks;
- memory-page influence;
- route/pacifist atmosphere changes;
- equipped costume/wardrobe identity;
- return moments;
- fireflies, petals, mist, lantern or canopy changes already driven by the
  sanctuary planner.

Progress should read from the scene before the player has to inspect numbers.

## 8. Forest Journal direction

The Journal is the quiet archival counterpart to the Garden. Its presentation
should feel like opening a field notebook, but platform accessibility and long-form
legibility take priority over decorative skeuomorphism.

The current implementation deliberately uses Android platform views for reliable
scrolling and semantic text. Future styling may add custom backgrounds, dividers,
small family portraits or discovered stamps only if it preserves:

- native reading order;
- large-font resilience;
- contrast;
- full content descriptions;
- undiscovered-content concealment;
- no synthetic coordinate-only interaction.

## 9. Music direction

Music should be adaptive but restrained. The preferred architecture is layered
leitmotif/state response, not constant track replacement.

Suggested musical roles:

- Willow/Home motif — recognizable emotional anchor;
- Meadow — open/light version of the motif;
- Orchard — warmer rhythmic variation;
- Ancient Grove — deeper texture and space;
- Dusk Canyon — more forward pulse without becoming combat music;
- Night Forest — sparse, suspended version;
- Bloom — temporary harmonic/layer lift that resolves cleanly;
- Rest — decompressed, forgiving cadence;
- Garden — home variation that can tolerate long idle listening.

Relationship or return moments may use tiny motif fragments, but avoid turning
every state change into a musical sting.

## 10. Sound-effect direction

SFX should prioritize information and material feel:

- jump/landing;
- duck or close-pass air movement where useful;
- Seed and Seed Orb collection;
- Bloom onset/afterglow;
- mercy/relationship acknowledgement;
- stumble;
- terminal hit/Rest transition;
- Garden purchase/growth;
- wardrobe equip;
- UI navigation;
- restrained species/environment cues.

Differentiate positive events by timbre and envelope rather than simply increasing
volume. Repeated collection sounds need enough variation or spacing to avoid
fatigue.

## 11. Haptic vocabulary

Haptics are optional and must remain meaningful with audio disabled.

Use a small consistent vocabulary:

- **light tick:** Seed/UI acknowledgement;
- **soft double pulse:** mercy/relationship positive event;
- **medium bloom pulse:** Bloom activation;
- **short rough pulse:** stumble;
- **longer weighted pulse:** terminal hit;
- **soft growth pulse:** Garden purchase/growth or major home milestone.

Do not haptically mirror every particle or score event. Haptics should remain
informative rather than continuous texture.

## 12. Branding

The app icon, wordmark, feature graphic and screenshots should share the same
visual language as the runtime game. `docs/STORE_LISTING.md` owns the store-facing
copy and graphics briefs.

A useful icon concept is a simplified willow leaf / Seed / Bloom mark. The final
wordmark should be original, warm and highly legible. Do not imitate another
studio/game's title lettering.

## 13. Asset replacement rules

When final creative assets replace current bytes:

1. preserve runtime filenames only when their semantic role is unchanged;
2. update frame-count naming/contracts when animation geometry changes;
3. update source ownership/catalogue declarations if assets move;
4. rerun structural asset validation;
5. regenerate candidate creative inventory;
6. bind creator/source/license evidence to the exact new hashes;
7. regenerate store graphics/screenshots when their source material changed;
8. invalidate prior candidate evidence rather than carrying approvals forward.

Do not encode fake provenance fields simply to satisfy a validator.

## 14. What remains an accountable human decision

No source commit can truthfully decide that current creative material is final.
The release owner still must approve, for the exact candidate:

- sprite quality and animation appeal;
- telegraph and silhouette clarity;
- environmental composition;
- Bloom restraint/readability;
- Garden emotional payoff;
- Journal visual finish;
- music composition/arrangement/mastering;
- SFX mix and repetition fatigue;
- haptic strength and cadence;
- reduced-motion presentation;
- icon/wordmark/store graphic quality;
- creator rights, licences and attribution for every shipping creative asset.
