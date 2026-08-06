# Forest Run — Architecture, Release Integrity, Build Authority, and Privacy Continuation Audit

**Date:** 2026-08-06  
**Repository:** `Anurag9000/Forest_Run`  
**Canonical branch:** `main`  
**Starting head:** `02e52a4092c22ddf8103ea48c07e77d0c8c9f2bf`  
**Validated implementation/privacy head:** `cc24e86784bd0d6c53114c55e37ff447e1b55bc5`  
**Successful exact-head workflow:** Android validation run `31105597864`

## 1. Scope

This continuation followed the complete documentation reconciliation and addressed the next source-verifiable work that could be implemented safely without physical devices, release credentials, store access, or an unsafe whole-file rewrite of the large `GameView` owner.

The tranche covered:

- a pure exhaustive collision-outcome dispatcher seam;
- adversarial branch-exclusivity tests;
- final release-evidence-index output-path and inode safety;
- an independent release-evidence-index verifier;
- mandatory verifier operator documentation and source contracts;
- maintained GitHub Actions runtime versions;
- one authoritative pinned dependency declaration surface;
- a source-backed offline privacy policy and enforcement contract;
- renewed exact-head host, package, R8, and API-35 validation.

## 2. Collision outcome dispatcher seam

Added:

```text
app/src/main/java/com/anurag9000/forestrun/engine/CollisionOutcomeDispatcher.kt
app/src/test/java/com/anurag9000/forestrun/engine/CollisionOutcomeDispatcherTest.kt
```

The dispatcher is exhaustive over:

```text
HIT
STUMBLE
MERCY_MISS
NONE
```

Its inputs are deliberately lazy. Only the selected outcome may capture its mutable live state:

- HIT captures terminal ghost, killer, biome, Player presentation, and summary preview;
- STUMBLE captures only its nonterminal input and selected-entity deactivation;
- MERCY_MISS captures only mercy presentation state;
- NONE captures nothing and performs no side effect.

The pure tests prove:

- HIT cannot evaluate stumble or mercy suppliers;
- STUMBLE cannot detach a terminal ghost or build a terminal summary;
- MERCY_MISS cannot deactivate the selected entity;
- NONE is a complete no-op;
- existing terminal and nonterminal effect ordering remains unchanged.

### Correction found by CI

The first mercy test expected center `(352, 768)`. The actual authoritative Player dimensions are `72 × 100`, so an input position of `(320, 720)` produces center `(356, 770)`. The expectation was corrected without changing production behavior.

### Exact remaining boundary

The seam is implemented and tested, but it is **not yet the live production branch owner**. `GameView` still contains the exhaustive `when (collision.result)` block.

The available GitHub contents API replaces complete files rather than applying an exact line patch. `GameView.kt` is a large, high-risk owner whose prior broad transformation attempt was deliberately rejected and restored. This continuation therefore does not falsely claim production integration.

Final wiring remains one bounded architecture item for an environment with an exact checkout or safe patch-capable path.

## 3. Release evidence index publication hardening

Updated:

```text
scripts/build_release_evidence_index.py
```

Added:

```text
scripts/test_release_evidence_index_output_safety.py
```

The final index output now must remain both lexically and physically inside the selected evidence root. The builder rejects:

- `..`-based output escape;
- an output symbolic link;
- an existing symbolic-link component in the output parent path;
- an existing output that is a hard-link alias of indexed evidence;
- an output that becomes a hard-link alias before atomic replacement;
- output/evidence path equality;
- publication redirection through a symlinked parent.

The builder rechecks output path and inode separation immediately before `os.replace(...)`.

## 4. Independent release evidence index verification

Added:

```text
scripts/verify_release_evidence_index.py
scripts/test_verify_release_evidence_index.py
scripts/test_release_evidence_index_verifier_contract.py
```

Updated:

```text
docs/RELEASE_EVIDENCE_INDEX.md
```

The verifier is intentionally independent: it does not import the builder.

It performs:

