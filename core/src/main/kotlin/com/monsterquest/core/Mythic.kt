package com.monsterquest.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * The hidden legendary: Murklet, Cinderwrithe, Fumewarden and Turumarth.
 *
 * - Not in the Journal ([Bestiary.hidden]), never in the wild, at shrines or in boxes.
 * - Once every legendary is caught, Murklet may wobble out of the water in a
 *   random encounter ([EncounterType.OMEN]). It can't be tamed then: beaten,
 *   it flees and leaves a scale behind, worn on the hero's chest like a badge.
 * - With the scale, it roams: on a Saturday or Sunday, rarely, and only if
 *   every task due that week (so far) is done. Then it can be tamed.
 * - It evolves only by level (30, 60, 100), never for candy: the game says
 *   "Oh?..." and plays the evolution.
 * - Stage 1 is the weakest monster in the game; Turumarth, the Mythical
 *   fourth stage, is the strongest and can learn every move.
 */
object Mythic {
    const val MURKLET = "murklet"
    const val CINDERWRITHE = "cinderwrithe"
    const val FUMEWARDEN = "fumewarden"
    const val TURUMARTH = "turumarth"
    val LINE = listOf(MURKLET, CINDERWRITHE, FUMEWARDEN, TURUMARTH)
    val EVOLVE_LEVELS = listOf(30, 60, 100)

    /** One signature move per stage, learned as it evolves (see [Moves.familyMoves]). */
    val SIGNATURES = listOf(
        "little_tide" to "Little Tide",
        "pyre_mirror" to "Pyre Mirror",
        "steamheart_storm" to "Steamheart Storm",
        "genesis_bloom" to "Genesis Bloom",
    )

    /** Chance a random encounter is Murklet's first appearance, once it's due. */
    const val OMEN_CHANCE = 0.1
    /** Chance a random encounter is the roaming Murklet, on a weekend it's out. */
    const val ROAM_CHANCE = 0.08
    /** It's met as a small thing: never above this level. */
    const val MAX_MEET_LEVEL = 29

    fun inLine(speciesId: String): Boolean = speciesId in LINE

    /** Every legendary in the Journal has been caught. */
    fun legendariesCaught(state: GameState): Boolean =
        Bestiary.all.filter { it.rarity == Rarity.LEGENDARY }.all { it.id in state.caught }

    /** You already have one of its line: it doesn't come again. */
    fun owned(state: GameState): Boolean = state.monsters.any { inLine(it.speciesId) }

    /** Its first appearance can happen now. */
    fun omenDue(state: GameState): Boolean = legendariesCaught(state) && !state.mythScale && !owned(state)

    /** Monday of the week [nowMs] is in. */
    private fun weekStart(nowMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().with(DayOfWeek.MONDAY)

    /**
     * This week's tasks, (done, total): every task due from Monday to the end
     * of today, and every to-do added this week without a due date.
     */
    fun weekTasks(state: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<Int, Int> {
        val from = weekStart(nowMs, zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val week = state.tasks.filter { t ->
            val due = t.dueAt
            if (due != null) due in from until to else (t.createdAtMs ?: 0L) >= from || (t.completedAtMs ?: 0L) >= from
        }
        return week.count { it.completed } to week.size
    }

    fun isWeekend(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        Instant.ofEpochMilli(nowMs).atZone(zone).dayOfWeek.let { it == DayOfWeek.SATURDAY || it == DayOfWeek.SUNDAY }

    /** Every task this week is done (and there was at least one). */
    fun weekComplete(state: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        weekTasks(state, nowMs, zone).let { (done, total) -> total > 0 && done == total }

    /** It's out roaming right now. */
    fun roaming(state: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        state.mythScale && !owned(state) && isWeekend(nowMs, zone) && weekComplete(state, nowMs, zone)

    /**
     * A random encounter (a walk's ambush or a hunt) may be Murklet instead:
     * its first appearance (can't be tamed) or, with the scale, the roaming
     * one (can be). Null when it's neither.
     */
    fun maybeEncounter(state: GameState, nowMs: Long, rng: Random, zone: ZoneId = ZoneId.systemDefault()): Pair<Monster, EncounterType>? {
        val type = when {
            omenDue(state) && rng.nextDouble() < OMEN_CHANCE -> EncounterType.OMEN
            roaming(state, nowMs, zone) && rng.nextDouble() < ROAM_CHANCE -> EncounterType.WILD
            else -> return null
        }
        val level = state.hero.level.coerceIn(1, MAX_MEET_LEVEL)
        return Monster.create(Bestiary.get(MURKLET), level, rng, evolve = false).withWildMoves(rng) to type
    }

    /** Beaten at its first appearance: it flees, and leaves its scale. */
    fun onOmenBeaten(state: GameState): GameState = state.copy(mythScale = true)

    const val SCALE_NAME = "Murk Scale"
    const val FLEE_TEXT = "Murklet sinks back into the water and is gone... but it left something behind: a $SCALE_NAME, " +
        "warm to the touch. You fasten it to your chest."

    /** What the scale tells you (the tracker). */
    fun scaleText(state: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (owned(state)) return "The scale is quiet now. Its owner travels with you."
        val (done, total) = weekTasks(state, nowMs, zone)
        val weekend = isWeekend(nowMs, zone)
        return when {
            total > 0 && done == total && weekend -> "The scale is warm. It's somewhere near: keep walking."
            total > 0 && done == total -> "The scale hums. Keep every task done and it may be out roaming this weekend."
            else -> "The scale hums faintly. Finish all of this week's tasks ($done of $total done) and it may be out roaming this weekend."
        }
    }

    /** One of its line that has reached its evolution level and is waiting for the "Oh?..." pop-up. */
    fun dueToEvolve(state: GameState): Monster? = state.monsters.firstOrNull { inLine(it.speciesId) && it.canEvolveTo != null }

    /** Evolves [uid] one stage (it only evolves by level). New moves fill empty slots of a custom loadout. */
    fun evolve(state: GameState, uid: String): Update {
        val m = state.monsters.find { it.uid == uid } ?: return Update(state, "Monster not found.")
        val into = m.canEvolveTo ?: return Update(state, "${m.displayName} isn't ready to evolve.")
        val before = m.learnedMoves.map { it.id }.toSet()
        var evolved = m.copy(speciesId = into.id)
        val fresh = evolved.learnedMoves.filter { it.id !in before }
        evolved.moves?.let { custom -> evolved = evolved.copy(moves = (custom + fresh.map { it.id }).distinct().take(Moves.MAX_MOVES)) }
        val s = state.copy(
            monsters = state.monsters.map { if (it.uid == uid) evolved else it },
            caught = state.caught + into.id,
        )
        val learned = fresh.filter { it.id in SIGNATURES.map { s -> s.first } }.joinToString { it.name }
        return Update(s, "${m.displayName} evolved into ${into.name}!" + if (learned.isNotEmpty()) " It learned $learned!" else "")
    }
}
