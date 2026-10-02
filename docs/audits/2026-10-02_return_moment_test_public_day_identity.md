# Forest Run — Return Moment concurrency regression uses public day-state behavior (2026-10-02)

The new Return Moment race regression initially referenced a non-existent test helper for the local-calendar day identifier. Production deliberately keeps that calculation private. The test now obtains the expected day through the real public acknowledgement path, resets the state, then performs the concurrent acknowledgement/outcome race and compares the final persisted day against that observed canonical value. No production API or behavior changes.
