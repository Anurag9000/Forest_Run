# Forest Run — Strict input admission in curation and release preparation (2026-09-26)

## Source sweep

All 53 non-test Python scripts under `scripts/` were inspected for JSON evidence admission. The screenshot curator and Play preparer's graphics reader were the remaining permissive `json.loads` paths for externally supplied release manifests. The ordinary `json.loads` used by `scenario_source_contract.py` decodes a Kotlin string literal, not a separate evidence file.

## Change

`curate_store_screenshots` now uses the repository's strict checked-file reader for its 256 KiB curation manifest. `prepare_play_release` uses the same reader for its 64 KiB graphics manifest and additionally requires exactly the canonical feature/promo output pair without duplicate names. Both translate strict-parser errors to their operator-facing `SystemExit` boundary. This complements the canonical wrapper's fuller store-graphics provenance verifier.

New regression tests cover valid input, duplicate JSON keys, nonfinite tokens, oversized files, duplicate output names, and wrong output names. Exact-head CI is the test authority.

## Limits

No creative approval, genuine device screenshot, source rights, production signing, or Play delivery is inferred from parser correctness.
