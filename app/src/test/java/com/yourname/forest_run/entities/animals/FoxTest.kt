package com.yourname.forest_run.entities.animals

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.PersistentMemoryManager
import com.yourname.forest_run.engine.SpriteManager
import com.yourname.forest_run.entities.EntityType
import com.yourname.forest_run.entities.Player
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FoxTest {

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
    fun `fox repeat-memory charm strengthens aura flag and remembered-trick reward`() {
        val baselineFox = Fox(
            context = context,
            startX = 520f,
            groundY = 885.6f,
            sprite = spriteManager.foxSprite.copy()
        )
        val baselineState = GameStateManager(context)

        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.FOX) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.FOX) }
        repeat(4) { PersistentMemoryManager.recordPass(context, EntityType.FOX) }

        val familiarFox = Fox(
            context = context,
            startX = 520f,
            groundY = 885.6f,
            sprite = spriteManager.foxSprite.copy()
        )
        val familiarState = GameStateManager(context)
        val player = Player(1920, 1080, spriteManager)

        setBooleanField(baselineFox, "hasJumped", true)
        setBooleanField(familiarFox, "hasJumped", true)

        baselineFox.performUniqueAction(player, baselineState)
        familiarFox.performUniqueAction(player, familiarState)

        assertTrue(booleanField(familiarFox, "repeatMemoryCharm"))
        assertTrue(familiarState.seedsThisRun > baselineState.seedsThisRun)
        assertTrue(familiarState.score > baselineState.score)
    }

    private fun booleanField(fox: Fox, name: String): Boolean {
        val field = Fox::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.getBoolean(fox)
    }

    private fun setBooleanField(fox: Fox, name: String, value: Boolean) {
        val field = Fox::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.setBoolean(fox, value)
    }
}
