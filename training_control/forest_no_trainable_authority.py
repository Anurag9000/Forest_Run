"""Fail-closed no-trainable-source authority for Forest_Run.

Forest_Run is currently a deterministic Android SurfaceView/Canvas game.  The
central training controller must therefore *not* invent optimizer jobs.  Instead,
this authority proves from the live source tree and dependency manifests that no
retained machine-learning training surface exists.  Any future ML/training marker
causes the audit to fail until the repository receives a real scientific DAG.
"""
from __future__ import annotations

from dataclasses import asdict, dataclass
from pathlib import Path
import ast
import hashlib
import io
import json
import os
import re
import tokenize
from typing import Iterable

ROOT = Path(__file__).resolve().parents[1]

SOURCE_SUFFIXES = {
    ".kt", ".kts", ".java", ".py", ".js", ".ts", ".tsx", ".jsx",
    ".gradle", ".toml", ".yaml", ".yml", ".json", ".xml",
    ".sh", ".bash", ".bat", ".cmd", ".ps1", ".ipynb", ".properties", ".pro",
}
DEPENDENCY_TEXT_NAMES = {
    "Pipfile", "Pipfile.lock", "poetry.lock", "uv.lock", "requirements.txt",
    "requirements-dev.txt", "requirements-test.txt", "constraints.txt",
    "Dockerfile", "gradlew", "Makefile", "makefile", "Justfile",
}
MODEL_ARTIFACT_SUFFIXES = {
    ".tflite", ".onnx", ".ort", ".pt", ".pth", ".ckpt", ".safetensors",
    ".keras", ".mlmodel", ".mlpackage", ".h5", ".hdf5", ".pb",
    ".joblib", ".pkl", ".pickle", ".npz",
}
OPAQUE_ARCHIVE_SUFFIXES = {
    ".zip", ".tar", ".tgz", ".tar.gz", ".tar.bz2", ".tar.xz", ".7z", ".rar",
}
OPAQUE_CODE_SUFFIXES = {".jar", ".aar", ".so", ".dll", ".dylib", ".whl", ".apk", ".aab", ".dex", ".class"}
OPAQUE_CODE_ALLOWLIST = {"gradle/wrapper/gradle-wrapper.jar"}
DEPENDENCY_CONFIG_RE = re.compile(
    r"^(?:implementation|api|compileOnly|runtimeOnly|annotationProcessor|kapt|ksp|"
    r"[A-Za-z_][A-Za-z0-9_]*(?:Implementation|Api|CompileOnly|RuntimeOnly|"
    r"AnnotationProcessor))\s*\("
)
REQUIRED_APPLICATION_FILES = (
    "settings.gradle.kts",
    "build.gradle.kts",
    "app/build.gradle.kts",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/anurag9000/forestrun/MainActivity.kt",
    "app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt",
)

AUTHORITY_RELATIVE_FILES = (
    "run_all_training.py",
    "training_control/forest_no_trainable_authority.py",
    "training_control/dataset_cohort_not_applicable_v1.py",
)

VOLATILE_SKIP_PARTS = {
    ".git", ".gradle", ".idea", "build", ".training_control", "artifacts",
    ".venv", "venv", "node_modules", "__pycache__", ".pytest_cache",
}
SKIP_PARTS = VOLATILE_SKIP_PARTS | {"docs", "Final_Assets (2)"}
MODEL_ARTIFACT_SKIP_PARTS = VOLATILE_SKIP_PARTS | {"docs"}
SCOPE_SKIP_PARTS = VOLATILE_SKIP_PARTS | {"docs"}
# These expressions intentionally target training/model-framework semantics rather
# than generic words such as "model" that are common in ordinary application code.
FORBIDDEN_PATTERNS: tuple[tuple[str, re.Pattern[str]], ...] = (
    ("pytorch", re.compile(r"\b(torch|pytorch|torchvision|torchaudio)\b", re.I)),
    ("tensorflow", re.compile(r"\b(tensorflow|keras|tflite|tensorflow[-_. ]?lite)\b", re.I)),
    ("jax", re.compile(r"\b(jax|flax|optax|haiku)\b", re.I)),
    ("sklearn", re.compile(r"\b(scikit[- ]?learn|sklearn)\b", re.I)),
    ("optimizer", re.compile(r"\b(optimizer|optimiser)\.(step|zero_grad)|\bAdamW?\s*\(|\bSGD\s*\(", re.I)),
    ("backprop", re.compile(r"\.backward\s*\(|gradient[_ -]?descent|backpropagation", re.I)),
    ("training-loop", re.compile(r"\b(train|training)[_ -]?(epoch|step|loop)|early[_ -]?stopping", re.I)),
    ("ml-dependency", re.compile(
        r"(org[.:]tensorflow|tensorflow[-_. ]?lite|org[.:]pytorch|"
        r"onnxruntime|com[.:]google[.:]mlkit|com[.:]google[.:]mediapipe|"
        r"deeplearning4j|org[.:]nd4j)", re.I
    )),
)


