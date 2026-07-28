package com.yourname.forest_run.engine

import android.content.Context
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.EncounterOutcome
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.EntityFactory
import com.yourname.forest_run.entities.EntityType
import com.yourname.forest_run.entities.Player
import com.yourname.forest_run.entities.animals.Dog
import com.yourname.forest_run.entities.animals.Wolf
import com.yourname.forest_run.entities.flora.Cactus
import com.yourname.forest_run.entities.flora.Eucalyptus
import com.yourname.forest_run.entities.flora.Hyacinth
import com.yourname.forest_run.entities.flora.LilyOfValley
import com.yourname.forest_run.entities.flora.VanillaOrchid
import com.yourname.forest_run.entities.trees.Bamboo
import com.yourname.forest_run.entities.trees.CherryBlossom
import com.yourname.forest_run.entities.trees.Jacaranda
import com.yourname.forest_run.entities.trees.WeepingWillow
import com.yourname.forest_run.systems.FxPreset
import com.yourname.forest_run.systems.ParticleManager
import com.yourname.forest_run.systems.SeedOrbManager
import com.yourname.forest_run.ui.DialogueBubbleManager
import com.yourname.forest_run.ui.FlavorTextManager
import kotlin.random.Random

/**
 * Owns entity spawning, updates, collision resolution, pass rewards, and Seed
 * Orbs. Every entity receives exactly one terminal [EncounterOutcome].
 *
 * Pooling is intentionally disabled until every concrete entity implements a
 * complete reset contract. Persistent encounter counts are written only when
 * an encounter actually resolves, never merely because an entity spawned.
 */
