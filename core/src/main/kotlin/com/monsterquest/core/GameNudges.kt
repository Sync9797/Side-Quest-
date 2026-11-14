package com.monsterquest.core

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** A notification to post at [atMs] if it still applies then (checked with [GameNudges.stillDue]). */
data class Nudge(val id: String, val atMs: Long, val title: String, val text: String)

/**
 * Gentle notifications about things waiting in the game while you're away:
 * an adventure team back home, a rare visitor about to leave, an egg ready
 * to hatch. Never at night ([QUIET_FROM] to [QUIET_UNTIL], moved to the
 * morning instead) and at most [PER_DAY] a day.
 */
object GameNudges {
    const val PER_DAY = 3
    val QUIET_FROM: LocalTime = LocalTime.of(22, 0)
    val QUIET_UNTIL: LocalTime = LocalTime.of(8, 0)
    /** Evening reminder time for a ready egg left unhatched. */
    val EGG_TIME: LocalTime = LocalTime.of(18, 0)
    /** How long before a rare visitor leaves to remind you. */
    const val VISITOR_WARNING_MS = 30 * 60_000L

    /** [atMs], moved out of the quiet hours to the next morning. */
    fun outsideQuietHours(atMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val t = Instant.ofEpochMilli(atMs).atZone(zone)
        val time = t.toLocalTime()
        return when {
            time >= QUIET_FROM -> t.toLocalDate().plusDays(1).atTime(QUIET_UNTIL).atZone(zone).toInstant().toEpochMilli()
            time < QUIET_UNTIL -> t.toLocalDate().atTime(QUIET_UNTIL).atZone(zone).toInstant().toEpochMilli()
            else -> atMs
        }
    }

    /** Everything worth a notification from now on, in time order (only what hasn't happened yet). */
    fun upcoming(state: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<Nudge> {
        if (!state.settings.gameNudges) return emptyList()
        val out = mutableListOf<Nudge>()
        for (a in state.adventures) {
            if (a.endMs <= nowMs) continue
            out += Nudge(
                "adv:${a.adventure.id}:${a.startMs}", outsideQuietHours(a.endMs, zone),
                "Your monsters are back",
                "The team from ${a.adventure.title} is home: open Adventures to see what they found.",
            )
        }
        for (v in GameEngine.activeVisitors(state, nowMs)) {
            // A warning that comes after the visitor has left is no use, so quiet hours don't move this one.
            val at = v.createdMs + GameEngine.VISITOR_TTL_MS - VISITOR_WARNING_MS
            if (at <= nowMs || inQuietHours(at, zone)) continue
            out += Nudge(
                "vis:${v.id}", at,
                "A rare ${v.species.name} is waiting",
                "Only ${VISITOR_WARNING_MS / 60_000} minutes left: turn on the battery saver and go for a walk to meet it.",
            )
        }
        if (state.eggs.any { it.ready }) {
            val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            var at = today.atTime(EGG_TIME).atZone(zone).toInstant().toEpochMilli()
            if (at <= nowMs) at = today.plusDays(1).atTime(EGG_TIME).atZone(zone).toInstant().toEpochMilli()
            val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
            out += Nudge("egg:$day", at, "An egg is ready to hatch", "It's warm and wriggling: open the game to see who's inside.")
        }
        return out.sortedBy { it.atMs }
    }

    fun inQuietHours(atMs: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val time = Instant.ofEpochMilli(atMs).atZone(zone).toLocalTime()
        return time >= QUIET_FROM || time < QUIET_UNTIL
    }

    /** Whether a nudge still applies when its time comes (the adventure not claimed, the visitor not met, the egg not hatched). */
    fun stillDue(state: GameState, nudgeId: String, nowMs: Long): Boolean {
        if (!state.settings.gameNudges) return false
        return when {
            nudgeId.startsWith("adv:") -> state.adventures.any { "adv:${it.adventure.id}:${it.startMs}" == nudgeId && it.done(nowMs) }
            nudgeId.startsWith("vis:") -> GameEngine.activeVisitors(state, nowMs).any { "vis:${it.id}" == nudgeId }
            nudgeId.startsWith("egg:") -> state.eggs.any { it.ready }
            else -> false
        }
    }

    /** Whether another notification fits today, given how many were sent ([sentToday]). */
    fun withinDailyLimit(sentToday: Int): Boolean = sentToday < PER_DAY
}
