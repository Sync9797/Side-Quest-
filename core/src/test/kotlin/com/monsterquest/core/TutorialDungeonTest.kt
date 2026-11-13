package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TutorialDungeonTest {
    @Test
    fun aGuidedFirstDungeonThatCantBeLost() {
        var s = Dungeons.startTutorial(GameState())
        val run = s.dungeonRun!!
        assertTrue(run.dungeon.tutorial)
        assertEquals(0, run.dungeon.stars)
        assertEquals(0, run.dungeon.torchCost)
        // Every room kind, in order, then the champion.
        val seen = mutableListOf<RoomKind>()
        // Room 1: choose a starter.
        assertEquals(RoomKind.STARTER, Dungeons.doors(s.dungeonRun!!).first)
        s = Dungeons.pickStarter(s, "voltfawn", Random(1)).state
        assertTrue(s.starterChosen && s.monsters.size == 1)
        seen += RoomKind.STARTER
        var guard = 0
        while (!s.dungeonRun!!.atBoss && guard++ < 20) {
            val room = Dungeons.doors(s.dungeonRun!!).first
            seen += room
            s = Dungeons.open(s, true, Random(guard)).state
            when {
                room.isFight -> {
                    // Even a lost fight goes on in the tutorial.
                    s = Dungeons.afterFight(s, BattleOutcome.LOST, 0L, Random(guard)).state
                    assertTrue(s.dungeonRun != null)
                }
                room == RoomKind.TRAP -> {
                    assertEquals(1.0, Dungeons.trapChance(s, s.dungeonRun!!, true))
                    s = Dungeons.springTrap(s, true, Random(guard)).state
                }
            }
        }
        assertEquals(Dungeons.TUTORIAL_ROOMS, seen)
        // The champion: a fight every cast wins, then +1 to the class stat.
        val stat = s.dungeonRun!!.dungeon.bossStat
        val before = s.hero.attr(stat)
        s = Dungeons.open(s, true).state
        assertEquals(RoomKind.BOSS, s.dungeonRun!!.fighting)
        s = Dungeons.afterFight(s, BattleOutcome.LOST, 0L, Random(1)).state
        assertNull(s.dungeonRun)
        assertTrue(s.tutorialDungeonDone)
        assertEquals(before + 1, s.hero.attr(stat))
        assertTrue(s.count(Item.BINDING_CRYSTAL) >= 5 && s.torches >= 3)
        // One of a kind.
        assertEquals(s, Dungeons.startTutorial(s))
        // Tutorial fights: every cast tames, foes are gentle.
        val foe = Dungeons.monstersFor(DungeonRun(Dungeons.tutorialDungeon, step = 2), RoomKind.MONSTERS).single()
        val b = GameEngine.newBattle(s, foe, EncounterType.WILD, rng = Random(2), tutorial = true)
        b.enemy.hp = b.enemy.maxHp / 3
        assertEquals(1.0, b.captureOdds(Item.BINDING_CRYSTAL, TraceGrade.FIZZLE))
    }
}
