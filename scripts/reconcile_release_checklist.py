#!/usr/bin/env python3
"""Reconcile release checklist with validated feedback and save-integrity work."""

from pathlib import Path

path = Path("docs/RELEASE.md")
text = path.read_text(encoding="utf-8")

replacements = [
    (
        "Forest Run is a feature-rich alpha with the primary correctness-remediation pass implemented. A release candidate must still pass connected-device, physical-hardware, signed-artifact, performance, settings/accessibility, and store-acceptance gates.",
        "Forest Run is a feature-rich alpha with the primary correctness-remediation pass implemented. A release candidate must still pass connected-device, physical-hardware, signed-artifact, measured-performance, visual-acceptance, and store-acceptance gates.",
    ),
    (
        "- [x] Menu, Garden, HUD, debug controls, and rest UI share one aspect-preserving safe-content transform with inverse touch mapping\n",
        "- [x] Menu, Garden, HUD, debug controls, and rest UI share one aspect-preserving safe-content transform with inverse touch mapping\n"
        "- [x] Reduced-motion, audio, and haptic preferences persist independently of progression and are enforced at camera, particles, cinematic shimmer, music, SFX, and vibration boundaries\n"
        "- [x] Save state is schema-versioned, known corrupt values are repaired before runtime reads, incomplete summaries are discarded, and writes are clamped or saturating\n"
        "- [x] Newer-schema preferences and ghost files are preserved while the older build uses isolated compatibility storage\n",
    ),
    (
        "- [ ] Validate safe-content behavior and density scaling on representative cutout, unusual-aspect, phone, and tablet hardware\n"
        "- [ ] Add reduced-motion, audio, and haptic user settings\n"
        "- [ ] Profile and remove material per-frame allocations or emitter churn found on hardware\n"
        "- [ ] Verify broader save migration, SharedPreferences corruption recovery, and forward compatibility\n"
        "- [ ] Decide whether fixed landscape and the remaining procedural scenic layers are final product choices\n",
        "- [ ] Validate safe-content behavior, density scaling, and feedback controls on representative cutout, unusual-aspect, phone, and tablet hardware\n"
        "- [ ] Profile and remove material per-frame allocations or emitter churn found on hardware\n"
        "- [ ] Decide whether fixed landscape and the remaining procedural scenic layers are final product choices\n",
    ),
    (
        "- [x] R8 mapping contains actually renamed Forest Run classes\n",
        "- [x] R8 mapping contains actually renamed Forest Run classes\n"
        "- [x] Feedback preference defaults, persistence, wrong-type recovery, reduced-motion camera/particle/shimmer behavior, and non-overlapping menu hit regions\n"
        "- [x] Legacy save repair, migration idempotence, partial-summary rejection, unknown-key preservation, clamped writes, and saturating counters\n"
        "- [x] Future-schema preference and ghost preservation with compatibility-namespace round trips\n",
    ),
    (
        "- [ ] Execute `connectedDebugAndroidTest` on an emulator and physical device\n"
        "- [ ] Add a deterministic interruption test around the real `GameThread`/`GameView` shutdown boundary if feasible without instrumentation\n"
        "- [ ] Add broader SharedPreferences save-corruption and migration fixtures\n"
        "- [ ] Add signed-release installation and launch smoke tests\n",
        "- [ ] Execute `connectedDebugAndroidTest` on an emulator and physical device\n"
        "- [ ] Add a deterministic interruption test around the real `GameThread`/`GameView` shutdown boundary if feasible without instrumentation\n"
        "- [ ] Add signed-release installation and launch smoke tests\n",
    ),
    (
        "| Safe-content flow | Essential UI and mapped tap regions remain inside cutouts/system bars without distorting readability |\n"
        "| Lifecycle recovery | Background/resume, process recreation, repeated intents, and surface recreation preserve coherent state |\n",
        "| Safe-content flow | Essential UI and mapped tap regions remain inside cutouts/system bars without distorting readability |\n"
        "| Feedback controls | Motion, audio, and haptic toggles remain reachable before a run and take effect immediately without altering gameplay physics or hazard telegraphs |\n"
        "| Save recovery | Legacy/corrupt saves repair safely, future-version primary data remains untouched, and compatibility storage behaves coherently |\n"
        "| Lifecycle recovery | Background/resume, process recreation, repeated intents, and surface recreation preserve coherent state |\n",
    ),
]

for old, new in replacements:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Expected one checklist fragment, found {count}: {old[:80]!r}")
    text = text.replace(old, new, 1)

path.write_text(text, encoding="utf-8")
