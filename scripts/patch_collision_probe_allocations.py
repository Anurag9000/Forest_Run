#!/usr/bin/env python3
"""Remove temporary RectF allocations from pure entity collision probes."""

from __future__ import annotations

import re
import textwrap
from pathlib import Path

ROOT = Path("app/src/main/java/com/anurag9000/forestrun/entities")
ENTITY_PATH = ROOT / "Entity.kt"
CHERRY_PATH = ROOT / "trees/CherryBlossom.kt"
VANILLA_PATH = ROOT / "flora/VanillaOrchid.kt"
BAMBOO_PATH = ROOT / "trees/Bamboo.kt"
JACARANDA_PATH = ROOT / "trees/Jacaranda.kt"
WILLOW_PATH = ROOT / "trees/WeepingWillow.kt"
TEST_PATH = Path("app/src/test/java/com/anurag9000/forestrun/entities/EntityCollisionGeometryTest.kt")


def block(value: str) -> str:
    return textwrap.dedent(value).lstrip("\n")


SYMMETRIC_DECLARATION = re.compile(
    r"(?P<indent>[ \t]*)val mercy = RectF\(\s*"
    r"(?P<rect>[A-Za-z_][A-Za-z0-9_]*)\.left - mercyPad,\s*"
    r"(?P=rect)\.top - mercyPad,\s*"
    r"(?P=rect)\.right \+ mercyPad,\s*"
    r"(?P=rect)\.bottom \+ mercyPad\s*"
    r"\)\s*\n"
)
SYMMETRIC_USE = "RectF.intersects(player.hitbox, mercy)"
HELPER_ANCHOR = "    /** Return the current overlap result without mutating state or emitting presentation. */\n"
HELPERS = block(
    '''
        /**
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
        ): Boolean = intersectsExpanded(
            target = target,
            source = source,
            leftPadding = horizontalPadding,
            topPadding = verticalPadding,
            rightPadding = horizontalPadding,
            bottomPadding = verticalPadding
        )

        /** Allocation-free expanded-rectangle probe with independent per-edge padding. */
        protected fun intersectsExpanded(
            target: RectF,
            source: RectF,
            leftPadding: Float,
            topPadding: Float,
            rightPadding: Float,
            bottomPadding: Float
        ): Boolean {
            if (!leftPadding.isFinite() || !topPadding.isFinite() ||
                !rightPadding.isFinite() || !bottomPadding.isFinite()
            ) return false

            val left = leftPadding.coerceAtLeast(0f)
            val top = topPadding.coerceAtLeast(0f)
            val right = rightPadding.coerceAtLeast(0f)
            val bottom = bottomPadding.coerceAtLeast(0f)
            return target.left < source.right + right &&
                source.left - left < target.right &&
                target.top < source.bottom + bottom &&
                source.top - top < target.bottom
        }

    '''
)

CHERRY_OLD = block(
    '''
        val mercyPad = readability.mercyPaddingPx
        val bm = RectF(
            stormVeilRect.left - mercyPad * 0.25f,
            stormVeilRect.top - mercyPad * 0.45f,
            stormVeilRect.right + mercyPad * 0.25f,
            stormVeilRect.bottom + mercyPad * 0.45f
        )
        val tm = RectF(hitbox.left - mercyPad, hitbox.top - mercyPad, hitbox.right + mercyPad, hitbox.bottom + mercyPad)
        if (RectF.intersects(player.hitbox, bm) || RectF.intersects(player.hitbox, tm)) return CollisionResult.MERCY_MISS
    '''
)
CHERRY_NEW = block(
    '''
        val mercyPad = readability.mercyPaddingPx
        if (
            intersectsExpanded(
                player.hitbox,
                stormVeilRect,
                horizontalPadding = mercyPad * 0.25f,
                verticalPadding = mercyPad * 0.45f
            ) || intersectsExpanded(player.hitbox, hitbox, mercyPad)
        ) return CollisionResult.MERCY_MISS
    '''
)

