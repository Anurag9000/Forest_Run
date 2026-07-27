
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
 * Owns entity spawning, updates and one-shot encounter resolution.
 *
 * Entities are intentionally constructed fresh. The previous pool reset only three fields
 * and leaked subclass state, projectiles, timers and relationship snapshots between spawns.
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
    private val bloomReactedEntities = mutableSetOf<Int>()
    private val spawnX get() = screenWidth + 120f

    data class CollisionFrame(val result: CollisionResult, val entity: Entity)

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
                persistEncounter = false
            )
        }

        if (encounterDirector?.isScenarioActive != true) {
            spawnTimer += deltaTime
            val interval = gameState.openingSpawnInterval(
                DifficultyScaler.getSpawnInterval(gameState.distanceMetres)
            )
            if (!gameState.shouldLockRandomOpeningSpawns() &&
                spawnTimer >= interval &&
                hasSafeSpawnGap(gameState.scrollSpeed)
            ) {
                spawnTimer = 0f
                spawnRandom(gameState)
            }
        }

        activeEntities.forEach { entity ->
            if (entity.isActive) entity.update(deltaTime, gameState.scrollSpeed)
        }

        if (gameState.isBloomActive) updateBloomNearbyWorldReaction(deltaTime, player)
        seedOrbManager.update(deltaTime, gameState, player)
        debugActiveEntityCount = activeEntities.size
    }

    /**
     * Resolves all overlaps by priority, then finalises passes. This makes the result
     * independent of list order and prevents pass rewards from preceding a same-frame hit.
     */
    fun checkCollisions(player: Player, gameState: GameStateManager): CollisionFrame? {
        var selected: CollisionFrame? = null

        if (!gameState.isBloomActive) {
            for (entity in activeEntities) {
                if (!entity.isActive || entity.isEncounterResolved) continue
                val result = entity.onCollision(player, gameState)
                if (result == CollisionResult.NONE) continue
                val candidate = CollisionFrame(result, entity)
                if (selected == null || collisionPriority(result) > collisionPriority(selected!!.result)) {
                    selected = candidate
                }
            }

            selected?.let { frame ->
                val outcome = when (frame.result) {
                    CollisionResult.HIT -> EncounterOutcome.HIT
                    CollisionResult.STUMBLE -> EncounterOutcome.STUMBLE
                    CollisionResult.MERCY_MISS -> EncounterOutcome.MERCY
                    CollisionResult.NONE -> EncounterOutcome.DESPAWNED
                }
                if (frame.entity.resolveEncounter(outcome) && frame.result == CollisionResult.MERCY_MISS) {
                    gameState.addMercyHeart()
                }
            }
        }

        if (selected?.result != CollisionResult.HIT) {
            finalisePassedEntities(player, gameState)
        }
        finaliseInactiveEntities(player, gameState)
        activeEntities.removeAll { !it.isActive }
        debugActiveEntityCount = activeEntities.size
        return selected
    }

    private fun finalisePassedEntities(player: Player, gameState: GameStateManager) {
        val passBoundary = player.hitbox.left
        activeEntities.forEach { entity ->
            if (!entity.isEncounterResolved && entity.hitbox.right < passBoundary) {
                if (gameState.isBloomActive) {
                    if (entity.resolveEncounter(EncounterOutcome.BLOOM_CONVERTED)) {
                        gameState.recordBloomConversion()
                        ParticleManager.emit(FxPreset.BLOOM_CONVERT, entity.hitbox.centerX(), entity.hitbox.centerY())
                        emitBloomEnvironmentReaction(entity)
                        entity.isActive = false
                    }
                } else if (entity.resolveEncounter(EncounterOutcome.CLEAN_PASS)) {
                    if (entity.persistProgression) {
                        entity.performUniqueAction(player, gameState)
                        entityTypeOf(entity)?.let { PersistentMemoryManager.recordPass(context, it) }
                    }
                    gameState.recordCleanPass()
                    emitPassPresentation(entity, gameState)
                    seedOrbManager.trySpawn(
                        centreX = entity.hitbox.centerX(),
                        topY = entity.hitbox.top,
                        spawnRate = orbSpawnRateFor(entity),
                        minimumReachableX = player.hitbox.right + (gameState.scrollSpeed * 0.35f).coerceIn(180f, 420f)
                    )
                }
            }
        }
    }

    private fun finaliseInactiveEntities(player: Player, gameState: GameStateManager) {
        activeEntities.forEach { entity ->
            if (entity.isActive || entity.isEncounterResolved) return@forEach
            if (entity.hitbox.right < player.hitbox.left) {
                if (gameState.isBloomActive && entity.resolveEncounter(EncounterOutcome.BLOOM_CONVERTED)) {
                    gameState.recordBloomConversion()
                    emitBloomEnvironmentReaction(entity)
                } else if (!gameState.isBloomActive && entity.resolveEncounter(EncounterOutcome.CLEAN_PASS)) {
                    if (entity.persistProgression) {
                        entity.performUniqueAction(player, gameState)
                        entityTypeOf(entity)?.let { PersistentMemoryManager.recordPass(context, it) }
                    }
                    gameState.recordCleanPass()
                }
            } else {
                entity.resolveEncounter(EncounterOutcome.DESPAWNED)
            }
        }
    }

    private fun emitPassPresentation(entity: Entity, gameState: GameStateManager) {
        val type = entityTypeOf(entity) ?: return
        val cue = RunFlavorPresentation.passCue(context, type, gameState.pacifistRouteTier)
        DialogueBubbleManager.spawnVariant(
            triggerKey = "pass_${type.name}_${gameState.pacifistRouteTier.name}",
            textOptions = RunFlavorPresentation.passBubbleTexts(context, type, gameState.pacifistRouteTier),
            anchorX = entity.hitbox.centerX(),
            anchorY = entity.hitbox.top - 18f,
            fillColor = cue.fillColor,
            borderColor = cue.borderColor
        )
        FlavorTextManager.spawn(
            text = cue.flavorText,
            x = entity.hitbox.left,
            y = entity.hitbox.top - 10f,
            colour = cue.flavorColor,
            lifetime = 0.95f,
            size = cue.flavorSize
        )
    }

    fun draw(canvas: android.graphics.Canvas) = activeEntities.forEach { it.draw(canvas) }

    fun drawOrbs(canvas: android.graphics.Canvas, bloomFraction: Float) {
        seedOrbManager.draw(canvas, bloomFraction)
    }

    private fun collisionPriority(result: CollisionResult): Int = when (result) {
        CollisionResult.HIT -> 3
        CollisionResult.STUMBLE -> 2
        CollisionResult.MERCY_MISS -> 1
        CollisionResult.NONE -> 0
    }

    private fun hasSafeSpawnGap(scrollSpeed: Float): Boolean {
        val rightmost = activeEntities.asSequence()
            .filter { it.isActive && !it.isEncounterResolved }
            .map { maxOf(it.x, it.hitbox.right) }
            .maxOrNull() ?: return true
        val minimumGap = (scrollSpeed * 0.62f).coerceIn(300f, 900f)
        return spawnX - rightmost >= minimumGap
    }

    private fun spawnRandom(gameState: GameStateManager) {
        val pool = gameState.openingSpawnPool(
            DifficultyScaler.getSpawnPool(gameState.distanceMetres, biomeManager)
        )
        if (pool.isNotEmpty()) spawn(pool[Random.nextInt(pool.size)])
    }

    fun spawn(
        type: EntityType,
        variant: EncounterVariant = EncounterVariant.DEFAULT,
        startX: Float = spawnX,
        persistEncounter: Boolean = true
    ) {
        val entity = EntityFactory.create(
            context, type, startX, screenWidth, screenHeight, spriteManager, variant
        )
        entity.isActive = true
        entity.x = startX
        entity.resetEncounterTracking(persistEncounter)
        if (persistEncounter) PersistentMemoryManager.recordEncounter(context, type)
        activeEntities.add(entity)
        debugActiveEntityCount = activeEntities.size
    }

    fun seedOpeningSequence() {
        if (activeEntities.isNotEmpty()) return
        spawn(EntityType.DUCK, startX = screenWidth + 380f)
        spawn(EntityType.LILY_OF_VALLEY, startX = screenWidth + 700f)
        spawn(EntityType.CAT, startX = screenWidth + 980f)
        spawn(EntityType.TIT, startX = screenWidth + 1_240f)
    }

    internal fun debugSpawnAt(type: EntityType, worldX: Float) {
        spawn(type, startX = worldX, persistEncounter = false)
    }

    private fun emitBloomEnvironmentReaction(entity: Entity) {
        val x = entity.hitbox.centerX()
        val y = entity.hitbox.centerY()
        ParticleManager.emit(FxPreset.BLOOM_WORLD_BURST, x, y)
        when (entity) {
            is LilyOfValley, is Hyacinth, is VanillaOrchid -> {
                ParticleManager.emit(FxPreset.POLLEN_BURST, x, y)
                ParticleManager.emit(FxPreset.SEED_COLLECT, x, y - 18f)
            }
            is Eucalyptus, is WeepingWillow, is Jacaranda, is CherryBlossom, is Bamboo -> {
                ParticleManager.emit(FxPreset.PETAL_DRIFT, x, y - 24f)
                ParticleManager.emit(FxPreset.SEED_COLLECT, x, y - 22f)
            }
            is Cactus -> ParticleManager.emit(FxPreset.SEED_COLLECT, x, y - 10f)
            else -> ParticleManager.emit(FxPreset.SEED_COLLECT, x, y - 12f)
        }
    }

    private fun updateBloomNearbyWorldReaction(deltaTime: Float, player: Player) {
        bloomReactionCooldown = (bloomReactionCooldown - deltaTime).coerceAtLeast(0f)
        val playerCenterX = player.hitbox.centerX()
        val playerCenterY = player.hitbox.centerY()
        for (entity in activeEntities) {
            if (!entity.isActive || entity.isEncounterResolved || entity.hitbox.isEmpty) continue
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
        else -> 1f
    }
}
