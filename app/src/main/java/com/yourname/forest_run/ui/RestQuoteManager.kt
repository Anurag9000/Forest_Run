package com.yourname.forest_run.ui

import android.content.Context
import com.yourname.forest_run.engine.Biome
import com.yourname.forest_run.engine.RunSummary
import com.yourname.forest_run.engine.StoryFragmentSystem
import com.yourname.forest_run.entities.EntityType

/**
 * Builds short reflective quotes for the post-run rest beat.
 */
object RestQuoteManager {

    fun quoteFor(context: Context, summary: RunSummary, biome: Biome, killer: EntityType?): String =
        StoryFragmentSystem.restQuote(context, summary, biome, killer)
}
