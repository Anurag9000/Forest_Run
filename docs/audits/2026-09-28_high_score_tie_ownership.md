# Forest Run — Strict ownership of a terminal NEW HIGH tie (2026-09-28)

## Finding

The stale-lower-record repair reconciled terminal summaries with the canonical stored score, but it used `score >= bestForSummary`. Two long-lived run owners could both locally mark 500 as new; after one owner published 500, the other stale owner still treated equality with that durable score as a NEW HIGH. `save()` likewise only cleared the marker when `score < highScore`. A tie with a record already owned by another run is not a newly established durable record.

## Repair

Track whether this run itself has already published a score that was strictly greater than the durable record observed at its save boundary. A persistent terminal preview is NEW HIGH only when the local run is marked new and either (a) it is still strictly above the current durable record or (b) this same run already published that record. A stale equal-score owner therefore loses the label, while repeated saves by the actual record-owning run remain idempotent. Reset clears the ownership marker. Nonpersistent/debug runs retain their isolated local presentation and never publish the marker.

Robolectric regressions cover equal-score concurrent owners and repeated saves by the actual winning owner, alongside the existing stale-lower, genuine-unsaved and nonpersistent cases.

## Boundary

This is process-local ownership around the existing SharedPreferences record. It does not claim cloud/multi-device leaderboard consensus or distributed transactions.
