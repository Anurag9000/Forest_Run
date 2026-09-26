# Forest Run — Raw screenshot PNG structural integrity (2026-09-26)

## Trace

`write_screenshot_capture_evidence.py` and `finalize_screenshot_capture_session.py` already validate PNG structure through `verify_curated_screenshot_set._inspect_png`. The raw-session verifier separately implemented only an eight-byte PNG signature and width/height header check, then hashed all remaining bytes. A fabricated 24-byte header and a matching forged sidecar image digest could therefore pass the raw verifier's image gate even though it was not a decodable PNG. The curated gate was stronger, but the raw gate must not independently report structurally invalid screenshots as valid.

## Remediation

`verify_raw_screenshot_set.py` now uses the existing bounded shared PNG validator, including chunk order, CRC, required IHDR/IDAT/IEND, zlib stream, scanline geometry and trailing-data checks. Shared errors are translated to `RawScreenshotSetError`. The duplicate weak parser is deleted.

The raw verifier's unit fixture now creates structurally valid RGB PNGs rather than fake headers. Regression tests prove rejection of a fabricated header even when its sidecar digest matches, a bad IDAT CRC, truncated IEND and trailing payload.

## Validation boundary

This is source/tooling correctness only. A structurally valid PNG is not itself proof of actual device capture, scenario readiness, human screenshot approval or Play delivery; the independent candidate/APK/device/session evidence remains required.
