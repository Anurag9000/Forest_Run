#!/usr/bin/env python3
"""Avoid referencing the later ghost constant during object initialization."""

from pathlib import Path

path = Path("app/src/main/java/com/anurag9000/forestrun/engine/SaveManager.kt")
text = path.read_text(encoding="utf-8")
old = '    private var activeGhostFilename: String = GHOST_FILENAME\n'
new = '    private var activeGhostFilename: String = "ghost_run.bin"\n'
if text.count(old) != 1:
    raise RuntimeError(f"SaveManager active ghost initializer: expected one match, found {text.count(old)}")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
