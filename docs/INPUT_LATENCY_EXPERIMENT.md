# Input latency experiment

Forest Run now has a dedicated physical-device experiment for **app/render input latency**. It measures four in-process milestones for gameplay touch gestures:

1. the `ACTION_DOWN` is accepted by `InputHandler`;
2. the gesture is classified as jump or duck;
3. the corresponding gameplay callback returns after applying the player response;
4. `GameThread` posts the first rendered frame after that response.

The resulting measurement kind is deliberately named `app_touch_to_posted_frame`.

It is **not touch-to-photon latency**. The app cannot observe touchscreen sensor acquisition, compositor/display scanout, or panel response. True touch-to-photon evidence requires external instrumentation such as a high-speed camera or photodiode/robotic-touch rig and must be retained as separate physical evidence.

## Runtime design

`InputLatencyTelemetry` uses fixed primitive ring buffers. Recording touch, classification, and gameplay-response timestamps performs no per-action allocation. `GameThread` checks one volatile pending-response value and avoids synchronization on ordinary frames; only the first frame after a pending response enters the synchronized completion path. Snapshotting may allocate and sort because it is an out-of-band profiling operation.

The retained statistics are p50/p95/p99 for:

- touch receipt → gesture decision;
- gesture decision → gameplay response;
- gameplay response → posted frame;
- touch receipt → posted frame.

The telemetry also records completed and dropped action counts. Pending/cancelled state is cleared on gesture cancellation rather than being turned into a fabricated latency sample.

## Physical profiling harness

`HardwareInputLatencyProfileTest` is an Android `@LargeTest`. It launches a deterministic performance-profile run on hardware, warms the render thread, clears prior latency samples, and injects a repeatable mix of quick-jump and downward-swipe gestures through Android instrumentation. It then waits until every injected action has reached a posted response frame and writes one JSON report under the debug app's external `input-latency-profiles` directory.

Instrumentation injection makes the app/render portion reproducible and useful for regression detection. It still does not reproduce a human finger's sensor-acquisition timing.

## Candidate-frozen collection

Run from a clean synchronized `main` checkout with exactly one authorized Android device attached:

```bash
bash scripts/collect_input_latency_profile.sh
```

When more than one device is connected:

```bash
FOREST_RUN_DEVICE_SERIAL='<adb-serial>' \
  bash scripts/collect_input_latency_profile.sh
```

The collector:

- verifies clean canonical `main` and freshly fetched `origin/main` before capture;
- freezes the exact candidate SHA;
- resolves and validates one authorized device;
- records manufacturer/model/device/SDK/build fingerprint;
- records pre/post display and input diagnostics;
- runs only `HardwareInputLatencyProfileTest`;
- pulls the generated report into a candidate-local evidence directory;
- optionally evaluates measured limits;
- revalidates both local `main` and `origin/main` after capture and rejects the run if either changed.

The evidence directory contains at least:

```text
device.properties
instrumentation.log
reports/*.json
display-before.txt
display-after.txt
input-before.txt
input-after.txt
package-after.txt
acceptance.txt
```

Without an approved threshold manifest, `acceptance.txt` is explicitly `PENDING`.

## Threshold derivation

Do not invent latency limits in source. First collect repeated runs on the accepted physical-device matrix, inspect distributions and refresh-rate effects, remediate material regressions, and only then version measured limits.

A threshold manifest has schema version `1`:

```json
{
  "schemaVersion": 1,
  "profiles": [
    {
      "name": "example-phone-120hz",
      "manufacturer": "Example",
      "model": "Phone",
      "minRefreshRateHz": 110.0,
      "maxRefreshRateHz": 130.0,
      "minSampledActions": 40,
      "maxDroppedActionRatio": 0.0,
      "maxP95TouchToDecisionNs": 75000000,
      "maxP95DecisionToResponseNs": 1000000,
      "maxP95ResponseToRenderNs": 12000000,
      "maxP95TouchToRenderNs": 90000000
    }
  ]
}
```

These values illustrate the schema only. They are **not Forest Run release limits**.

Evaluate captured reports directly:

```bash
python3 scripts/evaluate_input_latency_profiles.py \
  --thresholds input-latency-profiles/thresholds.json \
  input-latency-profiles/device/run/reports/*.json
```

Or collect and evaluate in one candidate-frozen command:

```bash
FOREST_RUN_INPUT_LATENCY_THRESHOLDS='input-latency-profiles/thresholds.json' \
  bash scripts/collect_input_latency_profile.sh
```

The evaluator selects the most-specific matching manufacturer/model/refresh profile, rejects ambiguous matches, validates report accounting and percentile ordering before comparing limits, and exits `0` for pass, `1` for measured-limit violations, or `2` for malformed/missing/ambiguous configuration.

## Release interpretation

This experiment is an additional performance/control diagnostic, not a new independent release authority. The existing physical-device acceptance manifest remains the authoritative matrix-level release contract for device classes, mandatory scenarios, candidate identity, signed/store-delivered artifact identity, manual touch-control review, frame performance, memory, crashes, and ANRs.

If input-latency evidence materially participates in a release decision:

1. capture it from the same frozen candidate/device session;
2. retain `device.properties`, raw report, diagnostics, threshold manifest and its hash, and evaluator output;
3. include those files among that session's physical evidence or final release evidence index;
4. have a human reviewer inspect control feel as well as the numeric app/render measurement;
5. keep any external touch-to-photon measurement clearly separate and labelled by its actual measurement method.
