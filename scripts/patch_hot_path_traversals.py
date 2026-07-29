#!/usr/bin/env python3
"""Remove iterator churn and repeated text measurement from frame hot paths."""
from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: {label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def replace_between(path: Path, start: str, end: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    start_index = text.find(start)
    if start_index < 0:
        raise RuntimeError(f"{path}: {label}: start marker missing")
    end_index = text.find(end, start_index)
    if end_index < 0:
        raise RuntimeError(f"{path}: {label}: end marker missing")
    path.write_text(text[:start_index] + new + text[end_index:], encoding="utf-8")


def patch_entity_manager() -> None:
    path = Path("app/src/main/java/com/anurag9000/forestrun/engine/EntityManager.kt")
    replace_once(path,
'''        encounterDirector?.advance(deltaTime)?.forEach { directive ->
            spawn(
                type = directive.type,
                variant = directive.variant,
                startX = screenWidth + directive.xOffset,
                recordPersistence = false
            )
        }
''',
'''        val directives = encounterDirector?.advance(deltaTime)
        if (directives != null) {
            var directiveIndex = 0
            while (directiveIndex < directives.size) {
                val directive = directives[directiveIndex]
                spawn(
                    type = directive.type,
                    variant = directive.variant,
                    startX = screenWidth + directive.xOffset,
                    recordPersistence = false
                )
                directiveIndex++
            }
        }
''', "directive traversal")
    replace_once(path,
'''        val iterator = activeEntities.iterator()
        while (iterator.hasNext()) {
            val entity = iterator.next()
            entity.update(deltaTime, gameState.scrollSpeed)
            if (entity.isActive && entity.encounterOutcome == EncounterOutcome.PENDING) {
                entity.updatePlayerInteraction(player, gameState)
            }
            if (!entity.isActive) iterator.remove()
        }
''',
'''        var entityIndex = 0
        while (entityIndex < activeEntities.size) {
            val entity = activeEntities[entityIndex]
            entity.update(deltaTime, gameState.scrollSpeed)
            if (entity.isActive && entity.encounterOutcome == EncounterOutcome.PENDING) {
                entity.updatePlayerInteraction(player, gameState)
            }
            if (!entity.isActive) {
                activeEntities.removeAt(entityIndex)
            } else {
                entityIndex++
            }
        }
''', "entity update traversal")
    replace_once(path,
'''            for (entity in activeEntities) {
                if (!entity.isActive || entity.encounterOutcome != EncounterOutcome.PENDING) continue

                val result = entity.onCollision(player, gameState)
                val priority = collisionPriority(result)
                if (priority > selectedPriority) {
                    selectedEntity = entity
                    selectedResult = result
                    selectedPriority = priority
                    if (result == CollisionResult.HIT) break
                }
            }
''',
'''            var collisionIndex = 0
            while (collisionIndex < activeEntities.size) {
                val entity = activeEntities[collisionIndex]
                if (entity.isActive && entity.encounterOutcome == EncounterOutcome.PENDING) {
                    val result = entity.onCollision(player, gameState)
                    val priority = collisionPriority(result)
                    if (priority > selectedPriority) {
                        selectedEntity = entity
                        selectedResult = result
                        selectedPriority = priority
                        if (result == CollisionResult.HIT) break
                    }
                }
                collisionIndex++
            }
''', "collision traversal")
    replace_between(path,
        "    private fun resolvePassedEntities(player: Player, gameState: GameStateManager) {\n",
        "    private fun resolveBloomConversion(entity: Entity, gameState: GameStateManager) {\n",
'''    private fun resolvePassedEntities(player: Player, gameState: GameStateManager) {
        var entityIndex = 0
        while (entityIndex < activeEntities.size) {
            val entity = activeEntities[entityIndex]
            if (
                entity.isActive &&
                entity.encounterOutcome == EncounterOutcome.PENDING &&
                entity.hitbox.right < player.hitbox.left
            ) {
                entity.hasBeenPassed = true
                if (gameState.isBloomActive) {
                    resolveBloomConversion(entity, gameState)
                } else {
                    resolveCleanPass(entity, player, gameState)
                }
            }
            entityIndex++
        }
    }

''', "pass traversal")
    replace_once(path,
'''    fun draw(canvas: android.graphics.Canvas) {
        for (entity in activeEntities) entity.draw(canvas)
    }
''',
'''    fun draw(canvas: android.graphics.Canvas) {
        var entityIndex = 0
        while (entityIndex < activeEntities.size) {
            activeEntities[entityIndex].draw(canvas)
            entityIndex++
        }
    }
''', "entity draw traversal")
    replace_between(path,
        "    private fun updateBloomNearbyWorldReaction(deltaTime: Float, player: Player) {\n",
        "    private fun emitBloomProximityReaction(entity: Entity, type: EntityType) {\n",
'''    private fun updateBloomNearbyWorldReaction(deltaTime: Float, player: Player) {
        bloomReactionCooldown = (bloomReactionCooldown - deltaTime).coerceAtLeast(0f)
        val playerCenterX = player.hitbox.centerX()
        val playerCenterY = player.hitbox.centerY()

        var entityIndex = 0
        while (entityIndex < activeEntities.size) {
            val entity = activeEntities[entityIndex]
            if (
                entity.isActive &&
                entity.encounterOutcome == EncounterOutcome.PENDING &&
                !entity.hitbox.isEmpty
            ) {
                val type = entityTypeOf(entity)
                if (type != null) {
                    val reactionKey = System.identityHashCode(entity)
                    if (BloomWorldReaction.shouldReact(
                            playerCenterX = playerCenterX,
                            playerCenterY = playerCenterY,
                            entityCenterX = entity.hitbox.centerX(),
                            entityCenterY = entity.hitbox.centerY(),
                            alreadyReacted = reactionKey in bloomReactedEntities
                        )
                    ) {
                        bloomReactedEntities.add(reactionKey)
                        emitBloomProximityReaction(entity, type)
                        if (bloomReactionCooldown <= 0f) {
                            val cue = BloomWorldReaction.cueFor(type)
                            FlavorTextManager.spawn(
                                text = cue.text,
                                x = entity.hitbox.left,
                                y = entity.hitbox.top - 12f,
                                colour = when (cue.family) {
                                    BloomReactionFamily.FLORA -> android.graphics.Color.rgb(255, 226, 168)
                                    BloomReactionFamily.TREE -> android.graphics.Color.rgb(255, 214, 178)
                                    BloomReactionFamily.BIRD -> android.graphics.Color.rgb(226, 214, 255)
                                    BloomReactionFamily.ANIMAL -> android.graphics.Color.rgb(255, 236, 190)
                                },
                                lifetime = 0.85f,
                                size = 25f
                            )
                            bloomReactionCooldown = 0.18f
                        }
                    }
                }
            }
            entityIndex++
        }
    }

''', "Bloom proximity traversal")


def patch_seed_orbs() -> None:
    path = Path("app/src/main/java/com/anurag9000/forestrun/systems/SeedOrbManager.kt")
    replace_between(path, "    fun update(deltaTime: Float, gameState: GameStateManager, player: Player) {\n",
        "    fun reset() = orbs.clear()\n",
'''    fun update(deltaTime: Float, gameState: GameStateManager, player: Player) {
        var orbIndex = 0
        while (orbIndex < orbs.size) {
            val orb = orbs[orbIndex]
            orb.update(deltaTime, gameState.scrollSpeed, gameState)

            if (orb.checkCollection(player.hitbox)) {
                orb.isActive = false
                orb.isCollected = true
                ParticleManager.emit(FxPreset.SEED_COLLECT, orb.x, orb.y)
                gameState.collectSeed()
                SfxManager.playSeedPing()
                HapticManager.shortPulse()
            }

            if (!orb.isActive) {
                orbs.removeAt(orbIndex)
            } else {
                orbIndex++
            }
        }
    }

    fun draw(canvas: Canvas, bloomFraction: Float) {
        var orbIndex = 0
        while (orbIndex < orbs.size) {
            orbs[orbIndex].draw(canvas, bloomFraction)
            orbIndex++
        }
    }

''', "orb traversal")


def patch_particles() -> None:
    path = Path("app/src/main/java/com/anurag9000/forestrun/systems/ParticleManager.kt")
    replace_once(path,
'''        // Update continuous emitters
        for (emitter in continuousEmitters) {
            val n = emitter.updateContinuous(deltaTime)
            repeat(n) { emit(emitter) }
        }
''',
'''        // Update continuous emitters without allocating a MutableList iterator.
        var emitterIndex = 0
        while (emitterIndex < continuousEmitters.size) {
            val emitter = continuousEmitters[emitterIndex]
            val n = emitter.updateContinuous(deltaTime)
            repeat(n) { emit(emitter) }
            emitterIndex++
        }
''', "continuous particle traversal")


def patch_flavor_text() -> None:
    path = Path("app/src/main/java/com/anurag9000/forestrun/ui/FlavorTextManager.kt")
    replace_once(path, "import java.util.ArrayDeque\n", "", "remove deque import")
    replace_once(path, "    private val active = ArrayDeque<FlavorText>(MAX_ACTIVE)\n",
        "    private val active = ArrayList<FlavorText>(MAX_ACTIVE)\n", "indexed storage")
    replace_once(path,
'''        while (active.size >= MAX_ACTIVE) active.removeFirst()
        active.addLast(
''',
'''        while (active.size >= MAX_ACTIVE) active.removeAt(0)
        active.add(
''', "bounded queue operations")
    replace_between(path, "    fun update(deltaTime: Float) {\n", "    fun clear() = active.clear()\n",
'''    fun update(deltaTime: Float) {
        if (!deltaTime.isFinite() || deltaTime <= 0f) return
        var textIndex = 0
        while (textIndex < active.size) {
            val flavorText = active[textIndex]
            flavorText.elapsed += deltaTime
            flavorText.y -= FLOAT_SPEED * deltaTime
            if (flavorText.isDead) {
                active.removeAt(textIndex)
            } else {
                textIndex++
            }
        }
    }

    fun draw(canvas: Canvas) {
        val font = pixelFont ?: Typeface.MONOSPACE
        var textIndex = 0
        while (textIndex < active.size) {
            val flavorText = active[textIndex]
            val size = flavorText.currentSize
            val alpha = flavorText.alpha
            if (alpha > 0) {
                textPaint.typeface = font
                textPaint.textSize = size
                textPaint.color = flavorText.colour
                textPaint.alpha = alpha

                shadowPaint.typeface = font
                shadowPaint.textSize = size
                shadowPaint.alpha = (alpha * 0.6f).toInt().coerceIn(0, 255)

                canvas.drawText(
                    flavorText.text,
                    flavorText.x + SHADOW_DX,
                    flavorText.y + SHADOW_DY,
                    shadowPaint
                )
                canvas.drawText(flavorText.text, flavorText.x, flavorText.y, textPaint)
            }
            textIndex++
        }
    }

''', "flavor update and draw")


def patch_dialogue() -> None:
    path = Path("app/src/main/java/com/anurag9000/forestrun/ui/DialogueBubbleManager.kt")
    replace_once(path, "        val lines: List<String>,\n        var x: Float,\n",
        "        val lines: List<String>,\n        val widestLine: Float,\n        var x: Float,\n", "cached width")
    replace_once(path, "    private val active = mutableListOf<Bubble>()\n",
'''    private val active = mutableListOf<Bubble>()
    internal var lineMeasurementCountForTest: Int = 0
        private set
''', "measurement counter")
    replace_once(path,
'''        if (active.size >= MAX_BUBBLES) active.removeAt(0)
        active.add(
            Bubble(
                text = normalized,
                lines = wrapText(normalized, MAX_TEXT_WIDTH, textPaint, MAX_LINES),
                x = anchorX,
''',
'''        if (active.size >= MAX_BUBBLES) active.removeAt(0)
        val lines = wrapText(normalized, MAX_TEXT_WIDTH, textPaint, MAX_LINES)
        var widestLine = 0f
        var lineIndex = 0
        while (lineIndex < lines.size) {
            widestLine = maxOf(widestLine, textPaint.measureText(lines[lineIndex]))
            lineMeasurementCountForTest++
            lineIndex++
        }
        active.add(
            Bubble(
                text = normalized,
                lines = lines,
                widestLine = widestLine,
                x = anchorX,
''', "measure at spawn")
    replace_between(path, "    fun update(deltaTime: Float) {\n", "    fun draw(canvas: Canvas) {\n",
'''    fun update(deltaTime: Float) {
        if (!deltaTime.isFinite() || deltaTime <= 0f) return
        var bubbleIndex = 0
        while (bubbleIndex < active.size) {
            val bubble = active[bubbleIndex]
            bubble.elapsed += deltaTime
            bubble.y -= FLOAT_SPEED * deltaTime
            if (bubble.isDead) {
                active.removeAt(bubbleIndex)
            } else {
                bubbleIndex++
            }
        }
    }

''', "dialogue update")
    replace_between(path, "    fun draw(canvas: Canvas) {\n", "    fun clear() {\n",
'''    fun draw(canvas: Canvas) {
        val lineHeight = TEXT_SIZE + LINE_SPACING
        var bubbleIndex = 0
        while (bubbleIndex < active.size) {
            val bubble = active[bubbleIndex]
            val alpha = bubble.alpha
            if (alpha > 0) {
                textPaint.alpha = alpha
                fillPaint.color = bubble.fillColor
                fillPaint.alpha = (alpha * 0.96f).toInt().coerceIn(0, 255)
                borderPaint.color = bubble.borderColor
                borderPaint.alpha = alpha
                shadowPaint.alpha = (alpha * 0.33f).toInt().coerceIn(0, 255)

                val bubbleWidth = (bubble.widestLine + PADDING_X * 2f)
                    .coerceAtMost(canvas.width - SCREEN_MARGIN * 2f)
                    .coerceAtLeast(PADDING_X * 2f + 1f)
                val textBlockHeight = bubble.lines.size * lineHeight - LINE_SPACING
                val bubbleHeight = textBlockHeight + PADDING_Y * 2f

                val unclampedLeft = bubble.x - bubbleWidth / 2f
                val maxLeft = (canvas.width - bubbleWidth - SCREEN_MARGIN)
                    .coerceAtLeast(SCREEN_MARGIN)
                val left = unclampedLeft.coerceIn(SCREEN_MARGIN, maxLeft)
                val desiredTop = bubble.y - bubbleHeight - POINTER_H
                val maxTop = (canvas.height - bubbleHeight - POINTER_H - SCREEN_MARGIN)
                    .coerceAtLeast(SCREEN_MARGIN)
                val top = desiredTop.coerceIn(SCREEN_MARGIN, maxTop)
                bubbleRect.set(left, top, left + bubbleWidth, top + bubbleHeight)
                shadowRect.set(
                    bubbleRect.left + 4f,
                    bubbleRect.top + 5f,
                    bubbleRect.right + 4f,
                    bubbleRect.bottom + 5f
                )

                val pointerX = bubble.x.coerceIn(
                    bubbleRect.left + MIN_POINTER_INSET,
                    bubbleRect.right - MIN_POINTER_INSET
                )
                val pointerTipY = (bubbleRect.bottom + POINTER_H)
                    .coerceAtMost(canvas.height - SCREEN_MARGIN)

                pointerPath.reset()
                pointerPath.moveTo(pointerX - 12f, bubbleRect.bottom - 1f)
                pointerPath.lineTo(pointerX, pointerTipY)
                pointerPath.lineTo(pointerX + 12f, bubbleRect.bottom - 1f)
                pointerPath.close()

                shadowPath.reset()
                shadowPath.moveTo(pointerX - 8f, bubbleRect.bottom + 4f)
                shadowPath.lineTo(pointerX + 4f, pointerTipY + 5f)
                shadowPath.lineTo(pointerX + 16f, bubbleRect.bottom + 4f)
                shadowPath.close()

                canvas.drawRoundRect(shadowRect, CORNER_R, CORNER_R, shadowPaint)
                canvas.drawPath(shadowPath, shadowPaint)
                canvas.drawRoundRect(bubbleRect, CORNER_R, CORNER_R, fillPaint)
                canvas.drawPath(pointerPath, fillPaint)
                canvas.drawRoundRect(bubbleRect, CORNER_R, CORNER_R, borderPaint)
                canvas.drawPath(pointerPath, borderPaint)

                var baseline = bubbleRect.top + PADDING_Y - textPaint.ascent()
                var lineIndex = 0
                while (lineIndex < bubble.lines.size) {
                    canvas.drawText(
                        bubble.lines[lineIndex],
                        bubbleRect.centerX(),
                        baseline,
                        textPaint
                    )
                    baseline += lineHeight
                    lineIndex++
                }
            }
            bubbleIndex++
        }
    }

''', "dialogue draw")
    replace_once(path,
'''    fun clear() {
        active.clear()
        variantCounts.clear()
    }
''',
'''    fun clear() {
        active.clear()
        variantCounts.clear()
        lineMeasurementCountForTest = 0
    }
''', "reset measurement counter")


def main() -> None:
    patch_entity_manager()
    patch_seed_orbs()
    patch_particles()
    patch_flavor_text()
    patch_dialogue()


if __name__ == "__main__":
    main()
