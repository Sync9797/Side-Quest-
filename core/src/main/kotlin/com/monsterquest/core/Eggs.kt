package com.monsterquest.core

import kotlinx.serialization.Serializable
import kotlin.random.Random

/** An egg from two monsters of the same line: it hatches the line's first stage after a walk. */
@Serializable
data class Egg(
    val id: String,
    /** What hatches: the line's first stage. */
    val speciesId: String,
    /** Genes inherited from the parents (at least [Eggs.INHERITED_STATS] are the better parent's). */
    val ivs: StatBlock,
    val personality: Personality,
    val shiny: Boolean,
    /** Distance to walk before it hatches. */
    val meters: Double,
    val walkedMeters: Double = 0.0,
    /** An Incubator halved its distance (once per egg). */
    val incubated: Boolean = false,
) {
    val species: Species get() = Bestiary.get(speciesId)
    val ready: Boolean get() = walkedMeters >= meters
    val progress: Double get() = (walkedMeters / meters).coerceIn(0.0, 1.0)
}

/**
 * Breeding: a male and a female of the same evolution line, both level
 * [BREED_LEVEL]+, make an egg (for line candy and gold). The parents stay, but
 * rest for a day. The egg hatches by walking and carries the best of its
 * parents' genes. Legendaries and mythics have no gender and can't breed.
 * Adventures can also bring eggs home ([fromAdventure]).
 */
object Eggs {
    /** Eggs you can carry. */
    const val MAX_EGGS = 5
    /** Eggs warmed by walking at once: the first ones in your list (move one up to warm it). */
    const val WARMING = 3
    const val BREED_LEVEL = 20
    const val BREED_COOLDOWN_MS = 24 * 60 * 60_000L
    const val BREED_GOLD = 300
    const val BREED_CANDY = 20
    /** Stats guaranteed to come from the better parent. */
    const val INHERITED_STATS = 3
    /** Chance the egg takes one parent's personality. */
    const val PERSONALITY_CHANCE = 0.5

    /** How far an egg of [species]' line must be walked: rarer lines take longer. */
    fun walkMeters(species: Species): Double = when (Bestiary.lineOf(species).last().rarity) {
        Rarity.EPIC, Rarity.LEGENDARY -> 6_000.0
        Rarity.RARE -> 4_000.0
        else -> 2_000.0
    }

    fun resting(m: Monster, nowMs: Long): Boolean = m.bredAtMs > 0 && nowMs - m.bredAtMs < BREED_COOLDOWN_MS

    /** Legendaries and mythics can never make eggs. */
    fun breedable(species: Species): Boolean = !species.genderless

    fun canBreed(m: Monster, nowMs: Long): Boolean = breedable(m.species) && m.level >= BREED_LEVEL && !m.fainted && !resting(m, nowMs)

    /** A male and a female of the same line. */
    fun compatible(a: Monster, b: Monster): Boolean =
        a.uid != b.uid && a.species.family == b.species.family && a.gender != null && b.gender == a.gender?.opposite

    /**
     * Everything making an egg with [m] needs, each with whether it's met, in
     * the order to fix them: its level, a partner, gold, candy and egg space.
     */
    fun checklist(state: GameState, m: Monster, nowMs: Long): List<Pair<String, Boolean>> {
        val partner = m.gender?.opposite?.label?.lowercase() ?: "partner"
        return listOf(
            "${m.displayName} at level $BREED_LEVEL+ (Lv ${m.level})" to (m.level >= BREED_LEVEL),
            "${m.displayName} rested (pairs once a day)" to !resting(m, nowMs),
            "A $partner of its line at level $BREED_LEVEL+, rested" to partners(state, m, nowMs).isNotEmpty(),
            "$BREED_GOLD gold (you have ${state.gold})" to (state.gold >= BREED_GOLD),
            "$BREED_CANDY ${GameEngine.candyName(m.species)} (you have ${GameEngine.candyOf(state, m.species)})" to (GameEngine.candyOf(state, m.species) >= BREED_CANDY),
            "Room for an egg (${state.eggs.size}/$MAX_EGGS carried)" to (state.eggs.size < MAX_EGGS),
        )
    }

    /** Why each other monster of [m]'s line can't be its partner now ("Ember ♂: same gender"), for when none is ready. */
    fun partnerNotes(state: GameState, m: Monster, nowMs: Long): List<String> =
        state.monsters.filter { it.uid != m.uid && it.species.family == m.species.family }.map { o ->
            val why = when {
                o.gender == null || o.gender != m.gender?.opposite -> "same gender"
                o.level < BREED_LEVEL -> "level ${o.level}, needs $BREED_LEVEL"
                o.fainted -> "fainted"
                resting(o, nowMs) -> "resting after an egg"
                else -> "ready"
            }
            "${o.displayName} ${o.gender?.symbol ?: ""}: $why".replace("  ", " ")
        }

