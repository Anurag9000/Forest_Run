# Forest Run — Debug spare persistence regression follows exclusive departure ownership (2026-09-28)

Android validation run 36385936580 reached the JVM suite and exposed one stale expectation in `DebugScenarioPersistenceTest`: after Fox/Wolf spare branches were moved onto the exclusive safe-departure seam, the test still expected an ordinary pass-history increment for a persistent spare. That expectation contradicts the corrected relationship contract: a spare/stand-down writes spare + encounter history when persistence is authorized, but never also writes ordinary pass history.

The test now retains its original debug-versus-normal persistence proof for spare and encounter counts while asserting pass history remains zero in both cases. Production code is unchanged by this correction. Exact-head Android/JVM/API-35 validation remains required.