- strict JSON parsing with duplicate-key rejection;
- exact root and entry schema validation;
- canonical candidate-SHA and UTC timestamp validation;
- entry count, ordering, uniqueness, and candidate-bound-count validation;
- canonical `evidenceSetSha256` reconstruction;
- required candidate-bound-kind enforcement;
- fresh filesystem reads for every indexed file;
- byte-count, SHA-256, and candidate-binding reconstruction;
- physical inode uniqueness and index/evidence alias rejection;
- final index reread after all evidence hashing.

Adversarial coverage includes:

- modified evidence after index creation;
- forged per-entry and evidence-set digests;
- duplicate index keys;
- expected-candidate mismatch;
- hard-linked evidence reuse;
- index/evidence inode aliasing;
- symlinked evidence;
- required but unbound evidence kinds.

The final release procedure now requires the verifier after index publication and before human approval.

### Remaining defense-in-depth

Two bounded improvements remain documented rather than falsely claimed:

1. bind builder JSON candidate parsing and hashing to one descriptor-backed stable snapshot;
2. reject symbolic-link components in evidence parent directories, not only the evidence file itself.

A focused local prototype passed adversarial tests, but the full large-file replacements were not published through the whole-file-only connector.

## 5. GitHub Actions runtime modernization

Updated `.github/workflows/android-validation.yml` to maintained action majors:

```text
actions/checkout@v6
actions/setup-java@v5
gradle/actions/setup-gradle@v6
gradle/actions/wrapper-validation@v6
```

Preserved:

- exact `${{ github.sha }}` checkout;
- `persist-credentials: false`;
- `contents: read` only;
- no commit or push path;
- API 36 host toolchain;
- API 35 connected behavior;
- existing Android SDK and emulator action owners;
- complete build/test/lint/package/R8 scope.

Gradle cache ownership is now explicit and singular through:

```text
cache-provider: basic
```

The duplicate setup-java Gradle cache declaration was removed.

Added `scripts/test_android_validation_action_versions.py` and updated the older workflow contract so deprecated action majors, credential persistence, write permissions, duplicate Gradle cache ownership, or missing exact-SHA checkout cannot silently return.

## 6. Dependency declaration authority

Removed the unused, stale parallel version catalogue:

```text
gradle/libs.versions.toml
```

It was not referenced by any build file and contained versions and Compose declarations that contradicted the actual non-Compose Canvas application build.

Added:

```text
scripts/test_dependency_authority.py
```

The contract now requires:

- one actual dependency authority in the checked-in build files;
- fixed Android Gradle Plugin and Kotlin plugin versions;
- fixed direct module versions;
- no `+`, `latest`, or `SNAPSHOT` declarations;
- no conflicting versions for the same module across configurations;
- explicit `compose = false` and no Compose dependency/plugin drift;
- centralized repositories with `FAIL_ON_PROJECT_REPOS`;
- exactly pinned Python CI dependencies.

The contract correctly permits the same module in JVM and instrumentation configurations when both use the same pinned version.

### Remaining supply-chain work

Not yet generated or claimed:

- Gradle dependency-verification metadata from a trusted resolution environment;
- a resolved transitive dependency/SBOM inventory;
- dependency licence attribution review;
- vulnerability review for the resolved graph;
- artifact attestation/provenance for the final signed candidate.

These must be produced from the frozen release environment rather than fabricated from direct declarations.

## 7. Source-backed privacy contract

Added:

```text
PRIVACY.md
scripts/test_privacy_contract.py
```

The policy is grounded in current source facts:

- the game is offline and single-player;
- the manifest requests only `android.permission.VIBRATE`;
- `android:allowBackup="false"`;
- no Internet permission;
- no advertising, analytics, billing, account, authentication, cloud-sync, networking, or remote crash-reporting SDK;
- progression, settings, relationships, Garden, wardrobe, ghost, and recovery state remain in private local storage;
- local data can be deleted through Android Clear storage or uninstall;
- debug evidence tools do not automatically transmit player data;
- future network/data features require a policy, Data Safety, and source-contract update.