    /** Who [m] can make an egg with right now. */
    fun partners(state: GameState, m: Monster, nowMs: Long): List<Monster> =
        state.monsters.filter { compatible(m, it) && canBreed(it, nowMs) }

    /** An egg of [a] and [b]'s line with the best of their genes. */
    private fun eggOf(a: Monster, b: Monster, rng: Random): Egg {
        val baby = Bestiary.lineOf(a.species).first()
        // The best of both: some stats are guaranteed the better parent's, the rest are rolled.
        val best = MONSTER_STATS.shuffled(rng).take(INHERITED_STATS).toSet()
        fun iv(stat: Stat) = if (stat in best) maxOf(a.ivs[stat], b.ivs[stat]) else maxOf(rng.nextInt(MAX_IV + 1), minOf(a.ivs[stat], b.ivs[stat]))
        val ivs = StatBlock(iv(Stat.HP), iv(Stat.ATTACK), rng.nextInt(MAX_IV + 1), iv(Stat.DEFENSE), iv(Stat.SPEED))
        val personality = if (rng.nextDouble() < PERSONALITY_CHANCE) listOf(a, b).random(rng).personality
        else Personality.entries.random(rng)
        val shiny = rollShiny(rng) || ((a.shiny || b.shiny) && rollShiny(rng))
        return Egg(newId(rng), baby.id, ivs, personality, shiny, walkMeters(baby))
    }

    /** An egg of [species]' line with [perfect] perfect stats (a gift). */
    fun gift(species: Species, rng: Random, perfect: Int): Egg {
        val baby = Bestiary.lineOf(species).first()
        val best = MONSTER_STATS.shuffled(rng).take(perfect).toSet()
        fun iv(stat: Stat) = if (stat in best) MAX_IV else rng.nextInt(MAX_IV + 1)
        val ivs = StatBlock(iv(Stat.HP), iv(Stat.ATTACK), rng.nextInt(MAX_IV + 1), iv(Stat.DEFENSE), iv(Stat.SPEED))
        return Egg(newId(rng), baby.id, ivs, Personality.entries.random(rng), rollShiny(rng), walkMeters(baby))
    }

    /** Chance an adventure team of [ADVENTURE_EGG_TEAM]+ monsters brings home an egg. */
    const val ADVENTURE_EGG_CHANCE = 0.75
    const val ADVENTURE_EGG_TEAM = 2

    /**
     * Rolls for an egg when an adventure ends. With a male and a female of the
     * same line on the team, it's their egg (with their genes); otherwise the
     * team finds an egg of one member's line, with ordinary genes. Legendaries
     * and mythics never pass on an egg. Free, and no one has to rest after.
     * Returns the new state and a note, or null for no egg.
     */
    fun fromAdventure(state: GameState, team: List<Monster>, rng: Random): Pair<GameState, String>? {
        if (team.size < ADVENTURE_EGG_TEAM || rng.nextDouble() >= ADVENTURE_EGG_CHANCE) return null
        val kin = team.filter { breedable(it.species) }
        if (kin.isEmpty()) return null
        val pair = kin.flatMap { a -> kin.filter { b -> compatible(a, b) }.map { a to it } }.randomOrNull(rng)
        val egg = if (pair != null) eggOf(pair.first, pair.second, rng) else {
            val baby = Bestiary.lineOf(kin.random(rng).species).first()
            Egg(newId(rng), baby.id, StatBlock.random(rng, MAX_IV), Personality.entries.random(rng), rollShiny(rng), walkMeters(baby))
        }
        if (state.eggs.size >= MAX_EGGS) return state to "They found an egg but your egg slots are full ($MAX_EGGS), so they left it."
        val note = if (pair != null) "${pair.first.displayName} and ${pair.second.displayName} came back with an egg!"
        else "The team found a ${egg.species.name} egg!"
        return state.copy(eggs = state.eggs + egg) to "$note Walk ${"%.1f".format(egg.meters / 1000)} km to hatch it."
    }

