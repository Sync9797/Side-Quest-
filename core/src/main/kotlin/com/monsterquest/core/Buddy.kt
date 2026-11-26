package com.monsterquest.core

/**
 * The first monster in your battle party is your buddy: it walks with you and
 * earns its line's candy for the distance you cover. Small, common monsters
 * find one every mile; stronger ones take longer. Changing who leads the party
 * starts the count over.
 */
object Buddy {
    /** Miles per candy, by the monster's own rarity (so it grows as it evolves). */
    fun milesPerCandy(species: Species): Double = when (species.rarity) {
        Rarity.COMMON -> 1.0
        Rarity.UNCOMMON -> 2.0
        Rarity.RARE -> 3.0
        Rarity.EPIC -> 5.0
        Rarity.LEGENDARY -> 10.0
    }

    fun metersPerCandy(species: Species): Double = milesPerCandy(species) * METERS_PER_MILE

    /** The monster in the first party slot, if any. */
    fun of(state: GameState): Monster? = state.partyMonsters.firstOrNull()

    /** A new buddy starts from zero. */
    fun sync(state: GameState): GameState {
        val lead = state.party.firstOrNull()
        return if (state.buddyUid == lead) state else state.copy(buddyUid = lead, buddyMeters = 0.0)
    }

    /** Distance walked toward the next candy with the current buddy. */
    fun progress(state: GameState): Double = if (state.buddyUid == state.party.firstOrNull()) state.buddyMeters else 0.0

    /** Every this far, your buddy sniffs around and may dig something up (half a mile). */
    val DIG_METERS = METERS_PER_MILE / 2

    /** Chance a dig turns something up: better friends look harder. */
    fun findChance(bondLevel: Int): Double = 0.5 + 0.08 * bondLevel.coerceIn(0, Bonds.MAX)

    /** What a dig turns up, weighted; a closer bond finds better things. */
    fun findTable(bondLevel: Int): List<Pair<Item?, Int>> = when {
        bondLevel >= 4 -> listOf(Item.GOLDEN_BERRY to 20, Item.SWEET_BERRY to 15, Item.RESONANT_CRYSTAL to 25, Item.RADIANT_CRYSTAL to 15, null to 25)
        bondLevel >= 2 -> listOf(Item.SWEET_BERRY to 35, Item.BINDING_CRYSTAL to 25, Item.RESONANT_CRYSTAL to 20, null to 20)
        else -> listOf(Item.SWEET_BERRY to 60, Item.BINDING_CRYSTAL to 40)
    }

    /** One dig: an item, a torch (null item, true), or nothing. */
    fun dig(bondLevel: Int, rng: kotlin.random.Random): Pair<Item?, Boolean>? {
        if (rng.nextDouble() >= findChance(bondLevel)) return null
        val table = findTable(bondLevel)
        var roll = rng.nextInt(table.sumOf { it.second })
        for ((item, w) in table) {
            roll -= w
            if (roll < 0) return item to (item == null)
        }
        return null
    }

    /** Walking with your buddy: candy for each full stretch, and now and then something it dug up. */
    fun addWalk(state: GameState, meters: Double): Pair<GameState, String> {
        val (s1, candyText) = addCandyWalk(state, meters)
        val buddy = of(s1) ?: return s1 to candyText
        if (meters <= 0) return s1 to candyText
        var s = s1
        val total = s.buddyDigMeters + meters
        val digs = (total / DIG_METERS).toInt()
        s = s.copy(buddyDigMeters = total - digs * DIG_METERS)
        val found = mutableListOf<String>()
        repeat(digs) { i ->
            val rng = kotlin.random.Random(buddy.uid.hashCode() * 31L + s.buddyDigs)
            s = s.copy(buddyDigs = s.buddyDigs + 1)
            when (val r = dig(buddy.bondLevel, rng)) {
                null -> {}
                else -> if (r.second) {
                    s = Torches.add(s, 1)
                    found += "a torch"
                } else {
                    s = GameEngine.addItems(s, mapOf(r.first!! to 1)).first
                    found += "a ${r.first!!.label}"
                }
            }
        }
        val digText = if (found.isEmpty()) "" else "${buddy.displayName} dug up ${found.joinToString(" and ")}!"
        return s to listOf(candyText, digText).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** Candy for each full stretch walked together. Returns the new state and a note (empty when nothing was earned). */
    private fun addCandyWalk(state: GameState, meters: Double): Pair<GameState, String> {
        var s = sync(state)
        val buddy = of(s) ?: return s to ""
        if (meters <= 0) return s to ""
        val per = metersPerCandy(buddy.species)
        val total = s.buddyMeters + meters
        val candy = (total / per).toInt()
        s = s.copy(buddyMeters = total - candy * per)
        if (candy == 0) return s to ""
        s = GameEngine.addCandy(s, buddy.species, candy)
        return s to "Your buddy ${buddy.displayName} found $candy ${GameEngine.candyName(buddy.species)} on your walk!"
    }
}
