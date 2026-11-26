package com.monsterquest.core

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.random.Random

/** An adventure offered at a real place near the player. */
@Serializable
data class Adventure(
    /** "placeId:epochDay"; each place offers at most one adventure a day. */
    val id: String,
    val placeName: String,
    val title: String,
    /** 1..5; also the number of monsters to send. */
    val difficulty: Int,
    val recommendedLevel: Int,
    val durationMs: Long,
) {
    val teamSize: Int get() = difficulty
}

@Serializable
data class ActiveAdventure(
    val adventure: Adventure,
    val monsterUids: List<String>,
    val startMs: Long,
    val successChance: Double,
    /** The bag was full when the team set out: loot only fits if there's room (otherwise it all comes home). */
    val bagFullAtStart: Boolean = false,
) {
    val endMs: Long get() = startMs + adventure.durationMs
    fun done(nowMs: Long) = nowMs >= endMs
}

/**
 * Monsters can be sent to adventure at real places on their own. They fight
 * without you, so they're less effective ([AUTO_BATTLE_EFFECTIVENESS]), the
 * hero earns no XP, and the monsters are unavailable until they return.
 */
object Adventures {
    const val UNLOCK_MONSTERS = 5
    const val MAX_ACTIVE = 3
    const val AUTO_BATTLE_EFFECTIVENESS = 0.75
    private const val MAX_OFFERS = 6

    private val durationsMin = listOf(30, 60, 120, 240, 480)

    private val titles = listOf(
        "Scout the area around %s",
        "Chase off wild pests at %s",
        "Recover a lost relic near %s",
        "Escort travelers past %s",
        "Purge the corruption at %s",
        "Hunt the night prowler near %s",
        "Guard the supply cache at %s",
        "Explore the hidden paths by %s",
    )

    private fun epochDay(nowMs: Long, zone: ZoneId) = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()

    private fun hash(vararg parts: Any): Int = abs(parts.joinToString("|").hashCode())

