package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/** Your buddy digs things up on walks, and finds better things as your bond grows. */
class BuddyDigTest {
    private fun walker(bond: Int): GameState {
        val m = Monster.create(Bestiary.get("zapmouse"), 10, Random(1)).copy(bond = bond)
        return GameState(monsters = listOf(m), party = listOf(m.uid))
    }

    @Test fun walkingTurnsThingsUp() {
        var s = walker(0)
        val before = s.inventory.values.sum() + s.torches
        repeat(20) { s = Buddy.addWalk(s, Buddy.DIG_METERS).first }
        assertTrue(s.inventory.values.sum() + s.torches > before + 5, "found things")
    }

    @Test fun aCloserBondFindsBetterThings() {
        assertTrue(Buddy.findChance(Bonds.MAX) > Buddy.findChance(0))
        val low = Buddy.findTable(0).map { it.first }
        val high = Buddy.findTable(Bonds.MAX).map { it.first }
        assertTrue(Item.GOLDEN_BERRY !in low && Item.GOLDEN_BERRY in high)
        assertTrue(null in high, "torches at a high bond")
        val digs = (0 until 400).mapNotNull { Buddy.dig(Bonds.MAX, Random(it)) }
        assertTrue(digs.any { it.second } && digs.any { it.first == Item.GOLDEN_BERRY })
    }

    @Test fun noBuddyNoDigs() {
        val s = GameState()
        assertTrue(Buddy.addWalk(s, Buddy.DIG_METERS * 10).first.inventory == s.inventory)
    }
}
