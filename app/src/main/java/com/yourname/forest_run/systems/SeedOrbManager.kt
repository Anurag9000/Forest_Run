package com.yourname.forest_run.systems

import android.graphics.Canvas
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.entities.Player
import kotlin.random.Random

/**
 * Manages the lifecycle of all [SeedOrb] collectibles.
 *
 * Spawning rules:
 *  - Every entity that the player successfully passes may trigger an orb spawn.
 *  - Spawn probability: 60% per passed entity (adjusted by LilyOfValley ×2 rate boost).
 *  - Orb spawns 80px–150px above the entity's hitbox top-centre.
 *  - A maximum of 4 orbs can be on-screen simultaneously (prevents clutter).
 *
 * Integration (GameView):
 *   seedOrbManager.update(deltaTime, gameState, player)
 *   seedOrbManager.draw(canvas, bloomFraction)
 *
 * Entities call:
 *   SeedOrbManager.trySpawn(entityCentreX, entityTopY)
 *
 * GameStateManager.collectSeed() is called internally when the player
 * overlaps an orb. It handles seed count, bloom trigger, and orb FX.
 */
class SeedOrbManager {

    companion object {
        const val MAX_ORBS        = 4
        const val BASE_SPAWN_RATE = 0.60f    // 60% chance per passed entity
        const val SPAWN_HEIGHT_MIN = 80f
        const val SPAWN_HEIGHT_MAX = 150f
    }

    private val orbs = mutableListOf<SeedOrb>()

    // ── Spawning ─────────────────────────────────────────────────────────

    /**
     * Called from EntityManager when an entity is passed (performUniqueAction).
     * @param centreX  World X centre of the passed entity.
     * @param topY     World Y of the top of the passed entity.
     * @param spawnRate Probability multiplier (1.0 = 60%, 2.0 = 100% clamped).
     */
    fun trySpawn(centreX: Float, topY: Float, spawnRate: Float = 1.0f) {
        if (orbs.size >= MAX_ORBS) return

        val chance = (BASE_SPAWN_RATE * spawnRate).coerceIn(0f, 1f)
        if (Random.nextFloat() > chance) return

        val offsetY = SPAWN_HEIGHT_MIN + Random.nextFloat() * (SPAWN_HEIGHT_MAX - SPAWN_HEIGHT_MIN)
        orbs.add(SeedOrb(
            x = centreX + (Random.nextFloat() - 0.5f) * 60f,   // slight X jitter
            y = topY - offsetY
        ))
    }

    // ── Update ────────────────────────────────────────────────────────────

    /**
     * Advance all orbs, check collection, emit FX, update game state.
     */
    fun update(deltaTime: Float, gameState: GameStateManager, player: Player) {
        val iter = orbs.iterator()
        while (iter.hasNext()) {
            val orb = iter.next()

            // Advance physics
            orb.update(deltaTime, gameState.scrollSpeed, gameState)

            // Collection check
            if (orb.checkCollection(player.hitbox)) {
                orb.isActive    = false
                orb.isCollected = true
                // Emit sparkle burst at orb position
                ParticleManager.emit(FxPreset.SEED_COLLECT, orb.x, orb.y)
                // Update game state (handles bloom trigger)
                gameState.collectSeed()
            }

            if (!orb.isActive) {
                iter.remove()
            }
        }
    }

    // ── Draw ──────────────────────────────────────────────────────────────

    /**
     * Draw all active orbs. Call inside CameraSystem.applyTo() block.
     * @param bloomFraction 0..1 — how full the Bloom Meter is (drives orb colour).
     */
    fun draw(canvas: Canvas, bloomFraction: Float) {
        for (orb in orbs) {
            orb.draw(canvas, bloomFraction)
        }
    }

    /** Clear all orbs on run reset. */
    fun reset() = orbs.clear()
}
