package com.monsterquest.core

/**
 * The bond between the hero and a monster: it grows from the battles it
 * fights at your side and the walks it takes in your team. Each bond level
 * makes it a little stronger, and at the top it refuses to fall once a fight.
 */
object Bonds {
    const val MAX = 5
    /** Bond points for each battle won (or monster tamed) that it fought in. */
    const val PER_BATTLE = 1
    /** Bond points for each [STEPS_PER_POINT] steps walked while it's in your team. */
    const val STEPS_PER_POINT = 1_000
    /** Points needed for bond levels 1 to 5. */
    val THRESHOLDS = listOf(15, 40, 80, 140, 220)
    /** Your buddy (first party slot) walks right beside you: a bond point every this many steps instead. */
    const val BUDDY_STEPS_PER_POINT = 400
    /** And it fights closest to you: this many points per battle instead of [PER_BATTLE]. */
    const val BUDDY_PER_BATTLE = 3

    /** Every bond level adds this much to HP, Attack, Defense and Speed, in percent. */
    const val STAT_PCT_PER_LEVEL = 2

    fun level(points: Int): Int = THRESHOLDS.count { points >= it }

    /** Points to the next level (null when maxed). */
    fun nextAt(points: Int): Int? = THRESHOLDS.firstOrNull { points < it }

    fun statFactor(points: Int): Double = 1 + STAT_PCT_PER_LEVEL * level(points) / 100.0

    /** At bond 5 it holds on with 1 HP from a knockout blow, once a battle. */
    fun steadfast(points: Int): Boolean = level(points) >= MAX

    fun perkText(level: Int): String = when {
        level <= 0 -> "Fight beside it and walk with it to build a bond."
        level < MAX -> "+${STAT_PCT_PER_LEVEL * level}% HP, Attack, Defense and Speed"
        else -> "+${STAT_PCT_PER_LEVEL * level}% HP, Attack, Defense and Speed; Steadfast: once a battle it survives a knockout with 1 HP"
    }

    /** Adds [points] to each monster in [uids]; returns the new state and the names that reached a new bond level. */
    fun add(state: GameState, uids: Collection<String>, points: Int): Pair<GameState, List<String>> {
        if (points <= 0 || uids.isEmpty()) return state to emptyList()
        val grew = mutableListOf<String>()
        val monsters = state.monsters.map { m ->
            if (m.uid !in uids) return@map m
            val after = m.bond + points
            if (level(after) > level(m.bond)) grew += "${m.displayName} (bond ${level(after)})"
            m.copy(bond = after)
        }
        return state.copy(monsters = monsters) to grew
    }

    fun grewText(names: List<String>): String =
        if (names.isEmpty()) "" else " Your bond grows: ${names.joinToString()}!"
}

/** A pairing of types in your team that helps every monster in it. */
enum class Synergy(val label: String, val a: Element, val b: Element, val stat: BattleStat, val text: String) {
    STORM_FRONT("Storm Front", Element.WATER, Element.ELECTRIC, BattleStat.SPEED, "+5% Speed"),
    MAGMA_CORE("Magma Core", Element.FIRE, Element.EARTH, BattleStat.DEFENSE, "+5% Defense"),
    MIASMA("Miasma", Element.GAS, Element.CORRUPT, BattleStat.ATTACK, "+5% Attack"),
    WILDFIRE("Wildfire", Element.FIRE, Element.FLIGHT, BattleStat.ATTACK, "+5% Attack"),
    TAILWIND("Tailwind", Element.FLIGHT, Element.GAS, BattleStat.SPEED, "+5% Speed");

    companion object {
        const val PCT = 5

        /** The synergies a team of monsters has (each type counts from any monster, either of its types). */
        fun of(team: List<Monster>): List<Synergy> {
            val types = team.flatMap { listOfNotNull(it.species.element, it.species.secondary) }.toSet()
            return entries.filter { it.a in types && it.b in types }
        }

        /** The multiplier a set of synergies puts on [stat]. */
        fun factor(active: List<Synergy>, stat: BattleStat): Double = 1 + PCT * active.count { it.stat == stat } / 100.0
    }
}
