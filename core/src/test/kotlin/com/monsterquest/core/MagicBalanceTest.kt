package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A Mage's magic keeps up with monsters: at every level, a rank-3 bolt hits a
 * little harder than a first-form monster's best move, and a rank-5 bolt
 * about as hard as a fully evolved monster's.
 */
class MagicBalanceTest {
    private fun factor(atk: Double, def: Double, lvl: Int) = atk / (atk + def) * (1 + lvl * 0.08)

    /** Average damage factor of each stage's best move at [lvl] against a typical wild monster's Defense. */
    private fun monsters(lvl: Int, def: Double): Map<Int, Double> = (1..3).associateWith { st ->
        Bestiary.all.filter { it.stage == st && !it.genderless }.mapNotNull { sp ->
            val m = Monster.create(sp, lvl, Random(sp.id.hashCode()), evolve = false)
            val mv = Moves.learned(sp, lvl).filter { it.isDamaging }.maxByOrNull { it.power * (if (it.type in sp.types) 1.5 else 1.0) } ?: return@mapNotNull null
            mv.power * (if (mv.type in sp.types) 1.5 else 1.0) * factor(m.stats.attack.toDouble(), def, lvl)
        }.average()
    }

    /** A Mage putting two of every three points into Magic, casting a bolt of [rank]. */
    private fun mage(lvl: Int, rank: Int, def: Double): Double {
        val magic = STARTING_ATTRIBUTE + ((lvl - 1) * STAT_POINTS_PER_LEVEL * 2 / 3)
        val h = Hero(level = lvl, heroClass = HeroClass.MAGE, classChosen = true, attributes = HeroAttribute.entries.associateWith { STARTING_ATTRIBUTE } + (HeroAttribute.MAGIC to magic))
        return Spells.ranked(Spells.byId("ember_bolt")!!, rank, lvl).power * factor(h.magic.toDouble(), def, lvl)
    }

    @Test
    fun aMagesBoltsKeepUpWithMonsters() {
        // Rank 3 opens at level 20 and rank 5 at 40; check from there on.
        for (lvl in listOf(20, 25, 30, 35, 40, 50, 60, 80, 100)) {
            val def = Bestiary.wild.filter { it.minWildLevel <= lvl }
                .map { Monster.create(it, lvl, Random(it.id.hashCode()), evolve = false).stats.defense.toDouble() }.average()
            val m = monsters(lvl, def)
            val r3 = mage(lvl, 3, def) / m.getValue(1)
            assertTrue(r3 in 1.0..1.25, "level $lvl: rank 3 is ${"%.2f".format(r3)}x a first form")
            if (lvl >= 40) {
                val r5 = mage(lvl, 5, def) / m.getValue(3)
                assertTrue(r5 in 0.9..1.15, "level $lvl: rank 5 is ${"%.2f".format(r5)}x a final form")
            }
        }
    }
}
