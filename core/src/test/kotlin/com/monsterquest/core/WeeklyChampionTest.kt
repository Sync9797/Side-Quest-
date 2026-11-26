package com.monsterquest.core

import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** One named champion a week, a rule, a prize once a week and a badge for each new record. */
class WeeklyChampionTest {
    private val zone = ZoneId.of("UTC")
    private fun ms(d: String) = LocalDate.parse(d).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val s = GameState(hero = Hero(level = 30))

    @Test fun theSameChampionAllWeekAndANewOneNextWeek() {
        val mon = WeeklyChampion.current(s, ms("2026-11-23"), zone)
        val sun = WeeklyChampion.current(s, ms("2026-11-29"), zone)
        assertEquals(mon, sun)
        val next = WeeklyChampion.current(s, ms("2026-11-30"), zone)
        assertNotEquals(mon.rule, next.rule)
        assertEquals(35, mon.level)
        assertEquals(mon.species.stages, mon.species.stage)
        val m = WeeklyChampion.monster(mon)
        assertEquals(MAX_IV, m.ivs.hp)
        assertEquals(mon.name, m.displayName)
    }

    @Test fun prizeOnceAWeekAndBadgesForRecords() {
        val now = ms("2026-11-25")
        val first = WeeklyChampion.onWin(s, 6, now, zone)
        assertEquals(s.gold + WeeklyChampion.PRIZE_GOLD, first.state.gold)
        assertEquals(1, first.state.championBadges)
        val rule = WeeklyChampion.current(s, now, zone).rule
        assertEquals(6, first.state.championRecords[rule])
        // Slower: no prize, no badge, best stays.
        val slower = WeeklyChampion.onWin(first.state, 9, now, zone)
        assertEquals(first.state.gold, slower.state.gold)
        assertEquals(1, slower.state.championBadges)
        assertEquals(6, slower.state.championWeek?.bestRounds)
        // Faster: a new record and a badge (but the prize was already paid).
        val faster = WeeklyChampion.onWin(slower.state, 4, now, zone)
        assertEquals(2, faster.state.championBadges)
        assertEquals(4, faster.state.championRecords[rule])
        assertEquals(slower.state.gold, faster.state.gold)
        // Next week: the prize again.
        val nextWeek = WeeklyChampion.onWin(faster.state, 10, ms("2026-12-02"), zone)
        assertEquals(faster.state.gold + WeeklyChampion.PRIZE_GOLD, nextWeek.state.gold)
    }

    @Test fun itCantBeTamed() {
        val c = WeeklyChampion.current(s, ms("2026-11-25"), zone)
        val b = Battle(Hero(level = 30), listOf(Monster.create(Bestiary.get("zapmouse"), 30, Random(1))), WeeklyChampion.monster(c), EncounterType.CHAMPION, mapOf(Item.BINDING_CRYSTAL to 3), storageFull = false)
        assertTrue(b.captureBlockedReason() != null)
    }
}
