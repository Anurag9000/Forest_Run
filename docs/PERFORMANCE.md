# Forest Run — Performance Evidence Protocol

Performance is a release-evidence task, not a documentation adjective. The engine contains low-overhead telemetry and a physical-device harness, but no device class is accepted until its measurements are captured and reviewed.

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

Recording does not allocate per frame. Snapshotting copies and sorts timing arrays and must not be called from the frame loop.

## Physical profiling scenarios

`HardwarePerformanceProfileTest` is marked `@LargeTest`, so permanent emulator CI compiles it but does not execute it. It currently profiles:

- `OPENING_READABILITY`
- `BLOOM_SHOWCASE`

Each test:

1. clears deterministic state;
2. starts a fresh telemetry monitor before Activity creation;
3. launches the requested deterministic scenario;
4. allows startup/warmup frames;
5. records a sustained twenty-second interval;
6. verifies structural metric sanity;
7. writes a JSON report to the debug app’s external files directory.

The test intentionally does not impose universal timing thresholds. A constrained 60 Hz phone, a modern mid-range phone, and a high-refresh device require different interpretation.

## Run and collect profiles

Connect one authorized device, or set `FOREST_RUN_DEVICE_SERIAL` when multiple devices are attached:

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

The collector:

- resolves `adb` from PATH or Android SDK environment variables;
- requires an explicit/unique authorized device;
- clears old app profile reports;
- executes the selected large instrumentation test;
- pulls JSON reports;
- records device properties;
- captures `dumpsys gfxinfo`, `meminfo`, and display diagnostics.

## Evidence directory

A collection directory contains:

```text
device.properties
instrumentation.log
reports/*.json
gfxinfo.txt
meminfo.txt
display.txt
```

Do not compare reports unless the candidate commit, build type, scenario, device, thermal state, refresh mode, battery mode, and test duration are known.

## Required device matrix

At minimum capture:

1. constrained/older supported phone;
2. current mid-range phone;
3. current high-refresh phone;
4. cutout or unusual-aspect device;
5. tablet if tablets remain supported.

Run both deterministic scenarios on each class. Add longer ordinary-play captures when deterministic results are stable.

## Review checklist

For each report, review:

- p50/p95/p99 processing time;
- maximum processing time;
- slow-frame ratio;
- update versus render contribution;
- heap growth across repeated scenarios;
- `gfxinfo` jank/frame histograms;
- `meminfo` growth and retained memory;
- visible GC or allocation spikes;
- audio-thread and I/O behavior around Bloom and ghost saves;
- thermal degradation during extended play.

A low mean does not excuse a damaging p99. A low p99 during a deterministic lane does not prove long-run stability.

## Threshold procedure

Thresholds must be written only after representative evidence exists. Record them in a candidate-specific acceptance table:

| Device class | Refresh mode | Scenario | p95 target | p99 target | Slow-frame target | Memory ceiling | Status |
|---|---:|---|---:|---:|---:|---:|---|
| Constrained | TBD | Opening | TBD | TBD | TBD | TBD | Pending |
| Constrained | TBD | Bloom | TBD | TBD | TBD | TBD | Pending |
| Mid-range | TBD | Opening | TBD | TBD | TBD | TBD | Pending |
| Mid-range | TBD | Bloom | TBD | TBD | TBD | TBD | Pending |
| High-refresh | TBD | Opening | TBD | TBD | TBD | TBD | Pending |
| High-refresh | TBD | Bloom | TBD | TBD | TBD | TBD | Pending |

Do not mark a row accepted merely because the instrumentation test passed. The test proves that evidence was collected, not that the evidence is satisfactory.

## Remaining performance work

The current harness still needs:

- representative physical-device runs;
- long ordinary-play scenarios;
- allocation/GC tracing beyond heap snapshots;
- audio-thread tracing;
- ghost-save I/O duration evidence;
- thermal and battery behavior;
- thresholds derived from evidence;
- remediation of any material hotspots found.
