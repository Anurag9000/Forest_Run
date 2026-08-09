#!/usr/bin/env python3
"""One-shot exact migration separating Play upload and app-signing identities."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github/workflows/play-signing-identity-migration.yml"
SELF = Path(__file__)


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor in {path}, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def replace_all(path: Path, old: str, new: str, expected: int, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != expected:
        raise SystemExit(f"{label}: expected {expected} anchors in {path}, found {count}")
    path.write_text(text.replace(old, new), encoding="utf-8")


def migrate_device_validator() -> None:
    path = ROOT / "scripts/validate_device_acceptance.py"
    replace_once(path, "SCHEMA_VERSION = 1\n", "SCHEMA_VERSION = 2\n", "device schema")
    replace_once(
        path,
        '''class ValidationSummary:\n    candidate_sha: str\n    artifact_sha256: str\n    session_count: int\n''',
        '''class ValidationSummary:\n    candidate_sha: str\n    artifact_sha256: str\n    upload_certificate_sha256: str\n    app_signing_certificate_sha256: str\n    session_count: int\n''',
        "device summary fields",
    )
    replace_once(
        path,
        '''            "candidate_sha": self.candidate_sha,\n            "artifact_sha256": self.artifact_sha256,\n            "session_count": self.session_count,\n''',
        '''            "candidate_sha": self.candidate_sha,\n            "artifact_sha256": self.artifact_sha256,\n            "upload_certificate_sha256": self.upload_certificate_sha256,\n            "app_signing_certificate_sha256": self.app_signing_certificate_sha256,\n            "session_count": self.session_count,\n''',
        "device summary json",
    )
    replace_once(
        path,
        '''def _validate_candidate(candidate: Mapping[str, Any]) -> tuple[str, str, str, str, int]:\n    repository = _string(candidate.get("repository"), "candidate.repository")\n''',
        '''def _validate_candidate(candidate: Mapping[str, Any]) -> tuple[str, str, str, str, str, int]:\n    _require_exact_keys(\n        candidate,\n        {\n            "repository", "branch", "application_id", "commit_sha", "version_code",\n            "artifact_sha256", "artifact_path", "signed",\n            "upload_certificate_sha256", "store_delivery",\n        },\n        "candidate",\n    )\n    repository = _string(candidate.get("repository"), "candidate.repository")\n''',
        "device candidate exact keys",
    )
    replace_once(
        path,
        '''    certificate_sha = _string(\n        candidate.get("certificate_sha256"),\n        "candidate.certificate_sha256",\n    ).lower()\n    if not SHA256_RE.fullmatch(certificate_sha):\n        raise EvidenceError(\n            "candidate.certificate_sha256 must be a lowercase 64-hex digest"\n        )\n\n    store = _mapping(candidate.get("store_delivery"), "candidate.store_delivery")\n''',
        '''    upload_certificate_sha = _string(\n        candidate.get("upload_certificate_sha256"),\n        "candidate.upload_certificate_sha256",\n    ).lower()\n    if not SHA256_RE.fullmatch(upload_certificate_sha):\n        raise EvidenceError(\n            "candidate.upload_certificate_sha256 must be a lowercase 64-hex digest"\n        )\n\n    store = _mapping(candidate.get("store_delivery"), "candidate.store_delivery")\n    _require_exact_keys(\n        store,\n        {\n            "track", "installed", "package_name", "version_code",\n            "artifact_sha256", "app_signing_certificate_sha256",\n        },\n        "candidate.store_delivery",\n    )\n''',
        "device upload cert and store keys",
    )
    replace_once(
        path,
        '''    if (\n        _string(\n            store.get("certificate_sha256"),\n            "candidate.store_delivery.certificate_sha256",\n        ).lower()\n        != certificate_sha\n    ):\n        raise EvidenceError(\n            "candidate.store_delivery.certificate_sha256 does not match candidate"\n        )\n    return sha, artifact_sha, artifact_path, certificate_sha, version_code\n''',
        '''    app_signing_certificate_sha = _string(\n        store.get("app_signing_certificate_sha256"),\n        "candidate.store_delivery.app_signing_certificate_sha256",\n    ).lower()\n    if not SHA256_RE.fullmatch(app_signing_certificate_sha):\n        raise EvidenceError(\n            "candidate.store_delivery.app_signing_certificate_sha256 must be a lowercase 64-hex digest"\n        )\n    return (\n        sha, artifact_sha, artifact_path, upload_certificate_sha,\n        app_signing_certificate_sha, version_code,\n    )\n''',
        "device app cert",
    )
    replace_once(
        path,
        '''    artifact_sha: str,\n    certificate_sha: str,\n    version_code: int,\n''',
        '''    artifact_sha: str,\n    app_signing_certificate_sha: str,\n    version_code: int,\n''',
        "session cert parameter",
    )
    replace_once(
        path,
        '''    build = _mapping(session.get("build"), f"{label}.build")\n    if (\n''',
        '''    build = _mapping(session.get("build"), f"{label}.build")\n    _require_exact_keys(\n        build,\n        {\n            "commit_sha", "artifact_sha256", "app_signing_certificate_sha256",\n            "version_code", "signed", "installed_via",\n        },\n        f"{label}.build",\n    )\n    if (\n''',
        "session build exact keys",
    )
    replace_once(
        path,
        '''            build.get("certificate_sha256"),\n            f"{label}.build.certificate_sha256",\n        ).lower()\n        != certificate_sha\n    ):\n        raise EvidenceError(\n            f"{label}.build.certificate_sha256 does not match candidate"\n        )\n''',
        '''            build.get("app_signing_certificate_sha256"),\n            f"{label}.build.app_signing_certificate_sha256",\n        ).lower()\n        != app_signing_certificate_sha\n    ):\n        raise EvidenceError(\n            f"{label}.build.app_signing_certificate_sha256 does not match store delivery"\n        )\n''',
        "session app cert comparison",
    )
    replace_once(
        path,
        '''        artifact_path,\n        certificate_sha,\n        version_code,\n    ) = _validate_candidate(candidate)\n''',
        '''        artifact_path,\n        upload_certificate_sha,\n        app_signing_certificate_sha,\n        version_code,\n    ) = _validate_candidate(candidate)\n''',
        "device candidate unpack",
    )
    replace_once(
        path,
        '''            artifact_sha=artifact_sha,\n            certificate_sha=certificate_sha,\n            version_code=version_code,\n''',
        '''            artifact_sha=artifact_sha,\n            app_signing_certificate_sha=app_signing_certificate_sha,\n            version_code=version_code,\n''',
        "session app cert call",
    )
    replace_once(
        path,
        '''        candidate_sha=candidate_sha,\n        artifact_sha256=artifact_sha,\n        session_count=len(sessions),\n''',
        '''        candidate_sha=candidate_sha,\n        artifact_sha256=artifact_sha,\n        upload_certificate_sha256=upload_certificate_sha,\n        app_signing_certificate_sha256=app_signing_certificate_sha,\n        session_count=len(sessions),\n''',
        "device summary construction",
    )


def migrate_device_compiler() -> None:
    path = ROOT / "scripts/compile_device_acceptance.py"
    replace_once(
        path,
        "import validate_device_acceptance as acceptance\n",
        "import strict_json\nimport validate_device_acceptance as acceptance\n",
        "device compiler strict import",
    )
    replace_once(
        path,
        '''    try:\n        value = json.loads(raw)\n    except (UnicodeDecodeError, json.JSONDecodeError) as exc:\n        raise CompilationError(f"invalid JSON in {path}: {exc}") from exc\n    if not isinstance(value, dict):\n        raise CompilationError(f"{path} must contain a JSON object")\n    return value\n''',
        '''    try:\n        value = strict_json.loads(\n            raw,\n            label=str(path),\n            maximum_bytes=acceptance.MAX_MANIFEST_BYTES,\n            require_object=True,\n        )\n    except strict_json.StrictJsonError as exc:\n        raise CompilationError(f"invalid JSON in {path}: {exc}") from exc\n    return dict(value)\n''',
        "device compiler strict draft",
    )
    replace_once(
        path,
        '''    canonical_base = base.resolve()\n    resolved = (canonical_base / relative).resolve()\n    try:\n        resolved.relative_to(canonical_base)\n    except ValueError as exc:\n        raise CompilationError(f"{label} escapes the draft directory") from exc\n    return relative, resolved\n''',
        '''    canonical_base = base.resolve()\n    try:\n        resolved = acceptance._resolve_evidence_file(canonical_base, relative, label)\n    except acceptance.EvidenceError as exc:\n        raise CompilationError(str(exc)) from exc\n    return relative, resolved\n''',
        "device compiler safe resolution",
    )


def migrate_device_fixture() -> None:
    path = ROOT / "scripts/test_validate_device_acceptance.py"
    replace_once(
        path,
        '''CERT_SHA = "3" * 64\nSHA = "1" * 40\n''',
        '''UPLOAD_CERT_SHA = "3" * 64\nAPP_SIGNING_CERT_SHA = "4" * 64\nCERT_SHA = UPLOAD_CERT_SHA  # compatibility alias for tests importing this fixture\nSHA = "1" * 40\n''',
        "device cert fixtures",
    )
    replace_once(
        path,
        '''            "certificate_sha256": CERT_SHA,\n            "version_code": 7,\n''',
        '''            "app_signing_certificate_sha256": APP_SIGNING_CERT_SHA,\n            "version_code": 7,\n''',
        "session cert fixture",
    )
    replace_once(
        path,
        '''        "schema_version": 1,\n''',
        '''        "schema_version": MODULE.SCHEMA_VERSION,\n''',
        "device fixture schema",
    )
    replace_once(
        path,
        '''            "certificate_sha256": CERT_SHA,\n            "store_delivery": {\n''',
        '''            "upload_certificate_sha256": UPLOAD_CERT_SHA,\n            "store_delivery": {\n''',
        "candidate upload cert fixture",
    )
    replace_once(
        path,
        '''                "certificate_sha256": CERT_SHA,\n''',
        '''                "app_signing_certificate_sha256": APP_SIGNING_CERT_SHA,\n''',
        "store app cert fixture",
    )
    replace_once(
        path,
        '''\n\nif __name__ == "__main__":\n    unittest.main()\n''',
        '''\n\n    def test_upload_and_app_signing_certificates_are_distinct_identities(self) -> None:\n        with tempfile.TemporaryDirectory() as temporary:\n            root = Path(temporary)\n            bundle = valid_bundle()\n            materialize_files(root, bundle)\n            raw = json.dumps(bundle, sort_keys=True).encode()\n            summary = MODULE.validate_bundle(bundle, source_bytes=raw, evidence_base=root)\n            self.assertEqual(UPLOAD_CERT_SHA, summary.upload_certificate_sha256)\n            self.assertEqual(APP_SIGNING_CERT_SHA, summary.app_signing_certificate_sha256)\n            self.assertNotEqual(summary.upload_certificate_sha256, summary.app_signing_certificate_sha256)\n\n    def test_session_must_match_delivered_app_signing_certificate(self) -> None:\n        with tempfile.TemporaryDirectory() as temporary:\n            root = Path(temporary)\n            bundle = valid_bundle()\n            materialize_files(root, bundle)\n            bundle["sessions"][0]["build"]["app_signing_certificate_sha256"] = "5" * 64\n            with self.assertRaises(MODULE.EvidenceError) as raised:\n                MODULE.validate_bundle(bundle, source_bytes=json.dumps(bundle).encode(), evidence_base=root)\n            self.assertIn("does not match store delivery", str(raised.exception))\n\n    def test_legacy_ambiguous_certificate_fields_are_rejected(self) -> None:\n        with tempfile.TemporaryDirectory() as temporary:\n            root = Path(temporary)\n            bundle = valid_bundle()\n            materialize_files(root, bundle)\n            bundle["candidate"]["certificate_sha256"] = bundle["candidate"].pop("upload_certificate_sha256")\n            with self.assertRaises(MODULE.EvidenceError) as raised:\n                MODULE.validate_bundle(bundle, source_bytes=json.dumps(bundle).encode(), evidence_base=root)\n            self.assertIn("candidate is missing", str(raised.exception))\n\n\nif __name__ == "__main__":\n    unittest.main()\n''',
        "device signing tests",
    )


def migrate_human() -> None:
    validator = ROOT / "scripts/validate_human_acceptance.py"
    replace_once(validator, "SCHEMA_VERSION = 1\n", "SCHEMA_VERSION = 2\n", "human schema")
    replace_once(
        validator,
        '''class HumanAcceptanceSummary:\n    candidate_sha: str\n    artifact_sha256: str\n    device_acceptance_sha256: str\n''',
        '''class HumanAcceptanceSummary:\n    candidate_sha: str\n    artifact_sha256: str\n    upload_certificate_sha256: str\n    app_signing_certificate_sha256: str\n    device_acceptance_sha256: str\n''',
        "human summary certs",
    )
    replace_once(
        validator,
        '''            "artifact_sha256": self.artifact_sha256,\n            "device_acceptance_sha256": self.device_acceptance_sha256,\n''',
        '''            "artifact_sha256": self.artifact_sha256,\n            "upload_certificate_sha256": self.upload_certificate_sha256,\n            "app_signing_certificate_sha256": self.app_signing_certificate_sha256,\n            "device_acceptance_sha256": self.device_acceptance_sha256,\n''',
        "human summary json",
    )
    replace_once(
        validator,
        '''def _validate_candidate(candidate: Mapping[str, Any]) -> tuple[str, str, str, int]:\n''',
        '''def _validate_candidate(candidate: Mapping[str, Any]) -> tuple[str, str, str, str, int]:\n''',
        "human tuple",
    )
    replace_once(
        validator,
        '''            "artifact_sha256",\n            "certificate_sha256",\n''',
        '''            "artifact_sha256",\n            "upload_certificate_sha256",\n            "app_signing_certificate_sha256",\n''',
        "human candidate keys",
    )
    replace_once(
        validator,
        '''    certificate = _string(candidate["certificate_sha256"], "candidate.certificate_sha256", maximum=64).lower()\n    version_code = _integer(candidate["version_code"], "candidate.version_code", minimum=1)\n''',
        '''    upload_certificate = _string(\n        candidate["upload_certificate_sha256"], "candidate.upload_certificate_sha256", maximum=64\n    ).lower()\n    app_signing_certificate = _string(\n        candidate["app_signing_certificate_sha256"], "candidate.app_signing_certificate_sha256", maximum=64\n    ).lower()\n    version_code = _integer(candidate["version_code"], "candidate.version_code", minimum=1)\n''',
        "human candidate cert parse",
    )
    replace_once(
        validator,
        '''    if not SHA256_RE.fullmatch(certificate):\n        raise HumanAcceptanceError("candidate.certificate_sha256 must be lowercase 64-hex")\n    return sha, artifact, certificate, version_code\n''',
        '''    if not SHA256_RE.fullmatch(upload_certificate):\n        raise HumanAcceptanceError("candidate.upload_certificate_sha256 must be lowercase 64-hex")\n    if not SHA256_RE.fullmatch(app_signing_certificate):\n        raise HumanAcceptanceError("candidate.app_signing_certificate_sha256 must be lowercase 64-hex")\n    return sha, artifact, upload_certificate, app_signing_certificate, version_code\n''',
        "human candidate cert validation",
    )
    replace_once(
        validator,
        '''    certificate_sha: str,\n    version_code: int,\n''',
        '''    upload_certificate_sha: str,\n    app_signing_certificate_sha: str,\n    version_code: int,\n''',
        "human device load cert params",
    )
    replace_once(
        validator,
        '''    if _string(candidate.get("certificate_sha256"), "device candidate.certificate_sha256").lower() != certificate_sha:\n        raise HumanAcceptanceError("device acceptance certificate does not match human candidate")\n''',
        '''    if summary.upload_certificate_sha256 != upload_certificate_sha:\n        raise HumanAcceptanceError("device acceptance upload certificate does not match human candidate")\n    if summary.app_signing_certificate_sha256 != app_signing_certificate_sha:\n        raise HumanAcceptanceError("device acceptance app-signing certificate does not match human candidate")\n''',
        "human device cert compare",
    )
    replace_once(
        validator,
        '''    candidate_sha, artifact_sha, certificate_sha, version_code = _validate_candidate(\n''',
        '''    (\n        candidate_sha, artifact_sha, upload_certificate_sha,\n        app_signing_certificate_sha, version_code,\n    ) = _validate_candidate(\n''',
        "human candidate unpack",
    )
    replace_once(
        validator,
        '''        artifact_sha=artifact_sha,\n        certificate_sha=certificate_sha,\n        version_code=version_code,\n''',
        '''        artifact_sha=artifact_sha,\n        upload_certificate_sha=upload_certificate_sha,\n        app_signing_certificate_sha=app_signing_certificate_sha,\n        version_code=version_code,\n''',
        "human device load call",
    )
    replace_once(
        validator,
        '''        candidate_sha=candidate_sha,\n        artifact_sha256=artifact_sha,\n        device_acceptance_sha256=device_digest,\n''',
        '''        candidate_sha=candidate_sha,\n        artifact_sha256=artifact_sha,\n        upload_certificate_sha256=upload_certificate_sha,\n        app_signing_certificate_sha256=app_signing_certificate_sha,\n        device_acceptance_sha256=device_digest,\n''',
        "human summary construction",
    )

    fixture = ROOT / "scripts/test_validate_human_acceptance.py"
    replace_once(
        fixture,
        '''            "certificate_sha256": candidate["certificate_sha256"],\n''',
        '''            "upload_certificate_sha256": candidate["upload_certificate_sha256"],\n            "app_signing_certificate_sha256": candidate["store_delivery"]["app_signing_certificate_sha256"],\n''',
        "human fixture certs",
    )


def migrate_governance() -> None:
    validator = ROOT / "scripts/validate_release_governance.py"
    replace_once(validator, "SCHEMA_VERSION = 1\n", "SCHEMA_VERSION = 2\n", "governance schema")
    replace_once(
        validator,
        '''    certificate_sha256: str\n    device_acceptance_sha256: str\n''',
        '''    upload_certificate_sha256: str\n    app_signing_certificate_sha256: str\n    device_acceptance_sha256: str\n''',
        "governance summary cert fields",
    )
    replace_once(
        validator,
        '''            "certificate_sha256": self.certificate_sha256,\n            "device_acceptance_sha256": self.device_acceptance_sha256,\n''',
        '''            "upload_certificate_sha256": self.upload_certificate_sha256,\n            "app_signing_certificate_sha256": self.app_signing_certificate_sha256,\n            "device_acceptance_sha256": self.device_acceptance_sha256,\n''',
        "governance summary json",
    )
    replace_once(
        validator,
        '''def _validate_candidate(candidate: Mapping[str, Any]) -> tuple[str, str, str, int, str]:\n''',
        '''def _validate_candidate(candidate: Mapping[str, Any]) -> tuple[str, str, str, str, int, str]:\n''',
        "governance tuple",
    )
    replace_once(
        validator,
        '''            "artifact_sha256",\n            "certificate_sha256",\n''',
        '''            "artifact_sha256",\n            "upload_certificate_sha256",\n            "app_signing_certificate_sha256",\n''',
        "governance candidate keys",
    )
    replace_once(
        validator,
        '''    certificate = _string(candidate["certificate_sha256"], "candidate.certificate_sha256", maximum=64).lower()\n    version_code = _integer(candidate["version_code"], "candidate.version_code", minimum=1)\n''',
        '''    upload_certificate = _string(\n        candidate["upload_certificate_sha256"], "candidate.upload_certificate_sha256", maximum=64\n    ).lower()\n    app_signing_certificate = _string(\n        candidate["app_signing_certificate_sha256"], "candidate.app_signing_certificate_sha256", maximum=64\n    ).lower()\n    version_code = _integer(candidate["version_code"], "candidate.version_code", minimum=1)\n''',
        "governance cert parse",
    )
    replace_once(
        validator,
        '''    if not SHA256_RE.fullmatch(certificate):\n        raise GovernanceError("candidate.certificate_sha256 must be lowercase 64-hex")\n    return sha, artifact, certificate, version_code, version_name\n''',
        '''    if not SHA256_RE.fullmatch(upload_certificate):\n        raise GovernanceError("candidate.upload_certificate_sha256 must be lowercase 64-hex")\n    if not SHA256_RE.fullmatch(app_signing_certificate):\n        raise GovernanceError("candidate.app_signing_certificate_sha256 must be lowercase 64-hex")\n    return sha, artifact, upload_certificate, app_signing_certificate, version_code, version_name\n''',
        "governance cert validate",
    )
    replace_all(
        validator,
        '''    certificate_sha: str,\n    version_code: int,\n''',
        '''    upload_certificate_sha: str,\n    app_signing_certificate_sha: str,\n    version_code: int,\n''',
        2,
        "governance reference cert params",
    )
    replace_once(
        validator,
        '''    if _string(device_candidate.get("certificate_sha256"), "device candidate.certificate_sha256").lower() != certificate_sha:\n        raise GovernanceError("device acceptance certificate does not match governance candidate")\n''',
        '''    if summary.upload_certificate_sha256 != upload_certificate_sha:\n        raise GovernanceError("device acceptance upload certificate does not match governance candidate")\n    if summary.app_signing_certificate_sha256 != app_signing_certificate_sha:\n        raise GovernanceError("device acceptance app-signing certificate does not match governance candidate")\n''',
        "governance device cert compare",
    )
    replace_once(
        validator,
        '''    if _string(human_candidate.get("certificate_sha256"), "human candidate.certificate_sha256").lower() != certificate_sha:\n        raise GovernanceError("human acceptance certificate does not match governance candidate")\n''',
        '''    if summary.upload_certificate_sha256 != upload_certificate_sha:\n        raise GovernanceError("human acceptance upload certificate does not match governance candidate")\n    if summary.app_signing_certificate_sha256 != app_signing_certificate_sha:\n        raise GovernanceError("human acceptance app-signing certificate does not match governance candidate")\n''',
        "governance human cert compare",
    )
    replace_once(
        validator,
        '''    candidate_sha, artifact_sha, certificate_sha, version_code, version_name = _validate_candidate(\n''',
        '''    (\n        candidate_sha, artifact_sha, upload_certificate_sha,\n        app_signing_certificate_sha, version_code, version_name,\n    ) = _validate_candidate(\n''',
        "governance candidate unpack",
    )
    replace_all(
        validator,
        '''        artifact_sha=artifact_sha,\n        certificate_sha=certificate_sha,\n        version_code=version_code,\n''',
        '''        artifact_sha=artifact_sha,\n        upload_certificate_sha=upload_certificate_sha,\n        app_signing_certificate_sha=app_signing_certificate_sha,\n        version_code=version_code,\n''',
        2,
        "governance reference calls",
    )
    replace_once(
        validator,
        '''        (certificate_sha, "certificate SHA-256"),\n''',
        '''        (upload_certificate_sha, "upload certificate SHA-256"),\n        (app_signing_certificate_sha, "app-signing certificate SHA-256"),\n''',
        "governance release notes certs",
    )
    replace_once(
        validator,
        '''        certificate_sha256=certificate_sha,\n        device_acceptance_sha256=device_digest,\n''',
        '''        upload_certificate_sha256=upload_certificate_sha,\n        app_signing_certificate_sha256=app_signing_certificate_sha,\n        device_acceptance_sha256=device_digest,\n''',
        "governance summary construction",
    )

    fixture = ROOT / "scripts/test_validate_release_governance.py"
    replace_once(
        fixture,
        '''                f"certificate sha256: {candidate['certificate_sha256']}\\n"\n''',
        '''                f"upload certificate sha256: {candidate['upload_certificate_sha256']}\\n"\n                f"app signing certificate sha256: {candidate['app_signing_certificate_sha256']}\\n"\n''',
        "governance release notes fixture",
    )


def migrate_readiness() -> None:
    path = ROOT / "scripts/validate_release_readiness.py"
    replace_once(
        path,
        '''    certificate_sha256: str\n    device_acceptance_sha256: str\n''',
        '''    upload_certificate_sha256: str\n    app_signing_certificate_sha256: str\n    device_acceptance_sha256: str\n''',
        "readiness summary certs",
    )
    replace_once(
        path,
        '''            "certificate_sha256": self.certificate_sha256,\n            "device_acceptance_sha256": self.device_acceptance_sha256,\n''',
        '''            "upload_certificate_sha256": self.upload_certificate_sha256,\n            "app_signing_certificate_sha256": self.app_signing_certificate_sha256,\n            "device_acceptance_sha256": self.device_acceptance_sha256,\n''',
        "readiness summary json",
    )
    replace_once(
        path,
        '''    artifact_values = {\n        device_summary.artifact_sha256,\n        human_summary.artifact_sha256,\n        governance_summary.artifact_sha256,\n    }\n    if len(artifact_values) != 1:\n        raise ReleaseReadinessError(\n            "device, human, and governance artifact SHA-256 values do not match"\n        )\n''',
        '''    artifact_values = {\n        device_summary.artifact_sha256,\n        human_summary.artifact_sha256,\n        governance_summary.artifact_sha256,\n    }\n    if len(artifact_values) != 1:\n        raise ReleaseReadinessError(\n            "device, human, and governance artifact SHA-256 values do not match"\n        )\n    upload_certificates = {\n        device_summary.upload_certificate_sha256,\n        human_summary.upload_certificate_sha256,\n        governance_summary.upload_certificate_sha256,\n    }\n    if len(upload_certificates) != 1:\n        raise ReleaseReadinessError(\n            "device, human, and governance upload-certificate SHA-256 values do not match"\n        )\n    app_signing_certificates = {\n        device_summary.app_signing_certificate_sha256,\n        human_summary.app_signing_certificate_sha256,\n        governance_summary.app_signing_certificate_sha256,\n    }\n    if len(app_signing_certificates) != 1:\n        raise ReleaseReadinessError(\n            "device, human, and governance app-signing-certificate SHA-256 values do not match"\n        )\n''',
        "readiness cert cross binding",
    )
    replace_once(
        path,
        '''        certificate_sha256=governance_summary.certificate_sha256,\n        device_acceptance_sha256=device_digest,\n''',
        '''        upload_certificate_sha256=governance_summary.upload_certificate_sha256,\n        app_signing_certificate_sha256=governance_summary.app_signing_certificate_sha256,\n        device_acceptance_sha256=device_digest,\n''',
        "readiness summary construction",
    )

    tests = ROOT / "scripts/test_validate_release_readiness.py"
    replace_once(
        tests,
        '''            self.assertEqual(device_fixture.CERT_SHA, summary.certificate_sha256)\n''',
        '''            self.assertEqual(device_fixture.UPLOAD_CERT_SHA, summary.upload_certificate_sha256)\n            self.assertEqual(device_fixture.APP_SIGNING_CERT_SHA, summary.app_signing_certificate_sha256)\n''',
        "readiness cert test",
    )


def migrate_docs() -> None:
    device = ROOT / "docs/DEVICE_ACCEPTANCE.md"
    replace_once(
        device,
        '''- signing-certificate SHA-256;\n- successful install from the internal store track;\n- delivered package name, version, artifact digest, and certificate digest matching the candidate.\n\nEvery device session must repeat the same commit, artifact, version, signing, and internal-store installation identity. Mixing local APKs, different commits, or different bundles in one acceptance set is rejected.\n''',
        '''- upload-certificate SHA-256 for the signed AAB submitted to Play;\n- successful install from the internal store track;\n- delivered package name and version plus the **Play app-signing certificate SHA-256** observed on the installed APK;\n- the uploaded AAB digest repeated in the store-delivery record so the delivery receipt remains tied to the exact submitted artifact.\n\nThe upload certificate and Play app-signing certificate are intentionally separate identities and may differ. Under Play App Signing, the upload key authenticates the bundle submitted by the developer while Play signs delivered APKs with the app-signing key. Every device session must repeat the same commit, uploaded artifact, version, delivered app-signing certificate, signing state, and internal-store installation identity. Mixing local APKs, different commits, different bundles, or a locally signed APK certificate into the Play-delivery field is rejected.\n''',
        "device signing semantics",
    )
    replace_once(
        device,
        '''- keep candidate identity, certificate, version, signed status, and internal-track installation facts explicit;\n- record the package, version, artifact digest, and certificate digest captured from the internal-store delivery path;\n- record every session's captured commit, artifact digest, version, certificate, signing state, and installation path;\n''',
        '''- keep candidate identity, **upload certificate**, version, signed status, and internal-track installation facts explicit;\n- record the package, version, uploaded AAB digest, and **app-signing certificate** captured from the internal-store delivery path;\n- record every session's captured commit, uploaded artifact digest, version, delivered app-signing certificate, signing state, and installation path;\n''',
        "device draft signing semantics",
    )

    human = ROOT / "docs/HUMAN_ACCEPTANCE.md"
    replace_once(
        human,
        '''- signing-certificate SHA-256.\n''',
        '''- upload-certificate SHA-256 for the submitted AAB;\n- app-signing-certificate SHA-256 for the Play-delivered APK.\n''',
        "human cert semantics",
    )
    replace_once(
        human,
        '''    "certificate_sha256": "<64-hex>"\n''',
        '''    "upload_certificate_sha256": "<64-hex>",\n    "app_signing_certificate_sha256": "<64-hex>"\n''',
        "human example certs",
    )

    governance = ROOT / "docs/RELEASE_GOVERNANCE_EVIDENCE.md"
    replace_once(
        governance,
        '''- signing-certificate SHA-256;\n''',
        '''- upload-certificate SHA-256 for the submitted AAB;\n- app-signing-certificate SHA-256 for the Play-delivered APK;\n''',
        "governance cert semantics",
    )
    replace_once(
        governance,
        '''- certificate SHA-256;\n- version code;\n''',
        '''- upload-certificate SHA-256;\n- app-signing-certificate SHA-256;\n- version code;\n''',
        "governance notes identities",
    )

    readiness = ROOT / "docs/RELEASE_READINESS.md"
    replace_once(
        readiness,
        '''- physical artifact SHA-256 = human artifact SHA-256 = governance artifact SHA-256;\n''',
        '''- physical artifact SHA-256 = human artifact SHA-256 = governance artifact SHA-256;\n- physical upload-certificate SHA-256 = human upload-certificate SHA-256 = governance upload-certificate SHA-256;\n- physical app-signing-certificate SHA-256 = human app-signing-certificate SHA-256 = governance app-signing-certificate SHA-256;\n''',
        "readiness cert invariants",
    )


def main() -> None:
    migrate_device_validator()
    migrate_device_compiler()
    migrate_device_fixture()
    migrate_human()
    migrate_governance()
    migrate_readiness()
    migrate_docs()

    for path in (WORKFLOW, SELF):
        if path.exists():
            path.unlink()


if __name__ == "__main__":
    main()
