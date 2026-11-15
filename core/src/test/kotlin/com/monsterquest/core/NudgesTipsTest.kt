package com.monsterquest.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NudgesTipsTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 3, 4)
    private fun at(h: Int, m: Int = 0, d: LocalDate = day) = d.atTime(h, m).toInstant(zone).toEpochMilli()
    private val base = GameEngine.chooseStarter(GameState(), "emberkit", Random(1)).state.copy(hero = Hero(level = 30))
    private fun adventure(startMs: Long, hours: Long) = ActiveAdventure(
        Adventure("a1", "Riverside Park", "Recover a lost relic", 2, 12, hours * 3_600_000L), base.monsters.map { it.uid }, startMs, 0.8,
    )

    @Test
    fun adventuresVisitorsAndEggsGetANudgeOutsideQuietHours() {
        // An adventure ending at 3 PM, one at 11 PM (moved to 8 AM), a visitor, and a ready egg.
        val v = GameEngine.rareVisitor(base, at(12), Random(2))!!
        val egg = Eggs.gift(Bestiary.get("emberkit"), Random(3), 3).let { it.copy(walkedMeters = it.meters) }
        val s = GameEngine.addVisitor(base, v, at(12)).copy(
            adventures = listOf(adventure(at(13), 2), adventure(at(13), 10).let { it.copy(adventure = it.adventure.copy(id = "a2")) }),
            eggs = listOf(egg),
        )
        val n = GameNudges.upcoming(s, at(13, 10), zone)
        val adv = n.filter { it.id.startsWith("adv:") }.map { it.atMs }
        assertEquals(listOf(at(15), at(8, d = day.plusDays(1))), adv)
        val vis = n.single { it.id.startsWith("vis:") }
        assertEquals(at(12) + GameEngine.VISITOR_TTL_MS - GameNudges.VISITOR_WARNING_MS, vis.atMs)
        assertTrue(v.species.name in vis.title)
        assertEquals(at(18), n.single { it.id.startsWith("egg:") }.atMs)
        // Sorted by time, and off when the player turns them off.
        assertEquals(n.sortedBy { it.atMs }, n)
        assertTrue(GameNudges.upcoming(s.copy(settings = s.settings.copy(gameNudges = false)), at(13, 10), zone).isEmpty())
    }

    @Test
    fun aNudgeOnlyGoesOutIfItStillApplies() {
        val a = adventure(at(9), 2)
        val s = base.copy(adventures = listOf(a))
        val id = "adv:${a.adventure.id}:${a.startMs}"
        assertTrue(GameNudges.stillDue(s, id, at(11)))
        // Claimed already: nothing to say.
        assertFalse(GameNudges.stillDue(s.copy(adventures = emptyList()), id, at(11)))
        assertFalse(GameNudges.stillDue(s, "egg:2026-03-04", at(18)))
        assertTrue(GameNudges.withinDailyLimit(2))
        assertFalse(GameNudges.withinDailyLimit(GameNudges.PER_DAY))
        assertEquals(at(8, d = day.plusDays(1)), GameNudges.outsideQuietHours(at(23, 30), zone))
        assertEquals(at(8), GameNudges.outsideQuietHours(at(3), zone))
        assertEquals(at(14), GameNudges.outsideQuietHours(at(14), zone))
        assertEquals(LocalTime.of(22, 0), GameNudges.QUIET_FROM)
    }

    @Test
    fun tipsShowOnce() {
        assertFalse(Tips.seen(base, Tip.DUNGEON))
        val s = Tips.markSeen(base, Tip.DUNGEON)
        assertTrue(Tips.seen(s, Tip.DUNGEON))
        assertFalse(Tips.seen(s, Tip.EGGS))
        assertEquals(s, Tips.markSeen(s, Tip.DUNGEON))
        assertEquals(s.tipsSeen, GameState.fromJson(s.toJson()).tipsSeen)
    }

    @Test
    fun releaseNotesBecomePoints() {
        val body = """
            3.25.0: torches in dungeons

            Traps and torches:
            - Traps are more dangerous and no longer near-certain. Each has a kind
              with its own odds.
            - Light a torch at a trap to pass it safely.

            Co-Authored-By: Someone <x@y>
        """.trimIndent()
        assertEquals(
            listOf("Traps are more dangerous and no longer near-certain. Each has a kind with its own odds.", "Light a torch at a trap to pass it safely."),
            ReleaseNotes.points(body),
        )
        assertEquals(listOf("Fixed a bug in the map."), ReleaseNotes.points("3.0.1: fix\n\nFixed a bug in the map."))
    }

    @Test
    fun aClosedCalendarEventStaysClosedAfterASync() {
        val start = at(10)
        val events = listOf(
            CalendarEvent("church:$start", "Church", start, start + 3_600_000, false, "me"),
            CalendarEvent("gym:$start", "Gym", start, start + 3_600_000, false, "me"),
        )
        var s = GameEngine.syncCalendar(base, events).state
        assertEquals(2, s.tasks.count { it.source == TaskSource.CALENDAR })
        val closed = GameEngine.deleteTask(s, "church:$start", at(9))
        s = closed.state
        assertTrue("calendar" in closed.message)
        assertEquals(listOf("Gym"), s.tasks.map { it.title })
        // The next sync doesn't bring it back, and nothing was paid or lost.
        s = GameEngine.syncCalendar(s, events).state
        assertEquals(listOf("Gym"), s.tasks.map { it.title })
        assertEquals(base.gold, s.gold)
        assertEquals(base.stats.tasksCompleted, s.stats.tasksCompleted)
        // A to-do just goes.
        s = GameEngine.addTask(s, "Take battery out", Difficulty.HARD, Random(1), at(8)).state
        val todo = s.tasks.first { it.source == TaskSource.MANUAL }
        s = GameEngine.deleteTask(s, todo.id, at(9)).state
        assertTrue(s.tasks.none { it.id == todo.id })
        // Closed events are forgotten after a couple of months (they won't sync again by then).
        val later = GameEngine.deleteTask(GameEngine.syncCalendar(s, events).state, "gym:$start", start + 90L * 24 * 3_600_000).state
        assertEquals(setOf("gym:$start"), later.dismissedQuests)
    }

    @Test
    fun clearMissedHidesEndedEventsOnly() {
        val past = at(8)
        val future = at(18)
        val events = listOf(
            CalendarEvent("a:$past", "Church", past, past + 3_600_000, false, "me"),
            CalendarEvent("b:$past", "Dentist", past, past + 1_800_000, false, "me"),
            CalendarEvent("c:$future", "Migraine - AW", future, future + 3_600_000, false, "me"),
        )
        var s = GameEngine.syncCalendar(base, events).state
        s = GameEngine.addTask(s, "Take battery out", Difficulty.HARD, Random(1), at(6), dueMs = at(7)).state
        assertEquals(2, GameEngine.missedEvents(s, at(12)).size)
        val u = GameEngine.closeMissed(s, at(12))
        s = u.state
        assertTrue("2 missed" in u.message)
        // The later event and the overdue to-do stay.
        assertEquals(setOf("Migraine - AW", "Take battery out"), s.tasks.map { it.title }.toSet())
        assertEquals(setOf("Migraine - AW", "Take battery out"), GameEngine.syncCalendar(s, events).state.tasks.map { it.title }.toSet())
        assertEquals(base.gold, s.gold)
        assertEquals("No missed events.", GameEngine.closeMissed(s, at(12)).message)
    }
}
