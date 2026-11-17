package com.monsterquest.core

import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FurcloakStepsTest {
    @Test fun furcloaksNeverRollAtRandom() {
        val rng = Random(9)
        repeat(2_000) { assertTrue(Armory.rollArmor(40, rng).type != ArmorType.FURCLOAK) }
        repeat(500) { assertTrue(Armory.randomArmorPartType(rng) != ArmorType.FURCLOAK) }
        val fur = Armory.rollArmor(40, rng, type = ArmorType.FURCLOAK, minimum = GearRarity.RARE)
        assertEquals(ArmorSlot.BACK, fur.type.slot)
        assertEquals("Furcloak", fur.type.label)
    }

    @Test fun championsCanCarryAFurcloak() {
        val d = Dungeon("dg:1:1:1", "Ember Forge", DungeonTheme.FORGE, 3, 12, 0.0, 0.0, seed = 7L)
        val fur = Dungeons.bossRewards(d).last()
        assertTrue("Furcloak" in fur.label)
        assertEquals(Dungeons.FURCLOAK_PCT + 6, fur.pct)
    }

    @Test fun stepDaysFillGapsAndAverageFromTheFirstWalk() {
        val today = LocalDate.of(2026, 3, 10)
        val t = today.toEpochDay()
        val s = GameState(stepHistory = mapOf(t - 3 to 6_000, t - 1 to 2_000))
        val week = Health.stepDays(s, today, 7, 4_000)
        assertEquals(7, week.size)
        assertEquals(today, week.last().first)
        assertEquals(4_000, week.last().second)
        assertEquals(0, week[2].second)
        // From the first day with steps: (6000 + 0 + 2000 + 4000) / 4.
        assertEquals(3_000, Health.stepAverage(week))
    }
}
