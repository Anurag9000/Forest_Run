#!/usr/bin/env python3
"""Keep decorative rooted sway out of axis-aligned collision geometry."""

from pathlib import Path

ROOT = Path("app/src/main/java/com/yourname/forest_run/entities")


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: {label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def patch_simple_flora(
    relative_path: str,
    pulse_declaration: str,
    update_old: str,
    update_new: str,
    draw_old: str,
) -> None:
    path = ROOT / relative_path
    replace_once(
        path,
        pulse_declaration,
        pulse_declaration + "    private var currentSway = 0f\n",
        "add current visual sway",
    )
    replace_once(path, update_old, update_new, "root collision geometry")
    replace_once(path, draw_old, "        val sway = currentSway\n", "draw stored sway")


def main() -> None:
    patch_simple_flora(
        "flora/Cactus.kt",
        "    private var warningPulse = 0f\n",
        """        val sway = swayComponent?.getOffset(deltaTime) ?: 0f\n        hitbox.offsetTo(x + insetX + sway, y + insetY)\n""",
        """        currentSway = swayComponent?.getOffset(deltaTime) ?: 0f\n        hitbox.offsetTo(x + insetX, y + insetY)\n""",
        "        val sway = swayComponent?.getOffset(0f) ?: 0f\n",
    )

    patch_simple_flora(
        "flora/LilyOfValley.kt",
        "    private var trapPulse = 0f\n",
        """        val sway = swayComponent?.getOffset(deltaTime) ?: 0f\n        hitbox.offsetTo(x + hitInsetX + sway, y + hitTopY)\n""",
        """        currentSway = swayComponent?.getOffset(deltaTime) ?: 0f\n        hitbox.offsetTo(x + hitInsetX, y + hitTopY)\n""",
        "        val sway = swayComponent?.getOffset(0f) ?: 0f\n",
    )

    patch_simple_flora(
        "flora/Hyacinth.kt",
        "    private var rhythmPulse = 0f\n",
        """        val sway = swayComponent?.getOffset(deltaTime) ?: 0f\n        hitbox.offsetTo(x + hitInsetX + sway, y + hitTopY)\n""",
        """        currentSway = swayComponent?.getOffset(deltaTime) ?: 0f\n        hitbox.offsetTo(x + hitInsetX, y + hitTopY)\n""",
        "        val sway = swayComponent?.getOffset(0f) ?: 0f\n",
    )

    patch_simple_flora(
        "flora/Eucalyptus.kt",
        "    private var gustPulse = 0f\n",
        """        val sway = swayComponent?.getOffset(deltaTime) ?: 0f\n        hitbox.offsetTo(x + hitInsetX + sway, y + hitTopY)\n""",
        """        currentSway = swayComponent?.getOffset(deltaTime) ?: 0f\n        hitbox.offsetTo(x + hitInsetX, y + hitTopY)\n""",
        "        val sway = swayComponent?.getOffset(0f) ?: 0f\n",
    )

    orchid = ROOT / "flora/VanillaOrchid.kt"
    replace_once(
        orchid,
        """    private val bottomRect   = RectF()\n    private val topRect      = RectF()\n""",
        """    private val bottomRect   = RectF()\n    private val topRect      = RectF()\n    private var currentSway  = 0f\n""",
        "add Orchid visual sway",
    )
    replace_once(orchid, "        updateCollisionGeometry(sway = 0f)\n", "        updateCollisionGeometry()\n", "root Orchid init")
    replace_once(
        orchid,
        """        val sway = swayComponent?.getOffset(deltaTime) ?: 0f\n        updateCollisionGeometry(sway)\n""",
        """        currentSway = swayComponent?.getOffset(deltaTime) ?: 0f\n        updateCollisionGeometry()\n""",
        "root Orchid update",
    )
    replace_once(orchid, "        val sway = swayComponent?.getOffset(0f) ?: 0f\n", "        val sway = currentSway\n", "draw Orchid stored sway")
    replace_once(orchid, "    private fun updateCollisionGeometry(sway: Float) {\n", "    private fun updateCollisionGeometry() {\n", "remove Orchid sway argument")
    replace_once(
        orchid,
        """            x + floraWidth * 0.14f + sway * 0.75f,\n            groundY - floraHeight * 0.22f,\n            x + floraWidth * 0.58f + sway * 0.75f,\n""",
        """            x + floraWidth * 0.14f,\n            groundY - floraHeight * 0.22f,\n            x + floraWidth * 0.58f,\n""",
        "root Orchid lower segment",
    )
    replace_once(
        orchid,
        """            x + floraWidth * 0.38f + sway * 0.35f,\n            y,\n            x + floraWidth * 0.88f + sway * 0.35f,\n""",
        """            x + floraWidth * 0.38f,\n            y,\n            x + floraWidth * 0.88f,\n""",
        "root Orchid upper segment",
    )

    willow = ROOT / "trees/WeepingWillow.kt"
    replace_once(willow, "    private var curtainPulse = 0f\n", "    private var curtainPulse = 0f\n    private var currentSway = 0f\n", "add Willow visual sway")
    replace_once(willow, "        updateGeometry(sway = 0f)\n", "        updateGeometry()\n", "root Willow init")
    replace_once(
        willow,
        """        val sway = swayComponent?.getOffset(deltaTime) ?: 0f\n        updateGeometry(sway)\n""",
        """        currentSway = swayComponent?.getOffset(deltaTime) ?: 0f\n        updateGeometry()\n""",
        "root Willow update",
    )
    replace_once(willow, "        val sway = swayComponent?.getOffset(0f) ?: 0f\n", "        val sway = currentSway\n", "draw Willow stored sway")
    replace_once(willow, "    private fun updateGeometry(sway: Float) {\n", "    private fun updateGeometry() {\n", "remove Willow sway argument")
    replace_once(
        willow,
        """            x + treeWidth * 0.06f + sway,\n            curtainTop,\n            x + treeWidth * 0.94f + sway,\n""",
        """            x + treeWidth * 0.06f,\n            curtainTop,\n            x + treeWidth * 0.94f,\n""",
        "root Willow curtain",
    )

    jacaranda = ROOT / "trees/Jacaranda.kt"
    replace_once(jacaranda, "    private var canopyPulse = 0f\n", "    private var canopyPulse = 0f\n    private var currentSway = 0f\n", "add Jacaranda visual sway")
    replace_once(jacaranda, "        updateGeometry(sway = 0f)\n", "        updateGeometry()\n", "root Jacaranda init")
    replace_once(
        jacaranda,
        """        val sway = swayComponent?.getOffset(deltaTime) ?: 0f\n        updateGeometry(sway)\n""",
        """        currentSway = swayComponent?.getOffset(deltaTime) ?: 0f\n        updateGeometry()\n""",
        "root Jacaranda update",
    )
    replace_once(jacaranda, "        val sway = swayComponent?.getOffset(0f) ?: 0f\n", "        val sway = currentSway\n", "draw Jacaranda stored sway")
    replace_once(jacaranda, "    private fun updateGeometry(sway: Float) {\n", "    private fun updateGeometry() {\n", "remove Jacaranda sway argument")
    replace_once(
        jacaranda,
        """            x + treeWidth * 0.06f + sway,\n            branchTop,\n            x + treeWidth * 0.94f + sway,\n""",
        """            x + treeWidth * 0.06f,\n            branchTop,\n            x + treeWidth * 0.94f,\n""",
        "root Jacaranda branch",
    )

    cherry = ROOT / "trees/CherryBlossom.kt"
    replace_once(cherry, "    private var gustPulse = 0f\n", "    private var gustPulse = 0f\n    private var currentSway = 0f\n", "add Cherry visual sway")
    replace_once(cherry, "        updateGeometry(sway = 0f)\n", "        updateGeometry()\n", "root Cherry init")
    replace_once(
        cherry,
        """        val sway = swayComponent?.getOffset(deltaTime) ?: 0f\n        updateGeometry(sway)\n""",
        """        currentSway = swayComponent?.getOffset(deltaTime) ?: 0f\n        updateGeometry()\n""",
        "root Cherry update",
    )
    replace_once(cherry, "        val sway = swayComponent?.getOffset(0f) ?: 0f\n", "        val sway = currentSway\n", "draw Cherry stored sway")
    replace_once(cherry, "    private fun updateGeometry(sway: Float) {\n", "    private fun updateGeometry() {\n", "remove Cherry sway argument")
    replace_once(
        cherry,
        """            x + treeWidth * 0.14f + sway,\n            branchHeightHigh + treeHeight * 0.04f,\n            x + treeWidth * 0.86f + sway,\n""",
        """            x + treeWidth * 0.14f,\n            branchHeightHigh + treeHeight * 0.04f,\n            x + treeWidth * 0.86f,\n""",
        "root Cherry branch",
    )
    replace_once(
        cherry,
        """            x + treeWidth * 0.06f + sway,\n            branchHeightHigh - readability.stagingPaddingPx * 0.25f,\n            x + treeWidth * 0.94f + sway,\n""",
        """            x + treeWidth * 0.06f,\n            branchHeightHigh - readability.stagingPaddingPx * 0.25f,\n            x + treeWidth * 0.94f,\n""",
        "root Cherry storm veil",
    )


if __name__ == "__main__":
    main()
