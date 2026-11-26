package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EggsTest {
    @Test
    fun twoOfALineMakeAnEggThatHatchesByWalking() {
        val r = Random(4)
        val a = Monster.create(Bestiary.get("flarepanther"), 30, r).copy(ivs = StatBlock(15, 2, 0, 15, 2)).withGender(Gender.MALE)
        val b = Monster.create(Bestiary.get("emberkit"), 22, r).copy(ivs = StatBlock(2, 15, 0, 2, 15)).withGender(Gender.FEMALE)
        var s = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state
        s = s.copy(monsters = s.monsters + a + b, gold = 1000, candy = s.candy + ("Emberkit line" to 40))
        assertEquals(setOf(b.uid), Eggs.partners(s, a, 10L).map { it.uid }.toSet(), "a=${a.species.family} L${a.level} b=${b.species.family} L${b.level} f=${b.fainted} bred=${b.bredAtMs} all=${s.monsters.map { it.speciesId + ":" + it.level }}")
        val u = Eggs.breed(s, a.uid, b.uid, 10L, Random(5))
        s = u.state
        val egg = s.eggs.single()
        assertEquals("emberkit", egg.speciesId)
        assertEquals(1000 - Eggs.BREED_GOLD, s.gold)
        assertEquals(40 - Eggs.BREED_CANDY, GameEngine.candyOf(s, a.species))
        // At least three stats are the better parent's (15 here).
        assertTrue(MONSTER_STATS.count { egg.ivs[it] == 15 } >= Eggs.INHERITED_STATS, egg.ivs.toString())
        // The parents rest a day.
        assertTrue(Eggs.partners(s, s.monsters.first { it.uid == a.uid }, 1_000L).isEmpty())
        assertEquals(s, Eggs.breed(s, a.uid, b.uid, 1_000L).state)
        // Walk it: not yet, then hatched.
        assertTrue(!Eggs.hatch(s, egg.id).state.eggs.isEmpty())
        s = GameEngine.walk(s, 8_000).state
        assertTrue(s.eggs.single().ready, "walked ${s.eggs.single().walkedMeters} of ${egg.meters}")
        val hatched = Eggs.hatch(s, egg.id, Random(6)).state
        assertTrue(hatched.eggs.isEmpty())
        val baby = hatched.monsters.last()
        assertEquals("emberkit", baby.speciesId)
        assertEquals(egg.ivs, baby.ivs)
        assertEquals(1, hatched.stats.eggsHatched)
    }

    @Test
    fun anEggTakesAMaleAndAFemale() {
        val r = Random(4)
        val a = Monster.create(Bestiary.get("flarepanther"), 30, r).withGender(Gender.MALE)
        val b = Monster.create(Bestiary.get("emberkit"), 22, r).withGender(Gender.MALE)
        var s = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state
        s = s.copy(monsters = s.monsters + a + b, gold = 1000, candy = s.candy + ("Emberkit line" to 40))
        assertTrue(Eggs.partners(s, a, 10L).isEmpty())
        assertEquals(s, Eggs.breed(s, a.uid, b.uid, 10L).state)
    }

    @Test
    fun legendariesAndMythicsHaveNoGenderAndNeverBreed() {
        val r = Random(2)
        for (sp in Bestiary.all) {
            val m = Monster.create(sp, 40, r)
            if (sp.rarity == Rarity.LEGENDARY || sp.id in Bestiary.MYTHIC_IDS) {
                assertEquals(null, m.gender, sp.id)
                assertTrue(!Eggs.canBreed(m, 0L), sp.id)
            } else {
                assertTrue(m.gender != null, sp.id)
            }
        }
        // Roughly even odds.
        val males = (1..2000).count { Monster.create(Bestiary.get("emberkit"), 5, r).gender == Gender.MALE }
        assertTrue(males in 850..1150, "$males of 2000 male")
    }

    @Test
    fun adventuresOfTwoOrMoreOftenBringAnEgg() {
        val r = Random(9)
        val s = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state
        val a = Monster.create(Bestiary.get("emberkit"), 10, r).withGender(Gender.MALE)
        val b = Monster.create(Bestiary.get("emberkit"), 10, r).withGender(Gender.FEMALE)
        val other = Monster.create(Bestiary.get("tidefin"), 10, r)
        val legend = Monster.create(Bestiary.get("thunder_titan"), 50, r)
        // About 75% of the time.
        val hits = (1..2000).count { Eggs.fromAdventure(s, listOf(a, other), r) != null }
        assertTrue(hits in 1400..1600, "$hits of 2000")
        // Never for one monster alone, or a team that can't pass on an egg.
        assertTrue((1..200).none { Eggs.fromAdventure(s, listOf(a), r) != null })
        assertTrue((1..200).none { Eggs.fromAdventure(s, listOf(legend, legend.copy(uid = "x")), r) != null })
        // A male and female of a line pass on their own egg.
        val (withEgg, note) = generateSequence(0) { it + 1 }.firstNotNullOf { Eggs.fromAdventure(s, listOf(a, b), r) }
        assertEquals("emberkit", withEgg.eggs.single().speciesId)
        assertTrue("came back with an egg" in note, note)
        // Full egg slots: the egg is left behind.
        val full = s.copy(eggs = List(Eggs.MAX_EGGS) { withEgg.eggs.single().copy(id = "e$it") })
        val (same, _) = generateSequence(0) { it + 1 }.firstNotNullOf { Eggs.fromAdventure(full, listOf(a, b), r) }
        assertEquals(full, same)
    }
}