@dataclass(frozen=True, slots=True)
class Finding:
    path: str
    line: int
    category: str
    excerpt: str


@dataclass(frozen=True, slots=True)
class Audit:
    scanned_files: tuple[str, ...]
    findings: tuple[Finding, ...]
    android_dependencies: tuple[str, ...]
    source_manifest_sha256: str
    scope_manifest_sha256: str
    authority_manifest_sha256: str

    @property
    def complete(self) -> bool:
        return not self.findings

    def to_dict(self) -> dict[str, object]:
        return {
            "schema_version": 1,
            "repository": "Anurag9000/Forest_Run",
            "classification": "no_retained_trainable_surface",
            "scanned_files": list(self.scanned_files),
            "findings": [asdict(row) for row in self.findings],
            "android_dependencies": list(self.android_dependencies),
            "source_manifest_sha256": self.source_manifest_sha256,
            "scope_manifest_sha256": self.scope_manifest_sha256,
            "authority_manifest_sha256": self.authority_manifest_sha256,
            "complete": self.complete,
            "wildcard_training_exemptions": False,
            "source_configuration_only": True,
            "execution_claim_emitted": False,
        }


def _files(root: Path = ROOT) -> Iterable[Path]:
    for path in sorted(root.rglob("*")):
        if not path.is_file():
            continue
        relative = path.relative_to(root)
        # Symlink provenance is classified separately; never dereference a
        # source symlink while scanning for training semantics.
        if path.is_symlink():
            continue
        # The root entrypoint and training-control authority are scanned too.
        # Python docstrings/string literals are masked below, so policy prose may
        # name ML frameworks without exempting executable imports or optimizers.
        if any(part in SKIP_PARTS for part in relative.parts):
            continue
        dependency_text = (
            path.name in DEPENDENCY_TEXT_NAMES
            or (path.name.startswith("requirements-") and path.suffix.lower() == ".txt")
        )
        if path.suffix.lower() in SOURCE_SUFFIXES or dependency_text or path.name in {
            "build.gradle.kts", "settings.gradle.kts", "gradle.properties",
        }:
            yield path


def _manifest_update(digest: "hashlib._Hash", relative: str, payload: bytes) -> None:
    """Bind exactly the same bytes that are parsed by the source scanner."""
    encoded = relative.encode("utf-8")
    digest.update(len(encoded).to_bytes(8, "big"))
    digest.update(encoded)
    digest.update(len(payload).to_bytes(8, "big"))
    digest.update(payload)


def _authority_manifest(root: Path = ROOT) -> str:
    """Bind the exact authority code for the repository root being audited."""
    root = Path(root).resolve()
    digest = hashlib.sha256()
    for relative in AUTHORITY_RELATIVE_FILES:
        path = root / relative
        if not path.is_file() or path.is_symlink():
            _manifest_update(digest, f"missing:{relative}", b"")
            continue
        _manifest_update(digest, relative, path.read_bytes())
    return digest.hexdigest()


