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
import os
import re
import tokenize
from typing import Iterable

ROOT = Path(__file__).resolve().parents[1]

SOURCE_SUFFIXES = {
    ".kt", ".kts", ".java", ".py", ".js", ".ts", ".tsx", ".jsx",
    ".gradle", ".toml", ".yaml", ".yml", ".json", ".xml",
    ".sh", ".bash", ".bat", ".cmd", ".ps1",
}
MODEL_ARTIFACT_SUFFIXES = {
    ".tflite", ".onnx", ".ort", ".pt", ".pth", ".ckpt", ".safetensors",
    ".keras", ".mlmodel", ".mlpackage",
}
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

SKIP_PARTS = {
    ".git", ".gradle", ".idea", "build", "docs", "Final_Assets (2)",
    ".training_control", "training_control", "artifacts",
    ".venv", "venv", "node_modules", "__pycache__", ".pytest_cache",
}
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
        # Only the root audit entrypoint is exempt. A newly introduced nested
        # script with the same filename must still be inspected for optimizers.
        if relative.as_posix() == "run_all_training.py":
            continue
        if any(part in SKIP_PARTS for part in relative.parts):
            continue
        if path.suffix.lower() in SOURCE_SUFFIXES or path.name in {"build.gradle.kts", "settings.gradle.kts", "gradle.properties"}:
            yield path


def _manifest_update(digest: "hashlib._Hash", relative: str, payload: bytes) -> None:
    """Bind exactly the same bytes that are parsed by the source scanner."""
    encoded = relative.encode("utf-8")
    digest.update(len(encoded).to_bytes(8, "big"))
    digest.update(encoded)
    digest.update(len(payload).to_bytes(8, "big"))
    digest.update(payload)


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


def _model_artifact_findings(root: Path = ROOT) -> tuple[Finding, ...]:
    findings: list[Finding] = []
    for path in sorted(root.rglob("*")):
        if not path.is_file():
            continue
        relative = path.relative_to(root)
        if any(part in SKIP_PARTS for part in relative.parts):
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


def _python_dynamic_import_findings(relative: str, text: str) -> tuple[Finding, ...]:
    """Catch ML frameworks loaded from string literals via import helpers."""
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
    for node in ast.walk(tree):
        if not isinstance(node, ast.Call) or not node.args:
            continue
        is_dynamic_import = (
            isinstance(node.func, ast.Name) and node.func.id == "__import__"
        ) or (
            isinstance(node.func, ast.Attribute)
            and node.func.attr == "import_module"
            and isinstance(node.func.value, ast.Name)
            and node.func.value.id == "importlib"
        )
        if not is_dynamic_import:
            continue
        argument = node.args[0]
        if not isinstance(argument, ast.Constant) or not isinstance(argument.value, str):
            continue
        root = argument.value.strip().lower().split(".", 1)[0]
        category = framework_categories.get(root)
        if category is None:
            continue
        excerpt = source_lines[node.lineno - 1].strip()[:240] if node.lineno <= len(source_lines) else ""
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
        text = payload.decode("utf-8", errors="replace")
        source_lines = text.splitlines()
        executable_lines = (
            _python_executable_lines(text)
            if path.suffix.lower() == ".py"
            else source_lines
        )
        for line_number, line in enumerate(executable_lines, 1):
            for category, pattern in FORBIDDEN_PATTERNS:
                if pattern.search(line):
                    excerpt = source_lines[line_number - 1].strip()[:240]
                    findings.append(Finding(relative, line_number, category, excerpt))
        if path.suffix.lower() == ".py":
            findings.extend(_python_dynamic_import_findings(relative, text))
    findings.extend(_model_artifact_findings(root))
    findings.extend(_symlink_findings(root))
    return Audit(
        tuple(scanned),
        tuple(findings),
        _dependencies(root),
        manifest.hexdigest(),
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
