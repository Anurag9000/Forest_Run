#!/usr/bin/env python3
"""One-shot exact hardening for physical acceptance path and JSON integrity."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VALIDATOR = ROOT / "scripts/validate_device_acceptance.py"
TESTS = ROOT / "scripts/test_validate_device_acceptance.py"
WORKFLOW = ROOT / ".github/workflows/device-acceptance-path-integrity.yml"
SELF = Path(__file__)


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor in {path}, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def main() -> None:
    replace_once(
        VALIDATOR,
        "import re\n",
        "import re\nimport stat\n",
        "stat import",
    )
    replace_once(
        VALIDATOR,
        "from typing import Any, Iterable, Mapping, Sequence\n",
        "from typing import Any, Iterable, Mapping, Sequence\n\nimport strict_json\n",
        "strict json import",
    )
    replace_once(
        VALIDATOR,
        '''def _hash_bounded_file(path: Path, label: str, maximum_bytes: int) -> tuple[str, os.stat_result]:\n    try:\n        stat_result = path.stat()\n    except FileNotFoundError as exc:\n        raise EvidenceError(f"{label} is missing: {path}") from exc\n    except OSError as exc:\n        raise EvidenceError(f"could not inspect {label} {path}: {exc}") from exc\n    if not path.is_file():\n        raise EvidenceError(f"{label} is not a regular file: {path}")\n''',
        '''def _resolve_evidence_file(base: Path, relative: str, label: str) -> Path:\n    canonical = base.resolve()\n    lexical = canonical / relative\n    try:\n        parts = lexical.relative_to(canonical).parts\n    except ValueError as exc:\n        raise EvidenceError(f"{label} escapes evidence base") from exc\n\n    current = canonical\n    for part in parts:\n        current = current / part\n        try:\n            metadata = current.lstat()\n        except FileNotFoundError:\n            break\n        except OSError as exc:\n            raise EvidenceError(f"could not inspect {label}: {current}: {exc}") from exc\n        if stat.S_ISLNK(metadata.st_mode):\n            raise EvidenceError(f"{label} must not traverse a symbolic link: {current}")\n\n    resolved = lexical.resolve()\n    try:\n        resolved.relative_to(canonical)\n    except ValueError as exc:\n        raise EvidenceError(f"{label} resolves outside evidence base") from exc\n    return resolved\n\n\ndef _hash_bounded_file(path: Path, label: str, maximum_bytes: int) -> tuple[str, os.stat_result]:\n    try:\n        stat_result = path.lstat()\n    except FileNotFoundError as exc:\n        raise EvidenceError(f"{label} is missing: {path}") from exc\n    except OSError as exc:\n        raise EvidenceError(f"could not inspect {label} {path}: {exc}") from exc\n    if stat.S_ISLNK(stat_result.st_mode):\n        raise EvidenceError(f"{label} must not be a symbolic link: {path}")\n    if not stat.S_ISREG(stat_result.st_mode):\n        raise EvidenceError(f"{label} is not a regular file: {path}")\n''',
        "safe resolver and lstat",
    )
    replace_once(
        VALIDATOR,
        "        after = path.stat()\n",
        "        after = path.lstat()\n",
        "post-hash lstat",
    )
    replace_once(
        VALIDATOR,
        '''        canonical_base = evidence_base.resolve()\n        resolved_artifact = (canonical_base / artifact_path).resolve()\n        try:\n            resolved_artifact.relative_to(canonical_base)\n        except ValueError as exc:\n            raise EvidenceError(\n                "candidate.artifact_path escapes manifest directory"\n            ) from exc\n''',
        '''        canonical_base = evidence_base.resolve()\n        resolved_artifact = _resolve_evidence_file(\n            canonical_base,\n            artifact_path,\n            "candidate.artifact_path",\n        )\n''',
        "artifact path resolution",
    )
    replace_once(
        VALIDATOR,
        '''            candidate_path = (canonical_base / relative_path).resolve()\n            try:\n                candidate_path.relative_to(canonical_base)\n            except ValueError as exc:\n                raise EvidenceError(\n                    f"evidence path escapes evidence base: {relative_path}"\n                ) from exc\n''',
        '''            candidate_path = _resolve_evidence_file(\n                canonical_base,\n                relative_path,\n                f"evidence path {relative_path}",\n            )\n''',
        "scenario evidence path resolution",
    )
    replace_once(
        VALIDATOR,
        '''def load_and_validate(path: Path) -> ValidationSummary:\n    try:\n        size = path.stat().st_size\n    except FileNotFoundError as exc:\n        raise EvidenceError(f"acceptance manifest is missing: {path}") from exc\n    except OSError as exc:\n        raise EvidenceError(f"could not inspect acceptance manifest {path}: {exc}") from exc\n    if size <= 0 or size > MAX_MANIFEST_BYTES:\n        raise EvidenceError(\n            f"acceptance manifest must be between 1 and {MAX_MANIFEST_BYTES} bytes"\n        )\n    raw = path.read_bytes()\n    if len(raw) != size:\n        raise EvidenceError(f"acceptance manifest changed while being read: {path}")\n    try:\n        data = json.loads(raw)\n    except (UnicodeDecodeError, json.JSONDecodeError) as exc:\n        raise EvidenceError(f"invalid JSON: {exc}") from exc\n    return validate_bundle(data, source_bytes=raw, evidence_base=path.parent)\n''',
        '''def load_and_validate(path: Path) -> ValidationSummary:\n    try:\n        before = path.lstat()\n    except FileNotFoundError as exc:\n        raise EvidenceError(f"acceptance manifest is missing: {path}") from exc\n    except OSError as exc:\n        raise EvidenceError(f"could not inspect acceptance manifest {path}: {exc}") from exc\n    if stat.S_ISLNK(before.st_mode):\n        raise EvidenceError("acceptance manifest must not be a symbolic link")\n    if not stat.S_ISREG(before.st_mode):\n        raise EvidenceError("acceptance manifest must be a regular file")\n    if before.st_size <= 0 or before.st_size > MAX_MANIFEST_BYTES:\n        raise EvidenceError(\n            f"acceptance manifest must be between 1 and {MAX_MANIFEST_BYTES} bytes"\n        )\n    try:\n        raw = path.read_bytes()\n        after = path.lstat()\n    except OSError as exc:\n        raise EvidenceError(f"could not read acceptance manifest {path}: {exc}") from exc\n    if (\n        len(raw) != before.st_size\n        or after.st_size != before.st_size\n        or after.st_mtime_ns != before.st_mtime_ns\n        or (before.st_ino and after.st_ino != before.st_ino)\n    ):\n        raise EvidenceError(f"acceptance manifest changed while being read: {path}")\n    try:\n        data = strict_json.loads(\n            raw,\n            label=str(path),\n            maximum_bytes=MAX_MANIFEST_BYTES,\n            require_object=True,\n        )\n    except strict_json.StrictJsonError as exc:\n        raise EvidenceError(f"invalid JSON: {exc}") from exc\n    return validate_bundle(data, source_bytes=raw, evidence_base=path.parent)\n''',
        "strict stable manifest loader",
    )

    replace_once(
        TESTS,
        '''\n\nif __name__ == "__main__":\n    unittest.main()\n''',
        '''\n\n    def test_load_rejects_duplicate_json_keys(self) -> None:\n        with tempfile.TemporaryDirectory() as temporary:\n            root = Path(temporary)\n            path = root / "device-acceptance.json"\n            path.write_text('{"schema_version":1,"schema_version":1}\\n', encoding="utf-8")\n            with self.assertRaises(MODULE.EvidenceError) as raised:\n                MODULE.load_and_validate(path)\n            self.assertIn("duplicate JSON object key", str(raised.exception))\n\n    def test_manifest_symlink_is_rejected(self) -> None:\n        with tempfile.TemporaryDirectory() as temporary:\n            root = Path(temporary)\n            bundle = valid_bundle()\n            materialize_files(root, bundle)\n            real = root / "real.json"\n            real.write_text(json.dumps(bundle) + "\\n", encoding="utf-8")\n            alias = root / "alias.json"\n            try:\n                alias.symlink_to(real)\n            except OSError as exc:\n                self.skipTest(f"symbolic links unavailable: {exc}")\n            with self.assertRaises(MODULE.EvidenceError) as raised:\n                MODULE.load_and_validate(alias)\n            self.assertIn("must not be a symbolic link", str(raised.exception))\n\n    def test_artifact_symlink_component_is_rejected(self) -> None:\n        with tempfile.TemporaryDirectory() as temporary:\n            root = Path(temporary)\n            bundle = valid_bundle()\n            materialize_files(root, bundle)\n            real_dir = root / "artifact"\n            alias_dir = root / "artifact-alias"\n            try:\n                alias_dir.symlink_to(real_dir, target_is_directory=True)\n            except OSError as exc:\n                self.skipTest(f"symbolic links unavailable: {exc}")\n            bundle["candidate"]["artifact_path"] = "artifact-alias/app-release.aab"\n            raw = json.dumps(bundle, sort_keys=True).encode()\n            with self.assertRaises(MODULE.EvidenceError) as raised:\n                MODULE.validate_bundle(bundle, source_bytes=raw, evidence_base=root)\n            self.assertIn("must not traverse a symbolic link", str(raised.exception))\n\n    def test_scenario_evidence_symlink_component_is_rejected(self) -> None:\n        with tempfile.TemporaryDirectory() as temporary:\n            root = Path(temporary)\n            bundle = valid_bundle()\n            materialize_files(root, bundle)\n            first_session = bundle["sessions"][0]\n            first_scenario = next(iter(first_session["scenarios"].values()))\n            original = first_scenario["evidence_files"][0]["path"]\n            real_parent = (root / original).parent\n            alias_parent = root / "evidence-alias"\n            try:\n                alias_parent.symlink_to(real_parent, target_is_directory=True)\n            except OSError as exc:\n                self.skipTest(f"symbolic links unavailable: {exc}")\n            first_scenario["evidence_files"][0]["path"] = f"evidence-alias/{Path(original).name}"\n            raw = json.dumps(bundle, sort_keys=True).encode()\n            with self.assertRaises(MODULE.EvidenceError) as raised:\n                MODULE.validate_bundle(bundle, source_bytes=raw, evidence_base=root)\n            self.assertIn("must not traverse a symbolic link", str(raised.exception))\n\n\nif __name__ == "__main__":\n    unittest.main()\n''',
        "device acceptance adversarial tests",
    )

    source = VALIDATOR.read_text(encoding="utf-8")
    for token in (
        "strict_json.loads(",
        "must not traverse a symbolic link",
        "acceptance manifest must not be a symbolic link",
        "path.lstat()",
    ):
        if token not in source:
            raise SystemExit(f"validator hardening missing token: {token}")

    for path in (WORKFLOW, SELF):
        if path.exists():
            path.unlink()


if __name__ == "__main__":
    main()
