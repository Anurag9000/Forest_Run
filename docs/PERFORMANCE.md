# Forest Run — Performance Evidence Protocol

Performance is a release-evidence task, not a documentation adjective. The engine contains low-overhead telemetry, repeated deterministic physical-device workloads, a canonical-`origin/main` collector, and a candidate-specific acceptance evaluator. No device class is accepted until its measurements are captured, reviewed, converted into explicit limits, and re-evaluated against those limits.

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

`GhostIoTelemetry` records asynchronous best-run persistence pressure:

- writes started, completed, and failed;
- latest and maximum ghost frame count;
- latest and maximum write duration.

Per-frame recording does not allocate. Snapshotting copies and sorts timing arrays and must not be called from the frame loop. Ghost I/O timing is published by the dedicated serialized worker, not by the render thread.

## Physical profiling scenarios

`HardwarePerformanceProfileTest` is marked `@LargeTest`, so permanent emulator CI compiles it but does not execute it. It currently profiles:

- `OPENING_READABILITY`;
- `BLOOM_SHOWCASE`;
- `FLORA_SHOWCASE`;
- `TREE_SHOWCASE`;
- `BIRD_SHOWCASE`;
- `ANIMAL_SHOWCASE`;
- `GHOST_READABILITY`;
- `GHOST_PERSISTENCE_MAX`, which writes the full 36,000-frame, twenty-minute-capacity ghost while rendering continues.

Each scenario profile:

1. clears deterministic disk and in-memory state;
2. starts a fresh telemetry monitor before Activity creation;
3. launches the requested deterministic scenario in `PERFORMANCE_PROFILE` mode;
4. warms the Activity, render thread, assets, and caches;
5. stops the producer thread and resets the existing monitor in place, excluding warmup samples;
6. restarts the requested scenario with the same monitor reference;
7. repeatedly replays the encounter script throughout the measured interval instead of measuring an idle post-script screen;
8. verifies structural metric sanity and replay count;
9. writes a JSON report to the debug app’s external files directory.

The maximum ghost-persistence profile additionally schedules a validated 36,000-frame run through `GhostPersistenceManager`, waits for the asynchronous worker, rejects write failure, and records exact latency while gameplay rendering remains active.

The instrumentation tests do not impose universal timing limits. Hardware classes require measured, candidate-specific interpretation.

## Run and collect profiles

Capture from one authorized device, or set `FOREST_RUN_DEVICE_SERIAL` when multiple devices are attached:

```bash
bash scripts/collect_performance_profiles.sh
```

Choose a custom local output directory. Keep it outside the repository or under an ignored evidence directory so the final source-integrity recheck remains clean:

```bash
bash scripts/collect_performance_profiles.sh performance-profiles/my-device
```

Run one profiling method only:

```bash
FOREST_RUN_PROFILE_TEST='com.anurag9000.forestrun.HardwarePerformanceProfileTest#profileBloomShowcaseOnHardware' \
  bash scripts/collect_performance_profiles.sh
```

A method-only run is targeted diagnostic evidence; it does not substitute for the complete release workload set.

Before contacting the device, the collector:

- requires the named `main` branch rather than a detached or feature checkout;
- rejects tracked changes and untracked files;
- freshly fetches canonical `origin/main`;
- rejects stale local main and unpushed local commits;
- freezes the full SHA shared by `HEAD`, local `main`, and `origin/main`.

After instrumentation, report pulling, diagnostics, and threshold evaluation, it re-runs both local and remote verification. Evidence is rejected if the worktree changes or `origin/main` advances during capture.

The collector also:

- resolves `adb` from PATH or Android SDK environment variables;
- requires an explicit or unique authorized device;
- clears old app profile reports;
- executes the selected large instrumentation test;
- pulls JSON reports;
- records device properties and build fingerprint;
- captures `dumpsys gfxinfo`, `meminfo`, and display diagnostics;
- records whether acceptance thresholds were supplied;
- hashes the supplied threshold manifest with Python's portable SHA-256 implementation;
- writes an explicit accepted, failed, or pending result;
- captures before/after `dumpsys battery`, `thermalservice`, `power`, `cpuinfo`, `audio`, and `media.audio_flinger` snapshots;
- captures post-run `gfxinfo ... framestats`, `meminfo`, `procstats`, package identity, and display diagnostics even when the instrumentation workload fails;
- can optionally overlap the physical workload with a Perfetto system trace when `FOREST_RUN_CAPTURE_PERFETTO=1`, retaining the trace only as diagnostic evidence rather than silently mixing traced and untraced threshold runs.

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
    },
    {
      "name": "midrange-one-maximum-ghost-write",
      "manufacturer": "Example",
      "model": "Midrange One",
      "scenario": "GHOST_PERSISTENCE_MAX",
      "minRefreshRateHz": 59.0,
      "maxRefreshRateHz": 61.0,
      "minSampledFrames": 300,
      "maxP95ProcessingNs": 13000000,
      "maxP99ProcessingNs": 16000000,
      "maxSlowFrameRatio": 0.02,
      "maxUsedHeapBytes": 96000000,
      "maxMaximumProcessingNs": 30000000,
      "minGhostWritesCompleted": 1,
      "maxGhostWriteFailures": 0,
      "minMaximumGhostFrameCount": 36000,
      "maxGhostWriteDurationNs": 100000000
    }
  ]
}
```

The numbers above illustrate the schema only. They are not Forest Run release limits.

`manufacturer`, `model`, and `scenario` may use `"*"` as a fallback. Exact matches outrank wildcards. Equal-specificity matches are rejected as ambiguous rather than selected by file order. Core frame and heap limits are mandatory. Maximum single-frame processing time and ghost-persistence limits are optional.

Evaluate existing reports directly:

```bash
python3 scripts/evaluate_performance_profiles.py \
  --thresholds performance-profiles/thresholds.json \
  performance-profiles/my-device/reports
