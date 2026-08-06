# Supply-chain and SBOM evidence

Forest Run maintains two different dependency views because they answer
different questions and must not be conflated.

## Declared dependency inventory

`scripts/build_declared_dependency_inventory.py` reads the checked-in Gradle
plugin declarations, direct module declarations, Gradle wrapper version, and
exactly pinned Python CI requirements. It binds those declarations and source
file digests to one 40-character candidate commit SHA.

Its scope is deliberately `declared-direct-dependencies-only`. It is not a
resolved transitive graph, SBOM, licence determination, vulnerability report, or
artifact-signature verification.

## Resolved CycloneDX inventory

`scripts/build_resolved_dependency_sbom.py` consumes one or more Gradle
`dependencies` reports after Gradle has resolved the selected configurations. It
normalizes conflict-resolution arrows, rejects conflicting final versions,
deduplicates Maven coordinates, emits package URLs, records source-report
SHA-256 digests, and creates deterministic CycloneDX 1.6 JSON bound to the exact
candidate SHA.

The release host workflow generates reports for:

- `releaseRuntimeClasspath`;
- `debugAndroidTestRuntimeClasspath`.

The resulting SBOM records the resolved components visible in those reports. It
does not claim that a dependency is safe, maintained, correctly licensed,
authentic, or free of vulnerabilities.

## Required release review

Before a public candidate is approved, a reviewer must still:

1. compare the declared and resolved inventories with the candidate source;
2. review dependency and asset licences and attribution obligations;
3. run an approved vulnerability scanner against the resolved SBOM and record
   scanner name, database timestamp, policy, findings, suppressions, and reviewer;
4. verify Gradle wrapper and downloaded-artifact integrity using approved
   verification metadata or an equivalent independently reviewed mechanism;
5. inspect repository and package provenance, signing custody, build logs, and
   candidate hashes;
6. reject evidence containing credentials, personal data, local absolute paths,
   or mutable aliases to a different candidate.

## Candidate-bound example

```bash
candidate_sha="$(git rev-parse HEAD)"
mkdir -p build/supply-chain

./gradlew app:dependencies \
  --configuration releaseRuntimeClasspath \
  --no-daemon --console=plain \
  > build/supply-chain/releaseRuntimeClasspath.txt

./gradlew app:dependencies \
  --configuration debugAndroidTestRuntimeClasspath \
  --no-daemon --console=plain \
  > build/supply-chain/debugAndroidTestRuntimeClasspath.txt

python3 scripts/build_declared_dependency_inventory.py \
  --root . \
  --candidate-sha "$candidate_sha" \
  --output build/supply-chain/declared-dependencies.json

python3 scripts/build_resolved_dependency_sbom.py \
  --candidate-sha "$candidate_sha" \
  --report releaseRuntimeClasspath=build/supply-chain/releaseRuntimeClasspath.txt \
  --report debugAndroidTestRuntimeClasspath=build/supply-chain/debugAndroidTestRuntimeClasspath.txt \
  --output build/supply-chain/resolved-sbom.cdx.json
```

These generated files are evidence inputs. They are not substitutes for the
signed artifact, physical-device acceptance, store receipt, or final independent
release approval.
