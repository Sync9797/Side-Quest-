package com.monsterquest.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The Health section: steps by hour and a simple water tracker. It only
 * helps the player keep track; nothing here pays rewards.
 */
object Health {
    /** A good day's walking (the calendar marks days that reach it). */
    const val STEP_GOAL = 5_000
    /** One glass of water in milliliters, in the player's units (8 oz is about 236.6 ml). */
    fun glassMl(imperial: Boolean): Double = if (imperial) GLASS_OZ * 29.5735 else GLASS_ML.toDouble()

    /**
     * Water from Health Connect (milliliters per epoch day, from every app,
     * the game's own glasses included): each day counts as many glasses as
     * that is, or what the game already had if that's more (glasses logged
     * before syncing was on). Each day keeps the goal it had.
     */
    fun syncWater(state: GameState, mlByDay: Map<Long, Double>, glassMl: Double): GameState {
        if (mlByDay.isEmpty()) return state
        var water = state.water
        var goals = state.waterGoalByDay
        for ((day, ml) in mlByDay) {
            val glasses = Math.round(ml / glassMl).toInt().coerceIn(0, MAX_GLASSES)
            val n = maxOf(glasses, water[day] ?: 0)
            if (n > 0) {
                water = water + (day to n)
                if (day !in goals) goals = goals + (day to state.waterGoal)
            }
        }
        return if (water == state.water && goals == state.waterGoalByDay) state else state.copy(water = water, waterGoalByDay = goals)
    }

    /** One glass of water: 8 oz, or 250 ml in metric. */
    const val GLASS_ML = 250
    const val GLASS_OZ = 8
    const val DEFAULT_WATER_GOAL = 8
    val WATER_GOALS = 4..16
    /** No one logs more than this in a day; stops runaway taps. */
    const val MAX_GLASSES = 30
    /** Hourly steps kept for the last week or so. */
    private const val HOURS_KEPT = 8 * 24L
    /** Water history is kept for about a year (one number a day). */
    const val WATER_DAYS_KEPT = 400

    /** Key for one hour of one local day: epoch day × 24 + hour (works in half-hour time zones too). */
    fun hourKey(day: LocalDate, hour: Int): Long = day.toEpochDay() * 24 + hour

    private fun hourKeyAt(ms: Long, zone: ZoneId): Long {
        val t = Instant.ofEpochMilli(ms).atZone(zone)
        return hourKey(t.toLocalDate(), t.hour)
    }

    /** Adds [steps] to the hour they were walked in. Called from [GameEngine.walk]. */
    fun recordSteps(state: GameState, steps: Int, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): GameState {
        if (steps <= 0) return state
        val key = hourKeyAt(nowMs, zone)
        val kept = state.stepsByHour.filterKeys { it > key - HOURS_KEPT }
        return state.copy(stepsByHour = kept + (key to (kept[key] ?: 0) + steps))
    }

    /** Steps in each hour (0..23) of [day], as the game counted them. */
    fun hourly(state: GameState, day: LocalDate): List<Int> =
        (0 until 24).map { state.stepsByHour[hourKey(day, it)] ?: 0 }

    fun glasses(state: GameState, day: Long): Int = state.water[day] ?: 0

    /** Adds (or with a negative [delta], takes back) glasses of water for today. */
    fun addWater(state: GameState, delta: Int, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): GameState {
        val day = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()
        val n = (glasses(state, day) + delta).coerceIn(0, MAX_GLASSES)
        val kept = state.water.filterKeys { it > day - WATER_DAYS_KEPT }
        return state.copy(
            water = if (n == 0) kept - day else kept + (day to n),
            waterGoalByDay = state.waterGoalByDay.filterKeys { it > day - WATER_DAYS_KEPT } + (day to state.waterGoal),
        )
    }

    /** Changes the goal; today's history follows it (past days keep the goal they had). */
    fun setWaterGoal(state: GameState, goal: Int, nowMs: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): GameState {
        val g = goal.coerceIn(WATER_GOALS.first, WATER_GOALS.last)
        val day = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()
        return state.copy(waterGoal = g, waterGoalByDay = state.waterGoalByDay + (day to g))
    }

