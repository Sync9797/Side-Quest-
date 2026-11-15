package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExoticWeaponsTest {
    private val base = GameEngine.chooseStarter(GameState(), "emberkit", Random(1)).state

    @Test
    fun exoticWeaponsAppearFromLevelFifty() {
        val early = (1..400).map { Armory.roll(49, Random(it)).type }.toSet()
        assertTrue(early.none { it.exotic }, "$early")
        val late = (1..400).map { Armory.roll(60, Random(it)).type }.toSet()
        assertTrue(late.containsAll(listOf(WeaponType.AXE, WeaponType.BOW, WeaponType.SCYTHE)))
        assertTrue((1..200).none { Armory.randomPartType(Random(it), 30).exotic })
    }

    @Test
    fun everyClassStartsUnfamiliarAndTakesLongToMaster() {
        val axe = Gear("a", WeaponType.AXE, GearRarity.EPIC, 60)
        for (cls in HeroClass.entries) {
            assertTrue(axe.foreign(cls), "$cls")
            assertTrue(axe.classStats(cls) < 0, "$cls starts with a penalty")
            // Even a perk that makes any weapon feel familiar doesn't help with an exotic one.
            assertTrue(axe.classStats(cls, anyWeapon = true) < 0)
        }
        assertEquals(EXOTIC_MASTERY_BATTLES, WeaponType.AXE.masteryBattles)
        var g = axe
        repeat(PROFICIENCY_BATTLES) { g = g.practiced() }
        assertTrue(!g.mastered && g.classStats(HeroClass.WARRIOR) < 0, "10 battles isn't enough")
        repeat(EXOTIC_MASTERY_BATTLES) { g = g.practiced() }
        assertTrue(g.mastered)
        assertEquals(0, g.classStats(HeroClass.WARRIOR))
        // A sword still suits a Warrior from the start.
        assertTrue(Gear("s", WeaponType.SWORD, GearRarity.EPIC, 60).classStats(HeroClass.WARRIOR) > 0)
    }

    @Test
    fun eachExoticWeaponHasItsTradeOff() {
        val h = Hero(level = 60)
        val sword = Combatant.fromHero(h, Gear("s", WeaponType.SWORD, GearRarity.EPIC, 60, proficiency = 99))
        val axe = Combatant.fromHero(h, Gear("a", WeaponType.AXE, GearRarity.EPIC, 60, proficiency = 99))
        val bow = Combatant.fromHero(h, Gear("b", WeaponType.BOW, GearRarity.EPIC, 60, proficiency = 99))
        val scythe = Combatant.fromHero(h, Gear("c", WeaponType.SCYTHE, GearRarity.EPIC, 60, proficiency = 99))
        assertTrue(axe.attack > sword.attack && axe.speed < sword.speed && axe.weaponAccuracy < 100 && axe.weaponPierce)
        assertTrue(bow.speed > sword.speed && bow.critBonus > sword.critBonus && bow.defense < sword.defense)
        assertTrue(scythe.weaponSweepPct > 0 && scythe.magic > sword.magic && scythe.defense < sword.defense)
        assertTrue(WeaponType.entries.filter { it.exotic }.all { it.drawback.isNotEmpty() })
    }

    @Test
    fun aScytheReapsTheWholePack() {
        val team = listOf(Monster.create(Bestiary.get("blazecat"), 60, Random(1)))
        val s = base.copy(
            hero = base.hero.copy(level = 60), monsters = team, party = team.map { it.uid },
            equippedGear = Gear("c", WeaponType.SCYTHE, GearRarity.LEGENDARY, 60, proficiency = 99),
        )
        val pack = (1..2).map { Monster.create(Bestiary.get("zapmouse"), 40, Random(it + 10)) }
        val b = GameEngine.newBattle(s, Monster.create(Bestiary.get("zapmouse"), 40, Random(9)), EncounterType.WILD, rng = Random(3), pack = pack)
        var guard = 0
        while (b.current?.isHero != true && b.outcome == BattleOutcome.ONGOING && guard++ < 10) b.act(BattleAction.Defend)
        val before = b.enemies.map { it.hp }
        b.act(BattleAction.Attack)
        assertTrue(b.enemies.zip(before).count { (e, hp) -> e.hp < hp } >= 2, b.log.takeLast(5).joinToString("\n"))
    }

    @Test
    fun relicsAreNamedLegendariesFoundLate() {
        assertNull(Relic.roll(49, Random(1), 1.0))
        val g = Relic.roll(60, Random(2), 1.0)!!
        assertEquals(GearRarity.LEGENDARY, g.rarity)
        assertEquals(g.relic!!.title, g.name)
        assertEquals(g.relic!!.effects, g.effects)
        assertTrue(Relic.entries.map { it.type }.containsAll(listOf(WeaponType.AXE, WeaponType.BOW, WeaponType.SCYTHE)))
        assertTrue(Relic.entries.all { it.effects.size == GearRarity.LEGENDARY.effectCount && it.lore.isNotEmpty() })
        // A relic survives a save.
        val s = base.copy(gear = listOf(g))
        assertEquals(g, GameState.fromJson(s.toJson()).gear.single())
    }

    @Test
    fun levelFiftyOffersThreeMysteryChests() {
        assertTrue(!ExoticChests.due(base.copy(hero = base.hero.copy(level = 49))))
        val s = base.copy(hero = base.hero.copy(level = 50))
        assertTrue(ExoticChests.due(s))
        val chests = ExoticChests.chests(s)
        assertEquals(setOf(WeaponType.AXE, WeaponType.BOW, WeaponType.SCYTHE), chests.map { it.type }.toSet())
        assertTrue(chests.all { it.rarity >= GearRarity.EPIC })
        assertEquals(chests, ExoticChests.chests(s))
        val u = ExoticChests.open(s, 1)
        assertEquals(chests[1], u.state.gear.last())
        assertTrue(!ExoticChests.due(u.state))
        assertEquals(u.state, ExoticChests.open(u.state, 0).state)
    }
}