    /**
     * Today's adventures at the given nearby places (closest first). The
     * selection and difficulty rotate daily; adventures already sent today
     * aren't offered again.
     */
    fun offersNear(
        state: GameState,
        places: List<Shrine>,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Adventure> {
        val day = epochDay(nowMs, zone)
        val taken = state.adventureLog.keys + state.adventures.map { it.adventure.id }
        return places.asSequence()
            .filter { hash(it.id, day) % 3 == 0 }
            .map { place ->
                val h = hash(place.id, day, "adv")
                val where = place.place ?: place.kind ?: place.name
                val difficulty = 1 + h % 5
                Adventure(
                    id = "${place.id}:$day",
                    placeName = where,
                    title = titles[(h / 5) % titles.size].format(where),
                    difficulty = difficulty,
                    recommendedLevel = (state.hero.level + (difficulty - 2) * 3).coerceAtLeast(1),
                    durationMs = durationsMin[difficulty - 1] * 60_000L,
                )
            }
            .filter { it.id !in taken }
            .take(MAX_OFFERS)
            .toList()
    }

    /** Monsters currently out on adventures. */
    fun awayUids(state: GameState): Set<String> = state.adventures.flatMap { it.monsterUids }.toSet()

    /** Monsters that can go adventuring: rested, not already away, and not in your battle party. */
    fun available(state: GameState): List<Monster> {
        val away = awayUids(state)
        return state.monsters.filter { !it.fainted && it.uid !in away && it.uid !in state.team }
    }

    private fun power(m: Monster): Double {
        val s = m.stats
        return (s.hp / 3.0 + s.attack + s.defense + s.speed)
    }

    /** What one ordinary monster at the recommended level brings. */
    private fun benchmark(level: Int): Double {
        val ref = Monster("ref", "shadow_pup", Personality.BALANCED, StatBlock(7, 7, 7, 7, 7), level = level)
        return power(ref)
    }

    /**
     * Success odds: the team's power (reduced, since they fight unsupervised)
     * against what the adventure demands. Higher-level monsters are better equipped.
     */
    fun successChance(team: List<Monster>, adventure: Adventure): Double {
        val teamPower = team.sumOf { power(it) } * AUTO_BATTLE_EFFECTIVENESS
        val needed = benchmark(adventure.recommendedLevel) * adventure.teamSize * AUTO_BATTLE_EFFECTIVENESS
        return (teamPower / needed - 0.25).coerceIn(0.05, 0.95)
    }

    fun send(state: GameState, adventure: Adventure, uids: List<String>, nowMs: Long): Update {
        if (state.monsters.size < UNLOCK_MONSTERS) return Update(state, "Collect $UNLOCK_MONSTERS monsters to unlock adventures.")
        if (state.adventures.size >= MAX_ACTIVE) return Update(state, "You can run $MAX_ACTIVE adventures at a time.")
        if (state.adventures.any { it.adventure.id == adventure.id } || adventure.id in state.adventureLog) {
            return Update(state, "This adventure is already taken.")
        }
        val ids = uids.distinct()
        if (ids.size != adventure.teamSize) return Update(state, "This adventure needs exactly ${adventure.teamSize} monster(s).")
        val free = available(state).associateBy { it.uid }
        if (ids.any { it in state.team }) return Update(state, "Your battle party and swaps stay with you. Pick monsters from your collection.")
        val team = ids.map { free[it] ?: return Update(state, "A chosen monster is fainted or already away.") }
        val chance = successChance(team, adventure)
        val active = ActiveAdventure(adventure, ids, nowMs, chance, bagFullAtStart = state.bagFull)
        return Update(
            state.copy(adventures = state.adventures + active),
            "${team.joinToString { it.displayName }} set out: ${adventure.title}. Back in ${adventure.durationMs / 60_000} min.",
        )
    }

    /** Collect the results of a finished adventure. */
    fun claim(state: GameState, adventureId: String, nowMs: Long, rng: Random = Random.Default, zone: ZoneId = ZoneId.systemDefault()): Update {
        val active = state.adventures.find { it.adventure.id == adventureId } ?: return Update(state, "Adventure not found.")
        if (!active.done(nowMs)) return Update(state, "Your monsters are still out adventuring.")
        val adv = active.adventure
        val d = adv.difficulty
        val success = rng.nextDouble() < active.successChance
        // Remember it (and forget entries older than a week) so it isn't offered again.
        val today = epochDay(nowMs, zone)
        val log = state.adventureLog.filterValues { it >= today - 7 } + (adv.id to today)
        var s = state.copy(adventures = state.adventures - active, adventureLog = log)

        val team = s.monsters.filter { it.uid in active.monsterUids }
        val xpEach = adv.recommendedLevel * 6 * d / (if (success) 1 else 3)
        val notes = mutableListOf<String>()
        s = s.copy(
            monsters = s.monsters.map { m ->
                if (m.uid !in active.monsterUids) return@map m
                val (after, n) = GameEngine.grow(m, xpEach, XpRules.monsterCap(s))
                notes += n
                after
            },
        )
        val teamNames = team.joinToString { it.displayName }
        // Teams of two or more often bring home an egg, win or lose.
        val egg = Eggs.fromAdventure(s, team, rng)
        if (egg != null) s = egg.first
        val eggNote = egg?.let { " ${it.second}" } ?: ""
        val eggReward = egg?.first?.eggs?.lastOrNull()?.let { e -> Reward.Egg(Bestiary.get(e.speciesId).family.removeSuffix(" line")) }
        return if (success) {
            val gold = 20 * d + adv.recommendedLevel * 2 * d + rng.nextInt(10 * d + 1)
            s = s.copy(gold = s.gold + gold)
            val loot = GameEngine.rollLoot(s, d + 1, rng).let { if (d >= 3) it.copy(parts = GameEngine.rollParts(d - 1, rng, s.hero.level)) else it }
            val granted = GameEngine.grantLoot(s, loot)
            Update(
                granted.state,
                "${adv.title}: success! $teamNames brought back +$gold gold, ${granted.message}. Each monster +$xpEach XP." +
                    eggNote + notes.joinToString("") { " $it" },
                rewards = listOf(Reward.Gold(gold)) + granted.rewards + Reward.Xp(xpEach) + listOfNotNull(eggReward),
                rewardTitle = adv.title,
            )
        } else {
            val gold = 10 * d
            val weakest = team.minByOrNull { it.level }
            s = s.copy(
                gold = s.gold + gold,
                monsters = s.monsters.map { if (it.uid == weakest?.uid) it.copy(fainted = true, damage = it.stats.hp) else it },
            )
            Update(
                s,
                "${adv.title}: the team was driven back. +$gold gold, +$xpEach XP each." +
                    (weakest?.let { " ${it.displayName} fainted and needs a Restore Potion." } ?: "") + eggNote,
                rewards = listOf(Reward.Gold(gold), Reward.Xp(xpEach)) + listOfNotNull(eggReward),
                rewardTitle = adv.title,
            )
        }
    }
}
