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
class CatTest {

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
    fun `cat repeat-friend history strengthens aura flag and familiar pass reward`() {
        val baselineCat = Cat(
            context = context,
            startX = 520f,
            groundY = 885.6f,
            sprite = spriteManager.catSprite.copy()
        )
        val baselineState = GameStateManager(context)

        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.CAT) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.CAT) }
        repeat(4) { PersistentMemoryManager.recordPass(context, EntityType.CAT) }

        val familiarCat = Cat(
            context = context,
            startX = 520f,
            groundY = 885.6f,
            sprite = spriteManager.catSprite.copy()
        )
        val familiarState = GameStateManager(context)
        val player = Player(1920, 1080, spriteManager)

        baselineCat.performUniqueAction(player, baselineState)
        familiarCat.performUniqueAction(player, familiarState)

        assertTrue(booleanField(familiarCat, "repeatFriendHistory"))
        assertTrue(familiarState.seedsThisRun > baselineState.seedsThisRun)
        assertTrue(familiarState.score > baselineState.score)
    }

    private fun booleanField(cat: Cat, name: String): Boolean {
        val field = Cat::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.getBoolean(cat)
    }
}
