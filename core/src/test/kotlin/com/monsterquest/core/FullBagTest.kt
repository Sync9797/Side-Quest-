package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Nothing earned is ever lost to a full bag, and torches never pass the cap. */
class FullBagTest {
    /** A bag stuffed to (or past) its size. */
    private fun full(extra: Int = 0): GameState {
        val s = GameState(hero = Hero(level = 30))
        return s.copy(inventory = mapOf(Item.POTION to s.bagCapacity + extra))
    }

    private fun total(s: GameState) = s.inventory.values.sum() + s.gear.size + s.armor.size

    @Test fun lootBoxesOpenedWithAFullBagKeepEverything() {
        for (rarity in BoxRarity.entries) for (extra in listOf(-1, 0, 3)) {
            val box = LootBox("b", rarity, 0L, walkedMeters = rarity.meters)
            val s = full(extra).copy(lootBoxes = listOf(box))
            val opened = LootBoxes.open(s, box.id, Random(rarity.ordinal * 7 + extra)).update
            val gained = total(opened.state) - total(s)
            assertTrue(gained > 0, "$rarity with ${s.bagCount}/${s.bagCapacity}")
            assertTrue("left behind" !in opened.message, opened.message)
        }
    }

    @Test fun anyGrantedLootIsKeptPastTheBagsSize() {
        val s = full()
        val gear = Armory.roll(30, Random(1))
        val u = GameEngine.grantLoot(s, GameEngine.Loot(items = mapOf(Item.RESTORE_POTION to 4), weapons = listOf(gear)))
        assertEquals(4, u.state.count(Item.RESTORE_POTION))
        assertTrue(gear in u.state.gear)
        assertTrue("left behind" !in u.message)
    }

    @Test fun buyingStillNeedsRoom() {
        val s = full().copy(gold = 10_000)
        val u = GameEngine.buy(s, Item.POTION)
        assertEquals(s, u.state)
        assertTrue("Bag is full" in u.message)
    }

    @Test fun aDungeonEnteredWithAFullBagStillBanksItsLoot() {
        val d = Dungeon("dg:1:1:1", "Ember Forge", DungeonTheme.FORGE, 3, 12, 0.0, 0.0, seed = 7L)
        val team = Monster.create(Bestiary.get("emberkit"), 20, Random(2))
        val s = full().copy(torches = 20, monsters = listOf(team), party = listOf(team.uid))
        val inside = Dungeons.enter(s, d).state
        val run = inside.dungeonRun!!.copy(items = mapOf(Item.GREATER_POTION to 2), torches = 0)
        val left = Dungeons.leave(inside.copy(dungeonRun = run)).state
        assertEquals(2, left.count(Item.GREATER_POTION))
    }

    @Test fun dungeonTorchesNeverPassTheCap() {
        val d = Dungeon("dg:1:1:1", "Ember Forge", DungeonTheme.FORGE, 3, 12, 0.0, 0.0, seed = 7L)
        val team = Monster.create(Bestiary.get("emberkit"), 20, Random(3))
        val s = GameState(hero = Hero(level = 30), torches = 25, monsters = listOf(team), party = listOf(team.uid))
        val cap = Torches.cap(s)
        val inside = Dungeons.enter(s, d).state
        // A haul of torches on the way out, with the hero already near the cap.
        val run = inside.dungeonRun!!.copy(torches = 9)
        val left = Dungeons.leave(inside.copy(dungeonRun = run)).state
        assertTrue(left.torches <= cap, "${left.torches}/$cap")
        assertTrue(left.gold > inside.gold)
    }
}
