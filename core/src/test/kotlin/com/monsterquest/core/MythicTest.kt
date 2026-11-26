package com.monsterquest.core

import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The hidden legendary: Murklet to Turumarth, the Mythical type, its scale and its weekends. */
class MythicTest {
    private val zone = ZoneId.of("UTC")
    private fun ms(date: String, hour: Int = 12) = LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    // 2026-11-28 is a Saturday, 2026-11-25 a Wednesday.
    private val saturday = ms("2026-11-28")
    private val wednesday = ms("2026-11-25")

    private val legendaries = Bestiary.all.filter { it.rarity == Rarity.LEGENDARY }.map { it.id }.toSet()
    private fun allCaught() = GameState(hero = Hero(level = 50), caught = legendaries)

    private fun mon(id: String, level: Int) = Monster.create(Bestiary.get(id), level, Random(1), evolve = false)

    @Test fun mythicalBeatsCorruptGasAndItselfAndIsNeverResisted() {
        val m = Element.MYTHICAL
        assertEquals(1.5, m.multiplierAgainst(Element.CORRUPT))
        assertEquals(1.5, m.multiplierAgainst(Element.GAS))
        assertEquals(1.5, m.multiplierAgainst(Element.MYTHICAL))
        for (t in Element.typed - Element.CORRUPT - Element.GAS) assertEquals(1.0, m.multiplierAgainst(t), "$t")
        // Only Mythical hits it hard.
        assertEquals(listOf(Element.MYTHICAL), m.weakTo)
        for (t in Element.typed) assertTrue(t.multiplierAgainst(Element.MYTHICAL) <= 1.0, "$t")
        // The seven-type wheel is unchanged.
        assertEquals(7, Element.typed.size)
        assertFalse(Element.MYTHICAL in Element.typed)
    }

    @Test fun theLineIsHiddenAndEvolvesAt30_60_100() {
        val line = Mythic.LINE.map { Bestiary.get(it) }
        assertEquals(listOf(Element.WATER, Element.FIRE, Element.FIRE, Element.MYTHICAL), line.map { it.element })
        assertEquals(listOf(Element.FIRE, Element.WATER), line[2].types)
        assertEquals(listOf(30, 60, 100, null), line.map { it.evolveLevel })
        for (sp in line) {
            assertFalse(sp in Bestiary.all, "${sp.id} is listed")
            assertFalse(sp in Bestiary.wild)
            assertFalse(Bestiary.isListed(sp.id))
            assertEquals(line, Bestiary.lineOf(sp))
        }
    }

    @Test fun stageOneIsTheWeakestMonsterAndStageFourTheStrongest() {
        val totals = Bestiary.all.associateWith { MaxStats.total(it) }
        val murklet = MaxStats.total(Bestiary.get(Mythic.MURKLET))
        val turumarth = MaxStats.total(Bestiary.get(Mythic.TURUMARTH))
        assertTrue(totals.values.all { murklet < it }, "Murklet $murklet vs weakest ${totals.values.min()}")
        assertTrue(totals.values.all { turumarth > it }, "Turumarth $turumarth vs strongest ${totals.values.max()}")
        assertEquals(1, MaxStats.rank(Bestiary.get(Mythic.TURUMARTH)))
    }

    @Test fun turumarthLearnsEveryMoveAndEachStageItsSignature() {
        val t = mon(Mythic.TURUMARTH, 100)
        assertEquals(Moves.all.map { it.id }.toSet(), t.learnedMoves.map { it.id }.toSet())
        for ((i, id) in Mythic.LINE.withIndex()) {
            val lvl = listOf(1, 30, 60, 100)[i]
            assertTrue(Mythic.SIGNATURES[i].first in mon(id, lvl).learnedMoves.map { it.id }, id)
        }
    }

    @Test fun steamheartStormStrikesAsWhicheverTypeHitsHarder() {
        val storm = Moves.get("steamheart_storm")
        assertTrue(MoveEffect.EitherType(Element.WATER) in storm.effects)
        assertEquals(Element.MYTHICAL, Moves.get("genesis_bloom").type)
    }

    @Test fun noCandyEvolution() {
        val m = mon(Mythic.MURKLET, 30)
        val s = GameState(monsters = listOf(m), candy = mapOf("Murklet line" to 999))
        val u = GameEngine.evolve(s, m.uid)
        assertEquals(Mythic.MURKLET, u.state.monsters.single().speciesId)
        assertTrue("candy" in u.message)
    }

    @Test fun itEvolvesByLevelOneStageAtATime() {
        val m = mon(Mythic.MURKLET, 61)
        var s = GameState(monsters = listOf(m))
        assertEquals(m.uid, Mythic.dueToEvolve(s)?.uid)
        s = Mythic.evolve(s, m.uid).state
        assertEquals(Mythic.CINDERWRITHE, s.monsters.single().speciesId)
        s = Mythic.evolve(s, m.uid).state
        assertEquals(Mythic.FUMEWARDEN, s.monsters.single().speciesId)
        assertNull(Mythic.dueToEvolve(s))
        // Other monsters never use this pop-up.
        val other = Monster.create(Bestiary.all.first { it.evolveLevel != null }, 60, Random(2), evolve = false)
        assertNull(Mythic.dueToEvolve(GameState(monsters = listOf(other))))
    }

