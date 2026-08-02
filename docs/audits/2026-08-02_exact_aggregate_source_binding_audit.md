# Forest Run — Exact Aggregate Source-Binding Audit

Date: 2026-08-02  
Repository: `Anurag9000/Forest_Run`  
Canonical branch: `main`

This supplement records the continuation after the fail-closed aggregate-publication tranche. It closes the remaining semantic gap between a valid staged report and the final acceptance manifests from which that report claims to have been derived.

It describes checked-in implementation and tests. It does not claim exact-head CI, Android builds, connected-emulator execution, physical-device acceptance, signing, or store delivery.

## Repository topology

- All changes were committed directly to `main`.
- No development branch was created.
- No pull request was created.
- No force-push or history rewrite was used.
- Existing commits and prior audits remain preserved.

## Gap identified

The existing final publisher provided strong identity and filesystem guarantees:

- exact candidate and baseline commit/artifact identity;
- independently validated aggregate schema;
- repeated manifest, artifact, evidence, and trace validation;
- digest-bound final source snapshots;
- staged-report byte and inode stability;
- alias protection and atomic replacement.

However, commit and artifact binding alone did not prove that all aggregate fields were derived from the final manifests.

A staged report could remain independently valid while substituting a source-derived field such as:

- candidate version code;
- certificate metadata;
- an anonymized physical-device ID;
- session IDs or counts that still formed a valid matrix;
- metric values that preserved distribution consistency;
- threshold headroom;
- trace summaries;
- finite baseline deltas.

The independent validator intentionally does not read the acceptance manifests, so it cannot establish source provenance for these values.

## Implementation

### Exact final payload reconstruction

Updated `scripts/publish_device_acceptance_aggregate.py`.

The publisher now imports `aggregate_device_acceptance` as a reconstruction authority after the independent gates have passed. It computes a fresh expected payload from the final candidate and optional baseline manifests.

The comparison is exact over the complete parsed object, not a selected-field allowlist.

The source-bound surface therefore includes:

- complete candidate metadata;
- session, evidence, device-class, and trace counts;
- duration and performance distributions;
- threshold headroom;
- class-level physical-device, profile, and session identities;
- trace-contract sets;
- comparison-matrix SHA-256;
- complete baseline comparison identity and deltas;
- interpretation text.

### Ordering and race protection

The expected payload is reconstructed after final manifest validation and digest-bound source snapshots are created.

After reconstruction, the publisher rereads the staged file and requires:

- the same device/inode identity;
- the same bytes;
- the same parsed payload;
- the same independent-validator summary.

It then requires the confirmed staged payload to equal the reconstructed expected payload and finally rechecks every protected-source snapshot.

This ordering protects both sides of the binding interval:

- staged-report mutation during reconstruction is detected by the second staged read;
- source mutation during reconstruction is detected by the final protected-source snapshot check and by the producer's own manifest/evidence validation.

### Failure semantics

A mismatch raises `PublicationError` before atomic replacement:

```text
staged aggregate does not exactly match the final validated manifest aggregation
```

The direct publisher leaves the staged file for forensic review. The canonical wrapper retains its existing cleanup policy.

## Adversarial coverage

Added `scripts/test_publish_aggregate_source_binding.py`.

The test suite covers:

1. **Candidate version substitution**
   - The forged version remains a valid positive integer.
   - The independent schema accepts its type and range.
   - Final source reconstruction rejects it.

2. **Physical-device identity substitution**
   - The replacement remains a valid lowercase SHA-256 value.
   - Physical-device count and ordering remain valid.
   - The comparison-matrix hash is unaffected because it covers profile IDs, not physical IDs.
   - Final source reconstruction rejects it.

3. **Baseline delta substitution**
   - The replacement remains finite and structurally valid.
   - The independent validator correctly treats it as a legal numeric delta in the absence of baseline source metrics.
   - Final source reconstruction rejects it.

4. **Exact baseline publication**
   - A report reconstructed from matching candidate and baseline manifests publishes successfully.

## Commits in this tranche

- `07510ca26b924a25b06aea382c5ccd78e6f5a7a2` — bind published aggregates to final manifest contents;
- `5b5bdb801bba29887f81acf2819aaf91f30076f1` — test exact aggregate source binding;
- `6a20fe95195e1134e5a935fa00e3a2b334aa5a0b` — document exact aggregate source binding;
- this audit commit records the completed tranche.

## Validation performed

- The reconstructed publisher replacement was syntax-compiled locally before commit.
- The new test module was syntax-compiled locally before commit.
- The checked-in publisher import and exact-comparison region were fetched again from `main` and statically reviewed.
- The independent validator's baseline-delta behavior and returned summary contract were fetched and reviewed to ensure the tests target a real source-binding distinction rather than duplicating schema checks.

A complete repository checkout remains unavailable in the execution container because `github.com` DNS resolution fails. Therefore the complete exact-head Python, Android unit, Robolectric, lint, release-build, and packaging suites are not claimed.

## Remaining isolated source debt

The previous runtime debt remains unchanged:

1. wire `FamiliarityWarmthScoring` into `RelationshipArcSystem.familiarityWarmth()`;
2. wire `SafeProgressionArithmetic` into both `ReturnMomentsSystem` call sites;
3. add finite-coordinate admission to `MainMenuScreen.onTap()`;
4. harden public finite-delta boundaries in `ParallaxBackground` and `GameView.update()`;
5. correct `SaveManager.hasGhostRun()` so recoverable `AtomicFile` backup state is recognized;
6. decompose `GameView` and consolidate distributed persistence ownership through behavior-preserving seams.

`SaveManager.hasGhostRun()` was re-inspected in this tranche. It still checks only the base ghost file, while `loadGhostRun()` correctly recognizes either the base file or its `.bak` recovery file. The fix remains a narrow call-site change inside a large persistence owner and has not been falsely claimed.

## External gates still required

- exact-head host/unit/Robolectric/lint/release/package execution;
- connected-emulator execution;
- real older, midrange, high-refresh, cutout/aspect, and tablet testing;
- frame, memory, crash, ANR, thermal, battery, and long-session evidence;
- real signing credentials and signed minified artifact;
- internal-store delivery and exact artifact receipt confirmation;
- final artwork, animation, screenshot, metadata, audio, haptic, reduced-motion, touch, and accessibility review;
- privacy, data-safety, content-rating, target-audience, and current store-policy review.

## Classification

The aggregate publication path is now source-bound in addition to being independently validated, digest-bound, alias-protected, and atomically published. Forest Run remains a feature-rich alpha until the remaining runtime debt and all external release gates are completed on one exact candidate.
