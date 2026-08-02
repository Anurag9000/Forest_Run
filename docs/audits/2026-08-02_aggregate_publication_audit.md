# Forest Run — Fail-Closed Aggregate Publication Audit

Date: 2026-08-02  
Repository: `Anurag9000/Forest_Run`  
Canonical branch: `main`

This supplement records the continuation that hardened physical-device acceptance aggregation from a validated producer into a staged, independently validated, source-revalidated publication transaction. It describes checked-in behavior and tests. It does not claim that exact-head CI, Android builds, connected-emulator execution, physical-device acceptance, signing, or store delivery has completed.

## Repository topology and change policy

- Every change in this tranche was committed directly to `main`.
- No development branch was created.
- No pull request was created.
- The branch listing observed before implementation contained only `main`.
- No force-push or history rewrite was used.
- Existing history and prior remediation work were preserved.

## Problem statement

The existing aggregation producer already required accepted manifests, exact deterministic traces, immutable manifest reads, protected output paths, and exact anonymized hardware matrices. Three independent integrity questions still needed explicit treatment:

1. Could a syntactically valid but semantically forged aggregate be accepted merely because the producer emitted it?
2. Could a signed artifact or evidence file change after aggregation validation but before the report became final?
3. Could the staged report itself change while the publisher performed expensive source validation?

The completed implementation separates producer, independent consumer validation, and final publication so each concern has a distinct fail-closed boundary.

## Implemented components

### 1. Independent aggregate contract validator

Added `scripts/validate_device_acceptance_aggregate.py`.

The validator does not import or trust `aggregate_device_acceptance.py`. It independently enforces:

- exact root keys and optional-baseline shape;
- schema version and `status = valid`;
- exact candidate identity keys;
- canonical Forest Run application ID;
- lowercase SHA-1/SHA-256 formats;
- the mandatory five physical-device classes;
- sorted, unique physical-device, profile, and session identifiers;
- physical/profile/session count consistency;
- exact metric-key surfaces;
- finite numeric distributions with `minimum <= mean <= maximum`;
- per-distribution counts equal to their class or global session counts;
- global minima and maxima reconstructed from class distributions;
- weighted global means reconstructed from class means and counts;
- nonnegative threshold headroom;
- `comparison_matrix_sha256` independently reconstructed from sorted class profile IDs and session counts;
- nonempty, sorted, unique exact trace contracts;
- trace count not exceeding evidence-file count;
- baseline comparison matrix and trace bindings;
- mandatory per-class baseline delta surfaces;
- finite baseline deltas;
- the frozen interpretation sentence that defines positive deltas as regressions.

Unknown keys fail closed. The validator therefore detects accidental schema drift, post-processing corruption, and several classes of forged report.

### 2. Independent validator tests

Added `scripts/test_validate_device_acceptance_aggregate.py`.

The suite covers:

- candidate-only reports;
- baseline-comparison reports;
- producer-output compatibility;
- unknown root and candidate fields;
- forged comparison-matrix hashes;
- forged weighted global means;
- identifier ordering, uniqueness, and count failures;
- mandatory-class substitution;
- negative threshold headroom;
- impossible trace/evidence counts;
- baseline matrix drift;
- baseline trace drift;
- frozen interpretation drift;
- strict duplicate-key JSON rejection.

### 3. Two-phase canonical wrapper

Updated `scripts/aggregate_device_acceptance_bundle.sh`.

The wrapper now:

1. performs strict manifest preflight;
2. independently validates exact scenario traces;
3. creates a unique staged file in the final output directory;
4. aggregates into the staged path rather than the final path;
5. strict-parses the staged report;
6. runs the independent aggregate validator;
7. invokes the final publisher with candidate and optional baseline manifests;
8. removes the staged file through a shell cleanup trap when publication does not consume it.

Same-directory staging preserves atomic replacement semantics.

### 4. Final fail-closed publisher

Added and iteratively hardened `scripts/publish_device_acceptance_aggregate.py`.

The publisher rejects:

- staged or output symbolic links;
- staged/output path equality or inode aliasing;
- cross-directory staging;
- candidate/baseline manifest path or inode aliasing;
- staged/output aliases to any protected manifest, signed artifact, or evidence file;
- invalid staged aggregate schema or arithmetic;
- candidate commit/artifact substitution;
- missing or unexpected baseline comparison;
- baseline commit/artifact substitution;
- source mutation during acceptance or trace validation;
- source mutation after the final snapshot;
- staged-report mutation during final source validation.

Its final sequence is deliberately ordered:

1. stable-read and independently validate the staged aggregate;
2. validate the candidate manifest and hash every declared source;
3. independently validate exact traces against Kotlin source;
4. run physical acceptance a second time, rehashing every artifact and evidence file after trace validation;
5. require both acceptance summaries to be identical;
6. require manifest bytes, device, inode, size, and modification timestamp to remain stable;
7. bind staged candidate/baseline identities to the final manifest summaries;
8. snapshot every protected source's device, inode, size, and modification timestamp;
9. reread the staged aggregate and require identical bytes, inode identity, parsed payload, and independent-validation summary;
10. require every protected-source snapshot to remain unchanged;
11. recheck all path and inode alias boundaries;
12. atomically replace the destination with the validated staged inode;
13. fsync the destination directory.

