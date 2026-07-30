package com.anurag9000.forestrun.systems

import android.graphics.Canvas
import com.anurag9000.forestrun.engine.GameStateManager
import com.anurag9000.forestrun.engine.HapticManager
import com.anurag9000.forestrun.engine.RuntimeWorkloadTelemetry
import com.anurag9000.forestrun.engine.SfxManager
import com.anurag9000.forestrun.entities.Player
import kotlin.random.Random

/** Manages the lifecycle of all [SeedOrb] collectibles. */
class SeedOrbManager(
    private val randomFloat: () -> Float = { Random.nextFloat() }
) {
    companion object {
        const val MAX_ORBS = 6
        const val BASE_SPAWN_RATE = 0.95f
        const val SPAWN_HEIGHT_MIN = 55f
        const val SPAWN_HEIGHT_MAX = 115f
    }

    private val orbs = mutableListOf<SeedOrb>()

    internal val activeOrbCount: Int
        get() = orbs.size

    /**
     * Attempts to spawn an orb near the supplied reachable staging point.
     * `spawnRate=1` means the current 95% base chance; callers may boost it.
     * Returns true only when a new Orb was actually added.
     */
    fun trySpawn(centreX: Float, topY: Float, spawnRate: Float = 1.0f): Boolean {
        if (orbs.size >= MAX_ORBS) return false
        if (!centreX.isFinite() || !topY.isFinite() || !spawnRate.isFinite() || spawnRate <= 0f) {
            return false
        }

        val chance = (BASE_SPAWN_RATE * spawnRate).coerceIn(0f, 1f)
        if (nextUnitFloat() > chance) return false

        val offsetY = SPAWN_HEIGHT_MIN +
            nextUnitFloat() * (SPAWN_HEIGHT_MAX - SPAWN_HEIGHT_MIN)
        val spawnX = finiteCoordinate(
            centreX.toDouble() + (nextUnitFloat() - 0.5f).toDouble() * 60.0
        )
        val spawnY = finiteCoordinate(topY.toDouble() - offsetY.toDouble())
        orbs.add(SeedOrb(x = spawnX, y = spawnY))
        RuntimeWorkloadTelemetry.publishSeedOrbs(orbs.size)
        return true
    }

    fun update(deltaTime: Float, gameState: GameStateManager, player: Player) {
        if (!deltaTime.isFinite() || deltaTime < 0f) {
            RuntimeWorkloadTelemetry.publishSeedOrbs(orbs.size)
            return
        }

        var orbIndex = 0
        while (orbIndex < orbs.size) {
            val orb = orbs[orbIndex]
            orb.update(deltaTime, gameState.scrollSpeed, gameState)

            if (orb.checkCollection(player.hitbox)) {
                ParticleManager.emit(FxPreset.SEED_COLLECT, orb.centreX, orb.centreY)
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

    private fun nextUnitFloat(): Float =
        randomFloat().takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.5f

    private fun finiteCoordinate(value: Double): Float =
        value.coerceIn(-Float.MAX_VALUE.toDouble(), Float.MAX_VALUE.toDouble()).toFloat()
}
