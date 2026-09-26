# Forest Run — Strict screenshot and graphics schema-version types (2026-09-26)

## Concrete finding

Four manifest/sidecar validators compared `schemaVersion` to integer 1 using ordinary Python equality. In Python `True == 1`, so a JSON boolean `true` passed the version check despite the contract requiring a numeric schema revision. This affected the store graphics manifest, curated capture session, raw capture session, and per-image capture sidecar. The strict JSON decoder already disallowed duplicate keys/nonfinite values, but does not itself impose each schema's field types.

## Correction and regression

Each reader now requires `type(schemaVersion) is int` as well as the supported revision value. Tests run a known valid fixture followed by `true`, `false`, floating-point 1.0, string "1", and missing/null revision. The existing error class and schema format are preserved; no source or generated evidence is silently rewritten.

## Verification and limits

Use the exact resulting commit's Python and Android CI to establish executable test status. This reinforces input validity, not creative-rights approval, real capture provenance, or Play delivery.
