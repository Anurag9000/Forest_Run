#!/usr/bin/env python3
"""One-shot exact hardening: bind installed APK metadata to pulled APK evidence."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "scripts/validate_installed_candidate_identity.py"
SELF = Path(__file__)
WORKFLOW = ROOT / ".github/workflows/installed-identity-apk-binding.yml"


def replace_once(old: str, new: str, label: str) -> None:
    text = TARGET.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    TARGET.write_text(text.replace(old, new, 1), encoding="utf-8")


def main() -> None:
    replace_once(
        '''    names: set[str] = set()\n    seen_base = False\n    for index, raw_apk in enumerate(apk_set):\n''',
        '''    names: set[str] = set()\n    apk_observations: dict[str, tuple[str, int]] = {}\n    seen_base = False\n    for index, raw_apk in enumerate(apk_set):\n''',
        "apk observation map",
    )
    replace_once(
        '''        _integer(apk["size_bytes"], f"{label}.size_bytes", minimum=1)\n        signer = _string(\n''',
        '''        size_bytes = _integer(apk["size_bytes"], f"{label}.size_bytes", minimum=1)\n        apk_observations[name] = (digest, size_bytes)\n        signer = _string(\n''',
        "apk size capture",
    )
    replace_once(
        '''    seen_paths: set[str] = set()\n    seen_inodes: set[tuple[int, int]] = set()\n    for index, raw_reference in enumerate(raw_files):\n''',
        '''    seen_paths: set[str] = set()\n    seen_inodes: set[tuple[int, int]] = set()\n    evidence_observations: dict[str, tuple[str, int]] = {}\n    for index, raw_reference in enumerate(raw_files):\n''',
        "evidence observation map",
    )
    replace_once(
        '''        if actual != expected:\n            raise InstalledIdentityError(f"evidence digest mismatch: {relative}")\n        if metadata.st_ino:\n''',
        '''        if actual != expected:\n            raise InstalledIdentityError(f"evidence digest mismatch: {relative}")\n        evidence_observations[relative] = (actual, metadata.st_size)\n        if metadata.st_ino:\n''',
        "evidence observation capture",
    )
    replace_once(
        '''            seen_inodes.add(identity)\n\n    return InstalledIdentitySummary(\n''',
        '''            seen_inodes.add(identity)\n\n    for name, observation in apk_observations.items():\n        evidence_path = f"apks/{name}"\n        if evidence_observations.get(evidence_path) != observation:\n            raise InstalledIdentityError(\n                f"installed_package.apk_set entry {name} does not match pulled APK evidence {evidence_path}"\n            )\n\n    return InstalledIdentitySummary(\n''',
        "apk-to-evidence cross binding",
    )

    source = TARGET.read_text(encoding="utf-8")
    if "does not match pulled APK evidence" not in source:
        raise SystemExit("APK evidence cross-binding did not land")
    for path in (WORKFLOW, SELF):
        if path.exists():
            path.unlink()


if __name__ == "__main__":
    main()