    @Test fun itAppearsOnlyOnceEveryLegendaryIsCaught() {
        val missing = GameState(hero = Hero(level = 50), caught = legendaries - legendaries.first())
        assertFalse(Mythic.omenDue(missing))
        assertTrue((0 until 300).none { Mythic.maybeEncounter(missing, wednesday, Random(it), zone) != null })
        val ready = allCaught()
        assertTrue(Mythic.omenDue(ready))
        val hits = (0 until 300).mapNotNull { Mythic.maybeEncounter(ready, wednesday, Random(it), zone) }
        assertTrue(hits.size in 10..60, "${hits.size}")
        for ((m, type) in hits) {
            assertEquals(EncounterType.OMEN, type)
            assertEquals(Mythic.MURKLET, m.speciesId)
            assertTrue(m.level <= Mythic.MAX_MEET_LEVEL)
        }
    }

    @Test fun beatenItFleesAndLeavesTheScale() {
        val s = allCaught()
        val after = Mythic.onOmenBeaten(s)
        assertTrue(after.mythScale)
        assertFalse(Mythic.omenDue(after))
        assertEquals(s.inventory, after.inventory, "the scale takes no bag slot")
    }

    private fun task(id: String, dueMs: Long?, done: Boolean, createdMs: Long = wednesday) =
        QuestTask(id, id, dueMs = dueMs, createdAtMs = createdMs, completedAtMs = if (done) dueMs ?: createdMs else null)

    @Test fun itRoamsOnWeekendsOnlyWhenEveryTaskIsDone() {
        val tasks = listOf(task("a", ms("2026-11-24"), true), task("b", ms("2026-11-26"), true), task("c", null, true))
        val s = allCaught().copy(mythScale = true, tasks = tasks)
        assertEquals(3 to 3, Mythic.weekTasks(s, saturday, zone))
        assertTrue(Mythic.roaming(s, saturday, zone))
        assertTrue(Mythic.roaming(s, ms("2026-11-29"), zone))
        // Not on a weekday.
        assertFalse(Mythic.roaming(s, wednesday, zone))
        // Not with a task left undone this week.
        val undone = s.copy(tasks = tasks + task("d", ms("2026-11-27"), false))
        assertFalse(Mythic.roaming(undone, saturday, zone))
        // Last week's tasks don't count, and a task due later than today doesn't block yet.
        val other = s.copy(tasks = tasks + task("old", ms("2026-11-18"), false, ms("2026-11-17")) + task("next", ms("2026-11-29"), false))
        assertTrue(Mythic.roaming(other, saturday, zone))
        // Not with no tasks at all, not without the scale, and not once you have it.
        assertFalse(Mythic.roaming(s.copy(tasks = emptyList()), saturday, zone))
        assertFalse(Mythic.roaming(s.copy(mythScale = false), saturday, zone))
        assertFalse(Mythic.roaming(s.copy(monsters = listOf(mon(Mythic.MURKLET, 5))), saturday, zone))
        // Roaming, it's an ordinary (tameable) wild encounter, and rare.
        val hits = (0 until 400).mapNotNull { Mythic.maybeEncounter(s, saturday, Random(it), zone) }
        assertTrue(hits.isNotEmpty() && hits.size < 70, "${hits.size}")
        assertTrue(hits.all { it.second == EncounterType.WILD })
    }

    @Test fun theScaleTracksTheWeek() {
        val s = allCaught().copy(mythScale = true, tasks = listOf(task("a", ms("2026-11-24"), true), task("b", ms("2026-11-25"), false)))
        assertTrue("1 of 2" in Mythic.scaleText(s, wednesday, zone), Mythic.scaleText(s, wednesday, zone))
        val done = s.copy(tasks = s.tasks.map { it.copy(completedAtMs = wednesday) })
        assertTrue("warm" in Mythic.scaleText(done, saturday, zone))
    }

    @Test fun theOmenCantBeTamed() {
        val b = Battle(Hero(level = 20), listOf(mon(Mythic.MURKLET, 10).copy(uid = "x")), mon(Mythic.MURKLET, 5), EncounterType.OMEN, mapOf(Item.POTION to 1), storageFull = false)
        assertNotNull(b.captureBlockedReason())
    }

    @Test fun hiddenSpeciesDontCountTowardTheJournal() {
        val s = GameState(caught = setOf(Mythic.MURKLET, Mythic.TURUMARTH))
        val journal = Achievements.all.first { it.id == "journal_all" }
        assertEquals(0, journal.progress(s))
    }
}
