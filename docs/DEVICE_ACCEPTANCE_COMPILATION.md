# Forest Run — Strict Device Acceptance Compilation

The physical-device acceptance manifest is generated evidence, not a checklist that can be self-certified. Use the strict wrapper rather than invoking the compiler alone:

```bash
bash scripts/compile_device_acceptance_bundle.sh \
  evidence/device-acceptance/device-acceptance-draft.json \
  evidence/device-acceptance/device-acceptance.json \
  evidence/device-acceptance/device-acceptance-summary.json
```

An explicit UTC generation timestamp can be supplied as the fourth argument:

```bash
bash scripts/compile_device_acceptance_bundle.sh \
  evidence/device-acceptance/device-acceptance-draft.json \
  evidence/device-acceptance/device-acceptance.json \
  evidence/device-acceptance/device-acceptance-summary.json \
  2026-08-01T12:00:00Z
```

The wrapper performs one fail-closed chain:

1. Strictly parses the draft JSON, rejecting duplicate keys, `NaN`/infinities, overflowed floating-point values, UTF-8 BOMs, oversized files, non-object roots, and excessive nesting.
2. Invokes `compile_device_acceptance.py`, which preserves separately captured store-delivery and per-session build identities, hashes the signed candidate and every evidence file with bounded streaming reads, validates the complete matrix, and publishes the final manifest plus summary as one rollback-safe transaction.
3. Strictly parses both generated outputs.
4. Independently invokes `validate_device_acceptance.py` against the published manifest and real evidence files.

## Minimum matrix

A policy cannot shrink below these device classes:

- `older_phone`
- `midrange_phone`
- `high_refresh_phone`
- `cutout_phone`
- `tablet`

It also cannot omit these scenarios:

- `ordinary_play_15m`
- `all_entities`
- `bloom_dense`
- `lifecycle_recovery`
- `settings_accessibility`
- `garden_transactions`
- `ghost_persistence`

One physical device identity cannot satisfy multiple device classes. Scenario evidence paths must be unique, and path aliases or hard links cannot make one physical file count more than once. The signed candidate artifact itself cannot double as scenario evidence.

## Manual and approval semantics

Every manual check must be `pass`, except `haptics`, which may be `not_applicable` only when the hardware genuinely lacks the capability. Unknown manual-check and approval keys are rejected. Final reviewers must identify at least two distinct people after Unicode normalization and case folding.

The compiler does not derive store or session identity from the candidate. Testers must record the observed package, version, artifact digest, certificate digest, installation source, commit, and signing state. The validator rejects mismatches; it does not overwrite them into agreement.

## Bounds and mutation safety

- draft/manifest: at most 16 MiB;
- signed candidate artifact: at most 4 GiB;
- each evidence file: at most 2 GiB;
- files are hashed in chunks and rejected if size, modification time, or inode changes during the read;
- output and summary must share the draft directory so relative evidence paths remain stable;
- output files cannot overwrite the draft or each other;
- a failure during second-file publication restores both previous outputs and removes transaction debris.

## What a valid result means

A valid result proves that the supplied evidence is internally consistent, complete for the mandatory matrix, cryptographically bound to one signed internal-track candidate, and approved according to the declared schema. It does not create measurements, prove that a tester actually performed an action, replace current store-policy review, or convert an alpha into a release candidate without the underlying physical sessions and approvals.
