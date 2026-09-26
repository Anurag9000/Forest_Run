package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BloomWorldReactionTest {

    private class CollidingCreature(val label: String) {
        override fun hashCode(): Int = 1
        override fun equals(other: Any?): Boolean = other is CollidingCreature
    }

    @Test
    fun `distinct objects remain independently eligible even with identical hash and equality`() {
        val reacted = BloomReactionIdentityLedger<CollidingCreature>()
        val first = CollidingCreature("first")
        val second = CollidingCreature("second")

        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(first, second)
        assertFalse(reacted.contains(first))
        assertFalse(reacted.contains(second))

        assertTrue(reacted.mark(first))
        assertTrue(reacted.contains(first))
        assertFalse(reacted.mark(first))
        assertFalse(reacted.contains(second))
        assertTrue(reacted.mark(second))
        assertTrue(reacted.contains(second))
        assertFalse(reacted.mark(second))

        reacted.clear()
        assertFalse(reacted.contains(first))
        assertFalse(reacted.contains(second))
        assertTrue(reacted.mark(first))
    }


    @Test
    fun `cue picks distinct families for nearby bloom reactions`() {
        assertEquals(BloomReactionFamily.FLORA, BloomWorldReaction.cueFor(EntityType.LILY_OF_VALLEY).family)
        assertEquals(BloomReactionFamily.TREE, BloomWorldReaction.cueFor(EntityType.JACARANDA).family)
        assertEquals(BloomReactionFamily.BIRD, BloomWorldReaction.cueFor(EntityType.OWL).family)
        assertEquals(BloomReactionFamily.ANIMAL, BloomWorldReaction.cueFor(EntityType.DOG).family)
    }

    @Test
    fun `reaction window only opens for nearby forward entities not yet reacted`() {
        assertTrue(
            BloomWorldReaction.shouldReact(
                playerCenterX = 140f,
                playerCenterY = 220f,
                entityCenterX = 360f,
                entityCenterY = 240f,
                alreadyReacted = false
            )
        )
        assertFalse(
            BloomWorldReaction.shouldReact(
                playerCenterX = 140f,
                playerCenterY = 220f,
                entityCenterX = 620f,
                entityCenterY = 240f,
                alreadyReacted = false
            )
        )
        assertFalse(
            BloomWorldReaction.shouldReact(
                playerCenterX = 140f,
                playerCenterY = 220f,
                entityCenterX = 360f,
                entityCenterY = 240f,
                alreadyReacted = true
            )
        )
    }
}
