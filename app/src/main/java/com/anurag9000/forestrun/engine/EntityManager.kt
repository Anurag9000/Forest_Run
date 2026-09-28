package com.anurag9000.forestrun.engine

import android.content.Context
import android.graphics.RectF
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.EncounterOutcome
import com.anurag9000.forestrun.entities.Entity
import com.anurag9000.forestrun.entities.EntityFactory
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.entities.animals.Cat
import com.anurag9000.forestrun.entities.animals.Dog
import com.anurag9000.forestrun.entities.animals.Fox
import com.anurag9000.forestrun.entities.animals.Hedgehog
import com.anurag9000.forestrun.entities.animals.Wolf
import com.anurag9000.forestrun.entities.birds.Duck
import com.anurag9000.forestrun.entities.birds.Eagle
import com.anurag9000.forestrun.entities.birds.Owl
import com.anurag9000.forestrun.entities.flora.Cactus
import com.anurag9000.forestrun.entities.flora.Eucalyptus
import com.anurag9000.forestrun.entities.flora.Hyacinth
import com.anurag9000.forestrun.entities.flora.LilyOfValley
import com.anurag9000.forestrun.entities.flora.VanillaOrchid
import com.anurag9000.forestrun.entities.trees.Bamboo
import com.anurag9000.forestrun.entities.trees.CherryBlossom
import com.anurag9000.forestrun.entities.trees.Jacaranda
import com.anurag9000.forestrun.entities.trees.WeepingWillow
import com.anurag9000.forestrun.systems.FxPreset
import com.anurag9000.forestrun.systems.ParticleManager
import com.anurag9000.forestrun.systems.SeedOrbManager
import com.anurag9000.forestrun.ui.DialogueBubbleManager
import com.anurag9000.forestrun.ui.FlavorTextManager
import kotlin.random.Random

/**
 * Owns entity spawning, updates, collision resolution, pass rewards, and Seed
 * Orbs. Every entity receives exactly one terminal [EncounterOutcome].
 *
 * Pooling is intentionally disabled until every concrete entity implements a
 * complete reset contract. Persistent encounter counts are written only when
 * an encounter actually resolves, never merely because an entity spawned.
 */
