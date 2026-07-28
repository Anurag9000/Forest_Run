#!/usr/bin/env python3
"""Remove per-frame GhostPlayer visibility allocations without changing behavior."""

from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: {label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def main() -> None:
    ghost_player = Path(
        "app/src/main/java/com/anurag9000/forestrun/systems/GhostPlayer.kt"
    )
    replace_once(
        ghost_player,
        '''    data class VisibilityContext(
        val livePlayerX: Float,
        val livePlayerY: Float,
        val livePlayerWidth: Float,
        val livePlayerHeight: Float,
        val nearbyHazardCount: Int,
        val nearestHazardDistancePx: Float
    )
''',
        '''    class VisibilityContext(
        var livePlayerX: Float,
        var livePlayerY: Float,
        var livePlayerWidth: Float,
        var livePlayerHeight: Float,
        var nearbyHazardCount: Int,
        var nearestHazardDistancePx: Float
    ) {
        fun set(
            livePlayerX: Float,
            livePlayerY: Float,
            livePlayerWidth: Float,
            livePlayerHeight: Float,
            nearbyHazardCount: Int,
            nearestHazardDistancePx: Float
        ): VisibilityContext {
            this.livePlayerX = livePlayerX
            this.livePlayerY = livePlayerY
            this.livePlayerWidth = livePlayerWidth
            this.livePlayerHeight = livePlayerHeight
            this.nearbyHazardCount = nearbyHazardCount
            this.nearestHazardDistancePx = nearestHazardDistancePx
            return this
        }
    }
''',
        "mutable visibility context",
    )

    game_view = Path(
        "app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt"
    )
    replace_once(
        game_view,
        '''    private val ghostRecorder = GhostRecorder()
    private val ghostPlayer   = GhostPlayer()
''',
        '''    private val ghostRecorder = GhostRecorder()
    private val ghostPlayer   = GhostPlayer()
    private val ghostHazardFocusRect = RectF()
    private val reusableGhostVisibilityContext = GhostPlayer.VisibilityContext(
        livePlayerX = 0f,
        livePlayerY = 0f,
        livePlayerWidth = Player.BASE_WIDTH,
        livePlayerHeight = Player.BASE_HEIGHT,
        nearbyHazardCount = 0,
        nearestHazardDistancePx = Float.POSITIVE_INFINITY
    )
''',
        "reusable ghost geometry",
    )
    replace_once(
        game_view,
        '''        if (::entityManager.isInitialized) {
            val focusRect = RectF(
                liveHitbox.left - Player.BASE_WIDTH * 1.4f,
                liveHitbox.top - Player.BASE_HEIGHT * 0.9f,
                liveHitbox.right + Player.BASE_WIDTH * 4.8f,
                liveHitbox.bottom + Player.BASE_HEIGHT * 0.9f
            )
            entityManager.activeEntities.forEach { entity ->
                if (!entity.isActive || entity.hitbox.isEmpty) return@forEach
                if (!RectF.intersects(focusRect, entity.hitbox)) return@forEach
''',
        '''        if (::entityManager.isInitialized) {
            ghostHazardFocusRect.set(
                liveHitbox.left - Player.BASE_WIDTH * 1.4f,
                liveHitbox.top - Player.BASE_HEIGHT * 0.9f,
                liveHitbox.right + Player.BASE_WIDTH * 4.8f,
                liveHitbox.bottom + Player.BASE_HEIGHT * 0.9f
            )
            entityManager.activeEntities.forEach { entity ->
                if (!entity.isActive || entity.hitbox.isEmpty) return@forEach
                if (!RectF.intersects(ghostHazardFocusRect, entity.hitbox)) return@forEach
''',
        "reusable hazard focus rectangle",
    )
    replace_once(
        game_view,
        '''        return GhostPlayer.VisibilityContext(
            livePlayerX = player.x,
            livePlayerY = player.y,
            livePlayerWidth = player.currentWidth,
            livePlayerHeight = player.currentHeight,
            nearbyHazardCount = nearbyHazardCount,
            nearestHazardDistancePx = nearestHazardDistancePx
        )
''',
        '''        return reusableGhostVisibilityContext.set(
            livePlayerX = player.x,
            livePlayerY = player.y,
            livePlayerWidth = player.currentWidth,
            livePlayerHeight = player.currentHeight,
            nearbyHazardCount = nearbyHazardCount,
            nearestHazardDistancePx = nearestHazardDistancePx
        )
''',
        "reusable visibility result",
    )


if __name__ == "__main__":
    main()
