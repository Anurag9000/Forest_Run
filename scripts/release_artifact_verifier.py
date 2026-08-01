#!/usr/bin/env python3
"""Artifact-level verification helpers for Forest Run Android releases."""

from __future__ import annotations

import os
import re
import shutil
import subprocess
import zipfile
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Mapping, Sequence

MAX_BUNDLE_ENTRIES = 100_000
MAX_ENTRY_UNCOMPRESSED_BYTES = 1 * 1024 * 1024 * 1024
MAX_TOTAL_UNCOMPRESSED_BYTES = 4 * 1024 * 1024 * 1024


class ArtifactVerificationError(RuntimeError):
    """Raised when a built Android release artifact is inconsistent or unverifiable."""


@dataclass(frozen=True)
class CommandResult:
    returncode: int
    stdout: str
    stderr: str


@dataclass(frozen=True)
class BundleIdentity:
    application_id: str
    version_code: int
    version_name: str


@dataclass(frozen=True)
class BundleSignature:
    verified: bool
    signer_sha256: str | None
    expected_sha256: str | None


CommandRunner = Callable[[Sequence[str], Mapping[str, str] | None], CommandResult]


def default_runner(
    command: Sequence[str],
    environment: Mapping[str, str] | None = None,
) -> CommandResult:
    completed = subprocess.run(
        list(command),
        text=True,
        capture_output=True,
        check=False,
        env=dict(environment) if environment is not None else None,
    )
    return CommandResult(completed.returncode, completed.stdout, completed.stderr)


def _platform_tool_names(name: str) -> tuple[str, ...]:
    return (name, f"{name}.bat", f"{name}.cmd", f"{name}.exe")


def resolve_tool(
    name: str,
    environment: Mapping[str, str] | None = None,
    which: Callable[[str], str | None] = shutil.which,
) -> str:
    environment = environment or os.environ
    for candidate_name in _platform_tool_names(name):
        resolved = which(candidate_name)
        if resolved:
            return resolved

    roots = [
        environment.get("ANDROID_HOME"),
        environment.get("ANDROID_SDK_ROOT"),
        environment.get("JAVA_HOME"),
    ]
    candidates: list[Path] = []
    for raw_root in roots:
        if not raw_root:
            continue
        root = Path(raw_root).expanduser()
        for candidate_name in _platform_tool_names(name):
            candidates.extend(
                [
                    root / "bin" / candidate_name,
                    root / "tools" / "bin" / candidate_name,
                    root / "cmdline-tools" / "latest" / "bin" / candidate_name,
                ]
            )
            cmdline_root = root / "cmdline-tools"
            if cmdline_root.is_dir():
                candidates.extend(
                    sorted(
                        cmdline_root.glob(f"*/bin/{candidate_name}"),
                        reverse=True,
                    )
                )

    for candidate in candidates:
        if candidate.is_file():
            return str(candidate)
    raise ArtifactVerificationError(
        f"Required tool '{name}' was not found on PATH, JAVA_HOME, ANDROID_HOME, or ANDROID_SDK_ROOT"
    )


def _run_checked(
    command: Sequence[str],
    runner: CommandRunner,
    environment: Mapping[str, str] | None = None,
    label: str = "command",
) -> str:
    result = runner(command, environment)
    if result.returncode != 0:
        details = (result.stderr or result.stdout).strip()
        raise ArtifactVerificationError(
            f"{label} failed with exit code {result.returncode}: {details or '(no output)'}"
        )
    return (result.stdout or result.stderr).strip()


def _last_non_blank_line(output: str, label: str) -> str:
    lines = [line.strip() for line in output.splitlines() if line.strip()]
    if not lines:
        raise ArtifactVerificationError(f"{label} produced no value")
    return lines[-1]


def _validate_zip_entry_name(name: str) -> None:
    if not name:
        raise ArtifactVerificationError("Release bundle contains an empty ZIP entry name")
    if name.startswith("/") or name.startswith("./") or "\\" in name:
        raise ArtifactVerificationError(
            f"Release bundle contains an unsafe ZIP entry name: {name!r}"
        )
    normalized = name[:-1] if name.endswith("/") else name
    parts = normalized.split("/")
    if not normalized or any(part in {"", ".", ".."} for part in parts):
        raise ArtifactVerificationError(
            f"Release bundle contains an unsafe ZIP entry name: {name!r}"
        )


