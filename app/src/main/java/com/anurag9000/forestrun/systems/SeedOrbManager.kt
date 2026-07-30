package com.anurag9000.forestrun.systems

import android.graphics.Canvas
import com.anurag9000.forestrun.engine.GameStateManager
import com.anurag9000.forestrun.engine.HapticManager
import com.anurag9000.forestrun.engine.SfxManager
import com.anurag9000.forestrun.engine.RuntimeWorkloadTelemetry
import com.anurag9000.forestrun.entities.Player
import kotlin.random.Random

/** Manages the lifecycle of all [SeedOrb] collectibles. */
class SeedOrbManager {
    companion object {
        const val MAX_ORBS = 6
        const val BASE_SPAWN_RATE = 0.95f
        const val SPAWN_HEIGHT_MIN = 55f
        const val SPAWN_HEIGHT_MAX = 115f
    }

    private val orbs = mutableListOf<SeedOrb>()

    /**
     * Attempts to spawn an orb near the supplied reachable staging point.
     * `spawnRate=1` means the current 95% base chance; callers may boost it.
     */
    fun trySpawn(centreX: Float, topY: Float, spawnRate: Float = 1.0f) {
        if (orbs.size >= MAX_ORBS) return

        val chance = (BASE_SPAWN_RATE * spawnRate).coerceIn(0f, 1f)
        if (Random.nextFloat() > chance) return

        val offsetY = SPAWN_HEIGHT_MIN +
            Random.nextFloat() * (SPAWN_HEIGHT_MAX - SPAWN_HEIGHT_MIN)
        orbs.add(
            SeedOrb(
                x = centreX + (Random.nextFloat() - 0.5f) * 60f,
                y = topY - offsetY
            )
        )
    }

    fun update(deltaTime: Float, gameState: GameStateManager, player: Player) {
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
        RuntimeWorkloadTelemetry.publishSeedOrbs(orbs.size)
    }

    fun draw(canvas: Canvas, bloomFraction: Float) {
        var orbIndex = 0
        while (orbIndex < orbs.size) {
            orbs[orbIndex].draw(canvas, bloomFraction)
            orbIndex++
        }
    }

    fun reset() {
        orbs.clear()
        RuntimeWorkloadTelemetry.publishSeedOrbs(0)
    }
}