```

The evaluator exits:

- `0` when every supplied report is structurally valid and passes;
- `1` when one or more measured limits are violated;
- `2` for malformed reports, malformed manifests, missing metrics, missing matches, or ambiguous matches.

Before threshold comparison, every current report must provide:

- nonblank scenario/manufacturer/model identity;
- nonnegative duration and API level;
- a finite positive refresh rate;
- coherent sampled, total, and slow-frame counts;
- an exact `slowFrames / totalFrames` slow-frame ratio;
- a positive frame budget;
- ordered p50/p95/p99/maximum timing values and bounded means;
- `usedHeapBytes <= maxHeapBytes`;
- each workload's `current <= peak` relationship;
- ghost `failed <= completed <= started` relationships;
- latest ghost frame count and write duration no greater than their maxima.

A tampered, truncated, internally contradictory, or older incomplete report is a configuration error even when its headline p95/p99 values happen to meet the manifest.

## Collect and enforce in one command

After measured limits have been approved:

```bash
FOREST_RUN_PERFORMANCE_THRESHOLDS='performance-profiles/thresholds.json' \
  bash scripts/collect_performance_profiles.sh
```

When no manifest is supplied, collection succeeds only as evidence acquisition and `acceptance.txt` is explicitly marked `PENDING`. Supplying a manifest makes threshold failure fail the collector command after all device diagnostics have been captured.

## Evidence directory

A collection directory contains:

```text
device.properties
instrumentation.log
reports/*.json
battery-before.txt / battery-after.txt
thermalservice-before.txt / thermalservice-after.txt
power-before.txt / power-after.txt
cpuinfo-before.txt / cpuinfo-after.txt
audio-before.txt / audio-after.txt
audio-flinger-before.txt / audio-flinger-after.txt
gfxinfo-framestats-after.txt
meminfo-after.txt
procstats-after.txt
package-after.txt
display.txt
gfxinfo.txt / meminfo.txt   # backward-compatible aliases
system-trace.perfetto-trace # only when explicitly requested
acceptance.txt
```

`device.properties` includes the frozen candidate SHA, freshly fetched `origin/main` SHA, device serial, manufacturer, model, SDK, build fingerprint, application ID, selected instrumentation test, capture time, threshold-manifest path, and threshold-manifest SHA-256 when applicable.

Do not compare or accept reports unless the candidate commit, remote-main identity, build type, scenario, device, thermal state, refresh mode, battery mode, and test duration are known.

## Required device matrix

At minimum capture:

1. constrained or older supported phone;
2. current mid-range phone;
3. current high-refresh phone;
4. cutout or unusual-aspect device;
5. tablet if tablets remain supported.

Run the complete deterministic workload set on each class. Add longer ordinary-play captures after deterministic results are stable.

## Review checklist

For each report and accompanying diagnostics, review:

- p50/p95/p99 processing time;
- maximum processing time;
- slow-frame ratio;
- update versus render contribution;
- workload peaks correlated with timing spikes;
- heap growth across repeated scenarios;
- maximum ghost-write duration and frame capacity;
- ghost-write failures;
- `gfxinfo` jank/frame histograms;
- `meminfo` growth and retained memory;
- visible GC or allocation spikes;
- audio-thread behaviour around Bloom and transitions;
- thermal degradation during extended play.

A low mean does not excuse a damaging p99. A low p99 during deterministic lanes does not prove long-run stability. A fast ghost write does not prove process-death recovery or playback readability.

## Threshold derivation procedure

1. Capture repeat runs on every required device class from a clean synchronized `origin/main` candidate.
2. Remove environmental outliers only with a written reason.
3. Inspect distributions and workload-correlated spikes rather than averages alone.
4. Remediate material hotspots before defining acceptance limits.
5. Choose limits that preserve measured headroom and product expectations.
6. Record exact device/scenario limits in the versioned manifest.
7. Re-run collection with the manifest enabled.
8. Archive reports, diagnostics, manifest, manifest hash, candidate SHA, origin/main SHA, and `acceptance.txt` together.
9. Repeat whenever code, assets, target SDK, rendering policy, persistence format, or accepted device scope changes materially.

Do not mark a device/scenario row accepted merely because instrumentation completed. Instrumentation proves that evidence was collected; the evaluator proves only that it met the approved measured manifest; human review still covers visual smoothness, thermal behaviour, audio, input feel, and ghost readability.

## Remaining physical performance work

The source collector now has explicit capture surfaces for frame/memory/process diagnostics, before/after battery/thermal/power/CPU/audio state, and an opt-in Perfetto trace for deeper scheduling/GC/memory/power investigation. Those capabilities are **not physical evidence until they are actually run on the frozen signed candidate**. The repository still requires:

- representative physical-device runs across the accepted matrix;
- approved candidate-specific threshold values;
- long ordinary-play scenarios in addition to deterministic stress profiles;
- reviewer inspection of allocation/GC, audio-thread, thermal, battery, CPU/frequency, and workload-correlated traces/diagnostics, using the opt-in trace when deeper evidence is needed;
- captured maximum ghost-save evidence on the physical device matrix;
- remediation and repeated measurement of every material hotspot found;
- archival of the accepted diagnostics together with the exact candidate, device, threshold, and reviewer records.
