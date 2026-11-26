package com.monsterquest.core

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TorchesTest {
    private val utc = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 11, 1)
    private fun at(h: Int) = day.atTime(h, 0).toInstant(utc).toEpochMilli()
    private fun start() = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state

    @Test
    fun walkingAndMorningsBringTorches() {
        // 1,000 steps light a torch.
        var s = GameEngine.walk(start(), 2_700, at(9), utc).state
        assertEquals(2, s.torches)
        assertEquals(700, s.torchSteps)
        s = Torches.morning(s, at(7), utc)!!.state
        assertEquals(2 + Torches.MORNING, s.torches)
        assertEquals(null, Torches.morning(s, at(12), utc))
        assertEquals(Torches.cap(s), Torches.add(s, 99).torches)
    }

    @Test
    fun sleepSetsTheDaysRest() {
        fun night(hours: Double) = start().copy(sleep = mapOf(day.toEpochDay() to SleepNight(at(0) - (hours * 3_600_000).toLong(), at(0))))
        val rested = night(8.5)
        val well = Rest.update(rested, at(8), utc).state
        assertEquals(Rest.WELL_RESTED, Rest.today(well, at(9), utc))
        // A good night brings a boost, not torches.
        assertEquals(rested.torches, well.torches)
        assertEquals(Rest.TIRED, Rest.today(Rest.update(night(6.0), at(8), utc).state, at(9), utc))
        assertEquals(Rest.NORMAL, Rest.today(Rest.update(night(7.5), at(8), utc).state, at(9), utc))
        // Only for that day.
        assertEquals(Rest.NORMAL, Rest.today(well, at(9) + 86_400_000L, utc))
        // Tired fighters are weaker in battle.
        val foe = Monster.create(Bestiary.get("zapmouse"), 5, Random(2))
        val tired = Rest.update(night(5.0), at(8), utc).state
        val bT = Battle(tired.hero, tired.partyMonsters, foe, EncounterType.WILD, emptyMap(), false, restFactor = Rest.TIRED.statFactor)
        val bN = Battle(tired.hero, tired.partyMonsters, foe, EncounterType.WILD, emptyMap(), false)
        assertTrue(bT.allies.first().attack < bN.allies.first().attack)
    }

    @Test
    fun theCapGrowsWithLevelAndRewardsCanGoALittleOver() {
        assertEquals(10, Torches.cap(1))
        assertEquals(10, Torches.cap(9))
        assertEquals(15, Torches.cap(10))
        assertEquals(20, Torches.cap(25))
        assertEquals(25, Torches.cap(39))
        assertEquals(30, Torches.cap(40))
        assertEquals(30, Torches.cap(120))
        val s = start().copy(torches = 0)
        val cap = Torches.cap(s)
        // Walking stops at the cap.
        val walked = GameEngine.walk(s, 50_000, at(9), utc).state
        assertEquals(cap, walked.torches)
        assertEquals(0, walked.torchSteps)
        assertTrue(Torches.full(walked))
        // An earned reward never goes over the cap: what doesn't fit becomes gold.
        val rewarded = Torches.add(walked, 3, reward = true)
        assertEquals(cap, rewarded.torches)
        assertEquals(walked.gold + 3 * Torches.GOLD_PER_SPARE_TORCH, rewarded.gold)
        assertTrue("3 torches past your limit" in Torches.spareText(walked, 3))
        // A save holding more than the cap (from before) is brought down to it, with gold for the rest.
        val over = walked.copy(torches = cap + 5)
        val fixed = GameState.fromJson(over.toJson())
        assertEquals(cap, fixed.torches)
        assertEquals(over.gold + 5 * Torches.GOLD_PER_SPARE_TORCH, fixed.gold)
    }
}
