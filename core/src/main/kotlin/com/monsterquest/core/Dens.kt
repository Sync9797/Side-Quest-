package com.monsterquest.core

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import kotlin.math.floor
import kotlin.random.Random

/** A den on the map: no loot, just one strong monster to beat and then try to tame. */
data class Den(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val monster: Monster,
) {
    val species: Species get() = monster.species
}

/** After beating a den's monster: a few throws to tame it before it slips away. */
@Serializable
data class DenCatch(
    val denId: String,
    val monster: Monster,
    val throwsLeft: Int,
    val throwsTotal: Int,
    /** A berry it has eaten: the next throw's odds are multiplied by this (1 = none). */
    val bait: Double = 1.0,
)

/**
 * Dens are lairs of strong ordinary monsters (never legendaries), a few levels
 * above you with great stats. They can't be tamed during the fight: beat the
 * monster first, then you get [BASE_THROWS] throws plus one for each of your
 * monsters still standing (up to [MAX_THROWS]), each one a long shot (with a
 * 2% critical catch). Dens move every day; a cleared den stays empty until then.
 */
object Dens {
    const val RANGE_M = 100.0
    /** Throws you always get after beating a den. */
    const val BASE_THROWS = 5
    const val MIN_THROWS = BASE_THROWS
    const val MAX_THROWS = 10
    /** Den monsters fight with this much more HP than usual. */
    const val HP_MULTIPLIER = 2.0
    /** Share of map cells with a den each day: monsters now live in dens, so there are plenty. */
    const val DEN_CHANCE = 0.5

    /** Den fights pay this much more than a wild fight. */
    const val REWARD_MULTIPLIER = 1.8

    private const val CELL_DEG = 0.0035 // ~390 m
    private const val CELL_RADIUS = 3

    private fun epochDay(nowMs: Long, zone: ZoneId) = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()

    private fun seed(x: Long, y: Long, day: Long): Long {
        var h = 1469598103934665603L
        for (p in longArrayOf(x, y, day, 0xde75)) {
            h = (h xor p) * 1099511628211L
            h = h xor (h ushr 31)
        }
        return h
    }

    private fun denName(e: Element): String = when (e) {
        Element.FIRE -> "Cinder Lair"
        Element.WATER -> "Tidepool Grotto"
        Element.EARTH -> "Mossy Burrow"
        Element.FLIGHT -> "Windswept Roost"
        Element.ELECTRIC -> "Static Hollow"
        Element.GAS -> "Fume Cave"
        Element.CORRUPT -> "Shadow Hollow"
        Element.NEUTRAL, Element.MYTHICAL -> "Beast Den"
    }

    /** Base per-throw taming chance: rarer monsters are harder. Over 5-10 throws that's roughly a coin flip. */
    fun throwChance(m: Monster): Double = when (m.species.rarity) {
        Rarity.COMMON -> 0.15
        Rarity.UNCOMMON -> 0.13
        Rarity.RARE -> 0.11
        else -> 0.09
    }

    /** A throw's real chance, with a berry's boost ([bait]) and the critical catch. */
    fun throwOdds(m: Monster, bait: Double = 1.0, grade: TraceGrade = TraceGrade.GOOD): Double =
        withCriticalCatch((throwChance(m) * bait * grade.multiplier).coerceAtMost(0.95))

    /** The chance of taming it with [throws] throws left (a berry only helps the next one). */
    fun oddsOver(m: Monster, throws: Int, bait: Double = 1.0): Double =
        if (throws <= 0) 0.0 else 1 - (1 - throwOdds(m, bait)) * Math.pow(1 - throwOdds(m), (throws - 1).toDouble())

    /** Feed the beaten den monster a berry before the next throw (doesn't use a throw). */
    fun feed(state: GameState, berry: Item): Update {
        val c = state.pendingDenCatch ?: return Update(state, "Nothing to feed.")
        if (berry.kind != ItemKind.BAIT || state.count(berry) <= 0) return Update(state, "No ${berry.label} left.")
        if (c.bait >= berry.power) return Update(state, "${c.monster.displayName} has already eaten a berry.")
        return Update(
            state.copy(
                inventory = GameEngine.removeItems(state.inventory, mapOf(berry to 1)),
                pendingDenCatch = c.copy(bait = berry.power),
            ),
            "${c.monster.displayName} eats the ${berry.label}. Your next cast is ${berry.power}x as likely!",
        )
    }

    /** Throws earned: [BASE_THROWS] plus one per monster of yours still standing. */
    fun throwsFor(standing: Int): Int = (BASE_THROWS + standing).coerceIn(MIN_THROWS, MAX_THROWS)

