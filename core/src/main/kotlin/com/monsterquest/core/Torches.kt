package com.monsterquest.core

import java.time.Instant
import java.time.ZoneId

/**
 * Torches light your way into dungeons. Walking earns them
 * ([STEPS_PER_TORCH] steps each), a few wait for you every morning, and
 * dungeon chests and long phone-free sessions hold a few more.
 */
object Torches {
    const val STEPS_PER_TORCH = 1_000
    const val MORNING = 3
    /** The most torches anyone can hold (level 40 and up). */
    const val MAX = 30
    /** What each earned torch that doesn't fit under the cap pays instead. */
    const val GOLD_PER_SPARE_TORCH = 25

    /** How many torches a hero of [level] can hold: 10, then 5 more every 10 levels, up to [MAX] at 40. */
    fun cap(level: Int): Int = (10 + 5 * ((level.coerceAtLeast(1)) / 10)).coerceAtMost(MAX)
    fun cap(state: GameState): Int = cap(state.hero.level)

    /** Whether walking and the morning gift have stopped adding torches. */
    fun full(state: GameState): Boolean = state.torches >= cap(state)

    /**
     * Adds torches, never past the hero's cap. Walking and the morning gift
     * just stop at the cap; an earned [reward] (a dungeon's haul, a long
     * focus session) pays [GOLD_PER_SPARE_TORCH] gold for each torch that
     * doesn't fit, so a prize is never wasted.
     */
    fun add(state: GameState, n: Int, reward: Boolean = false): GameState {
        if (n <= 0) return state
        val room = (cap(state) - state.torches).coerceAtLeast(0)
        val fit = minOf(n, room)
        val spare = if (reward) n - fit else 0
        return state.copy(torches = state.torches + fit, gold = state.gold + spare * GOLD_PER_SPARE_TORCH)
    }

    /** " (2 torches past your limit became 50 gold)" when an earned [n] torches won't all fit, else "". */
    fun spareText(state: GameState, n: Int): String {
        val spare = (n - (cap(state) - state.torches).coerceAtLeast(0)).coerceAtLeast(0)
        if (spare <= 0) return ""
        return " ($spare torch${if (spare == 1) "" else "es"} past your limit of ${cap(state)} became ${spare * GOLD_PER_SPARE_TORCH} gold)"
    }

    /** Brings a hero holding more torches than the cap (from before it was strict) down to it, paying gold for the rest. */
    fun clamp(state: GameState): GameState {
        val over = state.torches - cap(state)
        if (over <= 0) return state
        return state.copy(torches = cap(state), gold = state.gold + over * GOLD_PER_SPARE_TORCH)
    }

    /** Walking: steps build toward the next torch. Returns the state and how many torches were earned. */
    fun addSteps(state: GameState, steps: Int): Pair<GameState, Int> {
        if (steps <= 0) return state to 0
        val bank = state.torchSteps + steps
        val earned = bank / STEPS_PER_TORCH
        val before = state.torches
        // At the cap, steps stop banking too, so walking doesn't save up a pile for later.
        if (full(state)) return state.copy(torchSteps = 0) to 0
        val s = add(state.copy(torchSteps = bank % STEPS_PER_TORCH), earned)
        return s to (s.torches - before)
    }

    /** The morning's torches, once a day (the first time you open the game). */
    fun morning(state: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Update? {
        val day = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()
        if (state.torchGiftDay == day) return null
        val s = add(state.copy(torchGiftDay = day), MORNING)
        val got = s.torches - state.torches
        return Update(
            s,
            if (got == 0) "Good morning! Your torches are full (${s.torches}/${cap(s)}): spend some to keep earning."
            else "Good morning! $got torch${if (got == 1) " is" else "es are"} waiting for you (${s.torches}/${cap(s)}).",
        )
    }
}

/** How rested the hero is today, from last night's sleep. */
@kotlinx.serialization.Serializable
enum class Rest(val label: String, val statFactor: Double) {
    /** 8 hours or more: everyone fights a little better today. */
    WELL_RESTED("Well rested", 1.1),
    NORMAL("Rested", 1.0),
    /** Under 7 hours: a little worse today. */
    TIRED("Tired", 0.9);

    companion object {
        const val WELL_RESTED_MIN = 8 * 60
        const val TIRED_BELOW_MIN = 7 * 60

        fun of(asleepMin: Int): Rest = when {
            asleepMin >= WELL_RESTED_MIN -> WELL_RESTED
            asleepMin < TIRED_BELOW_MIN -> TIRED
            else -> NORMAL
        }

        /** Today's rest (normal when last night isn't known, or on another day). */
        fun today(state: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Rest {
            val day = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()
            return if (state.restDay == day) state.rest else NORMAL
        }

        /**
         * Sets today's rest from last night's sleep, once a day: well rested
         * brings a stat boost for the day, tired a stat penalty.
         */
        fun update(state: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Update {
            val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            if (state.restDay == today.toEpochDay()) return Update(state, "")
            val night = SleepLog.lastNight(state, today) ?: return Update(state, "")
            val rest = of(night.asleepMin)
            var s = state.copy(restDay = today.toEpochDay(), rest = rest)
            val text = when (rest) {
                WELL_RESTED -> {
                    "Well rested (${SleepLog.label(night.asleepMin)})! Your hero and monsters fight 10% better today."
                }
                TIRED -> "Short night (${SleepLog.label(night.asleepMin)}): your team is tired, 10% weaker today. Aim for 7+ hours."
                NORMAL -> ""
            }
            return Update(s, text)
        }
    }
}
