#!/usr/bin/env python3
"""Remove temporary RectF allocations from pure entity collision probes."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path("app/src/main/java/com/anurag9000/forestrun/entities")
ENTITY_PATH = ROOT / "Entity.kt"
CHERRY_PATH = ROOT / "trees/CherryBlossom.kt"
TEST_PATH = Path("app/src/test/java/com/anurag9000/forestrun/entities/EntityCollisionGeometryTest.kt")

DECLARATION = re.compile(
    r"(?P<indent>[ \t]*)val mercy = RectF\(\s*"
    r"(?P<rect>[A-Za-z_][A-Za-z0-9_]*)\.left - mercyPad,\s*"
    r"(?P=rect)\.top - mercyPad,\s*"
    r"(?P=rect)\.right \+ mercyPad,\s*"
    r"(?P=rect)\.bottom \+ mercyPad\s*"
    r"\)\s*\n"
)
USE = "RectF.intersects(player.hitbox, mercy)"

HELPER_ANCHOR = "    /** Return the current overlap result without mutating state or emitting presentation. */\n"
HELPERS = '''    /**
     * Allocation-free equivalent of intersecting [target] with a symmetrically
     * expanded copy of [source]. Invalid padding never creates an encounter.
     */
    protected fun intersectsExpanded(target: RectF, source: RectF, padding: Float): Boolean =
        intersectsExpanded(target, source, padding, padding)

    /** Allocation-free expanded-rectangle probe with independent axis padding. */
    protected fun intersectsExpanded(
        target: RectF,
        source: RectF,
        horizontalPadding: Float,
        verticalPadding: Float
    ): Boolean {
        if (!horizontalPadding.isFinite() || !verticalPadding.isFinite()) return false
        val padX = horizontalPadding.coerceAtLeast(0f)
        val padY = verticalPadding.coerceAtLeast(0f)
        return target.left < source.right + padX &&
            source.left - padX < target.right &&
            target.top < source.bottom + padY &&
            source.top - padY < target.bottom
    }

'''

CHERRY_OLD = '''        val mercyPad = readability.mercyPaddingPx
        val bm = RectF(
            stormVeilRect.left - mercyPad * 0.25f,
            stormVeilRect.top - mercyPad * 0.45f,
            stormVeilRect.right + mercyPad * 0.25f,
            stormVeilRect.bottom + mercyPad * 0.45f
        )
        val tm = RectF(hitbox.left - mercyPad, hitbox.top - mercyPad, hitbox.right + mercyPad, hitbox.bottom + mercyPad)
        if (RectF.intersects(player.hitbox, bm) || RectF.intersects(player.hitbox, tm)) return CollisionResult.MERCY_MISS
'''
CHERRY_NEW = '''        val mercyPad = readability.mercyPaddingPx
        if (
            intersectsExpanded(
                player.hitbox,
                stormVeilRect,
                horizontalPadding = mercyPad * 0.25f,
                verticalPadding = mercyPad * 0.45f
            ) || intersectsExpanded(player.hitbox, hitbox, mercyPad)
        ) return CollisionResult.MERCY_MISS
'''

TEST_CONTENT = '''package com.anurag9000.forestrun.entities

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.GameStateManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EntityCollisionGeometryTest {
    private class ProbeEntity(context: Context) : Entity(context) {
        override fun update(deltaTime: Float, scrollSpeed: Float) = Unit
        override fun draw(canvas: Canvas) = Unit
        override fun onCollision(player: Player, gameState: GameStateManager) = CollisionResult.NONE

        fun intersects(target: RectF, source: RectF, padding: Float): Boolean =
            intersectsExpanded(target, source, padding)

        fun intersects(
            target: RectF,
            source: RectF,
            horizontalPadding: Float,
            verticalPadding: Float
        ): Boolean = intersectsExpanded(target, source, horizontalPadding, verticalPadding)
    }

    private val probe = ProbeEntity(ApplicationProvider.getApplicationContext())
    private val source = RectF(20f, 20f, 40f, 40f)

    @Test
    fun `zero padding preserves strict RectF overlap semantics`() {
        assertTrue(probe.intersects(RectF(39f, 25f, 45f, 35f), source, 0f))
        assertFalse(probe.intersects(RectF(40f, 25f, 45f, 35f), source, 0f))
    }

    @Test
    fun `symmetric mercy padding admits a near miss without allocation`() {
        val nearMiss = RectF(44f, 25f, 50f, 35f)
        assertFalse(probe.intersects(nearMiss, source, 0f))
        assertTrue(probe.intersects(nearMiss, source, 5f))
    }

    @Test
    fun `expanded edge contact remains non intersecting`() {
        assertFalse(probe.intersects(RectF(45f, 25f, 50f, 35f), source, 5f))
    }

    @Test
    fun `negative padding is clamped and non finite padding is rejected`() {
        val nearMiss = RectF(41f, 25f, 45f, 35f)
        assertFalse(probe.intersects(nearMiss, source, -10f))
        assertFalse(probe.intersects(nearMiss, source, Float.NaN))
        assertFalse(probe.intersects(nearMiss, source, Float.POSITIVE_INFINITY))
    }

    @Test
    fun `asymmetric expansion preserves Cherry gust geometry`() {
        val verticalNearMiss = RectF(25f, 12f, 35f, 18f)
        assertFalse(probe.intersects(verticalNearMiss, source, 3f, 1f))
        assertTrue(probe.intersects(verticalNearMiss, source, 3f, 3f))
    }
}
'''


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: {label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def patch_entity_base() -> None:
    text = ENTITY_PATH.read_text(encoding="utf-8")
    if "protected fun intersectsExpanded" in text:
        raise RuntimeError("Entity collision helper already exists")
    if text.count(HELPER_ANCHOR) != 1:
        raise RuntimeError("Entity helper anchor missing or ambiguous")
    ENTITY_PATH.write_text(text.replace(HELPER_ANCHOR, HELPERS + HELPER_ANCHOR, 1), encoding="utf-8")


def patch_symmetric_mercy_rectangles() -> int:
    total = 0
    for path in sorted(ROOT.rglob("*.kt")):
        if path == ENTITY_PATH:
            continue
        text = path.read_text(encoding="utf-8")
        matches = list(DECLARATION.finditer(text))
        for match in reversed(matches):
            use_index = text.find(USE, match.end())
            if use_index < 0:
                raise RuntimeError(f"{path}: mercy rectangle has no matching collision use")
            next_declaration = DECLARATION.search(text, match.end())
            if next_declaration is not None and next_declaration.start() < use_index:
                raise RuntimeError(f"{path}: ambiguous mercy rectangle use")
            rect_name = match.group("rect")
            replacement = f"intersectsExpanded(player.hitbox, {rect_name}, mercyPad)"
            text = text[:use_index] + replacement + text[use_index + len(USE):]
            text = text[:match.start()] + text[match.end():]
            total += 1
        path.write_text(text, encoding="utf-8")
    return total


def patch_cherry() -> None:
    replace_once(CHERRY_PATH, CHERRY_OLD, CHERRY_NEW, "asymmetric Cherry mercy rectangles")


def write_tests() -> None:
    if TEST_PATH.exists():
        raise RuntimeError(f"{TEST_PATH}: test already exists")
    TEST_PATH.parent.mkdir(parents=True, exist_ok=True)
    TEST_PATH.write_text(TEST_CONTENT, encoding="utf-8")


def verify(replacements: int) -> None:
    if replacements != 10:
        raise RuntimeError(f"Expected 10 symmetric mercy allocations, replaced {replacements}")
    remaining = []
    for path in sorted(ROOT.rglob("*.kt")):
        text = path.read_text(encoding="utf-8")
        if "val mercy = RectF" in text or "val bm = RectF" in text or "val tm = RectF" in text:
            remaining.append(str(path))
    if remaining:
        raise RuntimeError(f"Temporary collision rectangles remain: {remaining}")


def main() -> None:
    patch_entity_base()
    replacements = patch_symmetric_mercy_rectangles()
    patch_cherry()
    write_tests()
    verify(replacements)
    print(f"Replaced {replacements + 2} temporary collision rectangles")


if __name__ == "__main__":
    main()
