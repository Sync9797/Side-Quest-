package com.monsterquest.core

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/** The weekly champion's scoring rule. */
@Serializable
enum class ChampionRule(val label: String, val description: String) {
    FEWEST_TURNS("Fewest turns", "Win in as few rounds as you can."),
    NO_ITEMS("No items", "No potions or other items in this fight. Win in as few rounds as you can."),
    ONE_MONSTER("One monster", "Just your hero and one monster: no swaps. Win in as few rounds as you can."),
}

/** This week's champion record: the week (its Monday), your best rounds this week, and whether its prize is claimed. */
@Serializable
data class ChampionWeek(val weekDay: Long, val bestRounds: Int? = null, val prized: Boolean = false)

/**
 * The weekly champion: one named monster each week (Monday to Sunday), with a
 * scoring rule. Beat it for a prize once a week, and in as few rounds as you
 * can: beat your own all-time record for that rule to earn a Champion's Badge.
 */
object WeeklyChampion {
    const val PRIZE_GOLD = 400
    const val PRIZE_CRYSTALS = 2
    const val LEVEL_ABOVE = 5

    data class Champion(val name: String, val species: Species, val level: Int, val rule: ChampionRule, val weekDay: Long)

    private val EPITHETS = listOf(
        "the Unbowed", "the Ironhide", "of the Long Night", "the Stormborn", "the Old", "the Ashen",
        "the Undying", "of the Deep Fen", "the Grey", "the Bright", "of the Hollow Hills", "the Wild",
    )

    /** Final forms of lines with three or more stages: the champion pool. */
    private val POOL: List<Species> by lazy { Bestiary.all.filter { !it.bossOnly && it.stages >= 3 && it.stage == it.stages } }

    fun weekOf(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().with(DayOfWeek.MONDAY).toEpochDay()

    /** This week's champion, the same for the whole week. */
    fun current(state: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Champion {
        val week = weekOf(nowMs, zone)
        val r = Random(week * 7919 + 17)
        val sp = POOL[r.nextInt(POOL.size)]
        val rule = ChampionRule.entries[Math.floorMod(week, ChampionRule.entries.size.toLong()).toInt()]
        return Champion("${sp.name} ${EPITHETS[r.nextInt(EPITHETS.size)]}", sp, (state.hero.level + LEVEL_ABOVE).coerceIn(10, MAX_MONSTER_LEVEL), rule, week)
    }

    /** The champion as a fighter: perfect genes, its four best moves. */
    fun monster(c: Champion): Monster {
        val r = Random(c.weekDay)
        return Monster.create(c.species, c.level, r, evolve = false)
            .copy(ivs = StatBlock(MAX_IV, MAX_IV, MAX_IV, MAX_IV, MAX_IV), nickname = c.name)
    }

    /** This week's record (a fresh one in a new week). */
    fun week(state: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): ChampionWeek {
        val w = weekOf(nowMs, zone)
        return state.championWeek?.takeIf { it.weekDay == w } ?: ChampionWeek(w)
    }

    fun record(state: GameState, rule: ChampionRule): Int? = state.championRecords[rule]

    /** You won in [rounds] rounds: this week's best, the weekly prize (once), and a badge for a new record. */
    fun onWin(state: GameState, rounds: Int, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Update {
        val c = current(state, nowMs, zone)
        val wk = week(state, nowMs, zone)
        var s = state
        val notes = mutableListOf("You beat ${c.name} in $rounds round${if (rounds == 1) "" else "s"}!")
        val rewards = mutableListOf<Reward>()
        if (!wk.prized) {
            s = s.copy(gold = s.gold + PRIZE_GOLD)
            s = GameEngine.addItems(s, mapOf(Item.RESONANT_CRYSTAL to PRIZE_CRYSTALS)).first
            rewards += Reward.Gold(PRIZE_GOLD)
            rewards += Reward.Items(Item.RESONANT_CRYSTAL, PRIZE_CRYSTALS)
        }
        val best = wk.bestRounds?.let { minOf(it, rounds) } ?: rounds
        if (wk.bestRounds != null && rounds < wk.bestRounds) notes += "A new best this week."
        val old = record(s, c.rule)
        if (old == null || rounds < old) {
            s = s.copy(championRecords = s.championRecords + (c.rule to rounds), championBadges = s.championBadges + 1)
            notes += if (old == null) "Your first ${c.rule.label} record: a Champion's Badge!" else "A new ${c.rule.label} record (was $old): a Champion's Badge!"
        }
        s = s.copy(championWeek = wk.copy(bestRounds = best, prized = true))
        return Update(s, notes.joinToString(" "), rewards, rewardTitle = if (rewards.isNotEmpty()) "Weekly champion" else null)
    }
}
