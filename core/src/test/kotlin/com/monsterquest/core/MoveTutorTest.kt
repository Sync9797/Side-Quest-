package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MoveTutorTest {
    private val mon = Monster.create(Bestiary.get("ember_pup"), 12, Random(1))

    @Test fun offersOnlyUnlearnedMovesOfItsOwnTypes() {
        val offers = MoveTutor.offers(mon)
        assertTrue(offers.isNotEmpty())
        val types = listOfNotNull(mon.species.element, mon.species.secondary)
        val known = mon.learnedMoves.map { it.id }.toSet()
        offers.forEach {
            assertTrue(it.move.type in types)
            assertTrue(it.move.id !in known)
        }
        // Better moves cost more.
        assertTrue(offers.first().gold <= offers.last().gold)
    }

    @Test fun buyingSpendsCandyAndGoldAndTeachesTheMove() {
        val o = MoveTutor.offers(mon).first { it.minLevel <= mon.level }
        val s = GameState(monsters = listOf(mon), gold = o.gold + 5, candy = mapOf(mon.species.family to o.candy + 1))
        val u = MoveTutor.buy(s, mon.uid, o.move.id)
        val after = u.state.monsters.single()
        assertTrue(o.move.id in after.learnedMoves.map { it.id })
        assertEquals(5, u.state.gold)
        assertEquals(1, u.state.candy[mon.species.family])
        // It can be put in the loadout, and isn't offered again.
        assertTrue(GameEngine.setMoves(u.state, mon.uid, listOf(o.move.id)).state.monsters.single().activeMoves.any { it.id == o.move.id })
        assertTrue(MoveTutor.offers(after).none { it.move.id == o.move.id })
    }

    @Test fun tooFewCandyOrTooLowLevelIsRefused() {
        val o = MoveTutor.offers(mon).first { it.minLevel <= mon.level }
        val poor = GameState(monsters = listOf(mon), gold = 99_999)
        assertNotNull(MoveTutor.blocker(poor, mon, o))
        assertEquals(poor, MoveTutor.buy(poor, mon.uid, o.move.id).state)
        MoveTutor.offers(mon).firstOrNull { it.minLevel > mon.level }?.let { far ->
            val rich = GameState(monsters = listOf(mon), gold = 99_999, candy = mapOf(mon.species.family to 999))
            assertEquals("Needs Lv ${far.minLevel}", MoveTutor.blocker(rich, mon, far))
        }
    }
}
