package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GearTiersTest {
    private fun hero(level: Int) = GameState(
        hero = Hero(level = level),
        monsters = listOf(Monster.create(Bestiary.get("emberkit"), level, Random(1))),
        starterChosen = true,
    ).let { it.copy(party = it.monsters.map { m -> m.uid }) }

    private fun shares(level: Int, n: Int = 20_000): Map<Material, Double> {
        val r = Random(level)
        return (1..n).map { Armory.rollMaterial(level, r) }.groupingBy { it }.eachCount().mapValues { it.value.toDouble() / n }
    }

    @Test
    fun materialsScaleBonusesAndGetLikelierWithLevelButStayCapped() {
        val wood = Gear("w", WeaponType.SWORD, GearRarity.NORMAL, 20)
        val bonuses = Material.entries.map { wood.copy(material = it).attackBonus(HeroClass.WARRIOR) }
        assertEquals(bonuses.sorted(), bonuses)
        assertEquals(bonuses.size, bonuses.toSet().size)
        assertEquals("Diamond Plate", Armor("a", ArmorType.PLATE, GearRarity.NORMAL, 5, material = Material.DIAMOND).name)
        assertEquals("Wooden Sword", wood.name)

        // Level 10: mostly wooden/leather, some bronze, diamond possible but very rare.
        val low = shares(10)
        assertTrue((low[Material.BASIC] ?: 0.0) > 0.6)
        assertTrue((low[Material.BRONZE] ?: 0.0) in 0.1..0.3)
        assertTrue((low[Material.DIAMOND] ?: 0.0) < 0.005)
        assertTrue(Material.DIAMOND in (1..200_000).map { Armory.rollMaterial(10, Random(it)) }.toSet(), "diamond can happen at level 10")
        // Milestones on the way: iron is the usual find from 40, gold from 65; nothing waits for 100.
        assertEquals(Material.BRONZE, Armory.materialTier(20).usual)
        assertEquals(Material.IRON, Armory.materialTier(40).usual)
        assertEquals(Material.GOLD, Armory.materialTier(65).usual)
        assertTrue((shares(30)[Material.IRON] ?: 0.0) > 0.25)
        assertTrue((shares(50)[Material.GOLD] ?: 0.0) > 0.25)
        assertEquals(30, Armory.nextMaterialTier(25)?.fromLevel)
        // Diamond never passes the cap.
        assertTrue(Armory.MATERIAL_TIERS.all { t -> t.weights.getValue(Material.DIAMOND) / t.weights.values.sum() <= 0.051 })
        // Level 100: better gear common, but diamond is capped.
        val high = shares(100)
        assertTrue((high[Material.BASIC] ?: 0.0) < 0.2)
        assertTrue((high[Material.DIAMOND] ?: 0.0) in 0.02..0.07)
    }

    @Test
    fun armorScrapForgesAndSmeltsAndDropsFromThugs() {
        var s = hero(10).copy(armorParts = mapOf(ArmorType.BOOTS to PARTS_TO_FORGE), gold = 100)
        s = GameEngine.forgeArmor(s, ArmorType.BOOTS, Random(2)).state
        assertEquals(ArmorType.BOOTS, s.armor.single().type)
        assertEquals(0, s.armorParts[ArmorType.BOOTS])
        s = GameEngine.smeltArmor(s, s.armor.single().id).state
        assertTrue((s.armorParts[ArmorType.BOOTS] ?: 0) >= 1)
        assertEquals(100 - SMELT_COST, s.gold)

        // Beating a thug crew drops weapon parts and armor scrap.
        val crew = Villains.ambusher(10, 1, Random(3))
        val b = GameEngine.newBattle(hero(10), crew.second.first(), EncounterType.THUG, rng = Random(4), thug = crew.first)
        b.enemies.forEach { it.hp = 0 }
        while (b.outcome == BattleOutcome.ONGOING && b.current != null) b.step(BattleAction.Defend)
        val after = GameEngine.applyBattleResult(hero(10), b.result(), 1_000).state
        assertTrue(after.parts.values.sum() > 0)
        assertTrue(after.armorParts.values.sum() > 0)
    }

    @Test
    fun cursedWeaponsHurtUntilLiftedThenKeepTheirPower() {
        val w = Armory.cursedWeapon(30, Random(5)).copy(curse = Curse.LIFE_DRAIN)
        assertEquals(GearRarity.LEGENDARY, w.rarity)
        assertTrue(w.cursed)
        assertTrue(w.name.startsWith("Cursed Life Drain"))
        // In battle: weaker hits, a drain on the wielder, and life drain on hit.
        val h = Hero(level = 30)
        val c = Combatant.fromHero(h, w)
        assertTrue(c.weaponCursed)
        assertTrue(c.curseDrainPct > 0)
        val foe = Monster.create(Bestiary.get("goopling"), 30, Random(6))
        val b = Battle(h, emptyList(), foe, EncounterType.WILD, emptyMap(), false, rng = Random(7), gear = w)
        var turns = 0
        while (b.outcome == BattleOutcome.ONGOING && turns++ < 20) {
            // Hurt (so life drain has something to heal) but never down.
            b.current?.let { it.hp = it.maxHp / 2 }
            b.enemy.hp = b.enemy.maxHp
            b.step(BattleAction.Attack)
        }
        assertTrue(b.log.any { "drained by a curse" in it })
        assertTrue(b.log.any { "drinks life" in it })
        assertTrue(b.allies.first().weaponUses > 0)

        // Every hit lifts 2%; at 0 the curse is gone and new effects arrive.
        var s = hero(30).copy(equippedGear = w)
        val msg = StringBuilder()
        s = GameEngine.wearCurses(s, 10, 0, msg)
        assertEquals(80, s.equippedGear!!.cursePct)
        s = GameEngine.wearCurses(s, 40, 0, msg)
        val lifted = s.equippedGear!!
        assertFalse(lifted.cursed)
        assertEquals(Curse.LIFE_DRAIN, lifted.curse)
        assertTrue(lifted.effects.size > w.effects.size)
        assertTrue("lifted" in msg.toString())
        assertFalse(Combatant.fromHero(h, lifted).weaponCursed)
        assertEquals(0, Combatant.fromHero(h, lifted).curseDrainPct)
    }

    @Test
    fun frostbiteHurtsUnlessADroughtIsOn() {
        // A weak foe, so its flinching Bites can't knock the hero out before the frost lands.
        val foe = Monster.create(Bestiary.get("goopling"), 3, Random(8))
        val frost = Armory.cursedWeapon(10, Random(9)).copy(curse = Curse.FROST)
        val b = Battle(Hero(level = 10), emptyList(), foe, EncounterType.WILD, emptyMap(), false, rng = Random(10), gear = frost)
        var turns = 0
        while (b.outcome == BattleOutcome.ONGOING && turns++ < 30 && b.log.none { "hurt by frostbite" in it }) {
            b.allies.forEach { it.hp = it.maxHp }
            b.enemy.hp = b.enemy.maxHp
            b.step(BattleAction.Attack)
        }
        assertTrue(b.log.any { "frostbite" in it }, b.log.takeLast(12).joinToString("\n"))
    }

    @Test
    fun weekendGuardianPaysSpellBooksAndSometimesCursedGear() {
        val s = hero(25)
        val results = (1..400).map { GameEngine.weekendReward(s, Random(it)) }
        val books = results.count { u -> u.rewards.any { it is Reward.SpellBook } }.toDouble() / results.size
        val cursed = results.count { u -> u.state.gear.any { it.cursed } || u.state.armor.any { it.cursed } }.toDouble() / results.size
        assertTrue(books in 0.65..0.85, "books $books")
        assertTrue(cursed in 0.3..0.5, "cursed $cursed")
        assertTrue(results.all { it.state.gold > s.gold })
        // Cursed weapons are the usual cursed prize.
        assertTrue(results.count { u -> u.state.gear.any { it.cursed } } > results.count { u -> u.state.armor.any { it.cursed } })
    }

    @Test
    fun evolvingTakesTheButtonAndCandy() {
        val kit = Monster.create(Bestiary.get("emberkit"), 13, Random(11)).copy(level = 14)
        var s = hero(14).copy(monsters = listOf(kit), party = listOf(kit.uid))
        val family = kit.species.family
        // Not enough candy.
        assertEquals(s, GameEngine.evolve(s.copy(candy = mapOf(family to 9)), kit.uid).state.copy(candy = s.candy))
        s = s.copy(candy = mapOf(family to GameEngine.evolveCost(kit) + 2))
        val u = GameEngine.evolve(s, kit.uid)
        assertEquals("blazecat", u.state.monsters.single().speciesId)
        assertEquals(2, u.state.candy[family])
        assertTrue("evolved" in u.message)
        // Too low a level can't evolve.
        val young = Monster.create(Bestiary.get("emberkit"), 5, Random(12))
        val s2 = hero(5).copy(monsters = listOf(young), candy = mapOf(family to 999))
        assertEquals("emberkit", GameEngine.evolve(s2, young.uid).state.monsters.single().speciesId)
        // Later stages cost more.
        assertTrue(GameEngine.EVOLVE_CANDY.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun beatingMonstersGivesCandy() {
        val foe = Monster.create(Bestiary.get("zapmouse"), 5, Random(13))
        val pack = listOf(Monster.create(Bestiary.get("zapmouse"), 5, Random(14)))
        val s = hero(10)
        val b = GameEngine.newBattle(s, foe, EncounterType.WILD, rng = Random(15), pack = pack)
        b.enemies.forEach { it.hp = 0 }
        while (b.outcome == BattleOutcome.ONGOING && b.current != null) b.step(BattleAction.Defend)
        val after = GameEngine.applyBattleResult(s, b.result(), 1_000).state
        assertEquals(2 * GameEngine.DEFEAT_CANDY, after.candy[foe.species.family])
    }

    @Test
    fun everyArmorSlotHasAtLeastTwoKinds() {
        ArmorSlot.entries.forEach { slot ->
            assertTrue(ArmorType.entries.count { it.slot == slot } >= 2, slot.label)
        }
        // The new kinds drop and can be forged like the rest (the Furcloak only comes from champions).
        val r = Random(9)
        val seen = (0 until 400).map { Armory.rollArmor(10, r).type }.toSet()
        assertTrue(ArmorType.common.all { it in seen }, "${ArmorType.common - seen}")
        var s = hero(10).copy(armorParts = mapOf(ArmorType.SANDALS to PARTS_TO_FORGE))
        s = GameEngine.forgeArmor(s, ArmorType.SANDALS, Random(2)).state
        assertEquals(ArmorType.SANDALS, s.armor.single().type)
        assertEquals(ArmorSlot.FEET, s.armor.single().type.slot)
    }
}