    fun breed(state: GameState, uidA: String, uidB: String, nowMs: Long, rng: Random = Random.Default): Update {
        val a = state.monsters.find { it.uid == uidA } ?: return Update(state, "Monster not found.")
        val b = state.monsters.find { it.uid == uidB } ?: return Update(state, "Monster not found.")
        if (!breedable(a.species) || !breedable(b.species)) return Update(state, "Legendary and mythical monsters can't make eggs.")
        if (a.species.family != b.species.family) return Update(state, "Only monsters of the same line can make an egg.")
        if (!compatible(a, b)) return Update(state, "An egg takes a male and a female.")
        if (!canBreed(a, nowMs) || !canBreed(b, nowMs)) return Update(state, "Both need to be level $BREED_LEVEL+, awake and rested.")
        if (state.eggs.size >= MAX_EGGS) return Update(state, "You can carry $MAX_EGGS eggs at a time. Hatch one first.")
        if (state.gold < BREED_GOLD) return Update(state, "Making an egg costs $BREED_GOLD gold.")
        if (GameEngine.candyOf(state, a.species) < BREED_CANDY) return Update(state, "Making an egg costs $BREED_CANDY ${GameEngine.candyName(a.species)}.")
        val egg = eggOf(a, b, rng)
        val s = GameEngine.addCandy(state, a.species, -BREED_CANDY).copy(
            gold = state.gold - BREED_GOLD,
            eggs = state.eggs + egg,
            monsters = state.monsters.map { if (it.uid == uidA || it.uid == uidB) it.copy(bredAtMs = nowMs) else it },
        )
        val walk = if (state.settings.imperialUnits) "%.1f miles".format(egg.meters / METERS_PER_MILE) else "%.1f km".format(egg.meters / 1000)
        return Update(
            s,
            "${a.displayName} and ${b.displayName} made a ${egg.species.name} egg! It's in your Bag: walk $walk to hatch it.",
            rewards = listOf(Reward.Egg(egg.species.family.removeSuffix(" line"))),
            rewardTitle = "A new egg!",
        )
    }

    /** The eggs walking warms now: the first [WARMING] in your list (a ready egg keeps its spot until it hatches). */
    fun warming(state: GameState): Set<String> = state.eggs.take(WARMING).map { it.id }.toSet()

    /** Walking warms the first [WARMING] eggs; the rest wait their turn. */
    fun addWalk(state: GameState, meters: Double): GameState {
        val warm = warming(state)
        if (meters <= 0 || state.eggs.none { !it.ready && it.id in warm }) return state
        return state.copy(eggs = state.eggs.map { if (it.ready || it.id !in warm) it else it.copy(walkedMeters = minOf(it.meters, it.walkedMeters + meters)) })
    }

    /** Moves an egg to the front, so it's one of the ones being warmed. */
    fun warmFirst(state: GameState, id: String): Update {
        val egg = state.eggs.find { it.id == id } ?: return Update(state, "Egg not found.")
        return Update(state.copy(eggs = listOf(egg) + state.eggs.filterNot { it.id == id }), "The ${egg.species.name} egg is warming now.")
    }

    /** Puts [id] in an Incubator: its distance is halved (what's walked stays). */
    fun incubate(state: GameState, id: String): Update {
        val egg = state.eggs.find { it.id == id } ?: return Update(state, "Egg not found.")
        if (egg.ready) return Update(state, "That egg is ready to hatch already.")
        if (egg.incubated) return Update(state, "That egg has been in an Incubator already.")
        if (state.count(Item.INCUBATOR) <= 0) return Update(state, "You need an Incubator (from the Store).")
        val meters = egg.meters * Item.INCUBATOR.power
        val after = egg.copy(meters = meters, walkedMeters = minOf(egg.walkedMeters, meters), incubated = true)
        val s = state.copy(
            inventory = GameEngine.removeItems(state.inventory, mapOf(Item.INCUBATOR to 1)),
            eggs = state.eggs.map { if (it.id == id) after else it },
        )
        return Update(s, "The ${egg.species.name} egg is in the Incubator: " +
            if (after.ready) "it's ready to hatch!" else "%.1f km to go.".format((after.meters - after.walkedMeters) / 1000))
    }

    fun hatch(state: GameState, id: String, rng: Random = Random.Default): Update {
        val egg = state.eggs.find { it.id == id } ?: return Update(state, "Egg not found.")
        if (!egg.ready) return Update(state, "Keep walking: %.1f of %.1f km.".format(egg.walkedMeters / 1000, egg.meters / 1000))
        if (state.storageFull) return Update(state, "Monster storage is full. Release a monster or expand storage first.")
        val m = Monster.create(egg.species, 1, rng, shiny = egg.shiny).copy(ivs = egg.ivs, personality = egg.personality)
        val s = state.copy(
            eggs = state.eggs.filterNot { it.id == id },
            monsters = state.monsters + m,
            party = if (state.party.size < MAX_PARTY_SIZE) state.party + m.uid else state.party,
            stats = state.stats.copy(eggsHatched = state.stats.eggsHatched + 1),
        )
        return Update(s, "The egg hatched: a ${if (m.shiny) "shiny " else ""}${m.gender?.let { it.label.lowercase() + " " } ?: ""}${m.species.name}! ${m.personality.label}, with ${GameEngine.perfectStatCount(m)} perfect stat(s).")
    }
}