The test enforces the exact permission set, backup policy, forbidden remote SDK dependencies/imports, and correspondence between policy language and source behavior.

### Remaining privacy/store boundary

The repository policy is not by itself final store approval. Before public release it still requires:

- owner/legal review for intended jurisdictions;
- publication at a stable public HTTPS URL;
- exact alignment with Play Data Safety answers;
- final target-audience and content-rating decisions.

## 8. Validation findings and corrections

Two exact-head runs exposed test-contract issues during this continuation:

1. the initial dispatcher mercy-center expectation was incorrect;
2. the older workflow contract still required checkout v4, and the first dependency contract incorrectly rejected a same-version module used in two test configurations.

All were corrected by changing tests/contracts, not weakening production behavior or validation scope.

## 9. Exact validation evidence

### Release-integrity/verifier implementation head

Commit:

```text
9e08644786bf9366e8f339547d26943f3f27862e
```

Android validation run:

```text
31103605412
```

Both host and API-35 jobs completed successfully.

### Modern workflow and dependency-authority head

Commit:

```text
8be9b5587f7f4968e9e63d9a39a55bfbfd42f036
```

Android validation run:

```text
31104918866
```

Both jobs completed successfully, including all 445 Python tests, complete Android compilation/JVM testing/lint/packaging, R8, connected behavior, and source immutability.

### Privacy head

Commit:

```text
cc24e86784bd0d6c53114c55e37ff447e1b55bc5
```

Android validation run:

```text
31105597864
```

Both host and API-35 jobs completed successfully.

## 10. Additional gaps found

### Screen-reader accessibility

The repository implements reduced motion, audio/haptic controls, safe-content geometry, touch admission, contrast/readability checks, and device acceptance. It does not currently contain a TalkBack virtual-view hierarchy, `AccessibilityNodeProvider`, `ExploreByTouchHelper`, or equivalent Canvas semantic navigation.

Screen-reader accessibility is therefore open and must not be implied by the existing accessibility wording.

### Vulnerability reporting

GitHub private vulnerability reporting is disabled. A `SECURITY.md` should not promise a nonexistent private channel. Enable private reporting first, then publish a tested security policy.

### Licensing

The public repository has no explicit `LICENSE`. Selecting a source-code and asset licence is a product-owner/legal decision and remains open.

### Release notes

Store metadata contains title, short description, and full description, but no final candidate release-notes file or repository changelog. Final notes should be written only for the frozen accepted version.

## 11. Current classification

Forest Run remains a **source-ready, feature-rich alpha**.

This continuation materially improves:

- collision-branch testability;
- release evidence publication and independent verification;
- CI runtime maintainability;
- dependency declaration truth;
- privacy-policy truthfulness.

It does not establish:

- production use of the new dispatcher;
- physical-device acceptance;
- measured performance acceptance;
- complete screen-reader accessibility;
- signed-artifact provenance;
- internal-store delivery;
- final artwork/audio/haptic approval;
- legal/privacy/store approval.

## 12. Next execution order

1. Integrate `CollisionOutcomeDispatcher` into `GameView` through a safe exact patch-capable checkout.
2. Add TalkBack/Canvas semantic navigation for menu, settings, Garden, Rest, and essential run state, with real screen-reader tests.
3. Generate trusted Gradle dependency-verification metadata and a resolved SBOM; perform licence and vulnerability review.
4. Enable GitHub private vulnerability reporting, then add `SECURITY.md`.
5. Select and add explicit source/asset licensing.
6. Freeze one candidate and complete physical performance, device, accessibility, lifecycle, audio, haptic, and visual acceptance.
7. Sign, install, and deliver that exact artifact through an internal store track.
8. Publish the privacy policy at a stable HTTPS URL and complete Data Safety, content rating, target audience, screenshots, metadata, and policy review.
9. Build and independently verify the final release-evidence index, record its digests, obtain independent approvals, tag the exact accepted `main` SHA, and write candidate-specific release notes.
