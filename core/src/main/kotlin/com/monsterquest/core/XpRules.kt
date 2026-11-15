package com.monsterquest.core

/**
 * Keeps levelling fair, so neither the hero nor the team races ahead of
 * the world (enemies level with the hero):
 * - monsters can't get more than [MONSTER_LEAD] levels above the hero; XP
 *   past that is saved and pays out as the hero levels up;
 * - beating foes far below you is worth little (see [gapFactor]);
 * - gym training is limited to a few sessions a day, each worth less.
 */
object XpRules {
    /** How far above the hero a monster can level. */
    const val MONSTER_LEAD = 5
    /** Up to this many levels above a foe, XP is full... */
    const val FREE_GAP = 2
    /** ...shrinking to [MIN_SHARE] at this many levels above it. */
    const val ZERO_GAP = 10
    const val MIN_SHARE = 0.05
    /** Gym sessions a day, and the share of a level each one gives. */
    val TRAINING_SHARES = listOf(0.30, 0.20, 0.10)

    /** The highest level a monster can reach right now. */
    fun monsterCap(state: GameState): Int = monsterCap(state.hero.level)
    /**
     * How high a monster can grow: [MONSTER_LEAD] levels above the hero. Past
     * level 100 the rest is ascension ranks (a level-100 monster with a hero at
     * level 103 can reach rank 8).
     */
    fun monsterCap(heroLevel: Int): Int = heroLevel + MONSTER_LEAD

    /** Share of XP earned for beating a foe at [foeLevel] at your level [myLevel]. */
    fun gapFactor(myLevel: Int, foeLevel: Int): Double {
        val gap = myLevel - foeLevel
        if (gap <= FREE_GAP) return 1.0
        return (1.0 - (gap - FREE_GAP).toDouble() / (ZERO_GAP - FREE_GAP)).coerceAtLeast(MIN_SHARE)
    }
}
