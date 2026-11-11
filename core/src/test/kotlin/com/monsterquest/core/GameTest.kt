package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameTest {
    private val rng = Random(42)

    private fun started(): GameState = GameEngine.chooseStarter(GameState(), "emberkit", rng).state

    @Test
    fun completingTaskGivesXpGoldAndToken() {
        var s = GameEngine.addTask(started(), "Do laundry", Difficulty.HARD, rng, nowMs = 1000).state
        val id = s.tasks.single().id
        // Quests pay once they've been around for a while.
        s = GameEngine.completeTask(s, id, 1000 + QuestRewards.MIN_AGE_MS).state
        assertEquals(50 + Difficulty.HARD.gold, s.gold)
        assertEquals(2, s.huntTokens)
        // Levelling is slower now: a Hard quest isn't a whole level at the start.
        assertTrue(Difficulty.HARD.xp < heroXpForNext(1))
        assertEquals(Difficulty.HARD.xp, s.hero.xp)
        assertEquals(1, s.hero.level)
        // Can't double-claim.
        assertEquals(s, GameEngine.completeTask(s, id, 2000).state)
    }

    @Test
    fun upgradeEarnedEveryFiveLevels() {
        var s = started()
        while (s.hero.level < 10) s = GameEngine.addHeroXp(s, 25).state
        assertEquals(2, s.pendingUpgrades)
        s = GameEngine.applyUpgrade(s).state
        assertEquals(STARTING_BAG_CAPACITY + BAG_UPGRADE_AMOUNT, s.bagCapacity)
        assertEquals(STARTING_MONSTER_CAPACITY + STORAGE_UPGRADE_AMOUNT, s.monsterCapacity)
        assertEquals(20, STARTING_MONSTER_CAPACITY)
        // Buying in the shop: +5 each, expensive, and pricier every time.
        assertEquals(1_500, GameEngine.expansionCost(s, bag = true))
        assertEquals(2_000, GameEngine.expansionCost(s, bag = false))
        val rich = s.copy(gold = 4_000)
        val bought = GameEngine.buyStorage(GameEngine.buyStorage(rich, bag = true).state, bag = false).state
        assertEquals(s.bagCapacity + PAID_UPGRADE_AMOUNT, bought.bagCapacity)
        assertEquals(s.monsterCapacity + PAID_UPGRADE_AMOUNT, bought.monsterCapacity)
        assertEquals(500, bought.gold)
        assertEquals(2_250, GameEngine.expansionCost(bought, bag = true))
        assertEquals(3_000, GameEngine.expansionCost(bought, bag = false))
        assertEquals(bought, GameEngine.buyStorage(bought, bag = true).state)
        // Older saves with 6 slots move up to the new 20.
        val old = GameState.fromJson(GameState(version = 4, monsterCapacity = 9).toJson())
        assertEquals(23, old.monsterCapacity)
        assertEquals(5, MAX_PARTY_SIZE)
        assertEquals(1, s.pendingUpgrades)
    }

    @Test
    fun personalitiesAndIvsMakeSameSpeciesDiffer() {
        val wolf = Bestiary.get("shadow_pup")
        val wolves = (1..20).map { Monster.create(wolf, 20, rng) }
        assertTrue(wolves.map { it.stats }.toSet().size > 15)
        val fierce = Monster(uid = "a", speciesId = "shadow_pup", personality = Personality.FEROCIOUS, ivs = StatBlock(), level = 20)
        val stalwart = fierce.copy(personality = Personality.STALWART)
        assertTrue(fierce.stats.attack > stalwart.stats.attack)
        assertTrue(stalwart.stats.defense > fierce.stats.defense)
        assertEquals("High Attack, low Defense", Personality.FEROCIOUS.description)
    }

    @Test
    fun monstersLevelUp() {
        val m = Monster.create(Bestiary.get("shadow_pup"), 5, rng)
        val up = m.gainXp(monsterXpForNext(5) + monsterXpForNext(6))
        assertEquals(7, up.level)
        assertEquals("shadow_pup", up.speciesId)
        assertTrue(up.stats.hp > m.stats.hp)
    }

    @Test
    fun partyLimitEnforced() {
        var s = started()
        repeat(MAX_PARTY_SIZE + 1) { s = s.copy(monsters = s.monsters + Monster.create(Bestiary.get("zapmouse"), 3, rng)) }
        s.monsters.forEach { s = GameEngine.toggleParty(s, it.uid).state }
        assertEquals(MAX_PARTY_SIZE, s.party.size)
    }

    @Test
    fun bagCapacityLimitsLoot() {
        val s = started().copy(inventory = mapOf(Item.POTION to STARTING_BAG_CAPACITY - 1))
        val (after, lost) = GameEngine.addItems(s, mapOf(Item.MANA_DRAUGHT to 3))
        assertEquals(STARTING_BAG_CAPACITY, after.bagCount)
        assertEquals(2, lost)
        assertEquals("Bag is full (20/20).", GameEngine.buy(after.copy(gold = 999), Item.POTION).message)
    }

    @Test
    fun captureOnlyWhenWeak() {
        assertEquals(0.0, captureChance(0.8, Rarity.COMMON, 0, Item.BINDING_CRYSTAL))
        val low = captureChance(0.1, Rarity.COMMON, 0, Item.BINDING_CRYSTAL)
        val mid = captureChance(0.45, Rarity.COMMON, 0, Item.BINDING_CRYSTAL)
        assertTrue(low > mid)
        assertTrue(captureChance(0.1, Rarity.COMMON, 0, Item.RESONANT_CRYSTAL) > low)
        assertTrue(captureChance(0.1, Rarity.LEGENDARY, 0, Item.BINDING_CRYSTAL) < low)
    }

    private val utc = java.time.ZoneId.of("UTC")

    /** Epoch ms for a date/time in UTC. 2026-10-26 is a Monday. */
    private fun at(day: Int, hour: Int = 12) =
        java.time.ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, utc).toInstant().toEpochMilli()

    /** A state with [done] of [total] quests completed on Monday 2026-10-26. */
    private fun withQuests(total: Int, done: Int): GameState {
        val tasks = (0 until total).map { i ->
            QuestTask("q$i", "Quest $i", createdAtMs = at(26, 9), completedAtMs = if (i < done) at(26, 10) else null)
        }
        return started().copy(tasks = tasks)
    }

    @Test
    fun bossMustBeDefeatedBeforeTaming() {
        val s = withQuests(total = 6, done = 5)
        val shrine = World.shrineFromPlace("1", "Museum", 0.0, 0.0, ShrineTier.GOLD)
        val saturday = at(31)
        val (boss, mode) = GameEngine.bossEncounter(s, shrine, saturday, zone = utc)
        assertNotNull(boss)
        assertEquals("challenge", mode)
        val battle = GameEngine.newBattle(s, boss, EncounterType.BOSS_CHALLENGE, shrine.id, rng)
        assertTrue(battle.captureLocked)
        assertNotNull(battle.captureBlockedReason())
        battle.enemy.hp = 1
        // Even at 1 HP, capture is refused during the first challenge.
        battle.current?.let { battle.step(BattleAction.Capture(Item.BINDING_CRYSTAL)) }
        assertNotEquals(BattleOutcome.CAPTURED, battle.outcome)
    }

    @Test
    fun goldShrinesOpenOnWeekendsWhenWeeklyGoalMet() {
        val shrine = World.shrineFromPlace("1", "Stadium", 0.0, 0.0, ShrineTier.GOLD)
        val good = withQuests(total = 7, done = 5) // 71%
        assertTrue(Schedule.access(good, shrine, at(31), utc).open) // Saturday
        assertTrue(Schedule.access(good, shrine, at(31) + 86_400_000, utc).open) // Sunday
        assertTrue(!Schedule.access(good, shrine, at(29), utc).open) // Thursday: wrong day
        val lowPercent = withQuests(total = 10, done = 6) // 60%
        assertTrue(!Schedule.access(lowPercent, shrine, at(31), utc).open)
        val tooFew = withQuests(total = 4, done = 4) // 100% but under the minimum of 5
        assertTrue(!Schedule.access(tooFew, shrine, at(31), utc).open)
        // Quests completed on the weekend don't count toward the Monday–Friday goal.
        val weekendWork = good.copy(tasks = good.tasks + QuestTask("w", "Weekend chore", createdAtMs = at(31, 9), completedAtMs = at(31, 10)))
        assertEquals(5, Schedule.workweek(weekendWork, at(31), utc).done)
    }

    @Test
    fun silverShrinesOpenTuesdayAndThursday() {
        val shrine = World.shrineFromPlace("2", "Cinema", 0.0, 0.0, ShrineTier.SILVER)
        val ok = withQuests(total = 4, done = 2) // 50%
        assertTrue(Schedule.access(ok, shrine, at(27), utc).open) // Tuesday
        assertTrue(Schedule.access(ok, shrine, at(29), utc).open) // Thursday
        assertTrue(!Schedule.access(ok, shrine, at(28), utc).open) // Wednesday
        assertTrue(!Schedule.access(withQuests(total = 4, done = 1), shrine, at(27), utc).open)

        val (guardian, mode) = GameEngine.bossEncounter(ok, shrine, at(27), rng, utc)
        assertEquals("silver", mode)
        assertNotNull(guardian)
        assertTrue(GameEngine.perfectStatCount(guardian) >= 3)
        assertTrue(guardian.species.rarity != Rarity.LEGENDARY)
        // Harder to tame than the same monster in the wild.
        val wild = GameEngine.newBattle(ok, guardian, EncounterType.WILD, rng = rng).apply { enemy.hp = enemy.maxHp / 5 }
        val silver = GameEngine.newBattle(ok, guardian, EncounterType.SILVER_BOSS, shrine.id, rng).apply { enemy.hp = enemy.maxHp / 5 }
        assertTrue(silver.captureOdds(Item.RESONANT_CRYSTAL) < wild.captureOdds(Item.RESONANT_CRYSTAL))
    }

    @Test
    fun battleItemLimit() {
        val s = started().copy(inventory = mapOf(Item.POTION to 10))
        val b = GameEngine.newBattle(s, Monster.create(Bestiary.get("zapmouse"), 1, rng), EncounterType.WILD, rng = rng)
        var uses = 0
        while (b.outcome == BattleOutcome.ONGOING && uses < 10) {
            b.step(BattleAction.UseItem(Item.POTION, Combatant.HERO_KEY)); uses++
        }
        assertTrue(b.itemsUsedCount <= MAX_ITEMS_PER_BATTLE)
    }

    @Test
    fun tamingDoesNotCountTowardTheItemLimit() {
        val s = started().copy(hero = Hero(level = 60), inventory = mapOf(Item.POTION to 10, Item.BINDING_CRYSTAL to 10, Item.SWEET_BERRY to 2))
        val b = GameEngine.newBattle(s, Monster.create(Bestiary.get("zapmouse"), 5, rng), EncounterType.WILD, rng = rng)
        fun heroTurn() { var g = 0; while (b.current?.isHero != true && g++ < 20 && b.outcome == BattleOutcome.ONGOING) b.step(BattleAction.Defend) }
        repeat(MAX_ITEMS_PER_BATTLE) { heroTurn(); b.step(BattleAction.UseItem(Item.POTION, Combatant.HERO_KEY)) }
        assertEquals(0, b.itemsRemainingThisBattle)
        // Item limit reached, but taming still works: a berry and a cast, and neither counts.
        b.enemy.hp = 1
        heroTurn()
        assertNull(b.captureBlockedReason())
        assertTrue(b.canFeed(Item.SWEET_BERRY))
        b.step(BattleAction.UseItem(Item.SWEET_BERRY, Combatant.HERO_KEY))
        heroTurn()
        if (b.outcome == BattleOutcome.ONGOING) b.step(BattleAction.Capture(Item.BINDING_CRYSTAL))
        assertEquals(0, b.itemsRemainingThisBattle)
        // The crystal still leaves the bag.
        assertEquals(9, b.count(Item.BINDING_CRYSTAL))
    }

    @Test
    fun fullBattleWinsAndPaysOut() {
        val s = started()
        val enemy = Monster.create(Bestiary.get("zapmouse"), 1, rng)
        val b = GameEngine.newBattle(s, enemy, EncounterType.WILD, rng = rng)
        var guard = 0
        while (b.outcome == BattleOutcome.ONGOING && guard++ < 200) b.step(BattleAction.Attack)
        assertEquals(BattleOutcome.WON, b.outcome)
        val u = GameEngine.applyBattleResult(s, b.result(), 0)
        assertTrue(u.state.gold > s.gold)
        assertTrue(u.state.monsters.first().xp > 0 || u.state.monsters.first().level > 3)
    }

    @Test
    fun losingCostsGold() {
        val s = started().copy(gold = 200)
        val boss = Monster.create(Bestiary.get("thunder_titan"), 60, rng)
        val b = GameEngine.newBattle(s, boss, EncounterType.WILD, rng = rng)
        var guard = 0
        while (b.outcome == BattleOutcome.ONGOING && guard++ < 200) b.step(BattleAction.Defend)
        assertEquals(BattleOutcome.LOST, b.outcome)
        val u = GameEngine.applyBattleResult(s, b.result(), 0)
        assertEquals(180, u.state.gold)
    }

    @Test
    fun captureAddsMonster() {
        val s = started().copy(inventory = mapOf(Item.SOVEREIGN_CRYSTAL to 3))
        val enemy = Monster.create(Bestiary.get("zapmouse"), 1, rng)
        val b = GameEngine.newBattle(s, enemy, EncounterType.WILD, rng = Random(1))
        b.enemy.hp = 1
        var guard = 0
        while (b.outcome == BattleOutcome.ONGOING && guard++ < 10) b.step(BattleAction.Capture(Item.SOVEREIGN_CRYSTAL))
        if (b.outcome == BattleOutcome.CAPTURED) {
            val u = GameEngine.applyBattleResult(s, b.result(), 0)
            assertEquals(2, u.state.monsters.size)
            assertEquals(2, u.state.party.size)
        }
    }

    @Test
    fun calendarSyncKeepsCompleted() {
        var s = started()
        val ev = CalendarEvent("e1:100", "Dentist", 0, 45 * 60_000, false, "Personal")
        s = GameEngine.syncCalendar(s, listOf(ev)).state
        s = GameEngine.completeTask(s, "e1:100", 5).state
        s = GameEngine.syncCalendar(s, listOf(ev, ev.copy(key = "e2:1", title = "Gym"))).state
        assertEquals(2, s.tasks.size)
        assertEquals(1, s.tasks.count { it.completed })
    }

    @Test
    fun saveRoundTrips() {
        val s = GameEngine.addTask(started(), "x", Difficulty.EASY, rng).state
            .copy(shrines = mapOf("osm:1" to ShrineRecord(5, true)))
        assertEquals(s, GameState.fromJson(s.toJson()))
    }

    @Test
    fun releaseKeepsLastMonster() {
        val s = started()
        assertNull(GameEngine.release(s, s.monsters.single().uid).state.monsters.firstOrNull { false })
        assertEquals(1, GameEngine.release(s, s.monsters.single().uid).state.monsters.size)
    }

    @Test
    fun allMonstersWithEvolutionLines() {
        assertEquals(98, Bestiary.all.size)
        assertEquals(98, Bestiary.all.map { it.id }.toSet().size)
        // One legendary per type, and they all guard Gold shrines.
        val legendaries = Bestiary.all.filter { it.rarity == Rarity.LEGENDARY }
        assertEquals(Element.typed.toSet(), legendaries.map { it.element }.toSet())
        assertEquals(Element.typed.size, legendaries.size)
        assertTrue(legendaries.all { it in Bestiary.bosses })
        val lines = Bestiary.all.groupBy { it.family }
        assertEquals(6, lines.values.count { it.size == 4 })
        assertEquals(17, lines.values.count { it.size == 3 })
        assertEquals(7, lines.values.count { it.size == 2 })
        // Starters are #1, #4, #7 and #10, each a three-stage line.
        assertEquals(GameEngine.starters, listOf(Bestiary.all[0].id, Bestiary.all[3].id, Bestiary.all[6].id, Bestiary.all[9].id))
        GameEngine.starters.forEach { assertEquals(3, Bestiary.lineOf(Bestiary.get(it)).size) }
        // The newer lines evolve like the hounds.
        assertEquals("sludgeon", Monster.create(Bestiary.get("goopling"), 16, rng).speciesId)
        assertEquals("magma_colossus", Monster.create(Bestiary.get("pebblit"), 32, rng).speciesId)
        // Every type is represented, and every evolution target exists.
        assertEquals(Element.typed.toSet(), Bestiary.all.map { it.element }.toSet())
        Bestiary.all.mapNotNull { it.evolvesTo }.forEach { Bestiary.get(it) }
        // Final forms of four-stage lines out-stat the final hound breeds.
        val tiger = Bestiary.get("solar_tiger").base
        val fenrir = Bestiary.get("void_fenrir").base
        assertTrue(tiger.hp + tiger.attack + tiger.magic + tiger.defense + tiger.speed >
            fenrir.hp + fenrir.attack + fenrir.magic + fenrir.defense + fenrir.speed)
    }

    @Test
    fun monstersEvolveAtLevels() {
        val kit = Monster.create(Bestiary.get("emberkit"), 13, rng)
        assertEquals("emberkit", kit.speciesId)
        // Levelling past the evolution level doesn't evolve it: the player chooses to.
        val grown = kit.gainXp(monsterXpForNext(13))
        assertEquals("emberkit", grown.speciesId)
        assertEquals("blazecat", grown.canEvolveTo?.id)
        assertNull(kit.canEvolveTo)
        // Wild monsters still turn up already evolved for their level.
        val all3 = Monster.create(Bestiary.get("emberkit"), 45, rng)
        assertEquals("solar_tiger", all3.speciesId)
        val hound = Monster.create(Bestiary.get("shadow_pup"), 32, rng)
        assertEquals("void_fenrir", hound.speciesId)
    }

    @Test
    fun typesAreBalanced() {
        assertEquals(7, Element.typed.size)
        for (t in Element.typed) {
            // Every type: strong vs exactly 2, weak to exactly 2, neutral to the other 2 (and itself).
            assertEquals(2, t.beats.size, "$t beats")
            assertEquals(2, t.weakTo.size, "$t weak to")
            assertTrue(t.beats.none { it in t.weakTo })
            for (b in t.beats) {
                assertEquals(1.5, t.multiplierAgainst(b))
                assertTrue(b.multiplierAgainst(t) < 1.0)
            }
            assertEquals(2, Element.typed.count { t.multiplierAgainst(it) == 1.0 && it != t })
        }
        // Earth: grass and stone. Strong vs Water, weak to Fire.
        assertEquals(1.5, Element.EARTH.multiplierAgainst(Element.WATER))
        assertEquals(1.5, Element.FIRE.multiplierAgainst(Element.EARTH))
        // Classic pairs kept.
        assertEquals(1.5, Element.ELECTRIC.multiplierAgainst(Element.WATER))
        assertEquals(1.5, Element.WATER.multiplierAgainst(Element.FIRE))
        // Electric shocks fliers, and fliers rise above the earth.
        assertEquals(1.5, Element.ELECTRIC.multiplierAgainst(Element.FLIGHT))
        assertEquals(1.5, Element.FLIGHT.multiplierAgainst(Element.EARTH))
        assertTrue(Element.FLIGHT.multiplierAgainst(Element.ELECTRIC) < 1.0)
        assertTrue(Element.EARTH.multiplierAgainst(Element.FLIGHT) < 1.0)
        assertEquals(1.5, Element.GAS.multiplierAgainst(Element.ELECTRIC))
        // Every type has monsters, and Earth has its moves.
        assertTrue(Element.typed.all { e -> Bestiary.wild.count { it.element == e } >= 6 })
        assertTrue(Element.typed.all { Moves.learnsets.getValue(it).size >= 11 })
    }

    @Test
    fun earthMovesHealAndRecharge() {
        val treant = Monster.create(Bestiary.get("ancient_treant"), 45, Random(1)).copy(moves = listOf("mega_sap", "mulch", "leaf_storm"))
        val s = started().let { it.copy(monsters = listOf(treant), party = listOf(treant.uid)) }
        val b = GameEngine.newBattle(s, Monster.create(Bestiary.get("bubblefin"), 10, Random(2)), EncounterType.WILD, rng = Random(3), fullTeam = true)
        val me = b.allies.first { it.key == treant.uid }
        me.hp = me.maxHp / 2
        var guard = 0
        while (b.current?.key != treant.uid && guard++ < 20) b.step(BattleAction.Defend)
        val before = me.hp
        b.step(BattleAction.UseMove("mulch"))
        assertTrue(me.hp > before)
        assertTrue(b.log.any { it.contains("restores itself") })
    }

    @Test
    fun spellPagesTeachSpells() {
        var s = started()
        repeat(Spells.PAGES_TO_LEARN - 1) { s = GameEngine.grantLoot(s, GameEngine.Loot(pages = mapOf("gust_bolt" to 1))).state }
        assertTrue("gust_bolt" !in s.hero.knownSpells)
        assertEquals(Spells.PAGES_TO_LEARN - 1, s.spellPages["gust_bolt"])
        val u = GameEngine.grantLoot(s, GameEngine.Loot(pages = mapOf("gust_bolt" to 1)))
        assertTrue("gust_bolt" in u.state.hero.knownSpells)
        assertNull(u.state.spellPages["gust_bolt"])
        assertTrue(u.message.contains("learned Gust Bolt"))
    }

    @Test
    fun heroMagicIsItsOwnAndRanksUp() {
        // One bolt per type, plus heal, shield and defense buff, none of them monster moves.
        assertEquals(Element.typed.toSet(), Spells.all.filter { it.kind == SkillKind.MAGICAL }.map { it.element }.toSet())
        // (Ward's plain shield is gone: Aegis replaced it in 3.7.)
        assertEquals(SUPPORT_KINDS - SkillKind.SHIELD, Spells.all.map { it.kind }.toSet() - SkillKind.MAGICAL)
        assertTrue(Spells.all.none { sp -> Moves.all.any { it.id == sp.id } })
        // Attack spells grow with the hero (see MagicBalanceTest); healing stops growing once mastered.
        val ember = Spells.byId("ember_bolt")!!
        assertTrue(Spells.ranked(ember, 1, 1).power < Spells.ranked(ember, 1, 25).power)
        assertTrue(Spells.ranked(ember, 1, 25).power < Spells.ranked(ember, 1, 60).power)
        val mend = Spells.byId("mend")!!
        assertEquals(Spells.ranked(mend, 1, 25).power, Spells.ranked(mend, 1, 60).power)
        assertTrue(Spells.ranked(ember, 5, 25).power > Spells.ranked(ember, 1, 25).power)

        var s = started()
        assertEquals(1, s.hero.spellRank("ember_bolt"))
        // Rank 2 opens at hero level 10; then it takes pages and gold.
        assertTrue(GameEngine.upgradeSpell(s, "ember_bolt").message.contains("level 10"))
        s = s.copy(hero = s.hero.copy(level = 10))
        assertTrue(GameEngine.upgradeSpell(s, "ember_bolt").message.contains("Need"))
        s = s.copy(gold = 1_000, spellPages = mapOf("ember_bolt" to 4))
        val u = GameEngine.upgradeSpell(s, "ember_bolt")
        assertEquals(2, u.state.hero.spellRank("ember_bolt"))
        assertEquals(850, u.state.gold)
        assertEquals(1, u.state.spellPages["ember_bolt"])
        // Maxed spells stop dropping pages.
        val maxed = s.copy(hero = s.hero.copy(knownSpells = Spells.all.map { it.id }.toSet(), spellRanks = Spells.all.associate { it.id to Spells.MAX_RANK }))
        assertTrue(GameEngine.pageCandidates(maxed).isEmpty())
    }

    @Test
    fun wardAbsorbsAndFortifyRaisesDefense() {
        val s = started().let { it.copy(hero = it.hero.copy(knownSpells = it.hero.knownSpells + "fortify")) }
        val b = GameEngine.newBattle(s, Monster.create(Bestiary.get("bubblefin"), 5, Random(2)), EncounterType.WILD, rng = Random(3), fullTeam = true)
        var guard = 0
        while (b.current?.isHero != true && guard++ < 20) b.step(BattleAction.Defend)
        val pal = b.allies.first { !it.isHero }
        b.step(BattleAction.CastSpell(Spells.AEGIS, pal.key))
        // The ward goes up (and may already have soaked a hit before we look).
        assertTrue(b.log.any { it.contains("great shield") })
        assertTrue(b.events.any { it.style == StrikeStyle.WARD && it.targetKey == pal.key })
        guard = 0
        while (b.current?.isHero != true && guard++ < 20) b.step(BattleAction.Defend)
        b.step(BattleAction.CastSpell("fortify", pal.key))
        assertEquals(1, pal.stages[BattleStat.DEFENSE])
    }

    @Test
    fun wardBecomesAegisForEveryone() {
        val old = started().let {
            it.copy(
                version = 8,
                hero = it.hero.copy(knownSpells = setOf("ember_bolt", "mend", "ward"), spellRanks = mapOf("ward" to 3)),
                spellPages = mapOf("ward" to 4, "fortify" to 1),
            )
        }
        val m = old.migrated()
        assertEquals(setOf("ember_bolt", "mend", Spells.AEGIS), m.hero.knownSpells)
        assertEquals(3, m.hero.spellRank(Spells.AEGIS))
        assertEquals(mapOf(Spells.AEGIS to 4, "fortify" to 1), m.spellPages)
        assertTrue(Spells.byId("ward") == null)
        assertTrue(Spells.AEGIS in GameState().hero.knownSpells)
    }

    @Test
    fun oldSpellsBecomeHeroMagic() {
        val old = started().let {
            it.copy(
                version = 6,
                hero = it.hero.copy(knownSpells = setOf("fire_bolt", "heal", "inferno", "gale")),
                spellPages = mapOf("tidal_lance" to 3, "judgement" to 2),
            )
        }
        val m = old.migrated()
        assertEquals(setOf("ember_bolt", "mend", Spells.AEGIS, "gust_bolt"), m.hero.knownSpells)
        assertEquals(2, m.hero.spellRank("ember_bolt"))
        assertEquals(mapOf("tide_bolt" to 3, "fortify" to 2), m.spellPages)
    }

    @Test
    fun weaponsEquipAndTypeTheAttack() {
        val spear = Gear("g1", WeaponType.SWORD, GearRarity.RARE, 10, Element.ELECTRIC)
        var s = GameEngine.grantLoot(started(), GameEngine.Loot(weapons = listOf(spear))).state
        assertEquals(1, s.gear.size)
        val before = s.bagCount
        s = GameEngine.equip(s, "g1").state
        assertEquals(spear, s.equippedGear)
        assertEquals(before - 1, s.bagCount)
        val b = GameEngine.newBattle(s, Monster.create(Bestiary.get("bubblefin"), 1, rng), EncounterType.WILD, rng = rng)
        val hero = b.allies.first()
        assertEquals(Element.ELECTRIC, hero.attackElement)
        assertEquals(s.hero.attack + spear.attackBonus(s.hero.heroClass), hero.attack)
    }

    @Test
    fun weaponTypesClassesAndRarity() {
        val warrior = HeroClass.WARRIOR
        val sword = Gear("a", WeaponType.SWORD, GearRarity.NORMAL, 20)
        val broad = sword.copy(type = WeaponType.BROADSWORD)
        val dagger = sword.copy(type = WeaponType.DAGGER)
        val staff = sword.copy(type = WeaponType.STAFF)
        // Class match bonus.
        assertTrue(sword.attackBonus(warrior) > sword.attackBonus(HeroClass.MAGE))
        assertTrue(staff.magicBonus(HeroClass.MAGE) > staff.magicBonus(warrior))
        // Broadsword hits harder but slows; staff is magic-heavy.
        assertTrue(broad.attackBonus(warrior) > sword.attackBonus(warrior))
        assertTrue(staff.magicBonus(HeroClass.MAGE) > staff.attackBonus(HeroClass.MAGE))
        val hero = Hero(level = 20, heroClass = warrior)
        assertTrue(Combatant.fromHero(hero, broad).speed < Combatant.fromHero(hero, sword).speed)
        // Daggers hit twice; wands restore energy.
        assertEquals(2, Combatant.fromHero(hero, dagger).hits)
        assertTrue(Combatant.fromHero(hero, sword.copy(type = WeaponType.WAND)).energyRegen > 0)
        // Rarity raises power and adds effects.
        val legendary = sword.copy(rarity = GearRarity.LEGENDARY)
        assertTrue(legendary.power > sword.power)
        val rolledLegend = (1..400).map { Armory.roll(40, Random(it)) }.filter { it.rarity == GearRarity.LEGENDARY }
        assertTrue(rolledLegend.isNotEmpty() && rolledLegend.all { it.effects.size == 3 })
        // Higher levels: better weapons.
        val low = (1..400).count { Armory.roll(2, Random(it)).rarity >= GearRarity.EPIC }
        val high = (1..400).count { Armory.roll(40, Random(it)).rarity >= GearRarity.EPIC }
        assertTrue(high > low)
        assertTrue(Armory.roll(40, rng).power > Armory.roll(2, rng).power)
    }

    @Test
    fun itemShrineGivesLoot() {
        val s = started().copy(inventory = emptyMap())
        val shrine = World.shrineFromPlace("9", "Cafe", 0.0, 0.0, ShrineTier.ITEM)
        val u = GameEngine.visitShrine(s, shrine, 0, Random(5))
        val got = u.state.bagCount + u.state.spellPages.values.sum() + (u.state.hero.knownSpells.size - s.hero.knownSpells.size)
        assertTrue(got >= 2)
        assertTrue(GameEngine.shrineCooldownLeft(u.state, shrine, 1) > 0)
    }

    @Test
    fun focusRewardsAndPenalties() {
        val s = started().copy(gold = 500)
        val running = GameEngine.startFocus(s, 60, 0).state
        assertNotNull(running.focus)
        val win = GameEngine.resolveFocus(running, success = true, rng = rng).state
        assertNull(win.focus)
        assertEquals(500, win.gold)
        assertEquals(1, win.lootBoxes.size)
        assertEquals(60, win.stats.focusMinutes)
        val fail = GameEngine.resolveFocus(running, success = false).state
        assertEquals(450, fail.gold)
        assertEquals(0, fail.huntTokens)
        assertEquals(1, fail.stats.focusFails)
    }

    @Test
    fun oldSavesMigrate() {
        val old = started().copy(version = 1, monsters = listOf(Monster("x", "greywolf", Personality.BRAVE, StatBlock(), level = 20)))
        val m = GameState.fromJson(old.toJson()).monsters.single()
        assertEquals("dusk_hound", m.speciesId) // shadow_pup, evolved at 16
    }

    @Test
    fun lootBoxesUnlockByWalking() {
        var s = started().copy(
            lootBoxes = listOf(
                LootBox("a", BoxRarity.COMMON, 0),
                LootBox("b", BoxRarity.RARE, 0),
                LootBox("c", BoxRarity.LEGENDARY, 0),
            ),
        )
        s = LootBoxes.startUnlocking(s, "a").state
        s = LootBoxes.startUnlocking(s, "b").state
        // Only two at a time.
        val third = LootBoxes.startUnlocking(s, "c")
        assertEquals(s, third.state)
        // First reading sets the baseline; 2,000 steps later = 1 mile.
        s = LootBoxes.onStepCount(s, 10_000)
        assertEquals(0.0, s.lootBoxes[0].walkedMeters)
        s = LootBoxes.onStepCount(s, 12_000)
        assertTrue(s.lootBoxes[0].ready)
        assertTrue(!s.lootBoxes[1].ready)
        assertEquals(0.0, s.lootBoxes[2].walkedMeters)
        // Can't open early.
        assertEquals(s, LootBoxes.open(s, "b").update.state)
        val opened = LootBoxes.open(s, "a", rng).update
        assertEquals(2, opened.state.lootBoxes.size)
        assertTrue(opened.state.gold > s.gold)
        // Reboot: counter restarts from zero.
        s = LootBoxes.onStepCount(opened.state, 4_000)
        assertEquals(3 * METERS_PER_MILE, s.lootBoxes.first { it.id == "b" }.walkedMeters, 0.01)
    }

    @Test
    fun boxLimitBlocksNewSessions() {
        val full = started().copy(lootBoxes = (1..MAX_LOOT_BOXES).map { LootBox("$it", BoxRarity.COMMON, 0) })
        assertNull(GameEngine.startFocus(full, 30, 0).state.focus)
    }

    @Test
    fun longerSessionsGiveRarerBoxes() {
        val short = (1..2000).count { LootBoxes.roll(15, Random(it)) != BoxRarity.COMMON }
        val long = (1..2000).count { LootBoxes.roll(120, Random(it)) != BoxRarity.COMMON }
        assertTrue(long > short)
    }

    @Test
    fun boxContentsByLevel() {
        val base = started().copy(inventory = emptyMap(), gold = 0)
        fun openBox(r: BoxRarity, seed: Int): BoxOpening {
            val s = base.copy(lootBoxes = listOf(LootBox("x", r, 0, unlocking = true, walkedMeters = r.meters)))
            return LootBoxes.open(s, "x", Random(seed))
        }
        repeat(20) { seed ->
            val l1 = openBox(BoxRarity.COMMON, seed)
            assertTrue(l1.update.state.gold in 30..60)
            assertEquals(0, l1.update.state.parts.values.sum())
            assertTrue(l1.update.state.spellPages.isEmpty())
            assertNull(l1.boss)
            val l2 = openBox(BoxRarity.RARE, seed).update.state
            assertTrue(l2.parts.values.sum() > 0)
            assertTrue(l2.spellPages.isEmpty())
            val l3 = openBox(BoxRarity.EPIC, seed).update.state
            assertTrue(l3.parts.values.sum() > 0 && l3.spellPages.isNotEmpty())
            assertNotNull(openBox(BoxRarity.LEGENDARY, seed).boss)
        }
        val legendaryBosses = (1..1000).count { LootBoxes.boxBoss(10, Random(it)).species.rarity == Rarity.LEGENDARY }
        assertTrue(legendaryBosses in 150..250, "about 20%: $legendaryBosses")
        // Level 4 boxes need an hour or more off the phone.
        assertNull(LootBoxes.odds(45)[BoxRarity.LEGENDARY])
        assertNotNull(LootBoxes.odds(60)[BoxRarity.LEGENDARY])
    }

    @Test
    fun forgingWeapons() {
        val s = started().copy(parts = mapOf(WeaponType.DAGGER to PARTS_TO_FORGE + 1, WeaponType.STAFF to 2))
        val u = GameEngine.forge(s, WeaponType.DAGGER, rng)
        assertEquals(1, u.state.parts[WeaponType.DAGGER])
        assertEquals(WeaponType.DAGGER, u.state.gear.single().type)
        assertEquals(u.state, GameEngine.forge(u.state, WeaponType.DAGGER, rng).state)
        assertEquals(u.state, GameEngine.forge(u.state, WeaponType.STAFF, rng).state)
    }

    @Test
    fun oldWeaponsConvert() {
        val old = started().copy(version = 2, weapons = listOf(Weapon.STORM_SPEAR), equippedWeapon = Weapon.ARCANE_STAFF, weaponParts = 3)
        val s = GameState.fromJson(old.toJson())
        assertEquals(WeaponType.BROADSWORD, s.gear.single().type)
        assertEquals(WeaponType.STAFF, s.equippedGear?.type)
        assertEquals(3, s.parts[WeaponType.SWORD])
        assertTrue(s.weapons.isEmpty())
    }

    @Test
    fun walkingRewardsAndHeals() {
        val m = Monster.create(Bestiary.get("emberkit"), 5, rng).copy(damage = 30)
        var s = started().copy(monsters = listOf(m), party = listOf(m.uid), heroDamage = 40)
        s = GameEngine.onStepCount(s, 1000, at(27)).state // baseline
        assertEquals(0L, s.stats.totalSteps)
        s = GameEngine.onStepCount(s, 1250, at(27)).state // +250 steps = 2 rewards, 50 banked
        // Walking heals, and builds toward torches (no XP any more).
        assertEquals(0, s.hero.xp)
        assertEquals(20, s.heroDamage)
        assertEquals(10, s.monsters.single().damage)
        assertEquals(0, s.monsters.single().xp)
        assertEquals(250, s.torchSteps)
        assertEquals(50, s.stepBank)
        assertEquals(250, s.stepsToday)
        s = GameEngine.onStepCount(s, 1300, at(27)).state // banked 50 + 50 = another reward
        assertEquals(10, s.heroDamage)
        assertEquals(0, s.monsters.single().damage)
        assertEquals(0, s.stepBank)
    }

    @Test
    fun damageCarriesBetweenBattles() {
        val s = started()
        val enemy = Monster.create(Bestiary.get("zapmouse"), 6, rng)
        val b = GameEngine.newBattle(s, enemy, EncounterType.WILD, rng = rng)
        var guard = 0
        while (b.outcome == BattleOutcome.ONGOING && guard++ < 200) b.step(BattleAction.Attack)
        val after = GameEngine.applyBattleResult(s, b.result(), 0).state
        val hurt = after.heroDamage > 0 || after.monsters.any { it.damage > 0 || it.fainted }
        assertTrue(hurt)
        // The next battle starts with the same missing HP.
        val hero = Combatant.fromHero(after.hero, after.equippedGear, after.heroDamage)
        assertEquals(after.hero.maxHp - after.heroDamage, hero.hp)
        // Potions heal outside battle.
        val healed = GameEngine.usePotion(after.copy(heroDamage = 60), Item.POTION, Combatant.HERO_KEY).state
        assertTrue(healed.heroDamage < 60)
    }

    @Test
    fun villainsAndShadowCurse() {
        val s = started().copy(hero = Hero(level = 10), inventory = mapOf(Item.RESTORE_POTION to 1))
        val camps = Villains.near(s, 40.7, -74.0, at(27), utc)
        assertTrue(camps.isNotEmpty())
        val camp = camps.first()
        assertTrue(camp.team.all { it.cursed })
        // Same day, same camps; cursed monsters can't be tamed.
        assertEquals(camps.map { it.id }, Villains.near(s, 40.7, -74.0, at(27, 20), utc).map { it.id })
        val b = GameEngine.newBattle(s, camp.team.first(), EncounterType.VILLAIN, rng = rng)
        b.enemy.hp = 1
        assertNotNull(b.captureBlockedReason())
        // The curse: half Defense, +25% Attack.
        val clean = camp.team.first().copy(cursed = false)
        assertTrue(camp.team.first().stats.defense < clean.stats.defense)
        assertTrue(camp.team.first().stats.attack > clean.stats.attack)

        val won = Villains.reward(s, camp, at(27), rng, utc).state
        assertTrue(won.gold > s.gold)
        assertEquals(camp.team.size, won.villainSpoils.size)
        assertTrue(Villains.near(won, 40.7, -74.0, at(27), utc).none { it.id == camp.id })
        // Redeeming costs a potion; about 75% join.
        val joins = (1..200).count { Villains.redeem(won, 0, Random(it)).state.monsters.size == won.monsters.size + 1 }
        assertTrue(joins in 120..180, "about 75%: $joins")
        val redeemed = (1..50).map { Villains.redeem(won, 0, Random(it)).state }.first { it.monsters.size > won.monsters.size }
        assertEquals(won.count(Item.RESTORE_POTION) - 1, redeemed.count(Item.RESTORE_POTION))
        assertTrue(redeemed.villainSpoils.isEmpty())
        val newcomer = redeemed.monsters.last()
        assertTrue(newcomer.cursed)

        // Fighting lifts the curse; then one stat can be perfected.
        var t = redeemed.copy(party = listOf(newcomer.uid))
        repeat(newcomer.cureNeeded) {
            val r = BattleResult(BattleOutcome.WON, EncounterType.WILD, clean, 0, 0, mapOf(newcomer.uid to 1), emptyMap(), emptyMap(), null, null)
            t = GameEngine.applyBattleResult(t, r, 0).state
        }
        val cured = t.monsters.last()
        assertTrue(!cured.cursed && cured.cureReady)
        val perfect = GameEngine.perfectStat(t, cured.uid, Stat.SPEED).state.monsters.last()
        assertEquals(MAX_IV, perfect.ivs.speed)
        assertTrue(!perfect.cureReady)
    }

    @Test
    fun learnsetsAndLoadouts() {
        Moves.learnsets.values.flatten().forEach { (_, id) -> Moves.get(id) }
        for (sp in Bestiary.all) {
            val m = Monster.create(sp, 1, rng)
            assertTrue(m.activeMoves.any { it.isDamaging }, "${sp.id} needs an attack at Lv 1")
            assertTrue(Monster.create(sp, 60, rng).activeMoves.size == Moves.MAX_MOVES)
            // No monster is Normal type; Normal is attack-only.
            assertNotEquals(Element.NEUTRAL, sp.element)
        }
        // Better moves as they grow.
        val young = Monster.create(Bestiary.get("bubblefin"), 5, rng).activeMoves.maxOf { it.power }
        val old = Monster.create(Bestiary.get("bubblefin"), 40, rng).activeMoves.maxOf { it.power }
        assertTrue(old > young)
    }

    @Test
    fun monstersOutclassTheHero() {
        // A level-20 hero who spread their points evenly, vs. every species at level 20 with average genes.
        val hero = Hero(level = 20, attributes = HeroAttribute.entries.associateWith { STARTING_ATTRIBUTE + 19 * STAT_POINTS_PER_LEVEL / 5 })
        val heroTotal = hero.maxHp + hero.attack + hero.defense + hero.speed
        for (sp in Bestiary.all) {
            val m = Monster(uid = "m", speciesId = sp.id, personality = Personality.BALANCED, ivs = StatBlock(7, 7, 7, 7, 7), level = 20).stats
            assertTrue(m.hp + m.attack + m.defense + m.speed > heroTotal, "${sp.id} should out-stat the hero")
            assertTrue(m.hp > hero.maxHp, "${sp.id} HP")
        }
    }

    @Test
    fun growingTeachesMoves() {
        val m = Monster.create(Bestiary.get("emberkit"), 6, rng)
        val (after, notes) = GameEngine.grow(m, monsterXpForNext(6))
        assertEquals(7, after.level)
        assertTrue(notes.any { it.contains("learned Ember Fang") })
        // A custom loadout with a free slot gets the new move...
        val (filled, _) = GameEngine.grow(m.copy(moves = listOf("spit_fire", "shoulder_rush", "fang_snap")), monsterXpForNext(6))
        assertEquals(listOf("spit_fire", "shoulder_rush", "fang_snap", "ember_fang"), filled.moves)
        // ...but a full one is left alone, with a hint to swap it in.
        val (full, fullNotes) = GameEngine.grow(m.copy(moves = listOf("spit_fire", "shoulder_rush", "fang_snap", "spit_fire").distinct() + "shoulder_rush"), monsterXpForNext(6))
        assertTrue(fullNotes.any { it.contains("can learn Ember Fang") })
        assertTrue("ember_fang" !in full.moves!!)
        // Players can choose any learned moves for their 4 slots.
        val s = started().copy(monsters = listOf(after))
        val set = GameEngine.setMoves(s, after.uid, listOf("ember_fang", "fang_snap", "not_a_move")).state
        assertEquals(listOf("ember_fang", "fang_snap"), set.monsters.single().moves)
    }

    @Test
    fun sleepStopsEnemyAndHelpsTaming() {
        val s = started().copy(party = emptyList())
        val enemy = Monster.create(Bestiary.get("zapmouse"), 5, rng)
        val b = GameEngine.newBattle(s, enemy, EncounterType.WILD, rng = Random(4))
        b.enemy.hp = b.enemy.maxHp / 4
        val awake = b.captureOdds(Item.BINDING_CRYSTAL)
        b.enemy.status = Status.SLEEP
        b.enemy.statusTurns = 99
        assertTrue(b.captureOdds(Item.BINDING_CRYSTAL) > awake)
        val heroHp = b.allies.first().hp
        repeat(5) { b.step(BattleAction.Defend) }
        assertEquals(heroHp, b.allies.first().hp)
    }

    @Test
    fun weatherAndTypes() {
        assertEquals(1.25, Weather.RAIN.multiplier(Element.WATER))
        assertEquals(1.25, Weather.RAIN.multiplier(Element.ELECTRIC))
        assertEquals(0.85, Weather.RAIN.multiplier(Element.FIRE))
        assertEquals(1.25, Weather.DROUGHT.multiplier(Element.CORRUPT))
        assertEquals(0.85, Weather.DROUGHT.multiplier(Element.WATER))
    }

    @Test
    fun faintedMonstersNeedRestorePotions() {
        var s = started().copy(gold = 100)
        val starter = s.monsters.single()
        val brute = Monster.create(Bestiary.get("thunder_titan"), 60, rng)
        val b = GameEngine.newBattle(s, brute, EncounterType.WILD, rng = rng, fullTeam = true)
        var guard = 0
        while (b.outcome == BattleOutcome.ONGOING && guard++ < 200) b.step(BattleAction.Defend)
        val r = b.result()
        assertTrue(starter.uid in r.faintedUids)
        s = GameEngine.applyBattleResult(s, r, 0).state
        assertTrue(s.monsters.single().fainted)
        // Fainted monsters sit out battles.
        assertEquals(1, GameEngine.newBattle(s, brute, EncounterType.WILD, rng = rng, fullTeam = true).allies.size)
        val restored = GameEngine.restore(s, starter.uid).state
        assertTrue(!restored.monsters.single().fainted)
        assertEquals(0, restored.count(Item.RESTORE_POTION))
        // Without a potion, the daily recovery kicks in once a day.
        val noPotion = s.copy(inventory = emptyMap())
        assertEquals(noPotion, GameEngine.restore(noPotion, starter.uid).state)
        val rec = GameEngine.dailyRecovery(noPotion, at(27), utc)
        assertNotNull(rec)
        assertTrue(!rec.state.monsters.single().fainted)
        val again = rec.state.copy(monsters = rec.state.monsters.map { it.copy(fainted = true) })
        assertNull(GameEngine.dailyRecovery(again, at(27, 20), utc))
    }

    @Test
    fun adventures() {
        val places = (1..60).map { World.shrineFromPlace("$it", "Place $it", 0.0, 0.0, ShrineTier.ITEM) }
        val few = started()
        // Locked until you have 5 monsters.
        val anyOffer = Adventures.offersNear(few, places, at(27), utc).first()
        assertTrue(Adventures.send(few, anyOffer, few.monsters.map { it.uid }, at(27)).state.adventures.isEmpty())

        val team = (1..6).map { Monster.create(Bestiary.get("shadow_pup"), 15, Random(it)) }
        val s = started().copy(hero = Hero(level = 12), monsters = team)
        val offers = Adventures.offersNear(s, places, at(27), utc)
        assertTrue(offers.isNotEmpty() && offers.size <= 6)
        // Same day, same offers; a different day rotates them.
        assertEquals(offers, Adventures.offersNear(s, places, at(27, 20), utc))
        assertNotEquals(offers, Adventures.offersNear(s, places, at(28), utc))

        val adv = offers.first()
        val uids = team.take(adv.teamSize).map { it.uid }
        assertEquals(s, Adventures.send(s, adv, team.take(adv.teamSize + 1).map { it.uid }.take(if (adv.teamSize == 5) 4 else adv.teamSize + 1), at(27)).state)
        val sent = Adventures.send(s, adv, uids, at(27)).state
        assertEquals(1, sent.adventures.size)
        // Away monsters sit out battles and aren't offered for other adventures.
        assertTrue(Adventures.available(sent).none { it.uid in uids })
        val sentParty = sent.copy(party = uids.take(1))
        assertEquals(1, GameEngine.newBattle(sentParty, Monster.create(Bestiary.get("zapmouse"), 3, rng), EncounterType.WILD, rng = rng).allies.size)
        // Your battle party stays with you: party monsters can't be sent...
        val withParty = s.copy(party = listOf(team[0].uid))
        assertTrue(Adventures.available(withParty).none { it.uid == team[0].uid })
        assertEquals(withParty, Adventures.send(withParty, adv, team.take(adv.teamSize).map { it.uid }, at(27)).state)
        // ...and monsters out adventuring can't join the party.
        assertTrue(GameEngine.toggleParty(sent.copy(party = emptyList()), uids[0]).state.party.isEmpty())
        assertTrue(GameEngine.setPartySlot(sent.copy(party = emptyList()), uids[0], 0).state.party.isEmpty())
        // The taken adventure isn't offered again.
        assertTrue(Adventures.offersNear(sent, places, at(27), utc).none { it.id == adv.id })
        // Can't claim early.
        assertEquals(sent, Adventures.claim(sent, adv.id, at(27), rng, utc).state)

        val done = Adventures.claim(sent, adv.id, at(27) + adv.durationMs, Random(1), utc)
        assertTrue(done.state.adventures.isEmpty())
        assertTrue(done.state.gold > sent.gold)
        // Hero earns no XP from adventures; the monsters do.
        assertEquals(sent.hero.level to sent.hero.xp, done.state.hero.level to done.state.hero.xp)
        assertTrue(done.state.monsters.filter { it.uid in uids }.all { it.xp > 0 || it.level > 15 })
        assertTrue(adv.id in done.state.adventureLog)
    }

    @Test
    fun strongerTeamsSucceedMoreOften() {
        val adv = Adventure("x", "Park", "Test", 3, 20, 60_000)
        val weak = (1..3).map { Monster.create(Bestiary.get("shadow_pup"), 10, Random(it)) }
        val strong = (1..3).map { Monster.create(Bestiary.get("shadow_pup"), 30, Random(it)) }
        assertTrue(Adventures.successChance(strong, adv) > Adventures.successChance(weak, adv))
        assertTrue(Adventures.successChance(weak, adv) < 0.5)
    }

    @Test
    fun heroClasses() {
        val base = Hero(level = 4)
        assertEquals(base, GameEngine.chooseClass(started().copy(hero = base), HeroClass.WARRIOR).state.hero)
        var s = started().copy(hero = Hero(level = 5), gold = 1000)
        s = GameEngine.chooseClass(s, HeroClass.WARRIOR).state
        assertEquals(HeroClass.WARRIOR, s.hero.heroClass)
        assertEquals(1000, s.gold) // first pick is free
        s = GameEngine.chooseClass(s, HeroClass.MAGE).state
        assertEquals(1000 - CLASS_CHANGE_COST, s.gold)

        // Small at level 5, much bigger later.
        fun gap(level: Int, cls: HeroClass, pick: (Hero) -> Int): Int {
            val attrs = HeroAttribute.entries.associateWith { 20 }
            return pick(Hero(level = level, attributes = attrs, heroClass = cls)) - pick(Hero(level = level, attributes = attrs))
        }
        assertTrue(gap(5, HeroClass.WARRIOR) { it.attack } in 0..2)
        assertTrue(gap(30, HeroClass.WARRIOR) { it.attack } >= 15)
        assertTrue(gap(30, HeroClass.WARRIOR) { it.magic } < 0)
        assertTrue(gap(30, HeroClass.MAGE) { it.magic } > 0 && gap(30, HeroClass.MAGE) { it.defense } > 0)
        assertTrue(gap(30, HeroClass.MAGE) { it.attack } < 0)
        assertTrue(gap(30, HeroClass.ROGUE) { it.speed } > 0)
        assertEquals(0.0, Hero(level = 30).evasion)
        assertEquals(0.2, Hero(level = 30, heroClass = HeroClass.ROGUE).evasion, 0.001)
        assertTrue(Hero(level = 5, heroClass = HeroClass.ROGUE).evasion < 0.02)
    }

    @Test
    fun roguesDodge() {
        val rogue = Hero(level = 60, heroClass = HeroClass.ROGUE, attributes = HeroAttribute.entries.associateWith { 60 })
        val s = started().copy(hero = rogue, party = emptyList())
        var dodges = 0
        repeat(20) { seed ->
            val b = GameEngine.newBattle(s, Monster.create(Bestiary.get("zapmouse"), 30, Random(seed)), EncounterType.WILD, rng = Random(seed))
            repeat(10) { if (b.outcome == BattleOutcome.ONGOING) b.step(BattleAction.Defend) }
            dodges += b.log.count { it.contains("nimbly dodges") }
        }
        assertTrue(dodges > 0)
    }

    @Test
    fun adventurerCanBeChosenAndLevelFiveGivesRareWeapon() {
        val s = started().copy(hero = Hero(level = 5))
        val u = GameEngine.chooseClass(s, HeroClass.NONE, Random(1))
        assertTrue(u.state.hero.classChosen)
        assertEquals(HeroClass.NONE, u.state.hero.heroClass)
        assertEquals(GearRarity.RARE, u.state.gear.single().rarity)
        // Switching later costs gold and gives no second weapon.
        val again = GameEngine.chooseClass(u.state.copy(gold = 1000), HeroClass.MAGE, Random(2)).state
        assertEquals(HeroClass.MAGE, again.hero.heroClass)
        assertEquals(1, again.gear.size)
        assertEquals(1000 - CLASS_CHANGE_COST, again.gold)
    }

    @Test
    fun armorAddsDefenseAndShowsInBattle() {
        val plate = Armor("a", ArmorType.PLATE, GearRarity.EPIC, 10)
        val s = started().copy(armor = listOf(plate))
        assertEquals(s.bagCount, s.inventory.values.sum() + 1)
        val worn = GameEngine.equipArmor(s, "a").state
        assertEquals(plate, worn.equippedArmor)
        assertTrue(worn.armor.isEmpty())
        val bare = Combatant.fromHero(s.hero)
        val armored = Combatant.fromHero(s.hero, armor = listOf(plate))
        assertTrue(armored.defense > bare.defense)
        assertTrue(armored.speed <= bare.speed)
        assertTrue(Armor("r", ArmorType.ROBE, GearRarity.RARE, 10).magicBonus(HeroClass.MAGE) > 0)
    }

    @Test
    fun armorPiecesFillTheirOwnSlots() {
        val hood = Armor("h", ArmorType.HOOD, GearRarity.RARE, 10, listOf(GearEffect.FOCUSED))
        val boots = Armor("b", ArmorType.BOOTS, GearRarity.NORMAL, 10)
        val pants = Armor("p", ArmorType.PANTS, GearRarity.NORMAL, 10)
        val plate = Armor("a", ArmorType.PLATE, GearRarity.NORMAL, 10)
        var s = started().copy(armor = listOf(hood, boots, pants, plate))
        for (id in listOf("h", "b", "p", "a")) s = GameEngine.equipArmor(s, id).state
        assertEquals(plate, s.equippedArmor)
        assertEquals(mapOf(ArmorSlot.HEAD to hood, ArmorSlot.FEET to boots, ArmorSlot.LEGS to pants), s.equippedPieces)
        assertEquals(4, s.wornArmor.size)
        assertTrue(s.armor.isEmpty())
        // Each piece has its own buff: boots add Speed, pants Attack, the hood Magic (plus Focused).
        val bare = Combatant.fromHero(s.hero)
        assertTrue(Combatant.fromHero(s.hero, armor = listOf(boots)).speed > bare.speed)
        assertTrue(Combatant.fromHero(s.hero, armor = listOf(pants)).attack > bare.attack)
        assertTrue(Combatant.fromHero(s.hero, armor = listOf(hood)).magic > bare.magic)
        // Swapping a hood puts the old one back in the bag.
        val hood2 = Armor("h2", ArmorType.HOOD, GearRarity.EPIC, 12)
        val swapped = GameEngine.equipArmor(s.copy(armor = listOf(hood2)), "h2").state
        assertEquals(hood2, swapped.equippedPieces[ArmorSlot.HEAD])
        assertEquals(listOf(hood), swapped.armor)
        val off = GameEngine.unequipArmor(swapped, ArmorSlot.FEET).state
        assertNull(off.equippedPieces[ArmorSlot.FEET])
        // Rare and better pieces always carry their signature buff.
        val rng = Random(4)
        repeat(30) {
            val a = Armory.rollArmor(20, rng, minimum = GearRarity.RARE)
            assertEquals(a.type.signature, a.effects.first())
            assertEquals(a.rarity.effectCount, a.effects.distinct().size)
        }
    }

    @Test
    fun journalTracksSeenAndCaught() {
        val s = started()
        assertTrue("emberkit" in s.caught && "emberkit" in s.seen)
        val seen = GameEngine.markSeen(s, "zapmouse")
        assertTrue("zapmouse" in seen.seen && "zapmouse" !in seen.caught)
        val loaded = GameState.fromJson(s.copy(caught = emptySet(), seen = emptySet()).toJson())
        assertTrue("emberkit" in loaded.caught)
    }

    @Test
    fun densHoldStrongMonstersThatMustBeBeatenFirst() {
        val now = 1_700_000_000_000L
        val s = started().copy(hero = started().hero.copy(level = 10))
        val dens = Dens.near(s, 40.0, -75.0, now, java.time.ZoneOffset.UTC)
        assertTrue(dens.isNotEmpty())
        // Same dens all day, and never legendaries.
        assertEquals(dens.map { it.id to it.monster.speciesId }, Dens.near(s, 40.0, -75.0, now + 60_000, java.time.ZoneOffset.UTC).map { it.id to it.monster.speciesId })
        for (d in dens) {
            assertTrue(d.species.rarity != Rarity.LEGENDARY)
            assertTrue(d.monster.level >= 13)
            assertTrue(GameEngine.perfectStatCount(d.monster) >= 2)
            assertTrue(MONSTER_STATS.all { d.monster.ivs[it] >= 10 })
        }
        val den = dens.first()
        // It can't be tamed during the fight.
        val b = GameEngine.newBattle(s, den.monster, EncounterType.DEN, den.id, rng = Random(2))
        assertTrue(b.captureLocked)
        assertFalse(b.isBoss)
        // Beating it clears the den and starts the taming throws: 5, plus one per monster still standing.
        var won = Dens.defeated(s, den.id, den.monster, now, 3, java.time.ZoneOffset.UTC)
        val c = won.pendingDenCatch!!
        assertEquals(8, c.throwsTotal)
        assertEquals(5, Dens.throwsFor(0))
        assertEquals(10, Dens.throwsFor(5))
        assertEquals(10, Dens.throwsFor(9))
        // Always a real chance: odds shown include the 2% critical catch.
        assertTrue(Dens.oddsOver(den.monster, 5) > 0.2)
        assertTrue(Dens.oddsOver(den.monster, 10) > Dens.oddsOver(den.monster, 5))
        // About 2% of throws are critical catches.
        val critRng = Random(77)
        var crits = 0
        repeat(5_000) {
            val one = Dens.defeated(s, den.id, den.monster, now, 0, java.time.ZoneOffset.UTC)
            if ("Critical catch" in Dens.attempt(one, critRng).message) crits++
        }
        assertTrue(crits in 60..140, "crits $crits of 5000")
        assertEquals(1.0, withCriticalCatch(1.0))
        assertEquals(0.02 + 0.98 * 0.5, withCriticalCatch(0.5), 1e-9)
        assertTrue(Dens.near(won, 40.0, -75.0, now, java.time.ZoneOffset.UTC).none { it.id == den.id })
        // Hard but possible: keep throwing until it's tamed or escapes.
        val rng = Random(9)
        var tries = 0
        while (won.pendingDenCatch != null) {
            won = Dens.attempt(won, rng).state
            tries++
        }
        assertTrue(tries <= c.throwsTotal)
        // Over many dens, some are tamed and some get away.
        var tamed = 0
        repeat(200) { i ->
            var t = Dens.defeated(s, den.id, den.monster, now, i % 6, java.time.ZoneOffset.UTC)
            val r = Random(1000 + i)
            while (t.pendingDenCatch != null) t = Dens.attempt(t, r).state
            if (t.monsters.size > s.monsters.size) tamed++
        }
        assertTrue(tamed in 60..190, "tamed $tamed of 200")
    }

    @Test
    fun berriesBoostTheNextThrow() {
        val now = 1_700_000_000_000L
        val foe = Monster.create(Bestiary.get("zapmouse"), 5, Random(4))
        val s = started().copy(inventory = mapOf(Item.BINDING_CRYSTAL to 5, Item.SWEET_BERRY to 2, Item.GOLDEN_BERRY to 1))
        val b = GameEngine.newBattle(s, foe, EncounterType.WILD, rng = Random(5))
        b.enemy.hp = b.enemy.maxHp / 3
        val before = b.captureOdds(Item.BINDING_CRYSTAL)
        // Berries are on the Tame menu, not with the potions.
        assertTrue(Item.SWEET_BERRY in b.berries() && Item.SWEET_BERRY !in b.battleItems())
        val me = b.current!!
        b.step(BattleAction.UseItem(Item.SWEET_BERRY, me.key))
        assertEquals(1.5, b.bait)
        assertTrue(b.captureOdds(Item.BINDING_CRYSTAL) > before)
        assertTrue(b.log.any { "Sweet Berry" in it })
        // Not in the shop; sold for a little.
        assertEquals(0, Item.SWEET_BERRY.price)
        assertTrue(Item.GOLDEN_BERRY.sellPrice in 1..100)

        // At a den: a berry doesn't use a throw, boosts only the next one, and doesn't stack.
        val den = Dens.near(s, 40.0, -75.0, now, java.time.ZoneOffset.UTC).first()
        var d = Dens.defeated(s, den.id, den.monster, now, 0, java.time.ZoneOffset.UTC)
        val plain = Dens.throwOdds(den.monster)
        d = Dens.feed(d, Item.GOLDEN_BERRY).state
        assertEquals(2.5, d.pendingDenCatch!!.bait)
        assertEquals(5, d.pendingDenCatch!!.throwsLeft)
        assertEquals(0, d.count(Item.GOLDEN_BERRY))
        assertTrue(Dens.throwOdds(den.monster, 2.5) > plain)
        assertTrue(Dens.oddsOver(den.monster, 5, 2.5) > Dens.oddsOver(den.monster, 5))
        assertEquals(d, Dens.feed(d, Item.SWEET_BERRY).state)
        // Den odds went up a little: a common one is now 15% a throw (before the critical catch).
        assertTrue(Dens.throwChance(Monster.create(Bestiary.get("zapmouse"), 5, Random(1))) >= 0.15)
    }

    @Test
    fun levelUpsReportStatsMovesAndEvolution() {
        // A level 13 Emberkit levels to 14: it doesn't evolve by itself, but it's ready to.
        val m = Monster.create(Bestiary.get("emberkit"), 13, Random(3))
        val (after, up) = GameEngine.growDetailed(m, monsterXpForNext(13))
        assertNotNull(up)
        assertEquals(13, up.fromLevel)
        assertEquals(14, up.toLevel)
        assertEquals("emberkit", after.speciesId)
        assertNull(up.evolvedFrom)
        assertTrue(up.notes.any { "Ready to evolve" in it })
        assertEquals(listOf("HP", "Attack", "Magic", "Defense", "Speed"), up.stats.map { it.label })
        assertTrue(up.stats.all { it.gain > 0 })
        assertEquals(after.stats.hp, up.stats.first().after)
        // No level, no level-up.
        assertNull(GameEngine.growDetailed(m, 1).second)
        // The hero's level-up lists attribute points to spend.
        val s = started()
        val gain = GameEngine.addHeroXp(s, heroXpForNext(1))
        val hero = GameEngine.heroLevelUp(s.hero, gain)
        assertNotNull(hero)
        assertEquals(2, hero.toLevel)
        assertTrue(hero.stats.first { it.label == "HP" }.gain > 0)
        assertTrue(hero.notes.any { it.contains("attribute points") })
    }

    @Test
    fun plannerGroupsByDueDateAndPriority() {
        val zone = java.time.ZoneOffset.UTC
        val now = java.time.LocalDateTime.of(2026, 10, 26, 12, 0).toInstant(zone).toEpochMilli()
        val h = 3_600_000L
        var s = started()
        fun add(title: String, due: Long?, p: Priority = Priority.NORMAL) {
            s = GameEngine.addTask(s, title, Difficulty.NORMAL, Random(title.hashCode()), now, dueMs = due, priority = p).state
        }
        add("late", now - h)
        add("soon", now + 20 * 60_000)
        add("tonight low", now + 6 * h, Priority.LOW)
        add("tonight high", now + 8 * h, Priority.HIGH)
        add("tomorrow", now + 24 * h)
        add("next week", now + 10 * 24 * h)
        add("whenever", null)
        val sections = Planner.sections(s.tasks, now, zone)
        assertEquals(
            listOf(Planner.Group.OVERDUE, Planner.Group.SOON, Planner.Group.TODAY, Planner.Group.TOMORROW, Planner.Group.LATER, Planner.Group.NO_DATE),
            sections.map { it.group },
        )
        // Higher priority first within a group, even if it's due later.
        assertEquals(listOf("tonight high", "tonight low"), sections.first { it.group == Planner.Group.TODAY }.tasks.map { it.title })
        val soon = s.tasks.first { it.title == "soon" }
        assertTrue(Planner.isDueSoon(soon, now))
        assertFalse(Planner.isDueSoon(s.tasks.first { it.title == "tomorrow" }, now))
        assertTrue(Planner.isOverdue(s.tasks.first { it.title == "late" }, now))
        assertEquals("in 20 min", Planner.relative(soon.dueAt!!, now))
        // Editing changes the due date and priority.
        val edited = GameEngine.editTask(s, soon.id, "soon!", Difficulty.HARD, now + 2 * h, Priority.HIGH).state.tasks.first { it.id == soon.id }
        assertEquals("soon!", edited.title)
        assertEquals(Priority.HIGH, edited.priority)
        assertEquals(Planner.Group.TODAY, Planner.groupOf(edited, now, zone))
        // A calendar event in progress isn't overdue until it ends.
        val meeting = QuestTask("cal", "Meeting", source = TaskSource.CALENDAR, startMs = now - 10 * 60_000, endMs = now + 50 * 60_000)
        assertEquals(Planner.Group.HAPPENING, Planner.groupOf(meeting, now, zone))
        // A single day's view: what's due that day, and what was done that day.
        val today = java.time.LocalDate.of(2026, 10, 26).toEpochDay()
        val done = GameEngine.completeTask(s, s.tasks.first { it.title == "late" }.id, now).state
        val day = Planner.onDay(done.tasks, today, zone)
        assertEquals(listOf("soon", "tonight low", "tonight high"), day.due.map { it.title })
        assertEquals(listOf("late"), day.completed.map { it.title })
        assertEquals(listOf("tomorrow"), Planner.onDay(done.tasks, today + 1, zone).due.map { it.title })
        assertTrue(today + 1 in Planner.dueDays(done.tasks, zone))
    }

    @Test
    fun secretWalkingEncounters() {
        // Every 50 steps rolls a 25% chance; leftover steps carry over.
        val rng = Random(7)
        var bank = 0
        var hits = 0
        repeat(4_000) {
            val (b, hit) = SecretEncounters.roll(bank, 50, rng)
            bank = b
            if (hit) hits++
        }
        assertTrue(hits in 850..1150, "hits $hits of 4000")
        assertEquals(30 to false, SecretEncounters.roll(0, 30, Random(1)))
        assertEquals(10, SecretEncounters.roll(40, 70, Random(1)).first)
        // Usually one monster, sometimes 2 or 3 of the same kind.
        val groups = (1..2_000).map { SecretEncounters.encounter(10, 12, Random(it)) }
        assertTrue(groups.all { g -> g.size in 1..3 && g.map { it.speciesId }.distinct().size == 1 })
        assertTrue(groups.count { it.size == 1 } > groups.count { it.size == 3 })
        assertTrue(groups.any { it.size == 3 })
        // Groups are proportionally weaker each, so a trio isn't overwhelming.
        val trio = groups.first { it.size == 3 }
        val s = started()
        val one = GameEngine.newBattle(s, trio[0], EncounterType.WILD, rng = Random(2), ambush = true, fullTeam = true)
        val three = GameEngine.newBattle(s, trio[0], EncounterType.WILD, rng = Random(2), pack = trio.drop(1), ambush = true, fullTeam = true)
        assertTrue(three.enemies.all { it.maxHp < one.enemy.maxHp })
        assertTrue(three.enemies.sumOf { it.maxHp } < one.enemy.maxHp * 1.2)
    }

    @Test
    fun movesAreVariedAndWork() {
        // Every learnset and signature move exists, and every type has plenty.
        (Moves.learnsets.values.flatten() + Moves.familyMoves.values.flatten()).forEach { (_, id) -> assertNotNull(Moves.find(id), id) }
        assertTrue(Moves.all.size >= 90)
        // Lines of the same type learn different signature moves.
        val drake = Moves.learnable(Bestiary.get("infernodrake")).map { it.second.id }
        val cat = Moves.learnable(Bestiary.get("solar_tiger")).map { it.second.id }
        assertTrue("razor_gale" in drake && "iron_wing" in drake && "iron_wing" !in cat)
        assertTrue("ember_fang" in Moves.learnable(Bestiary.get("shadow_pup")).map { it.second.id })
        // Multi-hit, sure-hit, self-buff and cure all work in battle.
        val user = Monster.create(Bestiary.get("blazecat"), 30, Random(3)).copy(moves = listOf("frenzy_swipes", "focus", "kindle", "quick_strike"))
        val s = started().let { it.copy(monsters = listOf(user), party = listOf(user.uid)) }
        val b = GameEngine.newBattle(s, Monster.create(Bestiary.get("goopling"), 30, Random(4)), EncounterType.BOSS_CHALLENGE, rng = Random(5), fullTeam = true)
        val me = b.allies.first { it.key == user.uid }
        fun myTurn() { var g = 0; while (b.current?.key != user.uid && g++ < 20 && b.outcome == BattleOutcome.ONGOING) b.step(BattleAction.Defend) }
        // Frenzy Swipes can miss (80% accuracy): swipe until it lands.
        repeat(8) { if (b.log.none { it.matches(Regex("Hit [2-5] times!")) }) { myTurn(); b.step(BattleAction.UseMove("frenzy_swipes")) } }
        assertTrue(b.log.any { it.matches(Regex("Hit [2-5] times!")) })
        myTurn(); b.step(BattleAction.UseMove("focus"))
        assertTrue((me.stages[BattleStat.ATTACK] ?: 0) >= 2)
        me.status = Status.BURN; me.statusTurns = 3
        myTurn(); b.step(BattleAction.UseMove("kindle"))
        assertTrue(b.log.any { it.contains("shakes off") })
        // 20 power, 3.5 hits on average, 85% accuracy.
        assertEquals(59.5, Moves.get("ember_barrage").expectedPower, 0.01)
    }

    @Test
    fun startersHaveUniqueSignatureMoves() {
        val signature = mapOf(
            "voltfawn" to listOf("storm_antlers", "heavens_charge"),
            "cindrake" to listOf("wyrmfire", "inferno_wings"),
            "drizzlet" to listOf("tidal_embrace", "maelstrom_vortex"),
            "mosscub" to listOf("grove_guardian", "tectonic_fury"),
        )
        val everyoneElse = Bestiary.all.filter { sp -> signature.keys.none { Bestiary.lineOf(Bestiary.get(it)).contains(sp) } }
        for ((starter, moves) in signature) {
            val final = Bestiary.lineOf(Bestiary.get(starter)).last()
            val known = Moves.learned(final, 40).map { it.id }
            moves.forEach { assertTrue(it in known, "$starter learns $it") }
            // Nobody outside the line learns them.
            everyoneElse.forEach { sp -> moves.forEach { assertTrue(it !in Moves.learnable(sp).map { m -> m.second.id }, "${sp.id} has $it") } }
        }

        fun fight(starterId: String, move: String, packSize: Int = 3): Pair<Battle, Combatant> {
            val m = Monster.create(Bestiary.get(starterId), 40, Random(3)).copy(moves = listOf(move))
            val s = started().let { it.copy(monsters = listOf(m), party = listOf(m.uid)) }
            val foes = (0 until packSize).map { Monster.create(Bestiary.get("zapmouse"), 30, Random(10 + it)).copy(skill = Trait.CRACKLE_SKIN) }
            val b = GameEngine.newBattle(s, foes[0], EncounterType.WILD, rng = Random(5), pack = foes.drop(1), fullTeam = true)
            var g = 0
            while (b.current?.key != m.uid && g++ < 30 && b.outcome == BattleOutcome.ONGOING) b.step(BattleAction.Defend)
            return b to b.allies.first { it.key == m.uid }
        }
        // Spread moves hit the whole pack.
        val (spread, _) = fight("thunderhorn", "storm_antlers")
        val before = spread.enemies.map { it.hp }
        spread.act(BattleAction.UseMove("storm_antlers"))
        // It's aimed at all three: each one is hit, dodges or is missed.
        val start = spread.log.indexOfFirst { it.contains("uses Storm Antlers") }
        val after = spread.log.drop(start + 1).takeWhile { !it.contains(" uses ") }
        assertEquals(3, after.count { it.startsWith("It hits") || it.contains("misses") || it.contains("dodges") }, "spread: $after")
        assertTrue(spread.enemies.indices.any { spread.enemies[it].hp < before[it] })
        // Lifesteal heals the user.
        val (drain, me) = fight("maelstrom_serpent", "tidal_embrace", 1)
        me.hp = me.maxHp / 2
        drain.act(BattleAction.UseMove("tidal_embrace"))
        assertTrue(drain.log.any { it.contains("drains the foe") }, "drain: " + drain.log.takeLast(6))
        // The grove heals and toughens the whole team.
        val (grove, bear) = fight("terraursa", "grove_guardian", 1)
        bear.hp = bear.maxHp / 2
        grove.act(BattleAction.UseMove("grove_guardian"))
        assertEquals(1, bear.stages[BattleStat.DEFENSE])
        assertTrue(grove.allies.first().stages[BattleStat.DEFENSE] == 1, "grove: " + grove.log.takeLast(6))
        // Heaven's Charge goes straight through a guard.
        val (pierce, _) = fight("thunderhorn", "heavens_charge", 1)
        pierce.enemy.protecting = true
        val hp = pierce.enemy.hp
        pierce.act(BattleAction.UseMove("heavens_charge"))
        assertTrue(pierce.enemy.hp < hp || pierce.log.any { it.contains("miss") }, "pierce: " + pierce.log.takeLast(6))
    }

    @Test
    fun packsAndAmbushes() {
        val s = started()
        val foes = (1..3).map { Monster.create(Bestiary.get("zapmouse"), 3, Random(it)) }
        val b = GameEngine.newBattle(s, foes[0], EncounterType.WILD, rng = Random(1), pack = foes.drop(1), ambush = true, fullTeam = true)
        assertEquals(3, b.enemies.size)
        assertEquals(listOf("enemy", "enemy2", "enemy3"), b.enemies.map { it.key })
        // In an ambush every monster acts before you in round 1.
        val firstAllyEvent = b.events.indexOfFirst { !Combatant.isEnemy(it.actorKey) }
        assertTrue(firstAllyEvent == -1)
        assertTrue(b.log.any { it.contains("Ambush") })
        // Targeting: attacks go at the chosen monster.
        b.selectTarget("enemy3")
        assertEquals("enemy3", b.enemy.key)
        var guard = 0
        while (b.outcome == BattleOutcome.ONGOING && guard++ < 200) {
            if (b.current != null) b.step(BattleAction.Attack) else break
        }
        if (b.outcome == BattleOutcome.WON) {
            assertTrue(b.enemies.none { it.alive })
            // Packs pay more than a single monster of the same level.
            val solo = TeamScaling.reward(b.teamSize)
            assertTrue(TeamScaling.reward(b.teamSize) * TeamScaling.packReward(3) > solo)
        }
        // Each pack member is weaker than a lone monster would be.
        val single = GameEngine.newBattle(s, foes[0], EncounterType.WILD, rng = Random(1), fullTeam = true)
        assertTrue(b.enemies.first().maxHp < single.enemy.maxHp)
    }

    @Test
    fun nightFavorsCorruptAndGas() {
        val rng = Random(9)
        val night = (1..2000).count { Bestiary.randomWild(rng, 50, TimeOfDay.NIGHT.favored).element in TimeOfDay.NIGHT.favored }
        val day = (1..2000).count { Bestiary.randomWild(rng, 50, TimeOfDay.DAY.favored).element in TimeOfDay.NIGHT.favored }
        assertTrue(night > day * 1.5)
        assertEquals(TimeOfDay.NIGHT, TimeOfDay.of(2))
        assertEquals(TimeOfDay.DAY, TimeOfDay.of(13))
    }

    @Test
    fun walkingTracksHistoryAndActivity() {
        val now = 1_700_000_000_000L
        val s = GameEngine.walk(started(), 1500, now).state
        assertEquals(1500, s.stepHistory.values.single())
        val withTask = GameEngine.addTask(s, "x", Difficulty.EASY, rng).state
        val done = GameEngine.completeTask(withTask, withTask.tasks.last().id, now).state
        assertEquals(mapOf(GameEngine.epochDay(now) to 1), done.questDays)
    }

    @Test
    fun releasingGivesCandyThatLevelsUpWithoutChangingStatPoints() {
        val s0 = started()
        val kit = s0.monsters.single()
        val extra = Monster.create(Bestiary.get("blazecat"), 20, Random(5))
        val s = s0.copy(monsters = s0.monsters + extra)
        val released = GameEngine.release(s, extra.uid).state
        val family = kit.species.family
        assertEquals(2 + 20 / 15, released.candy[family])
        assertEquals(0, released.bagCount - s0.bagCount)
        // Candy is shared by the whole line and cost climbs with level.
        assertTrue(GameEngine.candyCost(30) > GameEngine.candyCost(5))
        val leveled = GameEngine.candyLevelUp(released, kit.uid).state
        val after = leveled.monsters.single()
        assertEquals(kit.level + 1, after.level)
        assertEquals(kit.ivs, after.ivs)
        assertEquals(released.candy[family]!! - GameEngine.candyCost(kit.level), leveled.candy[family])
        // Not enough candy: nothing happens.
        val broke = leveled.copy(candy = emptyMap())
        assertEquals(broke, GameEngine.candyLevelUp(broke, kit.uid).state)
        // Evolving (with the button, for candy) keeps stat points too.
        // (A strong enough hero: monsters can't get more than 5 levels ahead.)
        var m = released.copy(candy = mapOf(family to 999), hero = released.hero.copy(level = 30))
        repeat(15) { m = GameEngine.candyLevelUp(m, kit.uid).state }
        assertEquals("emberkit", m.monsters.single().speciesId)
        m = GameEngine.evolve(m, kit.uid).state
        assertEquals("blazecat", m.monsters.single().speciesId)
        assertEquals(kit.ivs, m.monsters.single().ivs)
    }

    @Test
    fun tamingGivesThreeCandy() {
        val s = started()
        val wild = Monster.create(Bestiary.get("galehawk"), 20, Random(8))
        val result = BattleResult(
            BattleOutcome.CAPTURED, EncounterType.WILD, wild, 10, 5, emptyMap(), emptyMap(), emptyMap(), wild, null,
        )
        val after = GameEngine.applyBattleResult(s, result, 0).state
        assertEquals(GameEngine.TAME_CANDY, after.candy[wild.species.family])
        assertTrue(after.monsters.any { it.uid == wild.uid })
    }

    @Test
    fun healthConnectStepsCountOnlyNewSteps() {
        val now = 1_700_000_000_000L
        var s = GameEngine.setStepSource(started().copy(lastStepCount = 500), StepSource.SAMSUNG_HEALTH)
        assertNull(s.lastStepCount)
        assertEquals(StepSource.SAMSUNG_HEALTH, s.settings.stepSource)
        // First reading: baseline only.
        s = GameEngine.onDailyStepTotal(s, 10, 4000, now).state
        assertEquals(0, s.stepsToday)
        // Later the same day: only the difference counts.
        s = GameEngine.onDailyStepTotal(s, 10, 4250, now).state
        assertEquals(250, s.stats.totalSteps)
        // A lower reading (data corrected) never counts negative.
        s = GameEngine.onDailyStepTotal(s, 10, 4100, now).state
        assertEquals(250, s.stats.totalSteps)
        s = GameEngine.onDailyStepTotal(s, 10, 4300, now).state
        assertEquals(300, s.stats.totalSteps)
        // New day: the new total counts from midnight.
        s = GameEngine.onDailyStepTotal(s, 11, 120, now).state
        assertEquals(420, s.stats.totalSteps)
        val json = GameState.fromJson(s.toJson())
        assertEquals(StepSource.SAMSUNG_HEALTH, json.settings.stepSource)
    }

    @Test
    fun clearingCalendarQuestsKeepsCompletedAndManual() {
        val s = started().copy(
            tasks = listOf(
                QuestTask("a", "Manual", Difficulty.EASY),
                QuestTask("b", "Cal", Difficulty.EASY, TaskSource.CALENDAR),
                QuestTask("c", "Cal done", Difficulty.EASY, TaskSource.CALENDAR, completedAtMs = 5),
            ),
        )
        assertEquals(listOf("a", "c"), GameEngine.clearCalendarQuests(s).state.tasks.map { it.id })
    }

    @Test
    fun battleRecordsEventsAndTurnOrder() {
        val s = started()
        val b = GameEngine.newBattle(s, Monster.create(Bestiary.get("zapmouse"), 3, Random(2)), EncounterType.WILD, rng = Random(4))
        val order = b.turnOrder()
        assertEquals(b.current?.key, order.first())
        assertTrue(order.size >= 4)
        assertTrue(Combatant.ENEMY_KEY in order)
        repeat(6) { if (b.outcome == BattleOutcome.ONGOING) b.step(BattleAction.Attack) }
        // Hero sword attacks are slashes; every event is numbered in order.
        assertTrue(b.events.isNotEmpty())
        assertEquals(b.events.indices.toList(), b.events.map { it.seq })
        assertTrue(b.events.any { it.actorKey == Combatant.HERO_KEY && (it.style == StrikeStyle.SLASH || it.style == StrikeStyle.MISS) })
        b.events.filter { it.style == StrikeStyle.SLASH || it.style == StrikeStyle.BOLT }.forEach { assertTrue(it.amount > 0) }
        // Typed monster moves fly as bolts; Normal moves slash.
        b.events.filter { it.actorKey == Combatant.ENEMY_KEY && it.style != StrikeStyle.MISS && it.style != StrikeStyle.HEAL }.forEach {
            assertEquals(if (it.element == Element.NEUTRAL) StrikeStyle.SLASH else StrikeStyle.BOLT, it.style)
        }
    }

    @Test
    fun enemiesScaleWithTeamSize() {
        val rng0 = Random(1)
        val party = (1..3).map { Monster.create(Bestiary.get("emberkit"), 15, rng0) }
        val base = GameState(hero = Hero(level = 15), monsters = party, starterChosen = true)
        val foe = Monster.create(Bestiary.get("zapmouse"), 15, Random(2))
        fun battle(n: Int, type: EncounterType) =
            GameEngine.newBattle(base.copy(party = party.take(n - 1).map { it.uid }), foe, type, rng = Random(3), fullTeam = true)
        val hp = (1..4).map { battle(it, EncounterType.WILD).enemy.maxHp }
        assertEquals(hp.sorted(), hp)
        assertTrue(hp[3] > hp[0] * 5)
        assertEquals(4, battle(4, EncounterType.WILD).teamSize)
        // Bosses keep their full-team strength and get easier for smaller teams.
        val boss4 = battle(4, EncounterType.SILVER_BOSS).enemy
        assertEquals(Combatant.fromMonster(foe, Combatant.ENEMY_KEY, EncounterType.SILVER_BOSS.hpMultiplier).maxHp, boss4.maxHp)
        assertTrue(battle(2, EncounterType.SILVER_BOSS).enemy.maxHp < boss4.maxHp)
        // Rewards follow the enemy's strength: less alone, much more with a full team.
        assertTrue(TeamScaling.reward(1) < 1.0)
        assertTrue(TeamScaling.reward(4) > TeamScaling.reward(3) && TeamScaling.reward(3) > TeamScaling.reward(2))
        // Teams of 5 and 6 face tougher monsters for more, and bosses scale up past 4 too.
        assertTrue(TeamScaling.reward(6) > TeamScaling.reward(5) && TeamScaling.reward(5) > TeamScaling.reward(4))
        assertTrue(TeamScaling.hp(6) > TeamScaling.hp(5) && TeamScaling.hp(5) > TeamScaling.hp(4))
        assertEquals(1.0, TeamScaling.bossReward(3))
        assertTrue(TeamScaling.bossReward(6) > TeamScaling.bossReward(5) && TeamScaling.bossReward(5) > 1.0)
        fun xpFor(n: Int): Int {
            val b = battle(n, EncounterType.WILD)
            var guard = 0
            while (b.outcome == BattleOutcome.ONGOING && guard++ < 300) {
                if (b.current != null) b.step(BattleAction.Attack) else break
            }
            return if (b.outcome == BattleOutcome.WON) b.result().heroXp else -1
        }
        val solo = (1..4).map { xpFor(1) }.filter { it > 0 }
        val full = (1..4).map { xpFor(4) }.filter { it > 0 }
        if (solo.isNotEmpty() && full.isNotEmpty()) assertTrue(full.max() > solo.max() * 4)
    }

    @Test
    fun shrineVisitListsRewards() {
        val s = started()
        val shrine = Shrine("x", "Cafe", 0.0, 0.0, ShrineTier.ITEM)
        val u = GameEngine.visitShrine(s, shrine, 1_000_000L, Random(6))
        val gold = u.rewards.filterIsInstance<Reward.Gold>().single().amount
        assertEquals(s.gold + gold, u.state.gold)
        assertTrue(u.rewards.size >= 2)
        val items = u.rewards.filterIsInstance<Reward.Items>().sumOf { it.count }
        val gear = u.rewards.count { it is Reward.WeaponDrop || it is Reward.ArmorDrop }
        assertEquals(u.state.bagCount - s.bagCount, items + gear)
    }

    @Test
    fun dragMonstersIntoPartySlots() {
        val r = Random(4)
        val ms = (1..5).map { Monster.create(Bestiary.get("zapmouse"), 5, r) }
        var s = started().copy(monsters = ms, party = listOf(ms[0].uid, ms[1].uid))
        // Into an empty slot: appended.
        s = GameEngine.setPartySlot(s, ms[2].uid, 2).state
        assertEquals(listOf(ms[0].uid, ms[1].uid, ms[2].uid), s.party)
        // Onto an occupied slot: replaces it.
        s = GameEngine.setPartySlot(s, ms[3].uid, 0).state
        assertEquals(listOf(ms[3].uid, ms[1].uid, ms[2].uid), s.party)
        // A party member onto another slot: they swap.
        s = GameEngine.setPartySlot(s, ms[2].uid, 0).state
        assertEquals(listOf(ms[2].uid, ms[1].uid, ms[3].uid), s.party)
        assertEquals(3, s.party.size)
    }

    @Test
    fun restHealsEveryoneAndWakesFaintedMonsters() {
        val hour = 3_600_000L
        val t0 = 1_700_000_000_000L
        val base = started()
        val m = base.monsters.single()
        val max = m.stats.hp
        var s = base.copy(
            heroDamage = 50,
            monsters = listOf(m.copy(fainted = true, damage = max)),
        )
        // The first check only starts the clock.
        s = GameEngine.regenerate(s, t0).state
        assertEquals(t0, s.lastRegenMs)
        assertEquals(2, GameEngine.hoursUntilAwake(s.monsters.single()))
        // One hour: 5% back, still knocked out.
        s = GameEngine.regenerate(s, t0 + hour + 10).state
        assertTrue(s.monsters.single().fainted)
        assertEquals(1, GameEngine.hoursUntilAwake(s.monsters.single()))
        // Two hours: back to 10%, awake.
        val woke = GameEngine.regenerate(s, t0 + 2 * hour + 10)
        assertTrue(!woke.state.monsters.single().fainted)
        assertTrue(woke.message.contains("back on its feet"))
        // The hero heals too, and partial hours carry over.
        assertTrue(woke.state.heroDamage < 50)
        assertEquals(t0 + 2 * hour, woke.state.lastRegenMs)
        // Old saves stored knocked-out monsters with 0 damage: treated as empty, not full.
        val legacy = base.copy(monsters = listOf(m.copy(fainted = true, damage = 0)), lastRegenMs = t0)
        assertTrue(GameEngine.regenerate(legacy, t0 + hour).state.monsters.single().fainted)
    }

    @Test
    fun startersArePseudoLegendary() {
        // Monsters fight with HP, Attack (their better of Attack/Magic), Defense and Speed.
        fun total(id: String) = Bestiary.get(id).base.let { it.hp + maxOf(it.attack, it.magic) + it.defense + it.speed }
        val finals = listOf("thunderhorn", "infernodrake", "maelstrom_serpent", "terraursa")
        val otherFinals = Bestiary.all.filter { !it.bossOnly && it.evolvesTo == null && it.stages > 1 && it.id !in finals }
        val legendaries = Bestiary.all.filter { it.bossOnly }
        finals.forEach { f ->
            assertTrue(otherFinals.all { total(f) > total(it.id) }, "$f should beat every other line")
            assertTrue(total(f) < legendaries.minOf { total(it.id) }, "$f should stay below every legendary")
            assertTrue(Bestiary.get(f).rarity != Rarity.LEGENDARY)
        }
        // Mythics are as strong as a final evolution, but below starters and legendaries.
        val mythics = listOf("mimic", "prism_fairy")
        val avgFinal = otherFinals.map { total(it.id) }.average()
        mythics.forEach { m ->
            assertTrue(total(m) >= avgFinal - 5, "$m ${total(m)} vs finals $avgFinal")
            assertTrue(finals.all { total(m) < total(it) }, "$m below the starters")
            assertTrue(total(m) < legendaries.minOf { total(it.id) })
            assertEquals(Rarity.EPIC, Bestiary.get(m).rarity)
        }
        // Starting the game gives a starter, which evolves at 16 and 36.
        val s = GameEngine.chooseStarter(GameState(), "cindrake", Random(1)).state
        assertEquals("cindrake", s.monsters.single().speciesId)
        assertEquals("blazewyrm", Monster.create(Bestiary.get("cindrake"), 16, Random(1)).speciesId)
        assertEquals("infernodrake", Monster.create(Bestiary.get("cindrake"), 36, Random(1)).speciesId)
    }

    @Test
    fun shiniesAreRareButAchievable() {
        val rng = Random(5)
        val rolls = 200_000
        val shinies = (1..rolls).count { rollShiny(rng) }
        val rate = shinies.toDouble() / rolls
        assertTrue(rate in 0.0035..0.0065, "rate $rate")
        // The journal records shinies, and they stay shiny through a save.
        val m = Monster.create(Bestiary.get("emberkit"), 10, rng, shiny = true)
        val s = started().let { it.copy(monsters = it.monsters + m) }.withJournal()
        assertTrue(m.speciesId in s.shinyCaught)
        assertTrue(GameState.fromJson(s.toJson()).monsters.any { it.shiny })
    }

    @Test
    fun existingPlayersGetToPickANewStarter() {
        // A save from before 0.5 with an old starter.
        val old = GameEngine.chooseStarter(GameState(), "emberkit", Random(1)).state.copy(version = 5, hero = Hero(level = 22))
        val loaded = GameState.fromJson(old.toJson())
        assertTrue(loaded.starterGiftPending)
        val u = GameEngine.claimStarterGift(loaded, "drizzlet", Random(2))
        val gift = u.state.monsters.last()
        assertEquals("drizzlet", gift.speciesId)
        assertEquals(15, gift.level)
        assertTrue(!u.state.starterGiftPending)
        assertTrue(gift.uid in u.state.party)
        // Only once.
        assertEquals(u.state, GameEngine.claimStarterGift(u.state, "cindrake", Random(3)).state)
        // New players (who already chose a new starter) and brand-new saves aren't offered one.
        val fresh = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state
        assertTrue(!GameState.fromJson(fresh.copy(version = 5).toJson()).starterGiftPending)
        assertTrue(!GameState.fromJson(GameState().toJson()).starterGiftPending)
    }

    @Test
    fun goldShrinesKeepTheirGuardians() {
        val shrines = (0 until 2_000).map { World.shrineFromPlace("node$it", "Place", 0.0, 0.0, ShrineTier.GOLD) }
        val guardians = shrines.map { it.bossSpeciesId!! }
        val original = Bestiary.bosses.filter { it.id != "worldroot_colossus" }
        assertEquals(12, original.size)
        // Every shrine either kept its pre-Earth guardian or now holds the Worldroot Colossus.
        shrines.forEach { sh ->
            val h = kotlin.math.abs(sh.id.hashCode() % 1_000_000)
            assertTrue(sh.bossSpeciesId == original[h % 12].id || sh.bossSpeciesId == "worldroot_colossus")
        }
        val share = guardians.count { it == "worldroot_colossus" }.toDouble() / shrines.size
        assertTrue(share in 0.04..0.12, "share $share")
    }

    @Test
    fun chosenCalendarsAreSavedAndUnpickedQuestsDrop() {
        val s = started().copy(settings = Settings(syncAllCalendars = false, syncCalendars = setOf(3L, 7L)))
        val loaded = GameState.fromJson(s.toJson())
        assertEquals(setOf(3L, 7L), loaded.settings.syncCalendars)
        assertTrue(!loaded.settings.syncAllCalendars)
        assertTrue(GameState().settings.syncAllCalendars)
        // Re-syncing with only the still-chosen calendars' events drops the others' unfinished quests.
        val work = CalendarEvent("cal:1:0", "Standup", 0, 1, false, "Work")
        val home = CalendarEvent("cal:2:0", "Dentist", 0, 1, false, "Home")
        val both = GameEngine.syncCalendar(s, listOf(work, home)).state
        assertEquals(2, both.tasks.count { it.source == TaskSource.CALENDAR })
        val workOnly = GameEngine.syncCalendar(both, listOf(work)).state
        assertEquals(listOf("Standup"), workOnly.tasks.filter { it.source == TaskSource.CALENDAR }.map { it.title })
    }

    @Test
    fun compassAndUnitsSettingSaved() {
        assertEquals("N", World.compass(World.bearing(40.0, -75.0, 40.01, -75.0)))
        assertEquals("E", World.compass(World.bearing(40.0, -75.0, 40.0, -74.99)))
        assertEquals("SW", World.compass(World.bearing(40.0, -75.0, 39.99, -75.013)))
        assertTrue(Settings().imperialUnits)
        val s = started().copy(settings = Settings(imperialUnits = false))
        assertTrue(!GameState.fromJson(s.toJson()).settings.imperialUnits)
    }

    @Test
    fun healingSpellHealsWhatThePreviewSays() {
        val s0 = started()
        val s = s0.copy(hero = s0.hero.copy(level = 20, knownSpells = s0.hero.knownSpells + "mend"))
        val foe = Monster.create(Bestiary.get("zapmouse"), 5, Random(6))
        val b = GameEngine.newBattle(s, foe, EncounterType.WILD, rng = Random(7), fullTeam = true)
        var guard = 0
        while (b.current?.isHero != true && guard++ < 20) b.step(BattleAction.Defend)
        val hero = b.current!!
        val mend = b.spellsFor(hero).first { it.id == "mend" }
        hero.hp = 1
        hero.mp = hero.maxMp
        val expected = minOf(hero.maxHp, 1 + b.spellHeal(hero, mend))
        b.step(BattleAction.CastSpell("mend", hero.key))
        assertEquals(expected, hero.hp)
    }

    @Test
    fun batchReleaseKeepsOneAndPaysCandy() {
        val s0 = started()
        val extra = (1..4).map { Monster.create(Bestiary.get("zapmouse"), 5, Random(it)) }
        val s = s0.copy(monsters = s0.monsters + extra)
        val u = GameEngine.releaseMany(s, extra.map { it.uid })
        assertEquals(s0.monsters.size, u.state.monsters.size)
        val family = Bestiary.get("zapmouse").family
        assertEquals(extra.sumOf { GameEngine.releaseCandy(it) }, u.state.candy[family])
        assertTrue("Released 4" in u.message)
        // Never the last monster.
        val all = GameEngine.releaseMany(s, s.monsters.map { it.uid }).state
        assertEquals(1, all.monsters.size)
    }
}
