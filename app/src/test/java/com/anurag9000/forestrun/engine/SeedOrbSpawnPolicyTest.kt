package com.anurag9000.forestrun.engine

import android.graphics.RectF
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.systems.SeedOrb
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
}
