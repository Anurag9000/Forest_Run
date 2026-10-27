#!/usr/bin/env python3
"""Artifact-level verification helpers for Forest Run Android releases."""

from __future__ import annotations

import os
import re
import shutil
import stat
import subprocess
import zipfile
from collections import Counter
from contextlib import contextmanager
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
    which: Callable[[str], str | None] | None = None,
) -> str:
    which_tool = shutil.which if which is None else which
    environment = os.environ if environment is None else environment
    search_path = None if environment is os.environ else environment.get("PATH", "")
    for candidate_name in _platform_tool_names(name):
        if which is None:
            resolved = which_tool(candidate_name, path=search_path)
        else:
            resolved = which_tool(candidate_name)
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
                tools = list(cmdline_root.glob(f"*/bin/{candidate_name}"))
                numeric: list[tuple[tuple[int, ...], Path]] = []
                other: list[Path] = []
                for tool in tools:
                    version = tool.parent.parent.name
                    if re.fullmatch(r"\d+(?:\.\d+)*", version):
                        numeric.append((tuple(int(part) for part in version.split(".")), tool))
                    elif version.casefold() != "latest":
                        other.append(tool)
                candidates.extend(tool for _, tool in sorted(numeric, reverse=True))
                candidates.extend(sorted(other, key=lambda path: path.as_posix(), reverse=True))

    for candidate in candidates:
        if candidate.is_file() and (os.name == "nt" or os.access(candidate, os.X_OK)):
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
    if any(ord(character) < 32 or ord(character) == 127 for character in name):
        raise ArtifactVerificationError(
            f"Release bundle contains a control character in a ZIP entry name: {name!r}"
        )
    if name.startswith("/") or name.startswith("./") or "\\" in name:
        raise ArtifactVerificationError(
            f"Release bundle contains an unsafe ZIP entry name: {name!r}"
        )
    normalized = name[:-1] if name.endswith("/") else name
    parts = normalized.split("/")
    if (
        not normalized
        or re.match(r"^[A-Za-z]:", normalized)
        or any(part in {"", ".", ".."} or ":" in part for part in parts)
    ):
        raise ArtifactVerificationError(
            f"Release bundle contains an unsafe ZIP entry name: {name!r}"
        )


def _is_symbolic_link(info: zipfile.ZipInfo) -> bool:
    unix_mode = (info.external_attr >> 16) & 0xFFFF
    return stat.S_ISLNK(unix_mode)


@contextmanager
def _open_regular_bundle(bundle: Path):
    try:
        before = bundle.lstat()
    except OSError as exc:
        raise ArtifactVerificationError(f"Release bundle is missing or unreadable: {bundle}: {exc}") from exc
    if stat.S_ISLNK(before.st_mode) or not stat.S_ISREG(before.st_mode):
        raise ArtifactVerificationError(f"Release bundle must be a regular non-symlink file: {bundle}")
    if before.st_size <= 0:
        raise ArtifactVerificationError(f"Release bundle is missing or empty: {bundle}")

    flags = os.O_RDONLY
    if hasattr(os, "O_CLOEXEC"):
        flags |= os.O_CLOEXEC
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    if hasattr(os, "O_NONBLOCK"):
        flags |= os.O_NONBLOCK
    try:
        descriptor = os.open(bundle, flags)
    except OSError as exc:
        raise ArtifactVerificationError(f"Unable to safely open release bundle: {exc}") from exc

    identity_fields = (
        "st_dev", "st_ino", "st_mode", "st_size", "st_mtime_ns", "st_ctime_ns"
    )
    try:
        opened = os.fstat(descriptor)
        if not stat.S_ISREG(opened.st_mode) or any(
            getattr(before, field) != getattr(opened, field)
            for field in identity_fields
        ):
            raise ArtifactVerificationError("Release bundle changed while being opened")
        stream = os.fdopen(descriptor, "rb")
    except Exception:
        os.close(descriptor)
        raise

    try:
        yield stream
        after = os.fstat(stream.fileno())
        after_path = bundle.lstat()
        if any(
            getattr(before, field) != getattr(after, field)
            or getattr(before, field) != getattr(after_path, field)
            for field in identity_fields
        ):
            raise ArtifactVerificationError("Release bundle changed while being inspected")
    except OSError as exc:
        raise ArtifactVerificationError(f"Release bundle changed while being inspected: {exc}") from exc
    finally:
        stream.close()


def verify_bundle_structure(bundle: Path) -> dict[str, object]:
    try:
        with _open_regular_bundle(bundle) as stream:
            with zipfile.ZipFile(stream) as archive:
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
                namespace_paths: set[str] = set()
                file_paths: set[str] = set()
                parent_paths: set[str] = set()
                for info in infos:
                    _validate_zip_entry_name(info.filename)
                    canonical_name = info.filename[:-1] if info.filename.endswith("/") else info.filename
                    if canonical_name in namespace_paths:
                        raise ArtifactVerificationError(
                            f"Release bundle contains conflicting file/directory paths: {canonical_name}"
                        )
                    parts = canonical_name.split("/")
                    parents = ["/".join(parts[:index]) for index in range(1, len(parts))]
                    conflicting_parent = next((parent for parent in parents if parent in file_paths), None)
                    if conflicting_parent is not None:
                        raise ArtifactVerificationError(
                            f"Release bundle entry is nested beneath a file: {conflicting_parent}"
                        )
                    if not info.is_dir() and canonical_name in parent_paths:
                        raise ArtifactVerificationError(
                            f"Release bundle file shadows an existing directory path: {canonical_name}"
                        )
                    namespace_paths.add(canonical_name)
                    if not info.is_dir():
                        file_paths.add(canonical_name)
                    parent_paths.update(parents)
                    if _is_symbolic_link(info):
                        raise ArtifactVerificationError(
                            f"Release bundle contains a symbolic-link ZIP entry: {info.filename}"
                        )
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

    active_environment = dict(os.environ if environment is None else environment)
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
    unsigned_markers = (
        "jar is unsigned",
        "unsigned entry",
        "unsigned entries",
    )
    if (
        "jar verified" not in normalized_verification
        or any(marker in normalized_verification for marker in unsigned_markers)
    ):
        raise ArtifactVerificationError(
            "Release bundle is not completely and verifiably JAR-signed"
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
