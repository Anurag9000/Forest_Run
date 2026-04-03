# Store Screenshot Capture Plan

Raw screenshots are captured from deterministic in-app scenarios through:

- `scripts/capture_store_screenshots.sh`

The final curated store set is built from those raw captures through:

- `scripts/curate_store_screenshots.py`
- `curation_manifest.json`

## Planned Set

1. `01-opening.png`
   - scenario: `OPENING_READABILITY`
   - purpose: readable first-run scene

2. `02-bloom.png`
   - scenario: `BLOOM_SHOWCASE`
   - purpose: Bloom transformation and HUD state

3. `03-ghost.png`
   - scenario: `GHOST_READABILITY`
   - purpose: ghost readability without clutter

4. `04-rest.png`
   - scenario: `REST_LOOP`
   - purpose: restorative rest panel and carry-home tone

5. `05-cat.png`
   - scenario: `CAT_KINDNESS`
   - purpose: warm kindness payoff

6. `06-dog.png`
   - scenario: `DOG_BUDDY`
   - purpose: buddy-mode escort payoff

7. `07-owl.png`
   - scenario: `OWL_DIVE`
   - purpose: night readability and memory-rich encounter

8. `08-jacaranda.png`
   - scenario: `JACARANDA_PETALS`
   - purpose: scenic biome identity and petal spectacle

## Final Curation Rule

Do not finalize the store screenshot set until:

- the capture comes from a real connected device
- system bars are hidden
- the frame is readable at phone scale
- the scene shows a distinct product truth, not a redundant variant
- the curated `final/` set and `CURATED_SET.md` are generated from the raw captures