class EntityManager internal constructor(
    private val context: Context,
    private val screenWidth: Float,
    private val screenHeight: Float,
    private val spriteManager: SpriteManager,
    val biomeManager: BiomeManager = BiomeManager(),
    private val encounterPersistence: ApplicationEncounterPersistence =
        AndroidApplicationEncounterPersistence(context),
    val seedOrbManager: SeedOrbManager = SeedOrbManager()
) {
    @Volatile
    internal var debugActiveEntityCount: Int = 0

    val activeEntities: MutableList<Entity> = mutableListOf()

    /**
     * Completed, genuinely telegraphed diving-bird departures that could
     * exit vertically before an x-plane pass. Never credit an offscreen staged
     * Eagle or an unalerted sleeping Owl as a completed attack. Keep rewards
     * behind live HIT/STUMBLE arbitration, not inside the update/removal loop.
     */
    private val completedBirdEscapes = ArrayList<Entity>()

    private var distanceSinceRandomSpawnPx = 0f
    private var bloomReactionCooldown = 0f
    private var bloomWasActive = false
    private val bloomReactedEntities = BloomReactionIdentityLedger<Entity>()

    private val spawnX get() = screenWidth + 120f

    data class CollisionFrame(
        val result: CollisionResult,
        val entity: Entity
    )

    fun update(
        deltaTime: Float,
        gameState: GameStateManager,
        player: Player,
        encounterDirector: EncounterDirector? = null,
        runMode: RunMode = if (encounterDirector == null) {
            RunMode.NORMAL
        } else {
            RunMode.DEBUG_SCENARIO
        }
    ) {
        if (!deltaTime.isFinite() || deltaTime < 0f) {
            debugActiveEntityCount = activeEntities.size
            RuntimeWorkloadTelemetry.publishEntities(activeEntities.size)
            RuntimeWorkloadTelemetry.publishSeedOrbs(seedOrbManager.activeOrbCount)
            return
        }

        if (gameState.isBloomActive != bloomWasActive) {
            bloomReactedEntities.clear()
            bloomReactionCooldown = 0f
        }
        bloomWasActive = gameState.isBloomActive

        val directives = encounterDirector?.advance(deltaTime)
        if (directives != null) {
            var directiveIndex = 0
            while (directiveIndex < directives.size) {
                val directive = directives[directiveIndex]
                spawn(
                    type = directive.type,
                    variant = directive.variant,
                    startX = screenWidth + directive.xOffset,
                    recordPersistence = false,
                    random = Random(directive.deterministicSeed)
                )
                directiveIndex++
            }
        }

        if (runMode.allowsRandomSpawns) {
            val distanceDelta = gameState.scrollSpeed * deltaTime
            if (distanceDelta.isFinite() && distanceDelta > 0f) {
                distanceSinceRandomSpawnPx =
                    (distanceSinceRandomSpawnPx.toDouble() + distanceDelta.toDouble())
                        .coerceAtMost(Float.MAX_VALUE.toDouble())
                        .toFloat()
            }
            val requiredGapPx = SpawnPacing.requiredGapPx(
                distanceMetres = gameState.distanceMetres,
                runTimeSeconds = gameState.runTimeSeconds,
                scrollSpeedPxPerSec = gameState.scrollSpeed
            )
            if (!gameState.shouldLockRandomOpeningSpawns() &&
                distanceSinceRandomSpawnPx >= requiredGapPx
            ) {
                distanceSinceRandomSpawnPx = 0f
                spawnRandom(gameState)
            }
        }

        var entityIndex = 0
        while (entityIndex < activeEntities.size) {
            val entity = activeEntities[entityIndex]
            entity.previousHitbox.set(entity.hitbox)
            entity.hasMotionSample = isFiniteNonEmpty(entity.previousHitbox)
            entity.update(deltaTime, gameState.scrollSpeed)
            if (entity.isActive && entity.encounterOutcome == EncounterOutcome.PENDING) {
                entity.updatePlayerInteraction(player, gameState)
            }
            if (!entity.isActive) {
                if (entity.encounterOutcome == EncounterOutcome.PENDING &&
                    ((entity is Eagle && entity.hasCompletedAttackEscape) ||
                        (entity is Owl && entity.hasCompletedDiveEscape))
                ) {
                    completedBirdEscapes.add(entity)
                }
                activeEntities.removeAt(entityIndex)
            } else {
                entityIndex++
            }
        }

        if (gameState.isBloomActive) {
            updateBloomNearbyWorldReaction(deltaTime, player)
        }

        seedOrbManager.update(deltaTime, gameState, player)
        debugActiveEntityCount = activeEntities.size
        RuntimeWorkloadTelemetry.publishEntities(activeEntities.size)
    }

    /**
     * A padded MERCY_MISS is provisional while the entity is still in front
     * of the player. HIT outranks STUMBLE on this and every later frame; only
     * when the encounter is wholly behind can provisional mercy resolve.
     * Entity collision probes remain pure.
     */
    fun checkCollisions(player: Player, gameState: GameStateManager): CollisionFrame? {
        if (!gameState.isBloomActive) {
            var selectedEntity: Entity? = null
            var selectedResult = CollisionResult.NONE
            var selectedPriority = 0

            var collisionIndex = 0
            while (collisionIndex < activeEntities.size) {
                val entity = activeEntities[collisionIndex]
                if (entity.isActive && entity.encounterOutcome == EncounterOutcome.PENDING) {
                    val sampled = entity.onCollision(player, gameState)
                    val swept = sweptNarrowCoreResult(entity, player)
                    val result = if (collisionPriority(swept) > collisionPriority(sampled)) {
                        swept
                    } else {
                        sampled
                    }
                    if (result == CollisionResult.MERCY_MISS) {
                        // A close approach is not yet proof that the player
                        // avoided this entity for its entire dangerous span.
                        entity.observedMercyContact = true
                        collisionIndex++
                        continue
                    }
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

            if (selectedEntity != null && selectedResult != CollisionResult.NONE) {
                selectedEntity.onOutcomeSelected(selectedResult, player, gameState)
                selectedEntity.encounterOutcome = when (selectedResult) {
                    CollisionResult.HIT -> EncounterOutcome.HIT
                    CollisionResult.STUMBLE -> EncounterOutcome.STUMBLE
                    CollisionResult.MERCY_MISS, CollisionResult.NONE ->
                        error("Only direct HIT/STUMBLE may resolve before passage")
                }
                selectedEntity.observedMercyContact = false
                selectedEntity.hasBeenPassed = true
                recordResolvedEncounter(selectedEntity)
                return CollisionFrame(selectedResult, selectedEntity)
            }
        }

        return resolvePassedEntities(player, gameState)
    }

    /**
     * Recovery frames can carry even a single-body physical core through the
     * narrower airborne Player between endpoint samples. Only primary cores
     * with unconditional collision severity are eligible. Do not sweep
     * aggregate flock/tree/window boxes, staged divers or Dog's buddy mode.
     * Dog separately sweeps its own hazardous body and bark projectiles.
     */
    private fun sweptNarrowCoreResult(entity: Entity, player: Player): CollisionResult {
        val contact = when (entity) {
            is Cactus, is LilyOfValley, is Hyacinth, is Eucalyptus,
            is Duck, is Cat -> CollisionResult.HIT
            is Hedgehog, is Fox, is Wolf -> CollisionResult.STUMBLE
            else -> return CollisionResult.NONE
        }
        if (!entity.hasMotionSample || !player.hasMotionSample ||
            !isFiniteNonEmpty(entity.hitbox) || !isFiniteNonEmpty(player.hitbox)
        ) return CollisionResult.NONE
        return if (SweptCoreOverlap.intersects(
                player.previousHitbox, player.hitbox,
                entity.previousHitbox, entity.hitbox
            )
        ) contact else CollisionResult.NONE
    }

    private fun collisionPriority(result: CollisionResult): Int = when (result) {
        CollisionResult.HIT -> 3
        CollisionResult.STUMBLE -> 2
        CollisionResult.MERCY_MISS -> 1
        CollisionResult.NONE -> 0
    }

    private fun resolvePassedEntities(
        player: Player,
        gameState: GameStateManager
    ): CollisionFrame? {
        // Each completed encounter receives one exclusive final outcome.
        // Keep the existing single-frame UI contract: first mercy cue wins.
        var firstMercy: CollisionFrame? = null
        var entityIndex = 0
        while (entityIndex < activeEntities.size) {
            val entity = activeEntities[entityIndex]
            val bounds = liveBounds(entity)
            if (
                entity.isActive &&
                entity.encounterOutcome == EncounterOutcome.PENDING &&
                bounds != null &&
                bounds.right < player.hitbox.left
            ) {
                val mercy = resolveSafeDeparture(entity, bounds, player, gameState)
                if (firstMercy == null) firstMercy = mercy
            }
            entityIndex++
        }

        // Once a visible Owl/Eagle dive has exited, its vertical flight may
        // never satisfy the ordinary horizontal pass plane. Both species now
        // complete through this same outcome owner after live collision checks.
        while (completedBirdEscapes.isNotEmpty()) {
            val bird = completedBirdEscapes.removeAt(0)
            if (bird.encounterOutcome == EncounterOutcome.PENDING) {
                val bounds = liveBounds(bird)
                if (bounds != null) {
                    val mercy = resolveSafeDeparture(bird, bounds, player, gameState)
                    if (firstMercy == null) firstMercy = mercy
                }
            }
        }
        return firstMercy
    }

    private fun resolveSafeDeparture(
        entity: Entity,
        bounds: RectF,
        player: Player,
        gameState: GameStateManager
    ): CollisionFrame? {
        entity.hasBeenPassed = true
        if (gameState.isBloomActive) {
            resolveBloomConversion(entity, bounds, gameState)
            return null
        }
        if (entity.observedMercyContact) {
            entity.observedMercyContact = false
            entity.onOutcomeSelected(CollisionResult.MERCY_MISS, player, gameState)
            entity.encounterOutcome = EncounterOutcome.MERCY
            recordResolvedEncounter(entity)
            gameState.addMercyHeart()
            return CollisionFrame(CollisionResult.MERCY_MISS, entity)
        }
        resolveCleanPass(entity, bounds, player, gameState)
        return null
    }

    private fun resolveBloomConversion(
        entity: Entity,
        bounds: RectF,
        gameState: GameStateManager
    ) {
        entity.observedMercyContact = false
        entity.encounterOutcome = EncounterOutcome.BLOOM_CONVERTED
        recordResolvedEncounter(entity)
        gameState.recordBloomConversion()
        ParticleManager.emit(
            FxPreset.BLOOM_CONVERT,
            bounds.centerX(),
            bounds.centerY()
        )
        emitBloomEnvironmentReaction(entity, bounds)
        entity.isActive = false
    }

    private fun resolveCleanPass(
        entity: Entity,
        bounds: RectF,
        player: Player,
        gameState: GameStateManager
    ) {
        entity.observedMercyContact = false
        entity.encounterOutcome = EncounterOutcome.CLEAN_PASS
        recordResolvedEncounter(entity)

        // Relationship-specific safe departures (currently Cat's spare/wave)
        // own their reward/stat/presentation exclusively. Do not stack an
        // ordinary clean pass, pass history/cue, or optional clean-pass Orb on
        // the same resolved interaction.
        if (entity.resolveSpecialSafeDeparture(player, gameState)) {
            return
        }

        entity.performUniqueAction(player, gameState)
        gameState.recordCleanPass()

        entityTypeOf(entity)?.let { type ->
            if (entity.shouldRecordPersistence) {
                encounterPersistence.recordPass(type)
            }
            val passCue = RunFlavorPresentation.passCue(
                context = context,
                type = type,
                routeTier = gameState.pacifistRouteTier
            )
            DialogueBubbleManager.spawnVariant(
                triggerKey = "pass_${type.name}_${gameState.pacifistRouteTier.name}",
                textOptions = RunFlavorPresentation.passBubbleTexts(
                    context = context,
                    type = type,
                    routeTier = gameState.pacifistRouteTier
                ),
                anchorX = bounds.centerX(),
                anchorY = bounds.top - 18f,
                fillColor = passCue.fillColor,
                borderColor = passCue.borderColor
            )
            FlavorTextManager.spawn(
                text = passCue.flavorText,
                x = bounds.left,
                y = bounds.top - 10f,
                colour = passCue.flavorColor,
                lifetime = 0.95f,
                size = passCue.flavorSize
            )
        }

        val stagingPoint = SeedOrbSpawnPolicy.forCleanPass(
            encounterBounds = bounds,
            playerBounds = player.hitbox,
            playerGroundY = player.groundY,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            scrollSpeedPxPerSec = gameState.scrollSpeed
        )
        // The speed-aware pickup point may lie as far ahead as the next
        // still-unresolved hazard. Do not spawn an enticing Orb through that
        // encounter. Skip only the optional pickup, not clean-pass progress.
        var clearApproach = true
        var candidateIndex = 0
        while (candidateIndex < activeEntities.size) {
            val candidate = activeEntities[candidateIndex]
            if (candidate !== entity && candidate.isActive &&
                candidate.encounterOutcome == EncounterOutcome.PENDING
            ) {
                val candidateBounds = liveBounds(candidate)
                if (candidateBounds == null ||
                    !SeedOrbSpawnPolicy.isClearOfPendingEncounter(
                        stagingPoint, player.hitbox, candidateBounds
                    )
                ) {
                    clearApproach = false
                    break
                }
            }
            candidateIndex++
        }
        if (clearApproach) {
            seedOrbManager.trySpawn(
                centreX = stagingPoint.centreX,
                topY = stagingPoint.topY,
                spawnRate = orbSpawnRateFor(entity)
            )
        }
    }

    private fun recordResolvedEncounter(entity: Entity) {
        if (!entity.shouldRecordPersistence) return
        entityTypeOf(entity)?.let { type ->
            encounterPersistence.recordEncounter(type)
        }
    }

    fun draw(canvas: android.graphics.Canvas) {
        var entityIndex = 0
        while (entityIndex < activeEntities.size) {
            activeEntities[entityIndex].draw(canvas)
            entityIndex++
        }
    }

    fun drawOrbs(canvas: android.graphics.Canvas, bloomFraction: Float) {
        seedOrbManager.draw(canvas, bloomFraction)
    }

    private fun emitBloomEnvironmentReaction(entity: Entity, bounds: RectF) {
        val x = bounds.centerX()
        val y = bounds.centerY()
        ParticleManager.emit(FxPreset.BLOOM_WORLD_BURST, x, y)
        when (entity) {
            is LilyOfValley, is Hyacinth, is VanillaOrchid -> {
                ParticleManager.emit(FxPreset.POLLEN_BURST, x, y)
                ParticleManager.emit(FxPreset.SEED_COLLECT, x, y - 18f)
                ParticleManager.emit(FxPreset.BLOOM_CONVERT, x, y - 24f)
            }

            is Eucalyptus, is WeepingWillow, is Jacaranda, is CherryBlossom, is Bamboo -> {
                ParticleManager.emit(FxPreset.PETAL_DRIFT, x, y - 24f)
                ParticleManager.emit(FxPreset.BLOOM_CONVERT, x, y)
                ParticleManager.emit(FxPreset.SEED_COLLECT, x, y - 22f)
            }

            is Cactus -> {
                ParticleManager.emit(FxPreset.BLOOM_CONVERT, x, y)
                ParticleManager.emit(FxPreset.SEED_COLLECT, x, y - 10f)
                ParticleManager.emit(FxPreset.BLOOM_WORLD_BURST, x, y - 20f)
            }

            else -> {
                ParticleManager.emit(FxPreset.BLOOM_CONVERT, x, y)
                ParticleManager.emit(FxPreset.SEED_COLLECT, x, y - 12f)
            }
        }
    }

    private fun updateBloomNearbyWorldReaction(deltaTime: Float, player: Player) {
        bloomReactionCooldown = (bloomReactionCooldown - deltaTime).coerceAtLeast(0f)
        val playerCenterX = player.hitbox.centerX()
        val playerCenterY = player.hitbox.centerY()

        var entityIndex = 0
        while (entityIndex < activeEntities.size) {
            val entity = activeEntities[entityIndex]
            val bounds = liveBounds(entity)
            if (
                entity.isActive &&
                entity.encounterOutcome == EncounterOutcome.PENDING &&
                bounds != null
            ) {
                val type = entityTypeOf(entity)
                if (type != null) {
                    if (BloomWorldReaction.shouldReact(
                            playerCenterX = playerCenterX,
                            playerCenterY = playerCenterY,
                            entityCenterX = bounds.centerX(),
                            entityCenterY = bounds.centerY(),
                            alreadyReacted = bloomReactedEntities.contains(entity)
                        )
                    ) {
                        bloomReactedEntities.mark(entity)
                        emitBloomProximityReaction(type, bounds)
                        if (bloomReactionCooldown <= 0f) {
                            val cue = BloomWorldReaction.cueFor(type)
                            FlavorTextManager.spawn(
                                text = cue.text,
                                x = bounds.left,
                                y = bounds.top - 12f,
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

    private fun emitBloomProximityReaction(
        type: EntityType,
        bounds: RectF
    ) {
        val x = bounds.centerX()
        val y = bounds.centerY()
        ParticleManager.emit(FxPreset.BLOOM_WORLD_BURST, x, y)
        when (BloomWorldReaction.cueFor(type).family) {
            BloomReactionFamily.FLORA -> {
                ParticleManager.emit(FxPreset.POLLEN_BURST, x, y)
                ParticleManager.emit(FxPreset.SEED_COLLECT, x, y - 16f)
            }

            BloomReactionFamily.TREE -> {
                ParticleManager.emit(FxPreset.PETAL_DRIFT, x, y - 20f)
                ParticleManager.emit(FxPreset.SEED_COLLECT, x, y - 18f)
            }

            BloomReactionFamily.BIRD -> {
                ParticleManager.emit(FxPreset.BLOOM_CONVERT, x, y)
                ParticleManager.emit(FxPreset.MERCY_STARS, x, y - 14f)
            }

            BloomReactionFamily.ANIMAL -> {
                ParticleManager.emit(FxPreset.BLOOM_CONVERT, x, y)
                ParticleManager.emit(FxPreset.SEED_COLLECT, x, y - 12f)
            }
        }
    }

    private fun spawnRandom(gameState: GameStateManager) {
        val surfaceEligible = EntityFactory.eligibleOrdinaryPool(
            DifficultyScaler.getSpawnPool(gameState.distanceMetres, biomeManager),
            screenHeight
        )
        val pool = gameState.openingSpawnPool(surfaceEligible)
        if (pool.isNotEmpty()) {
            spawn(pool[Random.nextInt(pool.size)])
        }
    }

    fun spawn(
        type: EntityType,
        variant: EncounterVariant = EncounterVariant.DEFAULT,
        startX: Float = spawnX,
        recordPersistence: Boolean = true,
        random: Random = Random.Default
    ) {
        // Preserve an honest full-traversal Bamboo instead of constructing
        // impossible geometry (and throwing) on very short landscape devices.
        // Do not silently replace the authored species with a different type.
        if (type == EntityType.BAMBOO &&
            !EntityFactory.canStageBamboo(screenHeight)
        ) return
        val safeStartX = startX.takeIf { it.isFinite() } ?: spawnX
        val entity = EntityFactory.create(
            context,
            type,
            safeStartX,
            screenWidth,
            screenHeight,
            spriteManager,
            variant,
            random
        )
        entity.shouldRecordPersistence = recordPersistence
        activeEntities.add(entity)
        debugActiveEntityCount = activeEntities.size
    }

    fun seedOpeningSequence() {
        if (activeEntities.isNotEmpty()) return
        spawnAt(EntityType.DUCK, screenWidth + 380f)
        spawnAt(EntityType.LILY_OF_VALLEY, screenWidth + 700f)
        spawnAt(EntityType.CAT, screenWidth + 980f)
        spawnAt(EntityType.TIT, screenWidth + 1_240f)
    }

    internal fun debugSpawnAt(type: EntityType, worldX: Float) {
        spawn(type, startX = worldX, recordPersistence = false)
    }

    private fun spawnAt(type: EntityType, startX: Float) {
        spawn(type, startX = startX)
    }

    fun entityTypeOf(entity: Entity): EntityType? = when (entity) {
        is com.anurag9000.forestrun.entities.flora.Cactus -> EntityType.CACTUS
        is com.anurag9000.forestrun.entities.flora.LilyOfValley -> EntityType.LILY_OF_VALLEY
        is com.anurag9000.forestrun.entities.flora.Hyacinth -> EntityType.HYACINTH
        is com.anurag9000.forestrun.entities.flora.Eucalyptus -> EntityType.EUCALYPTUS
        is com.anurag9000.forestrun.entities.flora.VanillaOrchid -> EntityType.VANILLA_ORCHID
        is com.anurag9000.forestrun.entities.trees.WeepingWillow -> EntityType.WEEPING_WILLOW
        is com.anurag9000.forestrun.entities.trees.Jacaranda -> EntityType.JACARANDA
        is com.anurag9000.forestrun.entities.trees.Bamboo -> EntityType.BAMBOO
        is com.anurag9000.forestrun.entities.trees.CherryBlossom -> EntityType.CHERRY_BLOSSOM
        is com.anurag9000.forestrun.entities.birds.Duck -> EntityType.DUCK
        is com.anurag9000.forestrun.entities.birds.TitGroup -> EntityType.TIT
        is com.anurag9000.forestrun.entities.birds.ChickadeeGroup -> EntityType.CHICKADEE
        is com.anurag9000.forestrun.entities.birds.Owl -> EntityType.OWL
        is com.anurag9000.forestrun.entities.birds.Eagle -> EntityType.EAGLE
        is com.anurag9000.forestrun.entities.animals.Cat -> EntityType.CAT
        is com.anurag9000.forestrun.entities.animals.Wolf -> EntityType.WOLF
        is com.anurag9000.forestrun.entities.animals.Fox -> EntityType.FOX
        is com.anurag9000.forestrun.entities.animals.Hedgehog -> EntityType.HEDGEHOG
        is com.anurag9000.forestrun.entities.animals.Dog -> EntityType.DOG
        else -> null
    }

    fun reset() {
        activeEntities.clear()
        completedBirdEscapes.clear()
        seedOrbManager.reset()
        distanceSinceRandomSpawnPx = 0f
        bloomReactionCooldown = 0f
        bloomWasActive = false
        bloomReactedEntities.clear()
        debugActiveEntityCount = 0
        RuntimeWorkloadTelemetry.publishEntities(0)
    }

    private fun liveBounds(entity: Entity): RectF? {
        val candidate = entity.encounterBounds
        if (isFiniteNonEmpty(candidate)) return candidate

        val fallback = entity.hitbox
        return if (isFiniteNonEmpty(fallback)) fallback else null
    }

    private fun isFiniteNonEmpty(bounds: RectF): Boolean =
        bounds.left.isFinite() &&
            bounds.top.isFinite() &&
            bounds.right.isFinite() &&
            bounds.bottom.isFinite() &&
            bounds.left < bounds.right &&
            bounds.top < bounds.bottom

    private fun orbSpawnRateFor(entity: Entity): Float = when (entity) {
        is LilyOfValley -> 1.35f
        is Dog, is Wolf -> 1.20f
        else -> 1.0f
    }
}
