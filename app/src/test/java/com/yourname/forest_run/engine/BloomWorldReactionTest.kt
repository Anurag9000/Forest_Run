package com.yourname.forest_run.engine

import com.yourname.forest_run.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BloomWorldReactionTest {

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
