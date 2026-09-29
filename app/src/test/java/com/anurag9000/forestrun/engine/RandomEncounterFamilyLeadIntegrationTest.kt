package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RandomEncounterFamilyLeadIntegrationTest {
    private lateinit var context: Context
    private lateinit var sprites: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        sprites = SpriteManager(context)
    }

    @Test
    fun `every ordinary family preserves the sampled full-action lead after creation tick`() {
        val ordinaryFamilies = Biome.entries
            .flatMap { it.preferredPool }
            .distinct()
        assertEquals(EntityType.entries.toSet(), ordinaryFamilies.toSet())

        for (height in listOf(360, 720, 1_080, 1_440)) {
            val eligible = EntityFactory.eligibleOrdinaryPool(
                ordinaryFamilies,
                height.toFloat()
            )
            assertTrue("height=$height must retain an ordinary pool", eligible.isNotEmpty())
            if (!EntityFactory.canStageBamboo(height.toFloat())) {
                assertTrue(EntityType.BAMBOO !in eligible)
            }

            for (type in eligible) {
                val player = Player(1_920, height, sprites)
                val state = maxSpeedState()
                val manager = EntityManager(
                    context = context,
                    screenWidth = 1_920f,
                    screenHeight = height.toFloat(),
                    spriteManager = sprites
                )
                val fastestApproach =
                    state.undebuffedScrollSpeed * MAX_IMMEDIATE_APPROACH_MULTIPLIER
                val requiredLead =
                    SpawnPacing.minimumRandomEncounterLeadPx(state.undebuffedScrollSpeed)
                val startX = player.hitbox.right + requiredLead
                val variant = if (type == EntityType.DOG) {
                    EncounterVariant.DOG_HAZARD
                } else {
                    EncounterVariant.DEFAULT
                }

                manager.spawn(
                    type = type,
                    variant = variant,
                    startX = startX,
                    recordPersistence = true,
                    random = Random(0xF0E57 + type.ordinal)
                )
                assertEquals(
                    "height=$height type=$type did not stage exactly once",
                    1,
                    manager.activeEntities.size
                )

                // Production GameView advances Player before EntityManager.
                player.update(FrameInputAdmission.MAX_DELTA_SECONDS, state.scrollSpeed)
                manager.update(
                    deltaTime = FrameInputAdmission.MAX_DELTA_SECONDS,
                    gameState = state,
                    player = player,
                    runMode = RunMode.DEBUG_SCENARIO
                )

                val entity = manager.activeEntities.singleOrNull()
                    ?: error("height=$height type=$type retired on its creation tick")
                assertNull(
                    "height=$height type=$type collided on creation tick",
                    manager.checkCollisions(player, state)
                )
                val remainingLead =
                    entity.encounterBounds.left - player.hitbox.right
                assertTrue(
                    "height=$height type=$type has nonfinite/negative encounter lead: $remainingLead",
                    remainingLead.isFinite() && remainingLead >= 0f
                )

                val apex = EncounterActionFeasibility.observe(
                    leadDistancePx = Float.MAX_VALUE,
                    approachSpeedPxPerSec = fastestApproach,
                    requiredVerticalClearancePx = 0f,
                    jumpUpwardSpeedPxPerSec = -Player.MAX_JUMP_FORCE,
                    gravityPxPerSecSquared = Player.GRAVITY,
                    gestureDecisionSeconds = 0f
                ).maximumBallisticRisePx
                val reaction = EncounterActionFeasibility.observe(
                    leadDistancePx = remainingLead,
                    approachSpeedPxPerSec = fastestApproach,
                    requiredVerticalClearancePx = apex,
                    jumpUpwardSpeedPxPerSec = -Player.MAX_JUMP_FORCE,
                    gravityPxPerSecSquared = Player.GRAVITY,
                    gestureDecisionSeconds = GESTURE_DECISION_SECONDS,
                    safetyMarginSeconds = ACTION_SAFETY_SECONDS
                )
                assertTrue(
                    "height=$height type=$type consumed the generic full-action lead " +
                        "(remaining=$remainingLead required=$requiredLead)",
                    reaction.jumpFeasible
                )
                assertTrue(
                    "height=$height type=$type lost even the duck decision budget",
                    reaction.duckFeasible
                )
            }
        }
    }

    private fun maxSpeedState(): GameStateManager {
        val state = GameStateManager(context) { false }
        repeat(3) { state.update(5_000f) }
        assertEquals(GameConstants.MAX_SCROLL_SPEED, state.undebuffedScrollSpeed, 0f)
        return state
    }

    private companion object {
        const val MAX_IMMEDIATE_APPROACH_MULTIPLIER = 1.15f
        const val GESTURE_DECISION_SECONDS = 0.075f
        const val ACTION_SAFETY_SECONDS = 0.08f
    }
}
