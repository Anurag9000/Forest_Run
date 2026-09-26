package com.anurag9000.forestrun.entities.animals

import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.GameStateManager
import com.anurag9000.forestrun.engine.PersistentMemoryManager
import com.anurag9000.forestrun.engine.SpriteManager
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DogTest {

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
    fun `bonded buddy dog gains escort celebration flag dialogue depth and stronger reward`() {
        val baselineDog = Dog(
            context = context,
            startX = 520f,
            groundY = 885.6f,
            screenWidth = 1920f,
            sprite = spriteManager.dogSprite.copy(),
            isBuddy = true
        )
        val baselineState = GameStateManager(context)

        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.DOG) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.DOG) }

        val bondedDog = Dog(
            context = context,
            startX = 520f,
            groundY = 885.6f,
            screenWidth = 1920f,
            sprite = spriteManager.dogSprite.copy(),
            isBuddy = true
        )
        val bondedState = GameStateManager(context)
        val player = Player(1920, 1080, spriteManager)

        baselineDog.performUniqueAction(player, baselineState)
        bondedDog.performUniqueAction(player, bondedState)

        assertTrue(booleanField(bondedDog, "buddyCelebration"))
        assertEquals(4, listFieldSize(bondedDog, "buddyDialogue"))
        assertTrue(bondedState.seedsThisRun > baselineState.seedsThisRun)
        assertTrue(bondedState.score > baselineState.score)
    }

    @Test
    fun `projectile mercy ring cannot hide direct dog body hit`() {
        val dog = Dog(
            context = context,
            startX = 520f,
            groundY = 885.6f,
            screenWidth = 1920f,
            sprite = spriteManager.dogSprite.copy(),
            isBuddy = false
        )
        val player = Player(1920, 1080, spriteManager)
        val gameState = GameStateManager(context)

        // Trigger the first bark and move its projectile just beyond the
        // player's right edge: inside mercy padding, outside direct overlap.
        dog.update(deltaTime = 1f, scrollSpeed = 0f)
        val field = Dog::class.java.getDeclaredField("projectiles")
        field.isAccessible = true
        val projectiles = field.get(dog) as List<*>
        assertTrue(projectiles.isNotEmpty())
        val projectile = projectiles.first()!!
        val rectangleField = projectile.javaClass.getDeclaredField("rect")
        rectangleField.isAccessible = true
        val projectileRect = rectangleField.get(projectile) as RectF

        player.hitbox.set(dog.hitbox)
        projectileRect.set(
            player.hitbox.right + 2f,
            player.hitbox.top,
            player.hitbox.right + 24f,
            player.hitbox.bottom
        )
        assertEquals(CollisionResult.HIT, dog.onCollision(player, gameState))
    }

    private fun booleanField(dog: Dog, name: String): Boolean {
        val field = Dog::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.getBoolean(dog)
    }

    private fun listFieldSize(dog: Dog, name: String): Int {
        val field = Dog::class.java.getDeclaredField(name)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return (field.get(dog) as List<Any>).size
    }
}
