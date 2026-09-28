# Forest Run — Accessibility cadence epsilon/remainder coherence (2026-09-28)

## Exact-head failure

Android validation run 36390366235 on `71d55857` compiled and reached the JVM suite, where `RuntimeCadenceClockTest.one elapsed second is invariant to update partitioning` failed. The cadence clock intentionally tolerates tiny floating-point underflow around the 0.5 s poll boundary. However, after admitting an elapsed value microscopically below 0.5 s, the implementation applied the raw floating value to modulo. A value such as 0.49999997 therefore remained approximately 0.49999997 instead of becoming a near-zero remainder, allowing a second poll on the next ordinary frame.

## Correction

When a value below the interval is accepted solely by the epsilon tolerance, normalize it to the exact interval before modulo. Genuine overshoot and long disabled intervals still preserve their modulo remainder, so sampling does not drift and does not replay backlog bursts. A focused regression reproduces an epsilon-admitted boundary and proves a tiny following frame cannot trigger a duplicate poll; the existing 30/60/120 Hz partition test remains authoritative.

No gameplay timing, physics, accessibility announcement wording, or poll interval is retuned. Exact-head Android host/JVM/API-35 validation remains required.