    /** The goal [day] was held to: the one it was logged with, or today's goal. */
    fun waterGoalOn(state: GameState, day: Long): Int = state.waterGoalByDay[day] ?: state.waterGoal

    /** Whether [day] reached its water goal. */
    fun waterGoalMet(state: GameState, day: Long): Boolean = glasses(state, day).let { it > 0 && it >= waterGoalOn(state, day) }

    /**
     * Days in a row that reached the water goal, counting back from [today]
     * (or from yesterday while today isn't there yet, so the streak doesn't
     * vanish every morning).
     */
    fun waterStreak(state: GameState, today: Long): Int {
        var d = if (waterGoalMet(state, today)) today else today - 1
        var n = 0
        while (waterGoalMet(state, d)) {
            n++
            d--
        }
        return n
    }

    /** The longest run of goal days in the history. */
    fun bestWaterStreak(state: GameState): Int {
        var best = 0
        var run = 0
        var prev: Long? = null
        for (d in state.water.keys.sorted()) {
            if (!waterGoalMet(state, d)) {
                run = 0
                prev = d
                continue
            }
            run = if (prev == d - 1 && run > 0) run + 1 else 1
            best = maxOf(best, run)
            prev = d
        }
        return best
    }

    /** One day of water history. */
    data class WaterDay(val day: Long, val glasses: Int, val goal: Int) {
        val met: Boolean get() = glasses > 0 && glasses >= goal
    }

    /** The last [days] days, newest first. */
    fun waterHistory(state: GameState, today: Long, days: Int): List<WaterDay> =
        (0 until days).map { i -> (today - i).let { d -> WaterDay(d, glasses(state, d), waterGoalOn(state, d)) } }

    /** "1.5 L" / "40 oz" for [glasses]. */
    fun waterAmount(glasses: Int, imperial: Boolean): String =
        if (imperial) "${glasses * GLASS_OZ} oz"
        else (glasses * GLASS_ML).let { ml -> if (ml < 1000) "$ml ml" else "%.2f".format(java.util.Locale.US, ml / 1000.0).trimEnd('0').trimEnd('.') + " L" }

    /** A step record from a health app: [count] steps between [startMs] and [endMs]. */
    data class StepSpan(val startMs: Long, val endMs: Long, val count: Long)

    /** Records longer than this are too coarse to say when the steps happened. */
    const val COARSE_SPAN_MS = 2 * 60 * 60_000L

    /**
     * Steps in each hour (0..23) of [day], from health-app records, each spread
     * evenly over the time it covers (so a record that starts at midnight
     * doesn't pile everything into 12 AM). Null when most steps come from
     * records too long to place ([COARSE_SPAN_MS]): the chart then uses the
     * game's own counts.
     */
    fun spreadByHour(spans: List<StepSpan>, day: LocalDate, zone: ZoneId): List<Int>? {
        val total = spans.sumOf { it.count }
        if (total <= 0) return List(24) { 0 }
        val coarse = spans.filter { it.endMs - it.startMs > COARSE_SPAN_MS }.sumOf { it.count }
        if (coarse * 2 > total) return null
        val out = DoubleArray(24)
        for (sp in spans) {
            val len = (sp.endMs - sp.startMs).coerceAtLeast(1)
            for (h in 0 until 24) {
                // Hour boundaries in real time (DST-safe).
                val hs = day.atStartOfDay(zone).plusHours(h.toLong()).toInstant().toEpochMilli()
                val he = day.atStartOfDay(zone).plusHours(h + 1L).toInstant().toEpochMilli()
                val overlap = minOf(he, sp.endMs) - maxOf(hs, sp.startMs)
                if (sp.endMs == sp.startMs && sp.startMs in hs until he) out[h] += sp.count.toDouble()
                else if (overlap > 0) out[h] += sp.count * overlap.toDouble() / len
            }
        }
        return out.map { Math.round(it).toInt() }
    }

    /** "12a", "6a", "12p", "3p"... for chart labels. */
    fun hourLabel(hour: Int): String = "${if (hour % 12 == 0) 12 else hour % 12}${if (hour < 12) "a" else "p"}"
}