class EntityManager(
    private val context: Context,
    private val screenWidth: Float,
    private val screenHeight: Float,
    private val spriteManager: SpriteManager,
    val biomeManager: BiomeManager = BiomeManager()
) {
    @Volatile
    internal var debugActiveEntityCount: Int = 0

    val seedOrbManager = SeedOrbManager()
    val activeEntities: MutableList<Entity> = mutableListOf()

    private var spawnTimer = 0f
    private var bloomReactionCooldown = 0f
    private var bloomWasActive = false
    private val bloomReactedEntities: MutableSet<Int> = mutableSetOf()

    private val spawnX get() = screenWidth + 120f

    data class CollisionFrame(
        val result: CollisionResult,
        val entity: Entity
    )

    fun update(
        deltaTime: Float,
        gameState: GameStateManager,
        player: Player,
        encounterDirector: EncounterDirector? = null
    ) {
        if (gameState.isBloomActive != bloomWasActive) {
            bloomReactedEntities.clear()
            bloomReactionCooldown = 0f
        }
        bloomWasActive = gameState.isBloomActive

        encounterDirector?.advance(deltaTime)?.forEach { directive ->
            spawn(
                type = directive.type,
                variant = directive.variant,
                startX = screenWidth + directive.xOffset,
                recordPersistence = false
            )
        }

        if (encounterDirector?.isScenarioActive != true) {
            spawnTimer += deltaTime
            val defaultInterval = DifficultyScaler.getSpawnInterval(gameState.distanceMetres)
            val spawnInterval = gameState.openingSpawnInterval(defaultInterval)
            if (!gameState.shouldLockRandomOpeningSpawns() && spawnTimer >= spawnInterval) {
                spawnTimer = 0f
                spawnRandom(gameState)
            }
        }

        val iterator = activeEntities.iterator()
        while (iterator.hasNext()) {
            val entity = iterator.next()
            entity.update(deltaTime, gameState.scrollSpeed)
            if (!entity.isActive) iterator.remove()
        }

        if (gameState.isBloomActive) {
            updateBloomNearbyWorldReaction(deltaTime, player)
        }

        seedOrbManager.update(deltaTime, gameState, player)
        debugActiveEntityCount = activeEntities.size
    }

    /**
     * Resolve collision outcomes before any pass reward. HIT outranks STUMBLE,
     * which outranks MERCY. If no collision wins, entities behind the player's
     * real hitbox resolve as clean passes or Bloom conversions.
     */
    fun checkCollisions(player: Player, gameState: GameStateManager): CollisionFrame? {
        if (!gameState.isBloomActive) {
            var selectedEntity: Entity? = null
            var selectedResult = CollisionResult.NONE
            var selectedPriority = 0

            for (entity in activeEntities) {
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

            if (selectedEntity != null && selectedResult != CollisionResult.NONE) {
                selectedEntity.encounterOutcome = when (selectedResult) {
                    CollisionResult.HIT -> EncounterOutcome.HIT
                    CollisionResult.STUMBLE -> EncounterOutcome.STUMBLE
                    CollisionResult.MERCY_MISS -> EncounterOutcome.MERCY
                    CollisionResult.NONE -> EncounterOutcome.PENDING
                }
                selectedEntity.hasBeenPassed = true
                recordResolvedEncounter(selectedEntity)
                if (selectedResult == CollisionResult.MERCY_MISS) {
                    gameState.addMercyHeart()
                }
                return CollisionFrame(selectedResult, selectedEntity)
            }
        }

        resolvePassedEntities(player, gameState)
        return null
    }

    private fun collisionPriority(result: CollisionResult): Int = when (result) {
        CollisionResult.HIT -> 3
        CollisionResult.STUMBLE -> 2
        CollisionResult.MERCY_MISS -> 1
        CollisionResult.NONE -> 0
    }

    private fun resolvePassedEntities(player: Player, gameState: GameStateManager) {
        for (entity in activeEntities) {
            if (!entity.isActive || entity.encounterOutcome != EncounterOutcome.PENDING) continue
            if (entity.hitbox.right >= player.hitbox.left) continue

            entity.hasBeenPassed = true
            if (gameState.isBloomActive) {
                resolveBloomConversion(entity, gameState)
            } else {
                resolveCleanPass(entity, player, gameState)
            }
        }
    }

    private fun resolveBloomConversion(entity: Entity, gameState: GameStateManager) {
        entity.encounterOutcome = EncounterOutcome.BLOOM_CONVERTED
        recordResolvedEncounter(entity)
        gameState.recordBloomConversion()
        ParticleManager.emit(
            FxPreset.BLOOM_CONVERT,
            entity.hitbox.centerX(),
            entity.hitbox.centerY()
        )
        emitBloomEnvironmentReaction(entity)
        entity.isActive = false
    }

    private fun resolveCleanPass(entity: Entity, player: Player, gameState: GameStateManager) {
        entity.encounterOutcome = EncounterOutcome.CLEAN_PASS
        recordResolvedEncounter(entity)
        entity.performUniqueAction(player, gameState)
        gameState.recordCleanPass()

        entityTypeOf(entity)?.let { type ->
            PersistentMemoryManager.recordPass(context, type)
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
                anchorX = entity.hitbox.centerX(),
                anchorY = entity.hitbox.top - 18f,
                fillColor = passCue.fillColor,
                borderColor = passCue.borderColor
            )
            FlavorTextManager.spawn(
                text = passCue.flavorText,
                x = entity.hitbox.left,
                y = entity.hitbox.top - 10f,
                colour = passCue.flavorColor,
                lifetime = 0.95f,
                size = passCue.flavorSize
            )
        }

        val reachableX = maxOf(
            entity.hitbox.centerX(),
            player.hitbox.right + maxOf(120f, screenWidth * 0.08f)
        )
        val reachableTopY = minOf(entity.hitbox.top, player.hitbox.top - 24f)
        seedOrbManager.trySpawn(
            centreX = reachableX,
            topY = reachableTopY,
            spawnRate = orbSpawnRateFor(entity)
        )
    }

    private fun recordResolvedEncounter(entity: Entity) {
        if (!entity.shouldRecordPersistence) return
        entityTypeOf(entity)?.let { type ->
            PersistentMemoryManager.recordEncounter(context, type)
        }
    }

    fun draw(canvas: android.graphics.Canvas) {
        for (entity in activeEntities) entity.draw(canvas)
    }

    fun drawOrbs(canvas: android.graphics.Canvas, bloomFraction: Float) {
        seedOrbManager.draw(canvas, bloomFraction)
    }

    private fun emitBloomEnvironmentReaction(entity: Entity) {
        val x = entity.hitbox.centerX()
        val y = entity.hitbox.centerY()
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

        for (entity in activeEntities) {
            if (
                !entity.isActive ||
                entity.encounterOutcome != EncounterOutcome.PENDING ||
                entity.hitbox.isEmpty
            ) continue

            val type = entityTypeOf(entity) ?: continue
            val reactionKey = System.identityHashCode(entity)
            if (!BloomWorldReaction.shouldReact(
                    playerCenterX = playerCenterX,
                    playerCenterY = playerCenterY,
                    entityCenterX = entity.hitbox.centerX(),
                    entityCenterY = entity.hitbox.centerY(),
                    alreadyReacted = reactionKey in bloomReactedEntities
                )
            ) continue

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

    private fun emitBloomProximityReaction(entity: Entity, type: EntityType) {
        val x = entity.hitbox.centerX()
        val y = entity.hitbox.centerY()
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
        val pool = gameState.openingSpawnPool(
            DifficultyScaler.getSpawnPool(gameState.distanceMetres, biomeManager)
        )
        spawn(pool[Random.nextInt(pool.size)])
    }

    fun spawn(
        type: EntityType,
        variant: EncounterVariant = EncounterVariant.DEFAULT,
        startX: Float = spawnX,
        recordPersistence: Boolean = true
    ) {
        val entity = EntityFactory.create(
            context,
            type,
            startX,
            screenWidth,
            screenHeight,
            spriteManager,
            variant
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
        is com.yourname.forest_run.entities.flora.Cactus -> EntityType.CACTUS
        is com.yourname.forest_run.entities.flora.LilyOfValley -> EntityType.LILY_OF_VALLEY
        is com.yourname.forest_run.entities.flora.Hyacinth -> EntityType.HYACINTH
        is com.yourname.forest_run.entities.flora.Eucalyptus -> EntityType.EUCALYPTUS
        is com.yourname.forest_run.entities.flora.VanillaOrchid -> EntityType.VANILLA_ORCHID
        is com.yourname.forest_run.entities.trees.WeepingWillow -> EntityType.WEEPING_WILLOW
        is com.yourname.forest_run.entities.trees.Jacaranda -> EntityType.JACARANDA
        is com.yourname.forest_run.entities.trees.Bamboo -> EntityType.BAMBOO
        is com.yourname.forest_run.entities.trees.CherryBlossom -> EntityType.CHERRY_BLOSSOM
        is com.yourname.forest_run.entities.birds.Duck -> EntityType.DUCK
        is com.yourname.forest_run.entities.birds.TitGroup -> EntityType.TIT
        is com.yourname.forest_run.entities.birds.ChickadeeGroup -> EntityType.CHICKADEE
        is com.yourname.forest_run.entities.birds.Owl -> EntityType.OWL
        is com.yourname.forest_run.entities.birds.Eagle -> EntityType.EAGLE
        is com.yourname.forest_run.entities.animals.Cat -> EntityType.CAT
        is com.yourname.forest_run.entities.animals.Wolf -> EntityType.WOLF
        is com.yourname.forest_run.entities.animals.Fox -> EntityType.FOX
        is com.yourname.forest_run.entities.animals.Hedgehog -> EntityType.HEDGEHOG
        is com.yourname.forest_run.entities.animals.Dog -> EntityType.DOG
        else -> null
    }

    fun reset() {
        activeEntities.clear()
        seedOrbManager.reset()
        spawnTimer = 0f
        bloomReactionCooldown = 0f
        bloomWasActive = false
        bloomReactedEntities.clear()
        debugActiveEntityCount = 0
    }

    private fun orbSpawnRateFor(entity: Entity): Float = when (entity) {
        is LilyOfValley -> 1.35f
        is Dog, is Wolf -> 1.20f
        else -> 1.0f
    }
}
