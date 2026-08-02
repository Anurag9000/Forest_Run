# Exact Aggregate Source Binding

The physical-device acceptance aggregate is not authoritative merely because it is valid JSON or satisfies the independent aggregate schema. A report can be internally self-consistent while still being unrelated to the final accepted manifests that supposedly produced it.

The final publication gate therefore requires exact semantic equality between the staged report and a fresh aggregation reconstructed from the final candidate and optional baseline manifests.

## Threat model

Before this contract was added, the publisher already verified:

- strict finite JSON;
- exact aggregate schema and arithmetic invariants;
- candidate commit and artifact identity;
- optional baseline commit and artifact identity;
- manifest, artifact, evidence, and exact-trace validity;
- staged-report byte and inode stability;
- digest-bound protected-source snapshots;
- path, symbolic-link, and hard-link separation;
- atomic same-directory replacement.

Those checks did not prove that every serialized value came from the accepted manifests. A forged staged report could retain the correct commit and artifact while replacing a field that remained independently valid, including:

- candidate version code or certificate metadata;
- anonymized physical-device identities;
- session identifiers;
- measurement distributions;
- threshold headroom;
- comparison-matrix details;
- trace counts or contract summaries that still passed structural checks;
- finite baseline deltas.

An independent schema validator cannot derive these values without reading the source manifests and evidence. Source binding is therefore a separate publication responsibility.

## Final binding rule

`scripts/publish_device_acceptance_aggregate.py` imports the production aggregation core only for final reconstruction. After the staged report and all source evidence have passed their independent gates, the publisher calls:

```python
aggregate_device_acceptance.aggregate(
    candidate_manifest,
    baseline_path=optional_baseline_manifest,
)
```

The returned in-memory payload is the expected report for the final validated sources. The publisher then rereads and independently validates the staged report and requires exact Python object equality:

```python
confirmed_staged_payload == expected_payload
```

Any difference fails publication before `os.replace`.

This comparison binds the staged report to all producer-derived content, including:

- candidate commit, artifact, application ID, version code, and certificate;
- session, evidence-file, device-class, and trace counts;
- exact deterministic trace-contract tuples;
- duration distributions;
- global and per-class metric distributions;
- threshold headroom;
- anonymized physical-device and device-profile identities;
- session identifiers;
- comparison-matrix SHA-256;
- baseline commit and artifact identity;
- global and per-class baseline deltas;
- frozen comparison interpretation semantics.

## Layered trust model

The final gate intentionally uses both independent validation and producer reconstruction.

### Independent validator

`scripts/validate_device_acceptance_aggregate.py` does not import or trust the producer. It protects against:

- unknown or missing keys;
- malformed identities;
- nonfinite or invalid distributions;
- inconsistent counts;
- forged matrix hashes;
- incorrect weighted global means or extrema;
- trace-contract ordering and duplication errors;
- invalid baseline comparison shape and semantics.

### Producer reconstruction

The final producer reconstruction protects against a different class of failure: a staged report that is structurally valid but does not equal the report derived from the final source manifests.

Neither layer replaces the other. Exact source equality alone would trust producer schema behavior, while independent schema validation alone cannot know whether serialized values came from the final evidence.

## Publication ordering

The final source-binding sequence is:

1. stable-read and independently validate the staged aggregate;
2. validate the candidate manifest, signed artifact, evidence files, and exact traces;
3. repeat physical acceptance after trace validation;
4. perform the final digest-bound artifact and evidence hash pass;
5. validate the optional baseline through the same sequence;
6. bind staged candidate and baseline commit/artifact identities;
7. reconstruct the complete expected aggregate from the final manifests;
8. reread the staged inode and require identical bytes, identity, parsed payload, and independent-validator summary;
9. require exact equality between the confirmed staged payload and reconstructed expected payload;
10. require every protected-source snapshot to remain unchanged;
11. recheck all staged/output alias boundaries;
12. atomically replace the final output and fsync its parent directory.

The staged report is reread after reconstruction because reconstruction may be expensive on large evidence bundles. A concurrent staged-file change therefore cannot hide inside the reconstruction interval.

The protected-source snapshots are checked after reconstruction. A source changed during reconstruction cannot silently pass merely because the staged report remained stable.

## Failure behavior

A source-binding mismatch raises `PublicationError` with:

```text
staged aggregate does not exactly match the final validated manifest aggregation
```

The final output is not replaced. Direct publisher callers retain the staged file for forensic inspection. The canonical shell wrapper removes its temporary staged file through its cleanup trap and exits nonzero.

A reconstruction failure is reported separately as an inability to reconstruct the final aggregate from validated manifests.

## Adversarial tests

`scripts/test_publish_aggregate_source_binding.py` covers reports that remain valid under the independent schema but are not source-bound:

- candidate version-code substitution;
- anonymized physical-device identity substitution;
- finite baseline-delta substitution;
- successful publication of an exactly reconstructed candidate/baseline report.

The existing publisher tests continue to cover source mutation, staged mutation, digest mismatch, identity substitution, symlink and hard-link aliasing, cross-directory staging, and final snapshot races.

## Scope and limitations

This contract proves that the published aggregate exactly matches the final manifests and the deterministic producer logic at publication time. It does not prove that:

- the underlying physical sessions actually occurred;
- the devices were correctly classified by an operator;
- the signed artifact was accepted by a store;
- the measurements satisfy future policy changes;
- Android, emulator, physical-device, signing, visual, accessibility, or store-policy gates passed on the repository head.

Those remain separate release requirements.
