package com.monsterquest.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Journal rewards: a type's whole page, or a whole evolution line. */
class BestiaryRewardsTest {
    @Test fun everyTypeAndLineHasAGoalAndTheHiddenLineNone() {
        assertEquals(7, BestiaryRewards.typeGoals.size)
        assertTrue(BestiaryRewards.lineGoals.size > 10)
        assertTrue(BestiaryRewards.all.none { g -> g.members.any { Mythic.inLine(it.id) } })
    }

    @Test fun aCompleteLineIsClaimedOnce() {
        val g = BestiaryRewards.lineGoals.first()
        val partial = GameState(caught = setOf(g.members.first().id))
        assertTrue(BestiaryRewards.claimable(partial).isEmpty())
        assertFalse(BestiaryRewards.claim(partial, g.id).state.bestiaryClaimed.contains(g.id))
        val s = GameState(caught = g.members.map { it.id }.toSet())
        assertEquals(listOf(g.id), BestiaryRewards.claimable(s).map { it.id })
        val u = BestiaryRewards.claim(s, g.id)
        assertEquals(s.gold + BestiaryRewards.LINE_GOLD, u.state.gold)
        assertEquals(BestiaryRewards.LINE_CANDY, GameEngine.candyOf(u.state, g.members.first()))
        assertTrue(BestiaryRewards.claimable(u.state).isEmpty())
        assertEquals(u.state.gold, BestiaryRewards.claim(u.state, g.id).state.gold, "not twice")
    }

    @Test fun aCompleteTypePageGivesAFarwispAndATitle() {
        val g = BestiaryRewards.typeGoals.first { it.type == Element.FIRE }
        val s = GameState(caught = g.members.map { it.id }.toSet())
        val u = BestiaryRewards.claim(s, g.id)
        assertEquals(1, u.state.count(Item.FARWISP))
        assertTrue("Fire Warden" in u.state.titles)
        assertEquals("Fire Warden", Titles.shown(u.state))
    }
}
