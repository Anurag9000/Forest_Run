package com.anurag9000.forestrun.engine

import android.graphics.RectF
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.systems.SeedOrb
import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SeedOrbSpawnPolicyTest {

    @Test
    fun `full random band for a full-height tree stays visible and jump reachable`() {
        val screenHeight = 1_080f
        val groundY = screenHeight * 0.82f
        val point = SeedOrbSpawnPolicy.forCleanPass(
            encounterBounds = RectF(300f, 0f, 700f, groundY),
            playerBounds = RectF(400f, groundY - Player.BASE_HEIGHT, 472f, groundY),
            playerGroundY = groundY,
            screenWidth = 1_920f,
            screenHeight = screenHeight
        )

        val visibleMargin = SeedOrb.RADIUS + SeedOrb.HALO_MARGIN
        assertTrue(point.minimumPossibleCentreY >= visibleMargin)
        assertTrue(point.maximumPossibleCentreY <= screenHeight - visibleMargin)
        assertTrue(point.minimumPossibleCentreY >= 300f)
        assertTrue(point.centreX >= 472f + 120f)
    }

    @Test
    fun `ordinary ground encounter keeps a contextual reward height`() {
        val groundY = 885.6f
        val point = SeedOrbSpawnPolicy.forCleanPass(
            encounterBounds = RectF(650f, 650f, 780f, groundY),
            playerBounds = RectF(420f, 785.6f, 492f, groundY),
            playerGroundY = groundY,
            screenWidth = 1_920f,
            screenHeight = 1_080f
        )

        assertTrue(point.minimumPossibleCentreY in 500f..700f)
        assertTrue(point.maximumPossibleCentreY in 500f..700f)
    }

    @Test
    fun `small landscape surface still produces finite visible staging`() {
        val point = SeedOrbSpawnPolicy.forCleanPass(
            encounterBounds = RectF(Float.NaN, Float.NEGATIVE_INFINITY, Float.NaN, 260f),
            playerBounds = RectF(160f, 190f, 232f, 290f),
            playerGroundY = 290f,
            screenWidth = 640f,
            screenHeight = 360f
        )

        assertTrue(point.centreX.isFinite())
        assertTrue(point.topY.isFinite())
        assertTrue(point.minimumPossibleCentreY.isFinite())
        assertTrue(point.maximumPossibleCentreY.isFinite())
        assertTrue(point.minimumPossibleCentreY <= point.maximumPossibleCentreY)
    }

    @Test
    fun `production surface and geometry sweep keeps every random orb band finite visible and ahead`() {
        val widths = floatArrayOf(640f, 960f, 1_280f, 1_920f, 2_560f)
        val heights = floatArrayOf(360f, 540f, 720f, 1_080f, 1_440f)
        val random = Random(0x5EED0B)
        var caseIndex = 0

        for (width in widths) {
            for (height in heights) {
                val groundY = height * 0.82f
                repeat(96) {
                    val playerLeft = random.nextFloat() * width * 0.65f
                    val playerRight = playerLeft + Player.BASE_WIDTH
                    val playerBounds = RectF(
                        playerLeft,
                        groundY - Player.BASE_HEIGHT,
                        playerRight,
                        groundY
                    )
                    val encounterLeft = random.nextFloat() * width * 1.5f - width * 0.25f
                    val encounterWidth = 24f + random.nextFloat() * 420f
                    val encounterTop = random.nextFloat() * groundY
                    val point = SeedOrbSpawnPolicy.forCleanPass(
                        encounterBounds = RectF(
                            encounterLeft,
                            encounterTop,
                            encounterLeft + encounterWidth,
                            groundY
                        ),
                        playerBounds = playerBounds,
                        playerGroundY = groundY,
                        screenWidth = width,
                        screenHeight = height
                    )

                    val visibleMargin = SeedOrb.RADIUS + SeedOrb.HALO_MARGIN
                    val minimumAhead = playerRight + maxOf(120f, width * 0.08f)
                    val fullJumpRise =
                        Player.MAX_JUMP_FORCE * Player.MAX_JUMP_FORCE / (2f * Player.GRAVITY)
                    val highestPhysicallyReachableCentre =
                        groundY - Player.BASE_HEIGHT - fullJumpRise
                    val lowestPhysicallyReachableCentre = groundY - Player.HITBOX_INSET

                    assertTrue("case=$caseIndex centreX", point.centreX.isFinite())
                    assertTrue("case=$caseIndex topY", point.topY.isFinite())
                    assertTrue(
                        "case=$caseIndex minimumPossibleCentreY",
                        point.minimumPossibleCentreY.isFinite()
                    )
                    assertTrue(
                        "case=$caseIndex maximumPossibleCentreY",
                        point.maximumPossibleCentreY.isFinite()
                    )
                    assertTrue(
                        "case=$caseIndex ordered band",
                        point.minimumPossibleCentreY <= point.maximumPossibleCentreY
                    )
                    assertTrue("case=$caseIndex ahead", point.centreX + 0.001f >= minimumAhead)
                    assertTrue(
                        "case=$caseIndex visible top",
                        point.minimumPossibleCentreY + 0.001f >= visibleMargin
                    )
                    assertTrue(
                        "case=$caseIndex visible bottom",
                        point.maximumPossibleCentreY <= height - visibleMargin + 0.001f
                    )
                    assertTrue(
                        "case=$caseIndex jump envelope top",
                        point.minimumPossibleCentreY + 0.001f >= highestPhysicallyReachableCentre
                    )
                    assertTrue(
                        "case=$caseIndex jump envelope bottom",
                        point.maximumPossibleCentreY <= lowestPhysicallyReachableCentre + 0.001f
                    )
                    caseIndex++
                }
            }
        }
    }

    @Test
    fun `malformed geometry and dimensions always fail closed to finite ordered staging`() {
        val malformed = floatArrayOf(
            Float.NaN,
            Float.NEGATIVE_INFINITY,
            Float.POSITIVE_INFINITY,
            -Float.MAX_VALUE,
            -1f,
            0f,
            Float.MAX_VALUE
        )
        var caseIndex = 0
        for (value in malformed) {
            val point = SeedOrbSpawnPolicy.forCleanPass(
                encounterBounds = RectF(value, value, value, value),
                playerBounds = RectF(value, value, value, value),
                playerGroundY = value,
                screenWidth = value,
                screenHeight = value
            )
            assertTrue("case=$caseIndex centreX", point.centreX.isFinite())
            assertTrue("case=$caseIndex topY", point.topY.isFinite())
            assertTrue(
                "case=$caseIndex minimumPossibleCentreY",
                point.minimumPossibleCentreY.isFinite()
            )
            assertTrue(
                "case=$caseIndex maximumPossibleCentreY",
                point.maximumPossibleCentreY.isFinite()
            )
            assertTrue(
                "case=$caseIndex ordered band",
                point.minimumPossibleCentreY <= point.maximumPossibleCentreY
            )
            caseIndex++
        }
    }
}