def _scope_manifest(root: Path) -> str:
    """Bind path, type, size and bytes for every repository-owned input.

    Source bytes are also bound by source_manifest_sha256. This broader digest
    covers unscanned assets and allow-listed opaque build tooling so replacing
    a file at the same path cannot inherit an older applicability certificate.
    """
    digest = hashlib.sha256()
    for path in sorted(root.rglob("*")):
        relative = path.relative_to(root)
        if any(part in SCOPE_SKIP_PARTS for part in relative.parts):
            continue
        if not path.is_file() and not path.is_symlink():
            continue
        kind = "symlink" if path.is_symlink() else "file"
        if path.is_symlink():
            payload = os.readlink(path).encode("utf-8", "replace")
        else:
            # Bind unscanned assets and allow-listed opaque build tooling by
            # content as well as path. Source text is redundantly protected by
            # source_manifest_sha256; this full-scope digest prevents a same-path
            # binary replacement from inheriting an older applicability proof.
            file_hash = hashlib.sha256()
            size = 0
            with path.open("rb") as handle:
                while True:
                    chunk = handle.read(1024 * 1024)
                    if not chunk:
                        break
                    size += len(chunk)
                    file_hash.update(chunk)
            payload = size.to_bytes(8, "big") + file_hash.digest()
        _manifest_update(digest, f"{kind}:{relative.as_posix()}", payload)
    return digest.hexdigest()


