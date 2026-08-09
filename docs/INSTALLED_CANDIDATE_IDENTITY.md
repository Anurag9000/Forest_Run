# Installed candidate identity evidence

Forest Run distinguishes three different release identities that must not be conflated:

1. the source candidate on clean canonical `main`;
2. the signed Android App Bundle uploaded to Google Play, authenticated with the **upload key**;
3. the APK set actually delivered to a device by Google Play, signed with the **Play app-signing key**.

The installed-package evidence layer measures item 3 directly on every physical acceptance device and binds it back to item 1 plus the expected release version/app-signing certificate. It does **not** infer which Play track delivered the package.

The source tools are:

```text
scripts/collect_installed_candidate_identity.py
scripts/validate_installed_candidate_identity.py
scripts/compile_installed_identity_matrix.py
scripts/validate_installed_identity_matrix.py
```

## One-device collector

For each physical acceptance device, run the collector against the frozen clean candidate:

```bash
python3 scripts/collect_installed_candidate_identity.py \
  --root . \
  --output-dir release/evidence/installed/older_phone \
  --expected-candidate-sha "$CANDIDATE_SHA" \
  --expected-version-code "$VERSION_CODE" \
  --expected-app-signing-certificate-sha256 "$PLAY_APP_SIGNING_CERT_SHA256"
```

When more than one authorized device is connected, also pass `--serial` or set `FOREST_RUN_DEVICE_SERIAL`.

The collector:

- verifies a clean candidate through `verify_main_candidate.py`;
- verifies `HEAD`, local `main`, and freshly fetched canonical `origin/main` all equal the expected SHA before and after capture;
- requires the release package `com.anurag9000.forestrun`;
- requires package-manager installer attribution to `com.android.vending`;
- captures every path returned by `adb shell pm path` and requires exactly one `base.apk`;
- pulls `base.apk` and every installed split APK;
- runs `apksigner verify --print-certs` on every pulled APK and requires one common expected app-signing certificate SHA-256;
- runs `apkanalyzer apk summary` on `base.apk` and requires the expected application ID/version code;
- records the installed version name without using it as the primary immutable identity;
- hashes every pulled APK and raw command-output file;
- stores only SHA-256 of the device serial in the structured manifest;
- captures manufacturer, model, device codename, SDK, and build fingerprint;
- retains raw package-manager, package dump, APK analyzer, signature-verification, pull, and device-property evidence;
- independently revalidates the completed record before reporting success.

The collector resolves `adb`, `apkanalyzer`, and `apksigner` from PATH or the Android SDK. It requires an empty/nonexistent output directory so an old evidence set cannot be silently mixed with a new capture.

## What the one-device record proves

A valid `installed-candidate-identity.json` proves that the evidence bundle consistently reports:

- the expected clean `main` candidate context;
- expected release package and version code;
- Google Play as the package-manager installer;
- the expected Play app-signing certificate on every installed APK split;
- exact pulled APK bytes/digests/sizes;
- one base APK plus the observed split set;
- stable raw evidence files and stable device identity metadata.

The validator cross-binds each `apk_set` digest and byte size to the actual pulled `apks/<name>` evidence file. Symlink traversal, hard-link evidence reuse, duplicate paths/names, duplicate JSON keys, changing files, wrong signer/version/installer, and a missing base APK fail closed.

## What it deliberately does not prove

`com.android.vending` identifies Google Play as the installer. It does **not** by itself prove:

- internal vs closed vs open vs production track;
- which Play Console release/version was selected for the account;
- tester eligibility;
- upload completion;
- rollout percentage;
- update/receipt history.

Therefore every installed record contains:

```json
{
  "claims": {
    "play_store_installer_observed": true,
    "specific_play_track_verified": false
  }
}
```

The validator rejects `specific_play_track_verified: true`. Internal-track delivery, receipt/update path, and Play Console state remain candidate-bound external evidence in physical acceptance and release governance.

## Five-device installed identity matrix

After collecting one record for every physical acceptance session, create a draft:

```json
{
  "generated_at_utc": "2026-08-09T18:00:00Z",
  "device_acceptance": "device-acceptance.json",
  "records": [
    {
      "session_id": "<exact physical session id>",
      "path": "installed/older_phone/installed-candidate-identity.json"
    }
  ]
}
```

Include exactly one record for every physical session, then compile and revalidate:

```bash
python3 scripts/compile_installed_identity_matrix.py \
  release/evidence/installed-identity-matrix-draft.json \
  release/evidence/installed-identity-matrix.json \
  --summary-output release/evidence/installed-identity-matrix-summary.json

python3 scripts/validate_installed_identity_matrix.py \
  release/evidence/installed-identity-matrix.json \
  --summary-output release/evidence/installed-identity-matrix-summary.json
```

The compiler derives the candidate identity and all record hashes from already-existing evidence. It does not create device observations.

The matrix validator revalidates the complete physical acceptance manifest and every installed-package record, then requires:

- exactly one installed identity record per physical acceptance session;
- no unknown or duplicated session IDs;
- distinct hashed device serial identity across physical sessions;
- candidate SHA, version code, and Play app-signing certificate matching physical acceptance;
- manufacturer, model, build fingerprint, and SDK matching the corresponding physical session;
- identity capture no more than 24 hours away from its physical session;
- record path/digest integrity with no path/symlink/hard-link aliasing;
- one shared candidate/upload-certificate/app-signing-certificate identity across the complete matrix.

The 24-hour bound is a provenance guard, not a performance threshold. For final acceptance, capture package identity immediately before or after each physical session whenever practical.

## Relationship to upload signing

The installed matrix carries the physical manifest's `upload_certificate_sha256` and `app_signing_certificate_sha256` as **separate** fields. It never requires those digests to be equal.

The upload certificate authenticates the AAB submitted by the developer. Under Play App Signing, the delivered APK certificate is the Play app-signing certificate. The physical/human/governance/readiness schemas preserve and cross-bind both identities explicitly.

## Final evidence use

`installed-identity-matrix.json` is a candidate-bound principal release manifest. It should be:

- revalidated independently;
- required by release governance;
- included as a required candidate-bound kind in the final release-evidence index;
- independently revalidated again by the final release-readiness gate.

The raw per-device installed identity records and pulled-APK/raw-command evidence are also material release evidence and should be indexed when retained in the final evidence set.
