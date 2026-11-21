package com.monsterquest.core

import kotlin.random.Random

/** Titles the hero earns for milestones, shown by their name (the best one first). */
object Titles {
    const val CENTURION = "Centurion"
    const val MYTHBREAKER = "Mythbreaker"

    /** Best first. */
    private val ORDER = listOf(MYTHBREAKER, CENTURION)

    /** The title to show by the hero's name, or null. */
    fun shown(state: GameState): String? = ORDER.firstOrNull { it in state.titles }

    fun grant(state: GameState, title: String): GameState =
        if (title in state.titles) state else state.copy(titles = state.titles + title)
}

/**
 * Level 100: the Centurion reward, once. The hero takes the title (and a
 * glowing frame on the map) and chooses one legendary egg, with 3 perfect
 * stats, to hatch by walking.
 */
object Centurion {
    const val LEVEL = 100
    const val PERFECT_STATS = 3

    /** Waiting to be claimed. */
    fun due(state: GameState): Boolean = state.hero.level >= LEVEL && Titles.CENTURION !in state.titles

    /** The legendaries you can choose an egg of. */
    fun choices(): List<Species> = Bestiary.all.filter { it.rarity == Rarity.LEGENDARY && it.stage == 1 }

    fun claim(state: GameState, speciesId: String, rng: Random = Random.Default): Update {
        if (!due(state)) return Update(state, "")
        val sp = choices().find { it.id == speciesId } ?: return Update(state, "Choose a legendary.")
        // A milestone egg always fits, even past the usual slots.
        val egg = Eggs.gift(sp, rng, PERFECT_STATS)
        val s = Titles.grant(state, Titles.CENTURION).copy(eggs = state.eggs + egg)
        return Update(
            s,
            "Centurion! You've reached ${Stars.levelText(LEVEL)}. A ${sp.name} egg with $PERFECT_STATS perfect stats is yours: walk to hatch it.",
            rewards = listOf(Reward.Egg(sp.family.removeSuffix(" line"))),
            rewardTitle = "Centurion",
        )
    }
}
