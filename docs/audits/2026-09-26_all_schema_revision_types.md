# Forest Run — Strict version typing for remaining JSON evidence readers (2026-09-26)

## Forensic finding

A complete 53-file survey of non-test Python scripts identified seven further source-side schema revision checks that used ordinary Python numeric equality. Since `True == 1`, JSON boolean `true` could enter a manifest declared to require version integer 1; `visual_baseline_provenance` additionally compared reconstructed dicts, where boolean true and integer one compare equal. Other candidate-bound format readers already use typed integer helpers or validated structures.

The affected owning boundaries are:
- input-latency threshold and measured-report validators;
- performance threshold validator;
- source asset provenance registry;
- declared dependency/supply-chain inventory;
- candidate release evidence index;
- public store metadata manifest;
- reviewed visual-baseline identity descriptor.

## Remediation and regression

Each reader now requires an exact Python `int` (the decoded JSON integer type) before comparing supported revision values. Their existing JSON schemas, report output, hash rules and operator-facing error classes remain unchanged. Seven focused test functions exercise all listed entry points against boolean true/false, floating-point 1.0, string "1", and null (input latency covers both its threshold and report readers). Valid manifest coverage remains in each pre-existing suite. Exact-head CI is the execution authority.

## Boundary

Format validation is not proof of a genuine measurement, approved asset origin, safe dependency, reviewed creative baseline, or store-delivered signed artifact. Final physical/human/legal/Play gates remain open.
