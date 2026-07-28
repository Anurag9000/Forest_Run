#!/usr/bin/env python3
"""Prevent deterministic debug scenarios from mutating permanent progression."""

from pathlib import Path


def replace_first(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise RuntimeError(f"{path}: {label}: source pattern not found")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def main() -> None:
    manager = Path("app/src/main/java/com/yourname/forest_run/engine/EntityManager.kt")
    replace_first(
        manager,
        """        entityTypeOf(entity)?.let { type ->
            PersistentMemoryManager.recordPass(context, type)
            val passCue = RunFlavorPresentation.passCue(
""",
        """        entityTypeOf(entity)?.let { type ->
            if (entity.shouldRecordPersistence) {
                PersistentMemoryManager.recordPass(context, type)
            }
            val passCue = RunFlavorPresentation.passCue(
""",
        "gate persistent clean pass",
    )

    cat = Path("app/src/main/java/com/yourname/forest_run/entities/animals/Cat.kt")
    replace_first(
        cat,
        "        PersistentMemoryManager.recordSpare(context, EntityType.CAT)\n",
        """        if (shouldRecordPersistence) {
            PersistentMemoryManager.recordSpare(context, EntityType.CAT)
        }
""",
        "gate Cat spare persistence",
    )

    game_view = Path("app/src/main/java/com/yourname/forest_run/engine/GameView.kt")
    replace_first(
        game_view,
        """        if (::gameState.isInitialized) gameState.save()   // persist high score
""",
        """        if (::gameState.isInitialized && encounterDirector?.isScenarioActive != true) {
            gameState.save()   // persist ordinary-play high score only
        }
""",
        "gate debug high-score save",
    )
    replace_first(
        game_view,
        """            val collision = entityManager.checkCollisions(player, gameState)
            if (collision != null) {
                when (collision.result) {
""",
        """            val collision = entityManager.checkCollisions(player, gameState)
            if (collision != null) {
                val persistEncounter = collision.entity.shouldRecordPersistence &&
                    encounterDirector?.isScenarioActive != true
                when (collision.result) {
""",
        "derive selected persistence policy",
    )
    replace_first(
        game_view,
        """                        if (::gameState.isInitialized &&
                            gameState.distanceMetres > SaveManager.loadBestDistance(context)
                        ) {
""",
        """                        if (persistEncounter &&
                            ::gameState.isInitialized &&
                            gameState.distanceMetres > SaveManager.loadBestDistance(context)
                        ) {
""",
        "gate debug ghost and best distance",
    )
    replace_first(
        game_view,
        """                        val killerType = entityManager.entityTypeOf(collision.entity)
                        killerType?.let { PersistentMemoryManager.recordHit(context, it) }
""",
        """                        val killerType = entityManager.entityTypeOf(collision.entity)
                        if (persistEncounter) {
                            killerType?.let { PersistentMemoryManager.recordHit(context, it) }
                        }
""",
        "gate lethal hit persistence",
    )
    replace_first(
        game_view,
        """                        currentRunSummary?.let {
                            ForestMoodSystem.recordRun(context, it)
                            ReturnMomentsSystem.recordRunOutcome(context, it)
                            SaveManager.saveLastRunSummary(context, it)
                        }
""",
        """                        if (persistEncounter) {
                            currentRunSummary?.let {
                                ForestMoodSystem.recordRun(context, it)
                                ReturnMomentsSystem.recordRunOutcome(context, it)
                                SaveManager.saveLastRunSummary(context, it)
                            }
                        }
""",
        "gate debug run-summary persistence",
    )
    replace_first(
        game_view,
        """                        gameState.recordHit()
                        val killerType = entityManager.entityTypeOf(collision.entity)
                        killerType?.let { PersistentMemoryManager.recordHit(context, it) }
""",
        """                        gameState.recordHit()
                        val killerType = entityManager.entityTypeOf(collision.entity)
                        if (persistEncounter) {
                            killerType?.let { PersistentMemoryManager.recordHit(context, it) }
                        }
""",
        "gate stumble hit persistence",
    )
    replace_first(
        game_view,
        """                reward.friendBiome?.let { PersistentMemoryManager.recordBiomeFriendship(context, it) }
""",
        """                if (encounterDirector?.isScenarioActive != true) {
                    reward.friendBiome?.let { PersistentMemoryManager.recordBiomeFriendship(context, it) }
                }
""",
        "gate debug biome friendship",
    )


if __name__ == "__main__":
    main()
