# Android page-size compatibility evidence

Forest Run compiles and targets Android API 36. The Android package must still be inspected and exercised in a 16 KB environment before release because packaged dependencies, not only application source, determine whether native-code alignment work is required.

## Package inspection

After building the exact candidate bundle, run:

```bash
python3 scripts/verify_android_page_size_package.py \
  app/build/outputs/bundle/release/app-release.aab \
  --candidate-sha "$CANDIDATE_SHA" \
  --require-no-native-code \
  --output release/evidence/android-page-size-package.json
```

The verifier:

- freezes and SHA-256 hashes one bounded regular artifact;
- rejects artifact/output symlinks, unsafe archive paths, duplicate entries, encrypted entries, archive symlinks, and size-bound violations;
- enumerates every packaged `.so` file;
- succeeds with `assessment=no-native-code` only when no native library is present;
- fails closed under `--require-no-native-code` when a native library appears;
- emits canonical candidate-bound JSON suitable for the final release-evidence index.

A native-free package is compatible by package inspection, but this result is not physical acceptance. Any packaged native library requires independent ZIP alignment, ELF segment alignment, SDK provenance, and 16 KB runtime verification.

## Runtime acceptance

On the exact installed candidate, verify:

```bash
adb shell getconf PAGE_SIZE
```

The value must be `16384`. Then execute the full connected behavior suite plus ordinary play, dense Bloom, all-entity, Garden transaction, ghost persistence, lifecycle/process-death, audio, haptic, and accessibility scenarios. Record device/emulator identity, Android build, page size, artifact and certificate digests, candidate SHA, session result, reviewer, and evidence digests.

Compatibility or backcompat mode is not a substitute for native 16 KB alignment when native code is present. Do not set a manifest compatibility flag merely to hide a platform warning.

## Release-blocking rule

Release remains blocked if:

- the final signed AAB/APK has not been inspected;
- an unexpected native library is present;
- native ZIP/ELF alignment has not been independently verified;
- `getconf PAGE_SIZE` does not prove a 16 KB runtime environment;
- the exact signed artifact has not passed representative behavior and performance acceptance in that environment;
- the package-inspection evidence is absent from the final release-evidence index.

Authoritative platform guidance:

- https://developer.android.com/guide/practices/page-sizes
- https://developer.android.com/google/play/requirements/target-sdk
