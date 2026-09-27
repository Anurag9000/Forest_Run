# Forest Run — Independent malformed action-feasibility fields fail closed (2026-09-28)

## Source-derived defect

`EncounterActionFeasibility.observe` sanitized each bad field independently (NaN, infinities and negatives become zero) but rejected the result only if approach speed, jump speed or gravity became zero. Consequently a malformed required clearance, gesture-decision time or safety margin could become zero cost and yield `jumpFeasible=true` and/or `duckFeasible=true`. The previous invalid-input test passed the same malformed number in *every* field, which always made speed/gravity invalid and concealed this loophole. This affects the evidence/experiment boundary, not directly the production movement integrator.

## Correction and proof obligation

Validate all seven raw inputs independently before computing any feasibility decision. Lead, clearance, decision and safety margin must be finite/nonnegative; approach speed, jump velocity and gravity must be finite/strictly positive. Retain bounded sanitized report fields and `invalidObservation` so rejected reports remain finite and both outcomes fail closed. Preserve legitimate zero clearance, zero decision, zero safety and the existing physical jump equations.

Add an independent-field regression covering NaN, both infinities and negative values for each input, plus zero for the three strictly positive physical parameters. This also closes the coverage gap that let the previous all-fields-invalid test pass while some individual malformed inputs were accidentally certified. Require exact-head host/JVM and API35 CI for execution evidence. Do not infer all-pair/mixed encounter fairness or physical acceptance from this isolated timing model.
