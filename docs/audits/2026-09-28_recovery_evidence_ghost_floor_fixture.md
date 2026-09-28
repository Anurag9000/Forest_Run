# Forest Run — Recovery-evidence test uses the Ghost floor, not run distance (2026-09-28)

## Exact-head failure

Android validation on `e099fa79` compiled and ran the JVM suite but failed
`RecoveryEvidenceMaintenanceIntegrationTest.corrupt ghost receipt is discarded without deleting valid strong manifest`.
The fixture called `SaveManager.saveBestDistance` before discarding a corrupt promotion receipt and expected the preserved manifest to inspect as CLEAN.

The persistence model now intentionally separates the long-horizon run-distance achievement
(`best_run_distance`) from the historical best-Ghost promotion floor
(`best_distance`). Therefore `SaveManager.saveBestDistance` can no longer be used to pretend that the durable Ghost has already reached the manifest distance. After the receipt was removed, the valid manifest correctly remained PENDING because its Ghost floor was still lower.

## Correction

The test now seeds the best-Ghost floor through the same captured-namespace
`NamespaceBoundGhostPromotionArtifactStore` used by recovery maintenance. The production implementation is unchanged. This preserves the test's real purpose: deleting a corrupt receipt must not delete an independently valid strong manifest whose durable Ghost and promotion distance already match it.

Require exact-head Android validation before treating this regression as closed.
