package com.monsterquest.core

import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DungeonsTest {
    private val utc = ZoneOffset.UTC
    private val now = 1_800_000_000_000L
    private fun start(): GameState {
        val s = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state
        val team = (1..3).map { Monster.create(Bestiary.get("emberkit"), 20, Random(it)) }
        return s.copy(monsters = team, party = team.map { it.uid }, torches = 10, hero = s.hero.copy(level = 20))
    }
    private fun some(s: GameState, stars: Int? = null): Dungeon =
        (0 until 60).asSequence().flatMap { Dungeons.near(s, 40.0 + it * 0.05, -74.0, now, utc) }.first { stars == null || it.stars == stars }

    @Test
    fun dungeonsVaryByDifficulty() {
        val s = start()
        val all = (0 until 40).flatMap { Dungeons.near(s, 40.0 + it * 0.05, -74.0, now, utc) }
        assertEquals((1..5).toSet(), all.map { it.stars }.toSet())
        val easy = all.first { it.stars == 1 }
        val hard = all.first { it.stars == 5 }
        assertTrue(hard.rooms > easy.rooms && hard.torchCost > easy.torchCost && hard.level > easy.level)
        assertTrue(Dungeons.residents(easy).isNotEmpty())
        assertTrue(Dungeons.bossRewards(hard).first().label.contains(hard.bossStat.label))
    }

    @Test
    fun doorsAreRandomButFixedAndFightsScale() {
        val s = start()
        val d = some(s, 1)
        val run = DungeonRun(d)
        assertEquals(Dungeons.doors(run), Dungeons.doors(run))
        val (l, r) = Dungeons.doors(run)
        assertTrue(l != r)
        // Easy monster rooms hold at most three.
        val sizes = (0 until 40).map { Dungeons.monstersFor(run.copy(step = it), RoomKind.MONSTERS).size }
        assertTrue(sizes.all { it in 1..3 } && sizes.max() == 3)
        val hard = DungeonRun(some(s, 5))
        assertTrue((0 until 40).map { Dungeons.monstersFor(hard.copy(step = it), RoomKind.MONSTERS).size }.max() == 4)
        // Guardians are cursed; Alphas are Alphas; the champion is a hero of the boss class.
        assertTrue(Dungeons.monstersFor(run, RoomKind.COMBAT).all { it.cursed })
        assertTrue(Dungeons.monstersFor(run, RoomKind.ELITE).single().alpha)
        assertEquals(d.bossClass, Dungeons.champion(d).cls)
    }

    @Test
    fun aRunFromEntranceToChampion() {
        var s = start()
        val d = some(s, 2)
        s = Dungeons.enter(s, d).state
        assertEquals(10 - d.torchCost, s.torches)
        assertTrue(s.dungeonRun != null)
        // Walk through, taking whichever door, and win every fight.
        var guard = 0
        while (s.dungeonRun?.atBoss == false && guard++ < 40) {
            val run = s.dungeonRun!!
            val (l, _) = Dungeons.doors(run)
            s = Dungeons.open(s, left = true, Random(guard)).state
            when {
                l.isFight -> {
                    assertEquals(l, s.dungeonRun!!.fighting)
                    s = Dungeons.afterFight(s, BattleOutcome.WON, now, Random(guard), utc).state
                }
                l == RoomKind.TRAP -> s = Dungeons.springTrap(s, true, Random(guard)).state
            }
            assertEquals(run.step + 1, s.dungeonRun!!.step)
        }
        assertTrue(s.dungeonRun!!.atBoss)
        val before = s.hero.attr(d.bossStat)
        s = Dungeons.open(s, true).state
        assertEquals(RoomKind.BOSS, s.dungeonRun!!.fighting)
        val goldBefore = s.gold
        val cleared = Dungeons.afterFight(s, BattleOutcome.WON, now, Random(1), utc)
        // The champion's prize comes with its icons: the stat, the gold and whatever the pool rolled.
        assertTrue(cleared.rewards.any { it is Reward.Stat && it.attribute == d.bossStat })
        assertTrue(cleared.rewards.any { it is Reward.Gold })
        assertEquals("${d.name} cleared", cleared.rewardTitle)
        s = cleared.state
        assertNull(s.dungeonRun)
        assertEquals(before + 1, s.hero.attr(d.bossStat))
        assertTrue(s.gold > goldBefore)
        assertTrue(d.id in s.dungeonsCleared)
        assertTrue(Dungeons.near(s, d.lat, d.lon, now, utc).none { it.id == d.id })
        // The stat only rises once a day.
        val d2 = some(s, 1)
        var t = Dungeons.enter(s, d2).state
        t = t.copy(dungeonRun = t.dungeonRun!!.copy(step = d2.rooms, fighting = RoomKind.BOSS))
        t = Dungeons.afterFight(t, BattleOutcome.WON, now, Random(2), utc).state
        assertEquals(before + 1, t.hero.attr(d.bossStat))
    }

    @Test
    fun trapsShowOddsAndHurtWhenTheyFail() {
        var s = start()
        val easy = some(s, 1)
        val hard = some(s, 5)
        // Every trap has its own odds; harder dungeons are worse on average, and never certain.
        fun odds(st: GameState, d: Dungeon) = (0 until d.rooms).flatMap { step ->
            listOf(true, false).map { Dungeons.trapChance(st, DungeonRun(d, step = step), it) }
        }
        val easyOdds = odds(s, easy)
        val hardOdds = odds(s, hard)
        assertTrue(easyOdds.average() > hardOdds.average())
        assertTrue((easyOdds + hardOdds).all { it in Dungeons.MIN_TRAP_CHANCE..Dungeons.MAX_TRAP_CHANCE })
        assertTrue(hardOdds.toSet().size > 3, "traps vary: $hardOdds")
        // A Rogue does better, and a fast hero too, but never past the cap.
        val rogue = s.copy(hero = s.hero.copy(heroClass = HeroClass.ROGUE))
        assertTrue(odds(rogue, hard).average() > hardOdds.average())
        val speedy = rogue.copy(hero = rogue.hero.copy(attributes = rogue.hero.attributes + (HeroAttribute.SPEED to 200)))
        assertTrue(odds(speedy, easy).all { it <= Dungeons.MAX_TRAP_CHANCE })
        // A sprung trap hurts everyone, more in harder dungeons.
        assertTrue(Dungeons.trapDamagePct(hard, TrapKind.FLAMES) > Dungeons.trapDamagePct(easy, TrapKind.DARTS))
        s = Dungeons.enter(s, hard).state
        val hurt = (1..40).map { Dungeons.springTrap(s, true, Random(it)) }.first { it.state.heroDamage > 0 }
        assertTrue("!" in hurt.message)
        assertEquals(1, hurt.state.dungeonRun!!.step)
    }

    @Test
    fun aBadTrapCanKnockOutAHurtMonster() {
        var s = start()
        s = Dungeons.enter(s, some(s, 5)).state
        // Hanging on with 1 HP: the next failed trap knocks them out (the hero hangs on).
        s = s.copy(monsters = s.monsters.map { it.copy(damage = it.stats.hp - 1) })
        val hit = (1..40).map { Dungeons.springTrap(s, true, Random(it)) }.first { it.state.heroDamage > 0 }
        assertTrue(hit.state.partyMonsters.all { it.fainted })
        assertTrue("knocked out" in hit.message)
        assertTrue(hit.state.heroDamage < hit.state.hero.maxHp)
    }

    @Test
    fun torchesLightTheWayPastTrapsAndHiddenDoors() {
        var s = start().copy(torches = 20)
        s = Dungeons.enter(s, some(s, 5)).state
        val torches = s.torches
        // A torch at a trap: through unhurt, every time.
        val past = Dungeons.torchPastTrap(s, true)
        assertEquals(torches - 1, past.state.torches)
        assertEquals(0, past.state.heroDamage)
        assertEquals(1, past.state.dungeonRun!!.step)
        // Torches found inside are used first.
        val found = s.copy(dungeonRun = s.dungeonRun!!.copy(torches = 2))
        assertEquals(1, Dungeons.torchPastTrap(found, true).state.dungeonRun!!.torches)
        assertEquals(torches, Dungeons.torchPastTrap(found, true).state.torches)
        // No torches, no light.
        val none = s.copy(torches = 0)
        assertEquals(none, Dungeons.torchPastTrap(none, true).state)
        // Hidden doors: a torch shows what's behind.
        var run = s.dungeonRun!!
        var step = 0
        while (!Dungeons.hidden(run.copy(step = step), true) && step < run.dungeon.rooms) step++
        run = run.copy(step = step)
        assertTrue(Dungeons.hidden(run, true), "a 5-star dungeon has hidden doors")
        val looked = Dungeons.scout(s.copy(dungeonRun = run), true)
        assertEquals(torches - 1, looked.state.torches)
        assertTrue(!Dungeons.hidden(looked.state.dungeonRun!!, true))
        assertTrue("behind the door" in looked.message)
        // Walking in blind springs a hidden trap straight away.
        val trapRun = (0 until 200).map { run.copy(dungeon = run.dungeon.copy(seed = it.toLong())) }
            .first { Dungeons.hidden(it, true) && Dungeons.doors(it).first == RoomKind.TRAP }
        val blind = Dungeons.openBlind(s.copy(dungeonRun = trapRun), true, Random(1))
        assertEquals(trapRun.step + 1, blind.state.dungeonRun!!.step)
    }

    @Test
    fun dungeonsCostMoreTorchesAndGetHarderAsYouLevel() {
        val s = start()
        assertEquals(2, some(s, 1).torchCost)
        assertEquals(6, some(s, 5).torchCost)
        assertTrue(Venues.starDeck(5).average() < Venues.starDeck(25).average())
        assertTrue(Venues.starDeck(25).average() < Venues.starDeck(60).average())
        assertTrue(Venues.starDeck(60).count { it >= 4 } >= 6)
    }

    @Test
    fun fallingLosesHalfLeavingKeepsAll() {
        var s = start()
        s = Dungeons.enter(s, some(s, 3)).state
        s = s.copy(dungeonRun = s.dungeonRun!!.copy(gold = 100, torches = 4, fighting = RoomKind.MONSTERS))
        val lost = Dungeons.afterFight(s, BattleOutcome.LOST, now, Random(1), utc).state
        assertEquals(s.gold + 50, lost.gold)
        assertEquals(s.torches + 2, lost.torches)
        assertNull(lost.dungeonRun)
        val left = Dungeons.leave(s).state
        assertEquals(s.gold + 100, left.gold)
    }

    @Test
    fun fountainsRestoreAndNeedTorches() {
        var s = start().copy(torches = 0)
        val d = some(s)
        assertEquals(s, Dungeons.enter(s, d).state)
        s = Dungeons.enter(s.copy(torches = 10, heroDamage = 30), d).state
        // Force a fountain behind the left door by trying runs until one has it.
        val run = (0L until 200L).map { seed -> s.dungeonRun!!.copy(dungeon = s.dungeonRun!!.dungeon.copy(seed = seed)) }
            .flatMap { r -> (0 until r.dungeon.rooms).map { r.copy(step = it) } }
            .first { Dungeons.doors(it).first == RoomKind.FOUNTAIN }
        s = s.copy(dungeonRun = run)
        s = Dungeons.open(s, left = true).state
        assertEquals(0, s.heroDamage)
    }

    @Test
    fun rewardScreensListWhatTheyGive() {
        // A treasury chest lists its gold (and whatever else it held).
        var s = start()
        val d = some(s, 3)
        s = Dungeons.enter(s, d).state
        val run = s.dungeonRun!!
        val chest = Dungeons.open(s.copy(dungeonRun = run.copy(step = 0)).let { st ->
            // Force a treasury behind the left door.
            var t = st
            var seed = 0
            while (Dungeons.doors(t.dungeonRun!!).first != RoomKind.TREASURY && seed < 500) {
                seed++
                t = t.copy(dungeonRun = t.dungeonRun!!.copy(dungeon = t.dungeonRun!!.dungeon.copy(seed = seed.toLong())))
            }
            t
        }, left = true, Random(3))
        if (chest.rewardTitle == "Treasury") assertTrue(chest.rewards.first() is Reward.Gold)
        // Every entry in a champion's pool has something to show as its icon.
        Dungeons.bossRewards(d).forEach { r ->
            assertTrue(r.kind != BossReward.Kind.ITEM || r.item != null, r.label)
            assertTrue(r.kind != BossReward.Kind.STAT || r.stat != null, r.label)
        }
        // The academy hands out a tome.
        assertEquals(listOf<Reward>(Reward.Tome(Facilities.STUDY_PAGES)), Facilities.study(start(), now, utc).rewards)
    }

    @Test
    fun dungeonLootComesHomeEvenPastTheBagUnlessYouWentInFull() {
        val base = start()
        val d = some(base, 2)
        val room = base.bagCapacity - base.bagCount
        // Went in with room: everything comes home, even past the bag's size.
        var s = Dungeons.enter(base, d).state
        assertTrue(!s.dungeonRun!!.bagFullAtStart)
        s = s.copy(dungeonRun = s.dungeonRun!!.copy(items = mapOf(Item.POTION to room + 10)))
        val out = Dungeons.leave(s).state
        assertEquals(base.bagCount + room + 10, out.bagCount)
        assertTrue(out.bagCount > out.bagCapacity)
        // Over the bag's size, nothing else fits afterwards.
        assertEquals(out.inventory, GameEngine.addItems(out, mapOf(Item.POTION to 1)).first.inventory)
        // Went in full: you're told, and loot only fits if there's room.
        val full = base.copy(inventory = base.inventory + (Item.POTION to (base.inventory[Item.POTION] ?: 0) + room))
        assertTrue(full.bagFull)
        val entered = Dungeons.enter(full, d)
        assertTrue("bag is full" in entered.message)
        var f = entered.state.copy(dungeonRun = entered.state.dungeonRun!!.copy(items = mapOf(Item.GREATER_POTION to 5)))
        f = Dungeons.leave(f).state
        assertEquals(full.bagCount, f.bagCount)
    }
}
