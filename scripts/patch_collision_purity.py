#!/usr/bin/env python3
"""Separate per-frame entity interaction from pure collision queries."""

from pathlib import Path

ROOT = Path("app/src/main/java/com/yourname/forest_run")


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: {label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def method_span(text: str, marker: str) -> tuple[int, int]:
    index = text.find(marker)
    if index < 0:
        raise RuntimeError(f"method marker not found: {marker}")
    line_start = text.rfind("\n", 0, index) + 1
    brace = text.find("{", index)
    if brace < 0:
        raise RuntimeError(f"method body not found: {marker}")

    depth = 0
    in_string = False
    escaped = False
    for pos in range(brace, len(text)):
        ch = text[pos]
        if in_string:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                in_string = False
            continue
        if ch == '"':
            in_string = True
        elif ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return line_start, pos + 1
    raise RuntimeError(f"unbalanced method: {marker}")


def replace_method(path: Path, marker: str, replacement: str) -> None:
    text = path.read_text(encoding="utf-8")
    start, end = method_span(text, marker)
    path.write_text(text[:start] + replacement.rstrip() + text[end:], encoding="utf-8")


def main() -> None:
    entity = ROOT / "entities/Entity.kt"
    replace_once(
        entity,
        """    open fun performUniqueAction(player: Player, gameState: GameStateManager) = Unit\n\n    abstract fun onCollision(player: Player, gameState: GameStateManager): CollisionResult\n""",
        """    open fun performUniqueAction(player: Player, gameState: GameStateManager) = Unit\n\n    /**\n     * Advance telegraphs and player-reactive mechanics once per frame. This is\n     * intentionally separate from [onCollision], which must be a pure query.\n     */\n    open fun updatePlayerInteraction(player: Player, gameState: GameStateManager) = Unit\n\n    /** Apply mechanical or presentation effects only after this entity wins arbitration. */\n    open fun onOutcomeSelected(\n        result: CollisionResult,\n        player: Player,\n        gameState: GameStateManager\n    ) = Unit\n\n    /** Return the current overlap result without mutating state or emitting presentation. */\n    abstract fun onCollision(player: Player, gameState: GameStateManager): CollisionResult\n""",
        "add interaction contract",
    )

    manager = ROOT / "engine/EntityManager.kt"
    replace_once(
        manager,
        """            val entity = iterator.next()\n            entity.update(deltaTime, gameState.scrollSpeed)\n            if (!entity.isActive) iterator.remove()\n""",
        """            val entity = iterator.next()\n            entity.update(deltaTime, gameState.scrollSpeed)\n            if (entity.isActive && entity.encounterOutcome == EncounterOutcome.PENDING) {\n                entity.updatePlayerInteraction(player, gameState)\n            }\n            if (!entity.isActive) iterator.remove()\n""",
        "advance player interactions",
    )
    replace_once(
        manager,
        """            if (selectedEntity != null && selectedResult != CollisionResult.NONE) {\n                selectedEntity.encounterOutcome = when (selectedResult) {\n""",
        """            if (selectedEntity != null && selectedResult != CollisionResult.NONE) {\n                selectedEntity.onOutcomeSelected(selectedResult, player, gameState)\n                selectedEntity.encounterOutcome = when (selectedResult) {\n""",
        "apply only selected outcome",
    )

    cat = ROOT / "entities/animals/Cat.kt"
    replace_method(
        cat,
        "override fun onCollision(",
        """    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {\n        if (RectF.intersects(player.hitbox, hitbox)) return CollisionResult.HIT\n\n        val mercyPad = readability.mercyPaddingPx + relationshipTuning.mercyPaddingBonusPx\n        val mercy = RectF(\n            hitbox.left - mercyPad,\n            hitbox.top - mercyPad,\n            hitbox.right + mercyPad,\n            hitbox.bottom + mercyPad\n        )\n        return if (RectF.intersects(player.hitbox, mercy)) {\n            CollisionResult.MERCY_MISS\n        } else {\n            CollisionResult.NONE\n        }\n    }\n""",
    )

    fox = ROOT / "entities/animals/Fox.kt"
    replace_method(
        fox,
        "override fun onCollision(",
        """    override fun updatePlayerInteraction(player: Player, gameState: GameStateManager) {\n        tryMirrorJump(player)\n    }\n\n    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {\n        if (RectF.intersects(player.hitbox, hitbox)) return CollisionResult.STUMBLE\n\n        val mercyPad = readability.mercyPaddingPx + relationshipTuning.mercyPaddingBonusPx\n        val mercy = RectF(\n            hitbox.left - mercyPad,\n            hitbox.top - mercyPad,\n            hitbox.right + mercyPad,\n            hitbox.bottom + mercyPad\n        )\n        return if (RectF.intersects(player.hitbox, mercy)) {\n            CollisionResult.MERCY_MISS\n        } else {\n            CollisionResult.NONE\n        }\n    }\n""",
    )

    hedgehog = ROOT / "entities/animals/Hedgehog.kt"
    replace_once(
        hedgehog,
        """    private var warningLeadTimer = 0f\n    private var pulse = 0f\n""",
        """    private var warningLeadTimer = 0f\n    private var pulse = 0f\n    private val warningRect = RectF()\n    private val mercyRect = RectF()\n""",
        "cache Hedgehog interaction rectangles",
    )
    replace_method(
        hedgehog,
        "override fun onCollision(",
        """    override fun updatePlayerInteraction(player: Player, gameState: GameStateManager) {\n        warningRect.set(\n            hitbox.left - readability.stagingPaddingPx * 5f,\n            hitbox.top - readability.stagingPaddingPx,\n            hitbox.right + readability.stagingPaddingPx,\n            hitbox.bottom + readability.stagingPaddingPx\n        )\n        if (!warned && RectF.intersects(player.hitbox, warningRect)) {\n            warned = true\n            armed = false\n            warningLeadTimer = warningLeadDurationSec\n            DialogueBubbleManager.spawn(\n                AnimalEncounterFlavor.hedgehogWarning(repeatHits),\n                x + hogW * 0.5f,\n                y - 14f,\n                Color.rgb(255, 246, 220),\n                Color.rgb(160, 120, 70)\n            )\n        }\n    }\n\n    override fun onOutcomeSelected(\n        result: CollisionResult,\n        player: Player,\n        gameState: GameStateManager\n    ) {\n        when (result) {\n            CollisionResult.STUMBLE -> if (!hasHit) {\n                hasHit = true\n                gameState.applySpeedDebuff(0.5f, 3000)\n                sprite.isLooping = false\n                sprite.setFrame(sprite.frameCount - 1)\n                ParticleManager.emit(\n                    FxPreset.MERCY_STARS,\n                    player.x + Player.BASE_WIDTH * 0.5f,\n                    player.y + Player.BASE_HEIGHT * 0.5f\n                )\n                DialogueBubbleManager.spawn(\n                    AnimalEncounterFlavor.hedgehogHit(repeatHits),\n                    player.x + Player.BASE_WIDTH * 0.5f,\n                    player.y - 20f,\n                    Color.rgb(255, 242, 220),\n                    Color.rgb(160, 120, 70)\n                )\n            }\n\n            CollisionResult.MERCY_MISS -> {\n                val line = if (warned && !armed && RectF.intersects(player.hitbox, hitbox)) {\n                    \"Hop now.\"\n                } else {\n                    \"Eep!\"\n                }\n                DialogueBubbleManager.spawn(\n                    line,\n                    x + hogW * 0.5f,\n                    y - 14f,\n                    Color.rgb(255, 246, 220),\n                    Color.rgb(160, 120, 70)\n                )\n            }\n\n            else -> Unit\n        }\n    }\n\n    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {\n        if (RectF.intersects(player.hitbox, hitbox)) {\n            return if (warned && !armed) {\n                CollisionResult.MERCY_MISS\n            } else {\n                CollisionResult.STUMBLE\n            }\n        }\n\n        val mercyPad = readability.mercyPaddingPx\n        mercyRect.set(\n            hitbox.left - mercyPad,\n            hitbox.top - mercyPad,\n            hitbox.right + mercyPad,\n            hitbox.bottom + mercyPad\n        )\n        return if (RectF.intersects(player.hitbox, mercyRect)) {\n            CollisionResult.MERCY_MISS\n        } else {\n            CollisionResult.NONE\n        }\n    }\n""",
    )

    duck = ROOT / "entities/birds/Duck.kt"
    replace_once(
        duck,
        """    private val quackCallRect = RectF()\n    private val duckLaneRect = RectF()\n""",
        """    private val quackCallRect = RectF()\n    private val duckLaneRect = RectF()\n    private val quackApproachRect = RectF()\n    private val laneApproachRect = RectF()\n""",
        "cache Duck interaction rectangles",
    )
    replace_method(
        duck,
        "override fun onCollision(",
        """    override fun updatePlayerInteraction(player: Player, gameState: GameStateManager) {\n        quackApproachRect.set(\n            hitbox.left - readability.stagingPaddingPx * 7f,\n            hitbox.top - readability.stagingPaddingPx * 2.2f,\n            hitbox.right,\n            hitbox.bottom + readability.stagingPaddingPx\n        )\n        if (!quackCalled && RectF.intersects(player.hitbox, quackApproachRect)) {\n            quackCalled = true\n            DialogueBubbleManager.spawn(\n                BirdEncounterFlavor.duckCall(),\n                quackCallRect.centerX(),\n                quackCallRect.top - 12f,\n                Color.rgb(255, 249, 224),\n                Color.rgb(184, 146, 62)\n            )\n        }\n\n        laneApproachRect.set(\n            duckLaneRect.left - readability.stagingPaddingPx * 2f,\n            duckLaneRect.top - readability.stagingPaddingPx,\n            duckLaneRect.right,\n            duckLaneRect.bottom + readability.stagingPaddingPx\n        )\n        if (!lanePrompted && RectF.intersects(player.hitbox, laneApproachRect)) {\n            lanePrompted = true\n            DialogueBubbleManager.spawn(\n                BirdEncounterFlavor.duckAnswerPrompt(),\n                duckLaneRect.centerX(),\n                duckLaneRect.top - 10f,\n                Color.rgb(255, 250, 226),\n                Color.rgb(184, 146, 62)\n            )\n        }\n        if (player.state == PlayerState.DUCKING && RectF.intersects(player.hitbox, duckLaneRect)) {\n            stayedLow = true\n            if (quackCalled) answeredQuack = true\n        }\n    }\n\n    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {\n        if (RectF.intersects(player.hitbox, hitbox)) return CollisionResult.HIT\n        val mercyPad = readability.mercyPaddingPx\n        val mercy = RectF(\n            hitbox.left - mercyPad,\n            hitbox.top - mercyPad,\n            hitbox.right + mercyPad,\n            hitbox.bottom + mercyPad\n        )\n        return if (RectF.intersects(player.hitbox, mercy)) {\n            CollisionResult.MERCY_MISS\n        } else {\n            CollisionResult.NONE\n        }\n    }\n""",
    )

    chickadee = ROOT / "entities/birds/ChickadeeGroup.kt"
    replace_once(
        chickadee,
        "    private val flutterPocketRect = RectF()\n",
        """    private val flutterPocketRect = RectF()\n    private val pocketApproachRect = RectF()\n""",
        "cache Chickadee interaction rectangle",
    )
    replace_method(
        chickadee,
        "override fun onCollision(",
        """    override fun updatePlayerInteraction(player: Player, gameState: GameStateManager) {\n        val approachLeft = birdRects.first().left - readability.stagingPaddingPx * 6f\n        val approachRight = birdRects.last().right + readability.stagingPaddingPx\n        if (!warned && player.hitbox.right >= approachLeft && player.hitbox.left <= approachRight) {\n            warned = true\n            DialogueBubbleManager.spawn(\n                BirdEncounterFlavor.chickadeeWarning(flutterSpread()),\n                x + birdCount * spacing * 0.42f,\n                altitudes.min() - 24f,\n                Color.rgb(255, 246, 224),\n                Color.rgb(170, 128, 84)\n            )\n        }\n\n        pocketApproachRect.set(\n            flutterPocketRect.left - readability.stagingPaddingPx,\n            flutterPocketRect.top - readability.stagingPaddingPx,\n            flutterPocketRect.right + readability.stagingPaddingPx,\n            flutterPocketRect.bottom + readability.stagingPaddingPx\n        )\n        if (!pocketPrompted && RectF.intersects(player.hitbox, pocketApproachRect)) {\n            pocketPrompted = true\n            DialogueBubbleManager.spawn(\n                BirdEncounterFlavor.chickadeePocketPrompt(),\n                flutterPocketRect.centerX(),\n                flutterPocketRect.top - 14f,\n                Color.rgb(255, 246, 224),\n                Color.rgb(170, 128, 84)\n            )\n        }\n        if (RectF.intersects(player.hitbox, flutterPocketRect)) readPocket = true\n    }\n\n    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {\n        for (rect in birdRects) {\n            if (RectF.intersects(player.hitbox, rect)) return CollisionResult.HIT\n            val mercyPad = readability.mercyPaddingPx\n            val mercy = RectF(\n                rect.left - mercyPad,\n                rect.top - mercyPad,\n                rect.right + mercyPad,\n                rect.bottom + mercyPad\n            )\n            if (RectF.intersects(player.hitbox, mercy)) return CollisionResult.MERCY_MISS\n        }\n        return CollisionResult.NONE\n    }\n""",
    )

    tit = ROOT / "entities/birds/TitGroup.kt"
    replace_once(
        tit,
        "    private val troughGuideRect = RectF()\n",
        """    private val troughGuideRect = RectF()\n    private val troughApproachRect = RectF()\n""",
        "cache Tit interaction rectangle",
    )
    replace_method(
        tit,
        "override fun onCollision(",
        """    override fun updatePlayerInteraction(player: Player, gameState: GameStateManager) {\n        val approachLeft = birdRects.first().left - readability.stagingPaddingPx * 6f\n        val approachRight = birdRects.last().right + readability.stagingPaddingPx\n        if (!countInPrompted && player.hitbox.right >= approachLeft && player.hitbox.left <= approachRight) {\n            countInPrompted = true\n            DialogueBubbleManager.spawn(\n                BirdEncounterFlavor.titCountIn(birdCount),\n                x + birdCount * spacing * 0.45f,\n                baseLine - waveAmplitude - 18f,\n                Color.rgb(232, 246, 255),\n                Color.rgb(88, 138, 196)\n            )\n        }\n\n        troughApproachRect.set(\n            troughGuideRect.left - readability.stagingPaddingPx * 1.6f,\n            troughGuideRect.top - readability.stagingPaddingPx,\n            troughGuideRect.right,\n            troughGuideRect.bottom + readability.stagingPaddingPx\n        )\n        if (!throughPrompted && RectF.intersects(player.hitbox, troughApproachRect)) {\n            throughPrompted = true\n            DialogueBubbleManager.spawn(\n                BirdEncounterFlavor.titThroughPrompt(birdCount),\n                troughGuideRect.centerX(),\n                troughGuideRect.top - 16f,\n                Color.rgb(232, 246, 255),\n                Color.rgb(88, 138, 196)\n            )\n        }\n        if (RectF.intersects(player.hitbox, troughGuideRect)) keptBeat = true\n    }\n\n    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {\n        for (rect in birdRects) {\n            if (RectF.intersects(player.hitbox, rect)) return CollisionResult.HIT\n            val mercyPad = readability.mercyPaddingPx\n            val mercy = RectF(\n                rect.left - mercyPad,\n                rect.top - mercyPad,\n                rect.right + mercyPad,\n                rect.bottom + mercyPad\n            )\n            if (RectF.intersects(player.hitbox, mercy)) return CollisionResult.MERCY_MISS\n        }\n        return CollisionResult.NONE\n    }\n""",
    )

    owl = ROOT / "entities/birds/Owl.kt"
    replace_method(
        owl,
        "override fun onCollision(",
        """    override fun updatePlayerInteraction(player: Player, gameState: GameStateManager) {\n        if (player.state in listOf(\n                com.yourname.forest_run.entities.PlayerState.JUMPING,\n                com.yourname.forest_run.entities.PlayerState.APEX,\n                com.yourname.forest_run.entities.PlayerState.JUMP_START\n            ) && owlState == OwlState.SLEEPING\n        ) {\n            owlState = OwlState.ALERT\n            alertTimer = 0f\n            pendingTargetX = player.hitbox.centerX()\n            pendingTargetY = player.hitbox.centerY()\n            if (!hasWarned) {\n                DialogueBubbleManager.spawn(\n                    RelationshipArcSystem.encounterCueLine(\n                        context,\n                        EntityType.OWL,\n                        RelationshipArcSystem.EncounterCue.OWL_ALERT\n                    ),\n                    x + birdW * 0.5f,\n                    y - 14f,\n                    Color.rgb(255, 242, 220),\n                    Color.rgb(170, 120, 60)\n                )\n                hasWarned = true\n            }\n        }\n    }\n\n    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {\n        if (owlState == OwlState.DIVING && RectF.intersects(player.hitbox, hitbox)) {\n            return CollisionResult.HIT\n        }\n\n        val mercyPad = readability.mercyPaddingPx + relationshipTuning.mercyPaddingBonusPx\n        val mercy = RectF(\n            hitbox.left - mercyPad,\n            hitbox.top - mercyPad,\n            hitbox.right + mercyPad,\n            hitbox.bottom + mercyPad\n        )\n        return if (owlState == OwlState.DIVING && RectF.intersects(player.hitbox, mercy)) {\n            CollisionResult.MERCY_MISS\n        } else {\n            CollisionResult.NONE\n        }\n    }\n""",
    )

    eagle = ROOT / "entities/birds/Eagle.kt"
    replace_once(
        eagle,
        """    private var markPrompted = false\n    private var heldMark = true\n    private val targetZoneRect = RectF()\n    private val diveCorridorRect = RectF()\n""",
        """    private var markPrompted = false\n    private var heldMark = true\n    private var targetAnnounced = false\n    private var markGraceTimer = 0f\n    private val targetZoneRect = RectF()\n    private val diveCorridorRect = RectF()\n    private val markApproachRect = RectF()\n""",
        "add Eagle targeting state",
    )
    replace_once(
        eagle,
        """        // Auto-lock onto the player's typical horizontal running line\n        lockOnTarget(screenWidth * 0.25f, groundY - 50f)\n""",
        """        // Seed a visible lane. The live player position replaces this\n        // placeholder during the first interaction frame.\n        updateTarget(screenWidth * 0.25f, groundY - 50f)\n""",
        "defer Eagle announcement until live player exists",
    )
    replace_method(
        eagle,
        "fun lockOnTarget(",
        """    fun lockOnTarget(targetX: Float, targetY: Float) {\n        updateTarget(targetX, targetY)\n        announceTarget()\n    }\n\n    private fun updateTarget(targetX: Float, targetY: Float) {\n        this.targetX = targetX\n        this.targetY = targetY\n        val dx = targetX - x\n        val dy = targetY - y\n        val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat().coerceAtLeast(1f)\n        velX = dx / dist * diveSpeed\n        velY = dy / dist * diveSpeed\n        updateCueGeometry()\n    }\n\n    private fun announceTarget() {\n        if (targetAnnounced) return\n        targetAnnounced = true\n        CameraSystem.shakeEagle()\n        DialogueBubbleManager.spawn(\n            RelationshipArcSystem.encounterCueLine(\n                context,\n                EntityType.EAGLE,\n                RelationshipArcSystem.EncounterCue.EAGLE_LOCK\n            ),\n            targetX,\n            targetY - 28f,\n            Color.rgb(255, 234, 234),\n            Color.rgb(180, 70, 70)\n        )\n    }\n""",
    )
    replace_once(
        eagle,
        """            if (lockTimer >= lockDuration) {\n                isLocked = true\n            }\n        } else {\n            x += velX * deltaTime\n            y += velY * deltaTime\n        }\n""",
        """            if (lockTimer >= lockDuration) {\n                isLocked = true\n                markGraceTimer = 0.18f\n            }\n        } else {\n            markGraceTimer = (markGraceTimer - deltaTime).coerceAtLeast(0f)\n            x += velX * deltaTime\n            y += velY * deltaTime\n        }\n""",
        "add Eagle escape grace",
    )
    replace_method(
        eagle,
        "override fun onCollision(",
        """    override fun updatePlayerInteraction(player: Player, gameState: GameStateManager) {\n        if (!isLocked) {\n            updateTarget(player.hitbox.centerX(), player.hitbox.centerY())\n            announceTarget()\n        }\n\n        markApproachRect.set(\n            targetZoneRect.left - readability.stagingPaddingPx,\n            targetZoneRect.top - readability.stagingPaddingPx,\n            targetZoneRect.right + readability.stagingPaddingPx,\n            targetZoneRect.bottom + readability.stagingPaddingPx\n        )\n        if (isLocked && !markPrompted && RectF.intersects(player.hitbox, markApproachRect)) {\n            markPrompted = true\n            DialogueBubbleManager.spawn(\n                \"Clear the mark.\",\n                targetX,\n                targetY - 12f,\n                Color.rgb(255, 234, 234),\n                Color.rgb(180, 70, 70)\n            )\n        }\n        if (isLocked && markGraceTimer <= 0f && RectF.intersects(player.hitbox, targetZoneRect)) {\n            heldMark = false\n        }\n    }\n\n    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {\n        if (RectF.intersects(player.hitbox, hitbox)) return CollisionResult.HIT\n\n        val mercyPad = readability.mercyPaddingPx + relationshipTuning.mercyPaddingBonusPx\n        val mercy = RectF(\n            hitbox.left - mercyPad,\n            hitbox.top - mercyPad,\n            hitbox.right + mercyPad,\n            hitbox.bottom + mercyPad\n        )\n        return if (RectF.intersects(player.hitbox, mercy)) {\n            CollisionResult.MERCY_MISS\n        } else {\n            CollisionResult.NONE\n        }\n    }\n""",
    )


if __name__ == "__main__":
    main()
