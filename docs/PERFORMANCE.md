# Forest Run — Performance Evidence Protocol

Performance is a release-evidence task, not a documentation adjective. The engine contains low-overhead telemetry, deterministic physical-device scenarios, a clean-SHA collector, and a candidate-specific acceptance evaluator. No device class is accepted until its measurements are captured, reviewed, converted into explicit limits, and re-evaluated against those limits.

## What the engine records

`FramePerformanceMonitor` records, in fixed primitive ring buffers:

- update duration;
- render duration;
- total processing duration before frame sleep;
- cumulative frame count;
- frames exceeding the nominal 60 Hz processing budget;
- maximum processing duration.

An out-of-band snapshot calculates:

- mean update/render/processing duration;
- p50, p95, and p99 processing duration;
- slow-frame ratio;
- current and maximum Java heap observations.

`RuntimeWorkloadTelemetry` correlates those timings with current and peak:

- active entities;
- Seed Orbs;
- particles;
- dialogue bubbles;
- flavour texts.

Recording does not allocate per frame. Snapshotting copies and sorts timing arrays and must not be called from the frame loop.

## Physical profiling scenarios

`HardwarePerformanceProfileTest` is marked `@LargeTest`, so permanent emulator CI compiles it but does not execute it. It currently profiles:

- `OPENING_READABILITY`;
- `BLOOM_SHOWCASE`.

Each test:

1. clears deterministic state;
2. starts a fresh telemetry monitor before Activity creation;
3. launches the requested deterministic scenario in `PERFORMANCE_PROFILE` mode;
4. allows startup/warmup frames;
5. records a sustained twenty-second interval;
6. verifies structural metric sanity;
7. writes a JSON report to the debug app’s external files directory.

The instrumentation test does not impose universal timing limits. Hardware classes require measured, candidate-specific interpretation.

## Run and collect profiles

Capture from one authorized device, or set `FOREST_RUN_DEVICE_SERIAL` when multiple devices are attached:

```bash
bash scripts/collect_performance_profiles.sh
```

Choose a custom local output directory:

```bash
bash scripts/collect_performance_profiles.sh evidence/performance/my-device
```

Run one profiling method only:

```bash
FOREST_RUN_PROFILE_TEST='com.anurag9000.forestrun.HardwarePerformanceProfileTest#profileBloomShowcaseOnHardware' \
  bash scripts/collect_performance_profiles.sh
```

The collector refuses a dirty Git tree and records the exact candidate SHA. It also:

- resolves `adb` from PATH or Android SDK environment variables;
- requires an explicit or unique authorized device;
- clears old app profile reports;
- executes the selected large instrumentation test;
- pulls JSON reports;
- records device properties and build fingerprint;
- captures `dumpsys gfxinfo`, `meminfo`, and display diagnostics;
- records whether acceptance thresholds were supplied;
- hashes the supplied threshold manifest;
- writes an explicit accepted, failed, or pending result.

## Candidate-specific threshold manifest

Thresholds must be derived from representative evidence, not guessed before profiling. Once a candidate/device class has been characterized, create a JSON manifest with schema version `1`:

```json
{
  "schemaVersion": 1,
  "profiles": [
    {
      "name": "midrange-one-opening-60hz",
      "manufacturer": "Example",
      "model": "Midrange One",
      "scenario": "OPENING_READABILITY",
      "minRefreshRateHz": 59.0,
      "maxRefreshRateHz": 61.0,
      "minSampledFrames": 300,
      "maxP95ProcessingNs": 13000000,
      "maxP99ProcessingNs": 16000000,
      "maxSlowFrameRatio": 0.02,
      "maxUsedHeapBytes": 64000000,
      "maxMaximumProcessingNs": 30000000
    }
  ]
}
```

The numbers above illustrate the schema only. They are not Forest Run release limits.

`manufacturer`, `model`, and `scenario` may use `"*"` as a fallback. Exact matches outrank wildcards. Equal-specificity matches are rejected as ambiguous rather than selected by file order. Core measured limits are mandatory; maximum single-frame processing time is optional.

Evaluate existing reports directly:

```bash
python3 scripts/evaluate_performance_profiles.py \
  --thresholds evidence/performance/thresholds.json \
  evidence/performance/my-device/reports
```

The evaluator exits:

- `0` when every supplied report passes;
- `1` when one or more measured limits are violated;
- `2` for malformed reports, malformed manifests, missing matches, or ambiguous matches.

The evaluator validates percentile ordering, finite ratios, non-negative integer metrics, required limits, refresh bounds, and unique profile names before comparing results.

## Collect and enforce in one command

After measured limits have been approved:

```bash
FOREST_RUN_PERFORMANCE_THRESHOLDS='evidence/performance/thresholds.json' \
  bash scripts/collect_performance_profiles.sh
```

When no manifest is supplied, collection succeeds only as evidence acquisition and `acceptance.txt` is explicitly marked `PENDING`. Supplying a manifest makes threshold failure fail the collector command after all device diagnostics have been captured.

## Evidence directory

A collection directory contains:

```text
device.properties
instrumentation.log
reports/*.json
gfxinfo.txt
meminfo.txt
display.txt
acceptance.txt
```

`device.properties` includes the candidate SHA, device serial, manufacturer, model, SDK, build fingerprint, application ID, selected instrumentation test, capture time, threshold-manifest path, and threshold-manifest SHA-256 when applicable.

Do not compare or accept reports unless the candidate commit, build type, scenario, device, thermal state, refresh mode, battery mode, and test duration are known.

## Required device matrix

At minimum capture:

1. constrained or older supported phone;
2. current mid-range phone;
3. current high-refresh phone;
4. cutout or unusual-aspect device;
5. tablet if tablets remain supported.

Run both deterministic scenarios on each class. Add longer ordinary-play captures when deterministic results are stable.

## Review checklist

For each report and accompanying diagnostics, review:

- p50/p95/p99 processing time;
- maximum processing time;
- slow-frame ratio;
- update versus render contribution;
- workload peaks correlated with timing spikes;
- heap growth across repeated scenarios;
- `gfxinfo` jank/frame histograms;
- `meminfo` growth and retained memory;
- visible GC or allocation spikes;
- audio-thread and I/O behaviour around Bloom and ghost saves;
- thermal degradation during extended play.

A low mean does not excuse a damaging p99. A low p99 during a deterministic lane does not prove long-run stability.

## Threshold derivation procedure

1. Capture repeat runs on every required device class from a clean candidate SHA.
2. Remove environmental outliers only with a written reason.
3. Inspect distributions and workload-correlated spikes rather than averages alone.
4. Remediate material hotspots before defining acceptance limits.
5. Choose limits that preserve measured headroom and product expectations.
6. Record exact device/scenario limits in the versioned manifest.
7. Re-run collection with the manifest enabled.
8. Archive reports, diagnostics, manifest, manifest hash, and `acceptance.txt` together.
9. Repeat whenever code, assets, target SDK, rendering policy, or accepted device scope changes materially.

Do not mark a device/scenario row accepted merely because instrumentation completed. Instrumentation proves that evidence was collected; the evaluator proves only that it met the approved measured manifest; human review still covers visual smoothness, thermal behaviour, audio, and input feel.

## Remaining physical performance work

The repository still requires:

- representative physical-device runs;
- approved candidate-specific threshold values;
- long ordinary-play scenarios;
- allocation and GC tracing beyond heap snapshots;
- audio-thread tracing;
- ghost-save I/O duration evidence;
- thermal and battery behaviour;
- remediation and repeated measurement of any material hotspots found.
