package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BondsTest {
    private val base = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state
    /** Voltfawn (Electric) and Bubblefin (Water): a Storm Front pair. */
    private val state = base.let { s ->
        val pal = Monster.create(Bestiary.get("bubblefin"), 5, Random(7))
        s.copy(monsters = s.monsters + pal, party = s.party + pal.uid)
    }
    private val starter = state.partyMonsters.first()
    private fun foe() = Monster.create(Bestiary.get("zapmouse"), 3, Random(2))

    @Test
    fun bondLevelsFollowThePoints() {
        assertEquals(0, Bonds.level(0))
        assertEquals(1, Bonds.level(Bonds.THRESHOLDS[0]))
        assertEquals(Bonds.MAX, Bonds.level(10_000))
        assertEquals(1.10, Bonds.statFactor(10_000), 1e-9)
        assertFalse(Bonds.steadfast(Bonds.THRESHOLDS[3]))
        assertTrue(Bonds.steadfast(Bonds.THRESHOLDS[4]))
    }

    @Test
    fun walkingTogetherBuildsTheBond() {
        val s = GameEngine.walk(state, 2_500).state
        // The buddy (first slot) bonds every 400 steps, the rest every 1,000.
        assertEquals(starter.uid, Buddy.of(s)?.uid)
        s.partyMonsters.forEach { assertEquals(if (it.uid == starter.uid) 6 else 2, it.bond, it.displayName) }
        // The steps carry over.
        val more = GameEngine.walk(s, 600).state
        more.partyMonsters.forEach { assertEquals(if (it.uid == starter.uid) 7 else 3, it.bond) }
    }

    @Test
    fun winningAFightBuildsTheBondOfThoseWhoFought() {
        val b = GameEngine.newBattle(state, Monster.create(Bestiary.get("zapmouse"), 1, Random(2)), EncounterType.WILD, rng = Random(3))
        var guard = 0
        while (b.outcome == BattleOutcome.ONGOING && guard++ < 80) b.step(if (b.current?.alive == false) BattleAction.Skip else BattleAction.Attack)
        assertEquals(BattleOutcome.WON, b.outcome, b.log.takeLast(12).joinToString("\n") + " current=${b.current?.key}")
        val r = b.result()
        assertTrue(r.fought.isNotEmpty())
        val after = GameEngine.applyBattleResult(state, r, 0).state
        // The buddy (the starter, in the first slot) bonds fastest.
        val expected = if (starter.uid in r.fought) Bonds.BUDDY_PER_BATTLE else 0
        assertEquals(expected, after.monsters.first { it.uid == starter.uid }.bond)
        r.fought.forEach { uid -> assertEquals(if (uid == starter.uid) Bonds.BUDDY_PER_BATTLE else 1, after.monsters.first { it.uid == uid }.bond) }
    }

    @Test
    fun aDeepBondMakesItStrongerAndSteadfast() {
        val bonded = state.copy(monsters = state.monsters.map { if (it.uid == starter.uid) it.copy(bond = 1_000) else it })
        val plain = GameEngine.newBattle(state, foe(), EncounterType.WILD, rng = Random(3)).allies.first { it.key == starter.uid }
        val strong = GameEngine.newBattle(bonded, foe(), EncounterType.WILD, rng = Random(3)).allies.first { it.key == starter.uid }
        assertTrue(strong.maxHp > plain.maxHp)
        assertTrue(strong.steadfast)
        assertFalse(plain.steadfast)
    }

    @Test
    fun steadfastSurvivesOneKnockout() {
        val bonded = state.copy(monsters = state.monsters.map { if (it.uid == starter.uid) it.copy(bond = 1_000) else it })
        val brute = Monster.create(Bestiary.get("zapmouse"), 60, Random(5))
        val b = GameEngine.newBattle(bonded, brute, EncounterType.WILD, rng = Random(6), lead = starter.uid)
        var guard = 0
        while (b.outcome == BattleOutcome.ONGOING && guard++ < 80 && b.log.none { "Steadfast" in it }) {
            b.step(if (b.current?.alive == false) BattleAction.Skip else BattleAction.Defend)
        }
        assertTrue(b.log.any { "Steadfast" in it }, b.log.joinToString("\n"))
    }

    @Test
    fun pairedTypesGiveTheTeamASynergy() {
        assertEquals(listOf(Synergy.STORM_FRONT), Synergy.of(state.partyMonsters))
        assertEquals(1.05, Synergy.factor(listOf(Synergy.STORM_FRONT), BattleStat.SPEED), 1e-9)
        assertEquals(1.0, Synergy.factor(listOf(Synergy.STORM_FRONT), BattleStat.ATTACK), 1e-9)
        val b = GameEngine.newBattle(state, foe(), EncounterType.WILD, rng = Random(3))
        assertEquals(listOf(Synergy.STORM_FRONT), b.synergies)
        assertTrue(b.log.any { "Storm Front" in it })
        // Alone, the starter has no partner.
        val solo = state.copy(party = listOf(starter.uid))
        assertTrue(Synergy.of(solo.partyMonsters).isEmpty())
    }

    @Test
    fun theWeatherFavorsItsTypesOnBothSides() {
        val storm = GameEngine.newBattle(state, foe(), EncounterType.WILD, rng = Random(3), weather = LiveWeather.STORM)
        val calm = GameEngine.newBattle(state, foe(), EncounterType.WILD, rng = Random(3))
        val mine = storm.allies.first { it.key == starter.uid }
        assertTrue(mine.attack > calm.allies.first { it.key == starter.uid }.attack)
        // Their Electric monster draws on the storm too.
        assertTrue(storm.enemy.attack > calm.enemy.attack)
        assertTrue(storm.log.any { "draw" in it && "weather" in it })
        assertEquals(0.9, LiveWeather.RAIN.attackFactor(listOf(Element.FIRE)), 1e-9)
        assertEquals(1.0, LiveWeather.WIND.attackFactor(listOf(Element.EARTH)), 1e-9)
    }

    @Test
    fun theOffHandStrikesAfterTheMainHand() {
        val sword = Gear("a", WeaponType.SWORD, GearRarity.RARE, 30)
        val broad = Gear("b", WeaponType.BROADSWORD, GearRarity.RARE, 30)
        var s = state.copy(hero = state.hero.copy(level = 30), gear = listOf(sword, broad))
        s = GameEngine.learnSkill(s, 0, Trait.KLEPTOMANIAC).state
        s = GameEngine.equip(s, "a").state
        s = GameEngine.equipOffhand(s, "b").state
        assertNotNull(s.offhandGear)
        val b = GameEngine.newBattle(s, Monster.create(Bestiary.get("zapmouse"), 30, Random(2)), EncounterType.WILD, rng = Random(3))
        var guard = 0
        while (b.outcome == BattleOutcome.ONGOING && guard++ < 40 && b.log.none { "off hand" in it }) b.step(BattleAction.Attack)
        assertTrue(b.log.any { "strikes with the off hand" in it }, b.log.joinToString("\n"))
    }

    @Test
    fun theBountyPaysOnceAllThreeAreDone() {
        val today = 20_000L
        var s = Goals.rollover(state, state, today)
        assertFalse(Bounty.ready(s, today))
        assertEquals(s, Bounty.claim(s, today).state)
        s = s.copy(
            stats = s.stats.copy(
                totalSteps = s.stats.totalSteps + Bounty.STEPS,
                longFocusWins = s.stats.longFocusWins + Bounty.LONG_FOCUS_SESSIONS,
                tasksCompleted = s.stats.tasksCompleted + Bounty.QUESTS,
            ),
        )
        assertTrue(Bounty.ready(s, today))
        val u = Bounty.claim(s, today, Random(4))
        assertEquals(s.gold + Bounty.GOLD, u.state.gold)
        val prize = (u.state.gear - s.gear.toSet()).map { it.rarity } + (u.state.armor - s.armor.toSet()).map { it.rarity }
        assertEquals(1, prize.size)
        assertTrue(prize.single() >= GearRarity.RARE)
        assertTrue(Bounty.claimed(u.state, today))
        assertFalse(Bounty.ready(u.state, today))
    }

    @Test
    fun aLongPhoneFreeSessionCountsForTheBounty() {
        val started = GameEngine.startFocus(state, 60, 0, Random(1)).state
        val done = GameEngine.resolveFocus(started, success = true, rng = Random(2)).state
        assertEquals(state.stats.longFocusWins + 1, done.stats.longFocusWins)
        val short = GameEngine.resolveFocus(GameEngine.startFocus(state, 30, 0, Random(1)).state, success = true, rng = Random(2)).state
        assertEquals(state.stats.longFocusWins, short.stats.longFocusWins)
    }
}
