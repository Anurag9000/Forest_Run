# Deterministic visual regression diagnostics

Forest Run's screenshot pipeline already proves that raw and curated store captures belong to one exact candidate. `scripts/compare_visual_regression.py` adds a separate diagnostic layer for comparing deterministic scenario captures against a deliberately selected visual baseline.

This is **not** a substitute for human art-direction review, accessibility acceptance, physical-device testing, or Play Store presentation approval. Raster similarity can catch accidental movement, missing sprites, layout drift, broken particles, wrong state, and broad rendering changes; it cannot decide whether an intentional visual change is good.

## What the comparator measures

For every screenshot listed in the existing curation manifest, it verifies that the baseline and candidate images:

- exist as regular non-symlink PNG files;
- remain within bounded file-size and pixel-count limits;
- decode successfully;
- have identical dimensions;
- remain byte-stable while decoded.

It then reports three complementary metrics:

1. **mean absolute channel delta** — average absolute RGB difference across all channels and pixels;
2. **changed-pixel ratio** — fraction of pixels whose maximum RGB-channel delta exceeds the configured per-channel noise tolerance;
3. **p95 pixel maximum channel delta** — the 95th percentile of per-pixel maximum RGB-channel difference.

A comparison passes only when all three configured budgets pass. This avoids relying on exact pixel equality, which is too fragile for legitimate device/rendering variance, while still detecting both localized and broad regressions.

## Example

Compare curated deterministic captures:

```bash
python3 scripts/compare_visual_regression.py \
  --manifest release/google-play/screenshots/curation_manifest.json \
  --baseline-dir release/visual-baselines/approved \
  --candidate-dir release/google-play/screenshots/final \
  --filename-field final_file \
  --per-channel-tolerance 4 \
  --max-mean-absolute-channel-delta 1.5 \
  --max-changed-pixel-ratio 0.01 \
  --max-p95-pixel-max-channel-delta 4 \
  --output release/evidence/visual-regression.json
```

For raw deterministic captures, use `--filename-field raw_file` and point the two directories at compatible raw screenshot sets.

Exit status is:

- `0`: all scenarios are within the declared diagnostic budgets;
- `1`: one or more valid screenshot comparisons exceed a budget;
- `2`: the comparison itself is invalid or untrustworthy, such as a missing/malformed image, dimension mismatch, symlink, invalid manifest, or invalid threshold.

The JSON result records per-scenario image digests and metrics, aggregate maxima, and the exact list of scenarios exceeding tolerance.

## Baseline discipline

A visual baseline should be treated as reviewed evidence, not as a file that silently updates whenever CI changes.

Recommended flow:

1. capture deterministic scenarios from one known candidate;
2. verify the capture session with the existing raw/curated screenshot validators;
3. have a human reviewer approve the intended appearance;
4. retain that approved set as the comparison baseline with candidate provenance;
5. compare later deterministic captures against it;
6. inspect every reported regression;
7. replace the approved baseline only when the visual change is intentional and separately reviewed.

Do not make CI automatically overwrite baselines after a mismatch. That would convert the regression detector into a self-approving mechanism.

## Choosing tolerances

The defaults (`4` channel levels, `1.5` mean RGB delta, `1%` changed pixels, p95 `4`) are conservative starting values, not universal release policy. Calibrate tolerances from repeated captures of an unchanged build on the exact capture environment before using them as a blocking gate.

For a strictly deterministic emulator/reference renderer, use all-zero thresholds to require exact raster equality. For heterogeneous physical devices, visual comparison is better treated as a reviewer aid unless a device-specific baseline and empirically justified tolerance have been established.

## Relationship to release readiness

Visual regression output is intentionally not a mandatory release-readiness kind. The final release chain still requires candidate-bound screenshot manifests and human acceptance. If a visual-regression report materially participates in a go/no-go decision, include that exact report in the release evidence index as additional candidate-specific evidence and record the reviewer decision that interpreted it.
