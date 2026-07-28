#!/usr/bin/env python3
"""Replace time-based random spawning with travelled-distance pacing."""

from pathlib import Path

TARGET = Path("app/src/main/java/com/yourname/forest_run/engine/EntityManager.kt")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


def main() -> None:
    text = TARGET.read_text(encoding="utf-8")
    text = replace_once(
        text,
        "    private var spawnTimer = 0f\n",
        "    private var distanceSinceRandomSpawnPx = 0f\n",
        "rename spawn accumulator",
    )
    text = replace_once(
        text,
        """        if (encounterDirector?.isScenarioActive != true) {\n            spawnTimer += deltaTime\n            val defaultInterval = DifficultyScaler.getSpawnInterval(gameState.distanceMetres)\n            val spawnInterval = gameState.openingSpawnInterval(defaultInterval)\n            if (!gameState.shouldLockRandomOpeningSpawns() && spawnTimer >= spawnInterval) {\n                spawnTimer = 0f\n                spawnRandom(gameState)\n            }\n        }\n""",
        """        if (encounterDirector?.isScenarioActive != true) {\n            distanceSinceRandomSpawnPx +=\n                (gameState.scrollSpeed * deltaTime.coerceAtLeast(0f)).coerceAtLeast(0f)\n            val requiredGapPx = SpawnPacing.requiredGapPx(\n                distanceMetres = gameState.distanceMetres,\n                runTimeSeconds = gameState.runTimeSeconds,\n                scrollSpeedPxPerSec = gameState.scrollSpeed\n            )\n            if (!gameState.shouldLockRandomOpeningSpawns() &&\n                distanceSinceRandomSpawnPx >= requiredGapPx\n            ) {\n                distanceSinceRandomSpawnPx = 0f\n                spawnRandom(gameState)\n            }\n        }\n""",
        "replace time-based spawn condition",
    )
    text = replace_once(
        text,
        "        spawnTimer = 0f\n",
        "        distanceSinceRandomSpawnPx = 0f\n",
        "reset distance accumulator",
    )
    TARGET.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    main()
