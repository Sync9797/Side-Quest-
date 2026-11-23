package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FarwispTest {
    @Test fun everyoneGetsOneOnceAndItCantBeBought() {
        val s = assertNotNull(Farwisp.gift(GameState())).state
        assertEquals(1, Farwisp.count(s))
        assertNull(Farwisp.gift(s))
        assertTrue(GameState.fromJson(s.toJson()).farwispGifted)
        assertEquals(0, Item.FARWISP.price)
    }

    @Test fun spendingUsesOne() {
        val s = GameState(inventory = mapOf(Item.FARWISP to 2))
        assertEquals(1, Farwisp.count(assertNotNull(Farwisp.spend(s))))
        assertNull(Farwisp.spend(GameState(inventory = emptyMap())))
    }

    @Test fun betterBoxesHoldThemMoreOften() {
        assertTrue((1..3).all { Farwisp.boxChance(it) < Farwisp.boxChance(it + 1) })
        // Over many boxes of each level, they do turn up, more in better boxes.
        fun found(rarity: BoxRarity): Int {
            val rng = Random(rarity.ordinal + 11)
            var n = 0
            repeat(1_500) { i ->
                val box = LootBox("b$i", rarity, 0L, walkedMeters = rarity.meters)
                val s = GameState(lootBoxes = listOf(box), inventory = emptyMap())
                n += LootBoxes.open(s, box.id, rng).update.state.count(Item.FARWISP)
            }
            return n
        }
        val common = found(BoxRarity.COMMON)
        val legendary = found(BoxRarity.LEGENDARY)
        assertTrue(common > 10, "$common")
        assertTrue(legendary > common * 3, "$common vs $legendary")
        assertFalse(found(BoxRarity.COMMON) > 150)
    }
}
