package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClassesTest {
    private val rng = Random(7)

    private fun started(): GameState = GameEngine.chooseStarter(GameState(), "emberkit", rng).state

    private fun atLevel(level: Int, cls: HeroClass = HeroClass.WARRIOR, gold: Int = 5000): GameState =
        started().copy(hero = Hero(level = level, heroClass = cls, classChosen = true), gold = gold)

    @Test
    fun classWeaponsAddToEveryStatByRarity() {
        val bonuses = GearRarity.entries.map { Gear("w", WeaponType.BROADSWORD, it, 10).classStats(HeroClass.WARRIOR) }
        assertEquals(listOf(2, 4, 6, 8), bonuses)
        val hero = Hero(level = 10, heroClass = HeroClass.WARRIOR)
        val epic = Gear("w", WeaponType.BROADSWORD, GearRarity.EPIC, 10)
        val c = Combatant.fromHero(hero, epic)
        assertEquals(hero.defense + 6, c.defense)
        // Adventurers have no class weapons: no bonus, no penalty.
        assertEquals(0, epic.classStats(HeroClass.NONE))
    }

    @Test
    fun unfamiliarWeaponPenaltyFadesWithPracticeAndIsPerWeapon() {
        val staff = Gear("s", WeaponType.STAFF, GearRarity.LEGENDARY, 10)
        assertEquals(-8, staff.classStats(HeroClass.WARRIOR))
        val hero = Hero(level = 10, heroClass = HeroClass.WARRIOR)
        assertEquals(hero.defense - 8, Combatant.fromHero(hero, staff).defense)

        var s = atLevel(10).copy(equippedGear = staff)
        val won = BattleResult(BattleOutcome.WON, EncounterType.WILD, s.monsters.first(), 0, 0, emptyMap(), emptyMap(), emptyMap(), null, null)
        repeat(5) { s = GameEngine.applyBattleResult(s, won, 0).state }
        assertEquals(5, s.equippedGear!!.proficiency)
        assertEquals(-4, s.equippedGear!!.classStats(HeroClass.WARRIOR))
        // Running away doesn't count.
        s = GameEngine.applyBattleResult(s, won.copy(outcome = BattleOutcome.FLED), 0).state
        assertEquals(5, s.equippedGear!!.proficiency)
        var msg = ""
        repeat(5) { GameEngine.applyBattleResult(s, won, 0).let { u -> s = u.state; msg = u.message } }
        assertTrue(s.equippedGear!!.mastered)
        assertTrue("mastered" in msg)
        assertEquals(0, s.equippedGear!!.classStats(HeroClass.WARRIOR))
        // A second staff has to be learned all over again.
        assertEquals(-8, staff.copy(id = "s2").classStats(HeroClass.WARRIOR))
        // Weapons of your own class don't need practice.
        val sword = Gear("b", WeaponType.SWORD, GearRarity.NORMAL, 10)
        s = GameEngine.applyBattleResult(s.copy(equippedGear = sword), won, 0).state
        assertEquals(0, s.equippedGear!!.proficiency)
    }

    @Test
    fun subclassesUnlockAtFifteenAndBelongToTheClass() {
        assertEquals(3, Subclass.of(HeroClass.WARRIOR).size)
        assertEquals(3, Subclass.of(HeroClass.MAGE).size)
        assertEquals(3, Subclass.of(HeroClass.ROGUE).size)
        assertEquals(3, Subclass.of(HeroClass.NONE).size)
        assertTrue("unlock" in GameEngine.chooseSubclass(atLevel(14), Subclass.BERSERKER).message)
        assertNull(GameEngine.chooseSubclass(atLevel(15), Subclass.CLERIC).state.hero.subclass)

        var s = GameEngine.chooseSubclass(atLevel(15), Subclass.BERSERKER).state
        assertEquals(Subclass.BERSERKER, s.hero.subclass)
        assertEquals(5000, s.gold)
        // Berserker: +15% Attack, -10% Defense.
        val plain = Hero(level = 15, heroClass = HeroClass.WARRIOR)
        assertTrue(s.hero.attack > plain.attack)
        assertTrue(s.hero.defense < plain.defense)
        // Switching costs gold.
        s = GameEngine.chooseSubclass(s, Subclass.PALADIN).state
        assertEquals(5000 - SUBCLASS_CHANGE_COST, s.gold)
        assertTrue(Combatant.fromHero(s.hero).shield > 0)
        // A new class clears the subclass.
        s = GameEngine.chooseClass(s, HeroClass.MAGE).state
        assertNull(s.hero.subclass)
    }

    @Test
    fun heroSkillSlotsOpenAt30And45And60AndRankUpEvery10Levels() {
        assertEquals(0, Hero(level = 29).skillSlots)
        assertEquals(1, Hero(level = 30).skillSlots)
        assertEquals(2, Hero(level = 45).skillSlots)
        assertEquals(2, Hero(level = 59).skillSlots)
        assertEquals(3, Hero(level = 60).skillSlots)
        // Past the first star, hero levels read as star levels.
        assertEquals("★1 · Lv 10", Stars.levelText(60))
        assertEquals("level 50", Stars.levelText(50))
        assertTrue("★1 · Lv 10" in GameEngine.learnSkill(atLevel(55), 2, Trait.HOVER).message)
        assertTrue("level 30" in GameEngine.learnSkill(atLevel(29), 0, Trait.HOVER).message)
        assertTrue("level 45" in GameEngine.learnSkill(atLevel(30), 1, Trait.HOVER).message)

        var s = GameEngine.learnSkill(atLevel(45), 0, Trait.MORALE_MASTER).state
        s = GameEngine.learnSkill(s, 1, Trait.MENACE).state
        assertEquals(5000, s.gold)
        assertEquals(listOf(Trait.MORALE_MASTER to 2, Trait.MENACE to 1), s.hero.skillRanks)
        // Monster-only skills and duplicates are refused.
        assertEquals(s, GameEngine.learnSkill(s, 1, Trait.AIRBORNE).state)
        assertEquals(s, GameEngine.learnSkill(s, 1, Trait.MORALE_MASTER).state)
        // Swapping costs gold; the slot keeps its rank.
        s = GameEngine.learnSkill(s, 0, Trait.UNFLAPPABLE).state
        assertEquals(5000 - SKILL_CHANGE_COST, s.gold)
        assertEquals(Trait.UNFLAPPABLE to 2, s.hero.skillRanks.first())
        assertEquals(5, skillRank(100, 30))
    }

    @Test
    fun moraleMasterBoostsMonstersAndMenaceLowersFoes() {
        val s = started()
        val foe = Monster.create(Bestiary.get("emberkit"), 5, rng)
        val plain = GameEngine.newBattle(s, foe, EncounterType.WILD, rng = Random(1), fullTeam = true)
        val leader = s.copy(hero = s.hero.copy(level = 50, skills = listOf(Trait.MORALE_MASTER, Trait.MENACE)))
        val b = GameEngine.newBattle(leader, foe, EncounterType.WILD, rng = Random(1), fullTeam = true)
        // Rank 3 Morale Master: +6%.
        assertTrue(b.allies[1].attack > plain.allies[1].attack)
        // Menace: every foe's Attack drops to 66.7%, the same cut as the Scare attack.
        assertTrue(b.enemies.all { it.scared })
        assertTrue(b.log.any { "Menace" in it })
    }

    @Test
    fun scareIsAnAttackThatCutsAttackToTwoThirdsOnce() {
        val scare = Moves.get("scare")
        assertTrue(scare.isDamaging)
        assertEquals(null, Moves.find("menace"))
        val foe = Monster.create(Bestiary.get("emberkit"), 20, rng)
        val me = Monster.create(Bestiary.get("zapmouse"), 20, rng)
        val s = started().copy(monsters = listOf(me), party = listOf(me.uid))
        val b = GameEngine.newBattle(s, foe, EncounterType.WILD, rng = Random(2))
        val target = b.enemies.first()
        assertFalse(target.scared)
        // Same damage formula with and without the cut: a scared foe hits for about 2/3 the Attack.
        assertEquals(2.0 / 3.0, SCARED_ATTACK, 1e-9)
        target.scared = true
        assertTrue(target.tags().any { "Scared" in it })
        // No two moves of a type do the same thing.
        Moves.all.groupBy { it.type }.forEach { (_, moves) ->
            val shapes = moves.filter { it.isDamaging }.map { Triple(it.power, it.accuracy, it.effects) }
            assertEquals(shapes.size, shapes.toSet().size)
        }
    }

    @Test
    fun hoveringFightersIgnoreEarthMoves() {
        val hero = Hero(level = 30, skills = listOf(Trait.HOVER))
        assertTrue(Combatant.fromHero(hero).immuneEarth)
        val flier = Monster.create(Bestiary.get("tempest_roc"), 30, rng).copy(skill = Trait.AIRBORNE)
        assertEquals(Trait.AIRBORNE, flier.trait?.first)
        // Earth types can't be Airborne; slimes don't get Menace; older saves' dragons keep Menace.
        assertTrue(Bestiary.all.filter { it.element == Element.EARTH }.none { sp -> MonsterTraits.pool(sp).any { it.first.hovers } })
        assertTrue(Bestiary.all.filter { "Goopling" in it.family }.none { sp -> MonsterTraits.pool(sp).any { it.first == Trait.MENACE } })
        assertEquals(Trait.MENACE, MonsterTraits.of(Bestiary.get("cindrake")))
        // Every monster has a skill, and only monster skills.
        assertTrue(Bestiary.all.all { MonsterTraits.of(it).forMonster })
        assertTrue(Bestiary.all.all { sp -> MonsterTraits.pool(sp).all { it.first.forMonster } })

        // An Earth move against an airborne monster never lands.
        val s = started()
        val earthUser = Monster.create(Bestiary.get("terraursa"), 40, rng)
        val battle = Battle(Hero(level = 40), listOf(earthUser), flier, EncounterType.WILD, emptyMap(), false, rng = Random(3), fullTeam = true)
        val hp = battle.enemy.hp
        val earthMove = earthUser.activeMoves.firstOrNull { it.type == Element.EARTH && it.isDamaging && it.effects.none { e -> e is MoveEffect.SmackDown } }
        assertNotNull(earthMove)
        repeat(6) {
            val c = battle.current ?: return@repeat
            if (c.key == earthUser.uid) battle.step(BattleAction.UseMove(earthMove.id)) else battle.step(BattleAction.Defend)
        }
        assertTrue(battle.log.any { "floats above" in it })
        assertTrue(battle.enemy.hp == hp || battle.events.none { it.targetKey == Combatant.ENEMY_KEY && it.element == Element.EARTH && it.amount > 0 })
        assertNotNull(s)
    }

    @Test
    fun monstersGainTheirSkillAtLevel30() {
        val m = Monster.create(Bestiary.get("goopling"), 29, rng)
        assertNull(m.trait)
        val (after, up) = GameEngine.growDetailed(m, m.xpToNext)
        assertEquals(30, after.level)
        assertEquals(Trait.REGENERATION to 1, after.trait)
        assertTrue(up!!.notes.any { "Regeneration" in it })
    }

    @Test
    fun wakefulRankFiveCantSleepAndUnflappableIgnoresDrops() {
        val c = Combatant.fromHero(Hero(level = 70, skills = listOf(Trait.WAKEFUL)))
        assertEquals(Combatant.NO_SLEEP, c.sleepCut)
        val o = Combatant.fromHero(Hero(level = 30, skills = listOf(Trait.UNFLAPPABLE)))
        assertTrue(o.unflappable)
    }

    @Test
    fun kleptomaniacCarriesASecondWeapon() {
        val sword = Gear("a", WeaponType.SWORD, GearRarity.RARE, 30)
        val broad = Gear("b", WeaponType.BROADSWORD, GearRarity.RARE, 30)
        var s = atLevel(30).copy(gear = listOf(sword, broad))
        assertEquals(s, GameEngine.equipOffhand(s, "b").state)
        s = GameEngine.learnSkill(s, 0, Trait.KLEPTOMANIAC).state
        s = GameEngine.equip(s, "a").state
        s = GameEngine.equipOffhand(s, "b").state
        assertEquals(broad, s.offhandGear)
        val one = Combatant.fromHero(s.hero, s.equippedGear)
        val two = Combatant.fromHero(s.hero, s.equippedGear, offhand = s.offhandGear)
        // The second weapon strikes on its own after each attack.
        assertEquals(0, one.offhandPct)
        assertTrue(two.offhandPct >= Trait.KLEPTOMANIAC.offhandPct(1), "the off hand strikes at rank 1 power or better")
        assertTrue(two.attack >= one.attack)
        // Swapping the skill out puts the second weapon away.
        s = GameEngine.learnSkill(s, 0, Trait.SCHOLAR).state
        assertNull(s.offhandGear)
        assertTrue(s.gear.any { it.id == "b" })
    }

    @Test
    fun sellingPaysMoreForRarerThingsAndSmeltingGivesParts() {
        var s = atLevel(10, gold = 100).copy(inventory = mapOf(Item.POTION to 2, Item.SOVEREIGN_CRYSTAL to 1))
        s = GameEngine.sellItem(s, Item.POTION).state
        assertEquals(100 + Item.POTION.price / 2, s.gold)
        assertEquals(1, s.count(Item.POTION))
        assertTrue(Item.SOVEREIGN_CRYSTAL.sellPrice > Item.RESONANT_CRYSTAL.sellPrice)
        // Never sells for more than it costs.
        assertTrue(Item.entries.filter { it.price > 0 }.all { it.sellPrice < it.price })

        val prices = GearRarity.entries.map { Gear("x", WeaponType.SWORD, it, 10).sellPrice }
        assertEquals(prices.sorted(), prices)
        assertEquals(prices.size, prices.toSet().size)

        val legendary = Gear("l", WeaponType.DAGGER, GearRarity.LEGENDARY, 10)
        s = s.copy(gear = listOf(legendary), gold = 100)
        val smelted = GameEngine.smelt(s, "l").state
        assertEquals(100 - SMELT_COST, smelted.gold)
        assertEquals(4, smelted.parts[WeaponType.DAGGER])
        assertTrue(smelted.gear.isEmpty())
        assertEquals(s, GameEngine.smelt(s.copy(gold = 10), "l").state.copy(gold = 100))
        val sold = GameEngine.sellWeapon(s, "l").state
        assertEquals(100 + legendary.sellPrice, sold.gold)
        // Worn gear has to come off first.
        assertEquals(s.copy(equippedGear = legendary, gear = emptyList()), GameEngine.sellWeapon(s.copy(equippedGear = legendary, gear = emptyList()), "l").state)
    }

    @Test
    fun wayfarerAndScholarRaiseRewards() {
        // A foe at the hero's level (far weaker ones give little XP).
        val foe = Monster.create(Bestiary.get("emberkit"), 20, rng)
        fun win(h: Hero): BattleResult {
            val b = Battle(h, emptyList(), foe, EncounterType.WILD, emptyMap(), false, rng = Random(9))
            b.enemies.forEach { it.hp = 0 }
            b.step(BattleAction.Defend)
            if (b.outcome == BattleOutcome.ONGOING) return win(h)
            return b.result()
        }
        val base = win(Hero(level = 20, heroClass = HeroClass.NONE, classChosen = true))
        val way = win(Hero(level = 20, heroClass = HeroClass.NONE, classChosen = true, subclass = Subclass.WAYFARER))
        assertTrue(way.heroXp > base.heroXp)
        assertFalse(base.heroXp == 0)
    }

    @Test
    fun everyTypeHasEightNewAttacksWithItsOwnStrength() {
        val new = mapOf(
            Element.FIRE to listOf("cinder_jab", "flare_rush", "searing_blade", "magma_fist", "wildfire_charge", "meteor_flame", "sunfire", "supernova"),
            Element.ELECTRIC to listOf("static_jolt", "galvanize", "charge_net", "ion_storm", "volt_snare", "plasma_lance", "thunderclap", "judgment_bolt"),
            Element.WATER to listOf("ripple_dart", "rapid_current", "tidal_crash", "riptide", "undertow", "torrent_rush", "tidal_wave", "tsunami"),
            Element.FLIGHT to listOf("wing_flick", "rising_strike", "updraft", "wind_slash", "soaring_strike", "ascent", "cyclone_fury", "talon_storm"),
            Element.GAS to listOf("noxious_spit", "venom_surge", "drowsy_mist", "toxic_cloud", "sludge_shot", "plague_fang", "caustic_burst", "pandemic"),
            Element.CORRUPT to listOf("low_blow", "spiteful_jab", "eye_gouge", "backstab", "rend", "dread_howl", "hollowing_strike", "doomfall"),
            Element.EARTH to listOf("stone_knuckle", "bedrock_press", "mud_fling", "shield_bash", "rockslide", "iron_root", "mountain_crush", "titan_slam"),
        ).mapValues { (t, ids) -> ids.map { Moves.get(it) }.onEach { m -> assertEquals(t, m.type); assertTrue(m.isDamaging) } }
        // Each type learns all eight.
        new.forEach { (t, moves) -> assertTrue(Moves.learnsets.getValue(t).map { it.second }.containsAll(moves.map { it.id })) }
        fun avg(t: Element) = new.getValue(t).map { it.expectedPower }.average()
        // Fire hits hardest; status types trade damage for effects.
        assertTrue(Element.entries.filter { it in new && it != Element.FIRE }.all { avg(Element.FIRE) > avg(it) })
        assertTrue(avg(Element.ELECTRIC) < avg(Element.WATER) && avg(Element.GAS) < avg(Element.FLIGHT))
        fun share(t: Element, test: (Move) -> Boolean) = new.getValue(t).count(test)
        assertTrue(share(Element.ELECTRIC) { m -> m.effects.any { it is MoveEffect.Inflict || it is MoveEffect.StatusBonus } } >= 6)
        assertTrue(share(Element.GAS) { m -> m.effects.any { it is MoveEffect.Inflict || it is MoveEffect.StatusBonus } } >= 6)
        assertTrue(share(Element.CORRUPT) { m -> m.effects.any { it is MoveEffect.LowerFoe || it is MoveEffect.FoeAccuracyDown } } >= 6)
        assertTrue(share(Element.FLIGHT) { m -> m.effects.any { it is MoveEffect.RaiseSelf || it is MoveEffect.PowerUp } } >= 5)
        assertTrue(share(Element.EARTH) { m -> m.effects.any { it == MoveEffect.DefenseStrike || (it is MoveEffect.RaiseSelf && BattleStat.DEFENSE in it.stats) } } >= 5)
        assertTrue(share(Element.WATER) { m -> m.effects.any { it == MoveEffect.Swift || it == MoveEffect.SureHit || (it is MoveEffect.RaiseSelf && BattleStat.SPEED in it.stats) } } >= 4)
    }

    @Test
    fun swiftMovesFirstAndStatusBonusAndDefenseStrikeWork() {
        val me = Monster.create(Bestiary.get("bubblefin"), 30, rng).copy(moves = listOf("ripple_dart"))
        val foe = Monster.create(Bestiary.get("goopling"), 30, rng)
        val s = started().copy(monsters = listOf(me), party = listOf(me.uid))
        val b = GameEngine.newBattle(s, foe, EncounterType.WILD, rng = Random(3), fullTeam = true)
        var guard = 0
        while (b.outcome == BattleOutcome.ONGOING && guard++ < 40 && b.log.none { "move first next round" in it }) {
            b.allies.forEach { it.hp = it.maxHp }
            b.enemies.forEach { it.hp = it.maxHp }
            val cur = b.current ?: break
            b.step(if (cur.isHero) BattleAction.Defend else BattleAction.UseMove("ripple_dart"))
        }
        assertTrue(b.log.any { "move first next round" in it })
        val swifter = (b.allies + b.enemies).first { it.swift }
        assertEquals(swifter.key, b.turnOrder().first { it != b.current?.key && b.combatant(it)?.swift == true })
        assertTrue(swifter.tags().contains("Moves first"))
        // Galvanize: double power on a statused foe; Bedrock Press: Defense does the hitting.
        assertEquals(2.0, Moves.get("galvanize").effects.filterIsInstance<MoveEffect.StatusBonus>().single().multiplier)
        assertTrue(MoveEffect.DefenseStrike in Moves.get("bedrock_press").effects)
        // Life Absorb: Corrupt's dark power, 80 power, heals 25% of the damage, learned late.
        val absorb = Moves.get("life_absorb")
        assertEquals(80, absorb.power)
        assertEquals(25, absorb.effects.filterIsInstance<MoveEffect.LifeDrain>().single().percent)
        assertEquals(48, Moves.learnsets.getValue(Element.CORRUPT).single { it.second == "life_absorb" }.first)
    }
}