class EggSlotsTest {
    @Test
    fun fiveEggsButOnlyThreeWarm() {
        val r = Random(3)
        var s = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state
        val eggs = List(Eggs.MAX_EGGS) { Eggs.gift(Bestiary.get("emberkit"), r, 0).copy(id = "e$it") }
        assertEquals(5, Eggs.MAX_EGGS)
        s = s.copy(eggs = eggs)
        s = Eggs.addWalk(s, 500.0)
        assertEquals(listOf(500.0, 500.0, 500.0, 0.0, 0.0), s.eggs.map { it.walkedMeters })
        // Move a waiting egg up: it warms, and the third one waits.
        s = Eggs.warmFirst(s, "e4").state
        assertEquals("e4", s.eggs.first().id)
        assertEquals(setOf("e4", "e0", "e1"), Eggs.warming(s))
        s = Eggs.addWalk(s, 100.0)
        assertEquals(100.0, s.eggs.first { it.id == "e4" }.walkedMeters)
        assertEquals(500.0, s.eggs.first { it.id == "e2" }.walkedMeters)
    }

    @Test
    fun incubatorHalvesTheDistanceOnce() {
        val r = Random(3)
        var s = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state
        val egg = Eggs.gift(Bestiary.get("emberkit"), r, 0).copy(id = "e", walkedMeters = 300.0)
        s = s.copy(eggs = listOf(egg), inventory = mapOf(Item.INCUBATOR to 2))
        assertEquals(800, Item.INCUBATOR.price)
        s = Eggs.incubate(s, "e").state
        assertEquals(egg.meters / 2, s.eggs.single().meters)
        assertEquals(300.0, s.eggs.single().walkedMeters)
        assertEquals(1, s.count(Item.INCUBATOR))
        // Once per egg.
        assertEquals(s, Eggs.incubate(s, "e").state)
    }
}

/** The same monster with an id that gives it [g]. */
internal fun Monster.withGender(g: Gender): Monster {
    var i = 0
    while (Gender.of("$uid$i") != g) i++
    return copy(uid = "$uid$i")

    @Test
    fun theEggChecklistSaysWhatsMissing() {
        val rng = Random(4)
        var a = Monster.create(Bestiary.get("emberkit"), 22, rng)
        while (a.gender == null) a = Monster.create(Bestiary.get("emberkit"), 22, Random(rng.nextInt()))
        val s = GameState(monsters = listOf(a), gold = 50)
        val list = Eggs.checklist(s, a, 0L)
        assertTrue(list.first().second)
        // No partner, not enough gold or candy: each shows as missing.
        assertTrue(list.any { "partner" !in it.first && "gold" in it.first && !it.second })
        assertTrue(list.any { it.first.startsWith("A ") && !it.second })
        // No other monster of the line: no notes; a same-gender one says why.
        assertTrue(Eggs.partnerNotes(s, a, 0L).isEmpty())
        var same = Monster.create(Bestiary.get("emberkit"), 22, Random(9))
        var seed = 10
        while (same.gender != a.gender) same = Monster.create(Bestiary.get("emberkit"), 22, Random(seed++))
        assertTrue(Eggs.partnerNotes(s.copy(monsters = listOf(a, same)), a, 0L).single().endsWith("same gender"))
    }
}