    /** Today's dens around a location (cleared ones excluded). */
    fun near(state: GameState, lat: Double, lon: Double, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<Den> {
        val day = epochDay(nowMs, zone)
        val cx = floor(lat / CELL_DEG).toLong()
        val cy = floor(lon / CELL_DEG).toLong()
        val out = mutableListOf<Den>()
        for (dx in -CELL_RADIUS..CELL_RADIUS) for (dy in -CELL_RADIUS..CELL_RADIUS) {
            val x = cx + dx
            val y = cy + dy
            val r = Random(seed(x, y, day))
            if (r.nextDouble() > DEN_CHANCE) continue
            val id = "den:$day:$x:$y"
            if (id in state.densCleared) continue
            out += make(state, id, (x + 0.15 + r.nextDouble() * 0.7) * CELL_DEG, (y + 0.15 + r.nextDouble() * 0.7) * CELL_DEG, r)
        }
        return out
    }

    /** A den at [lat], [lon]: one strong monster a few levels above you, with great stats. */
    fun make(state: GameState, id: String, lat: Double, lon: Double, r: Random, favored: Set<Element> = emptySet()): Den {
        val level = (state.hero.level + r.nextInt(3, 6)).coerceAtLeast(4)
        val species = Bestiary.randomWild(r, level, favored, rareBoost = 1.6, favorFirstForms = false)
        // Great stats: nothing below 10, and two perfect.
        val base = Monster.create(species, level, r, shiny = rollShiny(r)).withWildMoves(r)
        val sturdy = base.copy(
            ivs = StatBlock(
                maxOf(10, base.ivs.hp), maxOf(10, base.ivs.attack), maxOf(10, base.ivs.magic),
                maxOf(10, base.ivs.defense), maxOf(10, base.ivs.speed),
            ),
        )
        return Den(id, denName(species.element), lat, lon, GameEngine.withPerfectStats(sturdy, 2, r))
    }

    /** The den's monster is beaten: the den is cleared and the taming throws begin. */
    fun defeated(state: GameState, denId: String, monster: Monster, nowMs: Long, standing: Int, zone: ZoneId = ZoneId.systemDefault()): GameState {
        val day = epochDay(nowMs, zone)
        val throws = throwsFor(standing)
        return state.copy(
            densCleared = state.densCleared.filterValues { it >= day - 2 } + (denId to day),
            pendingDenCatch = DenCatch(denId, monster.copy(damage = 0), throws, throws),
        )
    }

    /** One taming throw at a beaten den monster. */
    fun attempt(state: GameState, rng: Random = Random.Default, grade: TraceGrade = TraceGrade.GOOD): Update = cast(state, rng, grade).first

    /** One Binding cast at a beaten den monster, with its roll for the shake animation (null if it couldn't be cast). */
    fun cast(state: GameState, rng: Random = Random.Default, grade: TraceGrade = TraceGrade.GOOD): Pair<Update, CastRoll?> {
        val c = state.pendingDenCatch ?: return Update(state, "Nothing to tame.") to null
        val m = c.monster
        if (state.storageFull) return Update(state, "Monster storage is full. Release a monster or upgrade storage first.") to null
        val critical = rng.nextDouble() < CRITICAL_CATCH
        val chance = (throwChance(m) * c.bait * grade.multiplier).coerceAtMost(0.95)
        val roll = rng.nextDouble()
        val success = critical || roll < chance
        val result = CastRoll(success, shakesFor(success, critical, roll, chance), critical)
        return castResult(state, c, m, rng, grade, critical, success) to result
    }

    private fun castResult(state: GameState, c: DenCatch, m: Monster, rng: Random, grade: TraceGrade, critical: Boolean, success: Boolean): Update {
        if (success) {
            val tamed = m.copy(uid = newId(rng), xp = 0)
            var s = state.copy(
                pendingDenCatch = null,
                monsters = state.monsters + tamed,
                party = if (state.party.size < MAX_PARTY_SIZE) state.party + tamed.uid else state.party,
                stats = state.stats.tamed(tamed),
            )
            s = GameEngine.addCandy(s, tamed.species, GameEngine.tameCandy(tamed.species))
            val xp = GameEngine.captureXp(tamed.level) * 2
            val gain = GameEngine.addHeroXp(s, xp)
            return Update(gain.state, (if (critical) "Critical catch! " else "") + "Got it! ${m.displayName} from the den was tamed! +$xp XP, +${GameEngine.tameCandy(tamed.species)} ${GameEngine.candyName(tamed.species)}.${GameEngine.levelUpText(gain)}")
        }
        val left = c.throwsLeft - 1
        return if (left <= 0) {
            Update(state.copy(pendingDenCatch = null), "${m.displayName} broke free and slipped back into the wild.")
        } else {
            Update(state.copy(pendingDenCatch = c.copy(throwsLeft = left, bait = 1.0)), "${m.displayName} broke free (${grade.label.lowercase()} trace)! $left ${if (left == 1) "cast" else "casts"} left.")
        }
    }

    /** Stop trying: the monster leaves. */
    fun giveUp(state: GameState): Update {
        val c = state.pendingDenCatch ?: return Update(state, "")
        return Update(state.copy(pendingDenCatch = null), "You let ${c.monster.displayName} go.")
    }
}
