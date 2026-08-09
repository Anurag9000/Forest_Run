#!/usr/bin/env python3
"""One-shot reconciliation for candidate evidence hardening and canonical docs."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github/workflows/candidate-evidence-reconciliation.yml"
SELF = Path(__file__)


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor in {path}, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def harden_human_validator() -> None:
    path = ROOT / "scripts/validate_human_acceptance.py"
    replace_once(path, "import re\n", "import re\nimport stat\n", "human stat import")
    replace_once(
        path,
        '''def _resolve_inside(base: Path, relative: str, label: str) -> Path:\n    base = base.resolve()\n    lexical = base / relative\n    try:\n        lexical.relative_to(base)\n    except ValueError as exc:\n        raise HumanAcceptanceError(f"{label} escapes the evidence root") from exc\n    resolved = lexical.resolve()\n    try:\n        resolved.relative_to(base)\n    except ValueError as exc:\n        raise HumanAcceptanceError(f"{label} resolves outside the evidence root") from exc\n    return resolved\n''',
        '''def _resolve_inside(base: Path, relative: str, label: str) -> Path:\n    base = base.resolve()\n    lexical = base / relative\n    try:\n        path_parts = lexical.relative_to(base).parts\n    except ValueError as exc:\n        raise HumanAcceptanceError(f"{label} escapes the evidence root") from exc\n\n    current = base\n    for part in path_parts:\n        current = current / part\n        try:\n            metadata = current.lstat()\n        except FileNotFoundError:\n            break\n        except OSError as exc:\n            raise HumanAcceptanceError(f"could not inspect {label}: {current}: {exc}") from exc\n        if stat.S_ISLNK(metadata.st_mode):\n            raise HumanAcceptanceError(\n                f"{label} must not traverse a symbolic link: {current}"\n            )\n\n    resolved = lexical.resolve()\n    try:\n        resolved.relative_to(base)\n    except ValueError as exc:\n        raise HumanAcceptanceError(f"{label} resolves outside the evidence root") from exc\n    return resolved\n''',
        "human symlink-safe resolver",
    )


def harden_governance_validator() -> None:
    path = ROOT / "scripts/validate_release_governance.py"
    replace_once(path, "import re\n", "import re\nimport stat\n", "governance stat import")
    replace_once(
        path,
        '''def _resolve_inside(base: Path, relative: str, label: str) -> Path:\n    canonical = base.resolve()\n    path = (canonical / relative).resolve()\n    try:\n        path.relative_to(canonical)\n    except ValueError as exc:\n        raise GovernanceError(f"{label} resolves outside the governance evidence root") from exc\n    return path\n''',
        '''def _resolve_inside(base: Path, relative: str, label: str) -> Path:\n    canonical = base.resolve()\n    lexical = canonical / relative\n    try:\n        path_parts = lexical.relative_to(canonical).parts\n    except ValueError as exc:\n        raise GovernanceError(f"{label} escapes the governance evidence root") from exc\n\n    current = canonical\n    for part in path_parts:\n        current = current / part\n        try:\n            metadata = current.lstat()\n        except FileNotFoundError:\n            break\n        except OSError as exc:\n            raise GovernanceError(f"could not inspect {label}: {current}: {exc}") from exc\n        if stat.S_ISLNK(metadata.st_mode):\n            raise GovernanceError(\n                f"{label} must not traverse a symbolic link: {current}"\n            )\n\n    resolved = lexical.resolve()\n    try:\n        resolved.relative_to(canonical)\n    except ValueError as exc:\n        raise GovernanceError(f"{label} resolves outside the governance evidence root") from exc\n    return resolved\n''',
        "governance symlink-safe resolver",
    )


def add_symlink_tests() -> None:
    human = ROOT / "scripts/test_validate_human_acceptance.py"
    replace_once(
        human,
        '''\n\nif __name__ == "__main__":\n    unittest.main()\n''',
        '''\n\n    def test_symlink_component_cannot_alias_human_evidence(self) -> None:\n        if not hasattr(Path, "symlink_to"):\n            self.skipTest("symbolic links are unavailable")\n        with tempfile.TemporaryDirectory() as temporary:\n            root = Path(temporary)\n            compiled, _ = self.compile_valid(root)\n            alias = root / "human-alias"\n            try:\n                alias.symlink_to(root / "human", target_is_directory=True)\n            except OSError as exc:\n                self.skipTest(f"symbolic links unavailable: {exc}")\n            review = next(\n                item for item in compiled["reviews"]\n                if item["device_class"] == "older_phone"\n            )\n            review["evidence_files"][0]["path"] = "human-alias/older_phone.txt"\n            with self.assertRaises(human.HumanAcceptanceError) as raised:\n                human.validate_bundle(\n                    compiled,\n                    source_bytes=json.dumps(compiled).encode(),\n                    evidence_base=root,\n                )\n            self.assertIn("must not traverse a symbolic link", str(raised.exception))\n\n\nif __name__ == "__main__":\n    unittest.main()\n''',
        "human symlink test",
    )

    governance_test = ROOT / "scripts/test_validate_release_governance.py"
    replace_once(
        governance_test,
        '''\n\nif __name__ == "__main__":\n    unittest.main()\n''',
        '''\n\n    def test_symlink_component_cannot_alias_governance_evidence(self) -> None:\n        if not hasattr(Path, "symlink_to"):\n            self.skipTest("symbolic links are unavailable")\n        with tempfile.TemporaryDirectory() as temporary:\n            root = Path(temporary)\n            compiled, _ = self.compile_valid(root)\n            real_dir = root / "governance-evidence"\n            alias = root / "governance-alias"\n            try:\n                alias.symlink_to(real_dir, target_is_directory=True)\n            except OSError as exc:\n                self.skipTest(f"symbolic links unavailable: {exc}")\n            reference = compiled["evidence"]["asset_provenance"]\n            reference["path"] = "governance-alias/asset_provenance.txt"\n            with self.assertRaises(governance.GovernanceError) as raised:\n                governance.validate_bundle(\n                    compiled,\n                    source_bytes=json.dumps(compiled).encode(),\n                    evidence_base=root,\n                )\n            self.assertIn("must not traverse a symbolic link", str(raised.exception))\n\n\nif __name__ == "__main__":\n    unittest.main()\n''',
        "governance symlink test",
    )


def reconcile_performance_doc() -> None:
    path = ROOT / "docs/PERFORMANCE.md"
    replace_once(
        path,
        '''- records whether acceptance thresholds were supplied;\n- hashes the supplied threshold manifest with Python's portable SHA-256 implementation;\n- writes an explicit accepted, failed, or pending result.\n''',
        '''- records whether acceptance thresholds were supplied;\n- hashes the supplied threshold manifest with Python's portable SHA-256 implementation;\n- writes an explicit accepted, failed, or pending result;\n- captures before/after `dumpsys battery`, `thermalservice`, `power`, `cpuinfo`, `audio`, and `media.audio_flinger` snapshots;\n- captures post-run `gfxinfo ... framestats`, `meminfo`, `procstats`, package identity, and display diagnostics even when the instrumentation workload fails;\n- can optionally overlap the physical workload with a Perfetto system trace when `FOREST_RUN_CAPTURE_PERFETTO=1`, retaining the trace only as diagnostic evidence rather than silently mixing traced and untraced threshold runs.\n''',
        "performance collector capabilities",
    )
    replace_once(
        path,
        '''device.properties\ninstrumentation.log\nreports/*.json\ngfxinfo.txt\nmeminfo.txt\ndisplay.txt\nacceptance.txt\n''',
        '''device.properties\ninstrumentation.log\nreports/*.json\nbattery-before.txt / battery-after.txt\nthermalservice-before.txt / thermalservice-after.txt\npower-before.txt / power-after.txt\ncpuinfo-before.txt / cpuinfo-after.txt\naudio-before.txt / audio-after.txt\naudio-flinger-before.txt / audio-flinger-after.txt\ngfxinfo-framestats-after.txt\nmeminfo-after.txt\nprocstats-after.txt\npackage-after.txt\ndisplay.txt\ngfxinfo.txt / meminfo.txt   # backward-compatible aliases\nsystem-trace.perfetto-trace # only when explicitly requested\nacceptance.txt\n''',
        "performance evidence directory",
    )
    replace_once(
        path,
        '''## Remaining physical performance work\n\nThe repository still requires:\n\n- representative physical-device runs;\n- approved candidate-specific threshold values;\n- long ordinary-play scenarios;\n- allocation and GC tracing beyond heap snapshots;\n- audio-thread tracing;\n- captured maximum ghost-save evidence on the physical device matrix;\n- thermal and battery behaviour;\n- remediation and repeated measurement of any material hotspots found.\n''',
        '''## Remaining physical performance work\n\nThe source collector now has explicit capture surfaces for frame/memory/process diagnostics, before/after battery/thermal/power/CPU/audio state, and an opt-in Perfetto trace for deeper scheduling/GC/memory/power investigation. Those capabilities are **not physical evidence until they are actually run on the frozen signed candidate**. The repository still requires:\n\n- representative physical-device runs across the accepted matrix;\n- approved candidate-specific threshold values;\n- long ordinary-play scenarios in addition to deterministic stress profiles;\n- reviewer inspection of allocation/GC, audio-thread, thermal, battery, CPU/frequency, and workload-correlated traces/diagnostics, using the opt-in trace when deeper evidence is needed;\n- captured maximum ghost-save evidence on the physical device matrix;\n- remediation and repeated measurement of every material hotspot found;\n- archival of the accepted diagnostics together with the exact candidate, device, threshold, and reviewer records.\n''',
        "performance remaining work",
    )


def reconcile_acceptance_docs() -> None:
    path = ROOT / "docs/DEVICE_ACCEPTANCE.md"
    replace_once(
        path,
        '''At least two distinct named reviewers are required for final visual/store approval.\n''',
        '''At least two distinct named reviewers are required for final visual/store approval.\n\nThese seven per-session manual fields are intentionally coarse physical-session gates. They do **not** replace the detailed human gameplay, fairness, TalkBack/accessibility, Garden/wardrobe/ghost, and presentation matrix. After this device manifest validates, compile and validate the separate candidate-bound human layer described in [`HUMAN_ACCEPTANCE.md`](HUMAN_ACCEPTANCE.md) with `scripts/compile_human_acceptance.py` and `scripts/validate_human_acceptance.py`. Every human review is bound back to one real session ID from this physical manifest.\n''',
        "device acceptance human layer",
    )

    index = ROOT / "docs/RELEASE_EVIDENCE_INDEX.md"
    replace_once(
        index,
        '''- physical-device acceptance sessions;\n- performance profiles;\n- screenshot capture and curation;\n- graphics and metadata generation;\n- manual visual, audio, haptic, accessibility, privacy, audience, content-rating, and store-policy approvals.\n''',
        '''- physical-device acceptance sessions;\n- detailed human gameplay/accessibility/presentation acceptance;\n- performance profiles and physical diagnostics;\n- screenshot capture and curation;\n- graphics and metadata generation;\n- candidate-bound security/licensing/privacy/store/presentation governance and final approvals.\n''',
        "index pipeline inventory",
    )
    replace_once(
        index,
        '''  --entry device_acceptance=release/evidence/device-acceptance.json \\\n  --entry device_aggregate=release/evidence/device-acceptance-aggregate.json \\\n  --entry screenshot_manifest=release/google-play/screenshots/screenshot_manifest.json \\\n''',
        '''  --entry device_acceptance=release/evidence/device-acceptance.json \\\n  --entry device_aggregate=release/evidence/device-acceptance-aggregate.json \\\n  --entry human_acceptance=release/evidence/human-acceptance.json \\\n  --entry release_governance=release/evidence/release-governance.json \\\n  --entry screenshot_manifest=release/google-play/screenshots/screenshot_manifest.json \\\n''',
        "index build entries",
    )
    replace_once(
        index,
        '''  --require-bound-kind device_acceptance \\\n  --require-bound-kind device_aggregate \\\n  --require-bound-kind screenshot_manifest \\\n''',
        '''  --require-bound-kind device_acceptance \\\n  --require-bound-kind device_aggregate \\\n  --require-bound-kind human_acceptance \\\n  --require-bound-kind release_governance \\\n  --require-bound-kind screenshot_manifest \\\n''',
        "index build required kinds",
    )
    replace_once(
        index,
        '''  --require-bound-kind device_acceptance \\\n  --require-bound-kind device_aggregate \\\n  --require-bound-kind screenshot_manifest \\\n''',
        '''  --require-bound-kind device_acceptance \\\n  --require-bound-kind device_aggregate \\\n  --require-bound-kind human_acceptance \\\n  --require-bound-kind release_governance \\\n  --require-bound-kind screenshot_manifest \\\n''',
        "index verify required kinds",
    )


def reconcile_security_and_readme() -> None:
    security = ROOT / "docs/SECURITY_AND_LICENSING_GOVERNANCE.md"
    replace_once(
        security,
        '''Forest Run is a public source repository, but public release governance is not complete until the repository owner makes and records the decisions below. This document is an explicit gate; it is not a substitute for those decisions.\n''',
        '''Forest Run is a public source repository, but public release governance is not complete until the repository owner makes and records the decisions below. This document is an explicit gate; it is not a substitute for those decisions. The source now also provides [`compile_release_governance.py`](../scripts/compile_release_governance.py) and [`validate_release_governance.py`](../scripts/validate_release_governance.py), which require candidate-bound evidence for every listed security/licensing/privacy/store/presentation decision while deliberately refusing to invent those external approvals. See [`RELEASE_GOVERNANCE_EVIDENCE.md`](RELEASE_GOVERNANCE_EVIDENCE.md).\n''',
        "security governance tooling",
    )

    readme = ROOT / "README.md"
    replace_once(
        readme,
        '''That does **not** make Forest Run a physically accepted release candidate or a store-ready production release. Representative-device performance/fairness/accessibility evidence, real signing and signed-install verification, internal-store delivery, final asset/audio/haptic review, privacy/store-policy decisions, dependency/licence/security review, and final candidate-bound approvals remain external gates.\n''',
        '''That does **not** make Forest Run a physically accepted release candidate or a store-ready production release. Source tooling now also captures extended physical diagnostics and compiles fail-closed human-acceptance plus release-governance evidence, but representative-device performance/fairness/accessibility runs, real signing and signed-install verification, internal-store delivery, final asset/audio/haptic review, privacy/store-policy decisions, dependency/licence/security review, and accountable final approvals remain external gates.\n''',
        "readme external-gate wording",
    )
    replace_once(
        readme,
        '''| [`docs/DEVICE_ACCEPTANCE.md`](docs/DEVICE_ACCEPTANCE.md) | Candidate identity, device/scenario evidence, thresholds, approvals, and release decision |\n| [`docs/RELEASE.md`](docs/RELEASE.md) | Correctness, validation, packaging, hardware, signing, and store checklist |\n''',
        '''| [`docs/DEVICE_ACCEPTANCE.md`](docs/DEVICE_ACCEPTANCE.md) | Candidate identity, device/scenario evidence, thresholds, approvals, and release decision |\n| [`docs/HUMAN_ACCEPTANCE.md`](docs/HUMAN_ACCEPTANCE.md) | Candidate-bound gameplay, TalkBack/accessibility, and presentation review matrix |\n| [`docs/RELEASE_GOVERNANCE_EVIDENCE.md`](docs/RELEASE_GOVERNANCE_EVIDENCE.md) | Security, licensing, privacy, store, provenance, release-note, and final decision evidence |\n| [`docs/RELEASE.md`](docs/RELEASE.md) | Correctness, validation, packaging, hardware, signing, and store checklist |\n''',
        "readme docs table",
    )


def main() -> None:
    harden_human_validator()
    harden_governance_validator()
    add_symlink_tests()
    reconcile_performance_doc()
    reconcile_acceptance_docs()
    reconcile_security_and_readme()

    human_source = (ROOT / "scripts/validate_human_acceptance.py").read_text(encoding="utf-8")
    governance_source = (ROOT / "scripts/validate_release_governance.py").read_text(encoding="utf-8")
    if "must not traverse a symbolic link" not in human_source:
        raise SystemExit("human symlink rejection did not land")
    if "must not traverse a symbolic link" not in governance_source:
        raise SystemExit("governance symlink rejection did not land")
    index_source = (ROOT / "docs/RELEASE_EVIDENCE_INDEX.md").read_text(encoding="utf-8")
    for token in ("human_acceptance", "release_governance"):
        if token not in index_source:
            raise SystemExit(f"release evidence index missing {token}")

    for path in (WORKFLOW, SELF):
        if path.exists():
            path.unlink()


if __name__ == "__main__":
    main()