Digest and semantic validation provide authenticity. Final stat snapshots provide an additional race detector between the last content validation and replacement.

### 5. Publisher adversarial tests

Added and expanded `scripts/test_publish_device_acceptance_aggregate.py`.

The suite covers:

- successful candidate-only atomic publication;
- source mutation after staging;
- non-trace evidence mutation during trace validation, detected by the second acceptance pass;
- staged aggregate mutation during final source validation;
- protected-source mutation after snapshot creation;
- staged candidate identity substitution;
- baseline presence mismatch;
- baseline identity substitution;
- output hard-link aliasing to protected evidence;
- output symbolic links;
- cross-directory staging;
- candidate/baseline manifest aliasing;
- direct publisher failure preserving the staged file for forensic inspection.

### 6. Documentation synchronization

Updated `docs/DEVICE_ACCEPTANCE_AGGREGATION.md` to describe:

- producer, validator, and publisher responsibilities;
- the complete thirteen-step canonical flow;
- independent schema and arithmetic reconstruction;
- post-trace evidence rehashing;
- staged byte/inode stability;
- protected-source snapshots;
- adversarial tests;
- filesystem-error semantics.

The documentation distinguishes pre-replacement validation failure from a low-level filesystem failure during or after `os.replace`: a destination may already be visible if directory `fsync` fails, so the operator must inspect the path and rerun the full gate.

## Commit sequence

The direct-to-`main` implementation sequence includes:

- `a24059b7bd9497ded9707f5a1c30f8fec6703852` — add independent aggregate contract validator;
- `83f947389aac9fbdd0258e993b5f40269503059f` — add adversarial aggregate-validator tests;
- `caab4028505a7bbd6f5f1190b466507f41708367` — require independent validation in the canonical wrapper;
- `f8cba3e4e488c9c02e28a14c5feb01d8b3418316` — add final aggregate publisher;
- `01c341ad5977bdc1b81d07ba72fc5e2b11e1adf1` — add publisher tests;
- `864b434ab2cfea6767b6fea0202b2e24e97507b3` — stage and revalidate before publication;
- `ae7ab38ff2e8212d71a5daa598e486e7bc044c48` — rehash protected sources after trace validation;
- `6e48f3daca3eafe3a65546ad2d190802532241c3` — test post-trace evidence rehashing;
- `4c28732efe82eea4f2b1af80491efc89f7619ea0` — lock staged and protected-source snapshots;
- `941f9e7897e8cc9b78dae0ccf3d9ca50ce06677b` — test final publication snapshot invariants;
- `ef53ff1d879a7bb70c601c17af155584e687da77` — synchronize final operator documentation.

Intermediate commits remain intentionally preserved; history was not squashed or rewritten.

## Validation performed in the available environment

The new Python modules and tests were syntax-compiled locally after their final edits.

A selected standalone set of seven adversarial aggregate-validator tests passed against the independent schema logic during development. This was a narrow local check, not the repository's complete host test suite.

The connected environment still could not provide a normal exact-head local checkout from `github.com`, and no exact-head Android/Gradle execution is claimed. GitHub combined status must be assessed separately for the final head.

Therefore:

- checked-in implementation and adversarial tests are present;
- local syntax validation is claimed;
- the selected independent-validator checks are claimed only at their narrow scope;
- complete exact-head host tests are not claimed;
- Android unit/Robolectric/lint/build/package success is not claimed;
- connected-emulator and physical-device success is not claimed.

## Remaining isolated source debt

This tranche deliberately avoided risky whole-file replacement of large authored/runtime coordinators. The previously isolated source debt remains:

1. substitute `FamiliarityWarmthScoring` at the `RelationshipArcSystem.familiarityWarmth()` call site;
2. substitute `SafeProgressionArithmetic` at the two `ReturnMomentsSystem` arithmetic call sites;
3. add the finite-coordinate admission guard inside `MainMenuScreen.onTap()`;
4. harden public finite-delta boundaries in `ParallaxBackground` and `GameView.update()`;
5. correct the unused `SaveManager.hasGhostRun()` convenience method so recoverable `AtomicFile` backup state is recognized consistently;
6. decompose `GameView` and consolidate distributed persistence ownership through behavior-preserving seams.

The helper implementations and tests for the first two items already exist. Their large-file call-site substitutions still require a patch-capable checkout and exact-head execution.

## External release gates still required

- exact-head host/unit/Robolectric/lint/release/package workflow conclusion;
- exact-head connected-emulator workflow conclusion;
- physical older-phone, midrange, high-refresh, cutout/aspect, and tablet sessions;
- frame-time, memory, crash, ANR, thermal, battery, and long-session evidence;
- real signing credentials and signed minified artifact;
- installation and internal-store delivery of that exact artifact;
- package/version/certificate/artifact receipt confirmation;
- final artwork, animation, screenshot, metadata, audio, haptic, reduced-motion, touch-latency, and accessibility review;
- privacy, data-safety, content-rating, target-audience, and current store-policy review.

## Classification

The acceptance evidence pipeline is materially more fail-closed and independently auditable after this tranche. That does not change the product classification: Forest Run remains a feature-rich alpha until the external execution, physical acceptance, signing, delivery, visual, accessibility, and policy gates are completed on one exact candidate.
