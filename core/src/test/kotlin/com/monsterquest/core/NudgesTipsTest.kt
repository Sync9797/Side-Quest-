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
}
