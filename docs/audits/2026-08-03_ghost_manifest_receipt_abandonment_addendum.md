# Ghost Manifest Receipt-Abandonment Addendum — 2026-08-03

## Reason for addendum

Final production diff review found a narrow consistency opportunity after the main ghost-artifact-manifest audit was recorded.

When a pending candidate receipt does not match the durable ghost, recovery has already loaded the durable frames to prove that the new candidate never landed. The first manifest implementation then called the ordinary manifest-only path, whose healthy-start optimization may return `ALREADY_APPLIED` without hashing the ghost when best distance already meets the manifest distance.

That optimization is correct for ordinary startup, where repeated full-file hashing is intentionally avoided. It was unnecessarily weak in the receipt-abandonment branch because the durable frames were already available at no additional I/O cost.

## Final behavior

`recoverReceipt(...)` now calls:

```text
recoverManifest(durableGhost)
```

The manifest recovery helper accepts an optional known ghost.

When known frames are supplied, recovery validates the older manifest against those frames **before** the already-applied distance fast path:

```text
known ghost does not match older manifest
    → CORRUPT_MANIFEST
    → block new promotion

known ghost matches older manifest
and best distance already meets manifest distance
    → ALREADY_APPLIED
    → map abandoned candidate to ABANDONED_UNWRITTEN_GHOST
```

Ordinary receipt-free startup still preserves the no-ghost-load fast path when best distance already meets manifest distance.

## Why this is behavior-preserving

- No gameplay path changes.
- No additional ghost read is introduced: receipt mismatch already loaded the frames.
- The stale candidate receipt is still cleared after proving its candidate did not land.
- Existing ghost and best distance are not modified.
- A healthy older manifest remains preserved.
- A known mismatched older manifest can no longer be silently accepted through the lazy path.

## Test and contract evidence

Added:

```text
GhostPromotionReceiptAbandonmentTest
```

It verifies:

- candidate receipt does not identify the durable ghost;
- older manifest also does not identify the durable ghost;
- best distance already equals the older manifest distance;
- result is `CORRUPT_MANIFEST`;
- new promotion remains blocked;
- stale receipt is cleared;
- best distance is unchanged;
- no distance write occurs;
- the already-loaded durable ghost is reused.

The ghost source contract now requires:

```text
recoverManifest(durableGhost)
knownGhost manifest validation before currentBest fast path
```

A focused executable Kotlin harness passed both:

```text
receipt mismatch + older manifest mismatch → CORRUPT_MANIFEST
receipt-free healthy applied manifest       → ALREADY_APPLIED without ghost load
```

Output:

```text
receipt abandonment manifest checks passed
```

## Evidence boundary

This addendum records focused Kotlin/JVM and source-shape evidence. Exact-head Android Gradle, Robolectric, emulator, and physical-device execution remain unclaimed.
