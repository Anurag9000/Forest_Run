package com.yourname.forest_run.engine

import android.content.Context
import com.yourname.forest_run.entities.CollisionResult
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
import kotlin.random.Random

/**
 * Manages the full lifecycle of all on-screen entities:
 *   • Choosing and spawning entity types from the difficulty-scaled pool.
 *   • Updating every active entity each frame.
 *   • Removing inactive entities.
 *   • Running the collision loop against the player.
 *   • Calling [Entity.performUniqueAction] when the player fully passes an entity.
 *
 * Object pooling: retired Entity instances are kept in [recyclePool] per type
 * and re-initialised on next use, avoiding allocations mid-run.
 */
class EntityManager(
    private val context: Context,
    private val screenWidth: Float,
    private val screenHeight: Float,
    private val spriteManager: SpriteManager,
    /** Injected from GameView — updated every frame before EntityManager.update() is called. */
    val biomeManager: BiomeManager = BiomeManager()
) {
    @Volatile
    internal var debugActiveEntityCount: Int = 0

    /** Seed orb spawner — public so GameView can call draw() with bloomFraction. */
    val seedOrbManager = SeedOrbManager()

    /** All entities currently on screen or within the active window. */
    val activeEntities: MutableList<Entity> = mutableListOf()

    /** Retired instances, keyed by type for object pooling (Phase 12+). */
    private val recyclePool: MutableMap<EntityType, MutableList<Entity>> = mutableMapOf()

    // ── Spawn timer ───────────────────────────────────────────────────────
    private var spawnTimer = 0f

    // ── Spawn X: slightly off the right edge of the screen ────────────────
    private val spawnX get() = screenWidth + 120f

    // ── Pass-detection threshold ──────────────────────────────────────────
    // An entity is considered "passed" when its right edge moves behind the player's left edge.
    private val playerPassX = screenWidth * 0.25f  // rough player position

    // ── Result accumulator (reset each collision frame) ───────────────────
    data class CollisionFrame(
        val result: CollisionResult,
        val entity: Entity
    )

    // ── Update ────────────────────────────────────────────────────────────

    /**
     * Main update. Call from [GameView.update()] every frame.
     *
     * @param deltaTime   Seconds since last frame.
     * @param gameState   The mutable game state (score, seeds, speed).
     * @param player      The active player instance.
     */
    fun update(
        deltaTime: Float,
        gameState: GameStateManager,
        player: Player,
        encounterDirector: EncounterDirector? = null
    ) {
        encounterDirector?.advance(deltaTime)?.forEach { directive ->
            spawn(directive.type, directive.variant, screenWidth + directive.xOffset)
        }

        // 1. Advance spawn timer
        if (encounterDirector?.isScenarioActive != true) {
            spawnTimer += deltaTime
            val defaultSpawnInterval = DifficultyScaler.getSpawnInterval(gameState.distanceMetres)
            val spawnInterval = gameState.openingSpawnInterval(defaultSpawnInterval)
            if (!gameState.shouldLockRandomOpeningSpawns() && spawnTimer >= spawnInterval) {
                spawnTimer = 0f
                spawnRandom(gameState)
            }
        }

        // 2. Update every active entity
        val iter = activeEntities.iterator()
        while (iter.hasNext()) {
            val entity = iter.next()
            entity.update(deltaTime, gameState.scrollSpeed)

            // Remove if entity flagged itself as done
            if (!entity.isActive) {
                recycle(entity)
                iter.remove()
                continue
            }

            // 3. Pass detection — entity has scrolled past the player's position
            if (!entity.hasBeenPassed && entity.hitbox.right < playerPassX) {
                entity.hasBeenPassed = true
                entity.performUniqueAction(player, gameState)
                gameState.recordCleanPass()
                if (gameState.isBloomActive) {
                    gameState.recordBloomConversion()
                    ParticleManager.emit(FxPreset.BLOOM_CONVERT, entity.hitbox.centerX(), entity.hitbox.centerY())
                    emitBloomEnvironmentReaction(entity)
                    entity.isActive = false
                }
                // Trigger seed orb spawn above the entity (60% base chance)
                seedOrbManager.trySpawn(
                    centreX    = entity.hitbox.centerX(),
                    topY       = entity.hitbox.top,
                    spawnRate  = orbSpawnRateFor(entity)
                )
            }
        }

        // Update orbs (collection check + scroll)
        seedOrbManager.update(deltaTime, gameState, player)
        debugActiveEntityCount = activeEntities.size
    }

    /**
     * Runs collision detection for all active entities vs the player.
     * Returns the first [CollisionFrame] that produces a non-NONE result,
     * or null if no collisions occurred this frame.
     *
     * Called from GameView AFTER update().
     */
    fun checkCollisions(player: Player, gameState: GameStateManager): CollisionFrame? {
        // Bloom makes player invincible — skip all collision
        if (gameState.isBloomActive) return null

        for (entity in activeEntities) {
            val result = entity.onCollision(player, gameState)
            if (result != CollisionResult.NONE) {
                if (result == CollisionResult.MERCY_MISS) {
                    gameState.addMercyHeart()
                }
                return CollisionFrame(result, entity)
            }
        }
        return null
    }

    // ── Draw ──────────────────────────────────────────────────────────────

    /** Draw all active entities. Call from [GameView.draw()] after background, before HUD. */
    fun draw(canvas: android.graphics.Canvas) {
        for (entity in activeEntities) {
            entity.draw(canvas)
        }
    }

    /**
     * Draw seed orbs. Call AFTER entity draw, BEFORE player draw so orbs appear between.
     * @param bloomFraction 0..1 — Bloom Meter fill proportion (drives orb colour).
     */
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

    // ── Spawning Helper ───────────────────────────────────────────────────

    private fun spawnRandom(gameState: GameStateManager) {
        val pool = gameState.openingSpawnPool(
            DifficultyScaler.getSpawnPool(gameState.distanceMetres, biomeManager)
        )
        val type = pool[Random.nextInt(pool.size)]
        spawn(type)
    }

    fun spawn(
        type: EntityType,
        variant: EncounterVariant = EncounterVariant.DEFAULT,
        startX: Float = spawnX
    ) {
        // Check recycle pool first
        val recycled = if (variant == EncounterVariant.DEFAULT) {
            recyclePool[type]?.removeLastOrNull()
        } else {
            null
        }
        val entity = recycled ?: EntityFactory.create(
            context, type, startX, screenWidth, screenHeight, spriteManager, variant
        )
        PersistentMemoryManager.recordEncounter(context, type)
        // Guarantee it's active and placed at spawn X
        entity.isActive = true
        entity.hasBeenPassed = false
        entity.x = startX
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
        spawn(type, startX = worldX)
    }

    private fun spawnAt(type: EntityType, startX: Float) {
        spawn(type, startX = startX)
    }

    // ── Recycling ─────────────────────────────────────────────────────────

    private fun recycle(entity: Entity) {
        // Only keep a small pool per type to cap memory usage
        val type = entityTypeOf(entity) ?: return
        val pool = recyclePool.getOrPut(type) { mutableListOf() }
        if (pool.size < 3) pool.add(entity)
    }

    /** Maps an entity instance back to its type for recycling. Extensible as we add more. */
    fun entityTypeOf(entity: Entity): EntityType? = when (entity) {
        is com.yourname.forest_run.entities.flora.Cactus          -> EntityType.CACTUS
        is com.yourname.forest_run.entities.flora.LilyOfValley    -> EntityType.LILY_OF_VALLEY
        is com.yourname.forest_run.entities.flora.Hyacinth        -> EntityType.HYACINTH
        is com.yourname.forest_run.entities.flora.Eucalyptus      -> EntityType.EUCALYPTUS
        is com.yourname.forest_run.entities.flora.VanillaOrchid   -> EntityType.VANILLA_ORCHID
        is com.yourname.forest_run.entities.trees.WeepingWillow   -> EntityType.WEEPING_WILLOW
        is com.yourname.forest_run.entities.trees.Jacaranda       -> EntityType.JACARANDA
        is com.yourname.forest_run.entities.trees.Bamboo          -> EntityType.BAMBOO
        is com.yourname.forest_run.entities.trees.CherryBlossom   -> EntityType.CHERRY_BLOSSOM
        is com.yourname.forest_run.entities.birds.Duck            -> EntityType.DUCK
        is com.yourname.forest_run.entities.birds.TitGroup        -> EntityType.TIT
        is com.yourname.forest_run.entities.birds.ChickadeeGroup  -> EntityType.CHICKADEE
        is com.yourname.forest_run.entities.birds.Owl             -> EntityType.OWL
        is com.yourname.forest_run.entities.birds.Eagle           -> EntityType.EAGLE
        is com.yourname.forest_run.entities.animals.Cat           -> EntityType.CAT
        is com.yourname.forest_run.entities.animals.Wolf          -> EntityType.WOLF
        is com.yourname.forest_run.entities.animals.Fox           -> EntityType.FOX
        is com.yourname.forest_run.entities.animals.Hedgehog      -> EntityType.HEDGEHOG
        is com.yourname.forest_run.entities.animals.Dog           -> EntityType.DOG
        else -> null
    }

    /** Clear all entities and orbs (on run reset). */
    fun reset() {
        activeEntities.clear()
        recyclePool.clear()
        seedOrbManager.reset()
        spawnTimer = 0f
        debugActiveEntityCount = 0
    }

    private fun orbSpawnRateFor(entity: Entity): Float = when (entity) {
        is LilyOfValley -> 1.35f
        is Dog, is Wolf -> 1.20f
        else -> 1.0f
    }
}