def verify_bundle_structure(bundle: Path) -> dict[str, object]:
    if not bundle.is_file() or bundle.stat().st_size <= 0:
        raise ArtifactVerificationError(f"Release bundle is missing or empty: {bundle}")
    try:
        with zipfile.ZipFile(bundle) as archive:
            infos = archive.infolist()
            if len(infos) > MAX_BUNDLE_ENTRIES:
                raise ArtifactVerificationError(
                    f"Release bundle contains too many entries: {len(infos)}"
                )

            names = [info.filename for info in infos]
            duplicate_counts = Counter(names)
            duplicates = sorted(
                name for name, count in duplicate_counts.items() if count > 1
            )
            if duplicates:
                raise ArtifactVerificationError(
                    "Release bundle contains duplicate ZIP entries: "
                    + ", ".join(duplicates[:10])
                )

            total_uncompressed_bytes = 0
            info_by_name: dict[str, zipfile.ZipInfo] = {}
            for info in infos:
                _validate_zip_entry_name(info.filename)
                if info.flag_bits & 0x1:
                    raise ArtifactVerificationError(
                        f"Release bundle contains an encrypted ZIP entry: {info.filename}"
                    )
                if info.file_size < 0 or info.file_size > MAX_ENTRY_UNCOMPRESSED_BYTES:
                    raise ArtifactVerificationError(
                        "Release bundle entry has an invalid or excessive expanded size: "
                        f"{info.filename} ({info.file_size} bytes)"
                    )
                total_uncompressed_bytes += info.file_size
                if total_uncompressed_bytes > MAX_TOTAL_UNCOMPRESSED_BYTES:
                    raise ArtifactVerificationError(
                        "Release bundle exceeds the expanded-size safety limit: "
                        f"{total_uncompressed_bytes} bytes"
                    )
                info_by_name[info.filename] = info

            bad_entry = archive.testzip()
    except ArtifactVerificationError:
        raise
    except (OSError, zipfile.BadZipFile, RuntimeError) as exc:
        raise ArtifactVerificationError(
            f"Release bundle is not a valid ZIP archive: {exc}"
        ) from exc
    if bad_entry is not None:
        raise ArtifactVerificationError(
            f"Release bundle contains a corrupt entry: {bad_entry}"
        )

    required_entries = {
        "BundleConfig.pb",
        "base/manifest/AndroidManifest.xml",
    }
    missing = sorted(required_entries.difference(info_by_name))
    if missing:
        raise ArtifactVerificationError(
            "Release bundle is missing required entries: " + ", ".join(missing)
        )
    empty_required = sorted(
        name for name in required_entries if info_by_name[name].file_size <= 0
    )
    if empty_required:
        raise ArtifactVerificationError(
            "Release bundle contains empty required entries: "
            + ", ".join(empty_required)
        )

    dex_entries = sorted(
        name
        for name, info in info_by_name.items()
        if name.startswith("base/dex/")
        and name.endswith(".dex")
        and not name.endswith("/")
        and info.file_size > 0
    )
    if not dex_entries:
        raise ArtifactVerificationError(
            "Release bundle contains no non-empty base-module DEX files"
        )
    return {
        "entries": len(infos),
        "dex_files": dex_entries,
        "total_uncompressed_bytes": total_uncompressed_bytes,
    }


