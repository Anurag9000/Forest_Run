# Final release readiness gate

Forest Run's final source-side release command is `scripts/validate_release_readiness.py`.

It does **not** create physical, installed-package, Play-delivery, human, signing, store, legal, policy, security, licence, or presentation evidence. Those facts must already exist and must already pass their owning validators. Readiness is the final orchestration layer that independently revalidates those accepted inputs and proves that the same candidate and files are represented in the final release-evidence index.

## Required inputs

One evidence root must contain:

- a compiled, valid physical `device-acceptance.json`;
- a compiled, valid `installed-identity-matrix.json`;
- a compiled, valid `play-delivery.json`;
- a compiled, valid `human-acceptance.json`;
- a compiled, valid `release-governance.json`;
- a published, independently verifiable `release-evidence-index.json`;
- the exact signed bundle and all other files indexed by that release-evidence index.

The command also requires the exact expected lowercase 40-character candidate SHA.

## What readiness revalidates

The orchestrator delegates rather than reimplementing the six owning formats:

1. `validate_device_acceptance.load_and_validate(...)` revalidates physical acceptance.
2. `validate_installed_identity_matrix.load_and_validate(...)` revalidates one measured Play-delivered package identity per physical session.
3. `validate_play_delivery_evidence.load_and_validate(...)` revalidates the external internal-track upload/install/update evidence.
4. `validate_human_acceptance.load_and_validate(...)` revalidates detailed human gameplay/accessibility/presentation acceptance.
5. `validate_release_governance.load_and_validate(...)` revalidates candidate-bound security/licensing/privacy/store/presentation governance.
6. `verify_release_evidence_index.verify_index(...)` independently reconstructs and verifies the final evidence index.

It then applies cross-layer invariants that no one individual format can prove alone.

## Cross-layer invariants

A ready result requires:

- expected SHA = physical candidate SHA = installed-identity candidate SHA = Play-delivery candidate SHA = human candidate SHA = governance candidate SHA = evidence-index candidate SHA;
- physical artifact SHA-256 = installed-identity artifact SHA-256 = Play-delivery artifact SHA-256 = human artifact SHA-256 = governance artifact SHA-256;
- physical upload-certificate SHA-256 = installed-identity upload-certificate SHA-256 = Play-delivery upload-certificate SHA-256 = human upload-certificate SHA-256 = governance upload-certificate SHA-256;
- physical app-signing-certificate SHA-256 = installed-identity app-signing-certificate SHA-256 = Play-delivery app-signing-certificate SHA-256 = human app-signing-certificate SHA-256 = governance app-signing-certificate SHA-256;
- the human manifest's recorded physical-manifest digest equals the exact revalidated physical manifest;
- the installed-identity matrix's recorded physical-manifest digest equals that same physical manifest;
- the Play-delivery manifest's recorded installed-identity-matrix digest equals the exact revalidated installed-identity matrix;
- the governance manifest's physical-manifest digest equals that same physical manifest;
- the governance manifest's human-manifest digest equals the exact revalidated human manifest;
- the governance manifest's installed-identity-matrix digest equals the exact revalidated installed-identity matrix;
- the governance manifest's Play-delivery digest equals the exact revalidated Play-delivery manifest;
- the exact revalidated physical manifest is the file indexed as `device_acceptance`;
- the exact revalidated installed-identity matrix is the file indexed as `installed_identity_matrix`;
- the exact revalidated Play-delivery manifest is the file indexed as `play_delivery`;
- the exact revalidated human manifest is the file indexed as `human_acceptance`;
- the exact revalidated governance manifest is the file indexed as `release_governance`;
- the indexed `signed_bundle` SHA-256 equals the accepted candidate artifact SHA-256;
- the binary `signed_bundle` remains transitively bound instead of pretending to carry a JSON candidate binding;
- every mandatory candidate-bound release-index kind is present and explicitly bound to the same candidate.

Required candidate-bound index kinds are:

- `artifact_verification`;
- `declared_dependencies`;
- `sbom`;
- `device_acceptance`;
- `device_aggregate`;
- `installed_identity_matrix`;
- `play_delivery`;
- `human_acceptance`;
- `release_governance`;
- `screenshot_manifest`;
- `graphics_manifest`.

The required transitively bound binary kind is `signed_bundle`.

Additional candidate-specific evidence should still be indexed whenever it participates in the release decision. The list above is only the minimum source-enforced readiness surface.

## Invocation

From the evidence root or with explicit paths under it:

```bash
python3 scripts/validate_release_readiness.py \
  --root release/evidence-root \
  --expected-candidate-sha "$CANDIDATE_SHA" \
  --device-acceptance release/evidence-root/device-acceptance.json \
  --installed-identity-matrix release/evidence-root/installed-identity-matrix.json \
  --play-delivery release/evidence-root/play-delivery.json \
  --human-acceptance release/evidence-root/human-acceptance.json \
  --release-governance release/evidence-root/release-governance.json \
  --release-evidence-index release/evidence-root/release-evidence-index.json \
  --summary-output release/evidence-root/release-readiness-summary.json
```

For paths supplied relative to the shell working directory, run from the repository/evidence operator location where those paths resolve inside `--root`. Absolute paths are accepted only when they remain inside the same evidence root. Symbolic-link path components are rejected before delegation.

Exit code `0` means the declared evidence layers independently validate and cross-bind to one exact candidate. It still does not mean a store rollout has happened or that any external reviewer statement is objectively true.

## Why this is separate from the evidence index

The evidence index proves set integrity: file identity, bytes, hashes, candidate bindings, ordering, and required kinds. It intentionally does not interpret the full semantics of every indexed schema.

Readiness adds semantic orchestration without replacing the index:

- it actually reruns the physical-device acceptance validator;
- it actually reruns the installed-package identity-matrix validator;
- it actually reruns the Play-delivery validator;
- it actually reruns the human-acceptance validator;
- it actually reruns the governance validator;
- it independently reruns the evidence-index verifier;
- it then proves the exact revalidated manifests are the exact files named by the index and that the indexed signed bundle is the artifact all acceptance layers approved.

This prevents a superficially valid final folder from mixing a valid governance file with a different valid device manifest, installed identity matrix, Play-delivery record, or human acceptance manifest; indexing a copied manifest while the operator validated another path; or including a different signed bundle than the accepted artifact.

## Correct final order

The release-owner sequence is:

1. freeze one exact clean `main` candidate;
2. build and sign that exact candidate;
3. deliver/install it through the accepted internal-store path;
4. run the physical device/scenario/performance matrix and compile `device-acceptance.json`;
5. collect one installed-package identity record per physical session and compile `installed-identity-matrix.json`;
6. retain Play Console/upload/tester/install/update evidence and compile `play-delivery.json`;
7. run detailed gameplay, TalkBack/accessibility, Garden/ghost, art/audio/haptic human review and compile `human-acceptance.json`;
8. complete security/licensing/privacy/Play/presentation/provenance decisions and compile `release-governance.json`;
9. build the stable release-evidence index containing the signed bundle and every material evidence file;
10. independently verify the index;
11. run `validate_release_readiness.py` against the same evidence root;
12. have the final independent reviewer inspect the readiness summary, underlying evidence, and external consoles/records before the release/tag decision.

Any source, artifact, store-delivery, evidence, or approval change after acceptance creates a new candidate/evidence set. Do not edit an accepted evidence set in place.
