package com.monsterquest.core

/**
 * The Move Tutor: teaches a monster a move of its own type that it hasn't
 * learned yet (often one it would only learn many levels later), for candy
 * from its own line plus gold. Better moves cost more, and each needs the
 * monster to be within [LEVELS_AHEAD] levels of when it would learn it.
 */
object MoveTutor {
    const val LEVELS_AHEAD = 10
    /** Moves without a level in the type's list are priced as if learned at this level. */
    private const val DEFAULT_RANK = 25

    data class Offer(
        val move: Move,
        /** The level the move belongs to in its type's list: sets the price and when it opens. */
        val rank: Int,
        val minLevel: Int,
        val candy: Int,
        val gold: Int,
    )

    /** Every family's signature moves: only their own family can learn them. */
    private val signatures: Set<String> by lazy { Moves.familyMoves.values.flatten().map { it.second }.toSet() }

    /** The moves [m] could buy, cheapest first (moves it already knows are left out). */
    fun offers(m: Monster): List<Offer> {
        val sp = m.species
        val types = listOfNotNull(sp.element, sp.secondary)
        val known = m.learnedMoves.map { it.id }.toSet()
        val ranked = types.flatMap { Moves.learnsets[it].orEmpty() }.groupBy({ it.second }, { it.first }).mapValues { it.value.min() }
        return Moves.all
            .filter { it.type in types && it.id !in known && it.id !in signatures }
            .map { mv ->
                val rank = ranked[mv.id] ?: DEFAULT_RANK
                Offer(mv, rank, minLevel = (rank - LEVELS_AHEAD).coerceAtLeast(1), candy = 3 + rank / 5, gold = 60 + rank * 15)
            }
            .sortedWith(compareBy<Offer> { it.rank }.thenBy { it.move.name })
    }

    /** Why [m] can't buy [o] right now, or null if it can. */
    fun blocker(state: GameState, m: Monster, o: Offer): String? = when {
        m.level < o.minLevel -> "Needs Lv ${o.minLevel}"
        GameEngine.candyOf(state, m.species) < o.candy -> "Needs ${o.candy} ${m.species.family} candy"
        state.gold < o.gold -> "Needs ${o.gold} gold"
        else -> null
    }

    /** Teaches monster [uid] the move [moveId]; it goes into an empty slot of a custom loadout. */
    fun buy(state: GameState, uid: String, moveId: String): Update {
        val m = state.monsters.find { it.uid == uid } ?: return Update(state, "Monster not found.")
        val o = offers(m).find { it.move.id == moveId } ?: return Update(state, "${m.displayName} can't learn that from the tutor.")
        blocker(state, m, o)?.let { return Update(state, "$it to teach ${m.displayName} ${o.move.name}.") }
        val family = m.species.family
        val custom = m.moves
        val taught = m.copy(
            taught = m.taught + moveId,
            moves = if (custom != null && custom.size < Moves.MAX_MOVES) custom + moveId else custom,
        )
        return Update(
            state.copy(
                gold = state.gold - o.gold,
                candy = state.candy + (family to GameEngine.candyOf(state, m.species) - o.candy),
                monsters = state.monsters.map { if (it.uid == uid) taught else it },
            ),
            "${m.displayName} learned ${o.move.name}! (-${o.candy} candy, -${o.gold} gold)" +
                if (taught.moves != null && moveId !in taught.moves) " Swap it in from its moves." else "",
        )
    }
}