def _notebook_code(relative: str, payload: bytes) -> tuple[str, tuple[Finding, ...]]:
    """Extract executable notebook cells while ignoring Markdown prose."""
    try:
        notebook = json.loads(payload.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        return "", (Finding(relative, 0, "notebook-parse-error", str(exc)[:240]),)
    cells = notebook.get("cells")
    if not isinstance(cells, list):
        return "", (Finding(relative, 0, "notebook-parse-error", "missing cells list"),)
    chunks: list[str] = []
    for cell in cells:
        if not isinstance(cell, dict) or cell.get("cell_type") != "code":
            continue
        source = cell.get("source", "")
        if isinstance(source, list):
            chunks.append("".join(str(item) for item in source))
        elif isinstance(source, str):
            chunks.append(source)
        else:
            return "", (Finding(
                relative, 0, "notebook-parse-error", "code cell source is not text/list"
            ),)
    return "\n".join(chunks), ()


def _dependency_executable_lines(path: Path, text: str) -> list[str]:
    """Ignore comments in simple dependency manifests, not active entries."""
    if path.name in DEPENDENCY_TEXT_NAMES or (
        path.name.startswith("requirements-") and path.suffix.lower() == ".txt"
    ):
        return ["" if line.lstrip().startswith("#") else line for line in text.splitlines()]
    return text.splitlines()


def _symlink_findings(root: Path = ROOT) -> tuple[Finding, ...]:
    """Repository source provenance must never depend on out-of-tree targets."""
    findings: list[Finding] = []
    for path in sorted(root.rglob("*")):
        if not path.is_symlink():
            continue
        relative = path.relative_to(root)
        if any(part in SKIP_PARTS for part in relative.parts):
            continue
        try:
            target = os.readlink(path)
        except OSError:
            target = "<unreadable>"
        findings.append(Finding(
            relative.as_posix(), 0, "symlink-source", str(target)[:240]
        ))
    return tuple(findings)


def _dependencies(root: Path = ROOT) -> tuple[str, ...]:
    dependencies: list[str] = []
    for path in sorted(root.glob("**/*.gradle*")):
        if any(part in SKIP_PARTS for part in path.relative_to(root).parts):
            continue
        for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
            stripped = line.strip()
            if DEPENDENCY_CONFIG_RE.match(stripped):
                dependencies.append(f"{path.relative_to(root).as_posix()}:{stripped}")
    return tuple(dependencies)


def _opaque_archive_findings(root: Path = ROOT) -> tuple[Finding, ...]:
    """Fail closed on retained archives whose contents are not source-scanned.

    Documentation/evidence archives remain outside this source applicability
    authority. Product/runtime archives cannot be treated as evidence of
    absence because a model, trainer, native library or script can be hidden
    inside while only the outer path participates in the scope manifest.
    """
    findings: list[Finding] = []
    for path in sorted(root.rglob("*")):
        if not path.is_file() or path.is_symlink():
            continue
        relative = path.relative_to(root)
        if any(part in MODEL_ARTIFACT_SKIP_PARTS for part in relative.parts):
            continue
        lowered = path.name.lower()
        if any(lowered.endswith(suffix) for suffix in OPAQUE_ARCHIVE_SUFFIXES):
            findings.append(Finding(
                relative.as_posix(), 0, "opaque-source-archive", path.name[:240]
            ))
    return tuple(findings)


def _opaque_code_findings(root: Path = ROOT) -> tuple[Finding, ...]:
    """Fail closed on uninspected executable archives/native libraries."""
    findings: list[Finding] = []
    for path in sorted(root.rglob("*")):
        if not path.is_file() or path.is_symlink():
            continue
        relative = path.relative_to(root)
        if any(part in MODEL_ARTIFACT_SKIP_PARTS for part in relative.parts):
            continue
        rel = relative.as_posix()
        if path.suffix.lower() in OPAQUE_CODE_SUFFIXES and rel not in OPAQUE_CODE_ALLOWLIST:
            findings.append(Finding(rel, 0, "opaque-code-artifact", path.name[:240]))
    return tuple(findings)


def _model_artifact_findings(root: Path = ROOT) -> tuple[Finding, ...]:
    findings: list[Finding] = []
    for path in sorted(root.rglob("*")):
        if not path.is_file():
            continue
        relative = path.relative_to(root)
        if any(part in MODEL_ARTIFACT_SKIP_PARTS for part in relative.parts):
            continue
        if path.suffix.lower() in MODEL_ARTIFACT_SUFFIXES:
            findings.append(Finding(
                relative.as_posix(), 0, "ml-model-artifact", path.name[:240]
            ))
    return tuple(findings)



def _python_executable_lines(text: str) -> list[str]:
    """Mask Python string/comment tokens while preserving source line numbers.

    The authority scans executable syntax, not prose in test assertions,
    docstrings or comments. Tokenization failure falls back to the original
    text, which is intentionally conservative rather than hiding a marker.
    """
    raw_lines = text.splitlines()
    masked = [list(line) for line in raw_lines]
    try:
        tokens = tokenize.generate_tokens(io.StringIO(text).readline)
        for token in tokens:
            if token.type not in {tokenize.STRING, tokenize.COMMENT}:
                continue
            (start_row, start_col), (end_row, end_col) = token.start, token.end
            for row in range(start_row - 1, end_row):
                if row < 0 or row >= len(masked):
                    continue
                left = start_col if row == start_row - 1 else 0
                right = end_col if row == end_row - 1 else len(masked[row])
                right = min(right, len(masked[row]))
                for index in range(min(left, right), right):
                    masked[row][index] = " "
    except (tokenize.TokenError, IndentationError, SyntaxError):
        return raw_lines
    return ["".join(line) for line in masked]


def _constant_string(node: ast.AST) -> str | None:
    """Conservatively fold only string expressions with no runtime inputs."""
    if isinstance(node, ast.Constant) and isinstance(node.value, str):
        return node.value
    if isinstance(node, ast.BinOp) and isinstance(node.op, ast.Add):
        left = _constant_string(node.left)
        right = _constant_string(node.right)
        return left + right if left is not None and right is not None else None
    if isinstance(node, ast.JoinedStr):
        chunks: list[str] = []
        for value in node.values:
            if not isinstance(value, ast.Constant) or not isinstance(value.value, str):
                return None
            chunks.append(value.value)
        return "".join(chunks)
    return None


def _python_dynamic_import_findings(relative: str, text: str) -> tuple[Finding, ...]:
    """Catch executable ML imports through common dynamic-import spellings."""
    framework_categories = {
        "torch": "pytorch",
        "pytorch": "pytorch",
        "torchvision": "pytorch",
        "torchaudio": "pytorch",
        "tensorflow": "tensorflow",
        "keras": "tensorflow",
        "jax": "jax",
        "flax": "jax",
        "optax": "jax",
        "haiku": "jax",
        "sklearn": "sklearn",
        "scikit-learn": "sklearn",
    }
    try:
        tree = ast.parse(text)
    except SyntaxError:
        return ()
    findings: list[Finding] = []
    source_lines = text.splitlines()

    direct_import_helpers: set[str] = {"__import__"}
    importlib_aliases: set[str] = {"importlib"}
    builtins_aliases: set[str] = {"builtins"}

    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for alias in node.names:
                if alias.name == "importlib":
                    importlib_aliases.add(alias.asname or alias.name)
                elif alias.name == "builtins":
                    builtins_aliases.add(alias.asname or alias.name)
        elif isinstance(node, ast.ImportFrom):
            if node.module == "importlib":
                for alias in node.names:
                    if alias.name == "import_module":
                        direct_import_helpers.add(alias.asname or alias.name)
            elif node.module == "builtins":
                for alias in node.names:
                    if alias.name == "__import__":
                        direct_import_helpers.add(alias.asname or alias.name)

    for node in ast.walk(tree):
        if not isinstance(node, ast.Call) or not node.args:
            continue
        is_dynamic_import = (
            isinstance(node.func, ast.Name)
            and node.func.id in direct_import_helpers
        ) or (
            isinstance(node.func, ast.Attribute)
            and node.func.attr == "import_module"
            and isinstance(node.func.value, ast.Name)
            and node.func.value.id in importlib_aliases
        ) or (
            isinstance(node.func, ast.Attribute)
            and node.func.attr == "__import__"
            and isinstance(node.func.value, ast.Name)
            and node.func.value.id in builtins_aliases
        )
        if not is_dynamic_import:
            continue
        module_name = _constant_string(node.args[0])
        if module_name is None:
            continue
        root = module_name.strip().lower().split(".", 1)[0]
        category = framework_categories.get(root)
        if category is None:
            continue
        excerpt = (
            source_lines[node.lineno - 1].strip()[:240]
            if node.lineno <= len(source_lines) else ""
        )
        findings.append(Finding(relative, node.lineno, category, excerpt))
    return tuple(findings)


def audit(root: Path = ROOT) -> Audit:
    root = Path(root).resolve()
    scanned: list[str] = []
    findings: list[Finding] = []
    manifest = hashlib.sha256()
    for path in _files(root):
        relative = path.relative_to(root).as_posix()
        payload = path.read_bytes()
        scanned.append(relative)
        _manifest_update(manifest, relative, payload)

        notebook_findings: tuple[Finding, ...] = ()
        if path.suffix.lower() == ".ipynb":
            text, notebook_findings = _notebook_code(relative, payload)
            findings.extend(notebook_findings)
            python_like = True
        else:
            text = payload.decode("utf-8", errors="replace")
            python_like = path.suffix.lower() == ".py"

        source_lines = text.splitlines()
        if python_like:
            executable_lines = _python_executable_lines(text)
        else:
            executable_lines = _dependency_executable_lines(path, text)

        for line_number, line in enumerate(executable_lines, 1):
            for category, pattern in FORBIDDEN_PATTERNS:
                if pattern.search(line):
                    excerpt = (
                        source_lines[line_number - 1].strip()[:240]
                        if line_number <= len(source_lines) else ""
                    )
                    findings.append(Finding(relative, line_number, category, excerpt))
        if python_like and not notebook_findings:
            findings.extend(_python_dynamic_import_findings(relative, text))
    findings.extend(_model_artifact_findings(root))
    findings.extend(_opaque_archive_findings(root))
    findings.extend(_opaque_code_findings(root))
    findings.extend(_symlink_findings(root))
    return Audit(
        tuple(scanned),
        tuple(findings),
        _dependencies(root),
        manifest.hexdigest(),
        _scope_manifest(root),
        _authority_manifest(root),
    )


def require_no_trainable_surface(root: Path = ROOT) -> Audit:
    # Absence of *all* files is not evidence of an application without ML.
    # Require the actual Android app/entrypoint/build surface before issuing
    # a repository-wide not-applicable certificate. audit(root) remains a
    # composable scanner for synthetic fixtures and future changes.
    root = Path(root).resolve()
    missing = [relative for relative in REQUIRED_APPLICATION_FILES
               if not (root / relative).is_file()]
    if missing:
        raise RuntimeError(
            "Forest_Run no-training authority cannot certify an incomplete "
            "Android source tree: " + ", ".join(missing)
        )
    linked_required = [
        relative for relative in REQUIRED_APPLICATION_FILES
        if (root / relative).is_symlink()
    ]
    if linked_required:
        raise RuntimeError(
            "Forest_Run no-training authority requires repository-owned regular "
            "application files, not symlinks: " + ", ".join(linked_required)
        )
    result = audit(root)
    if not any(name.startswith("app/src/main/") and name.endswith(".kt")
               for name in result.scanned_files):
        raise RuntimeError("Forest_Run no-training authority scanned no production Kotlin source")
    if not result.android_dependencies:
        raise RuntimeError("Forest_Run no-training authority found no Android dependencies")
    if not result.complete:
        rendered = "; ".join(
            f"{row.path}:{row.line}:{row.category}" for row in result.findings[:50]
        )
        raise RuntimeError(
            "Forest_Run can no longer be classified as no-training; retained ML/training "
            f"markers were discovered: {rendered}"
        )
    return result
