package com.monsterquest.core

/**
 * Past level 100 monsters ascend instead of levelling: each ascension rank
 * adds 1% to every stat, about what a level used to. Your monsters can ascend
 * up to [XpRules.MONSTER_LEAD] ranks past the enemies, and enemies at level
 * 100 ascend one rank for each hero level past 100, so fights keep pace with
 * your hero instead of getting easier.
 */
object Ascension {
    /** Stats per rank, in percent. */
    const val PCT_PER_RANK = 1
    /** XP for each rank. */
    val XP_PER_RANK: Int = monsterXpForNext(MAX_MONSTER_LEVEL) / 4

    fun factor(ranks: Int): Double = 1 + PCT_PER_RANK * ranks.coerceAtLeast(0) / 100.0

    /** Ranks a level-100 enemy has for a hero of [heroLevel]. */
    fun enemyRanks(heroLevel: Int): Int = (heroLevel - MAX_MONSTER_LEVEL).coerceAtLeast(0)

    /** The most ranks your monsters can have for a hero of [heroLevel]. */
    fun cap(heroLevel: Int): Int = (XpRules.monsterCap(heroLevel) - MAX_MONSTER_LEVEL).coerceAtLeast(0)
}