def inspect_bundle_identity(
    bundle: Path,
    expected_application_id: str,
    expected_version_code: int,
    expected_version_name: str,
    *,
    environment: Mapping[str, str] | None = None,
    runner: CommandRunner = default_runner,
    apkanalyzer: str | None = None,
) -> BundleIdentity:
    analyzer = apkanalyzer or resolve_tool("apkanalyzer", environment)

    def query(subcommand: str) -> str:
        output = _run_checked(
            [analyzer, "manifest", subcommand, str(bundle)],
            runner,
            environment,
            label=f"apkanalyzer manifest {subcommand}",
        )
        return _last_non_blank_line(output, f"apkanalyzer {subcommand}")

    application_id = query("application-id")
    version_code_text = query("version-code")
    version_name = query("version-name")
    try:
        version_code = int(version_code_text)
    except ValueError as exc:
        raise ArtifactVerificationError(
            f"apkanalyzer returned a non-integer version code: {version_code_text!r}"
        ) from exc

    actual = BundleIdentity(application_id, version_code, version_name)
    expected = BundleIdentity(
        expected_application_id,
        expected_version_code,
        expected_version_name,
    )
    if actual != expected:
        raise ArtifactVerificationError(
            "Built bundle identity does not match Gradle configuration: "
            f"expected {expected}, found {actual}"
        )
    return actual


def _extract_sha256_fingerprint(output: str, label: str) -> str:
    match = re.search(
        r"SHA-?256\s*:\s*((?:[0-9A-Fa-f]{2}(?::|\s)*){32})",
        output,
        flags=re.IGNORECASE,
    )
    if not match:
        raise ArtifactVerificationError(
            f"Could not find a SHA-256 fingerprint in {label} output"
        )
    fingerprint = "".join(
        re.findall(r"[0-9A-Fa-f]{2}", match.group(1))
    ).upper()
    if len(fingerprint) != 64:
        raise ArtifactVerificationError(
            f"Malformed SHA-256 fingerprint in {label} output"
        )
    return fingerprint


def verify_bundle_signature(
    bundle: Path,
    *,
    keystore: Path | None,
    alias: str | None,
    store_password: str | None,
    allow_unsigned: bool,
    environment: Mapping[str, str] | None = None,
    runner: CommandRunner = default_runner,
    jarsigner: str | None = None,
    keytool: str | None = None,
) -> BundleSignature:
    if allow_unsigned and (keystore is None or not alias or store_password is None):
        return BundleSignature(False, None, None)
    if keystore is None or not keystore.is_file():
        raise ArtifactVerificationError(
            f"Configured release keystore does not exist: {keystore}"
        )
    if not alias:
        raise ArtifactVerificationError("Release key alias is missing")
    if store_password is None:
        raise ArtifactVerificationError("Release keystore password is missing")

    active_environment = dict(environment or os.environ)
    password_variable = "FOREST_RUN_RELEASE_VERIFY_STORE_PASSWORD"
    active_environment[password_variable] = store_password
    jarsigner_path = jarsigner or resolve_tool("jarsigner", active_environment)
    keytool_path = keytool or resolve_tool("keytool", active_environment)

    verification_output = _run_checked(
        [jarsigner_path, "-verify", "-verbose", "-certs", str(bundle)],
        runner,
        active_environment,
        label="jarsigner verification",
    )
    normalized_verification = verification_output.casefold()
    if (
        "jar verified" not in normalized_verification
        or "jar is unsigned" in normalized_verification
    ):
        raise ArtifactVerificationError(
            "Release bundle is not verifiably JAR-signed"
        )

    signer_output = _run_checked(
        [keytool_path, "-printcert", "-jarfile", str(bundle)],
        runner,
        active_environment,
        label="bundle signer certificate inspection",
    )
    expected_output = _run_checked(
        [
            keytool_path,
            "-list",
            "-v",
            "-keystore",
            str(keystore),
            "-alias",
            alias,
            "-storepass:env",
            password_variable,
        ],
        runner,
        active_environment,
        label="configured keystore certificate inspection",
    )
    signer_sha256 = _extract_sha256_fingerprint(
        signer_output, "bundle signer"
    )
    expected_sha256 = _extract_sha256_fingerprint(
        expected_output, "configured keystore"
    )
    if signer_sha256 != expected_sha256:
        raise ArtifactVerificationError(
            "Release bundle signer does not match the configured upload key: "
            f"bundle={signer_sha256}, configured={expected_sha256}"
        )
    return BundleSignature(True, signer_sha256, expected_sha256)
