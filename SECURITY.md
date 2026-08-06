# Security Policy

## Supported versions

Forest Run is currently a source-ready alpha. Security fixes are applied to the
latest commit on `main`; there is not yet a separately supported public release
line.

## Reporting a vulnerability

Do not open a public issue containing an exploitable vulnerability, private key,
credential, personal data, or unpublished store artifact. Use GitHub's private
vulnerability reporting feature for this repository when it is available.
Otherwise, contact the repository owner privately through the contact method on
the owner's GitHub profile and include:

- the affected commit SHA and build variant;
- reproduction steps and the expected versus observed behavior;
- the security impact and prerequisites;
- logs or evidence with credentials, personal data, paths, and device identifiers
  removed;
- whether the report can be acknowledged publicly after remediation.

A report should receive an acknowledgement before any public disclosure. Do not
request, include, or test real user data. Do not perform denial-of-service,
credential attacks, store-account attacks, or testing against systems you do not
own or have explicit permission to assess.

## Release security boundaries

Repository validation can prove source contracts, deterministic tooling, package
shape, R8 output, and selected emulator behavior. It cannot by itself prove:

- the confidentiality or custody of signing credentials;
- Google Play Console configuration, receipt, or update behavior;
- absence of vulnerabilities in all resolved transitive artifacts;
- licence compatibility of every dependency or asset;
- physical-device security, privacy, accessibility, or performance acceptance.

Those items remain explicit candidate-bound release gates. Secrets must be
provided only through protected Gradle properties or environment variables and
must never be committed, printed, copied into evidence bundles, or embedded in
store metadata.
