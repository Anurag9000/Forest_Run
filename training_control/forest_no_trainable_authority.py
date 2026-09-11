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
import re
from typing import Iterable

ROOT = Path(__file__).resolve().parents[1]

SOURCE_SUFFIXES = {
    ".kt", ".kts", ".java", ".py", ".js", ".ts", ".tsx", ".jsx",
    ".gradle", ".toml", ".yaml", ".yml", ".json",
}
SKIP_PARTS = {
    ".git", ".gradle", ".idea", "build", "docs", "Final_Assets (2)",
    ".training_control", "training_control",
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
    ("ml-dependency", re.compile(r"implementation\s*\([^\n]*(tensorflow|pytorch|onnxruntime|mlkit)", re.I)),
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
            "complete": self.complete,
            "wildcard_training_exemptions": False,
            "source_configuration_only": True,
            "execution_claim_emitted": False,
        }


def _files(root: Path = ROOT) -> Iterable[Path]:
    for path in sorted(root.rglob("*")):
        if not path.is_file() or path.name == "run_all_training.py":
            continue
        relative = path.relative_to(root)
        if any(part in SKIP_PARTS for part in relative.parts):
            continue
        if path.suffix.lower() in SOURCE_SUFFIXES or path.name in {"build.gradle.kts", "settings.gradle.kts", "gradle.properties"}:
            yield path


def _dependencies(root: Path = ROOT) -> tuple[str, ...]:
    path = root / "app" / "build.gradle.kts"
    if not path.is_file():
        return ()
    dependencies: list[str] = []
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        stripped = line.strip()
        if stripped.startswith(("implementation(", "api(", "compileOnly(")):
            dependencies.append(stripped)
    return tuple(dependencies)


def audit(root: Path = ROOT) -> Audit:
    scanned: list[str] = []
    findings: list[Finding] = []
    for path in _files(root):
        relative = path.relative_to(root).as_posix()
        scanned.append(relative)
        text = path.read_text(encoding="utf-8", errors="replace")
        for line_number, line in enumerate(text.splitlines(), 1):
            for category, pattern in FORBIDDEN_PATTERNS:
                if pattern.search(line):
                    findings.append(
                        Finding(relative, line_number, category, line.strip()[:240])
                    )
    return Audit(tuple(scanned), tuple(findings), _dependencies(root))


def require_no_trainable_surface(root: Path = ROOT) -> Audit:
    result = audit(root)
    if not result.complete:
        rendered = "; ".join(
            f"{row.path}:{row.line}:{row.category}" for row in result.findings[:50]
        )
        raise RuntimeError(
            "Forest_Run can no longer be classified as no-training; retained ML/training "
            f"markers were discovered: {rendered}"
        )
    return result
