package com.anurag9000.forestrun.entities.birds

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.PersistentMemoryManager
import com.anurag9000.forestrun.engine.GameStateManager
import com.anurag9000.forestrun.engine.SpriteManager
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OwlTest {

    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        spriteManager = SpriteManager(context)
    }

    @Test
    fun `owl derives repeat shadow and familiar night history from persistent state`() {
        repeat(2) { PersistentMemoryManager.recordHit(context, EntityType.OWL) }
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.OWL) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.OWL) }
        repeat(4) { PersistentMemoryManager.recordPass(context, EntityType.OWL) }

        val owl = Owl(
            context = context,
            startX = 560f,
            groundY = 885.6f,
            idleSprite = spriteManager.owlSprite.copy(),
            actionSprite = spriteManager.owlFlying.copy()
        )

        assertTrue(booleanField(owl, "repeatShadowHistory"))
        assertTrue(booleanField(owl, "familiarNightHistory"))
    }

    @Test
    fun `owl enters alert state when the player jumps into its memory line`() {
        repeat(2) { PersistentMemoryManager.recordHit(context, EntityType.OWL) }

        val owl = Owl(
            context = context,
            startX = 560f,
            groundY = 885.6f,
            idleSprite = spriteManager.owlSprite.copy(),
            actionSprite = spriteManager.owlFlying.copy()
        )
        val player = Player(1920, 1080, spriteManager)
        val gameState = GameStateManager(context)

        player.onJumpPressed()
        owl.updatePlayerInteraction(player, gameState)

        assertEquals("ALERT", enumFieldName(owl, "owlState"))
        assertTrue(booleanField(owl, "hasWarned"))
    }

    private fun booleanField(owl: Owl, name: String): Boolean {
        val field = Owl::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.getBoolean(owl)
    }

    private fun enumFieldName(owl: Owl, name: String): String {
        val field = Owl::class.java.getDeclaredField(name)
        field.isAccessible = true
        return requireNotNull(field.get(owl)).toString()
    }
}