VANILLA_OLD = block(
    '''
        val mercyPad = readability.mercyPaddingPx
        val bm = RectF(
            bottomHitbox.left - mercyPad,
            bottomHitbox.top - mercyPad * 0.45f,
            bottomHitbox.right + mercyPad * 0.50f,
            bottomHitbox.bottom + mercyPad
        )
        val tm = RectF(
            topHitbox.left - mercyPad * 0.50f,
            topHitbox.top - mercyPad,
            topHitbox.right + mercyPad,
            topHitbox.bottom + mercyPad * 0.45f
        )
        if (RectF.intersects(player.hitbox, bm) || RectF.intersects(player.hitbox, tm)) return CollisionResult.MERCY_MISS
    '''
)
VANILLA_NEW = block(
    '''
        val mercyPad = readability.mercyPaddingPx
        if (
            intersectsExpanded(
                player.hitbox,
                bottomHitbox,
                leftPadding = mercyPad,
                topPadding = mercyPad * 0.45f,
                rightPadding = mercyPad * 0.50f,
                bottomPadding = mercyPad
            ) || intersectsExpanded(
                player.hitbox,
                topHitbox,
                leftPadding = mercyPad * 0.50f,
                topPadding = mercyPad,
                rightPadding = mercyPad,
                bottomPadding = mercyPad * 0.45f
            )
        ) return CollisionResult.MERCY_MISS
    '''
)

BAMBOO_OLD = block(
    '''
            val mercyPad = readability.mercyPaddingPx * 0.5f
            val tm = RectF(topHitboxes[i].left - mercyPad, topHitboxes[i].top, topHitboxes[i].right + mercyPad, topHitboxes[i].bottom + mercyPad)
            val bm = RectF(bottomHitboxes[i].left - mercyPad, bottomHitboxes[i].top - mercyPad, bottomHitboxes[i].right + mercyPad, bottomHitboxes[i].bottom)
            if (RectF.intersects(player.hitbox, tm) || RectF.intersects(player.hitbox, bm)) nearMiss = true
    '''
)
BAMBOO_NEW = block(
    '''
            val mercyPad = readability.mercyPaddingPx * 0.5f
            if (
                intersectsExpanded(
                    player.hitbox,
                    topHitboxes[i],
                    leftPadding = mercyPad,
                    topPadding = 0f,
                    rightPadding = mercyPad,
                    bottomPadding = mercyPad
                ) || intersectsExpanded(
                    player.hitbox,
                    bottomHitboxes[i],
                    leftPadding = mercyPad,
                    topPadding = mercyPad,
                    rightPadding = mercyPad,
                    bottomPadding = 0f
                )
            ) nearMiss = true
    '''
)

JACARANDA_OLD = block(
    '''
        val mercyPad = readability.mercyPaddingPx
        val bm = RectF(
            branchHitbox.left - mercyPad,
            branchHitbox.top,
            branchHitbox.right + mercyPad,
            branchHitbox.bottom + mercyPad * 0.40f
        )
        val tm = RectF(hitbox.left - mercyPad, hitbox.top - mercyPad, hitbox.right + mercyPad, hitbox.bottom + mercyPad)
        if (RectF.intersects(player.hitbox, bm) || RectF.intersects(player.hitbox, tm)) return CollisionResult.MERCY_MISS
    '''
)
JACARANDA_NEW = block(
    '''
        val mercyPad = readability.mercyPaddingPx
        if (
            intersectsExpanded(
                player.hitbox,
                branchHitbox,
                leftPadding = mercyPad,
                topPadding = 0f,
                rightPadding = mercyPad,
                bottomPadding = mercyPad * 0.40f
            ) || intersectsExpanded(player.hitbox, hitbox, mercyPad)
        ) return CollisionResult.MERCY_MISS
    '''
)

WILLOW_OLD = block(
    '''
        val mercyPad = readability.mercyPaddingPx
        val cm = RectF(
            curtainHitbox.left - mercyPad,
            curtainHitbox.top,
            curtainHitbox.right + mercyPad,
            curtainHitbox.bottom + mercyPad * 0.35f
        )
        val tm = RectF(hitbox.left - mercyPad, hitbox.top - mercyPad, hitbox.right + mercyPad, hitbox.bottom + mercyPad)
        if (RectF.intersects(player.hitbox, cm) || RectF.intersects(player.hitbox, tm)) return CollisionResult.MERCY_MISS
    '''
)
WILLOW_NEW = block(
    '''
        val mercyPad = readability.mercyPaddingPx
        if (
            intersectsExpanded(
                player.hitbox,
                curtainHitbox,
                leftPadding = mercyPad,
                topPadding = 0f,
                rightPadding = mercyPad,
                bottomPadding = mercyPad * 0.35f
            ) || intersectsExpanded(player.hitbox, hitbox, mercyPad)
        ) return CollisionResult.MERCY_MISS
    '''
)

