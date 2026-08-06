# Asset provenance and approval

Forest Run packages fonts, sprites, visual effects, launcher graphics, drawables,
and raw audio/media. File-format validation proves that these inputs are present
and structurally usable; it does **not** prove ownership, licence compatibility,
permission to distribute, attribution compliance, or approval for a public
release.

## Registry

`asset-provenance.json` is the authoritative source-asset coverage registry. A
rule selects a non-overlapping path set and records:

- asset kind;
- review status;
- source or creation record;
- licence or ownership basis;
- required attribution;
- reviewer;
- ISO review date.

The current registry intentionally marks every asset group as
`review-required`. Source, licence, attribution, reviewer, and review date are
blank so the repository does not invent legal conclusions that have not been
verified.

## Validation modes

`scripts/validate_asset_provenance.py --root .` is the normal source gate. It
requires every packaged source asset to match exactly one rule, rejects stale or
overlapping patterns, rejects symlinks, validates the strict JSON schema, and
forbids unreviewed claims inside `review-required` rules.

`scripts/validate_asset_provenance.py --root . --require-approved` is the release
gate. It fails while any matched asset remains `review-required`. Approved rules
must supply source, licence, reviewer, and a non-future `YYYY-MM-DD` review date.
Third-party attribution must be recorded whenever the licence or source requires
it.

`prepare_main_release.sh` invokes the strict mode before store graphics,
metadata, signing, or release-summary generation. Therefore the present source
can continue to compile and undergo technical validation, but it cannot be
prepared as a public release until provenance review is complete.

## Review procedure

For every rule, a reviewer must inspect the actual source files and supporting
records rather than infer provenance from file names or repository history.
The reviewer should:

1. identify the original creator or supplier and retain a stable source or
   creation record;
2. determine the exact licence, assignment, commission agreement, or ownership
   basis;
3. record attribution text and placement requirements;
4. confirm modification, redistribution, commercial-use, and store-distribution
   rights;
5. check whether the same binary appears under multiple paths and whether one
   approval genuinely covers each copy;
6. set the appropriate approved status, reviewer identity, and review date;
7. run both normal and strict validators and preserve the candidate-bound result
   with release evidence.

A successful validator result is evidence that the registry is complete and
internally consistent. It is not legal advice and does not replace counsel or an
independent release reviewer where required.
