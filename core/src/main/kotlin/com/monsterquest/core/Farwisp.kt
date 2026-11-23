package com.monsterquest.core

/**
 * The Farwisp: a rare ball of drifting mist that lets you take on one thing on
 * the map from anywhere (a dungeon, a den, a shrine, a villain camp or a
 * nomad) without walking to it. It can't be bought: it only turns up in loot
 * boxes, more often in better ones. Every player is given one, once.
 */
object Farwisp {
    /** Chance a loot box holds one, by box level (1..4). */
    fun boxChance(level: Int): Double = when (level) {
        1 -> 0.03
        2 -> 0.06
        3 -> 0.1
        else -> 0.18
    }

    fun count(state: GameState): Int = state.count(Item.FARWISP)

    /** Spends one, or null if there's none. */
    fun spend(state: GameState): GameState? {
        val n = count(state)
        if (n <= 0) return null
        return state.copy(inventory = state.inventory + (Item.FARWISP to n - 1))
    }

    /** The one every player is given (even past a full bag), once. */
    fun gift(state: GameState): Update? {
        if (state.farwispGifted) return null
        return Update(
            state.copy(farwispGifted = true, inventory = state.inventory + (Item.FARWISP to count(state) + 1)),
            "A Farwisp drifts into your Bag!",
        )
    }
}