TEST_CONTENT = block(
    '''
        package com.anurag9000.forestrun.entities

        import android.content.Context
        import android.graphics.Canvas
        import android.graphics.RectF
        import androidx.test.core.app.ApplicationProvider
        import com.anurag9000.forestrun.engine.GameStateManager
        import org.junit.Assert.assertEquals
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

                fun intersects(
                    target: RectF,
                    source: RectF,
                    leftPadding: Float,
                    topPadding: Float,
                    rightPadding: Float,
                    bottomPadding: Float
                ): Boolean = intersectsExpanded(
                    target,
                    source,
                    leftPadding,
                    topPadding,
                    rightPadding,
                    bottomPadding
                )
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
            fun `axis expansion preserves Cherry gust geometry`() {
                val verticalNearMiss = RectF(25f, 12f, 35f, 18f)
                assertFalse(probe.intersects(verticalNearMiss, source, 3f, 1f))
                assertTrue(probe.intersects(verticalNearMiss, source, 3f, 3f))
            }

            @Test
            fun `per edge helper is equivalent to an explicitly expanded RectF`() {
                val targets = listOf(
                    RectF(10f, 10f, 18f, 18f),
                    RectF(15f, 24f, 21f, 32f),
                    RectF(39f, 25f, 48f, 35f),
                    RectF(24f, 40f, 36f, 48f),
                    RectF(45f, 45f, 50f, 50f)
                )
                val paddings = listOf(
                    floatArrayOf(0f, 0f, 0f, 0f),
                    floatArrayOf(5f, 0f, 2f, 8f),
                    floatArrayOf(1.5f, 7f, 9f, 0.5f)
                )

                for (target in targets) {
                    for (padding in paddings) {
                        val expanded = RectF(
                            source.left - padding[0],
                            source.top - padding[1],
                            source.right + padding[2],
                            source.bottom + padding[3]
                        )
                        assertEquals(
                            RectF.intersects(target, expanded),
                            probe.intersects(
                                target,
                                source,
                                leftPadding = padding[0],
                                topPadding = padding[1],
                                rightPadding = padding[2],
                                bottomPadding = padding[3]
                            )
                        )
                    }
                }
            }
        }
    '''
)


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
        matches = list(SYMMETRIC_DECLARATION.finditer(text))
        for match in reversed(matches):
            use_index = text.find(SYMMETRIC_USE, match.end())
            if use_index < 0:
                raise RuntimeError(f"{path}: mercy rectangle has no matching collision use")
            next_declaration = SYMMETRIC_DECLARATION.search(text, match.end())
            if next_declaration is not None and next_declaration.start() < use_index:
                raise RuntimeError(f"{path}: ambiguous mercy rectangle use")
            rect_name = match.group("rect")
            replacement = f"intersectsExpanded(player.hitbox, {rect_name}, mercyPad)"
            text = text[:use_index] + replacement + text[use_index + len(SYMMETRIC_USE):]
            text = text[:match.start()] + text[match.end():]
            total += 1
        path.write_text(text, encoding="utf-8")
    return total


def patch_custom_mercy_rectangles() -> None:
    replace_once(CHERRY_PATH, CHERRY_OLD, CHERRY_NEW, "Cherry asymmetric mercy probes")
    replace_once(VANILLA_PATH, VANILLA_OLD, VANILLA_NEW, "Vanilla asymmetric mercy probes")
    replace_once(BAMBOO_PATH, BAMBOO_OLD, BAMBOO_NEW, "Bamboo one-sided mercy probes")
    replace_once(JACARANDA_PATH, JACARANDA_OLD, JACARANDA_NEW, "Jacaranda branch mercy probe")
    replace_once(WILLOW_PATH, WILLOW_OLD, WILLOW_NEW, "Willow curtain mercy probe")


def write_tests() -> None:
    if TEST_PATH.exists():
        raise RuntimeError(f"{TEST_PATH}: test already exists")
    TEST_PATH.parent.mkdir(parents=True, exist_ok=True)
    TEST_PATH.write_text(TEST_CONTENT, encoding="utf-8")


def verify(symmetric_replacements: int) -> None:
    if symmetric_replacements != 13:
        raise RuntimeError(
            f"Expected 13 symmetric mercy allocations, replaced {symmetric_replacements}"
        )

    forbidden = (
        "val mercy = RectF",
        "val bm = RectF",
        "val tm = RectF",
        "val cm = RectF",
    )
    remaining: list[str] = []
    for path in sorted(ROOT.rglob("*.kt")):
        text = path.read_text(encoding="utf-8")
        if any(marker in text for marker in forbidden):
            remaining.append(str(path))
    if remaining:
        raise RuntimeError(f"Temporary collision rectangles remain: {remaining}")


def main() -> None:
    patch_entity_base()
    symmetric_replacements = patch_symmetric_mercy_rectangles()
    patch_custom_mercy_rectangles()
    write_tests()
    verify(symmetric_replacements)
    print(
        "Removed 13 symmetric mercy rectangles and 9 custom temporary "
        "collision rectangles"
    )


if __name__ == "__main__":
    main()
