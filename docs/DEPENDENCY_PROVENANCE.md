# Dependency provenance and software-bill-of-materials policy

Forest Run separates three different supply-chain claims. They must not be conflated.

## 1. Declared dependency inventory

`scripts/build_declared_dependency_inventory.py` records the dependencies explicitly declared in the checked-in candidate:

- Android Gradle Plugin and Kotlin plugin versions;
- Gradle wrapper version;
- direct Maven modules and their configurations;
- exact Python CI package pins;
- SHA-256 digests of every declaration source file.

Build it for the exact candidate SHA:

```bash
python3 scripts/build_declared_dependency_inventory.py \
  --root . \
  --candidate-sha "$CANDIDATE_SHA" \
  --output release/evidence/declared-dependency-inventory.json
```

The output is deterministic, candidate-bound, canonically encoded JSON. Its `inventorySha256` covers the ordered declared-entry list. The declaration-source digests reveal any later change to the build files, wrapper properties, or Python requirements.

This inventory is useful release evidence, but it is deliberately labelled `declared-direct-dependencies-only`.

## 2. Resolved dependency graph and verification metadata

Before a public release, resolve the complete candidate graph in the trusted release environment and generate Gradle dependency-verification metadata. Review and retain:

- every resolved component, version, repository, and artifact checksum;
- Gradle plugin artifacts and transitive Maven dependencies;
- variant-specific debug, release, test, and instrumentation graphs;
- rejected substitutions, dynamic selectors, and repository fallback;
- the verification metadata and the exact Gradle/JDK/Android toolchain used.

The metadata must be generated from a clean, frozen `main` candidate. It must not be invented from direct declarations, copied from a different candidate, or silently accepted after an unexplained checksum change.

## 3. SBOM, licences, and vulnerability review

A release SBOM must be generated from the resolved candidate, preferably in CycloneDX or SPDX JSON. It should include:

- application identity, version code/name, commit SHA, and signed artifact SHA-256;
- direct and transitive packages with package URLs where available;
- dependency relationships and scopes;
- build-tool and plugin components when supported;
- licence identifiers and attribution sources;
- generation tool name/version and timestamp.

Then perform an independent review for:

- known vulnerabilities in the exact resolved versions;
- licence compatibility and required notices;
- abandoned or unmaintained components;
- unexpected networking, analytics, advertising, billing, account, or native-code additions;
- discrepancies between the SBOM, dependency-verification metadata, declared inventory, and packaged artifact.

The resulting reports must be candidate-bound evidence and included in the final release-evidence index.

## Fail-closed release rule

A declared inventory does **not** prove the transitive graph, licences, vulnerability status, or packaged contents. A public release remains blocked until the resolved graph, verification metadata, SBOM, licence attribution, vulnerability review, and signed-artifact provenance all agree on the same frozen candidate.
