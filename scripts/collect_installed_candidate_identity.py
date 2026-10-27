#!/usr/bin/env python3
"""Collect objective identity evidence from an installed Forest Run release package.

The collector verifies a clean canonical main candidate, inspects one authorized
Android device, pulls every installed APK split, verifies each APK signature,
records package/version/installer/device facts, and publishes a manifest that is
then independently validated by validate_installed_candidate_identity.py.

It does not infer a specific Play track. `com.android.vending` proves only that the
package manager attributes installation to Google Play; internal-track upload,
receipt, eligibility, and rollout remain external Play Console evidence.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import secrets
import stat
import shutil
import subprocess
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable, Sequence

import validate_installed_candidate_identity as identity
import verify_main_candidate
from stable_file_reader import (
    StableFileError,
    atomic_publish_directory,
    atomic_write_bytes,
    ensure_directory_exists,
    open_directory_descriptor,
    remove_directory_at,
)

CANONICAL_ORIGIN = "github.com/Anurag9000/Forest_Run"
CERT_LINE_RE = re.compile(
    r"^Signer #1 certificate SHA-256 digest:\s*([0-9A-Fa-f:]+)\s*$",
    re.MULTILINE,
)
INSTALLER_RE = re.compile(r"(?:^|\s)installer=([^\s]*)")


class CollectionError(RuntimeError):
    """Raised when installed identity cannot be collected without ambiguity."""


def _run(
    command: Sequence[str],
    *,
    cwd: Path | None = None,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    try:
        result = subprocess.run(
            list(command),
            cwd=cwd,
            text=True,
            capture_output=True,
            check=False,
        )
    except OSError as exc:
        raise CollectionError(f"could not run {command[0]!r}: {exc}") from exc
    if check and result.returncode != 0:
        diagnostic = (result.stderr or result.stdout).strip() or "no diagnostic output"
        raise CollectionError(
            f"command failed ({result.returncode}): {' '.join(command)}: {diagnostic}"
        )
    return result


def _normalize_origin(raw: str) -> str:
    value = raw.strip().rstrip("/")
    if value.endswith(".git"):
        value = value[:-4]
    if value.startswith("git@github.com:"):
        return "github.com/" + value[len("git@github.com:") :]
    if value.startswith("ssh://git@github.com/"):
        return "github.com/" + value[len("ssh://git@github.com/") :]
    if value.startswith("https://github.com/"):
        return "github.com/" + value[len("https://github.com/") :]
    return value


def _verify_origin_main(root: Path, candidate_sha: str) -> None:
    origin = _run(["git", "remote", "get-url", "origin"], cwd=root).stdout.strip()
    if _normalize_origin(origin) != CANONICAL_ORIGIN:
        raise CollectionError(
            f"origin is not the canonical Forest Run repository: {origin!r}"
        )
    _run(
        [
            "git",
            "fetch",
            "--quiet",
            "--no-tags",
            "origin",
            "+refs/heads/main:refs/remotes/origin/main",
        ],
        cwd=root,
    )
    for ref in ("HEAD", "refs/heads/main", "refs/remotes/origin/main"):
        observed = _run(["git", "rev-parse", ref], cwd=root).stdout.strip().lower()
        if observed != candidate_sha:
            raise CollectionError(
                f"candidate changed or diverged: {ref}={observed}, expected={candidate_sha}"
            )


def _sdk_roots() -> list[Path]:
    roots: list[Path] = []
    for name in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        raw = os.environ.get(name)
        if raw:
            path = Path(raw).expanduser()
            if path not in roots:
                roots.append(path)
    return roots


def _executable_variants(name: str) -> tuple[str, ...]:
    if os.name == "nt":
        return (f"{name}.exe", f"{name}.bat", f"{name}.cmd", name)
    return (name,)


def _resolve_adb() -> str:
    found = shutil.which("adb")
    if found:
        return found
    for root in _sdk_roots():
        for executable in _executable_variants("adb"):
            candidate = root / "platform-tools" / executable
            if candidate.is_file():
                return str(candidate)
    raise CollectionError("adb was not found on PATH, ANDROID_HOME, or ANDROID_SDK_ROOT")


def _resolve_apkanalyzer() -> str:
    found = shutil.which("apkanalyzer")
    if found:
        return found
    for root in _sdk_roots():
        candidates: list[Path] = []
        command_line = root / "cmdline-tools"
        if command_line.is_dir():
            versions = list(command_line.iterdir())
            versions.sort(key=_command_line_version_key, reverse=True)
            for child in versions:
                for executable in _executable_variants("apkanalyzer"):
                    candidates.append(child / "bin" / executable)
        for executable in _executable_variants("apkanalyzer"):
            candidates.append(root / "tools" / "bin" / executable)
        for candidate in candidates:
            if candidate.is_file():
                return str(candidate)
    raise CollectionError("apkanalyzer was not found in the Android SDK")


def _command_line_version_key(path: Path) -> tuple[int, tuple[int, ...]]:
    if path.name.lower() == "latest":
        return (1, ())
    numbers = re.findall(r"\d+", path.name)
    return (0, tuple(int(number) for number in numbers) or (0,))


def _version_key(path: Path) -> tuple[int, ...]:
    numbers = re.findall(r"\d+", path.parent.name)
    return tuple(int(number) for number in numbers) or (0,)


def _resolve_apksigner() -> str:
    found = shutil.which("apksigner")
    if found:
        return found
    for root in _sdk_roots():
        build_tools = root / "build-tools"
        if not build_tools.is_dir():
            continue
        candidates: list[Path] = []
        for version in build_tools.iterdir():
            if not version.is_dir():
                continue
            for executable in _executable_variants("apksigner"):
                candidate = version / executable
                if candidate.is_file():
                    candidates.append(candidate)
        if candidates:
            return str(max(candidates, key=_version_key))
    raise CollectionError("apksigner was not found in the Android SDK build-tools")


def parse_adb_devices(output: str) -> list[str]:
    serials: list[str] = []
    for raw_line in output.splitlines()[1:]:
        fields = raw_line.strip().split()
        if len(fields) >= 2 and fields[1] == "device":
            serials.append(fields[0])
    return serials


def parse_pm_paths(output: str) -> list[str]:
    paths: list[str] = []
    for raw_line in output.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        if not line.startswith("package:"):
            raise CollectionError(f"unexpected pm path output: {line!r}")
        remote = line[len("package:") :]
        if not remote.startswith("/") or not remote.endswith(".apk"):
            raise CollectionError(f"invalid installed APK path: {remote!r}")
        if remote in paths:
            raise CollectionError(f"duplicate installed APK path: {remote}")
        paths.append(remote)
    if not paths:
        raise CollectionError("package manager returned no installed APK paths")
    if sum(Path(path).name == "base.apk" for path in paths) != 1:
        raise CollectionError("installed package must expose exactly one base.apk")
    names = [Path(path).name for path in paths]
    if len(names) != len(set(names)):
        raise CollectionError("installed APK split basenames are not unique")
    return paths


def parse_apkanalyzer_summary(output: str) -> tuple[str, int, str]:
    line = next((item.strip() for item in output.splitlines() if item.strip()), "")
    fields = line.split(maxsplit=2)
    if len(fields) != 3:
        raise CollectionError(
            "apkanalyzer apk summary must contain applicationId, versionCode, and versionName"
        )
    application_id, raw_version_code, version_name = fields
    try:
        version_code = int(raw_version_code)
    except ValueError as exc:
        raise CollectionError("apkanalyzer returned a non-integer versionCode") from exc
    if version_code <= 0 or not version_name.strip():
        raise CollectionError("apkanalyzer returned an invalid package version")
    return application_id, version_code, version_name.strip()


def parse_installer_package(output: str, application_id: str) -> str:
    package_row = f"package:{application_id}"
    matching = [
        line.strip()
        for line in output.splitlines()
        if line.strip().split(maxsplit=1)[0:1] == [package_row]
    ]
    if len(matching) != 1:
        raise CollectionError(
            f"expected exactly one package-manager installer row for {application_id}"
        )
    match = INSTALLER_RE.search(matching[0])
    if match is None:
        raise CollectionError("package manager did not report an installer package")
    installer = match.group(1).strip()
    if not installer:
        raise CollectionError("installer package is blank")
    return installer


def parse_apksigner_certificate(output: str) -> str:
    match = CERT_LINE_RE.search(output)
    if match is None:
        raise CollectionError("apksigner did not report Signer #1 certificate SHA-256 digest")
    digest = match.group(1).replace(":", "").lower()
    if not identity.SHA256_RE.fullmatch(digest):
        raise CollectionError("apksigner returned an invalid certificate SHA-256 digest")
    return digest


def _write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _evidence_file_records(output_dir: Path, evidence_paths: Sequence[Path]) -> list[dict[str, str]]:
    records: list[dict[str, str]] = []
    seen_relative: set[str] = set()
    for path in sorted(evidence_paths, key=lambda item: item.relative_to(output_dir).as_posix()):
        relative = path.relative_to(output_dir).as_posix()
        if relative in seen_relative:
            raise CollectionError(f"duplicate evidence path: {relative}")
        seen_relative.add(relative)
        records.append({"path": relative, "sha256": _sha256(path)})
    return records


def _device_shell(adb: str, serial: str, *arguments: str) -> str:
    return _run([adb, "-s", serial, "shell", *arguments]).stdout.replace("\r\n", "\n")


def _getprop(adb: str, serial: str, name: str) -> str:
    value = _device_shell(adb, serial, "getprop", name).strip()
    if not value:
        raise CollectionError(f"device property {name} is blank")
    return value


def _atomic_json(path: Path, payload: dict[str, object]) -> None:
    content = (
        json.dumps(payload, indent=2, sort_keys=True, allow_nan=False) + "\n"
    ).encode("utf-8")
    try:
        atomic_write_bytes(
            path,
            content,
            "installed candidate identity manifest",
            reject_symlink_leaf=True,
        )
    except StableFileError as exc:
        raise CollectionError(str(exc)) from exc


def _copy_validated_tree(source_fd: int, destination_fd: int, label: str = "collection") -> None:
    """Copy a validated regular-file tree between held directory descriptors."""
    for name in os.listdir(source_fd):
        metadata = os.stat(name, dir_fd=source_fd, follow_symlinks=False)
        if stat.S_ISDIR(metadata.st_mode):
            os.mkdir(name, mode=0o700, dir_fd=destination_fd)
            source_child = os.open(
                name, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=source_fd
            )
            destination_child = os.open(
                name, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=destination_fd
            )
            try:
                opened = os.fstat(source_child)
                if (opened.st_dev, opened.st_ino) != (metadata.st_dev, metadata.st_ino):
                    raise CollectionError(f"{label} directory changed while being copied: {name}")
                _copy_validated_tree(source_child, destination_child, f"{label}/{name}")
                os.fchmod(destination_child, stat.S_IMODE(metadata.st_mode))
                os.utime(destination_child, ns=(metadata.st_atime_ns, metadata.st_mtime_ns))
                os.fsync(destination_child)
            finally:
                os.close(source_child)
                os.close(destination_child)
        elif stat.S_ISREG(metadata.st_mode):
            source_file = os.open(
                name,
                os.O_RDONLY | os.O_NOFOLLOW | getattr(os, "O_CLOEXEC", 0),
                dir_fd=source_fd,
            )
            destination_file: int | None = None
            try:
                before = os.fstat(source_file)
                identity_before = (
                    before.st_dev,
                    before.st_ino,
                    before.st_size,
                    before.st_mtime_ns,
                    before.st_ctime_ns,
                )
                expected = (
                    metadata.st_dev,
                    metadata.st_ino,
                    metadata.st_size,
                    metadata.st_mtime_ns,
                    metadata.st_ctime_ns,
                )
                if identity_before != expected:
                    raise CollectionError(f"{label} file changed while being copied: {name}")
                destination_file = os.open(
                    name,
                    os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW,
                    stat.S_IMODE(before.st_mode),
                    dir_fd=destination_fd,
                )
                while True:
                    block = os.read(source_file, 1024 * 1024)
                    if not block:
                        break
                    view = memoryview(block)
                    while view:
                        written = os.write(destination_file, view)
                        if written <= 0:
                            raise OSError("short write while copying validated collection")
                        view = view[written:]
                after = os.fstat(source_file)
                identity_after = (
                    after.st_dev,
                    after.st_ino,
                    after.st_size,
                    after.st_mtime_ns,
                    after.st_ctime_ns,
                )
                if identity_after != expected:
                    raise CollectionError(f"{label} file changed while being copied: {name}")
                os.fchmod(destination_file, stat.S_IMODE(before.st_mode))
                os.utime(destination_file, ns=(before.st_atime_ns, before.st_mtime_ns))
                os.fsync(destination_file)
            except BaseException:
                if destination_file is not None:
                    os.close(destination_file)
                    destination_file = None
                try:
                    os.unlink(name, dir_fd=destination_fd)
                except FileNotFoundError:
                    pass
                raise
            finally:
                os.close(source_file)
                if destination_file is not None:
                    os.close(destination_file)
        else:
            raise CollectionError(f"{label} contains an unsupported entry: {name}")


def _collect_in_directory(
    *,
    root: Path,
    output_dir: Path,
    expected_candidate_sha: str,
    expected_version_code: int,
    expected_app_signing_certificate_sha256: str,
    serial: str | None = None,
    package_name: str = identity.CANONICAL_APPLICATION_ID,
    expected_installer_package: str = identity.PLAY_STORE_INSTALLER,
) -> identity.InstalledIdentitySummary:
    root = root.expanduser().resolve()
    expected_candidate_sha = expected_candidate_sha.strip()
    expected_cert = expected_app_signing_certificate_sha256.strip().replace(":", "").lower()
    if not identity.SHA40_RE.fullmatch(expected_candidate_sha):
        raise CollectionError("expected candidate SHA must be lowercase 40-hex")
    if expected_version_code <= 0:
        raise CollectionError("expected version code must be positive")
    if not identity.SHA256_RE.fullmatch(expected_cert):
        raise CollectionError("expected app-signing certificate must be lowercase 64-hex")
    if package_name != identity.CANONICAL_APPLICATION_ID:
        raise CollectionError("package name must be the canonical Forest Run release application ID")
    if expected_installer_package != identity.PLAY_STORE_INSTALLER:
        raise CollectionError("expected installer must be Google Play")

    try:
        candidate = verify_main_candidate.verify_main_candidate(root, expected_candidate_sha)
    except verify_main_candidate.CandidateVerificationError as exc:
        raise CollectionError(str(exc)) from exc
    _verify_origin_main(root, candidate.sha)

    output_dir = output_dir.expanduser().resolve()
    if output_dir.exists() and any(output_dir.iterdir()):
        raise CollectionError(f"output directory must be absent or empty: {output_dir}")
    output_dir.mkdir(parents=True, exist_ok=True)
    raw_dir = output_dir / "raw"
    apk_dir = output_dir / "apks"
    raw_dir.mkdir()
    apk_dir.mkdir()

    adb = _resolve_adb()
    apkanalyzer = _resolve_apkanalyzer()
    apksigner = _resolve_apksigner()
    devices_output = _run([adb, "devices"]).stdout.replace("\r\n", "\n")
    _write_text(raw_dir / "adb-devices.txt", devices_output)
    connected = parse_adb_devices(devices_output)
    selected = serial or os.environ.get("FOREST_RUN_DEVICE_SERIAL")
    if selected is None:
        if len(connected) != 1:
            raise CollectionError(
                "exactly one authorized device is required, or pass --serial / FOREST_RUN_DEVICE_SERIAL"
            )
        selected = connected[0]
    if selected not in connected:
        raise CollectionError(f"requested device is not connected and authorized: {selected}")

    pm_path_output = _device_shell(adb, selected, "pm", "path", package_name)
    installer_output = _device_shell(adb, selected, "pm", "list", "packages", "-i", package_name)
    dumpsys_output = _device_shell(adb, selected, "dumpsys", "package", package_name)
    _write_text(raw_dir / "pm-path.txt", pm_path_output)
    _write_text(raw_dir / "pm-installer.txt", installer_output)
    _write_text(raw_dir / "dumpsys-package.txt", dumpsys_output)

    remote_paths = parse_pm_paths(pm_path_output)
    installer = parse_installer_package(installer_output, package_name)
    if installer != expected_installer_package:
        raise CollectionError(
            f"installed package is attributed to {installer!r}, expected {expected_installer_package!r}"
        )

    apk_entries: list[dict[str, object]] = []
    observed_certificates: set[str] = set()
    base_summary: tuple[str, int, str] | None = None
    evidence_paths: list[Path] = [
        raw_dir / "adb-devices.txt",
        raw_dir / "pm-path.txt",
        raw_dir / "pm-installer.txt",
        raw_dir / "dumpsys-package.txt",
    ]

    for remote_path in sorted(remote_paths, key=lambda item: (Path(item).name != "base.apk", Path(item).name)):
        name = Path(remote_path).name
        local_apk = apk_dir / name
        pull = _run([adb, "-s", selected, "pull", remote_path, str(local_apk)])
        _write_text(raw_dir / f"adb-pull-{name}.txt", pull.stdout + pull.stderr)
        evidence_paths.append(raw_dir / f"adb-pull-{name}.txt")
        if not local_apk.is_file() or local_apk.stat().st_size <= 0:
            raise CollectionError(f"pulled APK is missing or empty: {name}")
        evidence_paths.append(local_apk)

        signer_result = _run([apksigner, "verify", "--print-certs", str(local_apk)])
        signer_text = signer_result.stdout + signer_result.stderr
        _write_text(raw_dir / f"apksigner-{name}.txt", signer_text)
        evidence_paths.append(raw_dir / f"apksigner-{name}.txt")
        signer_digest = parse_apksigner_certificate(signer_text)
        observed_certificates.add(signer_digest)

        if name == "base.apk":
            summary_result = _run([apkanalyzer, "apk", "summary", str(local_apk)])
            summary_text = summary_result.stdout + summary_result.stderr
            _write_text(raw_dir / "apkanalyzer-summary.txt", summary_text)
            evidence_paths.append(raw_dir / "apkanalyzer-summary.txt")
            base_summary = parse_apkanalyzer_summary(summary_result.stdout)

        apk_entries.append(
            {
                "name": name,
                "sha256": _sha256(local_apk),
                "size_bytes": local_apk.stat().st_size,
                "signing_certificate_sha256": signer_digest,
            }
        )

    if base_summary is None:
        raise CollectionError("base.apk summary was not captured")
    observed_application_id, observed_version_code, observed_version_name = base_summary
    if observed_application_id != package_name:
        raise CollectionError(
            f"installed application ID is {observed_application_id!r}, expected {package_name!r}"
        )
    if observed_version_code != expected_version_code:
        raise CollectionError(
            f"installed versionCode is {observed_version_code}, expected {expected_version_code}"
        )
    if observed_certificates != {expected_cert}:
        raise CollectionError(
            "installed APK signer set does not equal the expected Play app-signing certificate"
        )

    manufacturer = _getprop(adb, selected, "ro.product.manufacturer")
    model = _getprop(adb, selected, "ro.product.model")
    device_name = _getprop(adb, selected, "ro.product.device")
    sdk_text = _getprop(adb, selected, "ro.build.version.sdk")
    fingerprint = _getprop(adb, selected, "ro.build.fingerprint")
    try:
        sdk = int(sdk_text)
    except ValueError as exc:
        raise CollectionError(f"device SDK property is not an integer: {sdk_text!r}") from exc
    device_properties = (
        f"manufacturer={manufacturer}\n"
        f"model={model}\n"
        f"device={device_name}\n"
        f"sdk={sdk}\n"
        f"build_fingerprint={fingerprint}\n"
    )
    _write_text(raw_dir / "device.properties", device_properties)
    evidence_paths.append(raw_dir / "device.properties")

    base_entry = next(item for item in apk_entries if item["name"] == "base.apk")
    unique_evidence = _evidence_file_records(output_dir, evidence_paths)

    payload: dict[str, object] = {
        "schema_version": identity.SCHEMA_VERSION,
        "captured_at_utc": datetime.now(timezone.utc).isoformat(timespec="seconds").replace(
            "+00:00", "Z"
        ),
        "candidate": {
            "repository": identity.CANONICAL_REPOSITORY,
            "branch": identity.CANONICAL_BRANCH,
            "commit_sha": candidate.sha,
            "application_id": package_name,
            "version_code": expected_version_code,
            "app_signing_certificate_sha256": expected_cert,
            "expected_installer_package": expected_installer_package,
        },
        "device": {
            "serial_sha256": hashlib.sha256(selected.encode("utf-8")).hexdigest(),
            "manufacturer": manufacturer,
            "model": model,
            "device": device_name,
            "sdk": sdk,
            "build_fingerprint": fingerprint,
        },
        "installed_package": {
            "application_id": observed_application_id,
            "version_code": observed_version_code,
            "version_name": observed_version_name,
            "installer_package": installer,
            "app_signing_certificate_sha256": expected_cert,
            "base_apk_sha256": base_entry["sha256"],
            "apk_set": apk_entries,
        },
        "claims": {
            "play_store_installer_observed": True,
            "specific_play_track_verified": False,
        },
        "evidence_files": unique_evidence,
    }

    manifest = output_dir / "installed-candidate-identity.json"
    _atomic_json(manifest, payload)
    _verify_origin_main(root, candidate.sha)
    try:
        summary = identity.load_and_validate(manifest)
    except identity.InstalledIdentityError as exc:
        manifest.unlink(missing_ok=True)
        raise CollectionError(f"collected record failed independent validation: {exc}") from exc
    return summary


def collect(
    *,
    root: Path,
    output_dir: Path,
    expected_candidate_sha: str,
    expected_version_code: int,
    expected_app_signing_certificate_sha256: str,
    serial: str | None = None,
    package_name: str = identity.CANONICAL_APPLICATION_ID,
    expected_installer_package: str = identity.PLAY_STORE_INSTALLER,
) -> identity.InstalledIdentitySummary:
    """Collect into a private sibling directory and publish only a valid bundle.

    Device and SDK failures are common during evidence collection. Keeping all
    intermediate pulls and command output outside the requested destination
    means a failed attempt can be retried without manually cleaning partial
    evidence, while the final directory appears in one rename after validation.
    """
    requested_destination = Path(
        os.path.abspath(os.fspath(output_dir.expanduser()))
    )
    if requested_destination.is_symlink():
        raise CollectionError(
            f"output directory must not be a symbolic link: {requested_destination}"
        )
    destination = requested_destination
    ensure_directory_exists(destination.parent, "installed identity output parent")
    try:
        parent_fd = open_directory_descriptor(
            destination.parent, "installed identity output parent"
        )
    except StableFileError as exc:
        raise CollectionError(str(exc)) from exc
    parent_metadata = os.fstat(parent_fd)
    staging_name: str | None = None
    staging_identity: tuple[int, int] | None = None
    staging_fd: int | None = None

    def check_parent_binding() -> None:
        try:
            current_fd = open_directory_descriptor(
                destination.parent, "installed identity output parent"
            )
        except StableFileError as exc:
            raise CollectionError("output parent changed during collection") from exc
        try:
            current = os.fstat(current_fd)
            if (current.st_dev, current.st_ino) != (
                parent_metadata.st_dev,
                parent_metadata.st_ino,
            ):
                raise CollectionError("output parent changed during collection")
        finally:
            os.close(current_fd)

    try:
        try:
            initial_destination = os.stat(
                destination.name, dir_fd=parent_fd, follow_symlinks=False
            )
        except FileNotFoundError:
            initial_destination = None
        if initial_destination is not None:
            if stat.S_ISLNK(initial_destination.st_mode):
                raise CollectionError(
                    f"output directory must not be a symbolic link: {destination}"
                )
            if not stat.S_ISDIR(initial_destination.st_mode):
                raise CollectionError(f"output directory is not a directory: {destination}")
            existing_fd = os.open(
                destination.name,
                os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW,
                dir_fd=parent_fd,
            )
            try:
                opened = os.fstat(existing_fd)
                if (opened.st_dev, opened.st_ino) != (
                    initial_destination.st_dev,
                    initial_destination.st_ino,
                ):
                    raise CollectionError("output directory changed during admission")
                if os.listdir(existing_fd):
                    raise CollectionError(
                        f"output directory must be absent or empty: {destination}"
                    )
            finally:
                os.close(existing_fd)

        with tempfile.TemporaryDirectory(prefix=f".{destination.name}.collection-") as scratch:
            scratch_dir = Path(scratch)
            summary = _collect_in_directory(
                root=root,
                output_dir=scratch_dir,
                expected_candidate_sha=expected_candidate_sha,
                expected_version_code=expected_version_code,
                expected_app_signing_certificate_sha256=expected_app_signing_certificate_sha256,
                serial=serial,
                package_name=package_name,
                expected_installer_package=expected_installer_package,
            )

            check_parent_binding()
            for _ in range(100):
                candidate = f".{destination.name}.{secrets.token_hex(12)}.staging"
                try:
                    os.mkdir(candidate, mode=0o700, dir_fd=parent_fd)
                    staging_name = candidate
                    break
                except FileExistsError:
                    continue
            if staging_name is None:
                raise CollectionError("could not allocate a private collection staging directory")
            staging_fd = os.open(
                staging_name,
                os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW,
                dir_fd=parent_fd,
            )
            staging_metadata = os.fstat(staging_fd)
            staging_identity = (staging_metadata.st_dev, staging_metadata.st_ino)
            source_fd = open_directory_descriptor(scratch_dir, "validated collection scratch")
            try:
                _copy_validated_tree(source_fd, staging_fd)
                os.fsync(staging_fd)
            finally:
                os.close(source_fd)
            os.close(staging_fd)
            staging_fd = None
            check_parent_binding()
            staging = destination.parent / staging_name
            backup = destination.parent / f".{destination.name}.{secrets.token_hex(12)}.backup"
            try:
                atomic_publish_directory(
                    staging,
                    destination,
                    backup,
                    "installed identity output",
                    destination_must_be_empty=True,
                )
            except StableFileError as exc:
                if "output directory must be absent or empty" in str(exc):
                    raise CollectionError(
                        f"output directory changed during collection: {destination}"
                    ) from exc
                raise CollectionError(str(exc)) from exc
            return summary
    finally:
        if staging_fd is not None:
            os.close(staging_fd)
        if staging_name is not None and staging_identity is not None:
            try:
                current = os.stat(
                    staging_name, dir_fd=parent_fd, follow_symlinks=False
                )
            except FileNotFoundError:
                current = None
            if current is not None and stat.S_ISDIR(current.st_mode) and (
                current.st_dev,
                current.st_ino,
            ) == staging_identity:
                remove_directory_at(parent_fd, staging_name)
        os.close(parent_fd)


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--expected-candidate-sha", required=True)
    parser.add_argument("--expected-version-code", type=int, required=True)
    parser.add_argument("--expected-app-signing-certificate-sha256", required=True)
    parser.add_argument("--serial")
    parser.add_argument("--package-name", default=identity.CANONICAL_APPLICATION_ID)
    parser.add_argument("--expected-installer-package", default=identity.PLAY_STORE_INSTALLER)
    args = parser.parse_args(argv)
    try:
        summary = collect(
            root=args.root,
            output_dir=args.output_dir,
            expected_candidate_sha=args.expected_candidate_sha,
            expected_version_code=args.expected_version_code,
            expected_app_signing_certificate_sha256=args.expected_app_signing_certificate_sha256,
            serial=args.serial,
            package_name=args.package_name,
            expected_installer_package=args.expected_installer_package,
        )
    except (CollectionError, OSError) as exc:
        print(json.dumps({"status": "invalid", "error": str(exc)}, sort_keys=True))
        return 1
    print(json.dumps(summary.to_json(), sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
