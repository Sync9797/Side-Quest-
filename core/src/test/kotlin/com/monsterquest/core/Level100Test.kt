package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Level100Test {
    private fun mon(level: Int) = Monster.create(Bestiary.get("blazecat"), level, Random(3), evolve = false)

    @Test
    fun levelHundredMonstersAscendAsFarAsTheHeroAllows() {
        val m = mon(100)
        // Hero at 100: up to 5 ranks; extra XP waits for the hero.
        val grown = m.gainXp(Ascension.XP_PER_RANK * 7 + 10, XpRules.monsterCap(100))
        assertEquals(100, grown.level)
        assertEquals(5, grown.ascension)
        assertEquals(Ascension.XP_PER_RANK - 1, grown.xp)
        assertTrue(grown.heldXp > 0)
        // Each rank is +1% to every stat.
        assertEquals((m.stats.attack * 1.05).toInt().toDouble(), grown.stats.attack.toDouble(), 1.0)
        // At hero 95 there's no room past 100 yet.
        assertEquals(0, m.gainXp(Ascension.XP_PER_RANK * 3, XpRules.monsterCap(95)).ascension)
        // XP left over on reaching 100 carries into ascension.
        val m99 = mon(99)
        val up = m99.gainXp(monsterXpForNext(99) + Ascension.XP_PER_RANK, XpRules.monsterCap(110))
        assertEquals(100, up.level)
        assertEquals(1, up.ascension)
        assertEquals(Ascension.XP_PER_RANK, up.xpToNext)
    }

    @Test
    fun heldXpAscendsWhenTheHeroLevels() {
        val base = GameEngine.chooseStarter(GameState(), "emberkit", Random(1)).state
        val m = mon(100).gainXp(Ascension.XP_PER_RANK * 9, XpRules.monsterCap(100))
        assertEquals(5, m.ascension)
        val s = base.copy(hero = base.hero.copy(level = 100), monsters = listOf(m), party = listOf(m.uid))
        val after = GameEngine.addHeroXp(s, heroXpForNext(100) * 3).state
        assertTrue(after.hero.level >= 102)
        assertTrue(after.monsters.single().ascension > 5)
    }

    @Test
    fun foesAscendWithAHeroPastHundred() {
        val base = GameEngine.chooseStarter(GameState(), "emberkit", Random(1)).state
        val foe = mon(100)
        fun hp(heroLevel: Int) = GameEngine.newBattle(base.copy(hero = base.hero.copy(level = heroLevel)), foe, EncounterType.WILD, rng = Random(2)).enemy.maxHp
        assertEquals(hp(90), hp(100))
        assertTrue(hp(130) > hp(100) * 1.25, "${hp(130)} vs ${hp(100)}")
        assertEquals(0, Ascension.enemyRanks(99))
        assertEquals(30, Ascension.enemyRanks(130))
        assertEquals(35, Ascension.cap(130))
    }

    @Test
    fun levelHundredBringsTheCenturionRewardOnce() {
        val base = GameEngine.chooseStarter(GameState(), "emberkit", Random(1)).state
        assertTrue(!Centurion.due(base.copy(hero = base.hero.copy(level = 99))))
        val s = base.copy(hero = base.hero.copy(level = 100), eggs = List(Eggs.MAX_EGGS) { Eggs.gift(Bestiary.get("emberkit"), Random(it), 0) })
        assertTrue(Centurion.due(s))
        val choices = Centurion.choices()
        assertTrue(choices.isNotEmpty() && choices.all { it.rarity == Rarity.LEGENDARY })
        val u = Centurion.claim(s, choices.first().id, Random(4))
        // The egg fits even with full egg slots, with 3 perfect stats.
        val egg = u.state.eggs.last()
        assertEquals(Eggs.MAX_EGGS + 1, u.state.eggs.size)
        assertEquals(choices.first().id, egg.speciesId)
        assertTrue(MONSTER_STATS.count { egg.ivs[it] == MAX_IV } >= Centurion.PERFECT_STATS)
        assertEquals(Titles.CENTURION, Titles.shown(u.state))
        assertTrue(!Centurion.due(u.state))
        assertEquals(u.state, Centurion.claim(u.state, choices.first().id).state)
    }

    @Test
    fun mythicDungeonsAppearPastHundredAndMakeAMythbreaker() {
        assertTrue(Venues.starDeck(100).none { it == Dungeons.MYTHIC_STARS })
        assertEquals(1, Venues.starDeck(101).count { it == Dungeons.MYTHIC_STARS })
        val base = GameEngine.chooseStarter(GameState(), "emberkit", Random(1)).state
        val team = (1..3).map { Monster.create(Bestiary.get("blazecat"), 100, Random(it)) }
        val s0 = base.copy(hero = base.hero.copy(level = 120), monsters = team, party = team.map { it.uid }, torches = 30)
        val mythic = (0 until 200).asSequence().flatMap { Dungeons.near(s0, 40.0 + it * 0.05, -74.0, 1_700_000_000_000L).asSequence() }
            .first { it.stars == Dungeons.MYTHIC_STARS }
        assertEquals(7, mythic.torchCost)
        assertEquals(14, mythic.rooms)
        var s = Dungeons.enter(s0, mythic).state
        s = s.copy(dungeonRun = s.dungeonRun!!.copy(step = mythic.rooms, fighting = RoomKind.BOSS))
        val won = Dungeons.afterFight(s, BattleOutcome.WON, 1_700_000_000_000L, Random(2))
        assertTrue(Titles.MYTHBREAKER in won.state.titles)
        assertEquals(1, won.state.mythicsCleared)
        assertEquals(Titles.MYTHBREAKER, Titles.shown(won.state.copy(titles = won.state.titles + Titles.CENTURION)))
        assertTrue("Mythbreaker" in won.message)
    }
}
