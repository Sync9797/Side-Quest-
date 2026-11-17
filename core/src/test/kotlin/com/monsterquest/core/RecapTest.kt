package com.monsterquest.core

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecapTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 1, 5)
    private fun at(h: Int, m: Int = 0, d: LocalDate = day) = d.atTime(h, m).toInstant(zone).toEpochMilli()

    private fun started(): GameState {
        val m = Monster.create(Bestiary.get("emberkit"), 5, Random(1))
        return GameState(hero = Hero(level = 3), monsters = listOf(m), party = listOf(m.uid), starterChosen = true)
    }

    @Test
    fun aDaysGainsAddUpAndShowNextMorning() {
        var s = started()
        val before = s
        // Walking: XP for the hero and the monster, and healing.
        val hurt = s.copy(heroDamage = 30, monsters = s.monsters.map { it.copy(damage = 20) })
        s = Recap.track(s, hurt, at(9))
        val walked = GameEngine.walk(hurt, 2_000, at(10), zone).state
        s = Recap.track(hurt, walked, at(10))
        // Loot: gold and a potion.
        val loot = s.copy(gold = s.gold + 120, inventory = s.inventory + (Item.POTION to (s.count(Item.POTION) + 2)))
        s = Recap.track(s, loot, at(12))
        // A purchase isn't loot.
        val bought = s.copy(gold = s.gold - 50, inventory = s.inventory + (Item.MANA_DRAUGHT to (s.count(Item.MANA_DRAUGHT) + 1)))
        s = Recap.track(s, bought, at(13))
        // A task.
        s = Recap.track(s, s.copy(stats = s.stats.copy(tasksCompleted = s.stats.tasksCompleted + 2)), at(15))

        val t = s.tallies.getValue(day.toEpochDay())
        assertTrue(t.hpRestored > 0)
        assertEquals(120, t.gold)
        assertTrue((t.items[Item.POTION] ?: 0) >= 2) // plus any from level-up boxes
        assertNull(t.items[Item.MANA_DRAUGHT])
        assertEquals(2, t.tasks)
        assertTrue(walked.torches > before.torches)

        // Not due the same day, nor before 4 AM; due the next morning, once.
        assertFalse(Recap.due(s, at(20), zone))
        assertFalse(Recap.due(s, at(3, d = day.plusDays(1)), zone))
        assertTrue(Recap.due(s, at(7, d = day.plusDays(1)), zone))
        assertEquals(t, Recap.yesterday(s, at(7, d = day.plusDays(1)), zone))
        val shown = Recap.markShown(s, at(7, d = day.plusDays(1)), zone)
        assertFalse(Recap.due(shown, at(9, d = day.plusDays(1)), zone))
        // Nothing happened yesterday and no sleep: no recap.
        assertFalse(Recap.due(started(), at(8), zone))
        // Yesterday's steps are in the recap (and enough on their own to show it).
        val walker = started().copy(stepHistory = mapOf(day.toEpochDay() - 1 to 8_421))
        assertEquals(8_421, Recap.yesterdaySteps(walker, at(8), zone))
        assertTrue(Recap.due(walker, at(8), zone))
    }

    @Test
    fun sleepLogKeepsOneNightPerMorning() {
        val s0 = started()
        val manual = SleepNight(at(23, d = day.minusDays(1)), at(7), manual = true)
        var s = SleepLog.record(s0, manual, zone)
        assertEquals(480, SleepLog.lastNight(s, day)!!.asleepMin)
        // A synced night replaces the hand-logged one; a hand-logged one doesn't replace a synced one.
        val synced = SleepNight(at(23, 30, day.minusDays(1)), at(6, 45), deepMin = 90, lightMin = 240, remMin = 80, awakeMin = 25)
        s = SleepLog.record(s, synced, zone)
        assertEquals(synced, SleepLog.lastNight(s, day))
        assertTrue(synced.hasStages)
        assertEquals(410, synced.asleepMin)
        s = SleepLog.record(s, manual, zone)
        assertEquals(synced, SleepLog.lastNight(s, day))
        assertEquals("6h 50m", SleepLog.label(410))
        assertEquals(7, SleepLog.week(s, day).size)
        assertEquals(410, SleepLog.weekAverage(s, day))
        // A night's sleep alone makes the morning recap worth showing.
        assertTrue(Recap.due(s, at(8), zone))
        // No rewards.
        assertEquals(s0.gold, s.gold)
        assertEquals(s0.hero, s.hero)
    }

    @Test
    fun aFullNightRefillsEnergyOncePerDay() {
        val tired = started().copy(heroEnergyUsed = 30, energyRegenMs = at(6))
        // A short night: no refill.
        val short = SleepLog.record(tired, SleepNight(at(1, d = day), at(6, d = day)), zone)
        assertEquals(30, SleepLog.restEnergy(short, at(8), zone).state.heroEnergyUsed)
        // A full night (7h+) that ended today: Energy full, once.
        val full = SleepLog.record(tired, SleepNight(at(23, d = day.minusDays(1)), at(7, d = day)), zone)
        val rested = SleepLog.restEnergy(full, at(8), zone)
        assertEquals(0, rested.state.heroEnergyUsed)
        assertTrue("Energy is restored" in rested.message)
        val again = rested.state.copy(heroEnergyUsed = 20)
        assertEquals(20, SleepLog.restEnergy(again, at(12), zone).state.heroEnergyUsed)
        // Last night only: a full night from days ago doesn't count today.
        val old = SleepLog.record(tired, SleepNight(at(23, d = day.minusDays(3)), at(7, d = day.minusDays(2))), zone)
        assertEquals(30, SleepLog.restEnergy(old, at(8), zone).state.heroEnergyUsed)
    }

    @Test
    fun aFullNightHealsTheTeamAndWakesTheFainted() {
        val rng = kotlin.random.Random(2)
        val hurt = Monster.create(Bestiary.get("ember_pup"), 10, rng).copy(damage = 12)
        val down = Monster.create(Bestiary.get("zapmouse"), 10, rng).copy(fainted = true)
        val base = started().copy(monsters = listOf(hurt, down), heroDamage = 15)
        val full = SleepLog.record(base, SleepNight(at(23, d = day.minusDays(1)), at(7, d = day)), zone)
        val rested = SleepLog.restEnergy(full, at(8), zone)
        assertTrue(rested.state.monsters.all { it.damage == 0 && !it.fainted })
        assertEquals(0, rested.state.heroDamage)
        assertTrue("fainted one wakes up" in rested.message)
        // A short night heals nobody.
        val short = SleepLog.record(base, SleepNight(at(1, d = day), at(6, d = day)), zone)
        assertEquals(base.monsters, SleepLog.restEnergy(short, at(8), zone).state.monsters)
    }
}
