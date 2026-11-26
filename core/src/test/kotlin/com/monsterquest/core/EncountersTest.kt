package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EncountersTest {
    private val rng = Random(11)

    private fun withMonsters(n: Int, level: Int = 10): GameState {
        val ms = List(n) { Monster.create(Bestiary.get("emberkit"), level, Random(it)) }
        return GameState(hero = Hero(level = level), monsters = ms, party = ms.take(MAX_PARTY_SIZE).map { it.uid }, starterChosen = true)
    }

    @Test
    fun everyTypeHasAGuardMove() {
        for (type in Element.typed) {
            val guards = Moves.learnsets.getValue(type).map { Moves.get(it.second) }.filter { MoveEffect.Protect in it.effects && it.type == type }
            assertEquals(1, guards.size, "$type should learn one guard move")
        }
        // Every monster has its guard by level 12.
        assertTrue(Bestiary.all.all { sp -> Moves.learned(sp, 12).any { MoveEffect.Protect in it.effects } })
    }

    @Test
    fun batterySaverMakesSurpriseAttacksLikelier() {
        fun hits(saver: Boolean): Int {
            val r = Random(5)
            return (1..2000).count { SecretEncounters.roll(0, SecretEncounters.STEPS, r, saver).second }
        }
        assertTrue(hits(true) > hits(false) * 1.3)
    }

    @Test
    fun thugsMatchYourTeamAndFightAlongside() {
        val s = withMonsters(4)
        val count = GameEngine.fightingCount(s)
        assertEquals(4, count)
        val (thug, crew) = Villains.ambusher(s.hero.level, count, rng)
        assertEquals(4, crew.size)
        val b = GameEngine.newBattle(s, crew.first(), EncounterType.THUG, rng = Random(2), pack = crew.drop(1), thug = thug)
        // Four monsters plus the thug against your hero plus four monsters.
        assertEquals(5, b.enemies.size)
        assertEquals(b.allies.size, b.enemies.size)
        assertTrue(b.enemies.last().thug)
        assertNotNull(b.captureBlockedReason())
        assertFalse(b.isBoss)
        // No extra scaling: the crew fights at its own strength.
        assertEquals(Combatant.fromMonster(crew[1], "x").maxHp, b.enemies[1].maxHp)
        // Targeting the thug doesn't break the taming odds shown in the menu.
        b.selectTarget(b.enemies.last().key)
        b.captureOdds(Item.BINDING_CRYSTAL)
        assertTrue(b.log.first().contains(thug.name))
    }

    @Test
    fun swapSlotsSendMonstersInMidBattle() {
        var s = withMonsters(7)
        val extra = s.monsters.drop(5)
        s = GameEngine.setSwapSlot(s, extra[0].uid, 0).state
        s = GameEngine.setSwapSlot(s, extra[1].uid, 1).state
        assertEquals(extra.map { it.uid }, s.swaps)
        // Swap-slot monsters stay home from adventures.
        assertTrue(Adventures.available(s).none { it.uid in s.swaps })

        val foe = Monster.create(Bestiary.get("emberkit"), 10, rng)
        val b = GameEngine.newBattle(s, foe, EncounterType.WILD, rng = Random(4), fullTeam = true)
        assertEquals(6, b.allies.size)
        assertEquals(2, b.swapsWaiting.size)
        // Swap in on the first ally turn, in place of a party monster.
        val out = b.allies[1].key
        b.step(BattleAction.Swap(extra[0].uid, out))
        assertTrue(b.allies.any { it.key == extra[0].uid })
        assertTrue(b.swapsWaiting.any { it.key == out })
        assertTrue(b.log.any { "Go, " in it })

        // Win: the one who stepped in gets full XP, the one who waited gets half.
        b.enemies.forEach { it.hp = 0 }
        while (b.outcome == BattleOutcome.ONGOING && b.current != null) b.step(BattleAction.Defend)
        val r = b.result()
        val full = r.monsterXp.getValue(extra[0].uid)
        assertEquals(full / 2, r.monsterXp.getValue(extra[1].uid))
    }

    @Test
    fun yourSideMatchesTheirsOutsideDens() {
        val s = withMonsters(7)
        val foe = Monster.create(Bestiary.get("emberkit"), 10, rng)
        // One wild monster: the hero and one monster (never fewer); the rest wait on the bench.
        val one = GameEngine.newBattle(s, foe, EncounterType.WILD, rng = Random(4))
        assertEquals(MIN_SIDE, one.allies.size)
        assertTrue(one.allies.first().isHero)
        assertEquals(s.party.size - 1, one.swapsWaiting.count { it.key in s.party })
        // A pack of 3: the hero and 2 monsters.
        val pack = (1..2).map { Monster.create(Bestiary.get("emberkit"), 10, rng) }
        val three = GameEngine.newBattle(s, foe, EncounterType.WILD, rng = Random(4), pack = pack)
        assertEquals(3, three.allies.size)
        // Bosses and villains match too; dens bring the whole team.
        assertEquals(MIN_SIDE, GameEngine.newBattle(s, foe, EncounterType.BOSS_CHALLENGE, rng = Random(4)).allies.size)
        assertEquals(1 + s.party.size, GameEngine.newBattle(s, foe, EncounterType.DEN, rng = Random(4)).allies.size)
    }

    @Test
    fun benchedPartyMonstersStillEarnXp() {
        val s = withMonsters(4)
        val foe = Monster.create(Bestiary.get("emberkit"), 10, rng)
        val b = GameEngine.newBattle(s, foe, EncounterType.WILD, rng = Random(4))
        b.enemies.forEach { it.hp = 0 }
        while (b.outcome == BattleOutcome.ONGOING && b.current != null) b.step(BattleAction.Defend)
        val xp = b.result().monsterXp
        // The whole party sat on the bench in a one-on-one win: each gets half, like a swap slot.
        assertEquals(s.party.toSet(), xp.keys)
        assertTrue(xp.values.all { it > 0 })
    }

    @Test
    fun theHeroCanStepBackAndAMonsterFights() {
        val s = withMonsters(3).let { it.copy(hero = it.hero.copy(level = 60)) }
        val foe = Monster.create(Bestiary.get("emberkit"), 10, rng)
        val b = GameEngine.newBattle(s, foe, EncounterType.WILD, rng = Random(4))
        val sub = s.party[1]
        b.step(BattleAction.Swap(sub, Combatant.HERO_KEY))
        assertEquals(listOf(sub, s.party.first()), b.allies.map { it.key })
        assertTrue(b.swapsWaiting.any { it.isHero })
        assertTrue(b.log.any { "steps back" in it })
        // Both monsters fall: not a loss, the player picks who steps in (here, the hero).
        b.allies.forEach { it.hp = 0 }
        val fallen = b.allies.first()
        assertTrue(b.mustSwap)
        assertTrue(b.replaceFainted(Combatant.HERO_KEY, fallen.key))
        assertEquals(BattleOutcome.ONGOING, b.outcome, b.log.joinToString("\n"))
        assertTrue(b.allies.first().isHero)
        assertTrue(b.log.any { "takes ${fallen.name}'s place" in it })
    }

    @Test
    fun movingSwapAndPartyMonstersAround() {
        var s = withMonsters(7)
        val a = s.monsters[5].uid
        val p0 = s.party[0]
        s = GameEngine.setSwapSlot(s, a, 0).state
        // Drag the swap monster onto party slot 0: they trade places.
        s = GameEngine.setPartySlot(s, a, 0).state
        assertEquals(a, s.party[0])
        assertEquals(listOf(p0), s.swaps)
        // Toggling takes it out of the swap slot.
        s = GameEngine.toggleParty(s, p0).state
        assertTrue(s.swaps.isEmpty())
        // Releasing clears it everywhere.
        s = GameEngine.setSwapSlot(s, p0, 0).state
        s = GameEngine.release(s, p0).state
        assertTrue(p0 !in s.swaps)
    }

    @Test
    fun speedGuardPausesSpawnsAboveFifteenMph() {
        var g = SpeedGuard.State()
        g = SpeedGuard.update(g, 40.0, -74.0, 0)
        // ~30 mph by GPS.
        g = SpeedGuard.update(g, 40.001, -74.0, 5_000, gpsSpeedMps = 13.4f)
        assertTrue(g.tooFast)
        assertTrue(g.justSpedUp)
        assertTrue(SpeedGuard.paused(g, 5_000))
        g = SpeedGuard.update(g, 40.002, -74.0, 10_000, gpsSpeedMps = 13.4f)
        assertFalse(g.justSpedUp)
        // Walking pace again: paused for another minute, then spawns return.
        g = SpeedGuard.update(g, 40.002, -74.0, 20_000, gpsSpeedMps = 1.4f)
        assertTrue(SpeedGuard.paused(g, 20_000))
        g = SpeedGuard.update(g, 40.0021, -74.0, 20_000 + SpeedGuard.RESUME_MS, gpsSpeedMps = 1.4f)
        assertFalse(SpeedGuard.paused(g, 20_000 + SpeedGuard.RESUME_MS))

        // Without GPS speed it's worked out from the distance: 100 m in 5 s is ~45 mph.
        var h = SpeedGuard.update(SpeedGuard.State(), 40.0, -74.0, 0)
        h = SpeedGuard.update(h, 40.0009, -74.0, 5_000)
        assertTrue(h.tooFast)
        // Standing still sends no fixes: spawns come back after a few minutes anyway.
        assertFalse(SpeedGuard.paused(h, 5_000 + SpeedGuard.STILL_MS))
        // Walking (5 m in 4 s) never trips it.
        var w = SpeedGuard.update(SpeedGuard.State(), 40.0, -74.0, 0)
        w = SpeedGuard.update(w, 40.000045, -74.0, 4_000)
        assertFalse(w.tooFast)
    }

    @Test
    fun smackDownGroundsFliersAndMostMonstersLearnIt() {
        val sd = Moves.get("smack_down")
        assertEquals(Element.EARTH, sd.type)
        assertEquals(65, sd.power)
        // Most monsters learn it; fish, slimes, wisps and the fairy don't.
        assertTrue(Moves.learned(Bestiary.get("mosscub"), 20).contains(sd))
        assertTrue(Moves.learned(Bestiary.get("cindrake"), 24).contains(sd))
        for (id in listOf("bubblefin", "goopling", "gloomwisp", "prism_fairy")) assertFalse(Moves.learned(Bestiary.get(id), 100).contains(sd), id)
        val learners = Bestiary.all.count { Moves.canLearnSmackDown(it) }
        assertTrue(learners > Bestiary.all.size * 3 / 4)

        // Against an airborne bird: it connects, and afterwards Earth moves land.
        val bird = Monster.create(Bestiary.get("tempest_roc"), 40, rng).copy(skill = Trait.AIRBORNE)
        val bear = Monster.create(Bestiary.get("terraursa"), 40, rng).copy(moves = listOf("smack_down"))
        val b = Battle(Hero(level = 40), listOf(bear), bird, EncounterType.WILD, emptyMap(), false, rng = Random(12), fullTeam = true)
        assertTrue(b.enemy.immuneEarth)
        var tries = 0
        while (b.enemy.immuneEarth && b.outcome == BattleOutcome.ONGOING && tries++ < 20) {
            val c = b.current ?: break
            if (c.key == bear.uid) b.step(BattleAction.UseMove("smack_down")) else b.step(BattleAction.Defend)
        }
        assertFalse(b.enemy.immuneEarth)
        assertTrue(b.enemy.grounded)
        assertTrue(b.log.any { "knocked out of the air" in it })
    }

    @Test
    fun maxedHoverLiftsTheTeamUntilTheHeroIsHit() {
        val s = withMonsters(2, level = 70)
        val hero = s.hero.copy(level = 70, skills = listOf(Trait.HOVER))
        assertEquals(5, hero.rankOf(Trait.HOVER))
        val foe = Monster.create(Bestiary.get("goopling"), 70, rng) // no Knockdown: only a hit on the hero ends the hover
        val b = GameEngine.newBattle(s.copy(hero = hero), foe, EncounterType.WILD, rng = Random(3), fullTeam = true)
        // Everyone hovers (unless a faster foe already hit the hero before your first turn).
        assertTrue(b.allies.all { it.immuneEarth } || b.log.any { "hover breaks" in it })
        assertTrue(b.allies.first().immuneEarth)
        // Rank 4 only softens Earth damage for the team.
        val r4 = GameEngine.newBattle(s.copy(hero = hero.copy(level = 69)), foe, EncounterType.WILD, rng = Random(3), fullTeam = true)
        assertFalse(r4.allies[1].immuneEarth)
        assertEquals(50, r4.allies[1].earthResistPct)
        // Fight until the hero takes a hit: the team drops, the hero keeps floating.
        var tries = 0
        while (b.allies.drop(1).any { it.immuneEarth } && b.outcome == BattleOutcome.ONGOING && tries++ < 60) b.step(BattleAction.Defend)
        if (b.outcome == BattleOutcome.ONGOING) {
            assertTrue(b.allies.first().immuneEarth)
            assertTrue(b.allies.drop(1).none { it.immuneEarth })
            assertTrue(b.log.any { "hover breaks" in it })
        }
    }

    @Test
    fun energyCarriesOverAndRecoversWithTimeAndWalking() {
        var s = withMonsters(1).copy(inventory = mapOf(Item.MANA_DRAUGHT to 1))
        val max = s.hero.maxMp
        // Cast a spell, then win: the Energy spent stays spent.
        val foe = Monster.create(Bestiary.get("emberkit"), 3, rng)
        val b = GameEngine.newBattle(s, foe, EncounterType.WILD, rng = Random(1))
        while (b.current?.isHero != true && b.outcome == BattleOutcome.ONGOING) b.step(BattleAction.Defend)
        b.step(BattleAction.CastSpell("ember_bolt"))
        b.enemies.forEach { it.hp = 0 }
        while (b.outcome == BattleOutcome.ONGOING && b.current != null) b.step(BattleAction.Defend)
        s = GameEngine.applyBattleResult(s, b.result(), 0).state
        assertTrue(s.heroEnergyUsed > 0)
        val used = s.heroEnergyUsed
        // The next fight starts with that much less.
        val next = GameEngine.newBattle(s, foe, EncounterType.WILD, rng = Random(1))
        assertEquals(max - used, next.allies.first().mp)

        // Over time: all of it back in 6 hours (a little at a time).
        var t = GameEngine.regenEnergy(s.copy(energyRegenMs = 1_000), 1_000)
        t = GameEngine.regenEnergy(t, 1_000 + GameEngine.ENERGY_FULL_MS / max * 2)
        assertEquals(used - 2, t.heroEnergyUsed)
        t = GameEngine.regenEnergy(t, 1_000 + GameEngine.ENERGY_FULL_MS)
        assertEquals(0, t.heroEnergyUsed)

        // Walking is faster: 2 Energy every 100 steps.
        val walked = GameEngine.walk(s, 100).state
        assertEquals(used - GameEngine.ENERGY_PER_REWARD, walked.heroEnergyUsed)

        // A Mana Draught works outside battle too.
        val draught = GameEngine.usePotion(s, Item.MANA_DRAUGHT, Combatant.HERO_KEY).state
        assertEquals(0, draught.heroEnergyUsed)
        assertEquals(0, draught.count(Item.MANA_DRAUGHT))
    }

    @Test
    fun thugsFightTheirOwnWay() {
        val brute = Combatant.fromThug(Thug("Brick", "Rats", 20, HeroClass.WARRIOR), "e")
        val hexer = Combatant.fromThug(Thug("Vex", "Rats", 20, HeroClass.MAGE), "e")
        val cut = Combatant.fromThug(Thug("Sly", "Rats", 20, HeroClass.ROGUE), "e")
        assertTrue(brute.maxHp > cut.maxHp && cut.maxHp > hexer.maxHp)
        assertTrue(hexer.speed > brute.speed && cut.speed > hexer.speed)
        assertEquals(Element.CORRUPT, hexer.element)
        assertTrue(cut.evasion > 0)
        assertEquals("Brick the Brute", brute.name)
        assertEquals(HeroClass.MAGE, hexer.heroClass)
    }

    @Test
    fun mudShotStacksAccuracyDownAndRanksUp() {
        val mud = Spells.byId(Spells.MUD_SLING)!!
        assertEquals(Element.EARTH, mud.element)
        // Weak: well under a bolt, and it ranks up like the others.
        assertTrue(mud.power < Spells.byId("stone_bolt")!!.power)
        assertTrue(Spells.ranked(mud, 5, 25).power > Spells.ranked(mud, 1, 25).power)
        val hero = Hero(level = 20, knownSpells = Spells.starting + Spells.MUD_SLING, attributes = HeroAttribute.entries.associateWith { 30 })
        val foe = Monster.create(Bestiary.get("goopling"), 30, rng)
        val b = Battle(hero, emptyList(), foe, EncounterType.WILD, emptyMap(), false, rng = Random(4))
        var casts = 0
        while (b.outcome == BattleOutcome.ONGOING && casts < 8) {
            val c = b.current ?: break
            c.mp = c.maxMp
            c.hp = c.maxHp
            b.enemy.hp = b.enemy.maxHp
            b.step(BattleAction.CastSpell(Spells.MUD_SLING))
            casts++
        }
        val muddied = b.enemy.muddiedPct
        assertTrue(muddied >= 20, "stacks: $muddied")
        assertTrue(muddied <= Spells.MUD_SLING_ACCURACY * Spells.MUD_SLING_STACKS)
        assertTrue(b.enemy.tags().any { "Acc" in it })
    }

    @Test
    fun questRewardsResistFakeQuests() {
        val t0 = 1_700_000_000_000L
        val later = t0 + QuestRewards.MIN_AGE_MS
        fun add(st: GameState, title: String, d: Difficulty = Difficulty.NORMAL, at: Long = t0) = GameEngine.addTask(st, title, d, rng, nowMs = at).state
        fun done(st: GameState, title: String, at: Long = later) = GameEngine.completeTask(st, st.tasks.last { it.title == title && !it.completed }.id, at)

        // 1. Added and ticked straight away: done, but no reward.
        var s = add(withMonsters(1), "asdf")
        val quick = done(s, "asdf", t0 + 60_000)
        assertEquals(s.gold, quick.state.gold)
        assertEquals(s.huntTokens, quick.state.huntTokens)
        assertTrue("No reward" in quick.message)
        assertTrue(quick.state.tasks.single().completed)

        // 2. Eight full, four half, then nothing.
        s = withMonsters(1).copy(huntTokens = 0)
        val golds = (1..13).map { i ->
            s = add(s, "Chore $i")
            val before = s.gold
            s = done(s, "Chore $i").state
            s.gold - before
        }
        assertEquals(List(8) { 15 } + List(4) { 7 } + listOf(0), golds)
        assertEquals(0, QuestRewards.status(s, later).fullLeft)
        // A new day starts fresh.
        val tomorrow = later + 24 * 3_600_000L
        s = add(s, "Fresh", at = tomorrow - QuestRewards.MIN_AGE_MS)
        val before = s.gold
        s = done(s, "Fresh", tomorrow).state
        assertEquals(15, s.gold - before)

        // 3. Only three Hard quests a day pay the Hard rate.
        s = withMonsters(1)
        val hard = (1..4).map { i ->
            s = add(s, "Big $i", Difficulty.HARD)
            val g = s.gold
            s = done(s, "Big $i").state
            s.gold - g
        }
        assertEquals(listOf(30, 30, 30, 15), hard)

        // 4. The same quest again the same day pays half, even if deleted and re-added.
        s = withMonsters(1)
        s = add(s, "Do dishes")
        s = done(s, "Do dishes").state
        s = GameEngine.deleteTask(s, s.tasks.single().id).state
        s = add(s, "  do   DISHES ")
        val g = s.gold
        val again = done(s, "do   DISHES")
        assertEquals(7, again.state.gold - g)
        assertTrue("already did" in again.message)

        // Calendar quests pay once they've started.
        val ev = CalendarEvent("e1", "Meeting", t0 + 3_600_000, t0 + 7_200_000, false, null)
        s = GameEngine.syncCalendar(withMonsters(1), listOf(ev)).state
        assertEquals(s.gold, GameEngine.completeTask(s, "e1", t0).state.gold)
        assertTrue(GameEngine.completeTask(s, "e1", t0 + 3_600_000).state.gold > s.gold)
    }

    @Test
    fun orbsAreLockedByLevelAndThePerfectOrbAlwaysWorks() {
        assertEquals(listOf(0, 10, 25, 50, 75), listOf(Item.BINDING_CRYSTAL, Item.RESONANT_CRYSTAL, Item.RADIANT_CRYSTAL, Item.PRISMATIC_CRYSTAL, Item.PERFECT_ORB).map { it.minLevel })
        assertEquals(2_000, Item.PERFECT_ORB.price)
        val rich = withMonsters(1).copy(gold = 10_000)
        assertEquals(0, GameEngine.buy(rich.copy(hero = rich.hero.copy(level = 9)), Item.RESONANT_CRYSTAL).state.count(Item.RESONANT_CRYSTAL))
        assertEquals(1, GameEngine.buy(rich.copy(hero = rich.hero.copy(level = 10)), Item.RESONANT_CRYSTAL).state.count(Item.RESONANT_CRYSTAL))
        assertEquals(0, GameEngine.buy(rich.copy(hero = rich.hero.copy(level = 74)), Item.PERFECT_ORB).state.count(Item.PERFECT_ORB))
        assertEquals(1, GameEngine.buy(rich.copy(hero = rich.hero.copy(level = 75)), Item.PERFECT_ORB).state.count(Item.PERFECT_ORB))
        // Better orbs, better odds.
        val odds = listOf(Item.BINDING_CRYSTAL, Item.RESONANT_CRYSTAL, Item.RADIANT_CRYSTAL, Item.PRISMATIC_CRYSTAL).map { captureChance(0.3, Rarity.RARE, 0, it) }
        assertEquals(odds.sorted(), odds)

        // A Perfect Orb tames even a monster at full health.
        val foe = Monster.create(Bestiary.get("emberkit"), 10, rng)
        val b = Battle(Hero(level = 80), emptyList(), foe, EncounterType.WILD, mapOf(Item.PERFECT_ORB to 1, Item.BINDING_CRYSTAL to 3), false, rng = Random(2))
        assertNull(b.captureBlockedReason())
        assertEquals(listOf(Item.PERFECT_ORB), b.orbs())
        assertEquals(1.0, b.captureOdds(Item.PERFECT_ORB))
        while (b.current?.isHero != true && b.outcome == BattleOutcome.ONGOING) b.step(BattleAction.Defend)
        b.step(BattleAction.Capture(Item.PERFECT_ORB))
        assertEquals(BattleOutcome.CAPTURED, b.outcome)
    }

    @Test
    fun wildMonstersFightWithRandomMovesFromTheSamePool() {
        val sp = Bestiary.get("emberkit")
        val known = Moves.learned(sp, 40).map { it.id }.toSet()
        val loadouts = (1..30).map { Moves.wildLoadout(sp, 40, Random(it)) }
        loadouts.forEach { l ->
            assertEquals(4, l.size)
            assertEquals(4, l.map { it.id }.toSet().size)
            assertTrue(l.all { it.id in known })
            assertTrue(l.count { it.isDamaging } >= 2)
        }
        // Not always the same four.
        assertTrue(loadouts.map { l -> l.map { it.id }.toSet() }.toSet().size > 5)
        // A wild monster keeps the moves it was given.
        val m = Monster.create(sp, 40, Random(3)).withWildMoves(Random(3))
        assertTrue(m.activeMoves.map { it.id }.toSet() == m.moves!!.toSet())
    }

    @Test
    fun movesHaveUsesThatComeBackWhenRested() {
        assertEquals(MoveRole.STATUS, Moves.get("spore_cloud").role)
        assertEquals(MoveRole.STATUS, Moves.get("sleep_wave").role)
        assertEquals(MoveRole.HIGH_RISK, Moves.get("blast_burn").role)
        assertEquals(MoveRole.HIGH_RISK, Moves.get("wildfire_charge").role)
        assertEquals(MoveRole.RELIABLE, Moves.get("flame_torrent").role)
        assertEquals(MoveRole.BASIC, Moves.get("shoulder_rush").role)
        assertEquals(MoveRole.SUPPORT, Moves.get("roost").role)
        // Every move lands in one role, and roles set the uses.
        assertTrue(Moves.all.all { it.maxPp == it.role.pp })

        val m = Monster.create(Bestiary.get("goopling"), 30, Random(3)).copy(moves = listOf("spore_cloud", "shoulder_rush"))
        val s = GameEngine.chooseStarter(GameState(), "emberkit", Random(1)).state.let { it.copy(monsters = listOf(m), party = listOf(m.uid)) }
        val foe = Monster.create(Bestiary.get("emberkit"), 30, Random(4))
        val b = GameEngine.newBattle(s, foe, EncounterType.WILD, rng = Random(5), fullTeam = true)
        val me = b.allies.first { it.key == m.uid }
        val spore = Moves.get("spore_cloud")
        fun myTurn() { var g = 0; while (b.current?.key != m.uid && g++ < 30 && b.outcome == BattleOutcome.ONGOING) { b.allies.forEach { it.hp = it.maxHp }; b.enemies.forEach { it.hp = it.maxHp }; b.step(BattleAction.Defend) } }
        repeat(3) { myTurn(); b.step(BattleAction.UseMove("spore_cloud")) }
        assertEquals(0, me.ppLeft(spore))
        // Out of uses: it can't be picked...
        myTurn()
        assertTrue(spore !in me.usableMoves)
        // ...until a turn spent on something else gives one back.
        b.step(BattleAction.UseMove("shoulder_rush"))
        assertEquals(1, me.ppLeft(spore))
        // Out of everything: Struggle.
        me.pp.keys.forEach { me.pp[it] = 0 }
        assertEquals(listOf(Moves.STRUGGLE), me.usableMoves)
    }

    @Test
    fun classicMoveCostsWork() {
        fun duel(move: String, hpFrac: Double = 1.0): Pair<Battle, Combatant> {
            val m = Monster.create(Bestiary.get("blazecat"), 50, Random(3)).copy(moves = listOf(move))
            val s = GameEngine.chooseStarter(GameState(), "emberkit", Random(1)).state.let { it.copy(monsters = listOf(m), party = listOf(m.uid)) }
            val foe = Monster.create(Bestiary.get("goopling"), 50, Random(4))
            val b = GameEngine.newBattle(s, foe, EncounterType.WILD, rng = Random(5), fullTeam = true)
            val me = b.allies.first { it.key == m.uid }
            var g = 0
            while (b.current?.key != m.uid && g++ < 30 && b.outcome == BattleOutcome.ONGOING) b.step(BattleAction.Defend)
            me.hp = (me.maxHp * hpFrac).toInt().coerceAtLeast(1)
            b.enemy.hp = b.enemy.maxHp
            return b to me
        }
        // Meltdown: a huge hit, and the user's Attack harshly falls (unless it missed).
        val (melt, hot) = duel("meltdown")
        melt.act(BattleAction.UseMove("meltdown"))
        assertTrue(hot.stages[BattleStat.ATTACK] == -2 || melt.log.any { "miss" in it }, melt.log.takeLast(5).toString())
        // Wildfire Charge: recoil is a third of the damage dealt.
        val (blitz, _) = duel("wildfire_charge")
        blitz.act(BattleAction.UseMove("wildfire_charge"))
        val hit = blitz.log.firstNotNullOfOrNull { Regex("It hits .* for (\\d+) damage").find(it)?.groupValues?.get(1)?.toInt() }
        val recoil = blitz.log.firstNotNullOfOrNull { Regex("hurt by recoil \\((\\d+)\\)").find(it)?.groupValues?.get(1)?.toInt() }
        if (hit != null) assertEquals(maxOf(1, hit * 33 / 100), recoil, blitz.log.takeLast(6).toString())
        // Supernova weakens with the user's HP.
        fun novaHit(frac: Double): Int? {
            val (b, _) = duel("supernova", frac)
            b.step(BattleAction.UseMove("supernova"))
            return b.log.firstNotNullOfOrNull { Regex("It hits .* for (\\d+) damage").find(it)?.groupValues?.get(1)?.toInt() }
        }
        val full = novaHit(1.0)
        val tired = novaHit(0.2)
        if (full != null && tired != null) assertTrue(full > tired, "full $full vs tired $tired")
    }

    @Test
    fun tamingOneOfAPackMakesTheWeakerRunAndTheStrongerRage() {
        val r = Random(5)
        val lead = Monster.create(Bestiary.get("emberkit"), 10, r)
        val weak = Monster.create(Bestiary.get("emberkit"), 4, r)
        val strong = Monster.create(Bestiary.get("emberkit"), 16, r)
        val b = Battle(Hero(level = 80), emptyList(), lead, EncounterType.WILD, mapOf(Item.PERFECT_ORB to 3), false, rng = Random(3), extraEnemies = listOf(weak, strong))
        fun heroTurn() { while (b.current?.isHero != true && b.outcome == BattleOutcome.ONGOING) b.step(BattleAction.Defend) }
        heroTurn()
        b.selectTarget(b.enemies[0].key)
        b.step(BattleAction.Capture(Item.PERFECT_ORB))
        // The fight goes on: the stronger one rages (+1 to every stat), the weaker one ran or stood firm.
        assertEquals(BattleOutcome.ONGOING, b.outcome)
        val rager = b.enemies[2]
        assertEquals(1, rager.rage)
        assertTrue(BattleStat.entries.all { rager.stages[it] == 1 })
        assertEquals(0, b.enemies[1].rage)
        assertTrue(b.log.any { "runs off" in it || "stands its ground" in it })
        // Tame the rest: every one comes home.
        while (b.outcome == BattleOutcome.ONGOING) {
            heroTurn()
            if (b.outcome != BattleOutcome.ONGOING) break
            b.enemies.filter { it.alive }.forEach { it.hp = 1 }
            b.step(BattleAction.Capture(Item.PERFECT_ORB))
        }
        assertEquals(BattleOutcome.CAPTURED, b.outcome)
        val res = b.result()
        assertTrue(res.tamed.size >= 2)
        assertEquals(lead.speciesId, res.tamed.first().speciesId)
        assertTrue(res.defeated.isEmpty())
    }

    @Test
    fun rageStacksWithEveryTame() {
        val r = Random(6)
        val a = Monster.create(Bestiary.get("emberkit"), 5, r)
        val c = Monster.create(Bestiary.get("emberkit"), 6, r)
        val big = Monster.create(Bestiary.get("emberkit"), 20, r)
        val b = Battle(Hero(level = 80), emptyList(), a, EncounterType.WILD, mapOf(Item.PERFECT_ORB to 3), false, rng = Random(3), extraEnemies = listOf(c, big))
        fun heroTurn() { while (b.current?.isHero != true && b.outcome == BattleOutcome.ONGOING) b.step(BattleAction.Defend) }
        for (i in 0..1) {
            heroTurn()
            b.selectTarget(b.enemies[i].key)
            b.step(BattleAction.Capture(Item.PERFECT_ORB))
        }
        assertEquals(2, b.enemies[2].rage)
        // +2 from rage (its own moves may have raised a stat further).
        assertTrue(BattleStat.entries.all { (b.enemies[2].stages[it] ?: 0) >= 2 })
        // Beat the last one: a win, and both tamed come home too.
        b.enemies[2].hp = 0
        b.step(BattleAction.Defend)
        assertEquals(BattleOutcome.WON, b.outcome)
        val res = b.result()
        assertEquals(2, res.tamed.size)
        assertEquals(listOf(big.speciesId), res.defeated)
    }

    @Test
    fun storageRoomLimitsHowManyCanBeTamed() {
        val r = Random(7)
        val pack = (0..2).map { Monster.create(Bestiary.get("emberkit"), 8, r) }
        val b = Battle(Hero(level = 80), emptyList(), pack[0], EncounterType.WILD, mapOf(Item.PERFECT_ORB to 3), false, rng = Random(3), extraEnemies = pack.drop(1), storageRoom = 1)
        while (b.current?.isHero != true && b.outcome == BattleOutcome.ONGOING) b.step(BattleAction.Defend)
        b.step(BattleAction.Capture(Item.PERFECT_ORB))
        if (b.outcome == BattleOutcome.ONGOING) assertNotNull(b.captureBlockedReason())
    }

    @Test
    fun everyLevelGivesASmallBoxThatGrowsAndLevel100GivesAPerfectOrb() {
        assertTrue((2..99).all { GameEngine.levelBox(it).values.sum() in 2..4 })
        assertEquals(5, GameEngine.levelBox(100).values.sum())
        assertTrue(Item.BINDING_CRYSTAL in GameEngine.levelBox(3))
        assertTrue(Item.RADIANT_CRYSTAL in GameEngine.levelBox(30))
        assertTrue(Item.PRISMATIC_CRYSTAL in GameEngine.levelBox(60))
        assertTrue(Item.PERFECT_ORB in GameEngine.levelBox(100))
        assertTrue((2..99).none { Item.PERFECT_ORB in GameEngine.levelBox(it) })
        // Loot never hands one out.
        val s = withMonsters(1).copy(hero = Hero(level = 90))
        assertTrue((1..200).none { Item.PERFECT_ORB in GameEngine.rollLoot(s, 6, Random(it)).items })

        // Levelling with a full bag: the box still arrives in full (no punishment)...
        val full = withMonsters(1).copy(hero = Hero(), inventory = mapOf(Item.POTION to STARTING_BAG_CAPACITY))
        val gain = GameEngine.addHeroXp(full, heroXpForNext(1))
        assertEquals(2, gain.state.hero.level)
        assertTrue(gain.state.bagCount > gain.state.bagCapacity)
        assertEquals(GameEngine.levelBox(2), gain.boxItems)
        // ...and nothing more can be bought until there's room, though earned items are still kept.
        val rich = gain.state.copy(gold = 1_000)
        assertEquals(rich, GameEngine.buy(rich, Item.POTION).state)
        assertEquals(1, GameEngine.addItems(rich, mapOf(Item.MANA_DRAUGHT to 1)).first.count(Item.MANA_DRAUGHT))
    }

    @Test
    fun aFaintedMonsterCanBeReplacedRightAwayOrOnItsTurn() {
        var s = withMonsters(7)
        s = GameEngine.setSwapSlot(s, s.monsters[5].uid, 0).state
        s = GameEngine.setSwapSlot(s, s.monsters[6].uid, 1).state
        val foe = Monster.create(Bestiary.get("emberkit"), 10, rng)
        val b = GameEngine.newBattle(s, foe, EncounterType.WILD, rng = Random(21), fullTeam = true)
        // Knock out the first party monster.
        val fallen = b.allies[1]
        fallen.hp = 0
        assertTrue(b.awaitsSwap(fallen))
        // Right away, for free: the first swap monster takes its place.
        val turnBefore = b.current?.key
        assertTrue(b.replaceFainted(s.monsters[5].uid, fallen.key))
        assertTrue(b.allies.any { it.key == s.monsters[5].uid })
        assertEquals(turnBefore, b.current?.key)

        // Declined: on the fallen monster's turn the only choices are Swap or Skip.
        val second = b.allies[2]
        second.hp = 0
        var guard = 0
        while (b.current?.key != second.key && b.outcome == BattleOutcome.ONGOING && guard++ < 40) {
            b.enemies.forEach { it.hp = it.maxHp }
            b.step(BattleAction.Defend)
        }
        assertEquals(second.key, b.current?.key)
        // Attacking from the grave does nothing; skipping passes the turn.
        b.step(BattleAction.Attack)
        assertEquals(second.key, b.current?.key)
        b.step(BattleAction.Skip)
        assertTrue(b.log.any { "turn passes" in it })
        // Next time round it can swap in the last waiting monster.
        guard = 0
        while (b.current?.key != second.key && b.outcome == BattleOutcome.ONGOING && guard++ < 40) {
            b.enemies.forEach { it.hp = it.maxHp }
            b.step(BattleAction.Defend)
        }
        b.step(BattleAction.Swap(s.monsters[6].uid, null))
        assertTrue(b.allies.any { it.key == s.monsters[6].uid })
        // With nobody left to swap in, fallen monsters stop getting turns.
        assertFalse(b.allies.any { b.awaitsSwap(it) })
    }
}
