#!/usr/bin/env python3
"""Prevent deterministic debug scenarios from mutating permanent progression."""

from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: {label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def main() -> None:
    manager = Path("app/src/main/java/com/yourname/forest_run/engine/EntityManager.kt")
    replace_once(
        manager,
        """        entityTypeOf(entity)?.let { type ->\n            PersistentMemoryManager.recordPass(context, type)\n            val passCue = RunFlavorPresentation.passCue(\n""",
        """        entityTypeOf(entity)?.let { type ->\n            if (entity.shouldRecordPersistence) {\n                PersistentMemoryManager.recordPass(context, type)\n            }\n            val passCue = RunFlavorPresentation.passCue(\n""",
        "gate persistent clean pass",
    )

    cat = Path("app/src/main/java/com/yourname/forest_run/entities/animals/Cat.kt")
    replace_once(
        cat,
        "        PersistentMemoryManager.recordSpare(context, EntityType.CAT)\n",
        """        if (shouldRecordPersistence) {\n            PersistentMemoryManager.recordSpare(context, EntityType.CAT)\n        }\n""",
        "gate Cat spare persistence",
    )

    game_view = Path("app/src/main/java/com/yourname/forest_run/engine/GameView.kt")
    replace_once(
        game_view,
        """        if (::gameState.isInitialized) gameState.save()   // persist high score\n""",
        """        if (::gameState.isInitialized && encounterDirector?.isScenarioActive != true) {\n            gameState.save()   // persist ordinary-play high score only\n        }\n""",
        "gate debug high-score save",
    )
    replace_once(
        game_view,
        """            val collision = entityManager.checkCollisions(player, gameState)\n            if (collision != null) {\n                when (collision.result) {\n""",
        """            val collision = entityManager.checkCollisions(player, gameState)\n            if (collision != null) {\n                val persistEncounter = collision.entity.shouldRecordPersistence &&\n                    encounterDirector?.isScenarioActive != true\n                when (collision.result) {\n""",
        "derive selected persistence policy",
    )
    replace_once(
        game_view,
        """                        if (::gameState.isInitialized &&\n                            gameState.distanceMetres > SaveManager.loadBestDistance(context)\n                        ) {\n""",
        """                        if (persistEncounter &&\n                            ::gameState.isInitialized &&\n                            gameState.distanceMetres > SaveManager.loadBestDistance(context)\n                        ) {\n""",
        "gate debug ghost and best distance",
    )
    replace_once(
        game_view,
        """                        val killerType = entityManager.entityTypeOf(collision.entity)\n                        killerType?.let { PersistentMemoryManager.recordHit(context, it) }\n""",
        """                        val killerType = entityManager.entityTypeOf(collision.entity)\n                        if (persistEncounter) {\n                            killerType?.let { PersistentMemoryManager.recordHit(context, it) }\n                        }\n""",
        "gate lethal hit persistence",
    )
    replace_once(
        game_view,
        """                        currentRunSummary?.let {\n                            ForestMoodSystem.recordRun(context, it)\n                            ReturnMomentsSystem.recordRunOutcome(context, it)\n                            SaveManager.saveLastRunSummary(context, it)\n                        }\n""",
        """                        if (persistEncounter) {\n                            currentRunSummary?.let {\n                                ForestMoodSystem.recordRun(context, it)\n                                ReturnMomentsSystem.recordRunOutcome(context, it)\n                                SaveManager.saveLastRunSummary(context, it)\n                            }\n                        }\n""",
        "gate debug run-summary persistence",
    )
    replace_once(
        game_view,
        """                        gameState.recordHit()\n                        val killerType = entityManager.entityTypeOf(collision.entity)\n                        killerType?.let { PersistentMemoryManager.recordHit(context, it) }\n""",
        """                        gameState.recordHit()\n                        val killerType = entityManager.entityTypeOf(collision.entity)\n                        if (persistEncounter) {\n                            killerType?.let { PersistentMemoryManager.recordHit(context, it) }\n                        }\n""",
        "gate stumble hit persistence",
    )
    replace_once(
        game_view,
        """                reward.friendBiome?.let { PersistentMemoryManager.recordBiomeFriendship(context, it) }\n""",
        """                if (encounterDirector?.isScenarioActive != true) {\n                    reward.friendBiome?.let { PersistentMemoryManager.recordBiomeFriendship(context, it) }\n                }\n""",
        "gate debug biome friendship",
    )


if __name__ == "__main__":
    main()
